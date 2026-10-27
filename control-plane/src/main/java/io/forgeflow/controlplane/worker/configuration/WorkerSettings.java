package io.forgeflow.controlplane.worker.configuration;

import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("forgeflow.worker")
public record WorkerSettings(boolean enabled, UUID id, String secret) {
    public WorkerSettings {
        if (enabled && (id == null || secret == null || !secret.matches("[A-Za-z0-9_-]{32,256}"))) {
            throw new IllegalArgumentException("Enabled worker access requires an ID and a 32–256 character URL-safe secret");
        }
    }
    @Override
    public String toString() { return "WorkerSettings[enabled=" + enabled + ", id=" + id + ", secret=REDACTED]"; }
}
