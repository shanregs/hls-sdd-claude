package com.hls.payroll.internal;

import com.hls.payroll.api.PayPolicyView.RoundingRule;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * The loss-of-pay arithmetic (spec 013a FR-009, FR-018, research.md section 4). Pure: no clock, no repository.
 * A day is worth the salary over the month's working days, kept to {@link #SCALE} decimal places and never rounded
 * to a rupee; the month's total is rounded exactly once, by the policy's rule.
 */
public final class LossOfPayCalculator {

    /** Decimal places a day's value is kept to; far finer than a paisa, so rounding the total is the only rounding. */
    public static final int SCALE = 10;

    /** One unpaid day, valued on its own day: the salary then, and the working days of that day's month. */
    public record DayInput(BigDecimal fraction, BigDecimal salary, int workingDays) {}

    private LossOfPayCalculator() {}

    /** The value of a full day. {@code workingDays} must be above zero; the caller reports "no working days" instead. */
    public static BigDecimal fullDay(BigDecimal monthlySalary, int workingDays) {
        if (workingDays <= 0) {
            throw new IllegalArgumentException("A day cannot be valued over " + workingDays + " working days.");
        }
        return monthlySalary.divide(BigDecimal.valueOf(workingDays), SCALE, RoundingMode.HALF_EVEN);
    }

    /** The value of a half day: the full day's value times the policy's half-day fraction. */
    public static BigDecimal halfDay(BigDecimal fullDay, BigDecimal halfDayFraction) {
        return fullDay.multiply(halfDayFraction).setScale(SCALE, RoundingMode.HALF_EVEN);
    }

    /** One unpaid day: the full day's value times the part of the day that is unpaid (1 or the half-day fraction). */
    public static BigDecimal amountOf(DayInput day) {
        return fullDay(day.salary(), day.workingDays()).multiply(day.fraction()).setScale(SCALE, RoundingMode.HALF_EVEN);
    }

    public static BigDecimal sum(List<DayInput> days) {
        BigDecimal total = BigDecimal.ZERO.setScale(SCALE);
        for (DayInput day : days) {
            total = total.add(amountOf(day));
        }
        return total;
    }

    /** Rounds the month's total to whole rupees, once. The result keeps two decimals ({@code 3462.00}). */
    public static BigDecimal round(BigDecimal total, RoundingRule rule) {
        RoundingMode mode =
                switch (rule) {
                    case NEAREST_RUPEE -> RoundingMode.HALF_UP;
                    case UP -> RoundingMode.CEILING;
                    case DOWN -> RoundingMode.FLOOR;
                };
        return total.setScale(0, mode).setScale(2);
    }
}
