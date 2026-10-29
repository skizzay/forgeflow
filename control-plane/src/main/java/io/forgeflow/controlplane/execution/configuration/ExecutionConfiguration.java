package io.forgeflow.controlplane.execution.configuration;

import io.forgeflow.controlplane.execution.adapter.in.flowable.ApprovedActivityDelegate;
import io.forgeflow.controlplane.execution.adapter.out.flowable.FlowableWorkflowStart;
import io.forgeflow.controlplane.execution.adapter.out.persistence.JdbcExecutionStore;
import io.forgeflow.controlplane.execution.application.ApprovedExecutionService;
import io.forgeflow.controlplane.execution.application.InitialActivityService;
import io.forgeflow.controlplane.execution.application.port.in.QueueInitialActivity;
import io.forgeflow.controlplane.execution.application.port.in.StartApprovedExecution;
import io.forgeflow.controlplane.lifecycle.adapter.out.persistence.SpringLifecycleTransactions;
import io.forgeflow.controlplane.lifecycle.application.port.in.PrepareApprovedStory;
import java.util.UUID;
import java.util.function.Supplier;
import org.flowable.engine.ProcessEngine;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "forgeflow.execution", name = "enabled", havingValue = "true")
class ExecutionConfiguration {
    @Bean StartApprovedExecution startApprovedExecution(PrepareApprovedStory preparation, JdbcTemplate jdbc,
            PlatformTransactionManager transactions, ProcessEngine engine, Supplier<UUID> identifiers) {
        return new ApprovedExecutionService(preparation, new SpringLifecycleTransactions(transactions),
                new JdbcExecutionStore(jdbc), new FlowableWorkflowStart(engine), identifiers);
    }
    @Bean QueueInitialActivity queueInitialActivity(JdbcTemplate jdbc, PlatformTransactionManager transactions) {
        return new InitialActivityService(new JdbcExecutionStore(jdbc), new SpringLifecycleTransactions(transactions));
    }
    @Bean ApprovedActivityDelegate enqueueApprovedActivity(QueueInitialActivity activity) { return new ApprovedActivityDelegate(activity); }
}
