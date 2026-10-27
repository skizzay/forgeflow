package io.forgeflow.controlplane.lifecycle.application;

import io.forgeflow.controlplane.lifecycle.application.port.in.RecordManualApproval;
import io.forgeflow.controlplane.lifecycle.application.port.out.LifecycleRepository;
import io.forgeflow.controlplane.lifecycle.application.port.out.LifecycleTransactions;
import io.forgeflow.controlplane.lifecycle.application.port.out.ManualApprovalStore;
import io.forgeflow.controlplane.lifecycle.domain.AuthorityFacts;
import io.forgeflow.controlplane.lifecycle.domain.EntityKind;
import io.forgeflow.controlplane.lifecycle.domain.EntityRef;
import io.forgeflow.controlplane.lifecycle.domain.EpicState;
import io.forgeflow.controlplane.lifecycle.domain.LifecycleException;
import io.forgeflow.controlplane.lifecycle.domain.TransitionCommand;
import io.forgeflow.controlplane.lifecycle.domain.TransitionReceipt;
import io.forgeflow.controlplane.lifecycle.domain.approval.ManualPlan;
import io.forgeflow.controlplane.lifecycle.domain.evidence.ActorAuthority;
import io.forgeflow.controlplane.lifecycle.domain.evidence.CompletionEvidence;
import io.forgeflow.controlplane.lifecycle.domain.evidence.DeliveryEvidence;
import io.forgeflow.controlplane.lifecycle.domain.evidence.RecoveryEvidence;
import java.time.Clock;

public final class ManualApprovalService implements RecordManualApproval {
    private final ManualPlan plan;
    private final LifecycleRepository repository;
    private final LifecycleTransactions transactions;
    private final ManualApprovalStore store;
    private final Clock clock;
    private final LifecycleService lifecycle;

    public ManualApprovalService(ManualPlan plan, LifecycleRepository repository, LifecycleTransactions transactions,
                                 ManualApprovalStore store, Clock clock) {
        this.plan = plan; this.repository = repository; this.transactions = transactions;
        this.store = store; this.clock = clock;
        // Authority is private to this use case; the general lifecycle service still denies unverified callers.
        this.lifecycle = new LifecycleService(repository, transactions, (snapshot, actor) -> {
            if (!snapshot.entity().equals(new EntityRef(EntityKind.EPIC, plan.epicId())) || !plan.approver().equals(actor)) {
                return AuthorityFacts.unverified();
            }
            return new AuthorityFacts(new ActorAuthority(actor, plan.identity(), true, false, null),
                    new RecoveryEvidence(false, null), new DeliveryEvidence(false, null), new CompletionEvidence(false, null));
        }, clock);
    }
    @Override
    public TransitionReceipt record(Command command) {
        if (!plan.approver().equals(command.actorId())) throw new LifecycleException(LifecycleException.Kind.UNAUTHORIZED);
        if (!plan.epicId().equals(command.epicId()) || !plan.storyId().equals(command.storyId())) {
            throw new LifecycleException(LifecycleException.Kind.IDENTITY_MISMATCH);
        }
        if (command.action() == null) throw new LifecycleException(LifecycleException.Kind.INVALID_TRANSITION);
        boolean approve = command.action() == Action.APPROVE;
        if (approve && (!plan.identity().equals(command.approval()) || !plan.definitionSha256().equals(command.definitionSha256())
                || !plan.workflowKey().equals(command.workflowKey()) || !Integer.valueOf(plan.workflowVersion()).equals(command.workflowVersion()))) {
            throw new LifecycleException(LifecycleException.Kind.IDENTITY_MISMATCH);
        }
        var entity = new EntityRef(EntityKind.EPIC, plan.epicId());
        var transition = new TransitionCommand(command.commandId(), entity, command.actorId(),
                approve ? EpicState.DRAFT : EpicState.APPROVED, approve ? EpicState.APPROVED : EpicState.DRAFT,
                command.expectedVersion(), approve ? plan.identity() : null, null, null);
        return transactions.required(() -> {
            repository.lock(entity);
            String definition = approve ? store.requireDefinition(plan) : null;
            var receipt = lifecycle.transition(transition);
            if (approve) store.record(plan, definition, clock.instant());
            else store.requireInvalidationTarget(command.commandId(), plan);
            return receipt;
        });
    }
}
