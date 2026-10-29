package io.forgeflow.controlplane;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.flowable.bpmn.converter.BpmnXMLConverter;
import org.flowable.bpmn.model.CallActivity;
import org.flowable.bpmn.model.FlowNode;
import org.flowable.bpmn.model.SequenceFlow;
import org.flowable.bpmn.model.StartEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowReferenceTest {
    @Test void nonExecutableBpmnAndMermaidDescribeTheSameConnectedProcesses() throws Exception {
        Path architecture = Path.of("../docs/architecture");
        byte[] xml = Files.readAllBytes(architecture.resolve("workflows/development-lifecycle.bpmn20.xml"));
        var converter = new BpmnXMLConverter();
        var model = converter.convertToBpmnModel(() -> new ByteArrayInputStream(xml), true, true);
        var processes = model.getProcesses().stream().map(process -> process.getId()).toList();
        assertThat(processes).hasSize(3);
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
        String guide = Files.readString(architecture.resolve("workflow-reference.md"));
        var nodePattern = Pattern.compile("^    (\\w+)(?:\\[|\\{|\\().*?\"([^\"]+)\"", Pattern.MULTILINE);
        var edgePattern = Pattern.compile("^    (\\w+) -->(?:\\|\"([^\"]+)\"\\|)? (\\w+)$", Pattern.MULTILINE);
        Map<String, String> mermaidNodes = new HashMap<>();
        Set<String> mermaidEdges = new HashSet<>();
        nodePattern.matcher(guide).results().forEach(match -> mermaidNodes.put(match.group(1), match.group(2)));
        edgePattern.matcher(guide).results().forEach(match -> mermaidEdges.add(edge(match.group(1), match.group(2), match.group(3))));
        assertThat(mermaidNodes).containsExactlyInAnyOrderEntriesOf(bpmnNodes);
        assertThat(mermaidEdges).containsExactlyInAnyOrderElementsOf(bpmnEdges);
    }
    private static String edge(String source, String label, String target) {
        return source + "\t" + (label == null ? "" : label) + "\t" + target;
    }
}
