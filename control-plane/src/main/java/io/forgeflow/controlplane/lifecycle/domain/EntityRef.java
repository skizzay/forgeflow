package io.forgeflow.controlplane.lifecycle.domain;

import java.util.Objects;
import java.util.UUID;
public record EntityRef(EntityKind kind, UUID id) {
    public EntityRef { Objects.requireNonNull(kind); Objects.requireNonNull(id); }
}
