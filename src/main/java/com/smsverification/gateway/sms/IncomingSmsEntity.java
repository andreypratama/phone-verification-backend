package com.smsverification.gateway.sms;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class IncomingSmsEntity {

    private UUID id;

    private String fingerprint;

    private String senderRaw;

    private String senderPhone;

    private String messageText;

    private Instant sentAt;

    private Instant receivedAt;

    private String simInfo;

    private IncomingSmsMatchStatus matchStatus;

    private UUID verificationId;

    private Instant createdAt;

    protected IncomingSmsEntity() {
    }

    public IncomingSmsEntity(
            String fingerprint,
            String senderRaw,
            String senderPhone,
            String messageText,
            Instant sentAt,
            Instant receivedAt,
            String simInfo,
            IncomingSmsMatchStatus matchStatus,
            UUID verificationId,
            Instant createdAt
    ) {
        this.id = UUID.randomUUID();
        this.fingerprint = Objects.requireNonNull(fingerprint, "fingerprint");
        this.senderRaw = Objects.requireNonNull(senderRaw, "senderRaw");
        this.senderPhone = senderPhone;
        this.messageText = Objects.requireNonNull(messageText, "messageText");
        this.sentAt = sentAt;
        this.receivedAt = Objects.requireNonNull(receivedAt, "receivedAt");
        this.simInfo = simInfo;
        this.matchStatus = Objects.requireNonNull(matchStatus, "matchStatus");
        this.verificationId = verificationId;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public UUID getId() {
        return id;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public String getSenderRaw() {
        return senderRaw;
    }

    public String getSenderPhone() {
        return senderPhone;
    }

    public String getMessageText() {
        return messageText;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public String getSimInfo() {
        return simInfo;
    }

    public IncomingSmsMatchStatus getMatchStatus() {
        return matchStatus;
    }

    public UUID getVerificationId() {
        return verificationId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
