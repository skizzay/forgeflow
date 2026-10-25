package io.forgeflow.controlplane.lifecycle.domain;

import java.util.UUID;
public record TransitionReceipt(UUID commandId, EntityRef entity, LifecycleState from, LifecycleState to, long version) { }
