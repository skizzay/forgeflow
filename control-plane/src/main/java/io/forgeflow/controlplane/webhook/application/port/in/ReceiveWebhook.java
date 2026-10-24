package io.forgeflow.controlplane.webhook.application.port.in;

import io.forgeflow.controlplane.webhook.domain.Reception;

public interface ReceiveWebhook { Reception receive(SignedWebhook webhook); }
