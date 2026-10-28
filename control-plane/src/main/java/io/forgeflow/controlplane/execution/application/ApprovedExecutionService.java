package io.forgeflow.controlplane.execution.application;

import io.forgeflow.controlplane.execution.application.port.in.StartApprovedExecution;
import io.forgeflow.controlplane.execution.application.port.out.ExecutionStore;
import io.forgeflow.controlplane.execution.application.port.out.StartWorkflow;
import io.forgeflow.controlplane.execution.domain.ExecutionRun;
import io.forgeflow.controlplane.lifecycle.application.port.in.PrepareApprovedStory;
import io.forgeflow.controlplane.lifecycle.application.port.out.LifecycleTransactions;
import io.forgeflow.controlplane.lifecycle.domain.LifecycleException;
import io.forgeflow.controlplane.lifecycle.domain.approval.ManualPlan;
import java.util.UUID;
import java.util.function.Supplier;

public final class ApprovedExecutionService implements StartApprovedExecution {
    private final PrepareApprovedStory preparation;
    private final LifecycleTransactions transactions;
    private final ExecutionStore store;
    private final StartWorkflow workflow;
    private final Supplier<UUID> identifiers;

    public ApprovedExecutionService(PrepareApprovedStory preparation, LifecycleTransactions transactions,
                                    ExecutionStore store, StartWorkflow workflow, Supplier<UUID> identifiers) {
        this.preparation = preparation; this.transactions = transactions; this.store = store;
        this.workflow = workflow; this.identifiers = identifiers;
    }
    @Override public ExecutionRun start(ManualPlan plan) {
        if (!"approved-story".equals(plan.workflowKey()) || plan.workflowVersion() != 1) {
            throw new LifecycleException(LifecycleException.Kind.IDENTITY_MISMATCH);
        }
        return transactions.required(() -> {
            String definition = preparation.prepare(plan);
            var previous = store.find(plan, definition);
            if (previous.isPresent()) return previous.get();
            var run = new ExecutionRun(identifiers.get(), identifiers.get().toString(), identifiers.get());
            store.insert(plan, definition, run);
            workflow.start(run, definition);
            return run;
        });
    }
}
