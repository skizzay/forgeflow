package io.forgeflow.controlplane.lifecycle.domain;

public final class TransitionPolicy {
    public enum Guard { APPROVAL, START_EXECUTION, ATTEMPT_RESULT, EXPIRED_LEASE, RECONCILED_RETRY, RECONCILED_RESULT,
                        STORY_READY, STORY_START, PR_OPEN, PR_MERGE, EPIC_START, EPIC_COMPLETE, INVALIDATE, CANCEL }
    private TransitionPolicy() { }
    public static Guard guard(LifecycleState from, LifecycleState to) {
        if (from.kind() != to.kind() || from == to) throw invalid();
        return switch (from) {
            case JobState job -> switch (job) {
                case PENDING -> to == JobState.RUNNING ? Guard.START_EXECUTION : to == JobState.CANCELLED ? Guard.CANCEL : fail();
                case RUNNING -> to == JobState.SUCCEEDED || to == JobState.FAILED ? Guard.ATTEMPT_RESULT
                        : to == JobState.RECONCILING ? Guard.EXPIRED_LEASE : to == JobState.CANCELLED ? Guard.CANCEL : fail();
                case RECONCILING -> to == JobState.PENDING ? Guard.RECONCILED_RETRY
                        : to == JobState.SUCCEEDED || to == JobState.FAILED ? Guard.RECONCILED_RESULT
                        : to == JobState.CANCELLED ? Guard.CANCEL : fail();
                default -> fail();
            };
            case StoryState story -> switch (story) {
                case DRAFT -> to == StoryState.READY ? Guard.STORY_READY : to == StoryState.CANCELLED ? Guard.CANCEL : fail();
                case READY -> to == StoryState.ACTIVE ? Guard.STORY_START : to == StoryState.DRAFT ? Guard.INVALIDATE
                        : to == StoryState.CANCELLED ? Guard.CANCEL : fail();
                case ACTIVE -> to == StoryState.PR_OPEN ? Guard.PR_OPEN : to == StoryState.CANCELLED ? Guard.CANCEL : fail();
                case PR_OPEN -> to == StoryState.MERGED ? Guard.PR_MERGE : to == StoryState.CANCELLED ? Guard.CANCEL : fail();
                default -> fail();
            };
            case EpicState epic -> switch (epic) {
                case DRAFT -> to == EpicState.APPROVED ? Guard.APPROVAL : to == EpicState.CANCELLED ? Guard.CANCEL : fail();
                case APPROVED -> to == EpicState.ACTIVE ? Guard.EPIC_START : to == EpicState.DRAFT ? Guard.INVALIDATE
                        : to == EpicState.CANCELLED ? Guard.CANCEL : fail();
                case ACTIVE -> to == EpicState.COMPLETED ? Guard.EPIC_COMPLETE : to == EpicState.CANCELLED ? Guard.CANCEL : fail();
                default -> fail();
            };
        };
    }
    private static Guard fail() { throw invalid(); }
    private static LifecycleException invalid() { return new LifecycleException(LifecycleException.Kind.INVALID_TRANSITION); }
}
