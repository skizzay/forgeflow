package io.forgeflow.controlplane.lifecycle.domain;

import io.forgeflow.controlplane.lifecycle.domain.approval.ApprovalIdentity;

/** Immutable aggregate roots; a successful operation returns the next version. */
public interface LifecycleEntity {
    EntityRef entity();
    LifecycleState state();
    long version();
    ApprovalIdentity approval();

    default void requireExpected(TransitionCommand command) {
        require(entity().equals(command.entity()) && state() == command.expectedState()
                && version() == command.expectedVersion(), LifecycleException.Kind.STALE_STATE);
    }
    static void require(boolean condition, LifecycleException.Kind failure) {
        if (!condition) throw new LifecycleException(failure);
    }
    static void validate(EntityRef entity, LifecycleState state, long version) {
        require(entity != null && state != null && entity.kind() == state.kind() && version >= 0,
                LifecycleException.Kind.INVALID_TRANSITION);
    }
}
