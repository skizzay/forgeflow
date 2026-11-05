package io.forgeflow.controlplane.execution.adapter.out.persistence;

import io.forgeflow.controlplane.execution.application.port.out.ApprovedEventInbox;
import io.forgeflow.controlplane.execution.domain.ApprovedEventTarget;
import io.forgeflow.controlplane.lifecycle.domain.LifecycleException;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public final class JdbcApprovedEventInbox implements ApprovedEventInbox {
    private final JdbcTemplate jdbc;
    public JdbcApprovedEventInbox(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public Optional<UUID> claimNext(ApprovedEventTarget target) {
        return access(() -> jdbc.query("select i.delivery_id from webhook_inbox i join webhook_event e using(delivery_id) "
                + "join repository r on r.id=? join story s on s.id=? and s.repository_id=r.id "
                + "where i.state='PROCESSED' and i.event_type='issues' and e.event_kind='ISSUE' "
                + "and i.metadata @> jsonb_build_object('repository_id',r.github_id,'installation_id',cast(? as bigint),"
                + "'subject_id',cast(? as bigint),'subject_number',s.issue_number,'action','opened') "
                + "and not exists(select 1 from workflow_source_delivery c where c.delivery_id=i.delivery_id) "
                + "order by i.received_at,i.delivery_id limit 1 for update of i skip locked",
                (row, index) -> row.getObject(1, UUID.class), target.repositoryId(), target.storyId(),
                target.installationId(), target.issueId()).stream().findFirst());
    }
    @Override public void record(UUID deliveryId, UUID runId) {
        access(() -> {
            jdbc.update("insert into workflow_source_delivery(delivery_id,run_id) values (?,?) on conflict(delivery_id) do nothing", deliveryId, runId);
            UUID existing = jdbc.queryForObject("select run_id from workflow_source_delivery where delivery_id=?", UUID.class, deliveryId);
            if (!runId.equals(existing)) throw new LifecycleException(LifecycleException.Kind.IDENTITY_MISMATCH);
            return null;
        });
    }
    private static <T> T access(Supplier<T> operation) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Caller transaction required");
        try { return operation.get(); }
        catch (DataAccessException failure) { throw new LifecycleException(LifecycleException.Kind.STORAGE_UNAVAILABLE); }
    }
}
