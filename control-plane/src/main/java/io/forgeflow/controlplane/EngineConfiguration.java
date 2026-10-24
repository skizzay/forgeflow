package io.forgeflow.controlplane;

import javax.sql.DataSource;
import org.flowable.engine.ProcessEngine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration(proxyBeanMethods = false)
public class EngineConfiguration {
    @Bean
    DatabaseSchemas databaseSchemas(@Value("${spring.datasource.url}") String jdbcUrl,
                                    @Value("${forgeflow.database.engine-schema}") String engineSchema) {
        return DatabaseSchemas.fromJdbcUrl(jdbcUrl, engineSchema);
    }

    @Bean(destroyMethod = "close")
    ProcessEngine processEngine(DataSource dataSource, PlatformTransactionManager transactionManager,
                                DatabaseSchemas schemas) {
        schemas.flyway(dataSource).validate();
        var configuration = schemas.engine(dataSource, transactionManager, false);
        var engine = configuration.buildProcessEngine();
        // Keep engine metadata checks in its own schema, but use qualified engine SQL
        // at runtime. Flowable otherwise calls Connection.setSchema on every command,
        // changing the shared transaction's schema before subsequent domain queries.
        configuration.getDbSqlSessionFactory().setDatabaseSchema(null);
        return engine;
    }
}
