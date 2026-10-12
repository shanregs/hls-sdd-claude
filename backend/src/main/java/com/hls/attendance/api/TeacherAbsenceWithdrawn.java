package com.hls.attendance.api;

import java.time.LocalDate;
import java.util.UUID;

/** A day that was an absence or approved leave stopped being one (cleared, corrected, leave revoked); amendment A2. */
public record TeacherAbsenceWithdrawn(UUID teacherId, LocalDate date, UUID actorUserId) {}
