package com.hls.identity.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * FR-011/FR-012. Once {@code revoked = true}, no further refresh succeeds against this
 * row (data-model.md invariant) — this is what makes SC-005's 60-second revocation
 * bound hold without waiting for the access token to expire on its own.
 */
@Entity
@Table(name = "identity_session")
public class Session {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false)
    private Channel channel;

    @Column(name = "refresh_token_hash", nullable = false)
    private String refreshTokenHash;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "last_active_at", nullable = false)
    private Instant lastActiveAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked", nullable = false)
    private boolean revoked;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "device_label")
    private String deviceLabel;

    protected Session() {
        // JPA
    }

    public Session(UUID id, UUID userId, Channel channel, String refreshTokenHash,
                    Instant issuedAt, Instant expiresAt, String deviceLabel) {
        this.id = id;
        this.userId = userId;
        this.channel = channel;
        this.refreshTokenHash = refreshTokenHash;
        this.issuedAt = issuedAt;
        this.lastActiveAt = issuedAt;
        this.expiresAt = expiresAt;
        this.deviceLabel = deviceLabel;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public Channel getChannel() {
        return channel;
    }

    public String getRefreshTokenHash() {
        return refreshTokenHash;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public Instant getLastActiveAt() {
        return lastActiveAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public boolean isRevoked() {
        return revoked;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public String getDeviceLabel() {
        return deviceLabel;
    }

    /** FR-012: ends this session's access immediately. */
    public void revoke(Instant now) {
        this.revoked = true;
        this.revokedAt = now;
    }

    /** FR-011: rotates the refresh token and extends the inactivity window. */
    public void rotate(String newRefreshTokenHash, Instant now, Instant newExpiresAt) {
        this.refreshTokenHash = newRefreshTokenHash;
        this.lastActiveAt = now;
        this.expiresAt = newExpiresAt;
    }

    /** A usable session: not revoked and not past its inactivity expiry. */
    public boolean isUsable(Instant now) {
        return !revoked && expiresAt.isAfter(now);
    }
}
