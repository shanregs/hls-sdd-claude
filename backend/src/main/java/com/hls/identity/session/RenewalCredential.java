package com.hls.identity.session;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A single-use renewal credential within a {@link Session}'s chain (data-model.md, research.md
 * §7). Only the hash is ever persisted; presenting an already-used one must revoke the whole chain
 * (FR-010).
 */
@Entity
@Table(name = "renewal_credential")
public class RenewalCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "credential_hash", nullable = false)
    private String credentialHash;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "superseded_by")
    private UUID supersededBy;

    protected RenewalCredential() {
        // JPA
    }

    public RenewalCredential(UUID sessionId, String credentialHash, Instant issuedAt) {
        this.sessionId = sessionId;
        this.credentialHash = credentialHash;
        this.issuedAt = issuedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public String getCredentialHash() {
        return credentialHash;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    public UUID getSupersededBy() {
        return supersededBy;
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    public void markUsed(Instant now, UUID supersededById) {
        this.usedAt = now;
        this.supersededBy = supersededById;
    }
}
