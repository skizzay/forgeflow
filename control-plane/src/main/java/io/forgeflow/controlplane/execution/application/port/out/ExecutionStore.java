package io.forgeflow.controlplane.execution.application.port.out;
import io.forgeflow.controlplane.execution.domain.ExecutionRun;
import io.forgeflow.controlplane.lifecycle.domain.approval.ManualPlan;
import java.util.Optional;
public interface ExecutionStore {
    /** Require immutable approval, exact registered content and a single-story epic under the parent locks. */
    String requireApproval(ManualPlan plan);
    Optional<ExecutionRun> find(ManualPlan plan, String definitionId);
    void insert(ManualPlan plan, String definitionId, ExecutionRun run);
    void queue(ExecutionRun run, String definitionId);
}
