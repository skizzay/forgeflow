package io.forgeflow.controlplane.lifecycle.application.port.out;

import io.forgeflow.controlplane.lifecycle.domain.approval.ManualPlan;
import java.time.Instant;
import java.util.UUID;

public interface ManualApprovalStore {
    /** Require the configured story/epic relationship and exact registered workflow content. */
    String requireDefinition(ManualPlan plan);
    /** Immutable, idempotent evidence; must participate in the lifecycle transaction. */
    void record(ManualPlan plan, String definitionId, Instant now);
    /** Bind invalidation and its historical replay to the approved target, not changed configuration. */
    void requireInvalidationTarget(UUID commandId, ManualPlan plan);
}
