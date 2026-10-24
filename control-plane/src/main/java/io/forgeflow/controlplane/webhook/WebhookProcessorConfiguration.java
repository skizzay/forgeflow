package io.forgeflow.controlplane.webhook;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "forgeflow.github.webhook.enabled", havingValue = "true")
@EnableScheduling
class WebhookProcessorConfiguration {
    @Bean
    @ConditionalOnProperty(name = "forgeflow.github.webhook.processing-enabled", havingValue = "true", matchIfMissing = true)
    Processor webhookProcessor(WebhookInbox inbox) { return new Processor(inbox); }

    static class Processor {
        private static final Logger LOG = LoggerFactory.getLogger(Processor.class);
        private final WebhookInbox inbox;
        Processor(WebhookInbox inbox) { this.inbox = inbox; }

        @Scheduled(fixedDelayString = "${forgeflow.github.webhook.poll-interval:PT1S}")
        public void poll() {
            try {
                for (int count = 0; count < 100 && inbox.processOne(); count++) { /* bounded batch */ }
            } catch (RuntimeException failure) {
                // Do not log exception details: SQL/JSON errors may include untrusted payload values.
                LOG.warn("Webhook processing failed; pending delivery retained for retry");
            }
        }
    }
}
