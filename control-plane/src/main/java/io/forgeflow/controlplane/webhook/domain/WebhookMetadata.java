package io.forgeflow.controlplane.webhook.domain;

public record WebhookMetadata(Long repositoryId, Long installationId, Long subjectId,
                              Long subjectNumber, String action) {
    public WebhookMetadata {
        positive(repositoryId); positive(installationId); positive(subjectId); positive(subjectNumber);
        if (action != null && !action.matches("[a-z][a-z0-9_]{0,63}")) invalid();
    }
    private static void positive(Long value) { if (value != null && value <= 0) invalid(); }
    private static void invalid() { throw new WebhookException(WebhookException.Kind.INVALID_DELIVERY); }
    public boolean hasSubject() {
        return repositoryId != null && installationId != null && subjectId != null && subjectNumber != null && action != null;
    }
}
