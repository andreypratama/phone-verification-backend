package com.smsverification.gateway.core.crypto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HmacServiceTest {

    private final HmacService hmacService = new HmacService();

    @Test
    void createsKnownHmacSha256Vector() {
        String signature = hmacService.hmacSha256Hex(
                "key",
                "The quick brown fox jumps over the lazy dog".getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );

        assertThat(signature)
                .isEqualTo("f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8");
    }

    @Test
    void createsKnownSha256Vector() {
        assertThat(hmacService.sha256Hex("abc".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void comparesHexSignaturesInConstantTimeAndAcceptsUppercase() {
        String expected = "f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8";

        assertThat(hmacService.constantTimeHexEquals(expected, expected.toUpperCase())).isTrue();
        assertThat(hmacService.constantTimeHexEquals(expected, "not-hex")).isFalse();
        assertThat(hmacService.constantTimeHexEquals(expected, null)).isFalse();
    }
}
