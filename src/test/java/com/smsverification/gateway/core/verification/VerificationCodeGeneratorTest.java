package com.smsverification.gateway.core.verification;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class VerificationCodeGeneratorTest {

    @Test
    void generatesCodesWithExpectedLengthAndUnambiguousAlphabet() {
        VerificationCodeGenerator generator = new VerificationCodeGenerator(8, new SecureRandom());
        Set<String> generated = new HashSet<>();

        for (int i = 0; i < 100; i++) {
            String code = generator.generate();
            assertThat(code).matches("[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{8}");
            generated.add(code);
        }

        assertThat(generated).hasSizeGreaterThan(95);
    }
}
