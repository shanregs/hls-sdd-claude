package com.hls.attendance.api;

import java.time.LocalDate;
import java.util.UUID;

/**
 * An Admin or Director corrected a Manager's day, or completed a missing check-out (spec 032). Published inside the
 * transaction of the change; becomes a notification for the Manager.
 */
public record ManagerDayCorrected(UUID managerId, LocalDate date, UUID actorUserId) {}
