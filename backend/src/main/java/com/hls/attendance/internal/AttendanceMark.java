package com.hls.attendance.internal;

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

/** The current mark of one Teacher on one date; earlier values live in {@link MarkHistoryEntry}. */
@Entity
@Table(name = "attendance_mark")
public class AttendanceMark {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "teacher_id", nullable = false)
    private UUID teacherId;

    @Column(name = "mark_date", nullable = false)
    private LocalDate markDate;

    @Column(name = "status_code_id", nullable = false)
    private UUID statusCodeId;

    @Column(name = "day_value", nullable = false)
    private BigDecimal dayValue;

    @Column(name = "school_id")
    private UUID schoolId;

    @Column(name = "note")
    private String note;

    @Column(name = "set_by_user_id", nullable = false)
    private UUID setByUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "set_by_kind", nullable = false)
    private SetByKind setByKind;

    @Column(name = "set_at", nullable = false)
    private Instant setAt;

    /** The leave request that made this mark (spec 009), cleared by any later hand-made change. */
    @Column(name = "leave_request_id")
    private UUID leaveRequestId;

    /** Amendment A2: true when the day is unpaid (an absence with no approved leave, or a Loss-of-Pay leave day). */
    @Column(name = "loss_of_pay", nullable = false)
    private boolean lossOfPay;

    @Column(name = "principal_confirmed")
    private Boolean principalConfirmed;

    @Column(name = "principal_name")
    private String principalName;

    @Column(name = "principal_note")
    private String principalNote;

    @Column(name = "principal_recorded_by")
    private UUID principalRecordedBy;

    @Column(name = "principal_recorded_at")
    private Instant principalRecordedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected AttendanceMark() {
        // JPA
    }

    public AttendanceMark(UUID teacherId, LocalDate markDate) {
        this.teacherId = teacherId;
        this.markDate = markDate;
    }

    public void set(
            UUID statusCodeId,
            BigDecimal dayValue,
            UUID schoolId,
            String note,
            UUID setByUserId,
            SetByKind kind,
            Instant now) {
        if (this.statusCodeId != null && !this.statusCodeId.equals(statusCodeId)) {
            clearPrincipalConfirmation();
        }
        this.statusCodeId = statusCodeId;
        this.dayValue = dayValue;
        this.schoolId = schoolId;
        this.note = note;
        this.setByUserId = setByUserId;
        this.setByKind = kind;
        this.setAt = now;
        this.leaveRequestId = null;
    }

    /** Tags the mark just set as made by approved leave; call after {@link #set}. */
    public void markFromLeave(UUID requestId) {
        this.leaveRequestId = requestId;
    }

    public boolean isLossOfPay() {
        return lossOfPay;
    }

    public void setLossOfPay(boolean lossOfPay) {
        this.lossOfPay = lossOfPay;
    }

    /** Records the optional Principal confirmation of the day (amendment A2, decision D7). */
    public void confirmByPrincipal(boolean confirmed, String name, String note, UUID recordedBy, Instant now) {
        this.principalConfirmed = confirmed;
        this.principalName = name;
        this.principalNote = note;
        this.principalRecordedBy = recordedBy;
        this.principalRecordedAt = now;
    }

    public void clearPrincipalConfirmation() {
        this.principalConfirmed = null;
        this.principalName = null;
        this.principalNote = null;
        this.principalRecordedBy = null;
        this.principalRecordedAt = null;
    }

    public Boolean getPrincipalConfirmed() {
        return principalConfirmed;
    }

    public String getPrincipalName() {
        return principalName;
    }

    public String getPrincipalNote() {
        return principalNote;
    }

    public UUID getPrincipalRecordedBy() {
        return principalRecordedBy;
    }

    public Instant getPrincipalRecordedAt() {
        return principalRecordedAt;
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

    public Long getVersion() {
        return version;
    }
}
