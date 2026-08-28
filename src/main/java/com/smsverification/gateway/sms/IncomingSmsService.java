package com.smsverification.gateway.sms;

import com.smsverification.gateway.api.ResourceNotFoundException;
import com.smsverification.gateway.core.crypto.HmacService;
import com.smsverification.gateway.core.phone.PhoneNumberNormalizer;
import com.smsverification.gateway.core.verification.VerificationCodeParser;
import com.smsverification.gateway.storage.InMemoryGatewayStorage;
import com.smsverification.gateway.verification.PhoneVerificationEntity;
import com.smsverification.gateway.verification.VerificationStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class IncomingSmsService {

    private static final String FINGERPRINT_SEPARATOR = "\u001f";

    private final InMemoryGatewayStorage storage;
    private final PhoneNumberNormalizer phoneNumberNormalizer;
    private final VerificationCodeParser codeParser;
    private final HmacService hmacService;
    private final Clock clock;

    public IncomingSmsService(
            InMemoryGatewayStorage storage,
            PhoneNumberNormalizer phoneNumberNormalizer,
            VerificationCodeParser codeParser,
            HmacService hmacService,
            Clock clock
    ) {
        this.storage = storage;
        this.phoneNumberNormalizer = phoneNumberNormalizer;
        this.codeParser = codeParser;
        this.hmacService = hmacService;
        this.clock = clock;
    }

    public IncomingSmsResponse receive(IncomingSmsRequest request) {
        Instant serverNow = Instant.now(clock);
        String fingerprint = fingerprint(request);

        Optional<IncomingSmsEntity> existing = storage.findIncomingSmsByFingerprint(fingerprint);
        if (existing.isPresent()) {
            return response(existing.get(), true);
        }

        String normalizedSender;
        try {
            normalizedSender = phoneNumberNormalizer.normalize(request.from());
        } catch (IllegalArgumentException exception) {
            IncomingSmsEntity invalidSender = store(
                    request,
                    fingerprint,
                    null,
                    IncomingSmsMatchStatus.INVALID_SENDER,
                    null,
                    serverNow
            );
            return response(invalidSender, false);
        }

        Optional<String> parsedCode = codeParser.parse(request.text());
        if (parsedCode.isEmpty()) {
            IncomingSmsEntity noCode = store(
                    request,
                    fingerprint,
                    normalizedSender,
                    IncomingSmsMatchStatus.NO_VERIFICATION_CODE,
                    null,
                    serverNow
            );
            return response(noCode, false);
        }

        String codeHash = hmacService.sha256Hex(parsedCode.get().getBytes(StandardCharsets.UTF_8));
        Optional<PhoneVerificationEntity> verificationOptional = storage.findVerificationByCodeHash(codeHash);
        if (verificationOptional.isEmpty()) {
            IncomingSmsEntity unknownCode = store(
                    request,
                    fingerprint,
                    normalizedSender,
                    IncomingSmsMatchStatus.UNKNOWN_CODE,
                    null,
                    serverNow
            );
            return response(unknownCode, false);
        }

        PhoneVerificationEntity verification = verificationOptional.get();
        IncomingSmsMatchStatus matchStatus;

        if (verification.expireIfDue(serverNow)
                || verification.getStatus() == VerificationStatus.EXPIRED) {
            matchStatus = IncomingSmsMatchStatus.EXPIRED_CODE;
        } else if (!verification.getPhoneNumber().equals(normalizedSender)) {
            matchStatus = IncomingSmsMatchStatus.PHONE_MISMATCH;
        } else if (verification.getStatus() == VerificationStatus.VERIFIED) {
            matchStatus = IncomingSmsMatchStatus.ALREADY_VERIFIED;
        } else {
            verification.verify(serverNow);
            matchStatus = IncomingSmsMatchStatus.MATCHED;
        }

        IncomingSmsEntity incomingSms = store(
                request,
                fingerprint,
                normalizedSender,
                matchStatus,
                verification.getId(),
                serverNow
        );
        return response(incomingSms, false, verification.getStatus());
    }

    public SmsListResponse listByVerification(UUID verificationId) {
        if (!storage.verificationExists(verificationId)) {
            throw new ResourceNotFoundException("Verification " + verificationId + " was not found");
        }

        List<SmsItemResponse> items = storage
                .findIncomingSmsByVerificationId(verificationId, 50)
                .stream()
                .map(this::toItemResponse)
                .toList();
        return new SmsListResponse(verificationId, items.size(), items);
    }

    private IncomingSmsEntity store(
            IncomingSmsRequest request,
            String fingerprint,
            String normalizedSender,
            IncomingSmsMatchStatus matchStatus,
            UUID verificationId,
            Instant serverNow
    ) {
        IncomingSmsEntity entity = new IncomingSmsEntity(
                fingerprint,
                request.from(),
                normalizedSender,
                request.text(),
                toInstant(request.sentStamp(), null),
                toInstant(request.receivedStamp(), serverNow),
                request.sim(),
                matchStatus,
                verificationId,
                serverNow
        );
        return storage.saveIncomingSms(entity);
    }

    private IncomingSmsResponse response(IncomingSmsEntity entity, boolean duplicate) {
        VerificationStatus status = null;
        if (entity.getVerificationId() != null) {
            status = storage.findVerificationById(entity.getVerificationId())
                    .map(PhoneVerificationEntity::getStatus)
                    .orElse(null);
        }
        return response(entity, duplicate, status);
    }

    private IncomingSmsResponse response(
            IncomingSmsEntity entity,
            boolean duplicate,
            VerificationStatus verificationStatus
    ) {
        return new IncomingSmsResponse(
                entity.getId(),
                duplicate,
                entity.getMatchStatus(),
                entity.getVerificationId(),
                verificationStatus
        );
    }

    private SmsItemResponse toItemResponse(IncomingSmsEntity entity) {
        return new SmsItemResponse(
                entity.getId(),
                entity.getSenderRaw(),
                entity.getSenderPhone(),
                entity.getMessageText(),
                entity.getSentAt(),
                entity.getReceivedAt(),
                entity.getSimInfo(),
                entity.getMatchStatus()
        );
    }

    private String fingerprint(IncomingSmsRequest request) {
        String canonical = String.join(
                FINGERPRINT_SEPARATOR,
                nullSafe(request.from()),
                nullSafe(request.text()),
                nullSafe(request.sentStamp()),
                nullSafe(request.receivedStamp()),
                nullSafe(request.sim())
        );
        return hmacService.sha256Hex(canonical.getBytes(StandardCharsets.UTF_8));
    }

    private String nullSafe(Object value) {
        return value == null ? "" : value.toString();
    }

    private Instant toInstant(Long epochMillis, Instant fallback) {
        if (epochMillis == null) {
            return fallback;
        }
        try {
            return Instant.ofEpochMilli(epochMillis);
        } catch (DateTimeException exception) {
            return fallback;
        }
    }
}
