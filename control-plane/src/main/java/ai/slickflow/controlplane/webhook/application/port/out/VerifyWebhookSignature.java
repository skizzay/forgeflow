package ai.slickflow.controlplane.webhook.application.port.out;

public interface VerifyWebhookSignature { boolean valid(String signature, byte[] payload); }
