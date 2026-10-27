package io.forgeflow.controlplane.execution.adapter.out.persistence;

import io.forgeflow.controlplane.execution.application.port.out.ExecutionStore;
import io.forgeflow.controlplane.execution.domain.ApprovedWorkflow;
import io.forgeflow.controlplane.execution.domain.ExecutionRun;
import io.forgeflow.controlplane.lifecycle.domain.LifecycleException;
import io.forgeflow.controlplane.lifecycle.domain.approval.ManualPlan;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

public final class JdbcExecutionStore implements ExecutionStore {
    private final JdbcTemplate jdbc;
    public JdbcExecutionStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override public String requireApproval(ManualPlan plan) {
        var pin = plan.identity();
        return jdbc.query("select a.process_definition_id from manual_plan_approval a join workflow_definition d "
                + "on (d.workflow_key,d.registry_version,d.definition_id)=(a.workflow_key,a.workflow_version,a.process_definition_id) "
                + "join story s on s.id=a.story_id and s.epic_id=a.epic_id "
                + "where a.approval_id=? and a.epic_id=? and a.story_id=? and a.plan_revision=? and a.configuration_sha256=? "
                + "and a.workflow_key=? and a.workflow_version=? and a.definition_sha256=? and a.approved_by=? "
                + "and d.content_sha256=a.definition_sha256 and (select count(*) from story where epic_id=a.epic_id)=1",
                (row, index) -> row.getString(1), pin.approvalId(), plan.epicId(), plan.storyId(), pin.planRevision(),
                pin.configurationSha256(), plan.workflowKey(), plan.workflowVersion(), plan.definitionSha256(), plan.approver())
                .stream().findFirst().orElseThrow(() -> new LifecycleException(LifecycleException.Kind.IDENTITY_MISMATCH));
    }
    @Override public Optional<ExecutionRun> find(ManualPlan plan, String definitionId) {
        return jdbc.query("select r.id,r.process_instance_id,j.id job_id from workflow_run r join job j on j.run_id=r.id "
                + "where r.story_id=? and r.approval_id=? and r.process_definition_id=? and j.activity_key=? and j.activity_version=1",
                (row, index) -> new ExecutionRun(row.getObject("id", UUID.class), row.getString("process_instance_id"), row.getObject("job_id", UUID.class)),
                plan.storyId(), plan.identity().approvalId(), definitionId, ApprovedWorkflow.ACTIVITY).stream().findFirst();
    }
    @Override public void insert(ManualPlan plan, String definitionId, ExecutionRun run) {
        jdbc.update("insert into workflow_run(id,story_id,workflow_key,workflow_version,process_definition_id,process_instance_id,"
                + "plan_revision,configuration_sha256,approval_id,approved_by,approved_at) "
                + "select ?,story_id,workflow_key,workflow_version,process_definition_id,?,plan_revision,configuration_sha256,"
                + "approval_id,approved_by,approved_at from manual_plan_approval where approval_id=? and process_definition_id=?",
                run.runId(), run.processInstanceId(), plan.identity().approvalId(), definitionId);
    }
    @Override public void queue(ExecutionRun run, String definitionId) {
        int inserted = jdbc.update("insert into job(id,run_id,activity_key,activity_version,idempotency_key) "
                + "select ?,id,?,1,? from workflow_run where id=? and process_instance_id=? and process_definition_id=?",
                run.jobId(), ApprovedWorkflow.ACTIVITY, "initial:" + run.runId(), run.runId(), run.processInstanceId(), definitionId);
        if (inserted != 1) throw new LifecycleException(LifecycleException.Kind.IDENTITY_MISMATCH);
    }
}
