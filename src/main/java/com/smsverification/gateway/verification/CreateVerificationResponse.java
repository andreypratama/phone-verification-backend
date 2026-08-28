package com.smsverification.gateway.verification;

import java.time.Instant;
import java.util.UUID;

public record CreateVerificationResponse(
        UUID verificationId,
        String phoneNumber,
        String code,
        String smsText,
        String destinationNumber,
        VerificationStatus status,
        Instant expiresAt
) {
}
