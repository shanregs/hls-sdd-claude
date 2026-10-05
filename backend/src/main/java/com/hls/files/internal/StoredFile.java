package com.hls.files.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** The row of a stored file; it never changes except to record a removal. */
@Entity
@Table(name = "stored_file")
public class StoredFile {

    @Id
    private UUID id;

    @Column(name = "owner_type", nullable = false)
    private String ownerType;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "original_name", nullable = false)
    private String originalName;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "sha256", nullable = false)
    private String sha256;

    @Column(name = "storage_path", nullable = false)
    private String storagePath;

    @Column(name = "added_by", nullable = false)
    private UUID addedBy;

    @Column(name = "added_at", nullable = false)
    private Instant addedAt;

    @Column(name = "removed_at")
    private Instant removedAt;

    @Column(name = "removed_by")
    private UUID removedBy;

    @Column(name = "removal_reason")
    private String removalReason;

    protected StoredFile() {}

    StoredFile(
            UUID id,
            String ownerType,
            UUID ownerId,
            String originalName,
            String contentType,
            long sizeBytes,
            String sha256,
            String storagePath,
            UUID addedBy,
            Instant addedAt) {
        this.id = id;
        this.ownerType = ownerType;
        this.ownerId = ownerId;
        this.originalName = originalName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
        this.storagePath = storagePath;
        this.addedBy = addedBy;
        this.addedAt = addedAt;
    }

    UUID getId() {
        return id;
    }

    String getOwnerType() {
        return ownerType;
    }

    UUID getOwnerId() {
        return ownerId;
    }

    String getOriginalName() {
        return originalName;
    }

    String getContentType() {
        return contentType;
    }

    long getSizeBytes() {
        return sizeBytes;
    }

    String getSha256() {
        return sha256;
    }

    String getStoragePath() {
        return storagePath;
    }

    UUID getAddedBy() {
        return addedBy;
    }

    Instant getAddedAt() {
        return addedAt;
    }

    boolean isRemoved() {
        return removedAt != null;
    }

    void markRemoved(UUID by, String reason, Instant at) {
        this.removedBy = by;
        this.removalReason = reason;
        this.removedAt = at;
    }
}
