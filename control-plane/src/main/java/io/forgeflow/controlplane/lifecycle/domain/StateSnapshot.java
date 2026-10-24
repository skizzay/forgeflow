package io.forgeflow.controlplane.lifecycle.domain;

public record StateSnapshot(EntityRef entity, LifecycleState state, long version, ApprovalIdentity approval,
                            ExecutionIdentity execution, MergeTarget mergeTarget, boolean allStoriesMerged, boolean parentActive, ApprovalIdentity parentApproval) { }
