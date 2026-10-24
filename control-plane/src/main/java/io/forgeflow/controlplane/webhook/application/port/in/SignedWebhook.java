package io.forgeflow.controlplane.webhook.application.port.in;

import java.util.Objects;

public record SignedWebhook(String deliveryId, String eventType, String signature, byte[] payload) {
    public SignedWebhook { payload = Objects.requireNonNull(payload).clone(); }
    @Override public byte[] payload() { return payload.clone(); }
    @Override public String toString() { return "SignedWebhook[REDACTED]"; }
}
