package io.forgeflow.controlplane.worker.configuration;

import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("forgeflow.worker")
public record WorkerSettings(boolean enabled, UUID id, String issuer, String jwkSetUri,
                             String audience, String subject) {
    public WorkerSettings {
        if (enabled && (id == null || blank(issuer) || blank(jwkSetUri) || blank(audience) || blank(subject))) {
            throw new IllegalArgumentException("Enabled worker access requires ID, issuer, JWKS URI, audience and subject");
        }
    }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
