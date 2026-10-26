package com.hls.training.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hls.training.calendar.internal.PatternRules;
import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

/** Spec 024 T024: the date maths of a standing pattern. */
class PatternRulesTest {

    private static LocalDate nth(YearMonth m, int nth, int weekday) {
        return PatternRules.dateIn(m, PatternRules.NTH_WEEKDAY, nth, weekday, null);
    }

    private static LocalDate day(YearMonth m, int day) {
        return PatternRules.dateIn(m, PatternRules.DAY_OF_MONTH, null, null, day);
    }

    @Test
    void firstToFourthAndLastWeekday() {
        YearMonth nov = YearMonth.of(2026, 11); // 1 Nov 2026 is a Sunday
        assertThat(nth(nov, 1, 6)).isEqualTo(LocalDate.of(2026, 11, 7));
        assertThat(nth(nov, 2, 6)).isEqualTo(LocalDate.of(2026, 11, 14));
        assertThat(nth(nov, 3, 6)).isEqualTo(LocalDate.of(2026, 11, 21));
        assertThat(nth(nov, 4, 6)).isEqualTo(LocalDate.of(2026, 11, 28));
        assertThat(nth(nov, PatternRules.LAST, 6)).isEqualTo(LocalDate.of(2026, 11, 28));
        assertThat(nth(nov, 1, 7)).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(nth(nov, PatternRules.LAST, 7)).isEqualTo(LocalDate.of(2026, 11, 29));
        assertThat(nth(nov, 1, 1)).isEqualTo(LocalDate.of(2026, 11, 2));
    }

    @Test
    void lastDiffersFromFourthWhenTheMonthHasFiveOfThatWeekday() {
        YearMonth oct = YearMonth.of(2026, 10); // five Saturdays: 3, 10, 17, 24, 31
        assertThat(nth(oct, 4, 6)).isEqualTo(LocalDate.of(2026, 10, 24));
        assertThat(nth(oct, PatternRules.LAST, 6)).isEqualTo(LocalDate.of(2026, 10, 31));
    }

    @Test
    void fixedDayIsClampedToTheMonthEnd() {
        assertThat(day(YearMonth.of(2026, 4), 31)).isEqualTo(LocalDate.of(2026, 4, 30));
        assertThat(day(YearMonth.of(2026, 2), 30)).isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(day(YearMonth.of(2028, 2), 30)).isEqualTo(LocalDate.of(2028, 2, 29));
        assertThat(day(YearMonth.of(2026, 5), 31)).isEqualTo(LocalDate.of(2026, 5, 31));
        assertThat(day(YearMonth.of(2026, 5), 1)).isEqualTo(LocalDate.of(2026, 5, 1));
        assertThat(PatternRules.clamped(YearMonth.of(2026, 4), PatternRules.DAY_OF_MONTH, 31)).isTrue();
        assertThat(PatternRules.clamped(YearMonth.of(2026, 5), PatternRules.DAY_OF_MONTH, 31)).isFalse();
    }

    @Test
    void previewCrossesTheYearBoundaryAndNotesTheClamp() {
        var rows = PatternRules.preview(LocalDate.of(2026, 11, 1), 4, PatternRules.DAY_OF_MONTH, null, null, 31);
        assertThat(rows).extracting(PatternRules.Occurrence::date)
                .containsExactly(
                        LocalDate.of(2026, 11, 30), LocalDate.of(2026, 12, 31), LocalDate.of(2027, 1, 31), LocalDate.of(2027, 2, 28));
        assertThat(rows.get(0).note()).contains("last day");
        assertThat(rows.get(1).note()).isNull();
        assertThat(rows.get(3).note()).contains("last day");
        assertThat(rows.get(2).month()).isEqualTo(LocalDate.of(2027, 1, 1));
    }

    @Test
    void invalidRulesAreRefused() {
        YearMonth m = YearMonth.of(2026, 11);
        assertThatThrownBy(() -> nth(m, 0, 6)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> nth(m, 6, 6)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> nth(m, 1, 8)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> day(m, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> day(m, 32)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PatternRules.dateIn(m, "OTHER", null, null, null)).isInstanceOf(IllegalArgumentException.class);
    }
}
