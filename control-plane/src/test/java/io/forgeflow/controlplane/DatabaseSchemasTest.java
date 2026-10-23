package io.forgeflow.controlplane;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DatabaseSchemasTest {
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
