package io.forgeflow.controlplane.webhook.domain;

import java.util.UUID;

/** Delivery deduplication identity: delivery UUID, event type and exact-body hash. */
public record DeliveryIdentity(UUID deliveryId, String eventType, String bodySha256) { }
