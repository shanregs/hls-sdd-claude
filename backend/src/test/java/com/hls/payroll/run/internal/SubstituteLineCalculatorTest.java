package com.hls.payroll.run.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.payroll.api.PayPolicyView;
import com.hls.payroll.api.PayPolicyView.RoundingRule;
import com.hls.payroll.api.PayRules.Kind;
import com.hls.payroll.api.PayRules.PersonRef;
import com.hls.payroll.run.internal.LineCalculator.AdjustmentInput;
import com.hls.payroll.run.internal.SubstituteLineCalculator.DayLine;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 017b T029: pay by the day is days x rate, one line, no salary and no deduction, and nothing is zero by accident. */
class SubstituteLineCalculatorTest {

    private static final PersonRef PERSON = new PersonRef(Kind.TEACHER, UUID.randomUUID());
    private static final UUID POLICY_ID = UUID.randomUUID();

    private static PayPolicyView policy(RoundingRule rounding) {
        return new PayPolicyView(
                POLICY_ID,
                LocalDate.of(2000, 1, 1),
                PayPolicyView.LopDivisor.WORKING_DAYS,
                new BigDecimal("0.500"),
                rounding,
                PayPolicyView.PayMonth.CALENDAR_MONTH);
    }

    private static DayLine day(int dayOfMonth, String value, String rate) {
        return new DayLine(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Sunrise School",
                LocalDate.of(2026, 10, dayOfMonth),
                new BigDecimal(value),
                new BigDecimal(rate));
    }

    private static PersonResult line(RoundingRule rounding, List<DayLine> days, List<AdjustmentInput> adjustments) {
        return SubstituteLineCalculator.calculate(new SubstituteLineCalculator.Inputs(
                PERSON, "Sita Substitute", "Substitute Teacher", days, policy(rounding), adjustments, null));
    }

    @Test
    void twoAndAHalfDaysAtEightHundredIsTwoThousandWithNoDeduction() {
        PersonResult r = line(
                RoundingRule.NEAREST_RUPEE,
                List.of(day(3, "1.00", "800"), day(4, "1.00", "800"), day(5, "0.50", "800")),
                List.of());

        assertThat(r.state()).isEqualTo(PersonResult.State.PAYABLE);
        assertThat(r.earned()).isEqualByComparingTo("2000.00");
        assertThat(r.deduction()).isEqualByComparingTo("0.00");
        assertThat(r.adjustments()).isEqualByComparingTo("0.00");
        assertThat(r.net()).isEqualByComparingTo("2000.00");
        assertThat(r.payableDays()).isEqualTo(3);
        assertThat(r.workingDays()).isEqualByComparingTo("2.50");
        assertThat(r.unpaidDays()).isEqualByComparingTo("0.00");
    }

    @Test
    void aRateChangeMidMonthPricesEachDayAtItsOwnRateAndThePayslipGetsOneRowPerRate() {
        PersonResult r = line(
                RoundingRule.NEAREST_RUPEE,
                List.of(day(3, "1.00", "800"), day(4, "1.00", "800"), day(12, "1.00", "1000")),
                List.of());

        assertThat(r.earned()).isEqualByComparingTo("2600.00");
        assertThat(r.workingJson()).contains("\"rateRows\"").contains("\"days\":\"2.00\"").contains("\"rate\":\"800.00\"");
        assertThat(r.workingJson()).contains("\"days\":\"1.00\"").contains("\"rate\":\"1000.00\"");
    }

    @Test
    void roundingIsAppliedOnceToTheMonthByThePolicy() {
        // three half days at 333.33 = 499.995
        List<DayLine> days = List.of(day(3, "0.50", "333.33"), day(4, "0.50", "333.33"), day(5, "0.50", "333.33"));

        assertThat(line(RoundingRule.NEAREST_RUPEE, days, List.of()).earned()).isEqualByComparingTo("500.00");
        assertThat(line(RoundingRule.UP, days, List.of()).earned()).isEqualByComparingTo("500.00");
        assertThat(line(RoundingRule.DOWN, days, List.of()).earned()).isEqualByComparingTo("499.00");
    }

    @Test
    void aCorrectionIsAddedToTheNetAndAMonthWithNoDayCarriesOnlyTheCorrection() {
        AdjustmentInput correction = new AdjustmentInput(UUID.randomUUID(), "CORRECTION", new BigDecimal("800.00"), "Added late");

        PersonResult withDays = line(RoundingRule.NEAREST_RUPEE, List.of(day(3, "1.00", "800")), List.of(correction));
        PersonResult onlyCorrection = line(RoundingRule.NEAREST_RUPEE, List.of(), List.of(correction));

        assertThat(withDays.net()).isEqualByComparingTo("1600.00");
        assertThat(onlyCorrection.state()).isEqualTo(PersonResult.State.PAYABLE);
        assertThat(onlyCorrection.earned()).isEqualByComparingTo("0.00");
        assertThat(onlyCorrection.net()).isEqualByComparingTo("800.00");
        assertThat(onlyCorrection.payableDays()).isZero();
    }

    @Test
    void aSubstituteWhoCannotBePricedIsReportedWithTheReasonAndNoAmounts() {
        PersonResult r = SubstituteLineCalculator.calculate(new SubstituteLineCalculator.Inputs(
                PERSON, "Sita Substitute", "Substitute Teacher", List.of(), policy(RoundingRule.NEAREST_RUPEE), List.of(),
                "No day rate is recorded for this designation on 2026-10-03."));

        assertThat(r.state()).isEqualTo(PersonResult.State.CANNOT_PAY);
        assertThat(r.reason()).contains("No day rate");
        assertThat(r.earned()).isNull();
        assertThat(r.net()).isNull();
    }

    @Test
    void theSameInputsGiveTheSameFingerprintSoARecomputeChangesNothing() {
        List<DayLine> days = List.of(day(3, "1.00", "800"), day(4, "0.50", "800"));

        assertThat(line(RoundingRule.NEAREST_RUPEE, days, List.of()).fingerprint())
                .isEqualTo(line(RoundingRule.NEAREST_RUPEE, days, List.of()).fingerprint());
        assertThat(line(RoundingRule.NEAREST_RUPEE, days, List.of()).fingerprint())
                .isNotEqualTo(line(RoundingRule.NEAREST_RUPEE, List.of(day(3, "1.00", "800")), List.of()).fingerprint());
    }
}
