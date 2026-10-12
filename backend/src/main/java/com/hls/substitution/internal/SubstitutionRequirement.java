package com.hls.substitution.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One class left uncovered: an absent Teacher on a date at a School (spec 017a). */
@Entity
@Table(name = "substitution_requirement")
public class SubstitutionRequirement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "teacher_id", nullable = false)
    private UUID teacherId;

    @Column(name = "school_id", nullable = false)
    private UUID schoolId;

    @Column(name = "duty_date", nullable = false)
    private LocalDate dutyDate;

    @Column(name = "day_value", nullable = false)
    private BigDecimal dayValue;

    @Column(name = "kind", nullable = false)
    private String kind;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RequirementStatus status;

    @Column(name = "substitute_teacher_id")
    private UUID substituteTeacherId;

    @Column(name = "cover_user_id")
    private UUID coverUserId;

    @Column(name = "note")
    private String note;

    @Column(name = "informed_at")
    private Instant informedAt;

    @Column(name = "informed_by")
    private UUID informedBy;

    @Column(name = "informed_contact")
    private String informedContact;

    @Enumerated(EnumType.STRING)
    @Column(name = "informed_method")
    private InformMethod informedMethod;

    @Column(name = "informed_note")
    private String informedNote;

    @Column(name = "raised_at", nullable = false)
    private Instant raisedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected SubstitutionRequirement() {
        // JPA
    }

    public SubstitutionRequirement(
            UUID teacherId, UUID schoolId, LocalDate dutyDate, BigDecimal dayValue, String kind, Instant now) {
        this.teacherId = teacherId;
        this.schoolId = schoolId;
        this.dutyDate = dutyDate;
        this.dayValue = dayValue;
        this.kind = kind;
        this.status = RequirementStatus.OPEN;
        this.raisedAt = now;
        this.updatedAt = now;
    }

    /** The absence changed in a way that does not change the outcome (day value, School, kind). */
    public void refresh(UUID schoolId, BigDecimal dayValue, String kind, Instant now) {
        this.schoolId = schoolId;
        this.dayValue = dayValue;
        this.kind = kind;
        this.updatedAt = now;
    }

    public void reopen(UUID schoolId, BigDecimal dayValue, String kind, Instant now) {
        refresh(schoolId, dayValue, kind, now);
        this.status = RequirementStatus.OPEN;
        this.raisedAt = now;
    }

    public void assign(UUID substituteTeacherId, String note, Instant now) {
        this.status = RequirementStatus.ASSIGNED;
        this.substituteTeacherId = substituteTeacherId;
        this.coverUserId = null;
        this.note = note;
        this.updatedAt = now;
        clearInformed();
    }

    public void release(String reason, Instant now) {
        this.status = RequirementStatus.OPEN;
        this.substituteTeacherId = null;
        this.coverUserId = null;
        this.note = reason;
        this.updatedAt = now;
        clearInformed();
    }

    public void cover(UUID coverUserId, String note, Instant now) {
        this.status = RequirementStatus.MANAGER_COVER;
        this.substituteTeacherId = null;
        this.coverUserId = coverUserId;
        this.note = note;
        this.updatedAt = now;
        clearInformed();
    }

    public void cancel(Instant now) {
        this.status = RequirementStatus.CANCELLED;
        this.substituteTeacherId = null;
        this.coverUserId = null;
        this.updatedAt = now;
        clearInformed();
    }

    public void inform(UUID by, String contact, InformMethod method, String note, Instant now) {
        this.informedAt = now;
        this.informedBy = by;
        this.informedContact = contact;
        this.informedMethod = method;
        this.informedNote = note;
        this.updatedAt = now;
    }

    public boolean isInformed() {
        return informedAt != null;
    }

    private void clearInformed() {
        this.informedAt = null;
        this.informedBy = null;
        this.informedContact = null;
        this.informedMethod = null;
        this.informedNote = null;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTeacherId() {
        return teacherId;
    }

    public UUID getSchoolId() {
        return schoolId;
    }

    public LocalDate getDutyDate() {
        return dutyDate;
    }

    public BigDecimal getDayValue() {
        return dayValue;
    }

    public String getKind() {
        return kind;
    }

    public RequirementStatus getStatus() {
        return status;
    }

    public UUID getSubstituteTeacherId() {
        return substituteTeacherId;
    }

    public UUID getCoverUserId() {
        return coverUserId;
    }

    public String getNote() {
        return note;
    }

    public Instant getInformedAt() {
        return informedAt;
    }

    public UUID getInformedBy() {
        return informedBy;
    }

    public String getInformedContact() {
        return informedContact;
    }

    public InformMethod getInformedMethod() {
        return informedMethod;
    }

    public String getInformedNote() {
        return informedNote;
    }

    public Instant getRaisedAt() {
        return raisedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getVersion() {
        return version;
    }
}
