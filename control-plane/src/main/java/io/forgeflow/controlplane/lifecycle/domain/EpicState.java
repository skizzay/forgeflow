package io.forgeflow.controlplane.lifecycle.domain;

public enum EpicState implements LifecycleState {
    DRAFT, APPROVED, ACTIVE, COMPLETED, CANCELLED;
    @Override public EntityKind kind() { return EntityKind.EPIC; }
}
