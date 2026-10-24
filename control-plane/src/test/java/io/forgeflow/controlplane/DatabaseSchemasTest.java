package io.forgeflow.controlplane;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

class DatabaseSchemasTest {
    @Test
    void derivesSingleSchemaFromDriverParsedUrl() {
        assertThat(DatabaseSchemas.fromJdbcUrl("jdbc:postgresql://localhost/db?sslmode=require&currentSchema=custom_domain", "engine").domainSchema()).isEqualTo("custom_domain");
        for (String url : List.of("jdbc:postgresql://localhost/db", "jdbc:postgresql://localhost/db?currentSchema=one,two", "jdbc:postgresql://localhost/db?currentSchema=public", "jdbc:other:db")) {
            assertThatThrownBy(() -> DatabaseSchemas.fromJdbcUrl(url, "engine")).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void rejectsUnsafeReservedMissingOrSharedSchemaNames() {
        for (String invalid : List.of("", "UpperCase", "bad.name", "x;drop schema public", "pg_catalog",
                "information_schema", "public", "a".repeat(64))) {
            assertThatThrownBy(() -> new DatabaseSchemas(invalid, "engine"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new DatabaseSchemas("domain", invalid))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> new DatabaseSchemas(null, "engine")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DatabaseSchemas("same", "same")).isInstanceOf(IllegalArgumentException.class);
    }
}
