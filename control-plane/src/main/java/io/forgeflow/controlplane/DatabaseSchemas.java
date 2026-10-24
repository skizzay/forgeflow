package io.forgeflow.controlplane;

import java.util.Map;
import javax.sql.DataSource;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flywaydb.core.Flyway;
import org.postgresql.Driver;
import org.springframework.transaction.PlatformTransactionManager;

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

    static DatabaseSchemas fromJdbcUrl(String url, String engineSchema) {
        return new DatabaseSchemas(domainSchemaFromJdbcUrl(url), engineSchema);
    }

    static String domainSchemaFromJdbcUrl(String url) {
        var properties = Driver.parseURL(url, null);
        if (properties == null) throw new IllegalArgumentException("A PostgreSQL JDBC URL is required");
        String schema = properties.getProperty("currentSchema");
        validate(schema);
        return schema;
    }

    Flyway flyway(DataSource source) {
        return Flyway.configure().dataSource(source).schemas(domainSchema, engineSchema)
                .defaultSchema(domainSchema)
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
