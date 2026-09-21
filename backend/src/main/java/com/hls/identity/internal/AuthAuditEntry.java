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
 * FR-013, Constitution Principle I. Append-only: {@link AuthAuditEntryRepository}
 * exposes no update/delete — a correction is a new row, never an edit.
 */
@Entity
@Table(name = "identity_auth_audit_entry")
public class AuthAuditEntry {

    @Id
    private UUID id;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Column(name = "attempted_identifier")
    private String attemptedIdentifier;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false)

    private AuditAction action;

    @Column(name = "role_at_time")
    private String roleAtTime;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "request_id")
    private String requestId;

    protected AuthAuditEntry() {
        // JPA
    }

    public AuthAuditEntry(UUID id, UUID actorUserId, String attemptedIdentifier, AuditAction action,
                           String roleAtTime, Instant occurredAt, String requestId) {
        this.id = id;
        this.actorUserId = actorUserId;
        this.attemptedIdentifier = attemptedIdentifier;
        this.action = action;
        this.roleAtTime = roleAtTime;
        this.occurredAt = occurredAt;
        this.requestId = requestId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getActorUserId() {
        return actorUserId;
    }

    public String getAttemptedIdentifier() {
        return attemptedIdentifier;
    }

    public AuditAction getAction() {
        return action;
    }

    public String getRoleAtTime() {
        return roleAtTime;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getRequestId() {
        return requestId;
    }
}
