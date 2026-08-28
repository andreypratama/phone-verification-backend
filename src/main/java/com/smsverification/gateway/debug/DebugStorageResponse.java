package com.smsverification.gateway.debug;

import com.smsverification.gateway.security.HmacNonceEntity;
import com.smsverification.gateway.sms.IncomingSmsEntity;
import com.smsverification.gateway.verification.PhoneVerificationEntity;

import java.util.List;

public record DebugStorageResponse(
        int verificationsCount,
        int incomingSmsCount,
        int noncesCount,
        List<PhoneVerificationEntity> verifications,
        List<IncomingSmsEntity> incomingSms,
        List<HmacNonceEntity> nonces
) {
}
