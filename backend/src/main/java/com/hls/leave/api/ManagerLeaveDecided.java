package com.hls.leave.api;

import java.time.LocalDate;
import java.util.UUID;

/**
 * An Admin or Director approved, rejected or revoked a Manager's leave (spec 032); becomes a notification for the
 * Manager. {@code reason} is given on a rejection and on a revoke, otherwise null.
 */
public record ManagerLeaveDecided(
        UUID requestId, UUID managerId, Decision decision, LocalDate firstDate, LocalDate lastDate, String reason) {

    public enum Decision {
        APPROVED,
        REJECTED,
        REVOKED
    }
}
