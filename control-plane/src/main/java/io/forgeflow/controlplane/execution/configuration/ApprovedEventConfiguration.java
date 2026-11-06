package io.forgeflow.controlplane.execution.configuration;

import io.forgeflow.controlplane.execution.adapter.in.scheduling.ApprovedEventPoller;
import io.forgeflow.controlplane.execution.adapter.out.persistence.JdbcApprovedEventInbox;
import io.forgeflow.controlplane.execution.application.ApprovedEventService;
import io.forgeflow.controlplane.execution.application.port.in.DispatchApprovedEvent;
import io.forgeflow.controlplane.execution.application.port.in.StartApprovedExecution;
import io.forgeflow.controlplane.execution.domain.ApprovedEventTarget;
import io.forgeflow.controlplane.lifecycle.adapter.out.persistence.SpringLifecycleTransactions;
import io.forgeflow.controlplane.lifecycle.domain.approval.ManualPlan;
import java.time.Duration;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "forgeflow.execution.inbox.enabled", havingValue = "true")
@EnableConfigurationProperties(ApprovedEventConfiguration.Settings.class)
@EnableScheduling
class ApprovedEventConfiguration {
    @ConfigurationProperties("forgeflow.execution.inbox")
    record Settings(UUID repositoryId, long installationId, long issueId, Duration pollInterval) {
        Settings {
            if (repositoryId == null || installationId <= 0 || issueId <= 0 || pollInterval == null
                    || pollInterval.isNegative() || pollInterval.isZero()) throw new IllegalArgumentException("Invalid execution inbox settings");
        }
    }
    @Bean DispatchApprovedEvent dispatchApprovedEvent(Settings settings, ManualPlan plan,
            StartApprovedExecution starter, JdbcTemplate jdbc, PlatformTransactionManager manager) {
        return new ApprovedEventService(new JdbcApprovedEventInbox(jdbc), starter,
                new SpringLifecycleTransactions(manager),
                new ApprovedEventTarget(settings.repositoryId(), plan.storyId(), settings.installationId(), settings.issueId()), plan);
    }
    @Bean ApprovedEventPoller approvedEventPoller(DispatchApprovedEvent events) { return new ApprovedEventPoller(events); }
}
