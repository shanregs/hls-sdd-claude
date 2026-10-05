package com.hls.attendance.api;

import java.time.LocalDate;
import java.util.UUID;

/** A supervisor set or cleared one day of a Teacher's attendance (spec 010). Never published for self or leave marks. */
public record AttendanceMarkChanged(UUID teacherId, LocalDate date, UUID actorUserId, String actorName) {}
