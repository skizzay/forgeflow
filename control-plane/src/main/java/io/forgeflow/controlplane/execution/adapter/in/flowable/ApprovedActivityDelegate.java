package io.forgeflow.controlplane.execution.adapter.in.flowable;
import io.forgeflow.controlplane.execution.application.port.in.QueueInitialActivity;
import io.forgeflow.controlplane.execution.domain.ExecutionRun;
import java.util.UUID;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
public final class ApprovedActivityDelegate implements JavaDelegate {
    private final QueueInitialActivity activity;
    public ApprovedActivityDelegate(QueueInitialActivity activity) { this.activity = activity; }
    @Override public void execute(DelegateExecution execution) {
        activity.queue(new ExecutionRun(UUID.fromString((String) execution.getVariable("runId")), execution.getProcessInstanceId(),
                UUID.fromString((String) execution.getVariable("jobId"))), execution.getProcessDefinitionId());
    }
}
