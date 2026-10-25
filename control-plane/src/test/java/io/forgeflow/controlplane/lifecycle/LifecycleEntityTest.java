package io.forgeflow.controlplane.lifecycle;

import io.forgeflow.controlplane.lifecycle.domain.EntityKind;
import io.forgeflow.controlplane.lifecycle.domain.EntityRef;
import io.forgeflow.controlplane.lifecycle.domain.EpicState;
import io.forgeflow.controlplane.lifecycle.domain.JobState;
import io.forgeflow.controlplane.lifecycle.domain.LifecycleEntity;
import io.forgeflow.controlplane.lifecycle.domain.LifecycleException;
import io.forgeflow.controlplane.lifecycle.domain.LifecycleState;
import io.forgeflow.controlplane.lifecycle.domain.ParentContext;
import io.forgeflow.controlplane.lifecycle.domain.StoryState;
import io.forgeflow.controlplane.lifecycle.domain.TransitionCommand;
import io.forgeflow.controlplane.lifecycle.domain.approval.ApprovalIdentity;
import io.forgeflow.controlplane.lifecycle.domain.epic.Epic;
import io.forgeflow.controlplane.lifecycle.domain.evidence.ActorAuthority;
import io.forgeflow.controlplane.lifecycle.domain.evidence.CompletionEvidence;
import io.forgeflow.controlplane.lifecycle.domain.evidence.DeliveryEvidence;
import io.forgeflow.controlplane.lifecycle.domain.evidence.RecoveryEvidence;
import io.forgeflow.controlplane.lifecycle.domain.job.AttemptEvidence;
import io.forgeflow.controlplane.lifecycle.domain.job.AttemptIdentity;
import io.forgeflow.controlplane.lifecycle.domain.job.ExecutionIdentity;
import io.forgeflow.controlplane.lifecycle.domain.job.Job;
import io.forgeflow.controlplane.lifecycle.domain.story.MergeTarget;
import io.forgeflow.controlplane.lifecycle.domain.story.PullRequestObservation;
import io.forgeflow.controlplane.lifecycle.domain.story.Story;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Business invariants exercised without Spring, a database or repository mocks. */
class LifecycleEntityTest {
    private final ApprovalIdentity approval = new ApprovalIdentity(UUID.randomUUID(), "plan-1", "a".repeat(64));
    private final UUID worker = UUID.randomUUID();
    private final ActorAuthority actor = new ActorAuthority("operator", approval, true, true, worker);
    private final ParentContext parent = new ParentContext(true, approval);
    private final ExecutionIdentity execution = new ExecutionIdentity(UUID.randomUUID(), approval, "story", 1, "definition-1", "develop", 1);
    private final AttemptIdentity attempt = new AttemptIdentity(UUID.randomUUID(), worker, 1);
    private final RecoveryEvidence recovery = new RecoveryEvidence(true, JobState.SUCCEEDED);
    private final DeliveryEvidence dependencies = new DeliveryEvidence(true, null);
    private final MergeTarget target = new MergeTarget(42, 7, "epic/1", "b".repeat(40));

    @Test
    void jobOwnsTransitionsAndReturnsANewVersionWithoutMutatingItsParents() {
        var job = job(JobState.PENDING);
        var running = job.transition(command(job, JobState.RUNNING), actor, recovery, dependencies, new AttemptEvidence(true, true));
        var succeeded = running.transition(command(running, JobState.SUCCEEDED), actor, recovery, dependencies, new AttemptEvidence(true, true));
        assertThat(running).isEqualTo(job); // same entity across immutable versions
        assertThat(running.hashCode()).isEqualTo(job.hashCode());
        assertThat(job(JobState.PENDING)).isNotEqualTo(job);
        assertThat(job.state()).isEqualTo(JobState.PENDING);
        assertThat(job.version()).isZero();
        assertThat(succeeded.state()).isEqualTo(JobState.SUCCEEDED);
        assertThat(succeeded.version()).isEqualTo(2);
        assertThat(succeeded.execution()).isEqualTo(execution);
        assertThat(succeeded.parent()).isEqualTo(parent);
        rejects(() -> succeeded.transition(command(succeeded, JobState.RUNNING), actor, recovery, dependencies,
                new AttemptEvidence(true, true)), LifecycleException.Kind.INVALID_TRANSITION);
    }

    @Test
    void aLeaseObservationCannotSubstituteForAnAuthenticatedOwnerOrMatchingApproval() {
        var job = job(JobState.PENDING);
        var otherWorker = new ActorAuthority("operator", approval, true, true, UUID.randomUUID());
        rejects(() -> job.transition(command(job, JobState.RUNNING), otherWorker, recovery, dependencies,
                new AttemptEvidence(true, true)), LifecycleException.Kind.UNAUTHORIZED);
        var changedApproval = new ApprovalIdentity(UUID.randomUUID(), "plan-2", "c".repeat(64));
        var changedActor = new ActorAuthority("operator", changedApproval, true, true, worker);
        rejects(() -> job.transition(command(job, JobState.RUNNING), changedActor, recovery, dependencies,
                new AttemptEvidence(true, true)), LifecycleException.Kind.IDENTITY_MISMATCH);
    }

