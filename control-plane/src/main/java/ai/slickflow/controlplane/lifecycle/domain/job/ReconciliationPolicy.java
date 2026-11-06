package ai.slickflow.controlplane.lifecycle.domain.job;

import ai.slickflow.controlplane.lifecycle.domain.LifecycleException;
import ai.slickflow.controlplane.lifecycle.domain.evidence.ActorAuthority;
import ai.slickflow.controlplane.lifecycle.domain.evidence.RecoveryEvidence;

import static ai.slickflow.controlplane.lifecycle.domain.LifecycleEntity.require;

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
