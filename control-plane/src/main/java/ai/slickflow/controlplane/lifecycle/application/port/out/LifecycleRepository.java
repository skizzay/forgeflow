package ai.slickflow.controlplane.lifecycle.application.port.out;

import ai.slickflow.controlplane.lifecycle.domain.AuthorityFacts;
import ai.slickflow.controlplane.lifecycle.domain.EntityRef;
import ai.slickflow.controlplane.lifecycle.domain.LifecycleEntity;
import ai.slickflow.controlplane.lifecycle.domain.RecordedTransition;
import ai.slickflow.controlplane.lifecycle.domain.TransitionCommand;
import ai.slickflow.controlplane.lifecycle.domain.TransitionReceipt;
import ai.slickflow.controlplane.lifecycle.domain.approval.ApprovalIdentity;
import ai.slickflow.controlplane.lifecycle.domain.job.AttemptIdentity;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface LifecycleRepository {
    LifecycleEntity lock(EntityRef entity);
    Optional<RecordedTransition> findTransition(UUID commandId);
    boolean ownsLiveAttempt(UUID jobId, AttemptIdentity attempt, Instant now);
    boolean hasLiveAttempt(UUID jobId, Instant now);
    boolean hasSuccessfulJob(UUID storyId, ApprovalIdentity approval);
    /** Persist the aggregate result with optimistic version checking and append its audit entry
     * within the caller's transaction. Parent locks remain held through commit. */
    void save(LifecycleEntity before, LifecycleEntity after, TransitionCommand command, AuthorityFacts facts, TransitionReceipt receipt, String digest, Instant now);
}
