package com.hls.leave.api;

import java.time.LocalDate;
import java.util.UUID;

/** A Manager cancelled their own pending leave request (spec 032); becomes a notification for every active Admin and Director. */
public record ManagerLeaveCancelled(UUID requestId, UUID managerId, LocalDate firstDate, LocalDate lastDate) {}
