package io.forgeflow.controlplane.lifecycle.domain.job;

/** Lease observations made in the state transaction at one captured instant. */
public record AttemptEvidence(boolean ownsLiveAttempt, boolean hasLiveAttempt) { }
