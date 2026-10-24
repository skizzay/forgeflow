package io.forgeflow.controlplane;

import io.forgeflow.controlplane.webhook.WebhookInbox;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebhookIntegrationTest {
    private final String user = System.getenv("FORGEFLOW_DB_USER");
    private final String password = System.getenv("FORGEFLOW_DB_PASSWORD");
    private final String secret = UUID.randomUUID().toString();
    private final HttpClient client = HttpClient.newHttpClient();
    private String admin;
    private String database;
    private String url;
    private URI endpoint;
    private ConfigurableApplicationContext context;
    private JdbcTemplate jdbc;
    private static final String ISSUE = """
        {"action":"opened","repository":{"id":10,"private":true},"installation":{"id":20},
         "issue":{"id":30,"number":8,"title":"PRIVATE_TITLE","body":"PRIVATE_BODY"},"token":"DO_NOT_STORE"}
        """;

    @BeforeEach
    void prepare() throws Exception {
        admin = System.getenv("FORGEFLOW_DB_URL");
        assertThat(admin).as("Run with disposable PostgreSQL via scripts/verify-bootstrap.sh").isNotBlank();
        database = "inbox_" + UUID.randomUUID().toString().replace("-", "");
        url = admin.substring(0, admin.lastIndexOf('/') + 1) + database + "?currentSchema=inbox_domain";
        try (var connection = DriverManager.getConnection(admin, user, password); var sql = connection.createStatement()) {
            sql.execute("create database " + database);
        }
        var source = new DriverManagerDataSource(url, user, password);
        DatabaseMigrator.migrate(source, DatabaseSchemas.fromJdbcUrl(url, "inbox_engine"));
        start(false);
    }

    private void start(boolean processing) {
        context = new SpringApplicationBuilder(ControlPlaneApplication.class).web(WebApplicationType.REACTIVE).run(
                "--spring.datasource.url=" + url, "--spring.datasource.username=" + user,
                "--spring.datasource.password=" + password, "--forgeflow.database.engine-schema=inbox_engine",
                "--forgeflow.github.webhook.enabled=true", "--forgeflow.github.webhook.secret=" + secret,
                "--forgeflow.github.webhook.max-bytes=1024", "--forgeflow.github.webhook.processing-enabled=" + processing,
                "--forgeflow.github.webhook.poll-interval=PT0.05S", "--server.port=0", "--logging.level.root=ERROR");
        jdbc = context.getBean(JdbcTemplate.class);
        int port = ((WebServerApplicationContext) context).getWebServer().getPort();
        endpoint = URI.create("http://127.0.0.1:" + port + "/webhooks/github");
    }

    @AfterEach
    void cleanup() throws Exception {
        if (context != null) context.close();
        if (database != null) {
            try (var connection = DriverManager.getConnection(admin, user, password); var sql = connection.createStatement()) {
                sql.execute("drop database if exists " + database + " with (force)");
            }
        }
    }

    private static String signature(String secret, String body) throws Exception {
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }

    private HttpRequest.Builder request(UUID id, String event, String body) throws Exception {
        return HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json").header("X-GitHub-Delivery", id.toString())
                .header("X-GitHub-Event", event).header("X-Hub-Signature-256", signature(secret, body))
                .POST(HttpRequest.BodyPublishers.ofString(body));
    }

    private int send(HttpRequest.Builder request) throws Exception {
        return client.send(request.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    private int count(String table) { return jdbc.queryForObject("select count(*) from " + table, Integer.class); }

    @Test
    void commitsAllowlistedMetadataBeforeAcknowledgingWithoutStoringRawBody() throws Exception {
        UUID id = UUID.randomUUID();
        assertThat(send(request(id, "issues", ISSUE))).isEqualTo(202);
        var row = jdbc.queryForMap("select * from webhook_inbox where delivery_id=?", id);
        assertThat(row).containsEntry("state", "RECEIVED");
        assertThat(row.get("metadata").toString()).contains("repository_id", "installation_id", "subject_number", "opened")
                .doesNotContain("PRIVATE_TITLE", "PRIVATE_BODY", "DO_NOT_STORE", "token");
        assertThat(row.get("payload_sha256")).isEqualTo(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(ISSUE.getBytes(StandardCharsets.UTF_8))));
        assertThat(count("webhook_event")).isZero();
        assertThat(count("job")).isZero();
    }

    @Test
    void rejectsMissingMalformedWrongAndAlteredSignatures() throws Exception {
        for (String invalid : List.of("", "sha1=bad", "sha256=" + "0".repeat(64), signature("wrong-secret", ISSUE))) {
            var request = request(UUID.randomUUID(), "issues", ISSUE).setHeader("X-Hub-Signature-256", invalid);
            assertThat(send(request)).isEqualTo(401);
        }
        var absent = HttpRequest.newBuilder(endpoint).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(ISSUE));
        assertThat(send(absent)).isEqualTo(401);
        assertThat(send(request(UUID.randomUUID(), "issues", ISSUE).POST(HttpRequest.BodyPublishers.ofString(ISSUE + " ")))).isEqualTo(401);
        assertThat(count("webhook_inbox")).isZero();
    }

    @Test
    void rejectsBadHeadersMalformedJsonUnsupportedEncodingAndOversizedStreamingBody() throws Exception {
        assertThat(send(request(UUID.randomUUID(), "issues", ISSUE).setHeader("X-GitHub-Delivery", "bad"))).isEqualTo(400);
        assertThat(send(request(UUID.randomUUID(), "issues", ISSUE).header("X-GitHub-Delivery", UUID.randomUUID().toString()))).isEqualTo(400);
        assertThat(send(request(UUID.randomUUID(), "bad event", ISSUE))).isEqualTo(400);
        for (String invalid : List.of("[]", "{", "{} {}", "{}")) {
            assertThat(send(request(UUID.randomUUID(), "issues", invalid))).isEqualTo(400);
        }
        assertThat(send(request(UUID.randomUUID(), "issues", ISSUE).header("Content-Encoding", "gzip"))).isEqualTo(415);
        String tooLarge = "x".repeat(2048);
        assertThat(send(request(UUID.randomUUID(), "ping", tooLarge))).isEqualTo(413);
        byte[] bytes = tooLarge.getBytes(StandardCharsets.UTF_8);
        assertThat(send(request(UUID.randomUUID(), "ping", tooLarge)
                .POST(HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(bytes))))).isEqualTo(413);
        assertThat(count("webhook_inbox")).isZero();
    }

    @Test
    void concurrentDuplicatesAndReplayAfterRestartCreateOnlyOneLogicalDelivery() throws Exception {
        UUID id = UUID.randomUUID();
        var pending = new ArrayList<CompletableFuture<HttpResponse<Void>>>();
        for (int i = 0; i < 12; i++) pending.add(client.sendAsync(request(id, "issues", ISSUE).build(), HttpResponse.BodyHandlers.discarding()));
        var statuses = pending.stream().map(CompletableFuture::join).map(HttpResponse::statusCode).toList();
        assertThat(statuses).filteredOn(status -> status == 202).hasSize(1);
        assertThat(statuses).filteredOn(status -> status == 200).hasSize(11);
        assertThat(count("webhook_inbox")).isEqualTo(1);
        assertThat(send(request(id, "issues", ISSUE + " "))).isEqualTo(409);
        context.close();
        start(false);
        assertThat(send(request(id, "issues", ISSUE))).isEqualTo(200);
        assertThat(jdbc.queryForObject("select state from webhook_inbox", String.class)).isEqualTo("RECEIVED");
        try (var pool = Executors.newFixedThreadPool(4)) {
            var tasks = new ArrayList<CompletableFuture<Boolean>>();
            for (int i = 0; i < 8; i++) tasks.add(CompletableFuture.supplyAsync(() -> context.getBean(WebhookInbox.class).processOne(), pool));
            assertThat(tasks.stream().map(CompletableFuture::join).filter(Boolean::booleanValue).count()).isEqualTo(1);
        }
        assertThat(count("webhook_event")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select state from webhook_inbox", String.class)).isEqualTo("PROCESSED");
    }

    @Test
    void committedDeliveryWithLostAcknowledgementCanBeReplayed() throws Exception {
        UUID id = UUID.randomUUID();
        String body = "{}";
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8)));
        // Inject the commit-before-response boundary without issuing any HTTP acknowledgement.
        assertThat(context.getBean(WebhookInbox.class).receive(id, "ping", digest, 2, "{}")).isTrue();
        context.close();
        start(false);
        assertThat(send(request(id, "ping", body))).isEqualTo(200);
        assertThat(count("webhook_inbox")).isEqualTo(1);
    }

    @Test
    void persistenceFailureIsNotAcknowledgedAndCanBeRetried() throws Exception {
        UUID id = UUID.randomUUID();
        jdbc.execute("alter table webhook_inbox add constraint inject_failure check (false)");
        assertThat(send(request(id, "ping", "{}"))).isEqualTo(503);
        assertThat(count("webhook_inbox")).isZero();
        jdbc.execute("alter table webhook_inbox drop constraint inject_failure");
        assertThat(send(request(id, "ping", "{}"))).isEqualTo(202);
    }

    @Test
    void processorRollsBackOnFailureAndPublishesOnceAfterRecovery() throws Exception {
        assertThat(send(request(UUID.randomUUID(), "push", "{}"))).isEqualTo(202);
        jdbc.execute("alter table webhook_inbox add constraint inject_failure check (state='RECEIVED')");
        assertThatThrownBy(() -> context.getBean(WebhookInbox.class).processOne()).isInstanceOf(DataAccessException.class);
        assertThat(jdbc.queryForObject("select state from webhook_inbox", String.class)).isEqualTo("RECEIVED");
        assertThat(count("webhook_event")).isZero();
        jdbc.execute("alter table webhook_inbox drop constraint inject_failure");
        assertThat(context.getBean(WebhookInbox.class).processOne()).isTrue();
        assertThat(context.getBean(WebhookInbox.class).processOne()).isFalse();
        assertThat(jdbc.queryForObject("select event_kind from webhook_event", String.class)).isEqualTo("UNSUPPORTED");
    }

    @Test
    void scheduledProcessorResumesPendingDeliveryAfterRestart() throws Exception {
        assertThat(send(request(UUID.randomUUID(), "ping", "{}"))).isEqualTo(202);
        context.close();
        start(true);
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (count("webhook_event") == 0 && System.nanoTime() < deadline) Thread.sleep(20);
        assertThat(jdbc.queryForObject("select event_kind from webhook_event", String.class)).isEqualTo("PING");
        assertThat(jdbc.queryForObject("select state from webhook_inbox", String.class)).isEqualTo("PROCESSED");
    }
}
