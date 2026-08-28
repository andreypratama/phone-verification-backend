package com.smsverification.gateway.sms;

import com.smsverification.gateway.verification.VerificationStatus;

import java.util.UUID;

public record IncomingSmsResponse(
        UUID smsId,
        boolean duplicate,
        IncomingSmsMatchStatus matchStatus,
        UUID verificationId,
        VerificationStatus verificationStatus
) {
}
