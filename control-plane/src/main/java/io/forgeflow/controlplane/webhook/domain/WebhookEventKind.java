package io.forgeflow.controlplane.webhook.domain;

public enum WebhookEventKind {
    PING, ISSUE, PULL_REQUEST, UNSUPPORTED;
    public static WebhookEventKind classify(String eventType) {
        return switch (eventType) {
            case "ping" -> PING;
            case "issues" -> ISSUE;
            case "pull_request" -> PULL_REQUEST;
            default -> UNSUPPORTED;
        };
    }
}
