package com.smsverification.gateway.sms;

import java.util.List;
import java.util.UUID;

public record SmsListResponse(
        UUID verificationId,
        int count,
        List<SmsItemResponse> items
) {
}
