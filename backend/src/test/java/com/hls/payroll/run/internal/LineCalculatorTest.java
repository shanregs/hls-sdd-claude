package com.hls.payroll.run.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.payroll.api.EarnedPay;
import com.hls.payroll.api.PayPolicyView;
import com.hls.payroll.api.PayPolicyView.LopDivisor;
import com.hls.payroll.api.PayPolicyView.PayMonth;
import com.hls.payroll.api.PayPolicyView.RoundingRule;
import com.hls.payroll.api.PayRules.Kind;
import com.hls.payroll.api.PayRules.PersonRef;
import com.hls.payroll.api.TotalLossOfPay;
import com.hls.payroll.run.internal.LineCalculator.AdjustmentInput;
import com.hls.payroll.run.internal.LineCalculator.Inputs;
import com.hls.payroll.run.internal.LineCalculator.ManagerFigures;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Spec 013b T013: the net-pay assembly, with hand-computed tables. A month of 20 working days and a salary of 30,000
 * makes a day worth 1,500, so every figure below can be checked with a pencil.
 */
class LineCalculatorTest {

    private static final PersonRef PERSON = new PersonRef(Kind.TEACHER, UUID.fromString("00000000-0000-0000-0000-000000000001"));
    private static final PayPolicyView POLICY = new PayPolicyView(
            UUID.fromString("00000000-0000-0000-0013-000000000001"),
            LocalDate.of(2000, 1, 1),
            LopDivisor.WORKING_DAYS,
            new BigDecimal("0.500"),
            RoundingRule.NEAREST_RUPEE,
            PayMonth.CALENDAR_MONTH);

    private static BigDecimal d(String value) {
        return new BigDecimal(value);
    }

    private static EarnedPay.Found earnedOf(String salary, int divisor, LocalDate... dates) {
        BigDecimal day = d(salary).divide(BigDecimal.valueOf(divisor), 10, java.math.RoundingMode.HALF_EVEN);
        List<EarnedPay.DayValue> days = java.util.Arrays.stream(dates)
                .map(date -> new EarnedPay.DayValue(date, d(salary), divisor, day))
                .toList();
        BigDecimal unrounded = day.multiply(BigDecimal.valueOf(dates.length));
        return new EarnedPay.Found(unrounded.setScale(0, java.math.RoundingMode.HALF_UP).setScale(2), unrounded, days);
    }

    private static TotalLossOfPay.Found deductionOf(String salary, int divisor, Map<LocalDate, String> fractionByDate) {
        BigDecimal day = d(salary).divide(BigDecimal.valueOf(divisor), 10, java.math.RoundingMode.HALF_EVEN);
        List<TotalLossOfPay.UnpaidDayValue> days = fractionByDate.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> new TotalLossOfPay.UnpaidDayValue(
                        e.getKey(),
                        d(e.getValue()),
                        d(salary),
                        divisor,
                        day.multiply(d(e.getValue())).setScale(10, java.math.RoundingMode.HALF_EVEN)))
                .toList();
        BigDecimal unrounded = days.stream().map(TotalLossOfPay.UnpaidDayValue::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new TotalLossOfPay.Found(unrounded.setScale(0, java.math.RoundingMode.HALF_UP).setScale(2), unrounded, days);
    }

    private static TotalLossOfPay.Found noDeduction() {
        return new TotalLossOfPay.Found(d("0.00"), d("0.0000000000"), List.of());
    }

    private static Inputs inputs(EarnedPay earned, TotalLossOfPay deduction, AdjustmentInput... adjustments) {
        return new Inputs(
                PERSON, "Tara Teacher", "Teacher", earned, deduction, Map.of(), POLICY, List.of(adjustments), null, null, null);
    }

    private static final LocalDate D1 = LocalDate.of(2026, 9, 1);
    private static final LocalDate D2 = LocalDate.of(2026, 9, 2);
    private static final LocalDate D3 = LocalDate.of(2026, 9, 3);

    // ------------------------------------------------------------------------ the net

