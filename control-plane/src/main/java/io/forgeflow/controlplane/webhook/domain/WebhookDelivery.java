package io.forgeflow.controlplane.webhook.domain;

import java.util.UUID;

public record WebhookDelivery(UUID deliveryId, String eventType, String bodySha256,
                              int payloadBytes, WebhookMetadata metadata) {
    public WebhookDelivery {
        validateEventType(eventType);
        if (deliveryId == null || bodySha256 == null || !bodySha256.matches("[0-9a-f]{64}")
                || payloadBytes < 0 || metadata == null
                || ((eventType.equals("issues") || eventType.equals("pull_request")) && !metadata.hasSubject())) {
            throw new WebhookException(WebhookException.Kind.INVALID_DELIVERY);
        }
    }
    public static void validateEventType(String event) {
        if (event == null || !event.matches("[a-z][a-z0-9_]{0,63}")) {
            throw new WebhookException(WebhookException.Kind.INVALID_DELIVERY);
        }
    }
    public DeliveryIdentity identity() { return new DeliveryIdentity(deliveryId, eventType, bodySha256); }
}
