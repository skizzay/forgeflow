package ai.slickflow.controlplane.lifecycle.domain.story;

import ai.slickflow.controlplane.lifecycle.domain.EntityRef;
import ai.slickflow.controlplane.lifecycle.domain.LifecycleEntity;
import ai.slickflow.controlplane.lifecycle.domain.LifecycleException;
import ai.slickflow.controlplane.lifecycle.domain.ParentContext;
import ai.slickflow.controlplane.lifecycle.domain.StoryState;
import ai.slickflow.controlplane.lifecycle.domain.TransitionCommand;
import ai.slickflow.controlplane.lifecycle.domain.TransitionPolicy;
import ai.slickflow.controlplane.lifecycle.domain.approval.ApprovalIdentity;
import ai.slickflow.controlplane.lifecycle.domain.approval.ApprovalPolicy;
import ai.slickflow.controlplane.lifecycle.domain.evidence.ActorAuthority;
import ai.slickflow.controlplane.lifecycle.domain.evidence.DeliveryEvidence;
import java.util.Objects;

import static ai.slickflow.controlplane.lifecycle.domain.LifecycleEntity.require;

/** Immutable entity; equality follows identity across state versions. */
public final class Story implements LifecycleEntity {
    private final EntityRef entity;
    private final StoryState state;
    private final long version;
    private final ApprovalIdentity approval;
    private final MergeTarget mergeTarget;
    private final ParentContext parent;

    public Story(EntityRef entity, StoryState state, long version, ApprovalIdentity approval, MergeTarget mergeTarget, ParentContext parent) {
        LifecycleEntity.validate(entity, state, version);
        Objects.requireNonNull(parent);
        this.entity = entity;
        this.state = state;
        this.version = version;
        this.approval = approval;
        this.mergeTarget = mergeTarget;
        this.parent = parent;
    }

    public EntityRef entity() { return entity; }
    public StoryState state() { return state; }
    public long version() { return version; }
    public ApprovalIdentity approval() { return approval; }
    public MergeTarget mergeTarget() { return mergeTarget; }
    public ParentContext parent() { return parent; }

    @Override
    public boolean equals(Object other) { return other instanceof Story that && entity.equals(that.entity); }
    @Override
    public int hashCode() { return entity.hashCode(); }

    public Story transition(TransitionCommand command, ActorAuthority actor, DeliveryEvidence delivery, boolean successfulJob) {
        requireExpected(command);
        var guard = StoryTransitions.guard(state, command.nextState());
        actor.requireActor(command.actorId());
        ApprovalIdentity nextApproval = approval;
        if (guard == TransitionPolicy.Guard.CANCEL || guard == TransitionPolicy.Guard.INVALIDATE) {
            require(actor.cancellationAuthorized(), LifecycleException.Kind.UNAUTHORIZED);
            if (guard == TransitionPolicy.Guard.INVALIDATE) nextApproval = null;
        } else {
            ApprovalPolicy.requireCurrent(command.approval(), approval, actor);
            ApprovalPolicy.requireParent(command.approval(), parent);
            switch (guard) {
                case STORY_READY, STORY_START -> require(parent.active() && delivery.dependenciesMerged(), LifecycleException.Kind.EVIDENCE_MISSING);
                case PR_OPEN -> {
                    require(successfulJob, LifecycleException.Kind.EVIDENCE_MISSING);
                    requirePullRequest(delivery, false);
                }
                case PR_MERGE -> requirePullRequest(delivery, true);
                default -> throw new LifecycleException(LifecycleException.Kind.INVALID_TRANSITION);
            }
            nextApproval = command.approval();
        }
        return new Story(entity, (StoryState) command.nextState(), version + 1, nextApproval, mergeTarget, parent);
    }
    private void requirePullRequest(DeliveryEvidence delivery, boolean merged) {
        var observation = delivery.pullRequest();
        require(observation != null && observation.confirms(mergeTarget, merged), LifecycleException.Kind.EVIDENCE_MISSING);
    }
}