    @Test
    void netIsEarnedMinusTheDeductionPlusTheAdjustments() {
        // 3 payable days at 1,500 = 4,500; one full unpaid day = 1,500; a +2,000 correction
        PersonResult result = LineCalculator.calculate(inputs(
                earnedOf("30000", 20, D1, D2, D3),
                deductionOf("30000", 20, Map.of(D2, "1")),
                new AdjustmentInput(UUID.randomUUID(), "CORRECTION", d("2000.00"), "Arrears for August")));

        assertThat(result.state()).isEqualTo(PersonResult.State.PAYABLE);
        assertThat(result.reason()).isNull();
        assertThat(result.earned()).isEqualByComparingTo("4500.00");
        assertThat(result.deduction()).isEqualByComparingTo("1500.00");
        assertThat(result.adjustments()).isEqualByComparingTo("2000.00");
        assertThat(result.net()).isEqualByComparingTo("5000.00");
        assertThat(result.net().scale()).isEqualTo(2);
        assertThat(result.payableDays()).isEqualTo(3);
        assertThat(result.workingDays()).isEqualByComparingTo("20");
        assertThat(result.unpaidDays()).isEqualByComparingTo("1");
        assertThat(result.salary()).isEqualByComparingTo("30000.00");
    }

    @Test
    void aNegativeAdjustmentReducesTheNet() {
        PersonResult result = LineCalculator.calculate(inputs(
                earnedOf("30000", 20, D1, D2, D3),
                noDeduction(),
                new AdjustmentInput(UUID.randomUUID(), "CORRECTION", d("-500.00"), "Overpaid in August")));

        assertThat(result.adjustments()).isEqualByComparingTo("-500.00");
        assertThat(result.net()).isEqualByComparingTo("4000.00");
    }

    @Test
    void severalAdjustmentsAreSummed() {
        PersonResult result = LineCalculator.calculate(inputs(
                earnedOf("30000", 20, D1),
                noDeduction(),
                new AdjustmentInput(UUID.randomUUID(), "CORRECTION", d("100.50"), "a"),
                new AdjustmentInput(UUID.randomUUID(), "CORRECTION", d("-20.25"), "b")));

        assertThat(result.adjustments()).isEqualByComparingTo("80.25");
        assertThat(result.net()).isEqualByComparingTo("1580.25");
    }

    @Test
    void aFullyUnpaidMonthIsAValidLineWithZeroNet() {
        PersonResult result = LineCalculator.calculate(
                inputs(earnedOf("30000", 20, D1), deductionOf("30000", 20, Map.of(D1, "1"))));

        assertThat(result.state()).isEqualTo(PersonResult.State.PAYABLE);
        assertThat(result.net()).isEqualByComparingTo("0.00");
    }

    @Test
    void aNetThatWouldBeNegativeIsReportedNotClamped() {
        PersonResult result = LineCalculator.calculate(
                inputs(earnedOf("30000", 20, D1), deductionOf("30000", 20, Map.of(D1, "1", D2, "1"))));

        assertThat(result.state()).isEqualTo(PersonResult.State.PAYABLE);
        assertThat(result.net()).isEqualByComparingTo("-1500.00");
    }

    @Test
    void aHalfDayIsWorthTheDayValueOfTheDeductionDay() {
        PersonResult result = LineCalculator.calculate(
                inputs(earnedOf("30000", 20, D1, D2), deductionOf("30000", 20, Map.of(D2, "0.5"))));

        assertThat(result.deduction()).isEqualByComparingTo("750.00");
        assertThat(result.unpaidDays()).isEqualByComparingTo("0.5");
        assertThat(result.net()).isEqualByComparingTo("2250.00");
    }

    // ------------------------------------------------------------------ cannot be paid

    @Test
    void anEarnedPayThatCannotBeWorkedOutGivesACannotPayLineWithTheReasonAndNoAmounts() {
        PersonResult result = LineCalculator.calculate(
                inputs(new EarnedPay.Cannot("No salary on 02/09/2026: No salary has been recorded."), noDeduction()));

        assertThat(result.state()).isEqualTo(PersonResult.State.CANNOT_PAY);
        assertThat(result.reason()).isEqualTo("No salary on 02/09/2026: No salary has been recorded.");
        assertThat(result.earned()).isNull();
        assertThat(result.deduction()).isNull();
        assertThat(result.adjustments()).isNull();
        assertThat(result.net()).isNull();
        assertThat(result.fingerprint()).isNotBlank();
    }

