package com.smsverification.gateway.core.phone;

import java.util.Objects;
import java.util.regex.Pattern;

public final class PhoneNumberNormalizer {

    private static final Pattern ALLOWED_INPUT = Pattern.compile("[+0-9\\s().-]+");
    private static final Pattern E164 = Pattern.compile("^\\+[1-9][0-9]{7,14}$");

    private final String defaultCountryCode;

    public PhoneNumberNormalizer(String defaultCountryCode) {
        String digits = Objects.requireNonNull(defaultCountryCode, "defaultCountryCode")
                .replaceAll("\\D", "");
        if (digits.isBlank() || digits.startsWith("0")) {
            throw new IllegalArgumentException("Default country code must contain digits and must not start with zero");
        }
        this.defaultCountryCode = digits;
    }

    public String normalize(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Phone number must not be blank");
        }

        String trimmed = input.trim();
        if (!ALLOWED_INPUT.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("Phone number contains unsupported characters");
        }
        int firstPlus = trimmed.indexOf('+');
        if (firstPlus > 0 || (firstPlus == 0 && trimmed.indexOf('+', 1) >= 0)) {
            throw new IllegalArgumentException("Plus sign is only allowed once at the beginning");
        }

        boolean explicitlyInternational = firstPlus == 0;
        String digits = trimmed.replaceAll("\\D", "");
        if (digits.isBlank()) {
            throw new IllegalArgumentException("Phone number must contain digits");
        }

        String normalized;
        if (explicitlyInternational) {
            normalized = "+" + digits;
        } else if (digits.startsWith("0")) {
            normalized = "+" + defaultCountryCode + digits.substring(1);
        } else {
            normalized = "+" + digits;
        }

        if (!E164.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Phone number is not a valid E.164 number");
        }
        return normalized;
    }
}
