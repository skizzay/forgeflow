package io.forgeflow.spike;

import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;

import static org.assertj.core.api.Assertions.*;

/** Explicit probe, not a Pi sizing benchmark or a production load test. */
class ResourceFootprintProbe {
    @Test
    void sampleWarmEngineAfterOneHundredSequentialWorkflowInstances() throws Exception {
        long began = System.nanoTime();
        try (var context = new SpringApplicationBuilder(SpikeApplication.class).run()) {
            var repository = context.getBean(RepositoryService.class);
            var deployment = repository.createDeployment().addString("footprint.bpmn20.xml", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" targetNamespace="forgeflow.footprint">
                      <process id="footprint" isExecutable="true">
                        <startEvent id="start"/>
                        <sequenceFlow id="f1" sourceRef="start" targetRef="external"/>
                        <receiveTask id="external"/>
                        <sequenceFlow id="f2" sourceRef="external" targetRef="done"/>
                        <endEvent id="done"/>
                      </process>
                    </definitions>
                    """).deploy();
            String definition = repository.createProcessDefinitionQuery().deploymentId(deployment.getId()).singleResult().getId();
            var runtime = context.getBean(RuntimeService.class);
            for (int i = 0; i < 100; i++) {
                var process = runtime.startProcessInstanceById(definition);
                runtime.trigger(runtime.createExecutionQuery().processInstanceId(process.getId()).activityId("external").singleResult().getId());
            }
            assertThat(runtime.createProcessInstanceQuery().processDefinitionId(definition).count()).isZero();
            var heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
            String rss = Files.readAllLines(Path.of("/proc/self/status")).stream()
                    .filter(line -> line.startsWith("VmRSS:")).findFirst().orElseThrow().replaceAll("[^0-9]", "");
            System.out.println("FOOTPRINT_JVM_RSS_KIB=" + rss);
            System.out.println("FOOTPRINT_JVM_HEAP_USED_BYTES=" + heap.getUsed());
            System.out.println("FOOTPRINT_ELAPSED_MS=" + (System.nanoTime() - began) / 1_000_000);
        }
    }
}
