package com.hls.payroll.run.internal;

import com.hls.payroll.api.PayRules.PersonRef;
import com.hls.payroll.run.internal.LineCalculator.AdjustmentInput;
import java.math.BigDecimal;
import java.util.List;

/**
 * One person's outcome in a run (spec 013b): either payable, with every figure and the frozen working, or unable to be
 * paid, with the reason and no amounts (never zero for "missing", FR-006). Immutable, so a virtual-thread task can
 * hand it to the writing thread without sharing anything.
 */
public record PersonResult(
        PersonRef person,
        String name,
        String label,
        State state,
        String reason,
        Integer payableDays,
        BigDecimal workingDays,
        BigDecimal unpaidDays,
        BigDecimal salary,
        BigDecimal earned,
        BigDecimal deduction,
        BigDecimal adjustments,
        BigDecimal net,
        String workingJson,
        String fingerprint,
        String warning,
        List<AdjustmentInput> adjustmentLines) {

    public enum State {
        PAYABLE,
        CANNOT_PAY
    }
}
