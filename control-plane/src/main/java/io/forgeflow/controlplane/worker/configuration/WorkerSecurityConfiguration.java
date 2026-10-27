package io.forgeflow.controlplane.worker.configuration;

import io.forgeflow.controlplane.worker.adapter.in.security.WorkerAuthentication;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
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
    SecurityWebFilterChain workerSecurity(ServerHttpSecurity http, WorkerSettings settings) {
        var decoder = NimbusReactiveJwtDecoder.withJwkSetUri(settings.jwkSetUri()).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(settings.issuer()),
                new JwtClaimValidator<List<String>>("aud", audiences ->
                        audiences != null && audiences.contains(settings.audience())),
                new JwtClaimValidator<Object>("exp", expiry -> expiry != null)));
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
