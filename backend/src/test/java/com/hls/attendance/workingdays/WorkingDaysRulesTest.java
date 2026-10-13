package com.hls.attendance.workingdays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hls.attendance.workingdays.internal.WorkingDaysRules;
import com.hls.school.api.InvalidInputException;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Spec 037 research.md sections 2 and 3: the figure range per month (leap years), half steps and the year window. */
class WorkingDaysRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2027, 3, 10);

    private static BigDecimal d(String value) {
        return new BigDecimal(value);
    }

    @Test
    void leapFebruaryAccepts0To29InHalfDays() {
        for (String ok : new String[] {"0", "0.5", "28.5", "29"}) {
            assertThatCode(() -> WorkingDaysRules.checkFigure(d(ok), 2028, 2)).as(ok).doesNotThrowAnyException();
        }
        for (String bad : new String[] {"29.5", "-0.5"}) {
            assertThatThrownBy(() -> WorkingDaysRules.checkFigure(d(bad), 2028, 2))
                    .as(bad)
                    .isInstanceOf(InvalidInputException.class)
                    .hasMessageContaining("0 to 29");
        }
    }

    @Test
    void ordinaryFebruaryStopsAt28() {
        assertThatCode(() -> WorkingDaysRules.checkFigure(d("28"), 2027, 2)).doesNotThrowAnyException();
        assertThatThrownBy(() -> WorkingDaysRules.checkFigure(d("28.5"), 2027, 2))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("0 to 28");
        assertThatThrownBy(() -> WorkingDaysRules.checkFigure(d("29"), 2027, 2))
                .isInstanceOf(InvalidInputException.class);
    }

    @Test
    void thirtyDayMonthRefusesQuartersAndTooMany() {
        assertThatCode(() -> WorkingDaysRules.checkFigure(d("25.5"), 2027, 4)).doesNotThrowAnyException();
        assertThatCode(() -> WorkingDaysRules.checkFigure(d("30"), 2027, 4)).doesNotThrowAnyException();
        assertThatThrownBy(() -> WorkingDaysRules.checkFigure(d("30.5"), 2027, 4))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("April 2027")
                .hasMessageContaining("0 to 30, in half days");
        assertThatThrownBy(() -> WorkingDaysRules.checkFigure(d("25.3"), 2027, 4))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("half days");
    }

    @Test
    void thirtyOneDayMonthAccepts31ButNot31AndAHalf() {
        assertThatCode(() -> WorkingDaysRules.checkFigure(d("31"), 2027, 7)).doesNotThrowAnyException();
        assertThatThrownBy(() -> WorkingDaysRules.checkFigure(d("31.5"), 2027, 7))
                .isInstanceOf(InvalidInputException.class);
    }

    @Test
    void aMissingFigureAndAMonthOutOfRangeAreRefused() {
        assertThatThrownBy(() -> WorkingDaysRules.checkFigure(null, 2027, 7))
                .isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> WorkingDaysRules.checkFigure(d("1"), 2027, 0))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("Month");
        assertThatThrownBy(() -> WorkingDaysRules.checkFigure(d("1"), 2027, 13))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("Month");
    }

    @Test
    void newRowsMustFallWithinFiveYearsEitherSideOfToday() {
        assertThatCode(() -> WorkingDaysRules.checkNewYear(2022, TODAY)).doesNotThrowAnyException();
        assertThatCode(() -> WorkingDaysRules.checkNewYear(2032, TODAY)).doesNotThrowAnyException();
        assertThatThrownBy(() -> WorkingDaysRules.checkNewYear(2021, TODAY))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("2022 to 2032");
        assertThatThrownBy(() -> WorkingDaysRules.checkNewYear(2033, TODAY))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("2022 to 2032");
    }

    @Test
    void theFigureIsNormalisedToOneDecimal() {
        assertThat(WorkingDaysRules.checkFigure(d("26"), 2027, 3)).isEqualByComparingTo("26.0");
        assertThat(WorkingDaysRules.checkFigure(d("26.50"), 2027, 3).scale()).isEqualTo(1);
    }
}
