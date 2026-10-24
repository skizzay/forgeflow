package io.forgeflow.controlplane.webhook.adapter.out.security;

import io.forgeflow.controlplane.webhook.application.port.out.VerifyWebhookSignature;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class HmacWebhookSignature implements VerifyWebhookSignature {
    private final byte[] secret;
    public HmacWebhookSignature(String secret) { this.secret = secret.getBytes(StandardCharsets.UTF_8); }
    @Override
    public boolean valid(String signature, byte[] payload) {
        if (signature == null || !signature.matches("sha256=[0-9a-f]{64}")) return false;
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return MessageDigest.isEqual(mac.doFinal(payload), HexFormat.of().parseHex(signature.substring(7)));
        } catch (GeneralSecurityException unavailable) { throw new IllegalStateException("HMAC-SHA256 unavailable", unavailable); }
    }
}
