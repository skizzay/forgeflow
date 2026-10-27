package io.forgeflow.controlplane.worker.configuration;

import io.forgeflow.controlplane.worker.adapter.in.security.WorkerAuthentication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerProperties;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import reactor.core.publisher.Mono;
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
    SecurityWebFilterChain workerSecurity(ServerHttpSecurity http, WorkerSettings settings,
                                        ReactiveJwtDecoder decoder, OAuth2ResourceServerProperties oauthProperties) {
        var jwtProperties = oauthProperties.getJwt();
        if (jwtProperties.getIssuerUri() == null || jwtProperties.getIssuerUri().isBlank()
                || jwtProperties.getAudiences() == null || jwtProperties.getAudiences().isEmpty()
                || jwtProperties.getAudiences().stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("Worker access requires OAuth2 issuer-uri and audiences");
        }
        return http.securityMatcher(ServerWebExchangeMatchers.pathMatchers("/api/worker/v1", "/api/worker/v1/**"))
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .requestCache(ServerHttpSecurity.RequestCacheSpec::disable)
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtDecoder(decoder)
                        .jwtAuthenticationConverter(token -> Mono.fromCallable(() ->
                                WorkerAuthentication.map(token, settings.subject(), settings.id())))))
                .authorizeExchange(routes -> routes.anyExchange().hasAuthority("SCOPE_forgeflow.worker")).build();
    }
}
