package io.forgeflow.controlplane.worker.configuration;

import io.forgeflow.controlplane.worker.adapter.in.security.WorkerAuthentication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@EnableConfigurationProperties(WorkerSettings.class)
public class WorkerSecurityConfiguration {
    @Bean
    @Order(-1)
    @ConditionalOnProperty(prefix = "forgeflow.worker", name = "enabled", havingValue = "true")
    SecurityWebFilterChain workerSecurity(ServerHttpSecurity http, WorkerSettings settings) {
        return http.securityMatcher(ServerWebExchangeMatchers.pathMatchers("/api/worker/v1", "/api/worker/v1/**"))
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .requestCache(ServerHttpSecurity.RequestCacheSpec::disable)
                .exceptionHandling(errors -> errors.authenticationEntryPoint(WorkerAuthentication.unauthorized()))
                .addFilterAt(WorkerAuthentication.create(settings.id(), settings.secret()), SecurityWebFiltersOrder.AUTHENTICATION)
                .authorizeExchange(routes -> routes.anyExchange().hasRole("WORKER")).build();
    }
}
