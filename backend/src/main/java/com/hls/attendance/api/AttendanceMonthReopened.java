package com.hls.attendance.api;

import java.time.YearMonth;
import java.util.UUID;

/** A Teacher's locked month was reopened, with the reason given (spec 010). */
public record AttendanceMonthReopened(UUID teacherId, YearMonth month, UUID actorUserId, String reason) {}
