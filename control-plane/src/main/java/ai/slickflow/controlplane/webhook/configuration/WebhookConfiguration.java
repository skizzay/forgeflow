package ai.slickflow.controlplane.webhook.configuration;

import ai.slickflow.controlplane.webhook.application.WebhookService;
import ai.slickflow.controlplane.webhook.adapter.out.persistence.JdbcWebhookInbox;
import ai.slickflow.controlplane.webhook.adapter.out.persistence.SpringTransactionBoundary;
import ai.slickflow.controlplane.webhook.adapter.out.security.HmacWebhookSignature;
import ai.slickflow.controlplane.webhook.adapter.out.json.JacksonWebhookPayload;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(WebhookSettings.class)
class WebhookConfiguration {
    @Bean
    @ConditionalOnProperty(name = "slickflow.github.webhook.enabled", havingValue = "true")
    WebhookService webhookService(JdbcTemplate jdbc, PlatformTransactionManager transactions, WebhookSettings settings) {
        return new WebhookService(new JdbcWebhookInbox(jdbc), new SpringTransactionBoundary(transactions),
                new HmacWebhookSignature(settings.secret()), new JacksonWebhookPayload());
    }
}
