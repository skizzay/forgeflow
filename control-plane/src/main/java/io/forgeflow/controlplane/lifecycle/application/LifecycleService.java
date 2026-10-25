package io.forgeflow.controlplane.lifecycle.application;

import io.forgeflow.controlplane.lifecycle.application.port.in.ChangeLifecycle;
import io.forgeflow.controlplane.lifecycle.application.port.out.LifecycleAuthority;
import io.forgeflow.controlplane.lifecycle.application.port.out.LifecycleRepository;
import io.forgeflow.controlplane.lifecycle.application.port.out.LifecycleTransactions;
import io.forgeflow.controlplane.lifecycle.domain.AuthorityFacts;
import io.forgeflow.controlplane.lifecycle.domain.LifecycleEntity;
import io.forgeflow.controlplane.lifecycle.domain.LifecycleException;
import io.forgeflow.controlplane.lifecycle.domain.StoryState;
import io.forgeflow.controlplane.lifecycle.domain.TransitionCommand;
import io.forgeflow.controlplane.lifecycle.domain.TransitionReceipt;
import io.forgeflow.controlplane.lifecycle.domain.approval.ApprovalIdentity;
import io.forgeflow.controlplane.lifecycle.domain.epic.Epic;
import io.forgeflow.controlplane.lifecycle.domain.job.AttemptEvidence;
import io.forgeflow.controlplane.lifecycle.domain.job.Job;
import io.forgeflow.controlplane.lifecycle.domain.story.Story;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;

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
            var verified = authority.verify(snapshot,command.actorId());
            var facts = verified == null ? AuthorityFacts.unverified() : verified;
            if (previous.isPresent()) {
                facts.actor().requireActor(command.actorId());
                facts.actor().requireActor(previous.get().actorId());
                require(previous.get().requestDigest().equals(digest), LifecycleException.Kind.COMMAND_CONFLICT);
                return previous.get().receipt();
            }
            var now = clock.instant();
            // Observe local execution evidence under the same locks and transaction as the state.
            LifecycleEntity next = switch (snapshot) {
                case Job job -> job.transition(command, facts.actor(), facts.recovery(), facts.delivery(), new AttemptEvidence(
                        command.attempt() != null && repository.ownsLiveAttempt(job.entity().id(), command.attempt(), now),
                        repository.hasLiveAttempt(job.entity().id(), now)));
                case Story story -> story.transition(command, facts.actor(), facts.delivery(),
                        command.nextState() == StoryState.PR_OPEN && command.approval() != null
                                && repository.hasSuccessfulJob(story.entity().id(), command.approval()));
                case Epic epic -> epic.transition(command, facts.actor(), facts.completion());
                default -> throw new LifecycleException(LifecycleException.Kind.INVALID_TRANSITION);
            };
            var receipt = new TransitionReceipt(command.commandId(), command.entity(), snapshot.state(), next.state(), next.version());
            repository.save(snapshot, next, command, facts, receipt, digest, now);
            return receipt;
        });
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
