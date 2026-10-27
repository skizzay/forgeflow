package io.forgeflow.controlplane.lifecycle.domain.approval;

import java.util.Objects;
import java.util.UUID;

/** Server-selected, reviewed inputs for one manual approval; never request-provided authority. */
public record ManualPlan(UUID epicId, UUID storyId, String approver, ApprovalIdentity identity,
                         String workflowKey, int workflowVersion, String definitionSha256) {
    public ManualPlan {
        Objects.requireNonNull(epicId); Objects.requireNonNull(storyId); Objects.requireNonNull(identity);
        if (approver == null || approver.isBlank() || approver.length() > 256 || workflowKey == null
                || !workflowKey.matches("[A-Za-z0-9._:-]{1,128}") || workflowVersion < 1
                || definitionSha256 == null || !definitionSha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Invalid manual approval configuration");
        }
    }
}
