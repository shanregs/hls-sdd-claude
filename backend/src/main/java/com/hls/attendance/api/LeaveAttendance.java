package com.hls.attendance.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * What the leave module needs from attendance (spec 009): which days of a range are working days,
 * whether leave may be written onto them, and writing or removing the Leave marks atomically in the
 * caller's transaction. Leave never reads or writes attendance tables itself.
 */
public interface LeaveAttendance {

    /** One working day a leave covers: the date, the School of placement that day and the day value. */
    record LeaveDay(LocalDate date, UUID schoolId, BigDecimal value) {}

    /** What {@link #remove} did: the days cleared, and days left alone because someone changed them. */
    record RemoveResult(List<LocalDate> removed, List<LocalDate> keptBecauseChanged) {}

    /** Today's date in the business time zone (Asia/Kolkata). */
    LocalDate businessToday();

    /** The Teacher's working days in {@code from..to} inclusive (value 1), oldest first. */
    List<LeaveDay> workingDays(UUID teacherId, LocalDate from, LocalDate to);

    /** Why Leave cannot be written on these days: locked months, days a supervisor set; empty when it can. */
    List<String> problems(UUID teacherId, List<LeaveDay> days);

    /**
     * Writes Leave (L) marks for the days, attributed to the approver and tagged with the request,
     * replacing a mark that is absent or the Teacher's own. All or nothing: any problem throws and
     * nothing changes.
     */
    void apply(UUID requestId, UUID approverUserId, UUID teacherId, List<LeaveDay> days);

    /**
     * Removes the marks the request made and nobody has changed since. Refuses (throws) when any of
     * them is in a locked month.
     */
    RemoveResult remove(UUID requestId, UUID actorUserId);
}
