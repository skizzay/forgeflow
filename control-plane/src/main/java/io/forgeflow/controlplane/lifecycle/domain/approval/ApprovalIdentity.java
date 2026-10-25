package io.forgeflow.controlplane.lifecycle.domain.approval;

import io.forgeflow.controlplane.lifecycle.domain.LifecycleException;
import java.util.UUID;

public record ApprovalIdentity(UUID approvalId, String planRevision, String configurationSha256) {
    public ApprovalIdentity {
        if (approvalId == null || planRevision == null || !planRevision.matches("[A-Za-z0-9._:-]{1,128}")
                || configurationSha256 == null || !configurationSha256.matches("[0-9a-f]{64}")) {
            throw new LifecycleException(LifecycleException.Kind.IDENTITY_MISMATCH);
        }
    }
}
