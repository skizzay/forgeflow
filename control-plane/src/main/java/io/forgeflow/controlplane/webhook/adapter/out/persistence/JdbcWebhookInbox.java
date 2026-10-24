package io.forgeflow.controlplane.webhook.adapter.out.persistence;

import io.forgeflow.controlplane.webhook.application.port.out.WebhookInboxRepository;
import io.forgeflow.controlplane.webhook.domain.DeliveryIdentity;
import io.forgeflow.controlplane.webhook.domain.WebhookDelivery;
import io.forgeflow.controlplane.webhook.domain.WebhookEventKind;
import io.forgeflow.controlplane.webhook.domain.WebhookException;
import io.forgeflow.controlplane.webhook.domain.WebhookMetadata;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

public final class JdbcWebhookInbox implements WebhookInboxRepository {
    private final JdbcTemplate jdbc;
    private final JsonMapper mapper = JsonMapper.builder().build();
    public JdbcWebhookInbox(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override
    public boolean insertIfAbsent(WebhookDelivery delivery) {
        return access(() -> jdbc.update("insert into webhook_inbox(delivery_id,event_type,payload_sha256,payload_bytes,metadata) "
                + "values (?,?,?,?,cast(? as jsonb)) on conflict (delivery_id) do nothing", delivery.deliveryId(),
                delivery.eventType(), delivery.bodySha256(), delivery.payloadBytes(), encode(delivery.metadata())) == 1);
    }
    @Override
    public DeliveryIdentity findIdentity(UUID id) {
        return access(() -> jdbc.queryForObject("select delivery_id,event_type,payload_sha256 from webhook_inbox where delivery_id=?",
                (row, index) -> new DeliveryIdentity(row.getObject("delivery_id", UUID.class), row.getString("event_type"), row.getString("payload_sha256")), id));
    }
    @Override
    public Optional<DeliveryIdentity> claimNext() {
        return access(() -> jdbc.query("select delivery_id,event_type,payload_sha256 from webhook_inbox "
                + "where state='RECEIVED' and payload_sha256 is not null "
                + "order by received_at,delivery_id limit 1 for update skip locked",
                (row, index) -> new DeliveryIdentity(row.getObject("delivery_id", UUID.class), row.getString("event_type"), row.getString("payload_sha256"))).stream().findFirst());
    }
    @Override
    public void publish(UUID id, WebhookEventKind kind) {
        access(() -> jdbc.update("insert into webhook_event(delivery_id,event_kind) values (?,?) on conflict do nothing", id, kind.name()));
    }
    @Override
    public void markProcessed(UUID id) {
        access(() -> jdbc.update("update webhook_inbox set state='PROCESSED',processed_at=now() where delivery_id=?", id));
    }
    private String encode(WebhookMetadata metadata) {
        var fields = new LinkedHashMap<String, Object>();
        if (metadata.repositoryId() != null) fields.put("repository_id", metadata.repositoryId());
        if (metadata.installationId() != null) fields.put("installation_id", metadata.installationId());
        if (metadata.subjectId() != null) fields.put("subject_id", metadata.subjectId());
        if (metadata.subjectNumber() != null) fields.put("subject_number", metadata.subjectNumber());
        if (metadata.action() != null) fields.put("action", metadata.action());
        return mapper.writeValueAsString(fields);
    }
    private static <T> T access(Supplier<T> work) {
        try { return work.get(); }
        catch (DataAccessException unavailable) { throw new WebhookException(WebhookException.Kind.STORAGE_UNAVAILABLE); }
    }
}
