package ai.slickflow.controlplane.lifecycle.application.port.in;

import ai.slickflow.controlplane.lifecycle.domain.TransitionReceipt;
import ai.slickflow.controlplane.lifecycle.domain.approval.ApprovalIdentity;
import java.util.UUID;

public interface RecordManualApproval {
    enum Action { APPROVE, INVALIDATE }
    /** Actor must come from the authenticated inbound principal. Approval echoes reviewed inputs. */
    record Command(UUID commandId, String actorId, Action action, long expectedVersion,
                   UUID epicId, UUID storyId, ApprovalIdentity approval, String workflowKey, Integer workflowVersion, String definitionSha256) { }
    TransitionReceipt record(Command command);
}
