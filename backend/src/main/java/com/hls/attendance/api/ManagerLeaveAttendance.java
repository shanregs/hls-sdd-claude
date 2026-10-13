package com.hls.attendance.api;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * What the leave module uses to put an approved Manager leave request onto the Manager's days (spec 032). Attendance
 * never depends on leave: leave calls this interface, not the other way round.
 */
public interface ManagerLeaveAttendance {

    /** One leave day to write: a date, whether it is a half day, and whether the leave type is Loss-of-Pay. */
    record LeaveDay(LocalDate date, boolean half, boolean unpaid) {}

    /** The working days in the range for a Manager (default calendar, inside their employment), oldest first. */
    List<LocalDate> workingDays(UUID managerId, LocalDate from, LocalDate to);

    /**
     * Writes the days onto the Manager's records for an approved request. Fails with a conflict if any day is in a
     * locked month or already has full-day leave; changes nothing then.
     */
    void apply(UUID requestId, UUID actor, UUID managerId, List<LeaveDay> days);

    /** Removes the leave of a request (revoked or cancelled); fails with a conflict in a locked month. */
    void remove(UUID requestId, UUID actor);
}
