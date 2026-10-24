package io.forgeflow.controlplane.webhook;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.TransactionException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@RestController
@ConditionalOnProperty(name = "forgeflow.github.webhook.enabled", havingValue = "true")
class GitHubWebhookController {
    private final WebhookSettings settings;
    private final WebhookInbox inbox;
    private final JsonMapper mapper = JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();

    GitHubWebhookController(WebhookSettings settings, WebhookInbox inbox) {
        this.settings = settings;
        this.inbox = inbox;
    }

    @PostMapping(path = "/webhooks/github", consumes = "application/json")
    Mono<ResponseEntity<Void>> receive(ServerHttpRequest request) {
        if (request.getHeaders().getContentLength() > settings.maxBytes()) return Mono.just(ResponseEntity.status(413).build());
        String encoding = request.getHeaders().getFirst(HttpHeaders.CONTENT_ENCODING);
        if (encoding != null && !encoding.equalsIgnoreCase("identity")) return Mono.just(ResponseEntity.status(415).build());
        return DataBufferUtils.join(request.getBody(), settings.maxBytes()).map(buffer -> {
            try {
                byte[] bytes = new byte[buffer.readableByteCount()];
                buffer.read(bytes);
                return bytes;
            } finally { DataBufferUtils.release(buffer); }
        }).defaultIfEmpty(new byte[0])
                .flatMap(body -> Mono.fromCallable(() -> verifyAndStore(request.getHeaders(), body))
                        .subscribeOn(Schedulers.boundedElastic()))
                .onErrorResume(DataBufferLimitException.class, error -> Mono.just(ResponseEntity.status(413).build()))
                .onErrorResume(DataAccessException.class, error -> Mono.just(ResponseEntity.status(503).build()))
                .onErrorResume(TransactionException.class, error -> Mono.just(ResponseEntity.status(503).build()));
    }

    private ResponseEntity<Void> verifyAndStore(HttpHeaders headers, byte[] body) throws GeneralSecurityException {
        String signature = single(headers, "X-Hub-Signature-256");
        if (!signature.matches("sha256=[0-9a-f]{64}")) throw reject(HttpStatus.UNAUTHORIZED);
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(settings.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        if (!MessageDigest.isEqual(mac.doFinal(body), HexFormat.of().parseHex(signature.substring(7)))) {
            throw reject(HttpStatus.UNAUTHORIZED);
        }
        String id = single(headers, "X-GitHub-Delivery");
        UUID delivery;
        try { delivery = UUID.fromString(id); }
        catch (IllegalArgumentException invalid) { throw reject(HttpStatus.BAD_REQUEST); }
        if (!delivery.toString().equalsIgnoreCase(id)) throw reject(HttpStatus.BAD_REQUEST);
        String event = single(headers, "X-GitHub-Event");
        if (!event.matches("[a-z][a-z0-9_]{0,63}")) throw reject(HttpStatus.BAD_REQUEST);
        JsonNode payload;
        try { payload = mapper.readTree(body); }
        catch (RuntimeException invalid) { throw reject(HttpStatus.BAD_REQUEST); }
        if (payload == null || !payload.isObject()) throw reject(HttpStatus.BAD_REQUEST);
        var metadata = new LinkedHashMap<String, Object>();
        addId(metadata, "repository_id", payload.path("repository").path("id"));
        addId(metadata, "installation_id", payload.path("installation").path("id"));
        String subject = event.equals("issues") ? "issue" : event.equals("pull_request") ? "pull_request" : null;
        if (subject != null) {
            JsonNode action = payload.path("action");
            if (!action.isString() || !action.stringValue().matches("[a-z][a-z0-9_]{0,63}")) throw reject(HttpStatus.BAD_REQUEST);
            metadata.put("action", action.stringValue());
            addId(metadata, "subject_id", payload.path(subject).path("id"));
            addId(metadata, "subject_number", payload.path(subject).path("number"));
            if (!metadata.keySet().containsAll(List.of("repository_id", "installation_id", "subject_id", "subject_number"))) {
                throw reject(HttpStatus.BAD_REQUEST);
            }
        }
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
        boolean created = inbox.receive(delivery, event, digest, body.length, mapper.writeValueAsString(metadata));
        return ResponseEntity.status(created ? HttpStatus.ACCEPTED : HttpStatus.OK).build();
    }

    private static void addId(Map<String, Object> metadata, String key, JsonNode value) {
        if (value.isMissingNode()) return;
        if (!value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() <= 0) throw reject(HttpStatus.BAD_REQUEST);
        metadata.put(key, value.longValue());
    }

    private static String single(HttpHeaders headers, String name) {
        List<String> values = headers.get(name);
        if (values == null || values.size() != 1) throw reject(name.equals("X-Hub-Signature-256") ? HttpStatus.UNAUTHORIZED : HttpStatus.BAD_REQUEST);
        return values.getFirst();
    }

    private static ResponseStatusException reject(HttpStatus status) {
        return new ResponseStatusException(status, "Webhook request rejected");
    }
}
