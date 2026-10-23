package io.forgeflow.controlplane;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Mono;

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
class HttpSecurityConfiguration {
    @Bean
    ReactiveUserDetailsService noInteractiveUsers() {
        return username -> Mono.error(new UsernameNotFoundException("No interactive login"));
    }

    @Bean
    SecurityWebFilterChain securityFilterChain(ServerHttpSecurity http) {
        return http.authorizeExchange(requests -> requests
                .pathMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                .anyExchange().denyAll()).build();
    }
}
