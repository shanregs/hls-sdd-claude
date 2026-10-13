package com.hls.leave.api;

import java.time.LocalDate;
import java.util.UUID;

/**
 * A Manager asked for leave (spec 032); becomes a notification for every active Admin and Director other than the
 * requester. Published inside the request transaction.
 */
public record ManagerLeaveRequested(UUID requestId, UUID managerId, LocalDate firstDate, LocalDate lastDate) {}
