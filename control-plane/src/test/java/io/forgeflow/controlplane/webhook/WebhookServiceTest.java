package io.forgeflow.controlplane.webhook;

import io.forgeflow.controlplane.webhook.application.WebhookService;
import io.forgeflow.controlplane.webhook.application.port.in.SignedWebhook;
import io.forgeflow.controlplane.webhook.application.port.out.DecodeWebhookPayload;
import io.forgeflow.controlplane.webhook.application.port.out.TransactionBoundary;
import io.forgeflow.controlplane.webhook.application.port.out.VerifyWebhookSignature;
import io.forgeflow.controlplane.webhook.application.port.out.WebhookInboxRepository;
import io.forgeflow.controlplane.webhook.domain.DeliveryIdentity;
import io.forgeflow.controlplane.webhook.domain.Reception;
import io.forgeflow.controlplane.webhook.domain.WebhookEventKind;
import io.forgeflow.controlplane.webhook.domain.WebhookException;
import io.forgeflow.controlplane.webhook.domain.WebhookMetadata;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Runs the use cases without Spring, Reactor, JSON, JDBC or a database. */
class WebhookServiceTest {
    private final WebhookInboxRepository inbox = mock(WebhookInboxRepository.class);
    private final VerifyWebhookSignature signatures = mock(VerifyWebhookSignature.class);
    private final DecodeWebhookPayload payloads = mock(DecodeWebhookPayload.class);
    private final UUID id = UUID.randomUUID();
    private final TransactionBoundary transactions = new TransactionBoundary() {
        @Override public <T> T required(Supplier<T> work) { return work.get(); }
    };
    private final WebhookService service = new WebhookService(inbox, transactions, signatures, payloads);

    @BeforeEach
    void prepare() {
        when(signatures.valid(anyString(), any())).thenReturn(true);
        when(payloads.decode(anyString(), any())).thenReturn(new WebhookMetadata(null, null, null, null, null));
    }

    private SignedWebhook command(String event) {
        return new SignedWebhook(id.toString(), event, "synthetic-signature", "{}".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void rejectedSignatureCannotReachDecoderOrStorage() {
        when(signatures.valid(anyString(), any())).thenReturn(false);
        assertThatThrownBy(() -> service.receive(command("ping"))).isInstanceOf(WebhookException.class)
                .extracting(error -> ((WebhookException) error).kind()).isEqualTo(WebhookException.Kind.INVALID_SIGNATURE);
        verifyNoInteractions(payloads, inbox);
    }

    @Test
    void reusedIdentityWithDifferentContentIsAConflict() {
        when(inbox.findIdentity(id)).thenReturn(new DeliveryIdentity(id, "ping", "0".repeat(64)));
        assertThatThrownBy(() -> service.receive(command("ping"))).isInstanceOf(WebhookException.class)
                .extracting(error -> ((WebhookException) error).kind()).isEqualTo(WebhookException.Kind.DELIVERY_CONFLICT);
    }

    @Test
    void sameIdentityIsADuplicateWithoutCreatingAnotherDelivery() throws Exception {
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest("{}".getBytes(StandardCharsets.UTF_8)));
        when(inbox.findIdentity(id)).thenReturn(new DeliveryIdentity(id, "ping", digest));
        assertThat(service.receive(command("ping"))).isEqualTo(Reception.DUPLICATE);
    }

    @Test
    void issueWithoutRoutingIdentityCannotReachStorage() {
        assertThatThrownBy(() -> service.receive(command("issues"))).isInstanceOf(WebhookException.class)
                .extracting(error -> ((WebhookException) error).kind()).isEqualTo(WebhookException.Kind.INVALID_DELIVERY);
        verifyNoInteractions(inbox);
    }

    @Test
    void unknownEventIsClassifiedWithoutAnyExecutionPolicy() {
        when(inbox.claimNext()).thenReturn(Optional.of(new DeliveryIdentity(id, "push", "0".repeat(64))), Optional.empty());
        assertThat(service.processOne()).isTrue();
        assertThat(service.processOne()).isFalse();
        verify(inbox).publish(id, WebhookEventKind.UNSUPPORTED);
        verify(inbox).markProcessed(id);
    }
}
