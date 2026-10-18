package io.forgeflow.spike;

import java.util.Map;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** A registered, versioned activity with a guard at its side-effect boundary. */
@Component("codingTestV1")
public class CodingActivity implements JavaDelegate {
    private final JdbcTemplate jdbc;
    private final Map<String, TestProvider> providers = Map.of(
            "deterministic-a", () -> "fixture-a",
            "deterministic-b", () -> "fixture-b");

    public CodingActivity(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void execute(DelegateExecution execution) {
        Long approvals = jdbc.queryForObject("""
                SELECT count(*) FROM spike_approval
                WHERE run_key = ? AND definition_id = ? AND plan_digest = ? AND config_digest = ?
                """, Long.class, execution.getProcessInstanceBusinessKey(),
                execution.getProcessDefinitionId(), execution.getVariable("planDigest"),
                execution.getVariable("configDigest"));
        if (approvals == null || approvals != 1) {
            throw new IllegalStateException("Missing approval for pinned run identity");
        }
        TestProvider provider = providers.get((String) execution.getVariable("provider"));
        if (provider == null) {
            throw new IllegalArgumentException("Unregistered test provider");
        }
        execution.setVariable("sanitizedEvidence", provider.execute());
    }

    @FunctionalInterface
    interface TestProvider {
        String execute();
    }
}
