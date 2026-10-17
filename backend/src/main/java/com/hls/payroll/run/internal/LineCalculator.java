package com.hls.payroll.run.internal;

import com.hls.payroll.api.EarnedPay;
import com.hls.payroll.api.PayPolicyView;
import com.hls.payroll.api.PayRules.PersonRef;
import com.hls.payroll.api.TotalLossOfPay;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The net-pay assembly of one line (spec 013b FR-003, FR-004, FR-006, FR-011, FR-016). Pure: it takes the answers the
 * pay rules already gave and puts them together, with no repository, no clock and no random value, so the same inputs
 * always give the same figures, the same frozen working and the same fingerprint.
 *
 * <p>Net pay is earned pay, less the loss-of-pay deduction, plus the sum of the adjustment lines. A person whose pay
 * cannot be worked out is never shown with zero: the result carries the reason and no amounts, and takes none of the
 * adjustments (they stay waiting, FR-019).
 */
public final class LineCalculator {

    /** An adjustment as it is copied into a line (the frozen copy of FR-016). */
    public record AdjustmentInput(UUID id, String kind, BigDecimal amount, String reason) {}

    /** The Manager's figures frozen at the attendance lock, kept in the working so a reviewer can compare (research 5). */
    public record ManagerFigures(BigDecimal workingDaysAtLock, BigDecimal unpaidAtLock) {}

    /**
     * Everything one line is built from. {@code unpaidReasons} says why each unpaid date is unpaid; {@code blockedReason}
     * is set by the caller when the person must not be paid yet (for example the attendance month is not locked) and
     * wins over any other reason; {@code warning} is a reviewer cue that never blocks.
     */
    public record Inputs(
            PersonRef person,
            String name,
            String label,
            EarnedPay earned,
            TotalLossOfPay deduction,
            Map<LocalDate, String> unpaidReasons,
            PayPolicyView policy,
            List<AdjustmentInput> adjustments,
            ManagerFigures manager,
            String blockedReason,
            String warning) {}

    private static final int MONEY = 2;

    private LineCalculator() {}

    public static PersonResult calculate(Inputs in) {
        String reason = cannotReason(in);
        if (reason != null) {
            return cannotPay(in, reason);
        }
        EarnedPay.Found earned = (EarnedPay.Found) in.earned();
        TotalLossOfPay.Found deduction = (TotalLossOfPay.Found) in.deduction();

        List<AdjustmentInput> adjustments = in.adjustments().stream()
                .sorted(Comparator.comparing(a -> a.id().toString()))
                .toList();
        BigDecimal adjustmentTotal = adjustments.stream()
                .map(a -> a.amount().setScale(MONEY))
                .reduce(BigDecimal.ZERO.setScale(MONEY), BigDecimal::add);
        BigDecimal earnedRounded = earned.rounded().setScale(MONEY);
        BigDecimal deductionRounded = deduction.rounded().setScale(MONEY);
        BigDecimal net = earnedRounded.subtract(deductionRounded).add(adjustmentTotal);

        EarnedPay.DayValue last = earned.days().get(earned.days().size() - 1);
        BigDecimal unpaid = deduction.days().stream()
                .map(TotalLossOfPay.UnpaidDayValue::dayValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> working = new LinkedHashMap<>();
        working.put("state", "PAYABLE");
        working.put("policy", policy(in.policy()));
        working.put("salaryRows", salaryRows(earned.days()));
        working.put(
                "payableDates", earned.days().stream().map(d -> d.date().toString()).toList());
        working.put("days", earnedDays(earned.days()));
        working.put("unpaid", unpaidDays(deduction.days(), in.unpaidReasons()));
        working.put("earned", amounts(earned.unrounded(), earnedRounded));
        working.put("deduction", amounts(deduction.unrounded(), deductionRounded));
        working.put("adjustments", adjustmentJson(adjustments));
        working.put("manager", in.manager() == null ? null : manager(in.manager()));
        String json = Json.write(working);

        return new PersonResult(
                in.person(),
                in.name(),
                in.label(),
                PersonResult.State.PAYABLE,
                null,
                earned.days().size(),
                BigDecimal.valueOf(last.workingDays()),
                unpaid,
                last.salary().setScale(MONEY),
                earnedRounded,
                deductionRounded,
                adjustmentTotal,
                net,
                json,
                fingerprint(in, json),
                in.warning(),
                adjustments);
    }

    /** The reason this person cannot be paid, or null when they can: blocked first, then earned pay, then the deduction. */
    private static String cannotReason(Inputs in) {
        if (in.blockedReason() != null) {
            return in.blockedReason();
        }
        if (in.earned() instanceof EarnedPay.Cannot cannot) {
            return cannot.reason();
        }
        if (in.deduction() instanceof TotalLossOfPay.Cannot cannot) {
            return cannot.reason();
        }
        return null;
    }

    private static PersonResult cannotPay(Inputs in, String reason) {
        Map<String, Object> working = new LinkedHashMap<>();
        working.put("state", "CANNOT_PAY");
        working.put("reason", reason);
        working.put("policy", policy(in.policy()));
        String json = Json.write(working);
        return new PersonResult(
                in.person(),
                in.name(),
                in.label(),
                PersonResult.State.CANNOT_PAY,
                reason,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                json,
                fingerprint(in, json),
                in.warning(),
                List.of());
    }

    // ---------------------------------------------------------------- the working

    private static Map<String, Object> policy(PayPolicyView policy) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", policy.id().toString());
        map.put("effectiveOn", policy.effectiveOn().toString());
        map.put("rounding", policy.rounding().name());
        map.put("halfDayFraction", policy.halfDayFraction().toPlainString());
        return map;
    }

