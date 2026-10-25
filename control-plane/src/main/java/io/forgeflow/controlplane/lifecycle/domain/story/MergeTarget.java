package io.forgeflow.controlplane.lifecycle.domain.story;

public record MergeTarget(long repositoryId, int pullRequestNumber, String baseBranch, String headSha) {
    public boolean complete() {
        return repositoryId > 0 && pullRequestNumber > 0 && baseBranch != null && !baseBranch.isBlank()
                && headSha != null && headSha.matches("[0-9a-f]{40}([0-9a-f]{24})?");
    }
}
