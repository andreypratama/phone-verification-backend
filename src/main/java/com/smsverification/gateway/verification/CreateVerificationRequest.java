package com.smsverification.gateway.verification;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateVerificationRequest(
        @NotBlank @Size(max = 32) String phoneNumber
) {
}
