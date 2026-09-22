package com.hls.attendance.api;

import java.util.UUID;

/**
 * Raised when a {@code teacherId} passed to a mark/grid operation names no
 * Teacher Profile (validated live through {@code teacher.api.TeacherQueries});
 * mapped to 404 by {@code AttendanceController}. Not named in tasks.md's
 * original task list — a small, implementation-time addition (T042), the
 * same kind of discovery specs/005's `TeacherModuleTest` bootstrap-mode
 * correction already established as normal practice in this codebase.
 */
public class AttendanceTeacherNotFoundException extends RuntimeException {

    public AttendanceTeacherNotFoundException(UUID teacherId) {
        super("No teacher profile with id " + teacherId);
    }
}
