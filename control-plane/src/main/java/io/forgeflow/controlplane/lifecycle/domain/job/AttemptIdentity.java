package io.forgeflow.controlplane.lifecycle.domain.job;

import io.forgeflow.controlplane.lifecycle.domain.LifecycleException;
import java.util.UUID;

public record AttemptIdentity(UUID attemptId, UUID workerId, long fencingToken) {
    public AttemptIdentity {
        if (attemptId == null || workerId == null || fencingToken < 1) throw new LifecycleException(LifecycleException.Kind.IDENTITY_MISMATCH);
    }
}
