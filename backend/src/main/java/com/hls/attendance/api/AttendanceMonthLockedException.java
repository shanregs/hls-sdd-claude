package com.hls.attendance.api;

import java.util.UUID;

/** FR-012: raised when a direct add/edit is attempted against a locked teacher-month; mapped to 409 by {@code AttendanceController}. */
public class AttendanceMonthLockedException extends RuntimeException {

    private final UUID teacherId;
    private final String period;

    public AttendanceMonthLockedException(UUID teacherId, String period) {
        super("Attendance for " + period + " is locked");
        this.teacherId = teacherId;
        this.period = period;
    }

    public UUID getTeacherId() {
        return teacherId;
    }

    public String getPeriod() {
        return period;
    }
}
