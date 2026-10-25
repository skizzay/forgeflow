package io.forgeflow.controlplane.lifecycle.domain;

public sealed interface LifecycleState permits JobState, StoryState, EpicState {
    EntityKind kind();
    String name();
    static LifecycleState parse(EntityKind kind, String state) {
        return switch (kind) { case JOB -> JobState.valueOf(state); case STORY -> StoryState.valueOf(state); case EPIC -> EpicState.valueOf(state); };
    }
}
