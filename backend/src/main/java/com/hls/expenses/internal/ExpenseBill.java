package com.hls.expenses.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** One bill (receipt) kept in the shared file store; removal is recorded, never a delete (spec 015 FR-006). */
@Entity
@Table(name = "expense_bill")
public class ExpenseBill {

    @Id
    private UUID id;

    @Column(name = "claim_id", nullable = false)
    private UUID claimId;

    @Column(name = "file_id", nullable = false)
    private UUID fileId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "added_by", nullable = false)
    private UUID addedBy;

    @Column(name = "added_at", nullable = false)
    private Instant addedAt;

    @Column(name = "removed_by")
    private UUID removedBy;

    @Column(name = "removed_at")
    private Instant removedAt;

    @Column(name = "removal_reason")
    private String removalReason;

    protected ExpenseBill() {
        // JPA
    }

    ExpenseBill(UUID claimId, UUID fileId, String name, String contentType, long sizeBytes, UUID addedBy, Instant now) {
        this.id = UUID.randomUUID();
        this.claimId = claimId;
        this.fileId = fileId;
        this.name = name;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.addedBy = addedBy;
        this.addedAt = now;
    }

    void remove(UUID by, String reason, Instant now) {
        this.removedBy = by;
        this.removedAt = now;
        this.removalReason = reason;
    }

    public boolean isLive() {
        return removedAt == null;
    }

    public UUID getId() {
        return id;
    }

    public UUID getClaimId() {
        return claimId;
    }

    public UUID getFileId() {
        return fileId;
    }

    public String getName() {
        return name;
    }

    public String getContentType() {
        return contentType;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public UUID getAddedBy() {
        return addedBy;
    }

    public Instant getAddedAt() {
        return addedAt;
    }

    public UUID getRemovedBy() {
        return removedBy;
    }

    public Instant getRemovedAt() {
        return removedAt;
    }

    public String getRemovalReason() {
        return removalReason;
    }
}
