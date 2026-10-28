package io.forgeflow.controlplane;

import io.forgeflow.controlplane.lifecycle.adapter.out.persistence.JdbcLifecycleRepository;
import io.forgeflow.controlplane.lifecycle.adapter.out.persistence.JdbcManualApprovalStore;
import io.forgeflow.controlplane.lifecycle.adapter.out.persistence.SpringLifecycleTransactions;
import io.forgeflow.controlplane.lifecycle.application.ManualApprovalService;
import io.forgeflow.controlplane.lifecycle.application.port.in.PrepareApprovedStory;
import io.forgeflow.controlplane.lifecycle.application.port.in.RecordManualApproval;
import io.forgeflow.controlplane.lifecycle.domain.approval.ApprovalIdentity;
import io.forgeflow.controlplane.lifecycle.domain.approval.ManualPlan;
import java.sql.DriverManager;
import java.time.Clock;
import java.util.UUID;
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

class ApprovedStoryPreparationTest {
    private final String user = System.getenv("FORGEFLOW_DB_USER"), password = System.getenv("FORGEFLOW_DB_PASSWORD");
    private final UUID repo = UUID.randomUUID(), epic = UUID.randomUUID(), story = UUID.randomUUID();
    private String admin, database;
    private ConfigurableApplicationContext context;
    private JdbcTemplate jdbc;
    private ManualPlan plan;
    private ManualApprovalService manual;
    @BeforeEach void setup() throws Exception {
        admin = System.getenv("FORGEFLOW_DB_URL");
        assertThat(admin).as("Use disposable PostgreSQL via scripts/verify-bootstrap.sh").isNotBlank();
        database = "preparation_" + UUID.randomUUID().toString().replace("-", "");
        String url = admin.substring(0, admin.lastIndexOf('/') + 1) + database + "?currentSchema=preparation_domain";
        try (var c = DriverManager.getConnection(admin, user, password); var sql = c.createStatement()) { sql.execute("create database " + database); }
        DatabaseMigrator.migrate(new DriverManagerDataSource(url, user, password), DatabaseSchemas.fromJdbcUrl(url, "preparation_engine"));
        context = new SpringApplicationBuilder(ControlPlaneApplication.class).web(WebApplicationType.NONE).run(
                "--spring.datasource.url=" + url, "--spring.datasource.username=" + user, "--spring.datasource.password=" + password,
                "--forgeflow.database.engine-schema=preparation_engine", "--logging.level.root=ERROR");
        jdbc = context.getBean(JdbcTemplate.class);
        jdbc.update("insert into repository(id,github_id,full_name) values (?,42,'public/preparation-fixture')", repo);
        jdbc.update("insert into epic(id,repository_id,issue_number) values (?,?,1)", epic, repo);
        jdbc.update("insert into story(id,epic_id,repository_id,issue_number) values (?,?,?,2)", story, epic, repo);
        var pin = new ApprovalIdentity(UUID.randomUUID(), "fixture-plan", "b".repeat(64));
        plan = new ManualPlan(epic, story, "fixture-authenticated-human", pin, "bootstrap", 1,
                jdbc.queryForObject("select content_sha256 from workflow_definition", String.class));
        // Explicit trusted fixture; the separate HTTP suite verifies OAuth2 authentication.
        manual = new ManualApprovalService(plan, new JdbcLifecycleRepository(jdbc),
                new SpringLifecycleTransactions(context.getBean(PlatformTransactionManager.class)), new JdbcManualApprovalStore(jdbc), Clock.systemUTC());
    }
    private void approve() {
        manual.record(new RecordManualApproval.Command(UUID.randomUUID(), plan.approver(), RecordManualApproval.Action.APPROVE,
                0, epic, story, plan.identity(), plan.workflowKey(), 1, plan.definitionSha256()));
    }
    private String prepare() { return context.getBean(PrepareApprovedStory.class).prepare(plan); }
    @AfterEach void cleanup() throws Exception {
        if (context != null) context.close();
        if (database != null) try (var c = DriverManager.getConnection(admin, user, password); var sql = c.createStatement()) {
            sql.execute("drop database if exists " + database + " with (force)");
        }
    }
    @Test void requiresCurrentApprovalEvenWhenHistoricalEvidenceExists() {
        assertThatThrownBy(this::prepare).hasMessageContaining("IDENTITY_MISMATCH");
        approve();
        manual.record(new RecordManualApproval.Command(UUID.randomUUID(), plan.approver(), RecordManualApproval.Action.INVALIDATE,
                1, epic, story, null, null, null, null));
        assertThatThrownBy(this::prepare).hasMessageContaining("IDENTITY_MISMATCH");
        assertThat(jdbc.queryForObject("select state from epic", String.class)).isEqualTo("DRAFT");
    }
    @Test void preparationIsGuardedIdempotentAndStartsNoRun() {
        approve();
        String definition = prepare();
        assertThat(prepare()).isEqualTo(definition);
        assertThat(jdbc.queryForObject("select state from epic", String.class)).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("select state from story", String.class)).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("select count(*) from lifecycle_transition", Integer.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("select count(*) from workflow_run", Integer.class)).isZero();
    }
    @Test void callerRollbackRestoresParentsAndAuditTogether() {
        approve();
        var tx = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> { prepare(); throw new IllegalStateException("fixture rollback"); }))
                .hasMessageContaining("fixture rollback");
        assertThat(jdbc.queryForObject("select state from epic", String.class)).isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject("select state from story", String.class)).isEqualTo("DRAFT");
        assertThat(jdbc.queryForObject("select count(*) from lifecycle_transition", Integer.class)).isEqualTo(1);
    }
    @Test void rejectsMismatchedDefinitionAndMultiStoryPlan() {
        approve();
        var wrong = new ManualPlan(epic, story, plan.approver(), plan.identity(), "bootstrap", 1, "c".repeat(64));
        assertThatThrownBy(() -> context.getBean(PrepareApprovedStory.class).prepare(wrong)).hasMessageContaining("IDENTITY_MISMATCH");
        jdbc.update("insert into story(id,epic_id,repository_id,issue_number) values (?,?,?,3)", UUID.randomUUID(), epic, repo);
        assertThatThrownBy(this::prepare).hasMessageContaining("IDENTITY_MISMATCH");
        assertThat(jdbc.queryForObject("select state from epic", String.class)).isEqualTo("APPROVED");
    }
}
