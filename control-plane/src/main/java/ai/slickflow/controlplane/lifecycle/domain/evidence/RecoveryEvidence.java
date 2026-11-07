package ai.slickflow.controlplane.lifecycle.domain.evidence;

import ai.slickflow.controlplane.lifecycle.domain.JobState;

public record RecoveryEvidence(boolean externalEffectsQuiescent, JobState reconciledOutcome) { }
