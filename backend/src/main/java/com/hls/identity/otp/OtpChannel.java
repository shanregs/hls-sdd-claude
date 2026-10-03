package com.hls.identity.otp;

/**
 * Delivery channel for a one-time code. {@code SIGN_IN} purpose is always {@code SMS}. {@code BOTH}
 * is a request/verify-API-level value only (research.md §14) — a persisted {@link OneTimeCode} row
 * is always {@code SMS} or {@code EMAIL}, never {@code BOTH}.
 */
public enum OtpChannel {
    SMS,
    EMAIL,
    BOTH
}
