package com.hls.identity.policy;

/**
 * The runtime-editable policy values that sign-in, OTP, sessions and the mobile gate read (spec 011).
 * {@code identity} owns this interface and the {@code settings} module implements it, so the dependency
 * points one way ({@code settings} to {@code identity}). Each call returns the effective value at that moment.
 */
public interface RuntimeSettings {

    int passwordMinLength();

    int maxFailedAttempts();

    int lockoutMinutes();

    int otpResendCooldownSeconds();

    int otpMaxConsecutiveRequests();

    int otpLockoutHours();

    int renewalTtlDays();

    int accessTokenTtlSeconds();

    /** A {@code major.minor.patch} version. */
    String minAppVersion();

    int locationWaitSeconds();

    int locationReuseSeconds();
}
