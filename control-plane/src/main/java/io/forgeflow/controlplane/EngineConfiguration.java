package io.forgeflow.controlplane;

import javax.sql.DataSource;
import org.flowable.engine.ProcessEngine;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DatabaseSchemas.class)
public class EngineConfiguration {
    @Bean(destroyMethod = "close")
    ProcessEngine processEngine(DataSource dataSource, PlatformTransactionManager transactionManager,
                                DatabaseSchemas schemas) {
        schemas.flyway(dataSource).validate();
        return schemas.engine(dataSource, transactionManager, false).buildProcessEngine();
    }
}