    @Test
    void aDeductionThatCannotBeWorkedOutGivesACannotPayLineEvenWhenEarnedPayIsKnown() {
        PersonResult result = LineCalculator.calculate(inputs(
                earnedOf("30000", 20, D1),
                new TotalLossOfPay.Cannot("The Manager's joining date has not been recorded, so the unpaid days cannot be worked out.")));

        assertThat(result.state()).isEqualTo(PersonResult.State.CANNOT_PAY);
        assertThat(result.reason()).contains("joining date");
        assertThat(result.net()).isNull();
    }

    @Test
    void aBlockedPersonKeepsTheBlockingReasonAheadOfAnyOther() {
        Inputs blocked = new Inputs(
                PERSON,
                "Tara Teacher",
                "Teacher",
                new EarnedPay.Cannot("No salary"),
                noDeduction(),
                Map.of(),
                POLICY,
                List.of(),
                null,
                "The attendance month is not locked.",
                null);

        PersonResult result = LineCalculator.calculate(blocked);

        assertThat(result.state()).isEqualTo(PersonResult.State.CANNOT_PAY);
        assertThat(result.reason()).isEqualTo("The attendance month is not locked.");
    }

    @Test
    void aCannotPayLineNeverTakesItsAdjustments() {
        PersonResult result = LineCalculator.calculate(inputs(
                new EarnedPay.Cannot("No salary"),
                noDeduction(),
                new AdjustmentInput(UUID.randomUUID(), "CORRECTION", d("100.00"), "x")));

        assertThat(result.state()).isEqualTo(PersonResult.State.CANNOT_PAY);
        assertThat(result.adjustmentLines()).isEmpty();
    }

    // ------------------------------------------------------------------------ the working

    @Test
    void theWorkingHoldsEverythingTheReviewScreenAndThePayslipNeed() {
        UUID adjustmentId = UUID.randomUUID();
        Inputs in = new Inputs(
                PERSON,
                "Tara Teacher",
                "Teacher",
                earnedOf("30000", 20, D1, D2, D3),
                deductionOf("30000", 20, Map.of(D2, "1")),
                Map.of(D2, "Absent, no leave"),
                POLICY,
                List.of(new AdjustmentInput(adjustmentId, "CORRECTION", d("2000.00"), "Arrears")),
                new ManagerFigures(d("20"), d("1")),
                null,
                "Calendar changed after the lock");

        PersonResult result = LineCalculator.calculate(in);
        String working = result.workingJson();

        assertThat(working)
                .contains("\"payableDates\":[\"2026-09-01\",\"2026-09-02\",\"2026-09-03\"]")
                .contains("\"salaryRows\":[{\"from\":\"2026-09-01\",\"to\":\"2026-09-03\",\"monthly\":\"30000.00\"}]")
                .contains("\"divisor\":20")
                .contains("\"reason\":\"Absent, no leave\"")
                .contains("\"rounding\":\"NEAREST_RUPEE\"")
                .contains("\"halfDayFraction\":\"0.500\"")
                .contains("\"earned\":{\"unrounded\":\"4500.0000000000\",\"rounded\":\"4500.00\"}")
                .contains("\"deduction\":{\"unrounded\":\"1500.0000000000\",\"rounded\":\"1500.00\"}")
                .contains("\"adjustments\":[{\"id\":\"" + adjustmentId + "\",\"kind\":\"CORRECTION\",\"amount\":\"2000.00\"")
                .contains("\"manager\":{\"workingDaysAtLock\":\"20\",\"unpaidAtLock\":\"1\"}");
        assertThat(result.warning()).isEqualTo("Calendar changed after the lock");
        assertThat(result.adjustmentLines()).hasSize(1);
    }

