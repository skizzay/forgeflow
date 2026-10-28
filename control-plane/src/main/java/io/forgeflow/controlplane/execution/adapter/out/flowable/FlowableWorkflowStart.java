package io.forgeflow.controlplane.execution.adapter.out.flowable;
import io.forgeflow.controlplane.execution.application.port.out.StartWorkflow;
import io.forgeflow.controlplane.execution.domain.ExecutionRun;
import io.forgeflow.controlplane.lifecycle.domain.LifecycleException;
import org.flowable.common.engine.api.FlowableException;
import org.flowable.engine.ProcessEngine;
public final class FlowableWorkflowStart implements StartWorkflow {
    private final ProcessEngine engine;
    public FlowableWorkflowStart(ProcessEngine engine) { this.engine = engine; }
    @Override public void start(ExecutionRun run, String definitionId) {
        try {
            engine.getRuntimeService().createProcessInstanceBuilder().processDefinitionId(definitionId)
                    .predefineProcessInstanceId(run.processInstanceId()).businessKey(run.runId().toString())
                    .variable("runId", run.runId().toString()).variable("jobId", run.jobId().toString()).start();
        } catch (FlowableException failure) {
            throw new LifecycleException(LifecycleException.Kind.STORAGE_UNAVAILABLE);
        }
    }
}
