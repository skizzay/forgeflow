package io.forgeflow.controlplane.lifecycle.domain;

public final class LifecycleException extends RuntimeException {
    public enum Kind { INVALID_TRANSITION, STALE_STATE, UNAUTHORIZED, IDENTITY_MISMATCH, EVIDENCE_MISSING, NOT_FOUND, COMMAND_CONFLICT, STORAGE_UNAVAILABLE }
    private final Kind kind;
    public LifecycleException(Kind kind) { super("Lifecycle transition rejected: " + kind); this.kind = kind; }
    public Kind kind() { return kind; }
}