    @Test
    void authorizedCancellationSurvivesApprovalRevocationAndRetainsHistoricalIdentity() {
        var job = job(JobState.RUNNING);
        var revoked = new ActorAuthority("operator", null, true, false, null);
        var cancelled = job.transition(command(job, JobState.CANCELLED), revoked,
                new RecoveryEvidence(false, null), new DeliveryEvidence(false, null), new AttemptEvidence(false, true));
        assertThat(cancelled.state()).isEqualTo(JobState.CANCELLED);
        assertThat(cancelled.approval()).isEqualTo(approval);
        assertThat(cancelled.execution()).isEqualTo(execution);
    }

    @Test
    void recoveryComposesQuiescenceAndLeaseEvidenceBeforeReleasingARetry() {
        var job = job(JobState.RECONCILING);
        rejects(() -> job.transition(command(job, JobState.PENDING), actor, new RecoveryEvidence(false, null), dependencies,
                new AttemptEvidence(false, false)), LifecycleException.Kind.EVIDENCE_MISSING);
        rejects(() -> job.transition(command(job, JobState.PENDING), actor, recovery, dependencies,
                new AttemptEvidence(false, true)), LifecycleException.Kind.EVIDENCE_MISSING);
        assertThat(job.transition(command(job, JobState.PENDING), actor, recovery, dependencies,
                new AttemptEvidence(false, false)).state()).isEqualTo(JobState.PENDING);
    }

    @Test
    void storyReadinessPinsApprovalAndExplicitInvalidationClearsIt() {
        var draft = story(StoryState.DRAFT, null);
        rejects(() -> draft.transition(command(draft, StoryState.READY), actor, new DeliveryEvidence(false, null), false),
                LifecycleException.Kind.EVIDENCE_MISSING);
        var ready = draft.transition(command(draft, StoryState.READY), actor, dependencies, false);
        assertThat(ready.approval()).isEqualTo(approval);
        var invalidated = ready.transition(command(ready, StoryState.DRAFT), actor, dependencies, false);
        assertThat(invalidated.approval()).isNull();
        assertThat(invalidated.version()).isEqualTo(2);
        assertThat(ready.approval()).isEqualTo(approval);
    }

    @Test
    void jobSuccessDoesNotAuthorizeStoryMergeAndMergeEvidenceMustMatchTheBinding() {
        var story = story(StoryState.PR_OPEN, approval);
        rejects(() -> story.transition(command(story, StoryState.MERGED), actor, dependencies, true), LifecycleException.Kind.EVIDENCE_MISSING);
        var wrong = new MergeTarget(42, 7, "epic/1", "c".repeat(40));
        rejects(() -> story.transition(command(story, StoryState.MERGED), actor,
                new DeliveryEvidence(true, new PullRequestObservation(wrong, true, "d".repeat(40))), true), LifecycleException.Kind.EVIDENCE_MISSING);
        var observed = new DeliveryEvidence(true, new PullRequestObservation(target, true, "d".repeat(40)));
        assertThat(story.transition(command(story, StoryState.MERGED), actor, observed, false).state()).isEqualTo(StoryState.MERGED);
    }

    @Test
    void epicCompletionRequiresChildrenChecksAndFinalHumanApprovalIndependently() {
        var epic = new Epic(ref(EntityKind.EPIC), EpicState.ACTIVE, 0, approval, true);
        rejects(() -> epic.transition(command(epic, EpicState.COMPLETED), actor, new CompletionEvidence(true, null)), LifecycleException.Kind.EVIDENCE_MISSING);
        rejects(() -> epic.transition(command(epic, EpicState.COMPLETED), actor, new CompletionEvidence(false, UUID.randomUUID())), LifecycleException.Kind.EVIDENCE_MISSING);
        var incomplete = new Epic(epic.entity(), epic.state(), 0, approval, false);
        var evidence = new CompletionEvidence(true, UUID.randomUUID());
        rejects(() -> incomplete.transition(command(incomplete, EpicState.COMPLETED), actor, evidence), LifecycleException.Kind.EVIDENCE_MISSING);
        assertThat(epic.transition(command(epic, EpicState.COMPLETED), actor, evidence).state()).isEqualTo(EpicState.COMPLETED);
    }

    @Test
    void aggregatesRejectCommandsForAnotherIdentityOrVersionWithoutApplicationChecks() {
        var job = job(JobState.PENDING);
        var other = job(JobState.PENDING);
        rejects(() -> job.transition(command(other, JobState.RUNNING), actor, recovery, dependencies,
                new AttemptEvidence(true, true)), LifecycleException.Kind.STALE_STATE);
        var stale = new TransitionCommand(UUID.randomUUID(), job.entity(), "operator", job.state(), JobState.RUNNING,
                1, approval, execution, attempt);
        rejects(() -> job.transition(stale, actor, recovery, dependencies, new AttemptEvidence(true, true)), LifecycleException.Kind.STALE_STATE);
    }

    private Job job(JobState state) { return new Job(ref(EntityKind.JOB), state, 0, approval, execution, parent); }
    private Story story(StoryState state, ApprovalIdentity pin) { return new Story(ref(EntityKind.STORY), state, 0, pin, target, parent); }
    private static EntityRef ref(EntityKind kind) { return new EntityRef(kind, UUID.randomUUID()); }
    private TransitionCommand command(LifecycleEntity entity, LifecycleState next) {
        return new TransitionCommand(UUID.randomUUID(), entity.entity(), "operator", entity.state(), next, entity.version(), approval,
                entity instanceof Job ? execution : null, entity instanceof Job ? attempt : null);
    }
    private static void rejects(Runnable action, LifecycleException.Kind kind) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(LifecycleException.class, failure -> assertThat(failure.kind()).isEqualTo(kind));
    }
}