    @Test
    void aMidMonthRaiseMakesTwoSalaryRows() {
        List<EarnedPay.DayValue> days = List.of(
                new EarnedPay.DayValue(D1, d("26000"), 20, d("1300.0000000000")),
                new EarnedPay.DayValue(D2, d("26000"), 20, d("1300.0000000000")),
                new EarnedPay.DayValue(D3, d("31200"), 20, d("1560.0000000000")));
        EarnedPay.Found earned = new EarnedPay.Found(d("4160.00"), d("4160.0000000000"), days);

        PersonResult result = LineCalculator.calculate(inputs(earned, noDeduction()));

        assertThat(result.workingJson())
                .contains("{\"from\":\"2026-09-01\",\"to\":\"2026-09-02\",\"monthly\":\"26000.00\"}")
                .contains("{\"from\":\"2026-09-03\",\"to\":\"2026-09-03\",\"monthly\":\"31200.00\"}");
        // the salary shown on the line is the one on the last payable date
        assertThat(result.salary()).isEqualByComparingTo("31200.00");
    }

    // ------------------------------------------------------------------------ fingerprint

    @Test
    void theFingerprintIsTheSameForTheSameInputsAndDifferentWhenAnyInputChanges() {
        UUID adjustmentId = UUID.randomUUID();
        Inputs base = inputs(
                earnedOf("30000", 20, D1, D2),
                deductionOf("30000", 20, Map.of(D2, "1")),
                new AdjustmentInput(adjustmentId, "CORRECTION", d("100.00"), "a"));

        String first = LineCalculator.calculate(base).fingerprint();
        assertThat(LineCalculator.calculate(base).fingerprint()).isEqualTo(first);
        assertThat(first).hasSize(64);

        // a different salary
        assertThat(LineCalculator.calculate(inputs(
                                earnedOf("31000", 20, D1, D2),
                                deductionOf("31000", 20, Map.of(D2, "1")),
                                new AdjustmentInput(adjustmentId, "CORRECTION", d("100.00"), "a")))
                        .fingerprint())
                .isNotEqualTo(first);
        // a different unpaid day
        assertThat(LineCalculator.calculate(inputs(
                                earnedOf("30000", 20, D1, D2),
                                deductionOf("30000", 20, Map.of(D1, "1")),
                                new AdjustmentInput(adjustmentId, "CORRECTION", d("100.00"), "a")))
                        .fingerprint())
                .isNotEqualTo(first);
        // an extra adjustment
        assertThat(LineCalculator.calculate(inputs(
                                earnedOf("30000", 20, D1, D2),
                                deductionOf("30000", 20, Map.of(D2, "1")),
                                new AdjustmentInput(adjustmentId, "CORRECTION", d("100.00"), "a"),
                                new AdjustmentInput(UUID.randomUUID(), "CORRECTION", d("1.00"), "b")))
                        .fingerprint())
                .isNotEqualTo(first);
        // a different name
        Inputs renamed = new Inputs(
                PERSON,
                "Tara T.",
                "Teacher",
                base.earned(),
                base.deduction(),
                Map.of(),
                POLICY,
                base.adjustments(),
                null,
                null,
                null);
        assertThat(LineCalculator.calculate(renamed).fingerprint()).isNotEqualTo(first);
    }

    @Test
    void theFingerprintIgnoresTheOrderTheAdjustmentsWereGivenIn() {
        UUID a = UUID.fromString("00000000-0000-0000-0000-00000000000a");
        UUID b = UUID.fromString("00000000-0000-0000-0000-00000000000b");
        AdjustmentInput one = new AdjustmentInput(a, "CORRECTION", d("1.00"), "x");
        AdjustmentInput two = new AdjustmentInput(b, "CORRECTION", d("2.00"), "y");

        String forward = LineCalculator.calculate(inputs(earnedOf("30000", 20, D1), noDeduction(), one, two)).fingerprint();
        String backward = LineCalculator.calculate(inputs(earnedOf("30000", 20, D1), noDeduction(), two, one)).fingerprint();

        assertThat(forward).isEqualTo(backward);
    }

    @Test
    void textInTheWorkingIsEscaped() {
        PersonResult result = LineCalculator.calculate(inputs(
                earnedOf("30000", 20, D1),
                noDeduction(),
                new AdjustmentInput(UUID.randomUUID(), "CORRECTION", d("1.00"), "He said \"ok\"\nnext\\line")));

        assertThat(result.workingJson()).contains("He said \\\"ok\\\"\\nnext\\\\line");
    }
}
