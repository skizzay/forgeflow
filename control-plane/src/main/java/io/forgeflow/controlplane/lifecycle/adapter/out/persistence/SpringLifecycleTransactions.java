package io.forgeflow.controlplane.lifecycle.adapter.out.persistence;

import io.forgeflow.controlplane.lifecycle.application.port.out.LifecycleTransactions;
import io.forgeflow.controlplane.lifecycle.domain.LifecycleException;
import java.util.function.Supplier;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;
public final class SpringLifecycleTransactions implements LifecycleTransactions {
    private final TransactionTemplate transactions;
    public SpringLifecycleTransactions(PlatformTransactionManager manager) { transactions = new TransactionTemplate(manager); }
    @Override public <T> T required(Supplier<T> work) {
        try { return transactions.execute(status -> work.get()); }
        catch (DataAccessException | TransactionException failure) { throw new LifecycleException(LifecycleException.Kind.STORAGE_UNAVAILABLE); }
    }
}
