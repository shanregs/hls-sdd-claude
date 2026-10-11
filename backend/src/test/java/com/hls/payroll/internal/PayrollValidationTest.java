package com.hls.payroll.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hls.school.api.InvalidInputException;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Spec 013a FR-003, FR-004, FR-007: the server-side input rules, including the month boundary (T013, T022). */
class PayrollValidationTest {

    private static LocalDate d(String iso) {
        return LocalDate.parse(iso);
    }

    @Test
    void anEffectiveDateCannotBeBeforeTheFirstDayOfTheCurrentBusinessMonth() {
        LocalDate today = d("2026-11-01"); // 00:30 IST on the 1st is still the previous day in UTC; callers pass the IST date
        assertThat(PayrollInput.effectiveDate(d("2026-11-01"), today)).isEqualTo(d("2026-11-01"));
        assertThat(PayrollInput.effectiveDate(d("2027-06-15"), today)).isEqualTo(d("2027-06-15"));
        assertThatThrownBy(() -> PayrollInput.effectiveDate(d("2026-10-31"), today))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("01/11/2026");
    }

    @Test
    void onTheLastDayOfAMonthTheWholeMonthIsStillOpen() {
        LocalDate today = d("2026-11-30");
        assertThat(PayrollInput.effectiveDate(d("2026-11-01"), today)).isEqualTo(d("2026-11-01"));
        assertThatThrownBy(() -> PayrollInput.effectiveDate(d("2026-10-31"), today))
                .isInstanceOf(InvalidInputException.class);
    }

    @Test
    void aMissingDateIsRefused() {
        assertThatThrownBy(() -> PayrollInput.effectiveDate(null, d("2026-11-01")))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("required");
    }

    @Test
    void aNoteIsTrimmedBlankMeansNoneAndOver500CharactersIsRefused() {
        assertThat(PayrollInput.note(null)).isNull();
        assertThat(PayrollInput.note("   ")).isNull();
        assertThat(PayrollInput.note("  Annual revision ")).isEqualTo("Annual revision");
        assertThat(PayrollInput.note("n".repeat(500))).hasSize(500);
        assertThatThrownBy(() -> PayrollInput.note("n".repeat(501)))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("500");
    }

    @Test
    void aNoteIsStoredAsPlainTextNotInterpreted() {
        assertThat(PayrollInput.note("<script>alert(1)</script>")).isEqualTo("<script>alert(1)</script>");
    }

    @Test
    void anAmountMustBeMoreThanZeroWithTwoDecimalsAtMostAndUnderTheMaximum() {
        assertThat(SalaryStructureService.amount(new BigDecimal("30000"))).isEqualByComparingTo("30000.00");
        assertThat(SalaryStructureService.amount(new BigDecimal("0.01"))).isEqualByComparingTo("0.01");
        assertThat(SalaryStructureService.amount(new BigDecimal("9999999.99"))).isEqualByComparingTo("9999999.99");
        assertThat(SalaryStructureService.amount(new BigDecimal("100.10"))).isEqualByComparingTo("100.1");
        for (String bad : new String[] {"0", "-1", "100.123", "9999999.991", "10000000"}) {
            assertThatThrownBy(() -> SalaryStructureService.amount(new BigDecimal(bad)))
                    .as(bad)
                    .isInstanceOf(InvalidInputException.class);
        }
        assertThatThrownBy(() -> SalaryStructureService.amount(null)).isInstanceOf(InvalidInputException.class);
    }

    @Test
    void aHalfDayFractionMustBeStrictlyBetweenZeroAndOneWithThreeDecimalsAtMost() {
        assertThat(PayPolicyService.fraction(new BigDecimal("0.5"))).isEqualByComparingTo("0.500");
        assertThat(PayPolicyService.fraction(new BigDecimal("0.001"))).isEqualByComparingTo("0.001");
        assertThat(PayPolicyService.fraction(new BigDecimal("0.999"))).isEqualByComparingTo("0.999");
        for (String bad : new String[] {"0", "1", "-0.5", "1.5", "0.5555", "0.0001"}) {
            assertThatThrownBy(() -> PayPolicyService.fraction(new BigDecimal(bad)))
                    .as(bad)
                    .isInstanceOf(InvalidInputException.class);
        }
        assertThatThrownBy(() -> PayPolicyService.fraction(null)).isInstanceOf(InvalidInputException.class);
    }
}
