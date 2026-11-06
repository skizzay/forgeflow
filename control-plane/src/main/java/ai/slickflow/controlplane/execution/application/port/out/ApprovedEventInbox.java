package ai.slickflow.controlplane.execution.application.port.out;

import ai.slickflow.controlplane.execution.domain.ApprovedEventTarget;
import java.util.Optional;
import java.util.UUID;

/** Caller must hold one transaction across claim, approved startup and checkpoint. */
public interface ApprovedEventInbox {
    Optional<UUID> claimNext(ApprovedEventTarget target);
    /** Repeating the same binding is safe; changing it fails closed. */
    void record(UUID deliveryId, UUID runId);
}
