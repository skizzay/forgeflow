package io.forgeflow.controlplane.lifecycle.domain;

import io.forgeflow.controlplane.lifecycle.domain.epic.EpicTransitions;
import io.forgeflow.controlplane.lifecycle.domain.job.JobTransitions;
import io.forgeflow.controlplane.lifecycle.domain.story.StoryTransitions;

/** Read-only transition-table query; aggregate behavior owns the guards. */
public final class TransitionPolicy {
    public enum Guard { APPROVAL, START_EXECUTION, ATTEMPT_RESULT, EXPIRED_LEASE, RECONCILED_RETRY, RECONCILED_RESULT,
                        STORY_READY, STORY_START, PR_OPEN, PR_MERGE, EPIC_START, EPIC_COMPLETE, INVALIDATE, CANCEL }
    private TransitionPolicy() { }
    public static Guard guard(LifecycleState from, LifecycleState to) {
        if (from.kind() != to.kind() || from == to) throw new LifecycleException(LifecycleException.Kind.INVALID_TRANSITION);
        return switch (from) {
            case JobState job -> JobTransitions.guard(job, to);
            case StoryState story -> StoryTransitions.guard(story, to);
            case EpicState epic -> EpicTransitions.guard(epic, to);
        };
    }
}
