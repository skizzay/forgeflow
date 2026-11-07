package ai.slickflow.controlplane.lifecycle.domain.evidence;

import ai.slickflow.controlplane.lifecycle.domain.story.PullRequestObservation;

public record DeliveryEvidence(boolean dependenciesMerged, PullRequestObservation pullRequest) { }
