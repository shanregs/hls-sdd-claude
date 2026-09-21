package com.hls.identity.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Binds the {@code hls.identity.*} keys in application.yml. */
@Component
@ConfigurationProperties(prefix = "hls.identity")
public class IdentityProperties {

    private String jwtSigningKey;
    private long accessTokenTtlSeconds = 900;
    private long refreshTokenTtlSeconds = 1_209_600;
    private Lockout lockout = new Lockout();
    private Otp otp = new Otp();
    private long managerScopeCheckTimeoutMs = 2000;

    public String getJwtSigningKey() {
        return jwtSigningKey;
    }

    public void setJwtSigningKey(String jwtSigningKey) {
        this.jwtSigningKey = jwtSigningKey;
    }

    public long getAccessTokenTtlSeconds() {
        return accessTokenTtlSeconds;
    }

    public void setAccessTokenTtlSeconds(long accessTokenTtlSeconds) {
        this.accessTokenTtlSeconds = accessTokenTtlSeconds;
    }

    public long getRefreshTokenTtlSeconds() {
        return refreshTokenTtlSeconds;
    }

    public void setRefreshTokenTtlSeconds(long refreshTokenTtlSeconds) {
        this.refreshTokenTtlSeconds = refreshTokenTtlSeconds;
    }

    public Lockout getLockout() {
        return lockout;
    }

    public void setLockout(Lockout lockout) {
        this.lockout = lockout;
    }

    public Otp getOtp() {
        return otp;
    }

    public void setOtp(Otp otp) {
        this.otp = otp;
    }

    public long getManagerScopeCheckTimeoutMs() {
        return managerScopeCheckTimeoutMs;
    }

    public void setManagerScopeCheckTimeoutMs(long managerScopeCheckTimeoutMs) {
        this.managerScopeCheckTimeoutMs = managerScopeCheckTimeoutMs;
    }

    public static class Lockout {
        private int failedAttemptThreshold = 5;
        private long lockoutDurationMinutes = 30;

        public int getFailedAttemptThreshold() {
            return failedAttemptThreshold;
        }

        public void setFailedAttemptThreshold(int failedAttemptThreshold) {
            this.failedAttemptThreshold = failedAttemptThreshold;
        }

        public long getLockoutDurationMinutes() {
            return lockoutDurationMinutes;
        }

        public void setLockoutDurationMinutes(long lockoutDurationMinutes) {
            this.lockoutDurationMinutes = lockoutDurationMinutes;
        }
    }

    public static class Otp {
        private long ttlSeconds = 300;
        private int rateLimitPerMinute = 3;

        public long getTtlSeconds() {
            return ttlSeconds;
        }

        public void setTtlSeconds(long ttlSeconds) {
            this.ttlSeconds = ttlSeconds;
        }

        public int getRateLimitPerMinute() {
            return rateLimitPerMinute;
        }

        public void setRateLimitPerMinute(int rateLimitPerMinute) {
            this.rateLimitPerMinute = rateLimitPerMinute;
        }
    }
}
