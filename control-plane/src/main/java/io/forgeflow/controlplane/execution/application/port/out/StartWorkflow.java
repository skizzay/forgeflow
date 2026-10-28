package io.forgeflow.controlplane.execution.application.port.out;
import io.forgeflow.controlplane.execution.domain.ExecutionRun;
public interface StartWorkflow { void start(ExecutionRun run, String definitionId); }
