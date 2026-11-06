package ai.slickflow.controlplane.lifecycle.domain;

public enum JobState implements LifecycleState {
    PENDING, RUNNING, RECONCILING, SUCCEEDED, FAILED, CANCELLED;
    @Override public EntityKind kind() { return EntityKind.JOB; }
}
