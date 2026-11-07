package ai.slickflow.controlplane.webhook.application.port.out;

import ai.slickflow.controlplane.webhook.domain.WebhookMetadata;

public interface DecodeWebhookPayload { WebhookMetadata decode(String eventType, byte[] payload); }
