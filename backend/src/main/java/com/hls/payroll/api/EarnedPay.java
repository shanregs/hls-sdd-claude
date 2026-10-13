package com.hls.payroll.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * What a person earned for a month (spec 013b FR-004), the mirror of {@link TotalLossOfPay}: each payable date is valued
 * at the salary in effect on that date over the month's working days, the values are summed, and the sum is rounded
 * once by the policy in effect on the month's last day. Never a partial figure and never zero for "missing".
 */
public sealed interface EarnedPay {

    /**
     * One payable date valued on its own day: the {@code salary} in effect that day over the {@code workingDays} of its
     * month on the calendar of the School the person was in that day (the default calendar for a Manager or staff
     * member). {@code amount} is unrounded (ten decimal places).
     */
    record DayValue(LocalDate date, BigDecimal salary, int workingDays, BigDecimal amount) {}

    /** {@code rounded} is the sum rounded once by the month's policy; {@code unrounded} is the sum before that. */
    record Found(BigDecimal rounded, BigDecimal unrounded, List<DayValue> days) implements EarnedPay {}

    /**
     * No payable date, a payable date without a salary, a date with no working days, or a person who is not known. The
     * reason is shown to the Admin as it is.
     */
    record Cannot(String reason) implements EarnedPay {}
}
