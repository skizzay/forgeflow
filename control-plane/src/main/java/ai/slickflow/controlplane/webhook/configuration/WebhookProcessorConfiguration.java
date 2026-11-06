package ai.slickflow.controlplane.webhook.configuration;

import ai.slickflow.controlplane.webhook.adapter.in.scheduling.WebhookPoller;
import ai.slickflow.controlplane.webhook.application.port.in.ProcessWebhook;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "slickflow.github.webhook.enabled", havingValue = "true")
@EnableScheduling
class WebhookProcessorConfiguration {
    @Bean
    @ConditionalOnProperty(name = "slickflow.github.webhook.processing-enabled", havingValue = "true", matchIfMissing = true)
    WebhookPoller webhookProcessor(ProcessWebhook processWebhook) { return new WebhookPoller(processWebhook); }
}
