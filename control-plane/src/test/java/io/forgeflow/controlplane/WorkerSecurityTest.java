package io.forgeflow.controlplane;

import io.forgeflow.controlplane.worker.configuration.WorkerSecurityConfiguration;
import io.forgeflow.controlplane.worker.configuration.WorkerSettings;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RequestPredicates.POST;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

class WorkerSecurityTest {
    private final UUID worker = UUID.randomUUID();
    private final String secret = UUID.randomUUID().toString();
    private final ReactiveWebApplicationContextRunner runner = new ReactiveWebApplicationContextRunner()
            .withUserConfiguration(HttpSecurityConfiguration.class, WorkerSecurityConfiguration.class, Probe.class);

    private ReactiveWebApplicationContextRunner enabled(String credential) {
        return runner.withPropertyValues("forgeflow.worker.enabled=true", "forgeflow.worker.id=" + worker,
                "forgeflow.worker.secret=" + credential);
    }
    @Test
    void bindsConfiguredPrincipalWithoutTrustingBodyOrCreatingASession() {
        enabled(secret).run(context -> {
            var client = WebTestClient.bindToApplicationContext(context).build();
            client.post().uri("/api/worker/v1/probe").header(HttpHeaders.AUTHORIZATION, "Bearer " + secret)
                    .bodyValue("{\"workerId\":\"" + UUID.randomUUID() + "\",\"actorId\":\"operator\"}")
                    .exchange().expectStatus().isOk().expectHeader().doesNotExist(HttpHeaders.SET_COOKIE)
                    .expectBody(String.class).isEqualTo(worker.toString());
            client.get().uri("/api/worker/v1/probe").exchange().expectStatus().isUnauthorized()
                    .expectHeader().valueEquals(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                    .expectBody().json("{\"commandId\":null,\"code\":\"UNAUTHENTICATED\",\"action\":\"STOP\"}");
            client.get().uri("/actuator/health").exchange().expectStatus().isOk();
            client.get().uri("/operator/probe").header(HttpHeaders.AUTHORIZATION, "Bearer " + secret)
                    .exchange().expectStatus().isUnauthorized();
            client.get().uri("/api/worker/v2/probe").header(HttpHeaders.AUTHORIZATION, "Bearer " + secret)
                    .exchange().expectStatus().isUnauthorized();
            client.post().uri("/operator/probe").header(HttpHeaders.AUTHORIZATION, "Bearer " + secret)
                    .exchange().expectStatus().isForbidden();
        });
    }
    @Test
    void rejectsWrongMissingMalformedAndRepeatedCredentials() {
        enabled(secret).run(context -> {
            var client = WebTestClient.bindToApplicationContext(context).build();
            for (String header : new String[]{"Bearer " + UUID.randomUUID(), "Basic ignored", "Bearer short", "Bearer " + secret + " extra"}) {
                client.post().uri("/api/worker/v1/probe").header(HttpHeaders.AUTHORIZATION, header)
                        .exchange().expectStatus().isUnauthorized();
            }
            client.post().uri("/api/worker/v1/probe").bodyValue("{\"workerId\":\"" + worker + "\"}")
                    .exchange().expectStatus().isUnauthorized();
            client.post().uri("/api/worker/v1/probe").header(HttpHeaders.AUTHORIZATION, "Bearer " + secret, "Bearer " + secret)
                    .exchange().expectStatus().isUnauthorized();
        });
    }
    @Test
    void replacingCredentialRevokesOldAccessButPreservesIdentity() {
        String replacement = UUID.randomUUID().toString();
        enabled(replacement).run(context -> {
            var client = WebTestClient.bindToApplicationContext(context).build();
            client.get().uri("/api/worker/v1/probe").header(HttpHeaders.AUTHORIZATION, "Bearer " + secret)
                    .exchange().expectStatus().isUnauthorized();
            client.get().uri("/api/worker/v1/probe").header(HttpHeaders.AUTHORIZATION, "Bearer " + replacement)
                    .exchange().expectStatus().isOk().expectBody(String.class).isEqualTo(worker.toString());
        });
    }
    @Test
    void disabledAccessAndInvalidEnabledConfigurationFailClosed() {
        runner.run(context -> WebTestClient.bindToApplicationContext(context).build().get()
                .uri("/api/worker/v1/probe").header(HttpHeaders.AUTHORIZATION, "Bearer " + secret)
                .exchange().expectStatus().isUnauthorized());
        enabled("short").run(context -> assertThat(context).hasFailed());
        assertThatThrownBy(() -> new WorkerSettings(true, null, secret)).isInstanceOf(IllegalArgumentException.class);
        assertThat(new WorkerSettings(true, worker, secret).toString()).contains("REDACTED").doesNotContain(secret);
    }
    @Configuration(proxyBeanMethods = false)
    @EnableWebFlux
    @EnableWebFluxSecurity
    static class Probe {
        @Bean
        RouterFunction<ServerResponse> probes() {
            return route(POST("/api/worker/v1/probe").or(GET("/api/worker/v1/probe")), request ->
                    request.principal().flatMap(principal -> ServerResponse.ok().bodyValue(principal.getName())))
                    .andRoute(GET("/actuator/health"), request -> ServerResponse.ok().bodyValue("UP"))
                    .andRoute(GET("/operator/probe").or(POST("/operator/probe")), request -> ServerResponse.ok().build());
        }
    }
}
