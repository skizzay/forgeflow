package io.forgeflow.controlplane;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.csrf.CsrfWebFilter;
import org.springframework.security.web.server.util.matcher.AndServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.NegatedServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;
import reactor.core.publisher.Mono;

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
class HttpSecurityConfiguration {
    @Bean
    ReactiveUserDetailsService noInteractiveUsers() {
        return username -> Mono.error(new UsernameNotFoundException("No interactive login"));
    }

    @Bean
    SecurityWebFilterChain securityFilterChain(ServerHttpSecurity http,
            @Value("${forgeflow.github.webhook.enabled:false}") boolean webhooksEnabled) {
        if (webhooksEnabled) {
            http.csrf(csrf -> csrf.requireCsrfProtectionMatcher(new AndServerWebExchangeMatcher(
                    CsrfWebFilter.DEFAULT_CSRF_MATCHER,
                    new NegatedServerWebExchangeMatcher(ServerWebExchangeMatchers.pathMatchers(HttpMethod.POST, "/webhooks/github")))));
        }
        return http.authorizeExchange(requests -> {
            requests.pathMatchers(HttpMethod.GET, "/actuator/health").permitAll();
            if (webhooksEnabled) requests.pathMatchers(HttpMethod.POST, "/webhooks/github").permitAll();
            requests.anyExchange().denyAll();
        }).build();
    }
}
