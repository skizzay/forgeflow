package io.forgeflow.controlplane.execution.application;

import io.forgeflow.controlplane.execution.application.port.in.DispatchApprovedEvent;
import io.forgeflow.controlplane.execution.application.port.in.StartApprovedExecution;
import io.forgeflow.controlplane.execution.application.port.out.ApprovedEventInbox;
import io.forgeflow.controlplane.execution.domain.ApprovedEventTarget;
import io.forgeflow.controlplane.lifecycle.application.port.out.LifecycleTransactions;
import io.forgeflow.controlplane.lifecycle.domain.approval.ManualPlan;

public final class ApprovedEventService implements DispatchApprovedEvent {
    private final ApprovedEventInbox inbox;
    private final StartApprovedExecution starter;
    private final LifecycleTransactions transactions;
    private final ApprovedEventTarget target;
    private final ManualPlan plan;

    public ApprovedEventService(ApprovedEventInbox inbox, StartApprovedExecution starter,
            LifecycleTransactions transactions, ApprovedEventTarget target, ManualPlan plan) {
        if (!target.storyId().equals(plan.storyId())) throw new IllegalArgumentException("Story routing must match approved plan");
        this.inbox = inbox; this.starter = starter; this.transactions = transactions;
        this.target = target; this.plan = plan;
    }
    @Override public boolean dispatchOne() {
        return transactions.required(() -> {
            var delivery = inbox.claimNext(target);
            if (delivery.isEmpty()) return false;
            var run = starter.start(plan);
            inbox.record(delivery.get(), run.runId());
            return true;
        });
    }
}
