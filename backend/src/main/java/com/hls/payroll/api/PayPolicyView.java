package com.hls.payroll.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** One dated version of the pay policy (spec 013a FR-006). */
public record PayPolicyView(
        UUID id,
        LocalDate effectiveOn,
        LopDivisor lopDivisor,
        BigDecimal halfDayFraction,
        RoundingRule rounding,
        PayMonth payMonth) {

    /** What a day of loss of pay is divided by. */
    public enum LopDivisor {
        WORKING_DAYS
    }

    /** How the month's total deduction becomes whole rupees; applied once, to the total only. */
    public enum RoundingRule {
        NEAREST_RUPEE,
        UP,
        DOWN
    }

    /** The period a payroll run covers. */
    public enum PayMonth {
        CALENDAR_MONTH
    }
}
