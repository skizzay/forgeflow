package io.forgeflow.controlplane.lifecycle.domain;

public record PullRequestObservation(MergeTarget target, boolean merged, String mergeCommitSha) {
    public boolean confirms(MergeTarget expected, boolean requireMerge) {
        return expected != null && expected.complete() && expected.equals(target)
                && (!requireMerge || (merged && mergeCommitSha != null && mergeCommitSha.matches("[0-9a-f]{40}([0-9a-f]{24})?")));
    }
}
