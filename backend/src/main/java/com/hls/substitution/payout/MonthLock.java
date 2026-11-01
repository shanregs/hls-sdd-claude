package com.hls.substitution.payout;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

/** The lock of one substitute's attendance for one month (spec 017b FR-009); reopening needs a reason. */
@Entity
@Table(name = "substitute_month_lock")
public class MonthLock {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "substitute_teacher_id", nullable = false)
    private UUID substituteTeacherId;

    @Column(name = "month", nullable = false)
    private LocalDate month;

    @Column(name = "locked", nullable = false)
    private boolean locked;

    @Column(name = "locked_by")
    private UUID lockedBy;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "reopened_by")
    private UUID reopenedBy;

    @Column(name = "reopened_at")
    private Instant reopenedAt;

    @Column(name = "reopen_reason")
    private String reopenReason;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected MonthLock() {
        // JPA
    }

    public MonthLock(UUID substituteTeacherId, YearMonth month) {
        this.substituteTeacherId = substituteTeacherId;
        this.month = month.atDay(1);
    }

    public UUID getSubstituteTeacherId() {
        return substituteTeacherId;
    }

    public YearMonth month() {
        return YearMonth.from(month);
    }

    public boolean isLocked() {
        return locked;
    }

    public UUID getLockedBy() {
        return lockedBy;
    }

    public Instant getLockedAt() {
        return lockedAt;
    }

    public String getReopenReason() {
        return reopenReason;
    }

    public void lock(UUID by, Instant now) {
        this.locked = true;
        this.lockedBy = by;
        this.lockedAt = now;
    }

    public void reopen(UUID by, String reason, Instant now) {
        this.locked = false;
        this.reopenedBy = by;
        this.reopenedAt = now;
        this.reopenReason = reason;
    }
}
