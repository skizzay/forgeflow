package io.forgeflow.controlplane.execution.application.port.out;

import io.forgeflow.controlplane.execution.domain.ExecutionRun;
import io.forgeflow.controlplane.lifecycle.domain.approval.ManualPlan;
import java.util.Optional;

/** Persistence only: callers must verify current approval, hold parent locks and use one outer transaction. */
public interface ExecutionStore {
    /** Reject conflicting pins or an incomplete initial-job correlation instead of treating it as a new run. */
    Optional<ExecutionRun> find(ManualPlan plan, String definitionId);
    void insert(ManualPlan plan, String definitionId, ExecutionRun run);
    void queue(ExecutionRun run, String definitionId, String activityKey, int activityVersion);
}
