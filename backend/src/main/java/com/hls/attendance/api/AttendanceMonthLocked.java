package com.hls.attendance.api;

import java.time.YearMonth;
import java.util.UUID;

/** A Teacher's month was locked or re-locked (spec 010). Published once per Teacher. */
public record AttendanceMonthLocked(UUID teacherId, YearMonth month, UUID actorUserId) {}
