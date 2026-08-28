package com.smsverification.gateway.security;

import java.time.Instant;

public class HmacNonceEntity {

    private String nonce;

    private Instant usedAt;

    private Instant expiresAt;

    protected HmacNonceEntity() {
    }

    public HmacNonceEntity(String nonce, Instant usedAt, Instant expiresAt) {
        this.nonce = nonce;
        this.usedAt = usedAt;
        this.expiresAt = expiresAt;
    }

    public String getNonce() {
        return nonce;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
