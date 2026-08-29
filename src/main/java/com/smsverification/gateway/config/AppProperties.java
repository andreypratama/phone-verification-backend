package com.smsverification.gateway.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "app")
@Validated
public record AppProperties(
        @Valid @NotNull Gateway gateway,
        @Valid @NotNull Phone phone,
        @Valid @NotNull Verification verification,
        @Valid @NotNull Security security,
        @Valid @NotNull Cors cors
) {

    public record Gateway(
            @NotBlank String destinationPhoneNumber
    ) {
    }

    public record Phone(
            @NotBlank String defaultCountryCode
    ) {
    }

    public record Verification(
            @NotBlank @Size(max = 20) String prefix,
            @Min(6) @Max(32) int codeLength,
            @NotNull Duration ttl
    ) {
        public Verification {
            if (ttl != null && (ttl.isZero() || ttl.isNegative())) {
                throw new IllegalArgumentException("Verification TTL must be greater than zero");
            }
        }
    }

    public record Security(
            @NotBlank @Size(min = 32) String apiHmacSecret,
            @NotBlank @Size(min = 32) String smsForwarderHmacSecret,
            @NotNull Duration allowedClockSkew,
            @Min(60000) long nonceCleanupMillis
    ) {
        public Security {
            if (allowedClockSkew != null && (allowedClockSkew.isZero() || allowedClockSkew.isNegative())) {
                throw new IllegalArgumentException("Allowed HMAC clock skew must be greater than zero");
            }
        }
    }

    public record Cors(
            @NotEmpty List<@NotBlank String> allowedOrigins
    ) {
    }
}
