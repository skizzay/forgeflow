package io.forgeflow.controlplane.execution.domain;
import java.util.UUID;
/** A committed correlation receipt, not permission to execute or merge. */
public record ExecutionRun(UUID runId, String processInstanceId, UUID jobId) { }
