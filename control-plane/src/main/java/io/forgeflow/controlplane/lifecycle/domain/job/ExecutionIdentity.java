package io.forgeflow.controlplane.lifecycle.domain.job;

import io.forgeflow.controlplane.lifecycle.domain.LifecycleException;
import io.forgeflow.controlplane.lifecycle.domain.approval.ApprovalIdentity;
import java.util.UUID;

public record ExecutionIdentity(UUID runId, ApprovalIdentity approval, String workflowKey, int workflowVersion,
                                String processDefinitionId, String activityKey, int activityVersion) {
    public ExecutionIdentity {
        if (runId == null || approval == null || workflowKey == null || workflowKey.isBlank() || workflowVersion < 1
                || processDefinitionId == null || processDefinitionId.isBlank() || activityKey == null
                || activityKey.isBlank() || activityVersion < 1) {
            throw new LifecycleException(LifecycleException.Kind.IDENTITY_MISMATCH);
        }
    }
}
