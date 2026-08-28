package com.smsverification.gateway.verification;

import com.smsverification.gateway.api.ResourceNotFoundException;
import com.smsverification.gateway.config.AppProperties;
import com.smsverification.gateway.core.crypto.HmacService;
import com.smsverification.gateway.core.phone.PhoneNumberNormalizer;
import com.smsverification.gateway.core.verification.VerificationCodeGenerator;
import com.smsverification.gateway.storage.InMemoryGatewayStorage;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class VerificationService {

    private static final int MAX_CODE_GENERATION_ATTEMPTS = 20;

    private final InMemoryGatewayStorage storage;
    private final PhoneNumberNormalizer phoneNumberNormalizer;
    private final VerificationCodeGenerator codeGenerator;
    private final HmacService hmacService;
    private final AppProperties properties;
    private final Clock clock;

    public VerificationService(
            InMemoryGatewayStorage storage,
            PhoneNumberNormalizer phoneNumberNormalizer,
            VerificationCodeGenerator codeGenerator,
            HmacService hmacService,
            AppProperties properties,
            Clock clock
    ) {
        this.storage = storage;
        this.phoneNumberNormalizer = phoneNumberNormalizer;
        this.codeGenerator = codeGenerator;
        this.hmacService = hmacService;
        this.properties = properties;
        this.clock = clock;
    }

    public CreateVerificationResponse create(CreateVerificationRequest request) {
        String normalizedPhoneNumber = phoneNumberNormalizer.normalize(request.phoneNumber());
        Instant now = Instant.now(clock);

        var pendingVerifications = storage.findVerificationsByPhoneAndStatus(
                normalizedPhoneNumber,
                VerificationStatus.PENDING
        );
        pendingVerifications.forEach(PhoneVerificationEntity::expire);

        GeneratedCode generatedCode = generateUniqueCode();
        PhoneVerificationEntity verification = PhoneVerificationEntity.pending(
                normalizedPhoneNumber,
                generatedCode.hash(),
                now,
                now.plus(properties.verification().ttl())
        );
        storage.saveVerification(verification);

        String smsText = properties.verification().prefix() + " " + generatedCode.plaintext();
        return new CreateVerificationResponse(
                verification.getId(),
                verification.getPhoneNumber(),
                generatedCode.plaintext(),
                smsText,
                properties.gateway().destinationPhoneNumber(),
                verification.getStatus(),
                verification.getExpiresAt()
        );
    }

    public VerificationStatusResponse getStatus(UUID verificationId) {
        PhoneVerificationEntity verification = getEntity(verificationId);
        verification.expireIfDue(Instant.now(clock));
        return toStatusResponse(verification);
    }

    public PhoneVerificationEntity getEntity(UUID verificationId) {
        return storage.findVerificationById(verificationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Verification " + verificationId + " was not found"
                ));
    }

    private GeneratedCode generateUniqueCode() {
        for (int attempt = 0; attempt < MAX_CODE_GENERATION_ATTEMPTS; attempt++) {
            String plaintext = codeGenerator.generate();
            String hash = hmacService.sha256Hex(plaintext.getBytes(StandardCharsets.UTF_8));
            if (!storage.verificationCodeHashExists(hash)) {
                return new GeneratedCode(plaintext, hash);
            }
        }
        throw new IllegalStateException("Unable to generate a unique verification code");
    }

    private VerificationStatusResponse toStatusResponse(PhoneVerificationEntity verification) {
        return new VerificationStatusResponse(
                verification.getId(),
                verification.getPhoneNumber(),
                verification.getStatus(),
                verification.getCreatedAt(),
                verification.getExpiresAt(),
                verification.getVerifiedAt()
        );
    }

    private record GeneratedCode(String plaintext, String hash) {
    }
}
