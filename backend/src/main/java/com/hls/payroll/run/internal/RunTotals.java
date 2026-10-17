package com.hls.payroll.run.internal;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;

/** The totals and counts of a run, always the sums of its lines (spec 013b FR-015); one place so every path agrees. */
final class RunTotals {

    private RunTotals() {}

    static void refresh(PayRun run, List<PayRunLine> lines) {
        run.setTotals(
                sum(lines, PayRunLine::getEarned),
                sum(lines, PayRunLine::getDeduction),
                sum(lines, PayRunLine::getAdjustments),
                sum(lines, PayRunLine::getNet),
                (int) lines.stream().filter(l -> l.getState() == PersonResult.State.PAYABLE).count(),
                (int) lines.stream().filter(l -> l.getState() == PersonResult.State.CANNOT_PAY).count());
    }

    private static BigDecimal sum(List<PayRunLine> lines, Function<PayRunLine, BigDecimal> field) {
        return lines.stream()
                .map(field)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }
}
