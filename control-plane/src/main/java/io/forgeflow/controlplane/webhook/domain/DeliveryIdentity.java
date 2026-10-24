package io.forgeflow.controlplane.webhook.domain;

import java.util.UUID;

/** Legacy stored identities can have a null hash and must never match verified replays. */
public record DeliveryIdentity(UUID deliveryId, String eventType, String bodySha256) { }
