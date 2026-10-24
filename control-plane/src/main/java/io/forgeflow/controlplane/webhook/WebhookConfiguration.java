package io.forgeflow.controlplane.webhook;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(WebhookSettings.class)
class WebhookConfiguration { }
