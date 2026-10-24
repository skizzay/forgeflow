package io.forgeflow.controlplane;

import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;

/** Release job entry point: no Spring application context, HTTP server or workflow registration. */
public final class DatabaseMigrator {
    private DatabaseMigrator() { }

    public static void main(String[] args) {
        var environment = System.getenv();
        String url = required("FORGEFLOW_DB_URL");
        var schemas = DatabaseSchemas.fromJdbcUrl(url,
                environment.getOrDefault("FORGEFLOW_ENGINE_SCHEMA", "flowable"));
        var source = new DriverManagerDataSource(url,
                required("FORGEFLOW_DB_USER"), required("FORGEFLOW_DB_PASSWORD"));
        migrate(source, schemas);
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing environment variable: " + name);
        return value;
    }

    static void migrate(DataSource source, DatabaseSchemas schemas) {
        schemas.flyway(source).migrate();
        // Flowable owns engine DDL. This is the only entry point permitted to apply it.
        var engine = schemas.engine(source, new JdbcTransactionManager(source), true).buildProcessEngine();
        engine.close();
    }
}
