package com.hls.identity.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * FR-008/FR-009/FR-018. Not in spec.md's Key Entities — an implementation-level table
 * research.md §3 introduces. Looked up by {@code identifier} (a phone number for
 * Teacher OTP login or SMS-method MFA, or an email address for EMAIL-method MFA),
 * not user id, since a challenge can exist before the system reveals whether the
 * identifier is registered (FR-015).
 */
@Entity
@Table(name = "identity_otp_challenge")
public class OtpChallenge {

    @Id
    private UUID id;

    @Column(name = "identifier", nullable = false)
    private String identifier;

    @Column(name = "code_hash", nullable = false)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed", nullable = false)
    private boolean consumed;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    protected OtpChallenge() {
        // JPA
    }

    public OtpChallenge(UUID id, String identifier, String codeHash, Instant expiresAt) {
        this.id = id;
        this.identifier = identifier;
        this.codeHash = codeHash;
        this.expiresAt = expiresAt;
    }

    public UUID getId() {
        return id;
    }

    public String getIdentifier() {
        return identifier;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public boolean isConsumed() {
        return consumed;
    }

    public void markConsumed() {
        this.consumed = true;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public void incrementAttempts() {
        this.attemptCount++;
    }

    /** A challenge is usable exactly once, and only before it expires. */
    public boolean isUsable(Instant now) {
        return !consumed && expiresAt.isAfter(now);
    }
}
