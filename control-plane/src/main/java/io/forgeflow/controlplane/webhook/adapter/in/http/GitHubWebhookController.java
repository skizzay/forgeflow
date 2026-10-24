package io.forgeflow.controlplane.webhook.adapter.in.http;

import io.forgeflow.controlplane.webhook.application.port.in.ReceiveWebhook;
import io.forgeflow.controlplane.webhook.application.port.in.SignedWebhook;
import io.forgeflow.controlplane.webhook.domain.Reception;
import io.forgeflow.controlplane.webhook.domain.WebhookException;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@ConditionalOnProperty(name = "forgeflow.github.webhook.enabled", havingValue = "true")
class GitHubWebhookController {
    private final ReceiveWebhook receiveWebhook;
    private final int maxBytes;
    GitHubWebhookController(ReceiveWebhook receiveWebhook,
            @Value("${forgeflow.github.webhook.max-bytes}") int maxBytes) {
        this.receiveWebhook = receiveWebhook; this.maxBytes = maxBytes;
    }
    @PostMapping(path = "/webhooks/github", consumes = "application/json")
    Mono<ResponseEntity<Void>> receive(ServerHttpRequest request) {
        if (request.getHeaders().getContentLength() > maxBytes) return Mono.just(ResponseEntity.status(413).build());
        String encoding = request.getHeaders().getFirst(HttpHeaders.CONTENT_ENCODING);
        if (encoding != null && !encoding.equalsIgnoreCase("identity")) return Mono.just(ResponseEntity.status(415).build());
        return DataBufferUtils.join(request.getBody(), maxBytes).map(buffer -> {
            try {
                byte[] bytes = new byte[buffer.readableByteCount()];
                buffer.read(bytes);
                return bytes;
            } finally { DataBufferUtils.release(buffer); }
        }).defaultIfEmpty(new byte[0])
                .flatMap(body -> Mono.fromCallable(() -> receiveWebhook.receive(command(request.getHeaders(), body)))
                        .map(reception -> ResponseEntity.status(reception == Reception.CREATED ? HttpStatus.ACCEPTED : HttpStatus.OK).<Void>build())
                        .subscribeOn(Schedulers.boundedElastic()))
                .onErrorResume(DataBufferLimitException.class, error -> Mono.just(ResponseEntity.status(413).build()))
                .onErrorResume(WebhookException.class, error -> Mono.just(ResponseEntity.status(status(error.kind())).build()));
    }

    private static SignedWebhook command(HttpHeaders headers, byte[] body) {
        String signature = single(headers, "X-Hub-Signature-256");
        // Missing protocol headers are HTTP concerns; authentication/identity policy is in the use case.
        String delivery = optionalSingle(headers, "X-GitHub-Delivery");
        String event = optionalSingle(headers, "X-GitHub-Event");
        return new SignedWebhook(delivery, event, signature, body);
    }
    private static String optionalSingle(HttpHeaders headers, String name) {
        List<String> values = headers.get(name);
        if (values == null) return null;
        if (values.size() != 1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Webhook request rejected");
        return values.getFirst();
    }
    private static String single(HttpHeaders headers, String name) {
        List<String> values = headers.get(name);
        if (values == null || values.size() != 1) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Webhook request rejected");
        }
        return values.getFirst();
    }
    private static HttpStatus status(WebhookException.Kind kind) {
        return switch (kind) {
            case INVALID_SIGNATURE -> HttpStatus.UNAUTHORIZED;
            case INVALID_DELIVERY -> HttpStatus.BAD_REQUEST;
            case DELIVERY_CONFLICT -> HttpStatus.CONFLICT;
            case STORAGE_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
        };
    }
}
