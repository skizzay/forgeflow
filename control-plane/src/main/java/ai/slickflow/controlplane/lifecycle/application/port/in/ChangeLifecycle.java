package ai.slickflow.controlplane.lifecycle.application.port.in;

import ai.slickflow.controlplane.lifecycle.domain.TransitionCommand;
import ai.slickflow.controlplane.lifecycle.domain.TransitionReceipt;
public interface ChangeLifecycle { TransitionReceipt transition(TransitionCommand command); }
