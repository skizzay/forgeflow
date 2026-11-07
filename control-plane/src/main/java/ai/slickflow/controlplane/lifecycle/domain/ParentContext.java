package ai.slickflow.controlplane.lifecycle.domain;

import ai.slickflow.controlplane.lifecycle.domain.approval.ApprovalIdentity;

/** Parent facts read under the same root-to-leaf locks as the child. */
public record ParentContext(boolean active, ApprovalIdentity approval) { }
