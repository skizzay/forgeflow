package io.forgeflow.controlplane.lifecycle.domain.story;

import io.forgeflow.controlplane.lifecycle.domain.LifecycleException;
import io.forgeflow.controlplane.lifecycle.domain.LifecycleState;
import io.forgeflow.controlplane.lifecycle.domain.StoryState;
import io.forgeflow.controlplane.lifecycle.domain.TransitionPolicy.Guard;

/** Allowed state pairs owned by the story aggregate. */
public final class StoryTransitions {
    private StoryTransitions() { }
    public static Guard guard(StoryState story, LifecycleState to) {
        return switch (story) {
                case DRAFT -> to == StoryState.READY ? Guard.STORY_READY : to == StoryState.CANCELLED ? Guard.CANCEL : fail();
                case READY -> to == StoryState.ACTIVE ? Guard.STORY_START : to == StoryState.DRAFT ? Guard.INVALIDATE
                        : to == StoryState.CANCELLED ? Guard.CANCEL : fail();
                case ACTIVE -> to == StoryState.PR_OPEN ? Guard.PR_OPEN : to == StoryState.CANCELLED ? Guard.CANCEL : fail();
                case PR_OPEN -> to == StoryState.MERGED ? Guard.PR_MERGE : to == StoryState.CANCELLED ? Guard.CANCEL : fail();
                default -> fail();
            };
    }
    private static Guard fail() { throw new LifecycleException(LifecycleException.Kind.INVALID_TRANSITION); }
}
