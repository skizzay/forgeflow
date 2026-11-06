package ai.slickflow.controlplane.execution.domain;

import java.util.Objects;
import java.util.UUID;

/** Server-selected routing identity; this is not approval authority. */
public record ApprovedEventTarget(UUID repositoryId, UUID storyId, long installationId, long issueId) {
    public ApprovedEventTarget {
        Objects.requireNonNull(repositoryId);
        Objects.requireNonNull(storyId);
        if (installationId <= 0 || issueId <= 0) throw new IllegalArgumentException("Positive provider identifiers required");
    }
}
