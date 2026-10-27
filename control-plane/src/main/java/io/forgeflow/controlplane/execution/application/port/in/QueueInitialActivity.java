package io.forgeflow.controlplane.execution.application.port.in;
import io.forgeflow.controlplane.execution.domain.ExecutionRun;
/** Only the registered BPMN activity calls this input port. */
public interface QueueInitialActivity { void queue(ExecutionRun run, String definitionId); }
