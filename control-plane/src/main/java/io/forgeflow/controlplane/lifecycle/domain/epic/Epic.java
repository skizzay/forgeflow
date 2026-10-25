package io.forgeflow.controlplane.lifecycle.domain.epic;

import io.forgeflow.controlplane.lifecycle.domain.EntityRef;
import io.forgeflow.controlplane.lifecycle.domain.EpicState;
import io.forgeflow.controlplane.lifecycle.domain.LifecycleEntity;
import io.forgeflow.controlplane.lifecycle.domain.LifecycleException;
import io.forgeflow.controlplane.lifecycle.domain.TransitionCommand;
import io.forgeflow.controlplane.lifecycle.domain.TransitionPolicy;
import io.forgeflow.controlplane.lifecycle.domain.approval.ApprovalIdentity;
import io.forgeflow.controlplane.lifecycle.domain.approval.ApprovalPolicy;
import io.forgeflow.controlplane.lifecycle.domain.evidence.ActorAuthority;
import io.forgeflow.controlplane.lifecycle.domain.evidence.CompletionEvidence;

import static io.forgeflow.controlplane.lifecycle.domain.LifecycleEntity.require;

/** Immutable entity; equality follows identity across state versions. */
public final class Epic implements LifecycleEntity {
    private final EntityRef entity;
    private final EpicState state;
    private final long version;
    private final ApprovalIdentity approval;
    private final boolean allStoriesMerged;

    public Epic(EntityRef entity, EpicState state, long version, ApprovalIdentity approval, boolean allStoriesMerged) {
        LifecycleEntity.validate(entity, state, version);
        this.entity = entity;
        this.state = state;
        this.version = version;
        this.approval = approval;
        this.allStoriesMerged = allStoriesMerged;
    }

    public EntityRef entity() { return entity; }
    public EpicState state() { return state; }
    public long version() { return version; }
    public ApprovalIdentity approval() { return approval; }
    public boolean allStoriesMerged() { return allStoriesMerged; }

    @Override
    public boolean equals(Object other) { return other instanceof Epic that && entity.equals(that.entity); }
    @Override
    public int hashCode() { return entity.hashCode(); }

    public Epic transition(TransitionCommand command, ActorAuthority actor, CompletionEvidence completion) {
        requireExpected(command);
        var guard = EpicTransitions.guard(state, command.nextState());
        actor.requireActor(command.actorId());
        ApprovalIdentity nextApproval = approval;
        if (guard == TransitionPolicy.Guard.CANCEL || guard == TransitionPolicy.Guard.INVALIDATE) {
            require(actor.cancellationAuthorized(), LifecycleException.Kind.UNAUTHORIZED);
            if (guard == TransitionPolicy.Guard.INVALIDATE) nextApproval = null;
        } else {
            ApprovalPolicy.requireCurrent(command.approval(), approval, actor);
            if (guard == TransitionPolicy.Guard.EPIC_COMPLETE) {
                require(allStoriesMerged && completion.integrationChecksPassed()
                        && completion.finalHumanApprovalId() != null, LifecycleException.Kind.EVIDENCE_MISSING);
            }
            nextApproval = command.approval();
        }
        return new Epic(entity, (EpicState) command.nextState(), version + 1, nextApproval, allStoriesMerged);
    }
}
