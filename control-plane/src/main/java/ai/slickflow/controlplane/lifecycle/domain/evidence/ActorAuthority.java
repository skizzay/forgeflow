package ai.slickflow.controlplane.lifecycle.domain.evidence;

import ai.slickflow.controlplane.lifecycle.domain.LifecycleException;
import ai.slickflow.controlplane.lifecycle.domain.approval.ApprovalIdentity;
import java.util.UUID;

/** A trusted adapter's authorization decision for an authenticated principal. */
public record ActorAuthority(String actorId, ApprovalIdentity currentApproval, boolean cancellationAuthorized,
                             boolean recoveryAuthorized, UUID authenticatedWorkerId) {
    public void requireActor(String expectedActor) {
        if (actorId == null || !actorId.equals(expectedActor)) {
            throw new LifecycleException(LifecycleException.Kind.UNAUTHORIZED);
        }
    }
}
