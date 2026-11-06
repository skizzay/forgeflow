package ai.slickflow.controlplane.lifecycle.domain;

import ai.slickflow.controlplane.lifecycle.domain.evidence.ActorAuthority;
import ai.slickflow.controlplane.lifecycle.domain.evidence.CompletionEvidence;
import ai.slickflow.controlplane.lifecycle.domain.evidence.DeliveryEvidence;
import ai.slickflow.controlplane.lifecycle.domain.evidence.RecoveryEvidence;
import java.util.Objects;

/** Composition of trusted adapter observations, never evidence supplied by a request body. */
public record AuthorityFacts(ActorAuthority actor, RecoveryEvidence recovery,
                             DeliveryEvidence delivery, CompletionEvidence completion) {
    public AuthorityFacts {
        Objects.requireNonNull(actor); Objects.requireNonNull(recovery);
        Objects.requireNonNull(delivery); Objects.requireNonNull(completion);
    }
    public static AuthorityFacts unverified() {
        return new AuthorityFacts(new ActorAuthority(null, null, false, false, null),
                new RecoveryEvidence(false, null), new DeliveryEvidence(false, null), new CompletionEvidence(false, null));
    }
}
