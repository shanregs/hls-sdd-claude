package com.hls.leave.api;

import java.time.LocalDate;
import java.util.UUID;

/** A Teacher cancelled their own leave request (spec 010); {@code wasApproved} tells Pending from Approved. */
public record LeaveCancelled(
        UUID requestId,
        UUID teacherId,
        UUID schoolId,
        LocalDate firstDate,
        LocalDate lastDate,
        String teacherName,
        boolean wasApproved) {}
