package com.smsverification.gateway.core.crypto;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class HmacService {

    private static final String HMAC_SHA_256 = "HmacSHA256";
    private static final HexFormat HEX = HexFormat.of();

    public String hmacSha256Hex(String secret, byte[] payload) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("HMAC secret must not be blank");
        }
        if (payload == null) {
            throw new IllegalArgumentException("HMAC payload must not be null");
        }

        try {
            Mac mac = Mac.getInstance(HMAC_SHA_256);
            mac.init(new SecretKeySpec(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8), HMAC_SHA_256));
            return HEX.formatHex(mac.doFinal(payload));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HmacSHA256 is not available", exception);
        }
    }

    public String sha256Hex(byte[] payload) {
        if (payload == null) {
            throw new IllegalArgumentException("SHA-256 payload must not be null");
        }

        try {
            return HEX.formatHex(MessageDigest.getInstance("SHA-256").digest(payload));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    public boolean constantTimeHexEquals(String expectedHex, String suppliedHex) {
        if (expectedHex == null || suppliedHex == null) {
            return false;
        }

        try {
            byte[] expected = HEX.parseHex(expectedHex.toLowerCase(java.util.Locale.ROOT));
            byte[] supplied = HEX.parseHex(suppliedHex.toLowerCase(java.util.Locale.ROOT));
            return MessageDigest.isEqual(expected, supplied);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
