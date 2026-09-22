package com.hls.attendance.internal;

import com.hls.attendance.api.dto.EvidenceInput;
import com.hls.attendance.api.dto.MarkedByRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * FR-001-FR-004, FR-006. One Teacher's current recorded status for one
 * calendar day. A single mutable row per {@code (teacherId, markDate)} —
 * edits update this row in place (US1 AC4); every prior value is retrievable
 * through the {@code audit} module, not a second table (research.md §4).
 */
@Entity
@Table(name = "attendance_mark")
public class AttendanceMark {

    @Id
    private UUID id;

    @Column(name = "teacher_id", nullable = false)
    private UUID teacherId;

    @Column(name = "mark_date", nullable = false)
    private LocalDate markDate;

    @Column(name = "school_id", nullable = false)
    private UUID schoolId;

    @Column(name = "status_code", nullable = false)
    private String statusCode;

    @Column(name = "fractional_value", nullable = false)
    private BigDecimal fractionalValue;

    @Column(name = "evidence_geo_lat")
    private BigDecimal evidenceGeoLat;

    @Column(name = "evidence_geo_lng")
    private BigDecimal evidenceGeoLng;

    @Column(name = "evidence_photo_url")
    private String evidencePhotoUrl;

    @Column(name = "evidence_checkin_code")
    private String evidenceCheckinCode;

    @Column(name = "marked_by", nullable = false)
    private UUID markedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "marked_by_role", nullable = false)
    private MarkedByRole markedByRole;

    @Column(name = "marked_at", nullable = false)
    private Instant markedAt;

    protected AttendanceMark() {
        // JPA
    }

    public AttendanceMark(
            UUID id,
            UUID teacherId,
            LocalDate markDate,
            UUID schoolId,
            String statusCode,
            BigDecimal fractionalValue,
            EvidenceInput evidence,
            UUID markedBy,
            MarkedByRole markedByRole,
            Instant markedAt) {
        this.id = id;
        this.teacherId = teacherId;
        this.markDate = markDate;
        this.schoolId = schoolId;
        this.statusCode = statusCode;
        this.fractionalValue = fractionalValue;
        applyEvidence(evidence);
        this.markedBy = markedBy;
        this.markedByRole = markedByRole;
        this.markedAt = markedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTeacherId() {
        return teacherId;
    }

    public LocalDate getMarkDate() {
        return markDate;
    }

    public UUID getSchoolId() {
        return schoolId;
    }

    public String getStatusCode() {
        return statusCode;
    }

    public BigDecimal getFractionalValue() {
        return fractionalValue;
    }

    public EvidenceInput getEvidence() {
        return new EvidenceInput(evidenceGeoLat, evidenceGeoLng, evidencePhotoUrl, evidenceCheckinCode);
    }

    public UUID getMarkedBy() {
        return markedBy;
    }

    public MarkedByRole getMarkedByRole() {
        return markedByRole;
    }

    public Instant getMarkedAt() {
        return markedAt;
    }

    /** US1 AC4 — edits the existing row in place rather than creating a second one for the same day. */
    public void update(UUID schoolId, String statusCode, BigDecimal fractionalValue, EvidenceInput evidence, UUID markedBy, MarkedByRole markedByRole, Instant markedAt) {
        this.schoolId = schoolId;
        this.statusCode = statusCode;
        this.fractionalValue = fractionalValue;
        applyEvidence(evidence);
        this.markedBy = markedBy;
        this.markedByRole = markedByRole;
        this.markedAt = markedAt;
    }

    private void applyEvidence(EvidenceInput evidence) {
        if (evidence == null) {
            this.evidenceGeoLat = null;
            this.evidenceGeoLng = null;
            this.evidencePhotoUrl = null;
            this.evidenceCheckinCode = null;
            return;
        }
        this.evidenceGeoLat = evidence.geoLat();
        this.evidenceGeoLng = evidence.geoLng();
        this.evidencePhotoUrl = evidence.photoUrl();
        this.evidenceCheckinCode = evidence.checkinCode();
    }
}
