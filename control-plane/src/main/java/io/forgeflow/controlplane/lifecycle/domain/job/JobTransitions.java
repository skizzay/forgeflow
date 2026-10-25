package io.forgeflow.controlplane.lifecycle.domain.job;

import io.forgeflow.controlplane.lifecycle.domain.JobState;
import io.forgeflow.controlplane.lifecycle.domain.LifecycleException;
import io.forgeflow.controlplane.lifecycle.domain.LifecycleState;
import io.forgeflow.controlplane.lifecycle.domain.TransitionPolicy.Guard;

/** Allowed state pairs owned by the job aggregate. */
public final class JobTransitions {
    private JobTransitions() { }
    public static Guard guard(JobState job, LifecycleState to) {
        return switch (job) {
                case PENDING -> to == JobState.RUNNING ? Guard.START_EXECUTION : to == JobState.CANCELLED ? Guard.CANCEL : fail();
                case RUNNING -> to == JobState.SUCCEEDED || to == JobState.FAILED ? Guard.ATTEMPT_RESULT
                        : to == JobState.RECONCILING ? Guard.EXPIRED_LEASE : to == JobState.CANCELLED ? Guard.CANCEL : fail();
                case RECONCILING -> to == JobState.PENDING ? Guard.RECONCILED_RETRY
                        : to == JobState.SUCCEEDED || to == JobState.FAILED ? Guard.RECONCILED_RESULT
                        : to == JobState.CANCELLED ? Guard.CANCEL : fail();
                default -> fail();
            };
    }
    private static Guard fail() { throw new LifecycleException(LifecycleException.Kind.INVALID_TRANSITION); }
}
