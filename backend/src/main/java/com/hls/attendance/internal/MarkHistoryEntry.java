package com.hls.attendance.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Append-only: one row per create, correction or clear. No setters, no update or delete path. */
@Entity
@Table(name = "attendance_mark_history")
public class MarkHistoryEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "teacher_id", nullable = false, updatable = false)
    private UUID teacherId;

    @Column(name = "mark_date", nullable = false, updatable = false)
    private LocalDate markDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, updatable = false)
    private MarkAction action;

    @Column(name = "status_code_id", updatable = false)
    private UUID statusCodeId;

    @Column(name = "day_value", updatable = false)
    private BigDecimal dayValue;

    @Column(name = "school_id", updatable = false)
    private UUID schoolId;

    @Column(name = "note", updatable = false)
    private String note;

    @Column(name = "set_by_user_id", nullable = false, updatable = false)
    private UUID setByUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "set_by_kind", nullable = false, updatable = false)
    private SetByKind setByKind;

    @Column(name = "set_at", nullable = false, updatable = false)
    private Instant setAt;

    @Column(name = "leave_request_id", updatable = false)
    private UUID leaveRequestId;

    protected MarkHistoryEntry() {
        // JPA
    }

    public MarkHistoryEntry(
            UUID teacherId,
            LocalDate markDate,
            MarkAction action,
            UUID statusCodeId,
            BigDecimal dayValue,
            UUID schoolId,
            String note,
            UUID setByUserId,
            SetByKind setByKind,
            Instant setAt) {
        this.teacherId = teacherId;
        this.markDate = markDate;
        this.action = action;
        this.statusCodeId = statusCodeId;
        this.dayValue = dayValue;
        this.schoolId = schoolId;
        this.note = note;
        this.setByUserId = setByUserId;
        this.setByKind = setByKind;
        this.setAt = setAt;
    }

    public MarkHistoryEntry(
            UUID teacherId,
            LocalDate markDate,
            MarkAction action,
            UUID statusCodeId,
            BigDecimal dayValue,
            UUID schoolId,
            String note,
            UUID setByUserId,
            SetByKind setByKind,
            Instant setAt,
            UUID leaveRequestId) {
        this(teacherId, markDate, action, statusCodeId, dayValue, schoolId, note, setByUserId, setByKind, setAt);
        this.leaveRequestId = leaveRequestId;
    }

    public UUID getLeaveRequestId() {
        return leaveRequestId;
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

    public MarkAction getAction() {
        return action;
    }

    public UUID getStatusCodeId() {
        return statusCodeId;
    }

    public BigDecimal getDayValue() {
        return dayValue;
    }

    public UUID getSchoolId() {
        return schoolId;
    }

    public String getNote() {
        return note;
    }

    public UUID getSetByUserId() {
        return setByUserId;
    }

    public SetByKind getSetByKind() {
        return setByKind;
    }

    public Instant getSetAt() {
        return setAt;
    }
}
