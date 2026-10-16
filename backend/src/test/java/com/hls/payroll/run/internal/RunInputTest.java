package com.hls.payroll.run.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hls.school.api.InvalidInputException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

/** Spec 013b T016: the shared checks and formats. */
class RunInputTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 13);

    @Test
    void aReasonIsTrimmedRequiredAndLimitedTo500Characters() {
        assertThat(RunInput.requiredReason("  Arrears for August  ", "an adjustment")).isEqualTo("Arrears for August");
        assertThat(RunInput.requiredReason("x".repeat(500), "an adjustment")).hasSize(500);
        assertThatThrownBy(() -> RunInput.requiredReason(null, "an adjustment"))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("reason is required");
        assertThatThrownBy(() -> RunInput.requiredReason("   ", "a return")).isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> RunInput.requiredReason("x".repeat(501), "an adjustment"))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("at most 500");
    }

    @Test
    void aReferenceIsOptionalTrimmedAndLimitedTo100Characters() {
        assertThat(RunInput.reference(null)).isNull();
        assertThat(RunInput.reference("   ")).isNull();
        assertThat(RunInput.reference(" UTR123 ")).isEqualTo("UTR123");
        assertThat(RunInput.reference("y".repeat(100))).hasSize(100);
        assertThatThrownBy(() -> RunInput.reference("y".repeat(101)))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("at most 100");
    }

    @Test
    void anAdjustmentAmountIsNonZeroWithAtMostTwoDecimals() {
        assertThat(RunInput.adjustmentAmount(new BigDecimal("2000"))).isEqualByComparingTo("2000.00");
        assertThat(RunInput.adjustmentAmount(new BigDecimal("2000")).scale()).isEqualTo(2);
        assertThat(RunInput.adjustmentAmount(new BigDecimal("-0.5"))).isEqualByComparingTo("-0.50");
        assertThat(RunInput.adjustmentAmount(new BigDecimal("12.500"))).isEqualByComparingTo("12.50");
        assertThatThrownBy(() -> RunInput.adjustmentAmount(null)).isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> RunInput.adjustmentAmount(BigDecimal.ZERO))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("zero");
        assertThatThrownBy(() -> RunInput.adjustmentAmount(new BigDecimal("0.00"))).isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> RunInput.adjustmentAmount(new BigDecimal("1.234")))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("two decimals");
        assertThatThrownBy(() -> RunInput.adjustmentAmount(new BigDecimal("10000000")))
                .isInstanceOf(InvalidInputException.class);
    }

    @Test
    void aMonthIsWrittenYearDashMonth() {
        assertThat(RunInput.month("2026-09")).isEqualTo(YearMonth.of(2026, 9));
        assertThat(RunInput.month(" 2026-09 ")).isEqualTo(YearMonth.of(2026, 9));
        for (String bad : new String[] {null, "", "2026-13", "09/2026", "2026-9", "September"}) {
            assertThatThrownBy(() -> RunInput.month(bad)).as(String.valueOf(bad)).isInstanceOf(InvalidInputException.class);
        }
    }

    @Test
    void onlyAMonthThatHasEndedCanBeRun() {
        assertThat(RunInput.endedMonth(YearMonth.of(2026, 9), TODAY)).isEqualTo(YearMonth.of(2026, 9));
        assertThat(RunInput.endedMonth(YearMonth.of(2025, 1), TODAY)).isEqualTo(YearMonth.of(2025, 1));
        assertThatThrownBy(() -> RunInput.endedMonth(YearMonth.of(2026, 10), TODAY))
                .isInstanceOf(InvalidInputException.class)
                .hasMessage("The month has not ended: October 2026 ends on 31/10/2026.");
        assertThatThrownBy(() -> RunInput.endedMonth(YearMonth.of(2026, 11), TODAY)).isInstanceOf(InvalidInputException.class);
        // the last day of a month is not yet the end of it; the month ended once the next one began
        assertThat(RunInput.endedMonth(YearMonth.of(2026, 9), LocalDate.of(2026, 10, 1))).isEqualTo(YearMonth.of(2026, 9));
        assertThatThrownBy(() -> RunInput.endedMonth(YearMonth.of(2026, 9), LocalDate.of(2026, 9, 30)))
                .isInstanceOf(InvalidInputException.class);
    }

    @Test
    void aPaymentDateIsRequiredAndNotInTheFuture() {
        assertThat(RunInput.paymentDate(TODAY, TODAY)).isEqualTo(TODAY);
        assertThat(RunInput.paymentDate(TODAY.minusDays(30), TODAY)).isEqualTo(TODAY.minusDays(30));
        assertThatThrownBy(() -> RunInput.paymentDate(null, TODAY)).isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> RunInput.paymentDate(TODAY.plusDays(1), TODAY))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("13/10/2026");
    }

    @Test
    void rupeesUseIndianDigitGroupingAndTwoDecimals() {
        assertThat(RunInput.rupees(new BigDecimal("0"))).isEqualTo("₹0.00");
        assertThat(RunInput.rupees(new BigDecimal("5"))).isEqualTo("₹5.00");
        assertThat(RunInput.rupees(new BigDecimal("999.5"))).isEqualTo("₹999.50");
        assertThat(RunInput.rupees(new BigDecimal("1000"))).isEqualTo("₹1,000.00");
        assertThat(RunInput.rupees(new BigDecimal("12345"))).isEqualTo("₹12,345.00");
        assertThat(RunInput.rupees(new BigDecimal("125000.5"))).isEqualTo("₹1,25,000.50");
        assertThat(RunInput.rupees(new BigDecimal("1234567.891"))).isEqualTo("₹12,34,567.89");
        assertThat(RunInput.rupees(new BigDecimal("12345678"))).isEqualTo("₹1,23,45,678.00");
        assertThat(RunInput.rupees(new BigDecimal("-1500"))).isEqualTo("-₹1,500.00");
    }

    @Test
    void datesAreShownDayMonthYear() {
        assertThat(RunInput.date(LocalDate.of(2026, 9, 3))).isEqualTo("03/09/2026");
        assertThat(RunInput.monthName(YearMonth.of(2026, 9))).isEqualTo("September 2026");
    }
}
