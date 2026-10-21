package io.forgeflow.spike;

import java.util.Set;
import org.flowable.engine.RepositoryService;
import org.springframework.stereotype.Service;

/** Closed declarative configuration: project choices cannot introduce executable XML or Java. */
@Service
public class RegisteredWorkflows {
    private final RepositoryService repository;
    public RegisteredWorkflows(RepositoryService repository) { this.repository = repository; }

    public String deploy(String variant, String activity, String provider) {
        if (!Set.of("direct", "review-first").contains(variant)
                || !activity.equals("coding-test.v1")
                || !Set.of("deterministic-a", "deterministic-b", "codex").contains(provider)) {
            throw new IllegalArgumentException("Unregistered workflow, activity or provider");
        }
        String prefix = variant.equals("direct")
                ? "<sequenceFlow id=\"entry\" sourceRef=\"start\" targetRef=\"dispatch\"/>"
                : """
                  <sequenceFlow id="entry" sourceRef="start" targetRef="review"/>
                  <userTask id="review" name="Project review fixture"/>
                  <sequenceFlow id="reviewed" sourceRef="review" targetRef="dispatch"/>
                  """;
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                  xmlns:flowable="http://flowable.org/bpmn" targetNamespace="forgeflow.registered">
                  <process id="handoff_%s" isExecutable="true">
                    <startEvent id="start"/>%s
                    <serviceTask id="dispatch" flowable:delegateExpression="${dispatchV1}"/>
                    <sequenceFlow id="f2" sourceRef="dispatch" targetRef="external"/>
                    <receiveTask id="external"/>
                    <sequenceFlow id="f3" sourceRef="external" targetRef="done"/>
                    <endEvent id="done"/>
                  </process>
                </definitions>
                """.formatted(variant.replace('-', '_'), prefix);
        var deployment = repository.createDeployment().addString("handoff.bpmn20.xml", xml).deploy();
        return repository.createProcessDefinitionQuery().deploymentId(deployment.getId()).singleResult().getId();
    }
}
