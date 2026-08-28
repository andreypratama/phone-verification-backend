package com.smsverification.gateway.verification;

import java.time.Instant;
import java.util.UUID;

public record VerificationStatusResponse(
        UUID verificationId,
        String phoneNumber,
        VerificationStatus status,
        Instant createdAt,
        Instant expiresAt,
        Instant verifiedAt
) {
}
