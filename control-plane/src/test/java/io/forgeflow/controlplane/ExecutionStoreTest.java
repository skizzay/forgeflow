package io.forgeflow.controlplane;

import io.forgeflow.controlplane.execution.adapter.out.persistence.JdbcExecutionStore;
import io.forgeflow.controlplane.execution.domain.ExecutionRun;
import io.forgeflow.controlplane.lifecycle.adapter.out.persistence.JdbcManualApprovalStore;
import io.forgeflow.controlplane.lifecycle.domain.approval.ApprovalIdentity;
import io.forgeflow.controlplane.lifecycle.domain.approval.ManualPlan;
import java.sql.DriverManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExecutionStoreTest {
    private final String admin = System.getenv("FORGEFLOW_DB_URL"), user = System.getenv("FORGEFLOW_DB_USER"), password = System.getenv("FORGEFLOW_DB_PASSWORD");
    private final Instant approvedAt = Instant.parse("2026-10-28T12:00:00Z");
    private String database;
    private JdbcTemplate jdbc;
    private JdbcExecutionStore store;
    private TransactionTemplate tx;
    private ManualPlan plan;
    private final String definition = "fixture-definition";

    @BeforeEach void setup() throws Exception {
        assertThat(admin).as("Use disposable PostgreSQL via scripts/verify-bootstrap.sh").isNotBlank();
        database = "execution_store_" + UUID.randomUUID().toString().replace("-", "");
        try (var c = DriverManager.getConnection(admin, user, password); var sql = c.createStatement()) { sql.execute("create database " + database); }
        var source = new DriverManagerDataSource(admin.substring(0, admin.lastIndexOf('/') + 1) + database + "?currentSchema=store_domain", user, password);
        DatabaseMigrator.migrate(source, DatabaseSchemas.fromJdbcUrl(source.getUrl(), "store_engine"));
        jdbc = new JdbcTemplate(source);
        store = new JdbcExecutionStore(jdbc);
        tx = new TransactionTemplate(new JdbcTransactionManager(source));
        UUID repo = UUID.randomUUID(), epic = UUID.randomUUID(), story = UUID.randomUUID();
        jdbc.update("insert into repository(id,github_id,full_name) values (?,42,'public/store-fixture')", repo);
        jdbc.update("insert into epic(id,repository_id,issue_number) values (?,?,1)", epic, repo);
        jdbc.update("insert into story(id,epic_id,repository_id,issue_number) values (?,?,?,2)", story, epic, repo);
        jdbc.update("insert into workflow_definition values ('approved-story',1,?,?)", "b".repeat(64), definition);
        jdbc.update("insert into activity_definition values ('develop-story',1,1,1)");
        plan = new ManualPlan(epic, story, "fixture-human", new ApprovalIdentity(UUID.randomUUID(), "plan-v1", "c".repeat(64)), "approved-story", 1, "b".repeat(64));
        // Storage fixture only: this does not authenticate or authorize an execution.
        new JdbcManualApprovalStore(jdbc).record(plan, definition, approvedAt);
    }
    @AfterEach void cleanup() throws Exception {
        if (database != null) try (var c = DriverManager.getConnection(admin, user, password); var sql = c.createStatement()) {
            sql.execute("drop database if exists " + database + " with (force)");
        }
    }
    private ExecutionRun run() { return new ExecutionRun(UUID.randomUUID(), UUID.randomUUID().toString(), UUID.randomUUID()); }
    private void persist(ExecutionRun run) { store.insert(plan, definition, run); store.queue(run, definition, "develop-story", 1); }

    @Test void persistsExactPinsAndReloadsUsingAnotherAdapter() {
        var run = run();
        tx.executeWithoutResult(status -> { assertThat(store.find(plan, definition)).isEmpty(); persist(run); });
        var reloaded = new JdbcExecutionStore(new JdbcTemplate(jdbc.getDataSource()));
        var found = tx.execute(status -> reloaded.find(plan, definition));
        assertThat(found).contains(run);
        assertThat(jdbc.queryForMap("select * from workflow_run")).containsEntry("story_id", plan.storyId())
                .containsEntry("approval_id", plan.identity().approvalId()).containsEntry("plan_revision", plan.identity().planRevision())
                .containsEntry("configuration_sha256", plan.identity().configurationSha256()).containsEntry("approved_by", plan.approver())
                .containsEntry("approved_at", Timestamp.from(approvedAt)).containsEntry("workflow_key", plan.workflowKey())
                .containsEntry("workflow_version", 1).containsEntry("process_definition_id", definition).containsEntry("process_instance_id", run.processInstanceId());
        assertThat(jdbc.queryForMap("select * from job")).containsEntry("id", run.jobId()).containsEntry("run_id", run.runId())
                .containsEntry("state", "PENDING").containsEntry("activity_key", "develop-story").containsEntry("activity_version", 1);
    }
    @Test void rejectsMissingEvidenceAndChangedPinsOnInsertAndReload() {
        var pin = plan.identity();
        var variants = List.of(
                new ManualPlan(UUID.randomUUID(), plan.storyId(), plan.approver(), pin, plan.workflowKey(), 1, plan.definitionSha256()),
                new ManualPlan(plan.epicId(), plan.storyId(), "other-human", pin, plan.workflowKey(), 1, plan.definitionSha256()),
                new ManualPlan(plan.epicId(), plan.storyId(), plan.approver(), pin, plan.workflowKey(), 1, "d".repeat(64)),
                new ManualPlan(plan.epicId(), plan.storyId(), plan.approver(), new ApprovalIdentity(pin.approvalId(), "changed", pin.configurationSha256()), plan.workflowKey(), 1, plan.definitionSha256()),
                new ManualPlan(plan.epicId(), plan.storyId(), plan.approver(), new ApprovalIdentity(pin.approvalId(), pin.planRevision(), "d".repeat(64)), plan.workflowKey(), 1, plan.definitionSha256()),
                new ManualPlan(plan.epicId(), plan.storyId(), plan.approver(), pin, plan.workflowKey(), 2, plan.definitionSha256()));
        for (var wrong : variants) assertThatThrownBy(() -> tx.executeWithoutResult(status -> store.insert(wrong, definition, run())))
                .hasMessageContaining("IDENTITY_MISMATCH");
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> store.insert(plan, "missing-definition", run()))).hasMessageContaining("IDENTITY_MISMATCH");
        var missing = new ManualPlan(plan.epicId(), plan.storyId(), plan.approver(),
                new ApprovalIdentity(UUID.randomUUID(), pin.planRevision(), pin.configurationSha256()), plan.workflowKey(), 1, plan.definitionSha256());
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> store.insert(missing, definition, run()))).hasMessageContaining("IDENTITY_MISMATCH");
        var run = run();
        tx.executeWithoutResult(status -> persist(run));
        for (var wrong : variants) assertThatThrownBy(() -> tx.execute(status -> store.find(wrong, definition))).hasMessageContaining("IDENTITY_MISMATCH");
        assertThatThrownBy(() -> tx.execute(status -> store.find(plan, "wrong-definition"))).hasMessageContaining("IDENTITY_MISMATCH");
    }
    @Test void rejectsPartialCorrelationWrongJobBindingAndDuplicateIntent() {
        var run = run();
        tx.executeWithoutResult(status -> store.insert(plan, definition, run));
        assertThatThrownBy(() -> tx.execute(status -> store.find(plan, definition))).hasMessageContaining("IDENTITY_MISMATCH");
        for (var wrong : List.of(new ExecutionRun(UUID.randomUUID(), run.processInstanceId(), run.jobId()),
                new ExecutionRun(run.runId(), "wrong-process", run.jobId()))) {
            assertThatThrownBy(() -> tx.executeWithoutResult(status -> store.queue(wrong, definition, "develop-story", 1))).hasMessageContaining("IDENTITY_MISMATCH");
        }
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> store.queue(run, "wrong-definition", "develop-story", 1))).hasMessageContaining("IDENTITY_MISMATCH");
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> store.queue(run, definition, "unknown-activity", 1))).hasMessageContaining("STORAGE_UNAVAILABLE");
        tx.executeWithoutResult(status -> store.queue(run, definition, "develop-story", 1));
        var duplicate = new ExecutionRun(run.runId(), run.processInstanceId(), UUID.randomUUID());
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> store.queue(duplicate, definition, "develop-story", 1))).hasMessageContaining("COMMAND_CONFLICT");
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> store.insert(plan, definition, run()))).hasMessageContaining("COMMAND_CONFLICT");
        var found = tx.execute(status -> store.find(plan, definition));
        assertThat(found).contains(run);
    }
    @Test void callerFailureRollsBackRunAndJobTogether() {
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> { persist(run()); throw new IllegalStateException("fixture rollback"); }))
                .hasMessageContaining("fixture rollback");
        assertThat(jdbc.queryForObject("select count(*) from workflow_run", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from job", Integer.class)).isZero();
        var found = tx.execute(status -> store.find(plan, definition));
        assertThat(found).isEmpty();
    }
}
