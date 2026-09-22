package com.hls.school.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * FR-001. A named locality (city/town/village) with a PIN code, belonging to
 * one Zone. Never deleted once added — enforced by {@link PlaceRepository}
 * exposing no delete method at all (FR-007).
 */
@Entity
@Table(name = "school_place")
public class Place {

    @Id
    private UUID id;

    @Column(name = "zone_id", nullable = false)
    private UUID zoneId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "pincode", nullable = false)
    private String pincode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    protected Place() {
        // JPA
    }

    public Place(UUID id, UUID zoneId, String name, String pincode, Instant createdAt, UUID createdBy) {
        this.id = id;
        this.zoneId = zoneId;
        this.name = name;
        this.pincode = pincode;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
    }

    public UUID getId() {
        return id;
    }

    public UUID getZoneId() {
        return zoneId;
    }

    public String getName() {
        return name;
    }

    public String getPincode() {
        return pincode;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }
}
