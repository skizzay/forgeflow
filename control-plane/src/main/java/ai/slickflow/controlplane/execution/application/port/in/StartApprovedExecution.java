package ai.slickflow.controlplane.execution.application.port.in;
import ai.slickflow.controlplane.execution.domain.ExecutionRun;
import ai.slickflow.controlplane.lifecycle.domain.approval.ManualPlan;
/** Trusted control-plane entry; provider event routing belongs in an inbound adapter. */
public interface StartApprovedExecution { ExecutionRun start(ManualPlan plan); }
