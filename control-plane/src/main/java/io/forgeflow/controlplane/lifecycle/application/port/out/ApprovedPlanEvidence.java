package io.forgeflow.controlplane.lifecycle.application.port.out;
import io.forgeflow.controlplane.lifecycle.domain.approval.ManualPlan;
public interface ApprovedPlanEvidence {
    /** Verify immutable approval, exact registered content and the one-child plan under the epic lock. */
    String requireDefinition(ManualPlan plan);
}
