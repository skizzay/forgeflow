package io.forgeflow.spike;

import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Test workflow dispatch: persist intent in the engine transaction, never call SCM here. */
@Component("dispatchV1")
public class DispatchActivity implements JavaDelegate {
    private final JdbcTemplate jdbc;

    public DispatchActivity(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void execute(DelegateExecution execution) {
        // Duplicate business keys fail and roll back the second process creation.
        jdbc.update("INSERT INTO spike_job (job_key, instance_id) VALUES (?, ?)",
                execution.getProcessInstanceBusinessKey(), execution.getProcessInstanceId());
    }
}
