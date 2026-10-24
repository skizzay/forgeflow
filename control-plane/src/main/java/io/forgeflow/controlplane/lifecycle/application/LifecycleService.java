package io.forgeflow.controlplane.lifecycle.application;

import io.forgeflow.controlplane.lifecycle.application.port.in.ChangeLifecycle;
import io.forgeflow.controlplane.lifecycle.application.port.out.LifecycleAuthority;
import io.forgeflow.controlplane.lifecycle.application.port.out.LifecycleRepository;
import io.forgeflow.controlplane.lifecycle.application.port.out.LifecycleTransactions;
import io.forgeflow.controlplane.lifecycle.domain.AuthorityFacts;
import io.forgeflow.controlplane.lifecycle.domain.ApprovalIdentity;
import io.forgeflow.controlplane.lifecycle.domain.EntityKind;
import io.forgeflow.controlplane.lifecycle.domain.LifecycleException;
import io.forgeflow.controlplane.lifecycle.domain.StateSnapshot;
import io.forgeflow.controlplane.lifecycle.domain.TransitionCommand;
import io.forgeflow.controlplane.lifecycle.domain.TransitionPolicy;
import io.forgeflow.controlplane.lifecycle.domain.TransitionReceipt;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Objects;

public final class LifecycleService implements ChangeLifecycle {
    private final LifecycleRepository repository;
    private final LifecycleTransactions transactions;
    private final LifecycleAuthority authority;
    private final Clock clock;
    public LifecycleService(LifecycleRepository repository, LifecycleTransactions transactions, LifecycleAuthority authority, Clock clock) {
        this.repository = repository; this.transactions = transactions; this.authority = authority; this.clock = clock;
    }
    @Override
    public TransitionReceipt transition(TransitionCommand command) {
        String digest = digest(command);
        return transactions.required(() -> {
            var snapshot = repository.lock(command.entity());
            var previous = repository.findTransition(command.commandId());
            var facts = authority.verify(snapshot,command.actorId());
            if (previous.isPresent()) {
                require(facts != null && facts.actorId() != null && facts.actorId().equals(command.actorId()) && facts.actorId().equals(previous.get().actorId()), LifecycleException.Kind.UNAUTHORIZED);
                require(previous.get().requestDigest().equals(digest), LifecycleException.Kind.COMMAND_CONFLICT);
                return previous.get().receipt();
            }
            require(snapshot.version() == command.expectedVersion() && snapshot.state() == command.expectedState(), LifecycleException.Kind.STALE_STATE);
            var guard = TransitionPolicy.guard(snapshot.state(), command.nextState());
            authorize(guard, snapshot, command, facts);
            var receipt = new TransitionReceipt(command.commandId(), command.entity(), snapshot.state(), command.nextState(), snapshot.version() + 1);
            repository.save(snapshot, command, facts, receipt, digest, clock.instant());
            return receipt;
        });
    }
    private void authorize(TransitionPolicy.Guard guard, StateSnapshot state, TransitionCommand command, AuthorityFacts facts) {
        require(facts != null && facts.actorId() != null && facts.actorId().equals(command.actorId()), LifecycleException.Kind.UNAUTHORIZED);
        if (guard == TransitionPolicy.Guard.CANCEL || guard == TransitionPolicy.Guard.INVALIDATE) {
            require(facts.cancellationAuthorized(), LifecycleException.Kind.UNAUTHORIZED);
            return; // Stale/revoked approval must not prevent an authorized stop or invalidation.
        }
        if (guard == TransitionPolicy.Guard.EXPIRED_LEASE) {
            require(facts.recoveryAuthorized() && !repository.hasLiveAttempt(state.entity().id(), clock.instant()), LifecycleException.Kind.UNAUTHORIZED);
            return;
        }
        require(command.approval() != null && command.approval().equals(facts.currentApproval()), LifecycleException.Kind.IDENTITY_MISMATCH);
        require(state.approval() == null || state.approval().equals(command.approval()), LifecycleException.Kind.IDENTITY_MISMATCH);
        if (state.entity().kind() != EntityKind.EPIC) {
            require(command.approval().equals(state.parentApproval()), LifecycleException.Kind.IDENTITY_MISMATCH);
        }
        if (state.entity().kind() == EntityKind.JOB) {
            require(state.execution() != null && Objects.equals(state.execution(), command.execution())
                    && state.execution().approval().equals(command.approval()), LifecycleException.Kind.IDENTITY_MISMATCH);
        }
        switch (guard) {
            case APPROVAL, EPIC_START -> { }
            case STORY_READY, STORY_START -> require(state.parentActive() && facts.dependenciesMerged(), LifecycleException.Kind.EVIDENCE_MISSING);
            case START_EXECUTION -> {
                require(state.parentActive() && facts.dependenciesMerged(), LifecycleException.Kind.EVIDENCE_MISSING);
                require(ownsLiveAttempt(state, command, facts), LifecycleException.Kind.UNAUTHORIZED);
            }
            case ATTEMPT_RESULT -> require(state.parentActive() && ownsLiveAttempt(state, command, facts), LifecycleException.Kind.UNAUTHORIZED);
            case RECONCILED_RETRY -> require(state.parentActive() && facts.recoveryAuthorized() && facts.externalEffectsQuiescent() && !repository.hasLiveAttempt(state.entity().id(), clock.instant()), LifecycleException.Kind.EVIDENCE_MISSING);
            case RECONCILED_RESULT -> require(facts.recoveryAuthorized() && facts.externalEffectsQuiescent() && !repository.hasLiveAttempt(state.entity().id(), clock.instant()) && facts.reconciledOutcome() == command.nextState(), LifecycleException.Kind.EVIDENCE_MISSING);
            case PR_OPEN -> {
                require(repository.hasSuccessfulJob(state.entity().id(), command.approval()), LifecycleException.Kind.EVIDENCE_MISSING);
                require(facts.pullRequest() != null && facts.pullRequest().confirms(state.mergeTarget(), false), LifecycleException.Kind.EVIDENCE_MISSING);
            }
            case PR_MERGE -> require(facts.pullRequest() != null && facts.pullRequest().confirms(state.mergeTarget(), true), LifecycleException.Kind.EVIDENCE_MISSING);
            case EPIC_COMPLETE -> require(state.allStoriesMerged() && facts.integrationChecksPassed() && facts.finalHumanApprovalId() != null, LifecycleException.Kind.EVIDENCE_MISSING);
            default -> throw new LifecycleException(LifecycleException.Kind.INVALID_TRANSITION);
        }
    }
    private boolean ownsLiveAttempt(StateSnapshot state, TransitionCommand command, AuthorityFacts facts) {
        return command.attempt() != null && command.attempt().workerId().equals(facts.authenticatedWorkerId())
                && repository.ownsLiveAttempt(state.entity().id(), command.attempt(), clock.instant());
    }
    private static void require(boolean condition, LifecycleException.Kind failure) { if (!condition) throw new LifecycleException(failure); }
    private static String digest(TransitionCommand command) {
        var canonical = new StringBuilder();
        append(canonical, command.commandId(), command.entity().kind(), command.entity().id(), command.actorId(),
                command.expectedState().name(), command.nextState().name(), command.expectedVersion());
        approval(canonical, command.approval());
        var execution = command.execution();
        append(canonical, execution != null);
        if (execution != null) {
            append(canonical, execution.runId(), execution.workflowKey(), execution.workflowVersion(),
                    execution.processDefinitionId(), execution.activityKey(), execution.activityVersion());
            approval(canonical, execution.approval());
        }
        var attempt = command.attempt();
        append(canonical, attempt != null);
        if (attempt != null) append(canonical, attempt.attemptId(), attempt.workerId(), attempt.fencingToken());
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.toString().getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException unavailable) { throw new IllegalStateException("SHA-256 unavailable", unavailable); }
    }
    private static void approval(StringBuilder canonical, ApprovalIdentity approval) {
        append(canonical, approval != null);
        if (approval != null) append(canonical, approval.approvalId(), approval.planRevision(), approval.configurationSha256());
    }
    private static void append(StringBuilder canonical, Object... values) {
        for (Object value : values) {
            if (value == null) canonical.append("-1:");
            else { String text = value.toString(); canonical.append(text.length()).append(':').append(text); }
        }
    }
}
