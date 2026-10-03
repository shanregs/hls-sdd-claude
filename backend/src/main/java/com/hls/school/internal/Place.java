package com.hls.school.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A town, city or village in a Zone; neither name nor PIN code is unique (spec 005 FR-002). */
@Entity
@Table(name = "place")
public class Place {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "zone_id", nullable = false)
    private UUID zoneId;

    @Column(name = "name", nullable = false)
    private String name;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "pin_code", nullable = false, length = 6)
    private String pinCode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Place() {
        // JPA
    }

    public Place(UUID zoneId, String name, String pinCode, Instant now) {
        this.zoneId = zoneId;
        this.name = name;
        this.pinCode = pinCode;
        this.createdAt = now;
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

    public String getPinCode() {
        return pinCode;
    }

    public void update(String name, String pinCode, UUID zoneId) {
        this.name = name;
        this.pinCode = pinCode;
        this.zoneId = zoneId;
    }
}
