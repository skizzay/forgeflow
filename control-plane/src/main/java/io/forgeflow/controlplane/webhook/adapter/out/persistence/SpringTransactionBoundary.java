package io.forgeflow.controlplane.webhook.adapter.out.persistence;

import io.forgeflow.controlplane.webhook.application.port.out.TransactionBoundary;
import io.forgeflow.controlplane.webhook.domain.WebhookException;
import java.util.function.Supplier;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;

public final class SpringTransactionBoundary implements TransactionBoundary {
    private final TransactionTemplate transactions;
    public SpringTransactionBoundary(PlatformTransactionManager manager) { transactions = new TransactionTemplate(manager); }
    @Override
    public <T> T required(Supplier<T> work) {
        try { return transactions.execute(status -> work.get()); }
        catch (DataAccessException | TransactionException unavailable) {
            throw new WebhookException(WebhookException.Kind.STORAGE_UNAVAILABLE);
        }
    }
}
