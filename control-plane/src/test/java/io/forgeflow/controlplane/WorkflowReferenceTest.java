package io.forgeflow.controlplane;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import org.flowable.bpmn.converter.BpmnXMLConverter;
import org.flowable.bpmn.model.CallActivity;
import org.flowable.bpmn.model.FlowNode;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.SequenceFlow;
import org.flowable.bpmn.model.StartEvent;
import org.flowable.bpmn.model.Task;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowReferenceTest {
    @Test void nonExecutableBpmnAndMermaidDescribeTheSameConnectedProcesses() throws Exception {
        Path architecture = Path.of("../docs/architecture");
        byte[] xml = Files.readAllBytes(architecture.resolve("workflows/development-lifecycle.bpmn20.xml"));
        assertThat(new String(xml, StandardCharsets.UTF_8)).contains("xmlns:flowable=\"http://flowable.org/bpmn\"");
        var converter = new BpmnXMLConverter();
        var model = converter.convertToBpmnModel(() -> new ByteArrayInputStream(xml), true, true);
        var processes = model.getProcesses().stream().map(process -> process.getId()).toList();
        assertThat(processes).containsExactlyInAnyOrder("epicCoordinationReference", "storyDeliveryReference", "reviewMergeReference", "hotfixDeliveryReference", "skillImprovementReference");
        Map<String, String> bpmnNodes = new HashMap<>();
        Set<String> bpmnEdges = new HashSet<>();
        for (var process : model.getProcesses()) {
            assertThat(process.isExecutable()).as(process.getId()).isFalse();
            var nodes = process.getFlowElements().stream().filter(FlowNode.class::isInstance).map(FlowNode.class::cast).toList();
            var reachable = new HashSet<String>();
            var pending = new ArrayDeque<FlowNode>();
            nodes.stream().filter(StartEvent.class::isInstance).forEach(pending::add);
            assertThat(pending).hasSize(1);
            while (!pending.isEmpty()) {
                var node = pending.remove();
                if (reachable.add(node.getId())) node.getOutgoingFlows().forEach(flow -> pending.add((FlowNode) process.getFlowElement(flow.getTargetRef())));
            }
            assertThat(reachable).containsExactlyInAnyOrderElementsOf(nodes.stream().map(FlowNode::getId).toList());
            for (var node : nodes) {
                assertThat(node.getClass()).as("Concrete task type for IDE compatibility: %s", node.getId()).isNotEqualTo(Task.class);
                assertThat(bpmnNodes.put(node.getId(), node.getName())).isNull();
                assertThat(model.getGraphicInfo(node.getId())).as("BPMN shape for %s", node.getId()).isNotNull();
                if (node instanceof CallActivity call) assertThat(processes).contains(call.getCalledElement());
            }
            for (var element : process.getFlowElements()) if (element instanceof SequenceFlow flow) {
                assertThat(process.getFlowElement(flow.getSourceRef())).isInstanceOf(FlowNode.class);
                assertThat(process.getFlowElement(flow.getTargetRef())).isInstanceOf(FlowNode.class);
                assertThat(model.getFlowLocationGraphicInfo(flow.getId())).as("BPMN edge for %s", flow.getId()).hasSizeGreaterThanOrEqualTo(2);
                var points = model.getFlowLocationGraphicInfo(flow.getId());
                for (int i = 1; i < points.size(); i++) {
                    var before = points.get(i - 1);
                    var after = points.get(i);
                    assertThat(before.getX() == after.getX() || before.getY() == after.getY()).as("Orthogonal connector %s", flow.getId()).isTrue();
                }
                bpmnEdges.add(edge(flow.getSourceRef(), flow.getName(), flow.getTargetRef()));
            }
        }
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        var document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
        String namespace = "http://www.omg.org/spec/BPMN/20100524/MODEL";
        var stores = document.getElementsByTagNameNS(namespace, "dataStoreReference");
        assertThat(stores.getLength()).isEqualTo(2);
        for (int i = 0; i < stores.getLength(); i++) {
            var store = (Element) stores.item(i);
            assertThat(store.getAttribute("dataStoreRef")).isEqualTo("agentFeedbackData");
            bpmnNodes.put(store.getAttribute("id"), store.getAttribute("name"));
            assertThat(model.getGraphicInfo(store.getAttribute("id"))).isNotNull();
        }
        var associations = document.getElementsByTagNameNS(namespace, "association");
        for (int i = 0; i < associations.getLength(); i++) {
            var association = (Element) associations.item(i);
            bpmnEdges.add(edge(association.getAttribute("sourceRef"), null, association.getAttribute("targetRef")));
            assertThat(model.getFlowLocationGraphicInfo(association.getAttribute("id"))).hasSizeGreaterThanOrEqualTo(2);
        }
        var review = model.getProcessById("reviewMergeReference");
        assertThat(reaches(review, "reviewStart", "requestMerge", "")).isTrue();
        for (String gate : Set.of("qaDecision", "recordQAFeedback", "requestAgentReviews", "recordAgentFeedback", "awaitHumanReview", "recordHumanFeedback", "mergeGuards")) {
            assertThat(reaches(review, "reviewStart", "requestMerge", gate)).as("Review cannot bypass %s", gate).isFalse();
        }
        assertThat(((FlowNode) review.getFlowElement("publishRemediation")).getOutgoingFlows())
                .extracting(SequenceFlow::getTargetRef).containsExactly("runQA");
        var learning = model.getProcessById("skillImprovementReference");
        assertThat(reaches(learning, "feedbackBatchReady", "publishSkillVersion", "")).isTrue();
        for (String gate : Set.of("evaluateSkillChange", "awaitSkillApproval")) {
            assertThat(reaches(learning, "feedbackBatchReady", "publishSkillVersion", gate)).as("Skill publication cannot bypass %s", gate).isFalse();
        }
        var hotfix = model.getProcessById("hotfixDeliveryReference");
        assertThat(((CallActivity) hotfix.getFlowElement("reviewHotfix")).getCalledElement()).isEqualTo("reviewMergeReference");
        assertThat(reaches(hotfix, "hotfixRequested", "tagHotfix", "")).isTrue();
        for (String gate : Set.of("awaitHotfixApproval", "hotfixBaseReady", "hotfixTests", "reviewHotfix", "awaitHotfixReleaseApproval", "hotfixReleaseReady")) {
            assertThat(reaches(hotfix, "hotfixRequested", "tagHotfix", gate)).as("Hotfix cannot bypass %s", gate).isFalse();
        }
        String guide = Files.readString(architecture.resolve("workflow-reference.md"));
        var nodePattern = Pattern.compile("^    (\\w+)(?:\\[|\\{|\\().*?\"([^\"]+)\"", Pattern.MULTILINE);
        var edgePattern = Pattern.compile("^    (\\w+) (?:-->|-\\.->)(?:\\|\"([^\"]+)\"\\|)? (\\w+)$", Pattern.MULTILINE);
        Map<String, String> mermaidNodes = new HashMap<>();
        Set<String> mermaidEdges = new HashSet<>();
        nodePattern.matcher(guide).results().forEach(match -> mermaidNodes.put(match.group(1), match.group(2)));
        edgePattern.matcher(guide).results().forEach(match -> mermaidEdges.add(edge(match.group(1), match.group(2), match.group(3))));
        assertThat(mermaidNodes).containsExactlyInAnyOrderEntriesOf(bpmnNodes);
        assertThat(mermaidEdges).containsExactlyInAnyOrderElementsOf(bpmnEdges);
    }
    private static boolean reaches(Process process, String start, String target, String excluded) {
        var pending = new ArrayDeque<String>();
        var visited = new HashSet<String>();
        pending.add(start);
        while (!pending.isEmpty()) {
            String current = pending.remove();
            if (current.equals(excluded) || !visited.add(current)) continue;
            if (current.equals(target)) return true;
            var node = (FlowNode) process.getFlowElement(current);
            node.getOutgoingFlows().forEach(flow -> pending.add(flow.getTargetRef()));
        }
        return false;
    }
    private static String edge(String source, String label, String target) {
        return source + "\t" + (label == null ? "" : label) + "\t" + target;
    }
}