    /** Consecutive payable dates at one salary become one row, so a raise on the 16th gives two rows. */
    private static List<Map<String, Object>> salaryRows(List<EarnedPay.DayValue> days) {
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Object> current = null;
        BigDecimal currentSalary = null;
        for (EarnedPay.DayValue day : days) {
            BigDecimal salary = day.salary().setScale(MONEY);
            if (current != null && salary.compareTo(currentSalary) == 0) {
                current.put("to", day.date().toString());
            } else {
                current = new LinkedHashMap<>();
                current.put("from", day.date().toString());
                current.put("to", day.date().toString());
                current.put("monthly", salary.toPlainString());
                currentSalary = salary;
                rows.add(current);
            }
        }
        return rows;
    }

    private static List<Map<String, Object>> earnedDays(List<EarnedPay.DayValue> days) {
        return days.stream()
                .map(d -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("date", d.date().toString());
                    map.put("salary", d.salary().setScale(MONEY).toPlainString());
                    map.put("divisor", d.workingDays());
                    map.put("amount", d.amount().toPlainString());
                    return map;
                })
                .toList();
    }

    private static List<Map<String, Object>> unpaidDays(
            List<TotalLossOfPay.UnpaidDayValue> days, Map<LocalDate, String> reasons) {
        return days.stream()
                .map(d -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("date", d.date().toString());
                    map.put("dayValue", d.dayValue().toPlainString());
                    map.put("salary", d.salary().setScale(MONEY).toPlainString());
                    map.put("divisor", d.workingDays());
                    map.put("amount", d.amount().toPlainString());
                    String reason = reasons == null ? null : reasons.get(d.date());
                    if (reason != null) {
                        map.put("reason", reason);
                    }
                    return map;
                })
                .toList();
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

    private static Map<String, Object> manager(ManagerFigures figures) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("workingDaysAtLock", figures.workingDaysAtLock().toPlainString());
        map.put("unpaidAtLock", figures.unpaidAtLock().toPlainString());
        return map;
    }

    // -------------------------------------------------------------- fingerprint

    /**
     * A hash of everything the line shows: the working, the person's name and label, and the warning. Recompute rewrites
     * a line only when this changes (FR-008).
     */
    private static String fingerprint(Inputs in, String workingJson) {
        return fingerprintOf(in.name(), in.label(), in.warning(), workingJson);
    }

    /** The same hash from the parts, for a line whose working was changed by arithmetic (withdrawing an adjustment). */
    static String fingerprintOf(String name, String label, String warning, String workingJson) {
        String text = name + "\u0001" + label + "\u0001" + warning + "\u0001" + workingJson;
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }
}
