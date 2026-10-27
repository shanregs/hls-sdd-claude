package com.hls.incentive.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** A School with an approved MoU that earned nobody an incentive, and why (spec 030). One row per School. */
@Entity
@Table(name = "no_earner")
public class NoEarnerRecord {

    @Id
    private UUID id;

    @Column(name = "school_id", nullable = false, updatable = false)
    private UUID schoolId;

    @Column(name = "contract_id")
    private UUID contractId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false)
    private NoEarnerReason reason;

    @Column(name = "detail")
    private String detail;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected NoEarnerRecord() {
        // JPA
    }

    NoEarnerRecord(UUID schoolId, UUID contractId, NoEarnerReason reason, String detail, Instant now) {
        this.id = UUID.randomUUID();
        this.schoolId = schoolId;
        this.contractId = contractId;
        this.reason = reason;
        this.detail = detail;
        this.createdAt = now;
    }

    /** Sets the current finding; returns true when anything changed. */
    boolean update(UUID contractId, NoEarnerReason reason, String detail) {
        boolean changed = !Objects.equals(this.contractId, contractId)
                || this.reason != reason
                || !Objects.equals(this.detail, detail)
                || this.resolvedAt != null;
        this.contractId = contractId;
        this.reason = reason;
        this.detail = detail;
        this.resolvedAt = null;
        return changed;
    }

    void resolve(Instant now) {
        if (resolvedAt == null) {
            resolvedAt = now;
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getSchoolId() {
        return schoolId;
    }

    public UUID getContractId() {
        return contractId;
    }

    public NoEarnerReason getReason() {
        return reason;
    }

    public String getDetail() {
        return detail;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
