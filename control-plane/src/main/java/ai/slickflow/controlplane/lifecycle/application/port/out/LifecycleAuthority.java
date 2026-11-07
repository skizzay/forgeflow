package ai.slickflow.controlplane.lifecycle.application.port.out;

import ai.slickflow.controlplane.lifecycle.domain.AuthorityFacts;
import ai.slickflow.controlplane.lifecycle.domain.LifecycleEntity;

public interface LifecycleAuthority {
    /** Resolve authorization for the explicit actor authenticated by the inbound adapter and return transactionally current, previously verified
     * approval/SCM facts. Perform remote reconciliation outside the state-lock transaction. */
    AuthorityFacts verify(LifecycleEntity snapshot, String authenticatedActorId);
}
