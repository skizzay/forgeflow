package io.forgeflow.spike;

import java.util.Map;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.*;

/** Explicit -Dtest probe: run in separate JVMs against the same/restored disposable database. */
class SchemaLifecycleProbe {
    @Test
    void preservePinnedRunAcrossEngineUpgradeAndRestore() {
        String phase = System.getProperty("spike.schema.phase", "");
        assertThat(phase).isIn("prepare", "migrate", "validate");
        boolean validate = phase.equals("validate");
        try (var context = new SpringApplicationBuilder(SpikeApplication.class)
                .run("--flowable.database-schema-update=" + !validate)) {
            var jdbc = context.getBean(JdbcTemplate.class);
            var runtime = context.getBean(RuntimeService.class);
            if (phase.equals("prepare")) {
                jdbc.execute("CREATE TABLE spike_schema_probe (instance_id text NOT NULL, definition_id text NOT NULL)");
                var repository = context.getBean(RepositoryService.class);
                var deployment = repository.createDeployment().addString("schema.bpmn20.xml", """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" targetNamespace="forgeflow.schema">
                          <process id="schema_probe" isExecutable="true">
                            <startEvent id="start"/>
                            <sequenceFlow id="f1" sourceRef="start" targetRef="external"/>
                            <receiveTask id="external"/>
                            <sequenceFlow id="f2" sourceRef="external" targetRef="done"/>
                            <endEvent id="done"/>
                          </process>
                        </definitions>
                        """).deploy();
                String definition = repository.createProcessDefinitionQuery().deploymentId(deployment.getId()).singleResult().getId();
                var instance = runtime.startProcessInstanceById(definition, "schema-probe",
                        Map.of("planDigest", "approved-plan-1", "configDigest", "approved-config-1"));
                jdbc.update("INSERT INTO spike_schema_probe VALUES (?, ?)", instance.getId(), definition);
            }
            var identity = jdbc.queryForMap("SELECT instance_id, definition_id FROM spike_schema_probe");
            String instance = (String) identity.get("instance_id");
            assertThat(runtime.createProcessInstanceQuery().processInstanceId(instance).singleResult()
                    .getProcessDefinitionId()).isEqualTo(identity.get("definition_id"));
            assertThat(runtime.getVariable(instance, "planDigest")).isEqualTo("approved-plan-1");
            assertThat(runtime.getVariable(instance, "configDigest")).isEqualTo("approved-config-1");
            var waiting = runtime.createExecutionQuery().processInstanceId(instance).activityId("external").singleResult();
            assertThat(waiting).isNotNull();
            if (validate) {
                runtime.trigger(waiting.getId());
                assertThat(runtime.createProcessInstanceQuery().processInstanceId(instance).count()).isZero();
            }
        }
    }
}
