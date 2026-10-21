package io.forgeflow.spike;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import static org.assertj.core.api.Assertions.*;

class HandoffExperimentTest {
    @TempDir static Path certificates;
    static SSLContext tls;
    // Public test-only keystore password. Private keys and bearer tokens are generated, never committed.
    static final String PASSWORD = "disposable-fixture-only";
    static final ObjectMapper JSON = new ObjectMapper();

    @BeforeAll
    static void generateTestCertificates() throws Exception {
        var command = List.of(Path.of(System.getProperty("java.home"), "bin", "keytool").toString(),
                "-genkeypair", "-alias", "localhost", "-keyalg", "RSA", "-keysize", "2048", "-validity", "2",
                "-dname", "CN=localhost", "-ext", "SAN=DNS:localhost,IP:127.0.0.1", "-storetype", "PKCS12",
                "-keystore", certificates.resolve("server.p12").toString(), "-storepass", PASSWORD);
        var child = new ProcessBuilder(command).redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD).start();
        try {
            assertThat(child.waitFor(20, TimeUnit.SECONDS)).isTrue();
            assertThat(child.exitValue()).isZero();
        } finally { if (child.isAlive()) child.destroyForcibly(); }
        var serverStore = KeyStore.getInstance("PKCS12");
        try (var input = Files.newInputStream(certificates.resolve("server.p12"))) {
            serverStore.load(input, PASSWORD.toCharArray());
        }
        var keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keys.init(serverStore, PASSWORD.toCharArray());
        var trust = KeyStore.getInstance("PKCS12");
        trust.load(null, PASSWORD.toCharArray());
        trust.setCertificateEntry("localhost", serverStore.getCertificate("localhost"));
        try (var output = Files.newOutputStream(certificates.resolve("trust.p12"))) {
            trust.store(output, PASSWORD.toCharArray());
        }
        var managers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        managers.init(trust);
        tls = SSLContext.getInstance("TLS");
        tls.init(keys.getKeyManagers(), managers.getTrustManagers(), null);
    }

    static class Sandbox implements AutoCloseable {
        final ConfigurableApplicationContext context;
        final JdbcTemplate jdbc;
        final RuntimeService runtime;
        final HttpClient client = HttpClient.newBuilder().sslContext(tls).connectTimeout(Duration.ofSeconds(5)).build();
        final String operator = UUID.randomUUID().toString();
        final String worker = UUID.randomUUID().toString();
        final String other = UUID.randomUUID().toString();
        final String incompatible = UUID.randomUUID().toString();
        HandoffApi api;

        Sandbox() throws Exception {
            context = new SpringApplicationBuilder(SpikeApplication.class).run();
            jdbc = context.getBean(JdbcTemplate.class);
            runtime = context.getBean(RuntimeService.class);
            new ResourceDatabasePopulator(new ClassPathResource("recovery-schema.sql"),
                    new ClassPathResource("handoff-schema.sql")).execute(context.getBean(javax.sql.DataSource.class));
            startApi(worker);
        }

        void startApi(String currentWorkerToken) throws Exception {
            if (api != null) api.close();
            api = new HandoffApi(tls, List.of(
                    new HandoffApi.Identity("test-operator", operator, true, Set.of()),
                    new HandoffApi.Identity("worker-a", currentWorkerToken, false, Set.of("linux-amd64")),
                    new HandoffApi.Identity("worker-b", other, false, Set.of("linux-amd64")),
                    new HandoffApi.Identity("worker-c", incompatible, false, Set.of("other-platform"))),
                    context.getBean(ApprovedHandoff.class), context.getBean(RecoveryJobs.class));
        }

        String create(String provider) {
            String key = UUID.randomUUID().toString();
            String variant = provider.equals("deterministic-b") ? "review-first" : "direct";
            String definition = context.getBean(RegisteredWorkflows.class).deploy(variant, "coding-test.v1", provider);
            var instance = runtime.startProcessInstanceById(definition, key, Map.of("planDigest", "plan-1", "configDigest", "config-1"));
            if (variant.equals("review-first")) {
                var tasks = context.getBean(org.flowable.engine.TaskService.class);
                tasks.complete(tasks.createTaskQuery().processInstanceId(instance.getId()).singleResult().getId());
            }
            jdbc.update("INSERT INTO spike_manifest VALUES (?, ?, ?, ?, ?, ?, ?)",
                    key, definition, "plan-1", "config-1", provider, "coding-test.v1", "linux-amd64");
            return key;
        }

        String url(String key, String action) { return "https://localhost:" + api.port() + "/v1/jobs/" + key + "/" + action; }

        HttpResponse<String> post(String key, String action, String token, String body) throws Exception {
            var request = HttpRequest.newBuilder(URI.create(url(key, action))).timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));
            if (token != null) request.header("Authorization", "Bearer " + token);
            return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }

        void approve(String key) throws Exception { assertThat(post(key, "approve", operator, "{}").statusCode()).isEqualTo(200); }

        long claim(String key) throws Exception {
            var response = post(key, "claim", worker, "{}");
            assertThat(response.statusCode()).isEqualTo(200);
            return JSON.readTree(response.body()).path("lease").path("fence").asLong();
        }

        String receipt(long fence) { return "{\"fence\":" + fence + ",\"effectId\":\"fixture-result\"}"; }

        String instance(String key) { return jdbc.queryForObject("SELECT instance_id FROM spike_job WHERE job_key = ?", String.class, key); }

        @Override public void close() { api.close(); client.close(); context.close(); }
    }

    @Test
    void separateJavaWorkerUsesHttpsOnlyAndBothRegisteredProvidersComplete() throws Exception {
        try (var sandbox = new Sandbox()) {
            for (String provider : List.of("deterministic-a", "deterministic-b")) {
                String key = sandbox.create(provider);
                sandbox.approve(key);
                var command = List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                        "-Djavax.net.ssl.trustStore=" + certificates.resolve("trust.p12"),
                        "-Djavax.net.ssl.trustStorePassword=" + PASSWORD, "-cp",
                        System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
                        NetworkWorker.class.getName());
                var builder = new ProcessBuilder(command).redirectOutput(ProcessBuilder.Redirect.DISCARD)
                        .redirectError(ProcessBuilder.Redirect.DISCARD);
                builder.environment().clear();
                builder.environment().putAll(Map.of("SPIKE_API_URL", sandbox.url(key, "").replaceAll("/$", ""),
                        "SPIKE_WORKER_TOKEN", sandbox.worker));
                assertThat(builder.environment()).doesNotContainKey("SPRING_DATASOURCE_URL");
                var child = builder.start();
                try {
                    assertThat(child.waitFor(30, TimeUnit.SECONDS)).isTrue();
                    assertThat(child.exitValue()).isZero();
                } finally { if (child.isAlive()) child.destroyForcibly(); }
                assertThat(sandbox.jdbc.queryForObject("SELECT effect_id FROM spike_job WHERE job_key = ?", String.class, key))
                        .isEqualTo(provider.equals("deterministic-a") ? "fixture-a" : "fixture-b");
                assertThat(sandbox.runtime.createProcessInstanceQuery().processInstanceBusinessKey(key).count()).isZero();
                assertThat(sandbox.jdbc.queryForObject("SELECT approved_by FROM spike_authorization WHERE job_key = ?", String.class, key))
                        .isEqualTo("test-operator");
            }
        }
    }

    @Test
    void authenticationRolesCapabilitiesAndImpersonationAreRejected() throws Exception {
        try (var sandbox = new Sandbox()) {
            String key = sandbox.create("deterministic-a");
            assertThat(sandbox.post(key, "claim", null, "{}").statusCode()).isEqualTo(401);
            assertThat(sandbox.post(key, "claim", "incorrect", "{}").statusCode()).isEqualTo(401);
            assertThat(sandbox.post(key, "approve", sandbox.worker, "{}").statusCode()).isEqualTo(403);
            assertThat(sandbox.post(key, "claim", sandbox.operator, "{}").statusCode()).isEqualTo(403);
            sandbox.approve(key);
            assertThat(sandbox.post(key, "claim", sandbox.incompatible, "{}").statusCode()).isEqualTo(403);
            assertThat(sandbox.post(key, "claim", sandbox.incompatible,
                    "{\"worker\":\"worker-a\",\"capabilities\":[\"linux-amd64\"]}").statusCode()).isEqualTo(400);
            assertThat(sandbox.post(key, "deploy", sandbox.worker, "{\"fence\":1}").statusCode()).isEqualTo(404);
            assertThat(sandbox.jdbc.queryForObject("SELECT state FROM spike_job WHERE job_key = ?", String.class, key)).isEqualTo("READY");
        }
    }

    @Test
    void unapprovedAndChangedManifestOrProcessVariablesCannotAuthorizeDispatch() throws Exception {
        try (var sandbox = new Sandbox()) {
            String unapproved = sandbox.create("deterministic-a");
            sandbox.runtime.setVariable(sandbox.instance(unapproved), "approved", true);
            assertThat(sandbox.post(unapproved, "claim", sandbox.worker, "{}").statusCode()).isEqualTo(409);
            for (String field : List.of("definition_id", "plan_digest", "config_digest", "provider", "activity", "capability")) {
                String key = sandbox.create("deterministic-a");
                sandbox.approve(key);
                // Field names come from the fixed test list, never a request parameter.
                sandbox.jdbc.update("UPDATE spike_manifest SET " + field + " = 'changed' WHERE job_key = ?", key);
                assertThat(sandbox.post(key, "claim", sandbox.worker, "{}").statusCode()).isEqualTo(409);
            }
            String key = sandbox.create("deterministic-a");
            sandbox.approve(key);
            sandbox.runtime.setVariable(sandbox.instance(key), "configDigest", "changed");
            assertThat(sandbox.post(key, "claim", sandbox.worker, "{}").statusCode()).isEqualTo(409);
        }
    }

    @Test
    void heartbeatCompletionAndCancellationEnforceOwnerFenceAndLiveLease() throws Exception {
        try (var sandbox = new Sandbox()) {
            String key = sandbox.create("deterministic-a");
            sandbox.approve(key);
            long fence = sandbox.claim(key);
            for (String action : List.of("heartbeat", "complete", "cancel")) {
                assertThat(sandbox.post(key, action, sandbox.other, sandbox.receipt(fence)).statusCode()).isEqualTo(409);
                assertThat(sandbox.post(key, action, sandbox.worker, sandbox.receipt(fence + 1)).statusCode()).isEqualTo(409);
            }
            sandbox.jdbc.update("UPDATE spike_job SET lease_until = clock_timestamp() + interval '10 seconds' WHERE job_key = ?", key);
            var before = sandbox.jdbc.queryForObject("SELECT lease_until FROM spike_job WHERE job_key = ?", java.sql.Timestamp.class, key);
            assertThat(sandbox.post(key, "heartbeat", sandbox.worker, sandbox.receipt(fence)).statusCode()).isEqualTo(200);
            var after = sandbox.jdbc.queryForObject("SELECT lease_until FROM spike_job WHERE job_key = ?", java.sql.Timestamp.class, key);
            assertThat(after).isAfter(before);
            sandbox.jdbc.update("UPDATE spike_job SET lease_until = clock_timestamp() - interval '1 second' WHERE job_key = ?", key);
            assertThat(sandbox.post(key, "heartbeat", sandbox.worker, sandbox.receipt(fence)).statusCode()).isEqualTo(409);
            assertThat(sandbox.post(key, "complete", sandbox.worker, sandbox.receipt(fence)).statusCode()).isEqualTo(409);
        }
    }

    @Test
    void changedApprovalBlocksActiveWorkerButStillAllowsCancellation() throws Exception {
        try (var sandbox = new Sandbox()) {
            String key = sandbox.create("deterministic-a");
            sandbox.approve(key);
            long fence = sandbox.claim(key);
            sandbox.jdbc.update("UPDATE spike_manifest SET config_digest = 'changed' WHERE job_key = ?", key);
            assertThat(sandbox.post(key, "complete", sandbox.worker, sandbox.receipt(fence)).statusCode()).isEqualTo(409);
            assertThat(sandbox.post(key, "heartbeat", sandbox.worker, sandbox.receipt(fence)).statusCode()).isEqualTo(409);
            assertThat(sandbox.post(key, "cancel", sandbox.worker, sandbox.receipt(fence)).statusCode()).isEqualTo(200);
            assertThat(sandbox.post(key, "complete", sandbox.worker, sandbox.receipt(fence)).statusCode()).isEqualTo(409);
            assertThat(sandbox.jdbc.queryForObject("SELECT state FROM spike_job WHERE job_key = ?", String.class, key)).isEqualTo("CANCELLED");
        }
    }

    @Test
    void certificateTrustAndTokenRotationDoNotChangeLogicalWorkerIdentity() throws Exception {
        try (var sandbox = new Sandbox()) {
            String key = sandbox.create("deterministic-a");
            sandbox.approve(key);
            try (var untrusted = HttpClient.newHttpClient()) {
                assertThatThrownBy(() -> untrusted.send(HttpRequest.newBuilder(URI.create(sandbox.url(key, "claim")))
                        .POST(HttpRequest.BodyPublishers.ofString("{}")).build(), HttpResponse.BodyHandlers.ofString()))
                        .isInstanceOf(javax.net.ssl.SSLHandshakeException.class);
            }
            String rotated = UUID.randomUUID().toString();
            sandbox.startApi(rotated);
            assertThat(sandbox.post(key, "claim", sandbox.worker, "{}").statusCode()).isEqualTo(401);
            var response = sandbox.post(key, "claim", rotated, "{}");
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(JSON.readTree(response.body()).path("lease").path("worker").asText()).isEqualTo("worker-a");
        }
    }

    @Test
    void projectConfigurationCannotRegisterArbitraryExecutableActivities() throws Exception {
        try (var sandbox = new Sandbox()) {
            var registry = sandbox.context.getBean(RegisteredWorkflows.class);
            var repository = sandbox.context.getBean(RepositoryService.class);
            long before = repository.createDeploymentQuery().count();
            assertThatThrownBy(() -> registry.deploy("direct", "${runtimeService}", "deterministic-a"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> registry.deploy("script-task", "coding-test.v1", "deterministic-a"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> registry.deploy("direct", "coding-test.v1", "unregistered-provider"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(repository.createDeploymentQuery().count()).isEqualTo(before);
        }
    }

    @Test
    void networkWorkerCrashAndControlPlaneRestartRequireReconciliationBeforeResume() throws Exception {
        String key;
        long oldFence;
        try (var sandbox = new Sandbox()) {
            key = sandbox.create("deterministic-a");
            sandbox.approve(key);
            var command = List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                    "-Djavax.net.ssl.trustStore=" + certificates.resolve("trust.p12"),
                    "-Djavax.net.ssl.trustStorePassword=" + PASSWORD, "-cp",
                    System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
                    NetworkWorker.class.getName(), "crash-after-heartbeat");
            var builder = new ProcessBuilder(command).redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD);
            builder.environment().clear();
            builder.environment().putAll(Map.of("SPIKE_API_URL", sandbox.url(key, "").replaceAll("/$", ""),
                    "SPIKE_WORKER_TOKEN", sandbox.worker));
            var child = builder.start();
            try {
                assertThat(child.waitFor(30, TimeUnit.SECONDS)).isTrue();
                assertThat(child.exitValue()).isEqualTo(75);
            } finally { if (child.isAlive()) child.destroyForcibly(); }
            oldFence = sandbox.jdbc.queryForObject("SELECT fence FROM spike_job WHERE job_key = ?", Long.class, key);
        }
        try (var sandbox = new Sandbox()) {
            assertThat(sandbox.post(key, "claim", sandbox.worker, "{}").statusCode()).isEqualTo(409);
            sandbox.jdbc.update("UPDATE spike_job SET lease_until = clock_timestamp() - interval '1 second' WHERE job_key = ?", key);
            var recovery = sandbox.context.getBean(RecoveryJobs.class);
            var token = recovery.beginReconciliation(key);
            assertThat(sandbox.post(key, "complete", sandbox.worker, sandbox.receipt(oldFence)).statusCode()).isEqualTo(409);
            // This fixture worker halts before provider invocation, with no outstanding external requests.
            recovery.finishReconciliation(token, new RecoveryJobs.Observation(RecoveryJobs.Finding.ABSENT, null), true);
            long nextFence = sandbox.claim(key);
            assertThat(nextFence).isGreaterThan(oldFence);
            assertThat(sandbox.post(key, "complete", sandbox.worker, sandbox.receipt(nextFence)).statusCode()).isEqualTo(200);
            assertThat(sandbox.runtime.createProcessInstanceQuery().processInstanceBusinessKey(key).count()).isZero();
        }
    }
}
