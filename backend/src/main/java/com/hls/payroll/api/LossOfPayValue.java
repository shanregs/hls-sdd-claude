package com.hls.payroll.api;

import java.math.BigDecimal;
import java.time.YearMonth;

/** What a day of loss of pay is worth on a date: the salary over the month's working days, never rounded to a rupee. */
public sealed interface LossOfPayValue {

    /** {@code fullDay} and {@code halfDay} are kept at ten decimal places; payroll rounds once, on the month total. */
    record Found(BigDecimal fullDay, BigDecimal halfDay, int workingDays) implements LossOfPayValue {}

    record NoSalary(SalaryInEffect.NoSalary cause) implements LossOfPayValue {}

    /** The month has no working day for the person's calendar, so nothing is divided. */
    record NoWorkingDays(YearMonth month) implements LossOfPayValue {}
}
