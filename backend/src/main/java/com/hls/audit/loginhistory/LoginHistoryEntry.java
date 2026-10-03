package com.hls.audit.loginhistory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * An immutable Login History entry (data-model.md), the audit module's own copy of every sign-in
 * attempt spec 001 publishes as {@code LoginHistoryRecorded}. Never updated or deleted; a
 * correction is a new entry (FR-004).
 */
@Entity
@Table(name = "login_history_entry")
public class LoginHistoryEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "source_event_id", nullable = false, unique = true)
    private UUID sourceEventId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "phone_masked", nullable = false)
    private String phoneMasked;

    @Column(name = "method", nullable = false)
    private String method;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "outcome", nullable = false)
    private String outcome;

    protected LoginHistoryEntry() {
        // JPA
    }

    public LoginHistoryEntry(
            Instant occurredAt,
            UUID sourceEventId,
            UUID userId,
            String phoneMasked,
            String method,
            String eventType,
            String outcome) {
        this.occurredAt = occurredAt;
        this.sourceEventId = sourceEventId;
        this.userId = userId;
        this.phoneMasked = phoneMasked;
        this.method = method;
        this.eventType = eventType;
        this.outcome = outcome;
    }

    public UUID getId() {
        return id;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public UUID getSourceEventId() {
        return sourceEventId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getPhoneMasked() {
        return phoneMasked;
    }

    public String getMethod() {
        return method;
    }

    public String getEventType() {
        return eventType;
    }

    public String getOutcome() {
        return outcome;
    }
}
