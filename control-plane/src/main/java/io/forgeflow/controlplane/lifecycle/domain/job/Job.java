package io.forgeflow.controlplane.lifecycle.domain.job;

import io.forgeflow.controlplane.lifecycle.domain.EntityRef;
import io.forgeflow.controlplane.lifecycle.domain.JobState;
import io.forgeflow.controlplane.lifecycle.domain.LifecycleEntity;
import io.forgeflow.controlplane.lifecycle.domain.LifecycleException;
import io.forgeflow.controlplane.lifecycle.domain.ParentContext;
import io.forgeflow.controlplane.lifecycle.domain.TransitionCommand;
import io.forgeflow.controlplane.lifecycle.domain.TransitionPolicy;
import io.forgeflow.controlplane.lifecycle.domain.approval.ApprovalIdentity;
import io.forgeflow.controlplane.lifecycle.domain.approval.ApprovalPolicy;
import io.forgeflow.controlplane.lifecycle.domain.evidence.ActorAuthority;
import io.forgeflow.controlplane.lifecycle.domain.evidence.DeliveryEvidence;
import io.forgeflow.controlplane.lifecycle.domain.evidence.RecoveryEvidence;
import java.util.Objects;

import static io.forgeflow.controlplane.lifecycle.domain.LifecycleEntity.require;

/** Immutable entity; equality follows identity across state versions. */
public final class Job implements LifecycleEntity {
    private final EntityRef entity;
    private final JobState state;
    private final long version;
    private final ApprovalIdentity approval;
    private final ExecutionIdentity execution;
    private final ParentContext parent;

    public Job(EntityRef entity, JobState state, long version, ApprovalIdentity approval, ExecutionIdentity execution, ParentContext parent) {
        LifecycleEntity.validate(entity, state, version);
        Objects.requireNonNull(parent);
        this.entity = entity;
        this.state = state;
        this.version = version;
        this.approval = approval;
        this.execution = execution;
        this.parent = parent;
    }

    public EntityRef entity() { return entity; }
    public JobState state() { return state; }
    public long version() { return version; }
    public ApprovalIdentity approval() { return approval; }
    public ExecutionIdentity execution() { return execution; }
    public ParentContext parent() { return parent; }

    @Override
    public boolean equals(Object other) { return other instanceof Job that && entity.equals(that.entity); }
    @Override
    public int hashCode() { return entity.hashCode(); }

    public Job transition(TransitionCommand command, ActorAuthority actor, RecoveryEvidence recovery, DeliveryEvidence delivery, AttemptEvidence attempts) {
        requireExpected(command);
        var guard = JobTransitions.guard(state, command.nextState());
        actor.requireActor(command.actorId());
        if (guard == TransitionPolicy.Guard.CANCEL) {
            require(actor.cancellationAuthorized(), LifecycleException.Kind.UNAUTHORIZED);
        } else if (guard == TransitionPolicy.Guard.EXPIRED_LEASE) {
            ReconciliationPolicy.requireExpired(actor, attempts);
        } else {
            ApprovalPolicy.requireCurrent(command.approval(), approval, actor);
            ApprovalPolicy.requireParent(command.approval(), parent);
            require(execution != null && execution.equals(command.execution()) && execution.approval().equals(command.approval()),
                    LifecycleException.Kind.IDENTITY_MISMATCH);
            switch (guard) {
                case START_EXECUTION -> {
                    require(parent.active() && delivery.dependenciesMerged(), LifecycleException.Kind.EVIDENCE_MISSING);
                    requireOwner(command, actor, attempts);
                }
                case ATTEMPT_RESULT -> {
                    require(parent.active(), LifecycleException.Kind.UNAUTHORIZED);
                    requireOwner(command, actor, attempts);
                }
                case RECONCILED_RETRY -> {
                    require(parent.active(), LifecycleException.Kind.EVIDENCE_MISSING);
                    ReconciliationPolicy.requireQuiescent(actor, recovery, attempts);
                }
                case RECONCILED_RESULT -> {
                    ReconciliationPolicy.requireQuiescent(actor, recovery, attempts);
                    require(recovery.reconciledOutcome() == command.nextState(), LifecycleException.Kind.EVIDENCE_MISSING);
                }
                default -> throw new LifecycleException(LifecycleException.Kind.INVALID_TRANSITION);
            }
        }
        return new Job(entity, (JobState) command.nextState(), version + 1, approval, execution, parent);
    }
    private static void requireOwner(TransitionCommand command, ActorAuthority actor, AttemptEvidence attempts) {
        require(command.attempt() != null && command.attempt().workerId().equals(actor.authenticatedWorkerId())
                && attempts.ownsLiveAttempt(), LifecycleException.Kind.UNAUTHORIZED);
    }
}
