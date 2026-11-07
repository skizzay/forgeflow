package ai.slickflow.controlplane.webhook.application.port.in;

import ai.slickflow.controlplane.webhook.domain.Reception;

public interface ReceiveWebhook { Reception receive(SignedWebhook webhook); }
