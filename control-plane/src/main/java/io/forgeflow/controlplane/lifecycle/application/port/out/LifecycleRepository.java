package io.forgeflow.controlplane.lifecycle.application.port.out;

import io.forgeflow.controlplane.lifecycle.domain.ApprovalIdentity;
import io.forgeflow.controlplane.lifecycle.domain.AttemptIdentity;
import io.forgeflow.controlplane.lifecycle.domain.AuthorityFacts;
import io.forgeflow.controlplane.lifecycle.domain.EntityRef;
import io.forgeflow.controlplane.lifecycle.domain.RecordedTransition;
import io.forgeflow.controlplane.lifecycle.domain.StateSnapshot;
import io.forgeflow.controlplane.lifecycle.domain.TransitionCommand;
import io.forgeflow.controlplane.lifecycle.domain.TransitionReceipt;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
public interface LifecycleRepository {
    StateSnapshot lock(EntityRef entity);
    Optional<RecordedTransition> findTransition(UUID commandId);
    boolean ownsLiveAttempt(UUID jobId, AttemptIdentity attempt, Instant now);
    boolean hasLiveAttempt(UUID jobId, Instant now);
    boolean hasSuccessfulJob(UUID storyId, ApprovalIdentity approval);
    /** Update state/pins and append the audit entry within the caller's transaction. */
    void save(StateSnapshot before, TransitionCommand command, AuthorityFacts facts, TransitionReceipt receipt, String digest, Instant now);
}
