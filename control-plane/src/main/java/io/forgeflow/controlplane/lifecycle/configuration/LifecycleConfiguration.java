package io.forgeflow.controlplane.lifecycle.configuration;

import io.forgeflow.controlplane.lifecycle.adapter.out.authority.DenyUnverifiedAuthority;
import io.forgeflow.controlplane.lifecycle.adapter.out.persistence.JdbcApprovedPlanEvidence;
import io.forgeflow.controlplane.lifecycle.adapter.out.persistence.JdbcLifecycleRepository;
import io.forgeflow.controlplane.lifecycle.adapter.out.persistence.SpringLifecycleTransactions;
import io.forgeflow.controlplane.lifecycle.application.ApprovedStoryPreparation;
import io.forgeflow.controlplane.lifecycle.application.LifecycleService;
import io.forgeflow.controlplane.lifecycle.application.port.in.PrepareApprovedStory;
import io.forgeflow.controlplane.lifecycle.application.port.out.LifecycleAuthority;
import java.time.Clock;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
@Configuration(proxyBeanMethods = false)
class LifecycleConfiguration {
    @Bean @ConditionalOnMissingBean(LifecycleAuthority.class)
    LifecycleAuthority lifecycleAuthority() { return new DenyUnverifiedAuthority(); }
    @Bean @ConditionalOnMissingBean(Clock.class)
    Clock lifecycleClock() { return Clock.systemUTC(); }
    @Bean @ConditionalOnMissingBean(name = "lifecycleCommandIds")
    Supplier<UUID> lifecycleCommandIds() { return UUID::randomUUID; }
    @Bean
    PrepareApprovedStory prepareApprovedStory(JdbcTemplate jdbc, PlatformTransactionManager transactions, Clock clock, Supplier<UUID> commandIds) {
        return new ApprovedStoryPreparation(new JdbcLifecycleRepository(jdbc), new SpringLifecycleTransactions(transactions),
                new JdbcApprovedPlanEvidence(jdbc), clock, commandIds);
    }
    @Bean
    LifecycleService lifecycleService(JdbcTemplate jdbc, PlatformTransactionManager transactions, LifecycleAuthority authority, Clock clock) {
        return new LifecycleService(new JdbcLifecycleRepository(jdbc), new SpringLifecycleTransactions(transactions), authority, clock);
    }
}
