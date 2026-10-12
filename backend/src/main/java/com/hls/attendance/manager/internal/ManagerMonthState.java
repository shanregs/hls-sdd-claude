package com.hls.attendance.manager.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.UUID;

/** A Manager's month and its lock. A missing row means the month is open. Locking freezes the figures payroll reads. */
@Entity
@Table(name = "manager_month")
public class ManagerMonthState {

    public enum State {
        OPEN,
        LOCKED
    }

    @Embeddable
    public static class Key implements Serializable {

        @Column(name = "manager_id", nullable = false)
        private UUID managerId;

        @Column(name = "year_month", nullable = false, length = 7)
        private String yearMonth;

        protected Key() {
            // JPA
        }

        public Key(UUID managerId, YearMonth month) {
            this.managerId = managerId;
            this.yearMonth = month.toString();
        }

        public UUID getManagerId() {
            return managerId;
        }

        public YearMonth month() {
            return YearMonth.parse(yearMonth);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Key key && managerId.equals(key.managerId) && yearMonth.equals(key.yearMonth);
        }

        @Override
        public int hashCode() {
            return 31 * managerId.hashCode() + yearMonth.hashCode();
        }
    }

    /** The figures at the moment of the lock (research.md section 6). */
    public record Frozen(
            BigDecimal workingDays,
            BigDecimal daysWorked,
            BigDecimal leavePaid,
            BigDecimal leaveUnpaid,
            BigDecimal unpaid,
            BigDecimal fieldDays,
            String unpaidDates) {}

    @EmbeddedId
    private Key id;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false)
    private State state = State.OPEN;

    @Column(name = "frozen_working_days", precision = 4, scale = 1)
    private BigDecimal frozenWorkingDays;

    @Column(name = "frozen_days_worked", precision = 4, scale = 1)
    private BigDecimal frozenDaysWorked;

    @Column(name = "frozen_leave_paid", precision = 4, scale = 1)
    private BigDecimal frozenLeavePaid;

    @Column(name = "frozen_leave_unpaid", precision = 4, scale = 1)
    private BigDecimal frozenLeaveUnpaid;

    @Column(name = "frozen_unpaid", precision = 4, scale = 1)
    private BigDecimal frozenUnpaid;

    @Column(name = "frozen_field_days", precision = 4, scale = 1)
    private BigDecimal frozenFieldDays;

    /** The unpaid dates with their fractions, as {@code yyyy-MM-dd=1.0;...}, so payroll can value each day. */
    @Column(name = "frozen_unpaid_dates")
    private String frozenUnpaidDates;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "locked_by")
    private UUID lockedBy;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected ManagerMonthState() {
        // JPA
    }

    public ManagerMonthState(UUID managerId, YearMonth month) {
        this.id = new Key(managerId, month);
    }

    public void lock(Frozen frozen, UUID by, Instant at) {
        this.state = State.LOCKED;
        this.frozenWorkingDays = frozen.workingDays();
        this.frozenDaysWorked = frozen.daysWorked();
        this.frozenLeavePaid = frozen.leavePaid();
        this.frozenLeaveUnpaid = frozen.leaveUnpaid();
        this.frozenUnpaid = frozen.unpaid();
        this.frozenFieldDays = frozen.fieldDays();
        this.frozenUnpaidDates = frozen.unpaidDates();
        this.lockedAt = at;
        this.lockedBy = by;
    }

    public void reopen() {
        this.state = State.OPEN;
        this.frozenWorkingDays = null;
        this.frozenDaysWorked = null;
        this.frozenLeavePaid = null;
        this.frozenLeaveUnpaid = null;
        this.frozenUnpaid = null;
        this.frozenFieldDays = null;
        this.frozenUnpaidDates = null;
        this.lockedAt = null;
        this.lockedBy = null;
    }

    public boolean isLocked() {
        return state == State.LOCKED;
    }

    public Frozen frozen() {
        return new Frozen(
                frozenWorkingDays,
                frozenDaysWorked,
                frozenLeavePaid,
                frozenLeaveUnpaid,
                frozenUnpaid,
                frozenFieldDays,
                frozenUnpaidDates);
    }

    public Key getId() {
        return id;
    }

    public State getState() {
        return state;
    }

    public Instant getLockedAt() {
        return lockedAt;
    }

    public UUID getLockedBy() {
        return lockedBy;
    }
}
