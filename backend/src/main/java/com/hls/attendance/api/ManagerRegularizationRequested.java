package com.hls.attendance.api;

import java.time.LocalDate;
import java.util.UUID;

/** A Manager asked to add a forgotten check-in (spec 032); becomes a notification for every active Admin and Director. */
public record ManagerRegularizationRequested(UUID requestId, UUID managerId, LocalDate date) {}
