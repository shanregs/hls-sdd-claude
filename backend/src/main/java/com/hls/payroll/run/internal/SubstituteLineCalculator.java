package com.hls.payroll.run.internal;

import com.hls.payroll.api.EarnedPay;
import com.hls.payroll.api.PayPolicyView;
import com.hls.payroll.api.PayRules.PersonRef;
import com.hls.payroll.api.TotalLossOfPay;
import com.hls.payroll.internal.LossOfPayCalculator;
import com.hls.payroll.run.internal.LineCalculator.AdjustmentInput;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The line of a Substitute Teacher (spec 017b FR-011 to FR-013): no salary and no deduction, only the days they were
 * present, each worth the day value times the designation's daily rate on that date. Pure, like {@link LineCalculator}:
 * the same inputs always give the same figures, the same frozen working and the same fingerprint. The working keeps the
 * keys the payslip and the adjustment withdrawal read, and adds {@code substituteDays}.
 */
final class SubstituteLineCalculator {

    /** One Present day with the rate that prices it. */
    record DayLine(
            UUID dayId, UUID schoolId, String schoolName, LocalDate date, BigDecimal dayValue, BigDecimal rate) {}

    record Inputs(
            PersonRef person,
            String name,
            String label,
            List<DayLine> days,
            PayPolicyView policy,
            List<AdjustmentInput> adjustments,
            String blockedReason) {}

    private static final int MONEY = 2;

    private SubstituteLineCalculator() {}

    static PersonResult calculate(Inputs in) {
        if (in.blockedReason() != null) {
            // the same "cannot be paid" result as any other person: a reason and no amounts, never zero
            return LineCalculator.calculate(new LineCalculator.Inputs(
                    in.person(),
                    in.name(),
                    in.label(),
                    new EarnedPay.Cannot("unused"),
                    new TotalLossOfPay.Cannot("unused"),
                    Map.of(),
                    in.policy(),
                    List.of(),
                    null,
                    in.blockedReason(),
                    null));
        }
        List<DayLine> days = in.days().stream()
                .sorted(Comparator.comparing(DayLine::date).thenComparing(d -> d.dayId().toString()))
                .toList();
        List<AdjustmentInput> adjustments = in.adjustments().stream()
                .sorted(Comparator.comparing(a -> a.id().toString()))
                .toList();
        BigDecimal adjustmentTotal = adjustments.stream()
                .map(a -> a.amount().setScale(MONEY))
                .reduce(BigDecimal.ZERO.setScale(MONEY), BigDecimal::add);
        BigDecimal unrounded = days.stream().map(SubstituteLineCalculator::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal earned = LossOfPayCalculator.round(unrounded, in.policy().rounding());
        BigDecimal deduction = BigDecimal.ZERO.setScale(MONEY);
        BigDecimal net = earned.subtract(deduction).add(adjustmentTotal);
        BigDecimal daysWorked =
                days.stream().map(DayLine::dayValue).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(MONEY);

        Map<String, Object> working = new LinkedHashMap<>();
        working.put("state", "PAYABLE");
        working.put("policy", policy(in.policy()));
        working.put("salaryRows", List.of());
        working.put("payableDates", days.stream().map(d -> d.date().toString()).distinct().toList());
        working.put("days", List.of());
        working.put("unpaid", List.of());
        working.put("earned", amounts(unrounded, earned));
        working.put("deduction", amounts(BigDecimal.ZERO, deduction));
        working.put("adjustments", adjustmentJson(adjustments));
        working.put("manager", null);
        working.put("substituteDays", dayJson(days));
        working.put("rateRows", rateRows(days));
        String json = Json.write(working);

        return new PersonResult(
                in.person(),
                in.name(),
                in.label(),
                PersonResult.State.PAYABLE,
                null,
                (int) days.stream().map(DayLine::date).distinct().count(),
                daysWorked,
                BigDecimal.ZERO.setScale(MONEY),
                days.isEmpty() ? BigDecimal.ZERO.setScale(MONEY) : days.get(days.size() - 1).rate().setScale(MONEY),
                earned,
                deduction,
                adjustmentTotal,
                net,
                json,
                LineCalculator.fingerprintOf(in.name(), in.label(), null, json),
                null,
                adjustments);
    }

    /** A day's pay, unrounded: the day value times the rate. */
    static BigDecimal amount(DayLine day) {
        return day.dayValue().multiply(day.rate());
    }

    private static List<Map<String, Object>> dayJson(List<DayLine> days) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (DayLine d : days) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("dayId", d.dayId().toString());
            map.put("schoolId", d.schoolId().toString());
            map.put("school", d.schoolName());
            map.put("date", d.date().toString());
            map.put("dayValue", d.dayValue().setScale(MONEY).toPlainString());
            map.put("rate", d.rate().setScale(MONEY).toPlainString());
            map.put("amount", amount(d).setScale(4).toPlainString());
            rows.add(map);
        }
        return rows;
    }

    /** One row per rate: how many days at what rate, for the payslip ("N days x Rs R"). */
    private static List<Map<String, Object>> rateRows(List<DayLine> days) {
        Map<String, BigDecimal> byRate = new LinkedHashMap<>();
        for (DayLine d : days) {
            byRate.merge(d.rate().setScale(MONEY).toPlainString(), d.dayValue(), BigDecimal::add);
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        byRate.forEach((rate, count) -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("days", count.setScale(MONEY).toPlainString());
            map.put("rate", rate);
            rows.add(map);
        });
        return rows;
    }

    private static Map<String, Object> policy(PayPolicyView policy) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", policy.id().toString());
        map.put("effectiveOn", policy.effectiveOn().toString());
        map.put("rounding", policy.rounding().name());
        map.put("halfDayFraction", policy.halfDayFraction().toPlainString());
        return map;
    }

    private static Map<String, Object> amounts(BigDecimal unrounded, BigDecimal rounded) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("unrounded", unrounded.toPlainString());
        map.put("rounded", rounded.toPlainString());
        return map;
    }

    private static List<Map<String, Object>> adjustmentJson(List<AdjustmentInput> adjustments) {
        return adjustments.stream()
                .map(a -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("id", a.id().toString());
                    map.put("kind", a.kind());
                    map.put("amount", a.amount().setScale(MONEY).toPlainString());
                    map.put("reason", a.reason());
                    return map;
                })
                .toList();
    }
}
