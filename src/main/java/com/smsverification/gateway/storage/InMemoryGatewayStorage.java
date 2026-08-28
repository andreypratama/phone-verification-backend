package com.smsverification.gateway.storage;

import com.smsverification.gateway.security.HmacNonceEntity;
import com.smsverification.gateway.sms.IncomingSmsEntity;
import com.smsverification.gateway.verification.PhoneVerificationEntity;
import com.smsverification.gateway.verification.VerificationStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryGatewayStorage {

    private final ConcurrentHashMap<UUID, PhoneVerificationEntity> verificationsById = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, UUID> verificationIdsByCodeHash = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, IncomingSmsEntity> incomingSmsById = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, UUID> incomingSmsIdsByFingerprint = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, HmacNonceEntity> noncesByValue = new ConcurrentHashMap<>();

    public synchronized List<PhoneVerificationEntity> findVerificationsByPhoneAndStatus(
            String phoneNumber,
            VerificationStatus status
    ) {
        return verificationsById.values().stream()
                .filter(verification -> verification.getPhoneNumber().equals(phoneNumber))
                .filter(verification -> verification.getStatus() == status)
                .sorted(Comparator.comparing(PhoneVerificationEntity::getCreatedAt))
                .toList();
    }

    public boolean verificationCodeHashExists(String codeHash) {
        return verificationIdsByCodeHash.containsKey(codeHash);
    }

    public synchronized PhoneVerificationEntity saveVerification(PhoneVerificationEntity verification) {
        verificationsById.put(verification.getId(), verification);
        verificationIdsByCodeHash.put(verification.getCodeHash(), verification.getId());
        return verification;
    }

    public Optional<PhoneVerificationEntity> findVerificationById(UUID verificationId) {
        return Optional.ofNullable(verificationsById.get(verificationId));
    }

    public Optional<PhoneVerificationEntity> findVerificationByCodeHash(String codeHash) {
        UUID verificationId = verificationIdsByCodeHash.get(codeHash);
        if (verificationId == null) {
            return Optional.empty();
        }
        return findVerificationById(verificationId);
    }

    public boolean verificationExists(UUID verificationId) {
        return verificationsById.containsKey(verificationId);
    }

    public synchronized Optional<IncomingSmsEntity> findIncomingSmsByFingerprint(String fingerprint) {
        UUID smsId = incomingSmsIdsByFingerprint.get(fingerprint);
        if (smsId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(incomingSmsById.get(smsId));
    }

    public synchronized IncomingSmsEntity saveIncomingSms(IncomingSmsEntity incomingSms) {
        incomingSmsById.put(incomingSms.getId(), incomingSms);
        incomingSmsIdsByFingerprint.put(incomingSms.getFingerprint(), incomingSms.getId());
        return incomingSms;
    }

    public List<IncomingSmsEntity> findIncomingSmsByVerificationId(UUID verificationId, int limit) {
        return incomingSmsById.values().stream()
                .filter(incomingSms -> verificationId.equals(incomingSms.getVerificationId()))
                .sorted(Comparator.comparing(IncomingSmsEntity::getReceivedAt).reversed())
                .limit(limit)
                .toList();
    }

    public boolean claimNonce(String nonce, Instant usedAt, Instant expiresAt) {
        HmacNonceEntity entity = new HmacNonceEntity(nonce, usedAt, expiresAt);
        return noncesByValue.putIfAbsent(nonce, entity) == null;
    }

    public synchronized void removeExpiredNonces(Instant threshold) {
        noncesByValue.entrySet().removeIf(entry -> entry.getValue().getExpiresAt().isBefore(threshold));
    }

    public synchronized List<PhoneVerificationEntity> findAllVerifications() {
        return verificationsById.values().stream()
                .sorted(Comparator.comparing(PhoneVerificationEntity::getCreatedAt))
                .toList();
    }

    public synchronized List<IncomingSmsEntity> findAllIncomingSms() {
        return incomingSmsById.values().stream()
                .sorted(Comparator.comparing(IncomingSmsEntity::getCreatedAt))
                .toList();
    }

    public synchronized List<HmacNonceEntity> findAllNonces() {
        return noncesByValue.values().stream()
                .sorted(Comparator.comparing(HmacNonceEntity::getUsedAt))
                .toList();
    }

    public synchronized void clear() {
        verificationsById.clear();
        verificationIdsByCodeHash.clear();
        incomingSmsById.clear();
        incomingSmsIdsByFingerprint.clear();
        noncesByValue.clear();
    }
}
