package io.forgeflow.controlplane.execution.application;
import io.forgeflow.controlplane.execution.application.port.in.QueueInitialActivity;
import io.forgeflow.controlplane.execution.application.port.out.ExecutionStore;
import io.forgeflow.controlplane.execution.domain.ExecutionRun;
import io.forgeflow.controlplane.lifecycle.application.port.out.LifecycleTransactions;
public final class InitialActivityService implements QueueInitialActivity {
    private final ExecutionStore store;
    private final LifecycleTransactions transactions;
    public InitialActivityService(ExecutionStore store, LifecycleTransactions transactions) { this.store = store; this.transactions = transactions; }
    @Override public void queue(ExecutionRun run, String definitionId) {
        transactions.required(() -> { store.queue(run, definitionId, "develop-story", 1); return null; });
    }
}
