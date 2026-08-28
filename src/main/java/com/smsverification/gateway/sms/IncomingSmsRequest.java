package com.smsverification.gateway.sms;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record IncomingSmsRequest(
        @JsonProperty("from") @NotBlank @Size(max = 64) String from,
        @NotBlank @Size(max = 2000) String text,
        Long sentStamp,
        Long receivedStamp,
        @Size(max = 128) String sim
) {
}
