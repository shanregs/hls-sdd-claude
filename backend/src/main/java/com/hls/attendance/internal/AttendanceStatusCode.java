package com.hls.attendance.internal;

import com.hls.attendance.api.dto.AttendanceCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * FR-005. The configurable set of statuses an Attendance Mark can carry.
 * {@code code} is immutable once created; only {@code label}/{@code weight}/
 * {@code active} can change after creation (data-model.md).
 */
@Entity
@Table(name = "attendance_status_code")
public class AttendanceStatusCode {

    @Id
    private String code;

    @Column(name = "label", nullable = false)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false)
    private AttendanceCategory category;

    @Column(name = "weight", nullable = false)
    private BigDecimal weight;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    protected AttendanceStatusCode() {
        // JPA
    }

    public AttendanceStatusCode(String code, String label, AttendanceCategory category, BigDecimal weight, boolean active, Instant createdAt, UUID createdBy) {
        this.code = code;
        this.label = label;
        this.category = category;
        this.weight = weight;
        this.active = active;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public AttendanceCategory getCategory() {
        return category;
    }

    public BigDecimal getWeight() {
        return weight;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public void deactivate() {
        this.active = false;
    }

    public void updateLabelAndWeight(String label, BigDecimal weight) {
        if (label != null) {
            this.label = label;
        }
        if (weight != null) {
            this.weight = weight;
        }
    }
}
