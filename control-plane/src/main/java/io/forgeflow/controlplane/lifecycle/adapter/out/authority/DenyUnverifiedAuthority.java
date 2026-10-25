package io.forgeflow.controlplane.lifecycle.adapter.out.authority;

import io.forgeflow.controlplane.lifecycle.application.port.out.LifecycleAuthority;
import io.forgeflow.controlplane.lifecycle.domain.AuthorityFacts;
import io.forgeflow.controlplane.lifecycle.domain.LifecycleEntity;

/** Fail closed until trusted identity, approval and GitHub reconciliation adapters are implemented. */
public final class DenyUnverifiedAuthority implements LifecycleAuthority {
    @Override public AuthorityFacts verify(LifecycleEntity snapshot, String authenticatedActorId) { return AuthorityFacts.unverified(); }
}
