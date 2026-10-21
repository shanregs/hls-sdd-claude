package com.hls.performance.api;

import java.time.LocalDate;
import java.util.UUID;

/** An observation of a Teacher was planned for their Manager (spec 026 FR-015); notification tells the Manager. */
public record ObservationPlanned(UUID planId, UUID teacherId, String teacherName, UUID managerUserId, LocalDate plannedOn, String reason) {}
