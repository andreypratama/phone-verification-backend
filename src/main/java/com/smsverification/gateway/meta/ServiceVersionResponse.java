package com.smsverification.gateway.meta;

import java.time.Instant;

public record ServiceVersionResponse(
        String service,
        String version,
        Instant timestamp
) {
}
