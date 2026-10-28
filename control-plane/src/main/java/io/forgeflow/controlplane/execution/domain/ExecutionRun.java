package io.forgeflow.controlplane.execution.domain;

import java.util.UUID;

public record ExecutionRun(UUID runId, String processInstanceId, UUID jobId) {}
