package io.forgeflow.controlplane.webhook.application;

import io.forgeflow.controlplane.webhook.application.port.in.ProcessWebhook;
import io.forgeflow.controlplane.webhook.application.port.in.ReceiveWebhook;
import io.forgeflow.controlplane.webhook.application.port.in.SignedWebhook;
import io.forgeflow.controlplane.webhook.application.port.out.DecodeWebhookPayload;
import io.forgeflow.controlplane.webhook.application.port.out.TransactionBoundary;
import io.forgeflow.controlplane.webhook.application.port.out.VerifyWebhookSignature;
import io.forgeflow.controlplane.webhook.application.port.out.WebhookInboxRepository;
import io.forgeflow.controlplane.webhook.domain.Reception;
import io.forgeflow.controlplane.webhook.domain.WebhookDelivery;
import io.forgeflow.controlplane.webhook.domain.WebhookEventKind;
import io.forgeflow.controlplane.webhook.domain.WebhookException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

public final class WebhookService implements ReceiveWebhook, ProcessWebhook {
    private final WebhookInboxRepository inbox;
    private final TransactionBoundary transactions;
    private final VerifyWebhookSignature signatures;
    private final DecodeWebhookPayload payloads;

    public WebhookService(WebhookInboxRepository inbox, TransactionBoundary transactions,
                          VerifyWebhookSignature signatures, DecodeWebhookPayload payloads) {
        this.inbox = inbox; this.transactions = transactions;
        this.signatures = signatures; this.payloads = payloads;
    }

    @Override
    public Reception receive(SignedWebhook command) {
        byte[] body = command.payload();
        if (!signatures.valid(command.signature(), body)) {
            throw new WebhookException(WebhookException.Kind.INVALID_SIGNATURE);
        }
        UUID id;
        try { id = UUID.fromString(command.deliveryId()); }
        catch (IllegalArgumentException | NullPointerException invalid) { throw invalidDelivery(); }
        if (!id.toString().equalsIgnoreCase(command.deliveryId())) throw invalidDelivery();
        WebhookDelivery.validateEventType(command.eventType());
        var delivery = new WebhookDelivery(id, command.eventType(), digest(body), body.length,
                payloads.decode(command.eventType(), body));
        return transactions.required(() -> {
            if (inbox.insertIfAbsent(delivery)) return Reception.CREATED;
            if (!delivery.identity().equals(inbox.findIdentity(id))) {
                throw new WebhookException(WebhookException.Kind.DELIVERY_CONFLICT);
            }
            return Reception.DUPLICATE;
        });
    }

    @Override
    public boolean processOne() {
        return transactions.required(() -> {
            var pending = inbox.claimNext();
            if (pending.isEmpty()) return false;
            var delivery = pending.get();
            inbox.publish(delivery.deliveryId(), WebhookEventKind.classify(delivery.eventType()));
            inbox.markProcessed(delivery.deliveryId());
            return true;
        });
    }

    private static String digest(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException unavailable) { throw new IllegalStateException("SHA-256 unavailable", unavailable); }
    }
    private static WebhookException invalidDelivery() { return new WebhookException(WebhookException.Kind.INVALID_DELIVERY); }
}
