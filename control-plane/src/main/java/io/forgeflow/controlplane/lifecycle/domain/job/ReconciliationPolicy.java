package io.forgeflow.controlplane.lifecycle.domain.job;

import io.forgeflow.controlplane.lifecycle.domain.LifecycleException;
import io.forgeflow.controlplane.lifecycle.domain.evidence.ActorAuthority;
import io.forgeflow.controlplane.lifecycle.domain.evidence.RecoveryEvidence;

import static io.forgeflow.controlplane.lifecycle.domain.LifecycleEntity.require;

/** Recovery combines operator authority, external-effect observations and local leases. */
public final class ReconciliationPolicy {
    private ReconciliationPolicy() { }
    public static void requireExpired(ActorAuthority actor, AttemptEvidence attempts) {
        require(actor.recoveryAuthorized() && !attempts.hasLiveAttempt(), LifecycleException.Kind.UNAUTHORIZED);
    }
    public static void requireQuiescent(ActorAuthority actor, RecoveryEvidence recovery, AttemptEvidence attempts) {
        require(actor.recoveryAuthorized() && recovery.externalEffectsQuiescent() && !attempts.hasLiveAttempt(),
                LifecycleException.Kind.EVIDENCE_MISSING);
    }
}
