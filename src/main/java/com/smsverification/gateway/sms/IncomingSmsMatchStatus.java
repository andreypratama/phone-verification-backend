package com.smsverification.gateway.sms;

public enum IncomingSmsMatchStatus {
    MATCHED,
    NO_VERIFICATION_CODE,
    UNKNOWN_CODE,
    PHONE_MISMATCH,
    EXPIRED_CODE,
    ALREADY_VERIFIED,
    INVALID_SENDER
}
