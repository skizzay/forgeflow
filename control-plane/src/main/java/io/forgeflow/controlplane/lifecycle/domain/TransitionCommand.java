package io.forgeflow.controlplane.lifecycle.domain;

import java.util.Objects;
import java.util.UUID;

public record TransitionCommand(UUID commandId, EntityRef entity, String actorId, LifecycleState expectedState,
                                LifecycleState nextState, long expectedVersion, ApprovalIdentity approval,
                                ExecutionIdentity execution, AttemptIdentity attempt) {
    public TransitionCommand {
        Objects.requireNonNull(commandId); Objects.requireNonNull(entity); Objects.requireNonNull(expectedState); Objects.requireNonNull(nextState);
        if (actorId == null || actorId.isBlank() || actorId.length() > 256) throw new LifecycleException(LifecycleException.Kind.UNAUTHORIZED);
        if (expectedVersion < 0 || expectedState.kind() != entity.kind() || nextState.kind() != entity.kind()) {
            throw new LifecycleException(LifecycleException.Kind.INVALID_TRANSITION);
        }
    }
}
