package com.hls.leave.api;

import java.time.LocalDate;
import java.util.UUID;

/** A supervisor decided a leave request (spec 010). Published inside the decision transaction. */
public record LeaveDecided(
        UUID requestId, UUID teacherId, Decision decision, LocalDate firstDate, LocalDate lastDate, String reason) {

    public enum Decision {
        APPROVED,
        REJECTED,
        REVOKED
    }
}
