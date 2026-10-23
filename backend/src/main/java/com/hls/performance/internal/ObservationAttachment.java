package com.hls.performance.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A file attached to an observation through the shared file store (spec 026 FR-006). */
@Entity
@Table(name = "observation_attachment")
public class ObservationAttachment {

    @Id
    UUID id;

    @Column(name = "observation_id", nullable = false)
    UUID observationId;

    @Column(name = "file_id", nullable = false)
    UUID fileId;

    @Column(name = "name", nullable = false)
    String name;

    @Column(name = "added_by", nullable = false)
    UUID addedBy;

    @Column(name = "added_at", nullable = false)
    Instant addedAt;

    protected ObservationAttachment() {
        // JPA
    }

    ObservationAttachment(UUID observationId, UUID fileId, String name, UUID addedBy, Instant now) {
        this.id = UUID.randomUUID();
        this.observationId = observationId;
        this.fileId = fileId;
        this.name = name;
        this.addedBy = addedBy;
        this.addedAt = now;
    }
}
