package io.forgeflow.controlplane;

import io.forgeflow.controlplane.execution.adapter.out.persistence.JdbcApprovedEventInbox;
import io.forgeflow.controlplane.execution.domain.ApprovedEventTarget;
import java.sql.DriverManager;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApprovedEventInboxTest {
    private final String admin = System.getenv("FORGEFLOW_DB_URL"), user = System.getenv("FORGEFLOW_DB_USER"), password = System.getenv("FORGEFLOW_DB_PASSWORD");
    private String database;
    private JdbcTemplate jdbc;
    private JdbcApprovedEventInbox inbox;
    private TransactionTemplate tx;
    private ApprovedEventTarget target;
    private long sequence;

    @BeforeEach void setup() throws Exception {
        assertThat(admin).as("Use disposable PostgreSQL via scripts/verify-bootstrap.sh").isNotBlank();
        database = "event_store_" + UUID.randomUUID().toString().replace("-", "");
        try (var c = DriverManager.getConnection(admin, user, password); var sql = c.createStatement()) { sql.execute("create database " + database); }
        var source = new DriverManagerDataSource(admin.substring(0, admin.lastIndexOf('/') + 1) + database + "?currentSchema=event_domain", user, password);
        DatabaseMigrator.migrate(source, DatabaseSchemas.fromJdbcUrl(source.getUrl(), "event_engine"));
        jdbc = new JdbcTemplate(source);
        inbox = new JdbcApprovedEventInbox(jdbc);
        tx = new TransactionTemplate(new JdbcTransactionManager(source));
        UUID repo = UUID.randomUUID(), epic = UUID.randomUUID(), story = UUID.randomUUID();
        target = new ApprovedEventTarget(repo, story, 7, 99);
        jdbc.update("insert into repository(id,github_id,full_name) values (?,42,'public/event-fixture')", repo);
        jdbc.update("insert into epic(id,repository_id,issue_number) values (?,?,1)", epic, repo);
        jdbc.update("insert into story(id,epic_id,repository_id,issue_number) values (?,?,?,2)", story, epic, repo);
        jdbc.update("insert into workflow_definition values ('approved-story',1,?,'fixture-definition')", "b".repeat(64));
    }
    @AfterEach void cleanup() throws Exception {
        if (database != null) try (var c = DriverManager.getConnection(admin, user, password); var sql = c.createStatement()) {
            sql.execute("drop database if exists " + database + " with (force)");
        }
    }
    // Sanitized storage fixtures only; these do not verify webhook authentication or authorize startup.
    private UUID delivery(long repo, long installation, long issue, long number, String action, String event, String state, String kind) {
        UUID id = new UUID(0, ++sequence);
        jdbc.update("insert into webhook_inbox(delivery_id,event_type,state,payload_sha256,payload_bytes,metadata,received_at) "
                + "values (?,?,?,?,0,jsonb_build_object('repository_id',cast(? as bigint),'installation_id',cast(? as bigint),"
                + "'subject_id',cast(? as bigint),'subject_number',cast(? as bigint),'action',cast(? as text)),'2026-11-01T00:00:00Z')",
                id, event, state, "a".repeat(64), repo, installation, issue, number, action);
        if (kind != null) jdbc.update("insert into webhook_event(delivery_id,event_kind) values (?,?)", id, kind);
        return id;
    }
    private UUID delivery() { return delivery(42, 7, 99, 2, "opened", "issues", "PROCESSED", "ISSUE"); }
    private UUID run() {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into workflow_run(id,story_id,workflow_key,workflow_version,process_definition_id,process_instance_id,"
                + "plan_revision,configuration_sha256,approval_id,approved_by,approved_at) values (?,?,'approved-story',1,'fixture-definition',?,'v1',?,?, 'fixture-human',now())",
                id, target.storyId(), id.toString(), "c".repeat(64), UUID.randomUUID());
        return id;
    }
    @Test void matchesOnlyClassifiedProcessedOpenedDeliveryForExactTarget() {
        delivery(43,7,99,2,"opened","issues","PROCESSED","ISSUE");
        delivery(42,8,99,2,"opened","issues","PROCESSED","ISSUE");
        delivery(42,7,98,2,"opened","issues","PROCESSED","ISSUE");
        delivery(42,7,99,3,"opened","issues","PROCESSED","ISSUE");
        delivery(42,7,99,2,"edited","issues","PROCESSED","ISSUE");
        delivery(42,7,99,2,"opened","pull_request","PROCESSED","PULL_REQUEST");
        delivery(42,7,99,2,"opened","issues","RECEIVED","ISSUE");
        delivery(42,7,99,2,"opened","issues","PROCESSED",null);
        UUID matching = delivery();
        assertThat(tx.<Optional<UUID>>execute(status -> inbox.claimNext(target))).contains(matching);
        var wrongStory = new ApprovedEventTarget(target.repositoryId(), UUID.randomUUID(), 7, 99);
        assertThat(tx.<Optional<UUID>>execute(status -> inbox.claimNext(wrongStory))).isEmpty();
        assertThatThrownBy(() -> inbox.claimNext(target)).hasMessageContaining("Caller transaction required");
    }
    @Test void checkpointIsDurableIdempotentAndRejectsConflictingBinding() {
        UUID delivery = delivery(), run = run(), otherRun = run();
        tx.executeWithoutResult(status -> { assertThat(inbox.claimNext(target)).contains(delivery); inbox.record(delivery, run); inbox.record(delivery, run); });
        assertThat(tx.<Optional<UUID>>execute(status -> inbox.claimNext(target))).isEmpty();
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> inbox.record(delivery, otherRun))).hasMessageContaining("IDENTITY_MISMATCH");
        UUID duplicate = delivery();
        tx.executeWithoutResult(status -> inbox.record(duplicate, run));
        assertThat(jdbc.queryForObject("select count(*) from workflow_source_delivery where run_id=?", Integer.class, run)).isEqualTo(2);
    }
    @Test void outerRollbackRetainsPendingDeliveryWithoutCheckpoint() {
        UUID delivery = delivery(), run = run();
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            assertThat(inbox.claimNext(target)).contains(delivery);
            inbox.record(delivery, run);
            throw new IllegalStateException("fixture rollback");
        })).hasMessageContaining("fixture rollback");
        assertThat(jdbc.queryForObject("select count(*) from workflow_source_delivery", Integer.class)).isZero();
        assertThat(tx.<Optional<UUID>>execute(status -> inbox.claimNext(target))).contains(delivery);
    }
    @Test void competingTransactionSkipsLockedDelivery() {
        UUID first = delivery(), second = delivery();
        try (var executor = Executors.newSingleThreadExecutor()) {
            tx.executeWithoutResult(status -> {
                assertThat(inbox.claimNext(target)).contains(first);
                try {
                    assertThat(executor.submit(() -> tx.execute(other -> inbox.claimNext(target))).get(5, TimeUnit.SECONDS)).contains(second);
                } catch (Exception failure) { throw new AssertionError(failure); }
            });
        }
    }
}
