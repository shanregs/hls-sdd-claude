package com.hls.attendance.api;

import java.time.LocalDate;
import java.util.UUID;

/**
 * An Admin or Director approved or rejected a forgotten check-in (spec 032); becomes a notification for the Manager.
 *
 * @param decision {@code APPROVED} or {@code REJECTED}
 * @param reason the reason given on a rejection, otherwise null
 */
public record ManagerRegularizationDecided(UUID requestId, UUID managerId, LocalDate date, String decision, String reason) {

    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";
}
