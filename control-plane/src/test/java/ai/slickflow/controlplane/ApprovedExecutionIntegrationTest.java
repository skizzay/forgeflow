package ai.slickflow.controlplane;

import ai.slickflow.controlplane.execution.application.port.in.StartApprovedExecution;
import ai.slickflow.controlplane.execution.application.port.in.DispatchApprovedEvent;
import ai.slickflow.controlplane.execution.adapter.in.scheduling.ApprovedEventPoller;
import ai.slickflow.controlplane.webhook.application.port.in.ReceiveWebhook;
import ai.slickflow.controlplane.webhook.application.port.in.ProcessWebhook;
import ai.slickflow.controlplane.webhook.application.port.in.SignedWebhook;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import ai.slickflow.controlplane.execution.domain.ExecutionRun;
import ai.slickflow.controlplane.lifecycle.adapter.out.persistence.JdbcLifecycleRepository;
import ai.slickflow.controlplane.lifecycle.adapter.out.persistence.JdbcManualApprovalStore;
import ai.slickflow.controlplane.lifecycle.adapter.out.persistence.SpringLifecycleTransactions;
import ai.slickflow.controlplane.lifecycle.application.ManualApprovalService;
import ai.slickflow.controlplane.lifecycle.application.port.in.RecordManualApproval;
import ai.slickflow.controlplane.lifecycle.domain.approval.ApprovalIdentity;
import ai.slickflow.controlplane.lifecycle.domain.approval.ManualPlan;
import java.sql.DriverManager;
import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.flowable.engine.ProcessEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApprovedExecutionIntegrationTest {
    private final String user = System.getenv("SLICKFLOW_DB_USER"), password = System.getenv("SLICKFLOW_DB_PASSWORD");
    private final UUID repo = UUID.randomUUID(), epic = UUID.randomUUID(), story = UUID.randomUUID();
    private final ApprovalIdentity approval = new ApprovalIdentity(UUID.randomUUID(), "test-plan", "b".repeat(64));
    private String admin, database, url, digest;
    private ConfigurableApplicationContext context;
    private JdbcTemplate jdbc;
    private ManualPlan plan;
    private ManualApprovalService manual;

    @BeforeEach void prepare() throws Exception {
        admin = System.getenv("SLICKFLOW_DB_URL");
        assertThat(admin).as("Use disposable PostgreSQL via scripts/verify-bootstrap.sh").isNotBlank();
        database = "execution_" + UUID.randomUUID().toString().replace("-", "");
        url = admin.substring(0, admin.lastIndexOf('/') + 1) + database + "?currentSchema=execution_domain";
        try (var c = DriverManager.getConnection(admin, user, password); var sql = c.createStatement()) { sql.execute("create database " + database); }
        DatabaseMigrator.migrate(new DriverManagerDataSource(url, user, password), DatabaseSchemas.fromJdbcUrl(url, "execution_engine"));
        try (var resource = getClass().getResourceAsStream("/workflows/approved-story-v1.bpmn20.xml")) {
            digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(resource.readAllBytes()));
        }
        plan = new ManualPlan(epic, story, "https://identity.example human", approval, "approved-story", 1, digest);
        restart();
        jdbc.update("insert into repository(id,github_id,full_name) values (?,42,'public/execution-fixture')", repo);
        jdbc.update("insert into epic(id,repository_id,issue_number) values (?,?,1)", epic, repo);
        jdbc.update("insert into story(id,epic_id,repository_id,issue_number) values (?,?,?,2)", story, epic, repo);
    }
    private void restart() {
        if (context != null) context.close();
        // No live IdP or HTTP endpoint. The explicit fixture actor enters the same approval use case tested over OAuth2 separately.
        context = new SpringApplicationBuilder(ControlPlaneApplication.class).web(WebApplicationType.NONE)
                .initializers(application -> application.getBeanFactory().registerSingleton("fixtureManualPlan", plan)).run(
                "--spring.datasource.url=" + url, "--spring.datasource.username=" + user, "--spring.datasource.password=" + password,
                "--slickflow.database.engine-schema=execution_engine", "--slickflow.execution.enabled=true", "--logging.level.root=ERROR",
                "--slickflow.execution.inbox.enabled=true", "--slickflow.execution.inbox.repository-id=" + repo,
                "--slickflow.execution.inbox.installation-id=7", "--slickflow.execution.inbox.issue-id=99",
                "--slickflow.execution.inbox.poll-interval=PT24H", "--slickflow.github.webhook.enabled=true",
                "--slickflow.github.webhook.processing-enabled=false", "--slickflow.github.webhook.secret=public-test-fixture-secret");
        jdbc = context.getBean(JdbcTemplate.class);
    }
    private void approve() {
        // Headless tests construct the fixture use case because OAuth2 auto-configuration is WebFlux-only.
        var tx = new SpringLifecycleTransactions(
                context.getBean(PlatformTransactionManager.class));
        manual = new ManualApprovalService(plan,
                new JdbcLifecycleRepository(jdbc), tx,
                new JdbcManualApprovalStore(jdbc), Clock.systemUTC());
        manual.record(new RecordManualApproval.Command(UUID.randomUUID(), plan.approver(), RecordManualApproval.Action.APPROVE,
                0, epic, story, approval, plan.workflowKey(), plan.workflowVersion(), digest));
    }
    private ExecutionRun start() { return context.getBean(StartApprovedExecution.class).start(plan); }
    private long count(String table) { return jdbc.queryForObject("select count(*) from " + table, Long.class); }
    @AfterEach void cleanup() throws Exception {
        if (context != null) context.close();
        if (database != null) try (var c = DriverManager.getConnection(admin, user, password); var sql = c.createStatement()) {
            sql.execute("drop database if exists " + database + " with (force)");
        }
    }
    private SignedWebhook signedDelivery(long installation) throws Exception {
        byte[] body = ("{\"repository\":{\"id\":42},\"installation\":{\"id\":" + installation
                + "},\"issue\":{\"id\":99,\"number\":2},\"action\":\"opened\"}").getBytes(StandardCharsets.UTF_8);
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("public-test-fixture-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return new SignedWebhook(UUID.randomUUID().toString(), "issues", "sha256=" + HexFormat.of().formatHex(mac.doFinal(body)), body);
    }
    private boolean dispatch() { return context.getBean(DispatchApprovedEvent.class).dispatchOne(); }
    private void receive(SignedWebhook delivery) {
        context.getBean(ReceiveWebhook.class).receive(delivery);
        context.getBean(ProcessWebhook.class).processOne();
    }
    @Test void signedIngressWaitsForApprovalAndReplaysOneRunAcrossRestart() throws Exception {
        var signed = signedDelivery(7);
        assertThatThrownBy(() -> receive(new SignedWebhook(signed.deliveryId(), "issues", "sha256=" + "0".repeat(64), signed.payload())))
                .hasMessageContaining("INVALID_SIGNATURE");
        assertThat(count("webhook_inbox")).isZero();
        receive(signedDelivery(8)); // A signed routing mismatch must not block the configured target.
        receive(signed);
        assertThatThrownBy(this::dispatch).hasMessageContaining("IDENTITY_MISMATCH");
        assertThat(count("workflow_source_delivery")).isZero();
        approve();
        context.getBean(ApprovedEventPoller.class).poll();
        assertThat(count("workflow_source_delivery")).isEqualTo(1);
        receive(signed); // Same delivery ID replay.
        receive(signedDelivery(7)); // Different delivery ID for the same issue.
        restart();
        assertThat(dispatch()).isTrue();
        assertThat(dispatch()).isFalse();
        assertThat(count("workflow_source_delivery")).isEqualTo(2);
        assertThat(count("workflow_run")).isEqualTo(1);
        assertThat(count("job")).isEqualTo(1);
        var engine = context.getBean(ProcessEngine.class);
        var process = engine.getRuntimeService().createProcessInstanceQuery().singleResult();
        assertThat(engine.getRuntimeService().getActiveActivityIds(process.getId())).containsExactly("awaitOutcome");
    }
    @Test void checkpointFailureRollsBackEngineRunJobAndParentTransitions() throws Exception {
        approve();
        receive(signedDelivery(7));
        jdbc.execute("create function reject_checkpoint() returns trigger language plpgsql as $$ begin raise exception 'fixture checkpoint rollback'; end $$");
        jdbc.execute("create trigger reject_checkpoint after insert on workflow_source_delivery for each row execute function reject_checkpoint()");
        assertThatThrownBy(this::dispatch).isInstanceOf(RuntimeException.class);
        for (String table : new String[]{"workflow_source_delivery", "workflow_run", "job"}) assertThat(count(table)).isZero();
        assertThat(jdbc.queryForObject("select state from epic", String.class)).isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject("select state from story", String.class)).isEqualTo("DRAFT");
        assertThat(context.getBean(ProcessEngine.class).getRuntimeService().createProcessInstanceQuery().count()).isZero();
        jdbc.execute("drop trigger reject_checkpoint on workflow_source_delivery");
        assertThat(dispatch()).isTrue();
        assertThat(count("workflow_source_delivery")).isEqualTo(1);
    }
    @Test void revokedApprovalLeavesSignedDeliveryPending() throws Exception {
        approve();
        manual.record(new RecordManualApproval.Command(UUID.randomUUID(), plan.approver(), RecordManualApproval.Action.INVALIDATE,
                1, epic, story, null, null, null, null));
        receive(signedDelivery(7));
        assertThatThrownBy(this::dispatch).hasMessageContaining("IDENTITY_MISMATCH");
        assertThat(count("workflow_source_delivery")).isZero();
        assertThat(count("workflow_run")).isZero();
    }
    @Test void approvalIsRequiredAndRevocationCannotBeReplacedByOldEvidence() {
        assertThatThrownBy(this::start).hasMessageContaining("IDENTITY_MISMATCH");
        assertThat(count("workflow_run")).isZero();
        approve();
        manual.record(new RecordManualApproval.Command(UUID.randomUUID(), plan.approver(), RecordManualApproval.Action.INVALIDATE,
                1, epic, story, null, null, null, null));
        assertThatThrownBy(this::start).hasMessageContaining("IDENTITY_MISMATCH");
        assertThat(count("workflow_run")).isZero();
        assertThat(context.getBean(ProcessEngine.class).getRuntimeService().createProcessInstanceQuery().count()).isZero();
    }
    @Test void processOwnsDispatchAndDurableWaitAndRestartReplaysOneRun() {
        approve();
        var run = start();
        var engine = context.getBean(ProcessEngine.class);
        assertThat(engine.getRuntimeService().getActiveActivityIds(run.processInstanceId())).containsExactly("awaitOutcome");
        assertThat(jdbc.queryForMap("select * from job")).containsEntry("state", "PENDING").containsEntry("activity_key", "develop-story");
        assertThat(jdbc.queryForMap("select * from workflow_run")).containsEntry("approval_id", approval.approvalId())
                .containsEntry("process_instance_id", run.processInstanceId()).containsEntry("approved_by", plan.approver());
        assertThat(jdbc.queryForObject("select state from epic", String.class)).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("select state from story", String.class)).isEqualTo("ACTIVE");
        restart();
        assertThat(start()).isEqualTo(run);
        assertThat(count("workflow_run")).isEqualTo(1);
        assertThat(count("job")).isEqualTo(1);
        assertThat(context.getBean(ProcessEngine.class).getRuntimeService().createProcessInstanceQuery().count()).isEqualTo(1);
    }
    @Test void competingStartsHaveOneCommittedOwner() {
        approve();
        var one = CompletableFuture.supplyAsync(this::start);
        var two = CompletableFuture.supplyAsync(this::start);
        assertThat(one.join()).isEqualTo(two.join());
        assertThat(count("job")).isEqualTo(1);
        assertThat(count("workflow_run")).isEqualTo(1);
    }
    @Test void failureAfterDispatchRollsBackProcessRunParentsAndJob() {
        approve();
        jdbc.execute("create function reject_job() returns trigger language plpgsql as $$ begin raise exception 'test rollback'; end $$");
        jdbc.execute("create trigger reject_job after insert on job for each row execute function reject_job()");
        assertThatThrownBy(this::start).isInstanceOf(RuntimeException.class);
        assertThat(count("workflow_run")).isZero();
        assertThat(count("job")).isZero();
        assertThat(count("lifecycle_transition")).isEqualTo(1); // Only the committed human approval remains.
        assertThat(jdbc.queryForObject("select state from epic", String.class)).isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject("select state from story", String.class)).isEqualTo("DRAFT");
        assertThat(context.getBean(ProcessEngine.class).getRuntimeService().createProcessInstanceQuery().count()).isZero();
        jdbc.execute("drop trigger reject_job on job");
        var outer = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        assertThatThrownBy(() -> outer.executeWithoutResult(status -> {
            start();
            throw new IllegalStateException("test failure after engine flush");
        })).hasMessageContaining("test failure after engine flush");
        assertThat(count("workflow_run")).isZero();
        assertThat(count("job")).isZero();
        assertThat(context.getBean(ProcessEngine.class).getRuntimeService().createProcessInstanceQuery().count()).isZero();
        assertThat(jdbc.queryForObject("select state from epic", String.class)).isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject("select state from story", String.class)).isEqualTo("DRAFT");
        assertThat(count("lifecycle_transition")).isEqualTo(1);
        assertThat(start()).isNotNull();
    }
    @Test void mismatchedPinsAndNonSingleStoryPlansFailClosed() {
        approve();
        var wrong = new ManualPlan(epic, story, plan.approver(), approval, plan.workflowKey(), 1, "c".repeat(64));
        assertThatThrownBy(() -> context.getBean(StartApprovedExecution.class).start(wrong)).hasMessageContaining("IDENTITY_MISMATCH");
        var unsupported = new ManualPlan(epic, story, plan.approver(), approval, "bootstrap", 1, digest);
        assertThatThrownBy(() -> context.getBean(StartApprovedExecution.class).start(unsupported)).hasMessageContaining("IDENTITY_MISMATCH");
        jdbc.update("insert into story(id,epic_id,repository_id,issue_number) values (?,?,?,3)", UUID.randomUUID(), epic, repo);
        assertThatThrownBy(this::start).hasMessageContaining("IDENTITY_MISMATCH");
        assertThat(count("workflow_run")).isZero();
    }
}
