package ai.slickflow.controlplane.execution.application.port.in;

public interface DispatchApprovedEvent {
    /** Returns false when no matching uncheckpointed delivery exists. */
    boolean dispatchOne();
}
