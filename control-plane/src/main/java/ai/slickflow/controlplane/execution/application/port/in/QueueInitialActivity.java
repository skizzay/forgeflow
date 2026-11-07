package ai.slickflow.controlplane.execution.application.port.in;
import ai.slickflow.controlplane.execution.domain.ExecutionRun;
/** Only the registered BPMN activity calls this input port. */
public interface QueueInitialActivity { void queue(ExecutionRun run, String definitionId); }
