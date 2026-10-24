package io.forgeflow.controlplane.lifecycle.domain;

import java.util.UUID;

/** Facts from a trusted authorization/reconciliation adapter, never a request's labels or flags. */
public record AuthorityFacts(String actorId, ApprovalIdentity currentApproval, boolean cancellationAuthorized,
                             boolean recoveryAuthorized, boolean dependenciesMerged, boolean externalEffectsQuiescent,
                             JobState reconciledOutcome, PullRequestObservation pullRequest,
                             boolean integrationChecksPassed, UUID finalHumanApprovalId, UUID authenticatedWorkerId) {
    public static AuthorityFacts unverified() {
        return new AuthorityFacts(null, null, false, false, false, false, null, null, false, null, null);
    }
}
