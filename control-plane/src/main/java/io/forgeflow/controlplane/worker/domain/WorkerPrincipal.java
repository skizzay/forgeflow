package io.forgeflow.controlplane.worker.domain;

import java.security.Principal;
import java.util.UUID;

/** Identity normalized from a validated external identity, never from a request body. */
public record WorkerPrincipal(UUID workerId) implements Principal {
    @Override
    public String getName() { return workerId.toString(); }
}
