package com.smsverification.gateway.security;

import com.smsverification.gateway.storage.InMemoryGatewayStorage;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class HmacNonceService {

    private final InMemoryGatewayStorage storage;

    public HmacNonceService(InMemoryGatewayStorage storage) {
        this.storage = storage;
    }

    public boolean claim(String nonce, Instant usedAt, Instant expiresAt) {
        return storage.claimNonce(nonce, usedAt, expiresAt);
    }

    @Scheduled(fixedDelayString = "${app.security.nonce-cleanup-millis:600000}")
    public void removeExpiredNonces() {
        storage.removeExpiredNonces(Instant.now());
    }
}
