package io.forgeflow.controlplane.execution.adapter.out.persistence;

import io.forgeflow.controlplane.execution.application.port.out.ExecutionStore;
import io.forgeflow.controlplane.execution.domain.ExecutionRun;
import io.forgeflow.controlplane.lifecycle.domain.LifecycleException;
import io.forgeflow.controlplane.lifecycle.domain.approval.ManualPlan;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

public final class JdbcExecutionStore implements ExecutionStore {
    private final JdbcTemplate jdbc;
    public JdbcExecutionStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public Optional<ExecutionRun> find(ManualPlan plan, String definitionId) {
        return access(() -> jdbc.query("select r.*,s.epic_id,d.content_sha256,j.id job_id from workflow_run r join story s on s.id=r.story_id "
                + "join workflow_definition d on d.definition_id=r.process_definition_id left join job j "
                + "on j.run_id=r.id and j.idempotency_key='initial:' || r.id::text where r.story_id=? and r.approval_id=?",
                (row, index) -> {
                    var pin = plan.identity();
                    require(plan.epicId().equals(row.getObject("epic_id", UUID.class))
                            && plan.definitionSha256().equals(row.getString("content_sha256"))
                            && definitionId.equals(row.getString("process_definition_id"))
                            && plan.workflowKey().equals(row.getString("workflow_key"))
                            && plan.workflowVersion() == row.getInt("workflow_version")
                            && pin.planRevision().equals(row.getString("plan_revision"))
                            && pin.configurationSha256().equals(row.getString("configuration_sha256"))
                            && plan.approver().equals(row.getString("approved_by"))
                            && row.getObject("job_id") != null);
                    return new ExecutionRun(row.getObject("id", UUID.class), row.getString("process_instance_id"), row.getObject("job_id", UUID.class));
                }, plan.storyId(), plan.identity().approvalId()).stream().findFirst());
    }
    @Override public void insert(ManualPlan plan, String definitionId, ExecutionRun run) {
        access(() -> {
            var pin = plan.identity();
            int inserted = jdbc.update("insert into workflow_run(id,story_id,workflow_key,workflow_version,process_definition_id,process_instance_id,"
                    + "plan_revision,configuration_sha256,approval_id,approved_by,approved_at) "
                    + "select ?,a.story_id,a.workflow_key,a.workflow_version,a.process_definition_id,?,a.plan_revision,a.configuration_sha256,"
                    + "a.approval_id,a.approved_by,a.approved_at from manual_plan_approval a join workflow_definition d "
                    + "on (d.workflow_key,d.registry_version,d.definition_id)=(a.workflow_key,a.workflow_version,a.process_definition_id) "
                    + "where a.approval_id=? and a.epic_id=? and a.story_id=? and a.plan_revision=? and a.configuration_sha256=? "
                    + "and a.workflow_key=? and a.workflow_version=? and a.process_definition_id=? and a.definition_sha256=? "
                    + "and a.approved_by=? and d.content_sha256=a.definition_sha256",
                    run.runId(), run.processInstanceId(), pin.approvalId(), plan.epicId(), plan.storyId(), pin.planRevision(),
                    pin.configurationSha256(), plan.workflowKey(), plan.workflowVersion(), definitionId, plan.definitionSha256(), plan.approver());
            require(inserted == 1);
            return null;
        });
    }
    @Override public void queue(ExecutionRun run, String definitionId, String activityKey, int activityVersion) {
        access(() -> {
            int inserted = jdbc.update("insert into job(id,run_id,activity_key,activity_version,idempotency_key) "
                    + "select ?,id,?,?,? from workflow_run where id=? and process_instance_id=? and process_definition_id=?",
                    run.jobId(), activityKey, activityVersion, "initial:" + run.runId(), run.runId(), run.processInstanceId(), definitionId);
            require(inserted == 1);
            return null;
        });
    }
    private static void require(boolean matches) {
        if (!matches) throw new LifecycleException(LifecycleException.Kind.IDENTITY_MISMATCH);
    }
    private static <T> T access(Supplier<T> operation) {
        try { return operation.get(); }
        catch (DuplicateKeyException failure) { throw new LifecycleException(LifecycleException.Kind.COMMAND_CONFLICT); }
        catch (DataAccessException failure) { throw new LifecycleException(LifecycleException.Kind.STORAGE_UNAVAILABLE); }
    }
}
