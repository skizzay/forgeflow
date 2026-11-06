package ai.slickflow.controlplane.worker.configuration;

import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("slickflow.worker")
public record WorkerSettings(boolean enabled, UUID id, String subject) {
    public WorkerSettings {
        if (enabled && (id == null || blank(subject))) {
            throw new IllegalArgumentException("Enabled worker access requires ID and subject");
        }
    }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
