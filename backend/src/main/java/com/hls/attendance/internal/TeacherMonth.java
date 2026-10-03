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
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Exists only once a Teacher-month has been locked; the rollup columns are frozen at lock. */
@Entity
@Table(name = "attendance_teacher_month")
public class TeacherMonth {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "teacher_id", nullable = false)
    private UUID teacherId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "year_month", nullable = false, length = 7)
    private String yearMonth;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false)
    private MonthState state;

    @Column(name = "working_days", nullable = false)
    private BigDecimal workingDays;

    @Column(name = "days_worked", nullable = false)
    private BigDecimal daysWorked;

    @Column(name = "days_leave", nullable = false)
    private BigDecimal daysLeave;

    @Column(name = "training_available", nullable = false)
    private BigDecimal trainingAvailable;

    @Column(name = "training_attended", nullable = false)
    private BigDecimal trainingAttended;

    @Column(name = "unmarked", nullable = false)
    private int unmarked;

    @Column(name = "weighted_total", nullable = false)
    private BigDecimal weightedTotal;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected TeacherMonth() {
        // JPA
    }

    public TeacherMonth(UUID teacherId, String yearMonth) {
        this.teacherId = teacherId;
        this.yearMonth = yearMonth;
    }

    /** Freezes the figures and marks the month LOCKED. */
    public void lock(Rollup rollup, Instant now) {
        this.state = MonthState.LOCKED;
        this.workingDays = rollup.workingDays();
        this.daysWorked = rollup.daysWorked();
        this.daysLeave = rollup.daysLeave();
        this.trainingAvailable = rollup.trainingAvailable();
        this.trainingAttended = rollup.trainingAttended();
        this.unmarked = rollup.unmarked();
        this.weightedTotal = rollup.weightedTotal();
        this.changedAt = now;
    }

    public void reopen(Instant now) {
        this.state = MonthState.OPEN;
        this.changedAt = now;
    }

    public Rollup frozenRollup() {
        return new Rollup(
                workingDays, daysWorked, daysLeave, trainingAvailable, trainingAttended, unmarked, weightedTotal);
    }

    public UUID getId() {
        return id;
    }

    public UUID getTeacherId() {
        return teacherId;
    }

    public String getYearMonth() {
        return yearMonth;
    }

    public MonthState getState() {
        return state;
    }

    public Instant getChangedAt() {
        return changedAt;
    }
}
