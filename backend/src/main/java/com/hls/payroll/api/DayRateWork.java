package com.hls.payroll.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The days worked by Substitute-type Teachers, implemented by {@code substitution} (spec 017b) and read by the pay run,
 * so {@code payroll} never depends on {@code substitution} (Principle VII). A Substitute has no salary and no
 * placement: the run pays the day value times the designation's daily rate for each day they were present.
 */
public interface DayRateWork {

    /** One Present day of a locked substitute-month. */
    record WorkedDay(UUID dayId, UUID teacherId, UUID schoolId, String schoolName, LocalDate date, BigDecimal dayValue) {}

    /** A Substitute who has days in the month that cannot be paid yet, and why. */
    record NotReady(UUID teacherId, String reason) {}

    /** Present days of the month for locked substitute-months, grouped by Teacher, in a fixed number of queries. */
    Map<UUID, List<WorkedDay>> presentDays(YearMonth month);

    /** Substitutes with Present or unmarked days in the month that cannot be paid yet, with the reason. */
    List<NotReady> notReady(YearMonth month);

    /**
     * Present days of locked substitute-months that no pay run holds yet, for the supplementary run of a month whose
     * primary run is approved.
     */
    Map<UUID, List<WorkedDay>> unpaidPresentDays(YearMonth month);
}
