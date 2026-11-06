package ai.slickflow.controlplane.execution.application.port.out;
import ai.slickflow.controlplane.execution.domain.ExecutionRun;
public interface StartWorkflow { void start(ExecutionRun run, String definitionId); }
