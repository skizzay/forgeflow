package io.forgeflow.controlplane.webhook.domain;

public final class WebhookException extends RuntimeException {
    public enum Kind { INVALID_SIGNATURE, INVALID_DELIVERY, DELIVERY_CONFLICT, STORAGE_UNAVAILABLE }
    private final Kind kind;
    public WebhookException(Kind kind) { super("Webhook operation rejected: " + kind); this.kind = kind; }
    public Kind kind() { return kind; }
}
