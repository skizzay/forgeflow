package io.forgeflow.controlplane.lifecycle.application.port.out;

import io.forgeflow.controlplane.lifecycle.domain.AuthorityFacts;
import io.forgeflow.controlplane.lifecycle.domain.LifecycleEntity;

public interface LifecycleAuthority {
    /** Resolve authorization for the explicit actor authenticated by the inbound adapter and return transactionally current, previously verified
     * approval/SCM facts. Perform remote reconciliation outside the state-lock transaction. */
    AuthorityFacts verify(LifecycleEntity snapshot, String authenticatedActorId);
}
