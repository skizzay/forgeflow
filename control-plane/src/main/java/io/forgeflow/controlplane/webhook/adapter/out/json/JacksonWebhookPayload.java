package io.forgeflow.controlplane.webhook.adapter.out.json;

import io.forgeflow.controlplane.webhook.application.port.out.DecodeWebhookPayload;
import io.forgeflow.controlplane.webhook.domain.WebhookException;
import io.forgeflow.controlplane.webhook.domain.WebhookMetadata;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

public final class JacksonWebhookPayload implements DecodeWebhookPayload {
    private final JsonMapper mapper = JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    @Override
    public WebhookMetadata decode(String eventType, byte[] body) {
        JsonNode payload;
        try { payload = mapper.readTree(body); }
        catch (RuntimeException invalid) { throw reject(); }
        if (payload == null || !payload.isObject()) throw reject();
        Long repository = id(payload.path("repository").path("id"));
        Long installation = id(payload.path("installation").path("id"));
        String subject = eventType.equals("issues") ? "issue" : eventType.equals("pull_request") ? "pull_request" : null;
        if (subject == null) return new WebhookMetadata(repository, installation, null, null, null);
        JsonNode action = payload.path("action");
        if (!action.isString()) throw reject();
        return new WebhookMetadata(repository, installation, id(payload.path(subject).path("id")),
                id(payload.path(subject).path("number")), action.stringValue());
    }
    private static Long id(JsonNode value) {
        if (value.isMissingNode()) return null;
        if (!value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() <= 0) throw reject();
        return value.longValue();
    }
    private static WebhookException reject() { return new WebhookException(WebhookException.Kind.INVALID_DELIVERY); }
}
