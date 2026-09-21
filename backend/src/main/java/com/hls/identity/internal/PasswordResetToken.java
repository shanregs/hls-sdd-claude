package com.hls.identity.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * FR-016. Not in spec.md's Key Entities — an implementation-level table
 * research.md introduces. Single-use, same pattern as {@link OtpChallenge}.
 */
@Entity
@Table(name = "identity_password_reset_token")
public class PasswordResetToken {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed", nullable = false)
    private boolean consumed;

    protected PasswordResetToken() {
        // JPA
    }

    public PasswordResetToken(UUID id, UUID userId, String tokenHash, Instant expiresAt) {
        this.id = id;
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public boolean isConsumed() {
        return consumed;
    }

    public void markConsumed() {
        this.consumed = true;
    }

    public boolean isUsable(Instant now) {
        return !consumed && expiresAt.isAfter(now);
    }
}
