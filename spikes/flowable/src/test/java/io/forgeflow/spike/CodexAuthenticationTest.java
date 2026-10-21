package io.forgeflow.spike;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CodexAuthenticationTest {
    @Test
    void projectionOmitsRefreshAndApiSecretsWithoutChangingHostCache() throws Exception {
        var original = new ObjectMapper().readTree("""
                {"auth_mode":"chatgpt","OPENAI_API_KEY":"dummy-api-key","other_secret":"dummy-other",
                 "tokens":{"access_token":"dummy-access","id_token":"dummy-id","account_id":"dummy-account",
                           "refresh_token":"dummy-refresh","future_secret":"dummy-future"}}
                """);
        var projected = CodexContainerProvider.accessOnlyAuth(original);
        assertThat(projected.toString()).doesNotContain("dummy-api-key", "dummy-refresh", "dummy-other", "dummy-future");
        assertThat(projected.path("tokens").path("access_token").asText()).isEqualTo("dummy-access");
        assertThat(projected.path("tokens").path("refresh_token").asText()).isEmpty();
        assertThat(original.path("tokens").path("refresh_token").asText()).isEqualTo("dummy-refresh");
    }

    @Test
    void absentOrWrongAuthenticationFailsBeforeStartingAJob() throws Exception {
        var json = new ObjectMapper();
        assertThatThrownBy(() -> CodexContainerProvider.accessOnlyAuth(json.readTree("{}")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> CodexContainerProvider.accessOnlyAuth(json.readTree("{\"auth_mode\":\"apikey\"}")))
                .isInstanceOf(IllegalStateException.class);
    }
}
