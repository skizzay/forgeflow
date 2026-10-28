package io.forgeflow.controlplane;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import io.forgeflow.controlplane.lifecycle.application.port.in.ChangeLifecycle;
import io.forgeflow.controlplane.lifecycle.domain.EntityKind;
import io.forgeflow.controlplane.lifecycle.domain.EntityRef;
import io.forgeflow.controlplane.lifecycle.domain.EpicState;
import io.forgeflow.controlplane.lifecycle.domain.TransitionCommand;
import io.forgeflow.controlplane.lifecycle.domain.approval.ApprovalIdentity;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.List;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.flowable.engine.ProcessEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ManualApprovalIntegrationTest {
    private static final String PATH = "/api/operator/v1/plan-approval";
    private final UUID repo = UUID.randomUUID(), epic = UUID.randomUUID(), story = UUID.randomUUID();
    private UUID approvalId = UUID.randomUUID();
    private String configuration = "b".repeat(64), approver = "human-operator";
    private String admin, database, url, definitionHash, definitionId;
    private final String user = System.getenv("FORGEFLOW_DB_USER"), password = System.getenv("FORGEFLOW_DB_PASSWORD");
    private HttpServer keys;
    private RSAKey key;
    private ConfigurableApplicationContext context;
    private JdbcTemplate jdbc;
    private WebTestClient client;

    @BeforeEach
    void prepare() throws Exception {
        admin = System.getenv("FORGEFLOW_DB_URL");
        assertThat(admin).as("Use disposable PostgreSQL via scripts/verify-bootstrap.sh").isNotBlank();
        database = "approval_" + UUID.randomUUID().toString().replace("-", "");
        url = admin.substring(0, admin.lastIndexOf('/') + 1) + database + "?currentSchema=approval_domain";
        try (var connection = DriverManager.getConnection(admin, user, password); var sql = connection.createStatement()) {
            sql.execute("create database " + database);
        }
        var source = new DriverManagerDataSource(url, user, password);
        DatabaseMigrator.migrate(source, DatabaseSchemas.fromJdbcUrl(url, "approval_engine"));
        key = new RSAKeyGenerator(2048).keyID("test").generate();
        keys = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        keys.createContext("/jwks", exchange -> {
            byte[] body = new JWKSet(key.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        keys.start();
        // Bootstrap registry exists before approval is enabled; no workflow instance is started.
        start(false);
        definitionHash = jdbc.queryForObject("select content_sha256 from workflow_definition", String.class);
        definitionId = jdbc.queryForObject("select definition_id from workflow_definition", String.class);
        jdbc.update("insert into repository(id,github_id,full_name) values (?,42,'public/approval-fixture')", repo);
        jdbc.update("insert into epic(id,repository_id,issue_number) values (?,?,1)", epic, repo);
        jdbc.update("insert into story(id,epic_id,repository_id,issue_number) values (?,?,?,2)", story, epic, repo);
        start(true);
    }
    private void start(boolean enabled) {
        if (context != null) context.close();
        context = new SpringApplicationBuilder(ControlPlaneApplication.class).web(WebApplicationType.REACTIVE).run(
                "--server.port=0", "--spring.datasource.url=" + url, "--spring.datasource.username=" + user,
                "--spring.datasource.password=" + password, "--forgeflow.database.engine-schema=approval_engine",
                "--logging.level.root=ERROR", "--forgeflow.manual-approval.enabled=" + enabled,
                "--forgeflow.manual-approval.epic-id=" + epic, "--forgeflow.manual-approval.story-id=" + story,
                "--forgeflow.manual-approval.subject=" + approver, "--forgeflow.manual-approval.approval-id=" + approvalId,
                "--forgeflow.manual-approval.plan-revision=plan-v1", "--forgeflow.manual-approval.configuration-sha256=" + configuration,
                "--forgeflow.manual-approval.workflow-key=bootstrap", "--forgeflow.manual-approval.workflow-version=1",
                "--forgeflow.manual-approval.definition-sha256=" + (definitionHash == null ? "a".repeat(64) : definitionHash),
                "--spring.security.oauth2.resourceserver.jwt.issuer-uri=https://identity.example",
                "--spring.security.oauth2.resourceserver.jwt.audiences=forgeflow",
                "--spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://127.0.0.1:" + keys.getAddress().getPort() + "/jwks");
        jdbc = context.getBean(JdbcTemplate.class);
        client = WebTestClient.bindToServer().baseUrl("http://127.0.0.1:"
                + ((WebServerApplicationContext) context).getWebServer().getPort()).build();
    }
    @AfterEach
    void cleanup() throws Exception {
        if (context != null) context.close();
        if (keys != null) keys.stop(0);
        if (database != null) try (var connection = DriverManager.getConnection(admin, user, password); var sql = connection.createStatement()) {
            sql.execute("drop database if exists " + database + " with (force)");
        }
    }
    private String token(String subject, String scope) throws Exception {
        var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test").build(),
                new JWTClaimsSet.Builder().issuer("https://identity.example").audience("forgeflow").subject(subject)
                        .expirationTime(Date.from(Instant.now().plusSeconds(300))).claim("scope", scope).build());
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }
    private Map<String, Object> request(UUID command, long version) {
        return Map.of("commandId", command, "expectedVersion", version, "epicId", epic, "storyId", story, "approval", identity(), "workflowKey", "bootstrap",
                "workflowVersion", 1, "definitionSha256", definitionHash);
    }
    private ApprovalIdentity identity() { return new ApprovalIdentity(approvalId, "plan-v1", configuration); }
    private WebTestClient.ResponseSpec post(String path, String token, Object body) {
        return client.post().uri(path).headers(headers -> headers.setBearerAuth(token)).bodyValue(body).exchange();
    }
    private long count(String table) { return jdbc.queryForObject("select count(*) from " + table, Long.class); }
    private void untouched() {
        assertThat(jdbc.queryForObject("select state from epic", String.class)).isEqualTo("DRAFT");
        assertThat(count("manual_plan_approval")).isZero();
        assertThat(count("lifecycle_transition")).isZero();
    }
    @Test
    void authenticatedHumanScopeAndSubjectAreBothRequired() throws Exception {
        var body = request(UUID.randomUUID(), 0);
        client.post().uri(PATH).bodyValue(body).exchange().expectStatus().isUnauthorized();
        for (String token : new String[]{token(approver, "forgeflow.worker"), token("worker-controller", "forgeflow.plan.approve"),
                token("other-human", "forgeflow.plan.approve")}) {
            post(PATH, token, body).expectStatus().isForbidden();
        }
        var forged = new HashMap<>(body);
        forged.put("actorId", approver);
        post(PATH, token(approver, "forgeflow.plan.approve"), forged).expectStatus().isBadRequest();
        untouched();
    }
    @Test
    void recordsExactIdentityAndHistoricalReplayAcrossRestartWithoutDispatch() throws Exception {
        var command = UUID.randomUUID();
        var body = request(command, 0);
        post(PATH, token(approver, "forgeflow.plan.approve"), body).expectStatus().isOk()
                .expectHeader().doesNotExist("Set-Cookie").expectBody().jsonPath("$.state").isEqualTo("APPROVED")
                .jsonPath("$.version").isEqualTo(1);
        var row = jdbc.queryForMap("select * from manual_plan_approval");
        assertThat(row).containsEntry("approval_id", approvalId).containsEntry("story_id", story)
                .containsEntry("epic_id", epic).containsEntry("configuration_sha256", configuration)
                .containsEntry("process_definition_id", definitionId).containsEntry("definition_sha256", definitionHash)
                .containsEntry("approved_by", "https://identity.example " + approver);
        Object approvedAt = row.get("approved_at");
        start(true);
        post(PATH, token(approver, "forgeflow.plan.approve"), body).expectStatus().isOk().expectBody().jsonPath("$.version").isEqualTo(1);
        assertThat(count("manual_plan_approval")).isEqualTo(1);
        assertThat(count("lifecycle_transition")).isEqualTo(1);
        assertThat(jdbc.queryForMap("select * from manual_plan_approval").get("approved_at")).isEqualTo(approvedAt);
        assertThat(jdbc.queryForObject("select state from story", String.class)).isEqualTo("DRAFT");
        assertThat(count("job")).isZero();
        assertThat(context.getBean(ProcessEngine.class).getRuntimeService().createProcessInstanceQuery().count()).isZero();
        assertThatThrownBy(() -> context.getBean(ChangeLifecycle.class).transition(new TransitionCommand(UUID.randomUUID(),
                new EntityRef(EntityKind.EPIC, epic), approver, EpicState.APPROVED, EpicState.ACTIVE, 1, identity(), null, null)))
                .hasMessageContaining("UNAUTHORIZED");
        approver = "replacement-human";
        start(true);
        post(PATH, token(approver, "forgeflow.plan.approve"), body).expectStatus().isForbidden();
    }
    @Test
    void changedInputsRequireInvalidationAndExplicitNewApproval() throws Exception {
        String accessToken = token(approver, "forgeflow.plan.approve");
        post(PATH, accessToken, request(UUID.randomUUID(), 0)).expectStatus().isOk();
        UUID oldApproval = approvalId;
        configuration = "c".repeat(64); approvalId = UUID.randomUUID(); start(true);
        post(PATH, accessToken, request(UUID.randomUUID(), 1)).expectStatus().isEqualTo(409);
        var invalidation = Map.of("commandId", UUID.randomUUID(), "expectedVersion", 1, "epicId", epic, "storyId", story);
        post(PATH + "/invalidate", accessToken, invalidation).expectStatus().isOk();
        post(PATH, accessToken, request(UUID.randomUUID(), 2)).expectStatus().isOk();
        post(PATH + "/invalidate", accessToken, invalidation).expectStatus().isOk()
                .expectBody().jsonPath("$.version").isEqualTo(2);
        assertThat(jdbc.queryForObject("select state from epic", String.class)).isEqualTo("APPROVED");
        assertThat(count("manual_plan_approval")).isEqualTo(2);
        assertThat(jdbc.queryForObject("select configuration_sha256 from manual_plan_approval where approval_id=?", String.class, oldApproval))
                .isEqualTo("b".repeat(64));
        assertThat(jdbc.queryForMap("select approved_identity from epic").get("approved_identity").toString()).contains(configuration);
        jdbc.update("update epic set state='ACTIVE'");
        post(PATH + "/invalidate", accessToken, Map.of("commandId", UUID.randomUUID(), "expectedVersion", 3, "epicId", epic, "storyId", story)).expectStatus().isEqualTo(409);
    }
    @Test
    void mismatchedReviewedInputsOrRegistryCannotGrantApproval() throws Exception {
        var wrong = Map.of("commandId", UUID.randomUUID(), "expectedVersion", 0, "epicId", epic, "storyId", story, "workflowKey", "bootstrap", "workflowVersion", 1, "approval",
                new ApprovalIdentity(approvalId, "different-plan", configuration), "definitionSha256", definitionHash);
        String accessToken = token(approver, "forgeflow.plan.approve");
        post(PATH, accessToken, wrong).expectStatus().isEqualTo(409);
        var otherTarget = new HashMap<>(request(UUID.randomUUID(), 0));
        otherTarget.put("storyId", UUID.randomUUID());
        post(PATH, accessToken, otherTarget).expectStatus().isEqualTo(409);
        jdbc.update("update workflow_definition set content_sha256=?", "d".repeat(64));
        post(PATH, accessToken, request(UUID.randomUUID(), 0)).expectStatus().isEqualTo(409);
        untouched();
    }
    @Test
    void storageFailureRollsBackApprovalPinAndAudit() throws Exception {
        jdbc.execute("create function reject_approval() returns trigger language plpgsql as $$ begin raise exception 'test rollback'; end $$");
        jdbc.execute("create trigger reject_approval before insert on manual_plan_approval for each row execute function reject_approval()");
        var body = request(UUID.randomUUID(), 0);
        String accessToken = token(approver, "forgeflow.plan.approve");
        post(PATH, accessToken, body).expectStatus().isEqualTo(503);
        untouched();
        jdbc.execute("drop trigger reject_approval on manual_plan_approval");
        post(PATH, accessToken, body).expectStatus().isOk();
    }
    @Test
    void competingApprovalsProduceOnePinAndOneAudit() throws Exception {
        String accessToken = token(approver, "forgeflow.plan.approve");
        var first = CompletableFuture.supplyAsync(() -> post(PATH, accessToken, request(UUID.randomUUID(), 0)).returnResult(Void.class).getStatus().value());
        var second = CompletableFuture.supplyAsync(() -> post(PATH, accessToken, request(UUID.randomUUID(), 0)).returnResult(Void.class).getStatus().value());
        assertThat(List.of(first.join(), second.join())).containsExactlyInAnyOrder(200, 409);
        assertThat(count("manual_plan_approval")).isEqualTo(1);
        assertThat(count("lifecycle_transition")).isEqualTo(1);
        start(false);
        post(PATH, accessToken, request(UUID.randomUUID(), 1)).expectStatus().isForbidden();
    }
}
