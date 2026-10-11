package com.hls.payroll;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hls.payroll.api.PayPolicyView.RoundingRule;
import com.hls.payroll.internal.LossOfPayCalculator;
import com.hls.payroll.internal.LossOfPayCalculator.DayInput;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Spec 013a FR-009, FR-018 and the clarified rules: values kept unrounded, one rounding on the month total (T028). */
class LossOfPayCalculatorTest {

    private static final BigDecimal HALF = new BigDecimal("0.500");

    private static BigDecimal d(String value) {
        return new BigDecimal(value);
    }

    @Test
    void aFullDayAndAHalfDayOver26WorkingDays() {
        BigDecimal full = LossOfPayCalculator.fullDay(d("26000.00"), 26);
        assertThat(full).isEqualByComparingTo("1000");
        assertThat(LossOfPayCalculator.halfDay(full, HALF)).isEqualByComparingTo("500");
    }

    @Test
    void aNonTerminatingDayValueIsKeptUnrounded() {
        BigDecimal full = LossOfPayCalculator.fullDay(d("30000.00"), 26);
        // not rounded to a rupee or a paisa: ten decimal places of 1153.846153846...
        assertThat(full).isEqualByComparingTo("1153.8461538462");
        assertThat(full.scale()).isEqualTo(LossOfPayCalculator.SCALE);
    }

    @Test
    void aHalfDayFractionOtherThanAHalf() {
        BigDecimal full = LossOfPayCalculator.fullDay(d("26000.00"), 26);
        assertThat(LossOfPayCalculator.halfDay(full, d("0.250"))).isEqualByComparingTo("250");
        assertThat(LossOfPayCalculator.halfDay(full, d("0.667"))).isEqualByComparingTo("667");
    }

    @Test
    void zeroWorkingDaysIsRefusedWithoutDividing() {
        assertThatThrownBy(() -> LossOfPayCalculator.fullDay(d("30000.00"), 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("0 working days");
    }

    @Test
    void theMonthTotalRoundsExactlyOnceByTheRule() {
        // 3 unpaid days at 30000/26 = 1153.8461538462 each = 3461.5384615386
        List<DayInput> days = List.of(
                new DayInput(BigDecimal.ONE, d("30000"), 26),
                new DayInput(BigDecimal.ONE, d("30000"), 26),
                new DayInput(BigDecimal.ONE, d("30000"), 26));
        BigDecimal total = LossOfPayCalculator.sum(days);
        assertThat(total).isEqualByComparingTo("3461.5384615386");
        assertThat(LossOfPayCalculator.round(total, RoundingRule.NEAREST_RUPEE)).isEqualByComparingTo("3462");
        assertThat(LossOfPayCalculator.round(total, RoundingRule.UP)).isEqualByComparingTo("3462");
        assertThat(LossOfPayCalculator.round(total, RoundingRule.DOWN)).isEqualByComparingTo("3461");
    }

    @Test
    void roundingToTheNearestRupeeHalvesGoUp() {
        assertThat(LossOfPayCalculator.round(d("100.50"), RoundingRule.NEAREST_RUPEE)).isEqualByComparingTo("101");
        assertThat(LossOfPayCalculator.round(d("100.49"), RoundingRule.NEAREST_RUPEE)).isEqualByComparingTo("100");
    }

    @Test
    void roundingTheRoundedDaysWouldGiveADifferentTotal() {
        // proof the rounding is on the total only: 7 days at 1153.8461538462 round per day to 7 x 1154 = 8078,
        // but the total 8076.92... rounds to 8077
        List<DayInput> days = java.util.Collections.nCopies(7, new DayInput(BigDecimal.ONE, d("30000"), 26));
        assertThat(LossOfPayCalculator.round(LossOfPayCalculator.sum(days), RoundingRule.NEAREST_RUPEE))
                .isEqualByComparingTo("8077");
    }

    @Test
    void aRaiseOnThe16thValuesEachDayAtItsOwnSalary() {
        // working days 26: days before the raise at 26000, days after at 31200
        List<DayInput> days = List.of(
                new DayInput(BigDecimal.ONE, d("26000"), 26), // 1000
                new DayInput(BigDecimal.ONE, d("26000"), 26), // 1000
                new DayInput(BigDecimal.ONE, d("31200"), 26), // 1200
                new DayInput(HALF, d("31200"), 26)); // 600
        BigDecimal total = LossOfPayCalculator.sum(days);
        assertThat(total).isEqualByComparingTo("3800");
        assertThat(LossOfPayCalculator.round(total, RoundingRule.NEAREST_RUPEE)).isEqualByComparingTo("3800");
    }

    @Test
    void aMonthWithNoUnpaidDaysDeductsNothing() {
        assertThat(LossOfPayCalculator.round(LossOfPayCalculator.sum(List.of()), RoundingRule.NEAREST_RUPEE))
                .isEqualByComparingTo("0");
    }
}
