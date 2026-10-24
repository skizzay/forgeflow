package io.forgeflow.controlplane.webhook.adapter.in.scheduling;

import io.forgeflow.controlplane.webhook.application.port.in.ProcessWebhook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

public final class WebhookPoller {
    private static final Logger LOG = LoggerFactory.getLogger(WebhookPoller.class);
    private final ProcessWebhook processWebhook;
    public WebhookPoller(ProcessWebhook processWebhook) { this.processWebhook = processWebhook; }
    @Scheduled(fixedDelayString = "${forgeflow.github.webhook.poll-interval:PT1S}")
    public void poll() {
        try {
            for (int count = 0; count < 100 && processWebhook.processOne(); count++) { /* bounded batch */ }
        } catch (RuntimeException failure) {
            LOG.warn("Webhook processing failed; pending delivery retained for retry");
        }
    }
}
