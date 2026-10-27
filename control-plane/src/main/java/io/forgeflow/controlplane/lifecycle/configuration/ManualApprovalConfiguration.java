package io.forgeflow.controlplane.lifecycle.configuration;

import io.forgeflow.controlplane.lifecycle.adapter.out.persistence.JdbcLifecycleRepository;
import io.forgeflow.controlplane.lifecycle.adapter.out.persistence.JdbcManualApprovalStore;
import io.forgeflow.controlplane.lifecycle.adapter.out.persistence.SpringLifecycleTransactions;
import io.forgeflow.controlplane.lifecycle.application.ManualApprovalService;
import io.forgeflow.controlplane.lifecycle.application.port.in.RecordManualApproval;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import reactor.core.publisher.Mono;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "forgeflow.manual-approval", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(ManualApprovalSettings.class)
public class ManualApprovalConfiguration {
    @Bean
    RecordManualApproval recordManualApproval(ManualApprovalSettings settings, JdbcTemplate jdbc, PlatformTransactionManager manager,
                                              OAuth2ResourceServerProperties properties) {
        return new ManualApprovalService(settings.plan(properties.getJwt().getIssuerUri()), new JdbcLifecycleRepository(jdbc),
                new SpringLifecycleTransactions(manager), new JdbcManualApprovalStore(jdbc), Clock.systemUTC());
    }
    @Bean @Order(-2)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
    SecurityWebFilterChain manualApprovalSecurity(ServerHttpSecurity http, ManualApprovalSettings settings,
                                                ReactiveJwtDecoder decoder, OAuth2ResourceServerProperties properties) {
        var jwt = properties.getJwt();
        if (jwt.getIssuerUri() == null || jwt.getIssuerUri().isBlank() || jwt.getAudiences() == null
                || jwt.getAudiences().isEmpty() || jwt.getAudiences().stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("Manual approval requires OAuth2 issuer-uri and audiences");
        }
        return http.securityMatcher(ServerWebExchangeMatchers.pathMatchers("/api/operator/v1/plan-approval", "/api/operator/v1/plan-approval/**"))
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .csrf(ServerHttpSecurity.CsrfSpec::disable).httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable).logout(ServerHttpSecurity.LogoutSpec::disable)
                .requestCache(ServerHttpSecurity.RequestCacheSpec::disable)
                .oauth2ResourceServer(oauth -> oauth.jwt(tokens -> tokens.jwtDecoder(decoder)
                        .jwtAuthenticationConverter(token -> Mono.just(new JwtAuthenticationToken(token,
                                new JwtGrantedAuthoritiesConverter().convert(token), token.getIssuer() + " " + token.getSubject())))))
                .authorizeExchange(routes -> routes.anyExchange().access((authentication, context) -> authentication.map(actor ->
                        new AuthorizationDecision(actor instanceof JwtAuthenticationToken token && token.getToken().getExpiresAt() != null
                                && settings.subject().equals(token.getToken().getSubject()) && actor.getAuthorities().stream()
                                .anyMatch(authority -> authority.getAuthority().equals("SCOPE_forgeflow.plan.approve")))))).build();
    }
}
