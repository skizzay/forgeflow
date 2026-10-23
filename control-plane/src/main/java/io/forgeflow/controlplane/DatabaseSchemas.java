package io.forgeflow.controlplane;

import java.util.Map;
import javax.sql.DataSource;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flywaydb.core.Flyway;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.transaction.PlatformTransactionManager;

@ConfigurationProperties("forgeflow.database")
public record DatabaseSchemas(String domainSchema, String engineSchema) {
    public DatabaseSchemas {
        validate(domainSchema);
        validate(engineSchema);
        if (domainSchema.equals(engineSchema)) {
            throw new IllegalArgumentException("Domain and engine schemas must be distinct");
        }
    }

    private static void validate(String name) {
        if (name == null || !name.matches("[a-z][a-z0-9_]{0,62}")
                || name.startsWith("pg_") || name.equals("information_schema") || name.equals("public")) {
            throw new IllegalArgumentException("Use a dedicated lowercase PostgreSQL schema identifier (1-63 characters)");
        }
    }

    Flyway flyway(DataSource source) {
        return Flyway.configure().dataSource(source).schemas(domainSchema, engineSchema)
                .defaultSchema(domainSchema).placeholders(Map.of("domainSchema", domainSchema))
                .cleanDisabled(true).load();
    }

    SpringProcessEngineConfiguration engine(DataSource source, PlatformTransactionManager transactions,
                                            boolean migrate) {
        var configuration = new SpringProcessEngineConfiguration();
        configuration.setDataSource(source);
        configuration.setTransactionManager(transactions);
        configuration.setDatabaseSchema(engineSchema);
        configuration.setDatabaseTablePrefix(engineSchema + ".");
        configuration.setTablePrefixIsSchema(true);
        configuration.setDatabaseSchemaUpdate(migrate ? "true" : "false");
        configuration.setAsyncExecutorActivate(false);
        configuration.setDisableIdmEngine(true);
        configuration.setDisableEventRegistry(true);
        configuration.setBeans(Map.of());
        return configuration;
    }
}
