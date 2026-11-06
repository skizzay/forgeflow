package ai.slickflow.controlplane.lifecycle.application.port.in;
import ai.slickflow.controlplane.lifecycle.domain.approval.ManualPlan;
/** Internal prerequisite operation; returns the verified engine definition ID and starts no workflow. */
public interface PrepareApprovedStory { String prepare(ManualPlan plan); }
