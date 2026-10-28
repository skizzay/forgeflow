package io.forgeflow.controlplane.lifecycle.adapter.out.persistence;
import io.forgeflow.controlplane.lifecycle.application.port.out.ApprovedPlanEvidence;
import io.forgeflow.controlplane.lifecycle.domain.LifecycleException;
import io.forgeflow.controlplane.lifecycle.domain.approval.ManualPlan;
import org.springframework.jdbc.core.JdbcTemplate;
public final class JdbcApprovedPlanEvidence implements ApprovedPlanEvidence {
    private final JdbcTemplate jdbc;
    public JdbcApprovedPlanEvidence(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override public String requireDefinition(ManualPlan plan) {
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
}
