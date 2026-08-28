package com.smsverification.gateway.core.verification;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class VerificationCodeParser {

    private final Pattern messagePattern;

    public VerificationCodeParser(String prefix, int codeLength) {
        if (codeLength < 4 || codeLength > 32) {
            throw new IllegalArgumentException("Code length must be between 4 and 32");
        }
        String normalizedPrefix = Objects.requireNonNull(prefix, "prefix").trim();
        if (normalizedPrefix.isBlank()) {
            throw new IllegalArgumentException("Verification prefix must not be blank");
        }
        this.messagePattern = Pattern.compile(
                "^\\s*" + Pattern.quote(normalizedPrefix) + "\\s+([A-Z0-9]{" + codeLength + "})\\s*$",
                Pattern.CASE_INSENSITIVE
        );
    }

    public Optional<String> parse(String message) {
        if (message == null) {
            return Optional.empty();
        }
        Matcher matcher = messagePattern.matcher(message);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        return Optional.of(matcher.group(1).toUpperCase(Locale.ROOT));
    }
}
