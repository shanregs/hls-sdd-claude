package com.hls.leave.api;

import java.time.LocalDate;
import java.util.UUID;

/** A Teacher submitted a leave request (spec 010). Published inside the submit transaction. */
public record LeaveRequested(
        UUID requestId, UUID teacherId, UUID schoolId, LocalDate firstDate, LocalDate lastDate, String teacherName) {}
