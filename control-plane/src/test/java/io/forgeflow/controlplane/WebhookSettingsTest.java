package io.forgeflow.controlplane;

import io.forgeflow.controlplane.webhook.WebhookSettings;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebhookSettingsTest {
    @Test
    void disabledFeatureNeedsNoSecretAndEnabledSettingsRedactIt() {
        assertThat(new WebhookSettings(false, "", 1024).enabled()).isFalse();
        assertThat(new WebhookSettings(true, "synthetic-fixture-secret", 1024).toString())
                .contains("REDACTED").doesNotContain("synthetic-fixture-secret");
    }

    @Test
    void refusesEnabledBlankSecretAndInvalidBodyLimits() {
        assertThatThrownBy(() -> new WebhookSettings(true, "", 1024)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WebhookSettings(false, "", 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WebhookSettings(false, "", 10485761)).isInstanceOf(IllegalArgumentException.class);
    }
}
