package com.hls.attendance.api;

import java.time.LocalDate;
import java.util.UUID;

/**
 * A Manager had not checked in by the cut-off on a working day (spec 032 US4). Raised once per Manager and date;
 * becomes a notification for the Manager and for every active Admin and Director.
 */
public record ManagerCheckInMissed(UUID managerId, LocalDate date) {}
