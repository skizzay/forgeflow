package io.forgeflow.controlplane;

import javax.sql.DataSource;
import org.flowable.engine.ProcessEngine;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration(proxyBeanMethods = false)
public class EngineConfiguration {
    @Bean
    PlatformTransactionManager transactionManager(DataSource dataSource) {
        return new JdbcTransactionManager(dataSource);
    }

    @Bean(destroyMethod = "close")
    @DependsOnDatabaseInitialization
    ProcessEngine processEngine(DataSource dataSource, PlatformTransactionManager transactionManager,
                                Environment environment) {
        if (!environment.matchesProfiles("migrate")) {
            org.flywaydb.core.Flyway.configure().dataSource(dataSource)
                    .schemas("forgeflow", "flowable").defaultSchema("forgeflow")
                    .cleanDisabled(true).load().validate();
        }
        var configuration = new SpringProcessEngineConfiguration();
        configuration.setDataSource(dataSource);
        configuration.setTransactionManager(transactionManager);
        configuration.setDatabaseSchema("flowable");
        configuration.setDatabaseTablePrefix("flowable.");
        configuration.setTablePrefixIsSchema(true);
        configuration.setDatabaseSchemaUpdate(environment.matchesProfiles("migrate") ? "true" : "false");
        configuration.setAsyncExecutorActivate(false);
        configuration.setDisableIdmEngine(true);
        configuration.setDisableEventRegistry(true);
        configuration.setBeans(java.util.Map.of());
        return configuration.buildProcessEngine();
    }
}
