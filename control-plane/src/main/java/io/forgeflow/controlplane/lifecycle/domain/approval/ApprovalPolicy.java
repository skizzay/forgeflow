package io.forgeflow.controlplane.lifecycle.domain.approval;

import io.forgeflow.controlplane.lifecycle.domain.LifecycleException;
import io.forgeflow.controlplane.lifecycle.domain.ParentContext;
import io.forgeflow.controlplane.lifecycle.domain.evidence.ActorAuthority;

import static io.forgeflow.controlplane.lifecycle.domain.LifecycleEntity.require;

/** Shared identity rules across separately versioned job, story and epic roots. */
public final class ApprovalPolicy {
    private ApprovalPolicy() { }
    public static void requireCurrent(ApprovalIdentity requested, ApprovalIdentity pinned, ActorAuthority actor) {
        require(requested != null && requested.equals(actor.currentApproval()), LifecycleException.Kind.IDENTITY_MISMATCH);
        require(pinned == null || pinned.equals(requested), LifecycleException.Kind.IDENTITY_MISMATCH);
    }
    public static void requireParent(ApprovalIdentity requested, ParentContext parent) {
        require(requested != null && requested.equals(parent.approval()), LifecycleException.Kind.IDENTITY_MISMATCH);
    }
}
