package com.hls.attendance.api;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * An SPI the month lock consults (spec 032 research.md section 6): the requests still waiting for a decision that touch
 * a month. The leave module implements it for Manager leave; attendance adds its own forgotten check-in requests. The
 * lock collects every implementation, so attendance never depends on leave.
 */
public interface PendingManagerRequests {

    /** A request waiting for a decision; {@code kind} says what it is, for example {@code LEAVE}. */
    record Pending(String kind, UUID requestId, UUID managerId, LocalDate firstDate, LocalDate lastDate) {}

    /** The requests still waiting for a decision that touch the month. */
    List<Pending> pendingFor(YearMonth month);
}
