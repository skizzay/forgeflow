package io.forgeflow.controlplane.lifecycle.domain.evidence;

import io.forgeflow.controlplane.lifecycle.domain.JobState;

public record RecoveryEvidence(boolean externalEffectsQuiescent, JobState reconciledOutcome) { }
