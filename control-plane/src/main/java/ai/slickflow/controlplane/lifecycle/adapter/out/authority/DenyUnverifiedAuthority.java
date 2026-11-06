package ai.slickflow.controlplane.lifecycle.adapter.out.authority;

import ai.slickflow.controlplane.lifecycle.application.port.out.LifecycleAuthority;
import ai.slickflow.controlplane.lifecycle.domain.AuthorityFacts;
import ai.slickflow.controlplane.lifecycle.domain.LifecycleEntity;

/** Fail closed until trusted identity, approval and GitHub reconciliation adapters are implemented. */
public final class DenyUnverifiedAuthority implements LifecycleAuthority {
    @Override public AuthorityFacts verify(LifecycleEntity snapshot, String authenticatedActorId) { return AuthorityFacts.unverified(); }
}
