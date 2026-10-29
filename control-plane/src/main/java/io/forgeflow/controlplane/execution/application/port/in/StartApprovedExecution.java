package io.forgeflow.controlplane.execution.application.port.in;
import io.forgeflow.controlplane.execution.domain.ExecutionRun;
import io.forgeflow.controlplane.lifecycle.domain.approval.ManualPlan;
/** Trusted control-plane entry; provider event routing belongs in an inbound adapter. */
public interface StartApprovedExecution { ExecutionRun start(ManualPlan plan); }
