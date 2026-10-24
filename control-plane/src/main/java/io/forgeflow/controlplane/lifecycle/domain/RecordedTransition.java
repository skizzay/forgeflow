package io.forgeflow.controlplane.lifecycle.domain;

public record RecordedTransition(String requestDigest, String actorId, TransitionReceipt receipt) { }
