package com.hls.identity.otp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Configurable OTP request throttling policy (FR-028/FR-029), stored as a single database row so
 * it is tunable at runtime without a redeploy — unlike the fixed deployment config (FR-013) the
 * rest of this spec's timing values use. Always read fresh (research.md §17); no admin screen
 * exists yet to edit it (spec 011's job), but it is already DB-backed so that screen only needs to
 * write here, not migrate storage later.
 */
@Entity
@Table(name = "otp_policy_settings")
public class OtpPolicySettings {

    public static final short SINGLETON_ID = 1;

    @Id
    private Short id;

    @Column(name = "resend_cooldown_seconds", nullable = false)
    private int resendCooldownSeconds;

    @Column(name = "max_consecutive_requests", nullable = false)
    private int maxConsecutiveRequests;

    @Column(name = "consecutive_request_lockout_hours", nullable = false)
    private int consecutiveRequestLockoutHours;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected OtpPolicySettings() {
        // JPA
    }

    /** Package-visible: tests build a policy directly rather than going through Flyway's seed row. */
    OtpPolicySettings(int resendCooldownSeconds, int maxConsecutiveRequests, int consecutiveRequestLockoutHours) {
        this.id = SINGLETON_ID;
        this.resendCooldownSeconds = resendCooldownSeconds;
        this.maxConsecutiveRequests = maxConsecutiveRequests;
        this.consecutiveRequestLockoutHours = consecutiveRequestLockoutHours;
        this.updatedAt = Instant.EPOCH;
    }

    public int getResendCooldownSeconds() {
        return resendCooldownSeconds;
    }

    public int getMaxConsecutiveRequests() {
        return maxConsecutiveRequests;
    }

    public int getConsecutiveRequestLockoutHours() {
        return consecutiveRequestLockoutHours;
    }
}
