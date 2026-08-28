package com.smsverification.gateway.verification;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class PhoneVerificationEntity {

    private UUID id;

    private String phoneNumber;

    private String codeHash;

    private VerificationStatus status;

    private Instant createdAt;

    private Instant expiresAt;

    private Instant verifiedAt;

    protected PhoneVerificationEntity() {
    }

    private PhoneVerificationEntity(
            UUID id,
            String phoneNumber,
            String codeHash,
            VerificationStatus status,
            Instant createdAt,
            Instant expiresAt
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.phoneNumber = Objects.requireNonNull(phoneNumber, "phoneNumber");
        this.codeHash = Objects.requireNonNull(codeHash, "codeHash");
        this.status = Objects.requireNonNull(status, "status");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
    }

    public static PhoneVerificationEntity pending(
            String phoneNumber,
            String codeHash,
            Instant createdAt,
            Instant expiresAt
    ) {
        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("Verification expiration must be after creation time");
        }
        return new PhoneVerificationEntity(
                UUID.randomUUID(),
                phoneNumber,
                codeHash,
                VerificationStatus.PENDING,
                createdAt,
                expiresAt
        );
    }

    public boolean expireIfDue(Instant now) {
        if (status == VerificationStatus.PENDING && !expiresAt.isAfter(now)) {
            status = VerificationStatus.EXPIRED;
            return true;
        }
        return false;
    }

    public boolean expire() {
        if (status == VerificationStatus.PENDING) {
            status = VerificationStatus.EXPIRED;
            return true;
        }
        return false;
    }

    public boolean verify(Instant verifiedAt) {
        if (status != VerificationStatus.PENDING) {
            return false;
        }
        this.status = VerificationStatus.VERIFIED;
        this.verifiedAt = Objects.requireNonNull(verifiedAt, "verifiedAt");
        return true;
    }

    public UUID getId() {
        return id;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public VerificationStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }
}
