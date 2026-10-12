package com.hls.identity.policy;

/** The built-in defaults, for unit tests that build a service by hand. They equal the behaviour before spec 011. */
public class DefaultRuntimeSettings implements RuntimeSettings {

    @Override
    public int passwordMinLength() {
        return 10;
    }

    @Override
    public int maxFailedAttempts() {
        return 5;
    }

    @Override
    public int lockoutMinutes() {
        return 30;
    }

    @Override
    public int otpResendCooldownSeconds() {
        return 30;
    }

    @Override
    public int otpMaxConsecutiveRequests() {
        return 5;
    }

    @Override
    public int otpLockoutHours() {
        return 4;
    }

    @Override
    public int renewalTtlDays() {
        return 14;
    }

    @Override
    public int accessTokenTtlSeconds() {
        return 900;
    }

    @Override
    public String minAppVersion() {
        return "0.0.0";
    }

    @Override
    public int locationWaitSeconds() {
        return 4;
    }

    @Override
    public int locationReuseSeconds() {
        return 10;
    }
}
