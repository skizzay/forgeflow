package ai.slickflow.controlplane.lifecycle.domain.epic;

import ai.slickflow.controlplane.lifecycle.domain.EpicState;
import ai.slickflow.controlplane.lifecycle.domain.LifecycleException;
import ai.slickflow.controlplane.lifecycle.domain.LifecycleState;
import ai.slickflow.controlplane.lifecycle.domain.TransitionPolicy.Guard;

/** Allowed state pairs owned by the epic aggregate. */
public final class EpicTransitions {
    private EpicTransitions() { }
    public static Guard guard(EpicState epic, LifecycleState to) {
        return switch (epic) {
                case DRAFT -> to == EpicState.APPROVED ? Guard.APPROVAL : to == EpicState.CANCELLED ? Guard.CANCEL : fail();
                case APPROVED -> to == EpicState.ACTIVE ? Guard.EPIC_START : to == EpicState.DRAFT ? Guard.INVALIDATE
                        : to == EpicState.CANCELLED ? Guard.CANCEL : fail();
                case ACTIVE -> to == EpicState.COMPLETED ? Guard.EPIC_COMPLETE : to == EpicState.CANCELLED ? Guard.CANCEL : fail();
                default -> fail();
            };
    }
    private static Guard fail() { throw new LifecycleException(LifecycleException.Kind.INVALID_TRANSITION); }
}
