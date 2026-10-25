package io.forgeflow.controlplane.lifecycle.domain.evidence;

import java.util.UUID;

public record CompletionEvidence(boolean integrationChecksPassed, UUID finalHumanApprovalId) { }
