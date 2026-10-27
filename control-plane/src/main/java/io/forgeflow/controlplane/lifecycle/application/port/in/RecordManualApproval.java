package io.forgeflow.controlplane.lifecycle.application.port.in;

import io.forgeflow.controlplane.lifecycle.domain.TransitionReceipt;
import io.forgeflow.controlplane.lifecycle.domain.approval.ApprovalIdentity;
import java.util.UUID;

public interface RecordManualApproval {
    enum Action { APPROVE, INVALIDATE }
    /** Actor must come from the authenticated inbound principal. Approval echoes reviewed inputs. */
    record Command(UUID commandId, String actorId, Action action, long expectedVersion,
                   UUID epicId, UUID storyId, ApprovalIdentity approval, String workflowKey, Integer workflowVersion, String definitionSha256) { }
    TransitionReceipt record(Command command);
}
