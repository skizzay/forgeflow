package io.forgeflow.controlplane;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import io.forgeflow.controlplane.worker.configuration.WorkerSecurityConfiguration;
import io.forgeflow.controlplane.worker.configuration.WorkerSettings;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
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

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RequestPredicates.POST;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

class WorkerSecurityTest {
    private final UUID worker = UUID.randomUUID();
    private final RSAKey key = new RSAKeyGenerator(2048).keyID("test").generate();
    private final HttpServer keys = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    private final ReactiveWebApplicationContextRunner runner = new ReactiveWebApplicationContextRunner()
            .withUserConfiguration(HttpSecurityConfiguration.class, WorkerSecurityConfiguration.class, Probe.class);

    WorkerSecurityTest() throws Exception {
        keys.createContext("/jwks", exchange -> {
            byte[] body = new JWKSet(key.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        keys.start();
    }
    @AfterEach
    void stopKeys() { keys.stop(0); }

    private ReactiveWebApplicationContextRunner enabled() {
        return runner.withPropertyValues("forgeflow.worker.enabled=true", "forgeflow.worker.id=" + worker,
                "forgeflow.worker.issuer=https://issuer.example", "forgeflow.worker.audience=forgeflow",
                "forgeflow.worker.subject=controller", "forgeflow.worker.jwk-set-uri=http://127.0.0.1:"
                        + keys.getAddress().getPort() + "/jwks");
    }
    private String token(RSAKey signingKey, String issuer, String audience, String subject,
                         String scope, Instant expiry) throws JOSEException {
        var claims = new JWTClaimsSet.Builder().issuer(issuer).audience(audience).subject(subject)
                .claim("scope", scope).notBeforeTime(Date.from(Instant.now().minusSeconds(5)));
        if (expiry != null) claims.expirationTime(Date.from(expiry));
        var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test").build(), claims.build());
        jwt.sign(new RSASSASigner(signingKey));
        return jwt.serialize();
    }
    private String valid() throws JOSEException {
        return token(key, "https://issuer.example", "forgeflow", "controller", "forgeflow.worker", Instant.now().plusSeconds(300));
    }
    @Test
    void bindsExternalIdentityWithoutTrustingBodyOrCreatingSession() throws Exception {
        String accessToken = valid();
        enabled().run(context -> {
            var client = WebTestClient.bindToApplicationContext(context).build();
            client.post().uri("/api/worker/v1/probe").headers(h -> h.setBearerAuth(accessToken))
                    .bodyValue("{\"workerId\":\"" + UUID.randomUUID() + "\",\"actorId\":\"operator\"}")
                    .exchange().expectStatus().isOk().expectHeader().doesNotExist(HttpHeaders.SET_COOKIE)
                    .expectBody(String.class).isEqualTo(worker.toString());
            client.get().uri("/actuator/health").exchange().expectStatus().isOk();
            for (String path : new String[]{"/operator/probe", "/api/worker/v2/probe"}) {
                client.get().uri(path).headers(h -> h.setBearerAuth(accessToken)).exchange().expectStatus().isUnauthorized();
            }
            client.post().uri("/operator/probe").headers(h -> h.setBearerAuth(accessToken))
                    .exchange().expectStatus().isForbidden();
        });
    }
    @Test
    void validatesSignatureIssuerAudienceExpiryAndSubject() throws Exception {
        var otherKey = new RSAKeyGenerator(2048).generate();
        String[] invalid = {
            token(otherKey, "https://issuer.example", "forgeflow", "controller", "forgeflow.worker", Instant.now().plusSeconds(300)),
            token(key, "https://other.example", "forgeflow", "controller", "forgeflow.worker", Instant.now().plusSeconds(300)),
            token(key, "https://issuer.example", "other", "controller", "forgeflow.worker", Instant.now().plusSeconds(300)),
            token(key, "https://issuer.example", "forgeflow", "controller", "forgeflow.worker", Instant.now().minusSeconds(300)),
            token(key, "https://issuer.example", "forgeflow", "controller", "forgeflow.worker", null),
            token(key, "https://issuer.example", "forgeflow", "other", "forgeflow.worker", Instant.now().plusSeconds(300))
        };
        enabled().run(context -> {
            var client = WebTestClient.bindToApplicationContext(context).build();
            for (String value : invalid) {
                client.get().uri("/api/worker/v1/probe").headers(h -> h.setBearerAuth(value))
                        .exchange().expectStatus().isUnauthorized();
            }
        });
    }
    @Test
    void requiresWorkerScopeRatherThanHumanOrOtherAuthority() throws Exception {
        String token = token(key, "https://issuer.example", "forgeflow", "controller", "admin approval", Instant.now().plusSeconds(300));
        enabled().run(context -> WebTestClient.bindToApplicationContext(context).build().get()
                .uri("/api/worker/v1/probe").headers(h -> h.setBearerAuth(token)).exchange().expectStatus().isForbidden());
    }
    @Test
    void rejectsAbsentAndMalformedBearerCredentials() {
        enabled().run(context -> {
            var client = WebTestClient.bindToApplicationContext(context).build();
            client.post().uri("/api/worker/v1/probe").bodyValue("{\"workerId\":\"" + worker + "\"}")
                    .exchange().expectStatus().isUnauthorized().expectHeader().exists(HttpHeaders.WWW_AUTHENTICATE);
            for (String value : new String[]{"Bearer invalid", "Basic ignored", "Bearer extra token"}) {
                client.get().uri("/api/worker/v1/probe").header(HttpHeaders.AUTHORIZATION, value)
                        .exchange().expectStatus().isUnauthorized();
            }
        });
    }
    @Test
    void disabledAndIncompleteConfigurationFailClosed() {
        runner.run(context -> WebTestClient.bindToApplicationContext(context).build().get()
                .uri("/api/worker/v1/probe").exchange().expectStatus().isUnauthorized());
        runner.withPropertyValues("forgeflow.worker.enabled=true").run(context -> assertThat(context).hasFailed());
        assertThatThrownBy(() -> new WorkerSettings(true, worker, "issuer", "jwks", "aud", ""))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test
    void workerPrincipalStaysIndependentOfIdentityProviderAndSecurityFramework() {
        var domain = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("io.forgeflow.controlplane.worker.domain");
        classes().should().onlyDependOnClassesThat()
                .resideInAnyPackage("java..", "io.forgeflow.controlplane.worker.domain..")
                .check(domain);
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
