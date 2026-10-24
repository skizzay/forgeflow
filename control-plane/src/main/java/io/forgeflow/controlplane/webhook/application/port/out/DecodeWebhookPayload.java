package io.forgeflow.controlplane.webhook.application.port.out;

import io.forgeflow.controlplane.webhook.domain.WebhookMetadata;

public interface DecodeWebhookPayload { WebhookMetadata decode(String eventType, byte[] payload); }
