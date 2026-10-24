package io.forgeflow.controlplane.webhook.application.port.out;

import io.forgeflow.controlplane.webhook.domain.DeliveryIdentity;
import io.forgeflow.controlplane.webhook.domain.WebhookDelivery;
import io.forgeflow.controlplane.webhook.domain.WebhookEventKind;
import java.util.Optional;
import java.util.UUID;

/** Invoke within TransactionBoundary; insert/claim must coordinate across instances. */
public interface WebhookInboxRepository {
    boolean insertIfAbsent(WebhookDelivery delivery);
    DeliveryIdentity findIdentity(UUID id);
    /** Lock one verified pending delivery until the current transaction ends. */
    Optional<DeliveryIdentity> claimNext();
    void publish(UUID id, WebhookEventKind kind);
    void markProcessed(UUID id);
}
