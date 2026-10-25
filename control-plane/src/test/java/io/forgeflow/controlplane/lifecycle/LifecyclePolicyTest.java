package io.forgeflow.controlplane.lifecycle;

import io.forgeflow.controlplane.lifecycle.domain.EntityKind;
import io.forgeflow.controlplane.lifecycle.domain.EntityRef;
import io.forgeflow.controlplane.lifecycle.domain.EpicState;
import io.forgeflow.controlplane.lifecycle.domain.JobState;
import io.forgeflow.controlplane.lifecycle.domain.LifecycleException;
import io.forgeflow.controlplane.lifecycle.domain.LifecycleState;
import io.forgeflow.controlplane.lifecycle.domain.StoryState;
import io.forgeflow.controlplane.lifecycle.domain.TransitionCommand;
import io.forgeflow.controlplane.lifecycle.domain.TransitionPolicy;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LifecyclePolicyTest {
    @Test
    void everyUnspecifiedJobTransitionIsRejected() {
        check(JobState.values(), Set.of("PENDING:RUNNING","PENDING:CANCELLED","RUNNING:SUCCEEDED","RUNNING:FAILED",
                "RUNNING:RECONCILING","RUNNING:CANCELLED","RECONCILING:PENDING","RECONCILING:SUCCEEDED","RECONCILING:FAILED","RECONCILING:CANCELLED"));
    }
    @Test
    void everyUnspecifiedStoryTransitionIsRejected() {
        check(StoryState.values(), Set.of("DRAFT:READY","DRAFT:CANCELLED","READY:ACTIVE","READY:DRAFT","READY:CANCELLED",
                "ACTIVE:PR_OPEN","ACTIVE:CANCELLED","PR_OPEN:MERGED","PR_OPEN:CANCELLED"));
    }
    @Test
    void everyUnspecifiedEpicTransitionIsRejected() {
        check(EpicState.values(), Set.of("DRAFT:APPROVED","DRAFT:CANCELLED","APPROVED:ACTIVE","APPROVED:DRAFT",
                "APPROVED:CANCELLED","ACTIVE:COMPLETED","ACTIVE:CANCELLED"));
    }
    @Test
    void crossAggregateStateCommandsAreRejected() {
        assertThatThrownBy(() -> new TransitionCommand(UUID.randomUUID(),new EntityRef(EntityKind.JOB,UUID.randomUUID()),"fixture-operator",
                JobState.RUNNING,StoryState.MERGED,0,null,null,null)).isInstanceOf(LifecycleException.class);
        assertThatThrownBy(() -> TransitionPolicy.guard(JobState.SUCCEEDED,StoryState.MERGED)).isInstanceOf(LifecycleException.class);
    }
    private static void check(LifecycleState[] states,Set<String> allowed) {
        for (var from : states) for (var to : states) {
            if (allowed.contains(from.name()+":"+to.name())) assertThat(TransitionPolicy.guard(from,to)).isNotNull();
            else assertThatThrownBy(() -> TransitionPolicy.guard(from,to)).isInstanceOf(LifecycleException.class);
        }
    }
}
