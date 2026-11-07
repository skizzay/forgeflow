package ai.slickflow.controlplane.execution.configuration;

import ai.slickflow.controlplane.execution.adapter.in.flowable.ApprovedActivityDelegate;
import ai.slickflow.controlplane.execution.adapter.out.flowable.FlowableWorkflowStart;
import ai.slickflow.controlplane.execution.adapter.out.persistence.JdbcExecutionStore;
import ai.slickflow.controlplane.execution.application.ApprovedExecutionService;
import ai.slickflow.controlplane.execution.application.InitialActivityService;
import ai.slickflow.controlplane.execution.application.port.in.QueueInitialActivity;
import ai.slickflow.controlplane.execution.application.port.in.StartApprovedExecution;
import ai.slickflow.controlplane.lifecycle.adapter.out.persistence.SpringLifecycleTransactions;
import ai.slickflow.controlplane.lifecycle.application.port.in.PrepareApprovedStory;
import java.util.UUID;
import java.util.function.Supplier;
import org.flowable.engine.ProcessEngine;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "slickflow.execution", name = "enabled", havingValue = "true")
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
