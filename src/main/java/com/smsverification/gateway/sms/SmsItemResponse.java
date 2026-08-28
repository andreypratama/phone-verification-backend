package com.smsverification.gateway.sms;

import java.time.Instant;
import java.util.UUID;

public record SmsItemResponse(
        UUID smsId,
        String senderRaw,
        String senderPhone,
        String text,
        Instant sentAt,
        Instant receivedAt,
        String sim,
        IncomingSmsMatchStatus matchStatus
) {
}
