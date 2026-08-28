package com.smsverification.gateway.core.verification;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VerificationCodeParserTest {

    private final VerificationCodeParser parser = new VerificationCodeParser("VERIF", 8);

    @Test
    void parsesStrictCodeMessageCaseInsensitively() {
        assertThat(parser.parse("  verif  7KM4P2QX  ")).contains("7KM4P2QX");
    }

    @Test
    void rejectsExtraTextAndWrongLength() {
        assertThat(parser.parse("Kode saya VERIF 7KM4P2QX")).isEmpty();
        assertThat(parser.parse("VERIF ABC123")).isEmpty();
        assertThat(parser.parse("VERIF ABCD-123")).isEmpty();
    }
}
