package io.forgeflow.controlplane.lifecycle.application.port.in;

import io.forgeflow.controlplane.lifecycle.domain.TransitionCommand;
import io.forgeflow.controlplane.lifecycle.domain.TransitionReceipt;
public interface ChangeLifecycle { TransitionReceipt transition(TransitionCommand command); }
