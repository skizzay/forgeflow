package io.forgeflow.controlplane.webhook.application.port.out;

public interface VerifyWebhookSignature { boolean valid(String signature, byte[] payload); }
