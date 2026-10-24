package io.forgeflow.controlplane.webhook;

import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
@ConditionalOnProperty(name = "forgeflow.github.webhook.enabled", havingValue = "true")
public class WebhookInbox {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public WebhookInbox(JdbcTemplate jdbc, PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(manager);
    }

    public boolean receive(UUID delivery, String event, String digest, int bytes, String metadata) {
        return Boolean.TRUE.equals(transactions.execute(status -> {
            int inserted = jdbc.update("insert into webhook_inbox(delivery_id,event_type,payload_sha256,payload_bytes,metadata) "
                    + "values (?,?,?,?,cast(? as jsonb)) on conflict (delivery_id) do nothing",
                    delivery, event, digest, bytes, metadata);
            if (inserted == 1) return true;
            var previous = jdbc.queryForMap("select event_type,payload_sha256 from webhook_inbox where delivery_id=?", delivery);
            if (!event.equals(previous.get("event_type")) || !digest.equals(previous.get("payload_sha256"))) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Delivery identity mismatch");
            }
            return false;
        }));
    }

    /** Publish one normalized event and mark its inbox row in the same transaction. */
    public boolean processOne() {
        return Boolean.TRUE.equals(transactions.execute(status -> {
            var pending = jdbc.queryForList("select delivery_id,event_type from webhook_inbox "
                    + "where state='RECEIVED' and payload_sha256 is not null "
                    + "order by received_at,delivery_id limit 1 for update skip locked");
            if (pending.isEmpty()) return false;
            var row = pending.getFirst();
            String kind = switch ((String) row.get("event_type")) {
                case "ping" -> "PING";
                case "issues" -> "ISSUE";
                case "pull_request" -> "PULL_REQUEST";
                default -> "UNSUPPORTED";
            };
            jdbc.update("insert into webhook_event(delivery_id,event_kind) values (?,?) on conflict do nothing",
                    row.get("delivery_id"), kind);
            jdbc.update("update webhook_inbox set state='PROCESSED',processed_at=now() where delivery_id=?", row.get("delivery_id"));
            return true;
        }));
    }
}
