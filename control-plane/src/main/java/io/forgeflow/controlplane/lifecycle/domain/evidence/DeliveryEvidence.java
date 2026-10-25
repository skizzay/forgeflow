package io.forgeflow.controlplane.lifecycle.domain.evidence;

import io.forgeflow.controlplane.lifecycle.domain.story.PullRequestObservation;

public record DeliveryEvidence(boolean dependenciesMerged, PullRequestObservation pullRequest) { }
