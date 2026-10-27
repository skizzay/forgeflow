package io.forgeflow.controlplane.lifecycle.adapter.in.http;

import io.forgeflow.controlplane.lifecycle.application.port.in.RecordManualApproval;
import io.forgeflow.controlplane.lifecycle.domain.LifecycleException;
import io.forgeflow.controlplane.lifecycle.domain.approval.ApprovalIdentity;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import java.security.Principal;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@ConditionalOnProperty(prefix = "forgeflow.manual-approval", name = "enabled", havingValue = "true")
public class ManualApprovalController {
    public record Request(UUID commandId, Long expectedVersion, UUID epicId, UUID storyId, ApprovalIdentity approval,
                          String workflowKey, Integer workflowVersion, String definitionSha256) {
        @JsonAnySetter
        public void rejectUnknown(String name, Object value) { throw new IllegalArgumentException("Unknown approval field"); }
    }
    public record Receipt(UUID commandId, String state, long version) { }
    private final RecordManualApproval useCase;
    public ManualApprovalController(RecordManualApproval useCase) { this.useCase = useCase; }
    @PostMapping(path = "/api/operator/v1/plan-approval", consumes = "application/json")
    Mono<Receipt> approve(@RequestBody Request request, Principal principal) {
        return invoke(request, principal, RecordManualApproval.Action.APPROVE);
    }
    @PostMapping(path = "/api/operator/v1/plan-approval/invalidate", consumes = "application/json")
    Mono<Receipt> invalidate(@RequestBody Request request, Principal principal) {
        return invoke(request, principal, RecordManualApproval.Action.INVALIDATE);
    }
    private Mono<Receipt> invoke(Request request, Principal principal, RecordManualApproval.Action action) {
        if (request.commandId() == null || request.expectedVersion() == null || request.expectedVersion() < 0
                || (action == RecordManualApproval.Action.INVALIDATE && (request.approval() != null || request.definitionSha256() != null
                        || request.workflowKey() != null || request.workflowVersion() != null))) {
            return Mono.error(new LifecycleException(LifecycleException.Kind.INVALID_TRANSITION));
        }
        return Mono.fromCallable(() -> useCase.record(new RecordManualApproval.Command(request.commandId(), principal.getName(),
                        action, request.expectedVersion(), request.epicId(), request.storyId(), request.approval(),
                        request.workflowKey(), request.workflowVersion(), request.definitionSha256())))
                .map(receipt -> new Receipt(receipt.commandId(), receipt.to().name(), receipt.version()))
                .subscribeOn(Schedulers.boundedElastic());
    }
    @ExceptionHandler(LifecycleException.class)
    ResponseEntity<Void> rejected(LifecycleException failure) {
        int status = switch (failure.kind()) {
            case UNAUTHORIZED -> 403;
            case NOT_FOUND -> 404;
            case STORAGE_UNAVAILABLE -> 503;
            case INVALID_TRANSITION -> 400;
            default -> 409;
        };
        return ResponseEntity.status(status).build();
    }
}
