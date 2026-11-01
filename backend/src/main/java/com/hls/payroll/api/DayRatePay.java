package com.hls.payroll.api;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * What the pay runs hold for substitute days (spec 017b), implemented by payroll and read by the reconciliation. One
 * row per day per run that holds it.
 */
public interface DayRatePay {

    enum RunState {
        DRAFT,
        AWAITING_APPROVAL,
        APPROVED
    }

    record PaidDay(
            UUID dayId, UUID runId, YearMonth runMonth, YearMonth workMonth, BigDecimal rate, BigDecimal amount, RunState state) {}

    /** The runs (not cancelled) that hold each day, in one query. */
    List<PaidDay> daysInRuns(Collection<UUID> dayIds);

    /** Whether the person has been paid for the month already, by an approved run (so a late day needs a Correction). */
    boolean paidForMonth(UUID teacherId, YearMonth month);

    /** Whether the month's primary run is approved. */
    boolean primaryRunApproved(YearMonth month);
}
