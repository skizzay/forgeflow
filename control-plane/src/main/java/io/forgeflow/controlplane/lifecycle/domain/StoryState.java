package io.forgeflow.controlplane.lifecycle.domain;

public enum StoryState implements LifecycleState {
    DRAFT, READY, ACTIVE, PR_OPEN, MERGED, CANCELLED;
    @Override public EntityKind kind() { return EntityKind.STORY; }
}
