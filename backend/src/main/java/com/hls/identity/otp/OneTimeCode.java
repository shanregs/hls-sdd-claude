package com.hls.identity.otp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A short-lived sign-in or password-reset code (data-model.md). */
@Entity
@Table(name = "one_time_code")
public class OneTimeCode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false)
    private OtpChannel channel;

    @Column(name = "destination", nullable = false)
    private String destination;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false)
    private OtpPurpose purpose;

    @Column(name = "code_hash", nullable = false)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "wrong_attempt_count", nullable = false)
    private int wrongAttemptCount;

    protected OneTimeCode() {
        // JPA
    }

    public OneTimeCode(OtpChannel channel, String destination, OtpPurpose purpose, String codeHash, Instant expiresAt) {
        this.channel = channel;
        this.destination = destination;
        this.purpose = purpose;
        this.codeHash = codeHash;
        this.expiresAt = expiresAt;
    }

    public UUID getId() {
        return id;
    }

    public OtpChannel getChannel() {
        return channel;
    }

    public String getDestination() {
        return destination;
    }

    public OtpPurpose getPurpose() {
        return purpose;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    public int getWrongAttemptCount() {
        return wrongAttemptCount;
    }

    public boolean isExpired(Instant now) {
        return now.isAfter(expiresAt);
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    public void markUsed(Instant now) {
        this.usedAt = now;
    }

    public void incrementWrongAttempts() {
        this.wrongAttemptCount++;
    }
}
