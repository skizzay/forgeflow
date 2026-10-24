package io.forgeflow.controlplane.webhook.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("forgeflow.github.webhook")
public record WebhookSettings(boolean enabled, String secret, int maxBytes) {
    public WebhookSettings {
        if (enabled && (secret == null || secret.isBlank())) {
            throw new IllegalArgumentException("Enabled GitHub webhooks require a signing secret");
        }
        if (maxBytes < 1 || maxBytes > 10 * 1024 * 1024) {
            throw new IllegalArgumentException("Webhook max-bytes must be between 1 and 10485760");
        }
    }

    @Override
    public String toString() {
        return "WebhookSettings[enabled=" + enabled + ", secret=REDACTED, maxBytes=" + maxBytes + "]";
    }
}
