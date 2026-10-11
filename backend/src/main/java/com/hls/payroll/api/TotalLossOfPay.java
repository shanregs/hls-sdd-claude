package com.hls.payroll.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** The month total deducted for loss of pay, or the stated reason it cannot be given. Never a partial figure. */
public sealed interface TotalLossOfPay {

    /**
     * One unpaid day valued on its own day: {@code salary} in effect that day over {@code workingDays} of its month,
     * times {@code dayValue}. {@code amount} is unrounded (ten decimal places).
     */
    record UnpaidDayValue(LocalDate date, BigDecimal dayValue, BigDecimal salary, int workingDays, BigDecimal amount) {}

    /** {@code rounded} is the sum rounded once by the month's policy; {@code unrounded} is the sum before that. */
    record Found(BigDecimal rounded, BigDecimal unrounded, List<UnpaidDayValue> days) implements TotalLossOfPay {}

    /** A day without a salary or without working days, or unpaid days that are not available. */
    record Cannot(String reason) implements TotalLossOfPay {}
}
