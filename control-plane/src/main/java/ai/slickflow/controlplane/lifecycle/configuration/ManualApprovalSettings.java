package ai.slickflow.controlplane.lifecycle.configuration;

import ai.slickflow.controlplane.lifecycle.domain.approval.ApprovalIdentity;
import ai.slickflow.controlplane.lifecycle.domain.approval.ManualPlan;
import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("slickflow.manual-approval")
public record ManualApprovalSettings(boolean enabled, UUID epicId, UUID storyId, String subject, UUID approvalId,
                                     String planRevision, String configurationSha256, String workflowKey,
                                     int workflowVersion, String definitionSha256) {
    public ManualApprovalSettings {
        if (enabled) new ManualPlan(epicId, storyId, subject, new ApprovalIdentity(approvalId, planRevision, configurationSha256),
                workflowKey, workflowVersion, definitionSha256);
    }
    ManualPlan plan(String issuer) {
        return new ManualPlan(epicId, storyId, issuer + " " + subject, new ApprovalIdentity(approvalId, planRevision, configurationSha256),
                workflowKey, workflowVersion, definitionSha256);
    }
}
