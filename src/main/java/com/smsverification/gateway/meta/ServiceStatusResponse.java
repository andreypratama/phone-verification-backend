package com.smsverification.gateway.meta;

import java.time.Instant;

public record ServiceStatusResponse(
        String status,
        String service,
        Instant timestamp
) {
}
