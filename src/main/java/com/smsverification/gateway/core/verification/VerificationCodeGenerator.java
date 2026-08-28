package com.smsverification.gateway.core.verification;

import java.security.SecureRandom;
import java.util.Objects;

public final class VerificationCodeGenerator {

    public static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final int length;
    private final SecureRandom secureRandom;

    public VerificationCodeGenerator(int length, SecureRandom secureRandom) {
        if (length < 6 || length > 32) {
            throw new IllegalArgumentException("Verification code length must be between 6 and 32");
        }
        this.length = length;
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom");
    }

    public String generate() {
        StringBuilder result = new StringBuilder(length);
        for (int index = 0; index < length; index++) {
            result.append(ALPHABET.charAt(secureRandom.nextInt(ALPHABET.length())));
        }
        return result.toString();
    }
}
