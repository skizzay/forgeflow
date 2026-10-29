package io.forgeflow.controlplane;

import io.forgeflow.controlplane.execution.adapter.in.flowable.ApprovedActivityDelegate;
import java.util.Map;
import javax.sql.DataSource;
import org.flowable.engine.ProcessEngine;
import org.springframework.beans.factory.ObjectProvider;
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
                                DatabaseSchemas schemas, ObjectProvider<ApprovedActivityDelegate> activity) {
        schemas.flyway(dataSource).validate();
        var configuration = schemas.engine(dataSource, transactionManager, false);
        var delegate = activity.getIfAvailable();
        if (delegate != null) configuration.setBeans(Map.of("enqueueApprovedActivity", delegate));
        var engine = configuration.buildProcessEngine();
        // Keep engine metadata checks in its own schema, but use qualified engine SQL
        // at runtime. Flowable otherwise calls Connection.setSchema on every command,
        // changing the shared transaction's schema before subsequent domain queries.
        configuration.getDbSqlSessionFactory().setDatabaseSchema(null);
        return engine;
    }
}
