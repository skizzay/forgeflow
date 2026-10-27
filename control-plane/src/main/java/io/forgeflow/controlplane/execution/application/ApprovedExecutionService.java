package io.forgeflow.controlplane.execution.application;

import io.forgeflow.controlplane.execution.application.port.in.StartApprovedExecution;
import io.forgeflow.controlplane.execution.application.port.out.ExecutionStore;
import io.forgeflow.controlplane.execution.application.port.out.StartWorkflow;
import io.forgeflow.controlplane.execution.domain.ApprovedWorkflow;
import io.forgeflow.controlplane.execution.domain.ExecutionRun;
import io.forgeflow.controlplane.lifecycle.application.LifecycleService;
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

public final class ApprovedExecutionService implements StartApprovedExecution {
    private final LifecycleRepository lifecycle;
    private final LifecycleTransactions transactions;
    private final ExecutionStore store;
    private final StartWorkflow workflow;
    public ApprovedExecutionService(LifecycleRepository lifecycle, LifecycleTransactions transactions, ExecutionStore store, StartWorkflow workflow) {
        this.lifecycle = lifecycle; this.transactions = transactions; this.store = store; this.workflow = workflow;
    }
    @Override
    public ExecutionRun start(ManualPlan plan) {
        if (!ApprovedWorkflow.KEY.equals(plan.workflowKey()) || plan.workflowVersion() != ApprovedWorkflow.VERSION) {
            throw new LifecycleException(LifecycleException.Kind.IDENTITY_MISMATCH);
        }
        return transactions.required(() -> {
            var epicRef = new EntityRef(EntityKind.EPIC, plan.epicId());
            var storyRef = new EntityRef(EntityKind.STORY, plan.storyId());
            var epic = lifecycle.lock(epicRef);
            String definition = store.requireApproval(plan);
            var story = lifecycle.lock(storyRef);
            if (!plan.identity().equals(epic.approval()) || (epic.state() != EpicState.APPROVED && epic.state() != EpicState.ACTIVE)) {
                throw new LifecycleException(LifecycleException.Kind.IDENTITY_MISMATCH);
            }
            var previous = store.find(plan, definition);
            if (previous.isPresent()) return previous.get();
            // These are guarded parent prerequisites, not a Java activity sequencer.
            String actor = "approved-execution:" + plan.identity().approvalId();
            var transitions = new LifecycleService(lifecycle, transactions, (entity, principal) -> {
                if (!actor.equals(principal) || (!entity.entity().equals(epicRef) && !entity.entity().equals(storyRef))) return AuthorityFacts.unverified();
                return new AuthorityFacts(new ActorAuthority(actor, plan.identity(), false, false, null),
                        new RecoveryEvidence(false, null), new DeliveryEvidence(true, null), new CompletionEvidence(false, null));
            }, Clock.systemUTC());
            transitions.transition(new TransitionCommand(UUID.randomUUID(), epicRef, actor, EpicState.APPROVED, EpicState.ACTIVE,
                    epic.version(), plan.identity(), null, null));
            transitions.transition(new TransitionCommand(UUID.randomUUID(), storyRef, actor, StoryState.DRAFT, StoryState.READY,
                    story.version(), plan.identity(), null, null));
            transitions.transition(new TransitionCommand(UUID.randomUUID(), storyRef, actor, StoryState.READY, StoryState.ACTIVE,
                    story.version() + 1, plan.identity(), null, null));
            var run = new ExecutionRun(UUID.randomUUID(), UUID.randomUUID().toString(), UUID.randomUUID());
            store.insert(plan, definition, run);
            workflow.start(run, definition);
            return run;
        });
    }
}
