package io.forgeflow.controlplane.lifecycle.application;

import io.forgeflow.controlplane.lifecycle.application.port.in.PrepareApprovedStory;
import io.forgeflow.controlplane.lifecycle.application.port.out.ApprovedPlanEvidence;
import io.forgeflow.controlplane.lifecycle.application.port.out.LifecycleRepository;
import io.forgeflow.controlplane.lifecycle.application.port.out.LifecycleTransactions;
import io.forgeflow.controlplane.lifecycle.domain.AuthorityFacts;
import io.forgeflow.controlplane.lifecycle.domain.EntityKind;
import io.forgeflow.controlplane.lifecycle.domain.EntityRef;
import io.forgeflow.controlplane.lifecycle.domain.EpicState;
import io.forgeflow.controlplane.lifecycle.domain.LifecycleException;
import io.forgeflow.controlplane.lifecycle.domain.StoryState;
import io.forgeflow.controlplane.lifecycle.domain.TransitionCommand;
import io.forgeflow.controlplane.lifecycle.domain.approval.ManualPlan;
import io.forgeflow.controlplane.lifecycle.domain.evidence.ActorAuthority;
import io.forgeflow.controlplane.lifecycle.domain.evidence.CompletionEvidence;
import io.forgeflow.controlplane.lifecycle.domain.evidence.DeliveryEvidence;
import io.forgeflow.controlplane.lifecycle.domain.evidence.RecoveryEvidence;
import java.time.Clock;
import java.util.UUID;
import java.util.function.Supplier;

public final class ApprovedStoryPreparation implements PrepareApprovedStory {
    private final LifecycleRepository repository;
    private final LifecycleTransactions transactions;
    private final ApprovedPlanEvidence evidence;
    private final Clock clock;
    private final Supplier<UUID> commandIds;
    public ApprovedStoryPreparation(LifecycleRepository repository, LifecycleTransactions transactions, ApprovedPlanEvidence evidence, Clock clock, Supplier<UUID> commandIds) {
        this.repository = repository; this.transactions = transactions; this.evidence = evidence; this.clock = clock; this.commandIds = commandIds;
    }
    @Override public String prepare(ManualPlan plan) {
        return transactions.required(() -> {
            var epicRef = new EntityRef(EntityKind.EPIC, plan.epicId());
            var storyRef = new EntityRef(EntityKind.STORY, plan.storyId());
            var epic = repository.lock(epicRef);
            String definition = evidence.requireDefinition(plan);
            var story = repository.lock(storyRef);
            if (!plan.identity().equals(epic.approval())) throw new LifecycleException(LifecycleException.Kind.IDENTITY_MISMATCH);
            if (epic.state() == EpicState.ACTIVE && story.state() == StoryState.ACTIVE && plan.identity().equals(story.approval())) return definition;
            String actor = "approved-preparation:" + plan.identity().approvalId();
            var transitions = new LifecycleService(repository, transactions, (entity, principal) -> {
                if (!actor.equals(principal) || (!entity.entity().equals(epicRef) && !entity.entity().equals(storyRef))) return AuthorityFacts.unverified();
                return new AuthorityFacts(new ActorAuthority(actor, plan.identity(), false, false, null),
                        new RecoveryEvidence(false, null), new DeliveryEvidence(true, null), new CompletionEvidence(false, null));
            }, clock);
            transitions.transition(new TransitionCommand(commandIds.get(), epicRef, actor, EpicState.APPROVED, EpicState.ACTIVE,
                    epic.version(), plan.identity(), null, null));
            transitions.transition(new TransitionCommand(commandIds.get(), storyRef, actor, StoryState.DRAFT, StoryState.READY,
                    story.version(), plan.identity(), null, null));
            transitions.transition(new TransitionCommand(commandIds.get(), storyRef, actor, StoryState.READY, StoryState.ACTIVE,
                    story.version() + 1, plan.identity(), null, null));
            return definition;
        });
    }
}
