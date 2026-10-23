package io.forgeflow.controlplane;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication
class HttpSecurityConfiguration {
    @Bean
    org.springframework.security.core.userdetails.UserDetailsService noInteractiveUsers() {
        return username -> { throw new org.springframework.security.core.userdetails.UsernameNotFoundException("No interactive login"); };
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(requests -> requests
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/actuator/health").permitAll()
                .anyRequest().denyAll()).build();
    }
}
