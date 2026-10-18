package io.forgeflow.spike;

import java.util.Map;
import java.util.UUID;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.repository.ProcessDefinition;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowPersistenceTest {
    private ConfigurableApplicationContext engine() {
        var context = new SpringApplicationBuilder(SpikeApplication.class).run();
        context.getBean(JdbcTemplate.class).execute("""
                CREATE TABLE IF NOT EXISTS spike_approval (
                    run_key text PRIMARY KEY, definition_id text NOT NULL,
                    plan_digest text NOT NULL, config_digest text NOT NULL)
                """);
        return context;
    }

    private ProcessDefinition deploy(ConfigurableApplicationContext context, String key, String wait) {
        // These fixtures are trusted application code, not project-supplied XML.
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                  xmlns:flowable="http://flowable.org/bpmn" targetNamespace="forgeflow.spike">
                  <process id="%s" isExecutable="true">
                    <startEvent id="start"/>
                    <sequenceFlow id="f1" sourceRef="start" targetRef="wait"/>
                    <userTask id="wait" name="%s"/>
                    <sequenceFlow id="f2" sourceRef="wait" targetRef="code"/>
                    <serviceTask id="code" flowable:delegateExpression="${codingTestV1}"/>
                    <sequenceFlow id="f3" sourceRef="code" targetRef="done"/>
                    <endEvent id="done"/>
                  </process>
                </definitions>
                """.formatted(key, wait);
        var repository = context.getBean(RepositoryService.class);
        var deployment = repository.createDeployment().addString(key + ".bpmn20.xml", xml).deploy();
        return repository.createProcessDefinitionQuery().deploymentId(deployment.getId()).singleResult();
    }

    private void approve(ConfigurableApplicationContext context, String key, ProcessDefinition definition) {
        // Trusted test fixture; NOT evidence of authorized human approval handling.
        context.getBean(JdbcTemplate.class).update("INSERT INTO spike_approval VALUES (?, ?, ?, ?)",
                key, definition.getId(), "plan-1", "config-1");
    }

    @Test
    void deploymentDoesNotMigrateWaitingRunAndRestartRetainsPinnedIdentity() {
        String key = "pin_" + UUID.randomUUID().toString().replace("-", "");
        String runKey = UUID.randomUUID().toString();
        String instanceId;
        String oldDefinition;
        try (var context = engine()) {
            var v1 = deploy(context, key, "approval-v1");
            approve(context, runKey, v1);
            var runtime = context.getBean(RuntimeService.class);
            var instance = runtime.startProcessInstanceById(v1.getId(), runKey, variables("deterministic-a"));
            instanceId = instance.getId();
            oldDefinition = v1.getId();
            var v2 = deploy(context, key, "approval-v2");
            assertThat(v2.getVersion()).isEqualTo(v1.getVersion() + 1);
            assertThat(runtime.createProcessInstanceQuery().processInstanceId(instanceId)
                    .singleResult().getProcessDefinitionId()).isEqualTo(v1.getId());
        }
        // New Spring context/engine with the same PostgreSQL database.
        try (var context = engine()) {
            var runtime = context.getBean(RuntimeService.class);
            assertThat(runtime.createProcessInstanceQuery().processInstanceId(instanceId)
                    .singleResult().getProcessDefinitionId()).isEqualTo(oldDefinition);
            assertThat(runtime.getVariable(instanceId, "configDigest")).isEqualTo("config-1");
            var tasks = context.getBean(TaskService.class);
            var task = tasks.createTaskQuery().processInstanceId(instanceId).singleResult();
            assertThat(task.getName()).isEqualTo("approval-v1");
            tasks.complete(task.getId());
            assertThat(runtime.createProcessInstanceQuery().processInstanceId(instanceId).count()).isZero();
        }
    }

    @Test
    void guardsRejectMissingOrChangedApprovalAndRollbackTaskCompletion() {
        try (var context = engine()) {
            String key = "guard_" + UUID.randomUUID().toString().replace("-", "");
            String runKey = UUID.randomUUID().toString();
            var definition = deploy(context, key, "wait");
            var runtime = context.getBean(RuntimeService.class);
            var instance = runtime.startProcessInstanceById(definition.getId(), runKey, variables("deterministic-a"));
            var tasks = context.getBean(TaskService.class);
            String taskId = tasks.createTaskQuery().processInstanceId(instance.getId()).singleResult().getId();
            assertThatThrownBy(() -> tasks.complete(taskId)).hasMessageContaining("Missing approval");
            assertThat(tasks.createTaskQuery().taskId(taskId).count()).isEqualTo(1);
            approve(context, runKey, definition);
            runtime.setVariable(instance.getId(), "configDigest", "changed");
            assertThatThrownBy(() -> tasks.complete(taskId)).hasMessageContaining("Missing approval");
            runtime.setVariable(instance.getId(), "configDigest", "config-1");
            runtime.setVariable(instance.getId(), "provider", "unregistered");
            assertThatThrownBy(() -> tasks.complete(taskId)).hasMessageContaining("Unregistered");
            runtime.setVariable(instance.getId(), "provider", "deterministic-a");
            tasks.complete(taskId);
            assertThat(runtime.createProcessInstanceQuery().processInstanceId(instance.getId()).count()).isZero();
        }
    }

    @Test
    void twoProjectVariantsUseTheSameActivityWithDifferentTestProviders() {
        try (var context = engine()) {
            var history = context.getBean(org.flowable.engine.HistoryService.class);
            for (String provider : new String[]{"deterministic-a", "deterministic-b"}) {
                String key = "project_" + UUID.randomUUID().toString().replace("-", "");
                String runKey = UUID.randomUUID().toString();
                var definition = deploy(context, key, "review-" + provider);
                approve(context, runKey, definition);
                var instance = context.getBean(RuntimeService.class).startProcessInstanceById(
                        definition.getId(), runKey, variables(provider));
                var tasks = context.getBean(TaskService.class);
                tasks.complete(tasks.createTaskQuery().processInstanceId(instance.getId()).singleResult().getId());
                var evidence = history.createHistoricVariableInstanceQuery().processInstanceId(instance.getId())
                        .variableName("sanitizedEvidence").singleResult();
                assertThat(evidence.getValue()).isEqualTo(provider.equals("deterministic-a") ? "fixture-a" : "fixture-b");
            }
        }
    }

    private Map<String, Object> variables(String provider) {
        return Map.of("planDigest", "plan-1", "configDigest", "config-1", "provider", provider);
    }
}
