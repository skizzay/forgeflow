package io.forgeflow.controlplane.lifecycle.adapter.out.persistence;

import io.forgeflow.controlplane.lifecycle.application.port.out.ManualApprovalStore;
import io.forgeflow.controlplane.lifecycle.domain.LifecycleException;
import io.forgeflow.controlplane.lifecycle.domain.approval.ManualPlan;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

public final class JdbcManualApprovalStore implements ManualApprovalStore {
    private final JdbcTemplate jdbc;
    public JdbcManualApprovalStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override
    public String requireDefinition(ManualPlan plan) {
        return jdbc.query("select d.definition_id from workflow_definition d, story s where d.workflow_key=? "
                + "and d.registry_version=? and d.content_sha256=? and s.id=? and s.epic_id=?",
                (row, index) -> row.getString(1), plan.workflowKey(), plan.workflowVersion(), plan.definitionSha256(),
                plan.storyId(), plan.epicId()).stream().findFirst()
                .orElseThrow(() -> new LifecycleException(LifecycleException.Kind.IDENTITY_MISMATCH));
    }
    @Override
    public void record(ManualPlan plan, String definitionId, Instant now) {
        var pin = plan.identity();
        jdbc.update("insert into manual_plan_approval(approval_id,epic_id,story_id,plan_revision,configuration_sha256,"
                + "workflow_key,workflow_version,process_definition_id,definition_sha256,approved_by,approved_at) "
                + "values (?,?,?,?,?,?,?,?,?,?,?) on conflict (approval_id) do nothing", pin.approvalId(), plan.epicId(),
                plan.storyId(), pin.planRevision(), pin.configurationSha256(), plan.workflowKey(), plan.workflowVersion(),
                definitionId, plan.definitionSha256(), plan.approver(), Timestamp.from(now));
        boolean same = Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from manual_plan_approval where approval_id=? "
                + "and epic_id=? and story_id=? and plan_revision=? and configuration_sha256=? and workflow_key=? "
                + "and workflow_version=? and process_definition_id=? and definition_sha256=? and approved_by=?)", Boolean.class,
                pin.approvalId(), plan.epicId(), plan.storyId(), pin.planRevision(), pin.configurationSha256(), plan.workflowKey(),
                plan.workflowVersion(), definitionId, plan.definitionSha256(), plan.approver()));
        if (!same) throw new LifecycleException(LifecycleException.Kind.IDENTITY_MISMATCH);
    }
    @Override
    public void requireInvalidationTarget(UUID commandId, ManualPlan plan) {
        boolean matches = Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from lifecycle_transition t "
                + "join manual_plan_approval a on a.approval_id=cast(t.identity->'approval'->>'approvalId' as uuid) "
                + "where t.command_id=? and a.epic_id=? and a.story_id=?)", Boolean.class,
                commandId, plan.epicId(), plan.storyId()));
        if (!matches) throw new LifecycleException(LifecycleException.Kind.IDENTITY_MISMATCH);
    }

}
