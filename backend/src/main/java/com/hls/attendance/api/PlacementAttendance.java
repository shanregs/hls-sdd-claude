package com.hls.attendance.api;

import java.time.LocalDate;
import java.util.UUID;

/** What a move of a Teacher between Schools needs from attendance (spec 027). */
public interface PlacementAttendance {

    /** True if the Teacher has any mark that is not a leave mark on or after {@code date} (present, absent and so on). */
    boolean hasNonLeaveMarkOnOrAfter(UUID teacherId, LocalDate date);

    /** True if the effective month, or a later month that holds marks, is locked for the Teacher. */
    boolean anyMonthLocked(UUID teacherId, LocalDate fromDate);

    /**
     * Re-tags the leave marks dated on or after {@code from} to the new School; runs in the caller's transaction and
     * writes one mark-history row (CORRECTED) per mark. Never touches an earlier date; throws if any is in a locked month.
     * Returns how many marks changed.
     */
    int retagLeaveMarks(UUID actorUserId, UUID teacherId, LocalDate from, UUID newSchoolId);
}
