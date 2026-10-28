package io.forgeflow.controlplane.lifecycle.application.port.in;
import io.forgeflow.controlplane.lifecycle.domain.approval.ManualPlan;
/** Internal prerequisite operation; returns the verified engine definition ID and starts no workflow. */
public interface PrepareApprovedStory { String prepare(ManualPlan plan); }
