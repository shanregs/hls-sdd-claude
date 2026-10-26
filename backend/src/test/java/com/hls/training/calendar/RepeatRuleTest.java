package com.hls.training.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.training.calendar.internal.RepeatRule;
import com.hls.training.calendar.internal.RepeatRule.Fact;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Spec 024 T048: which exceptions count towards the repeat marker. */
class RepeatRuleTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 26);

    private static Fact fact(LocalDate date, String status, String closure) {
        return new Fact(date, status, closure);
    }

    @Test
    void openAndAcknowledgedCountButTheRestDoNot() {
        List<Fact> facts = List.of(
                fact(TODAY.minusDays(10), "OPEN", null),
                fact(TODAY.minusDays(20), "CLOSED", "ACKNOWLEDGED"),
                fact(TODAY.minusDays(30), "CLOSED", "EXCUSED"),
                fact(TODAY.minusDays(40), "CLOSED", "MADE_UP"),
                fact(TODAY.minusDays(50), "WITHDRAWN", null));
        assertThat(RepeatRule.count(facts, TODAY, 6)).isEqualTo(2);
        assertThat(RepeatRule.isRepeat(facts, TODAY, 6, 2)).isTrue();
        assertThat(RepeatRule.isRepeat(facts, TODAY, 6, 3)).isFalse();
    }

    @Test
    void onlyTheWindowCounts() {
        List<Fact> facts = List.of(fact(TODAY.minusMonths(7), "OPEN", null), fact(TODAY.minusDays(5), "OPEN", null));
        assertThat(RepeatRule.count(facts, TODAY, 6)).isEqualTo(1);
        assertThat(RepeatRule.isRepeat(facts, TODAY, 6, 2)).isFalse();
        assertThat(RepeatRule.isRepeat(facts, TODAY, 12, 2)).isTrue();
    }

    @Test
    void theBoundaryDatesAreInside() {
        List<Fact> facts = List.of(fact(TODAY.minusMonths(6), "OPEN", null), fact(TODAY, "OPEN", null), fact(TODAY.minusMonths(6).minusDays(1), "OPEN", null));
        assertThat(RepeatRule.count(facts, TODAY, 6)).isEqualTo(2);
        // a session dated after today is not counted
        assertThat(RepeatRule.count(List.of(fact(TODAY.plusDays(1), "OPEN", null)), TODAY, 6)).isZero();
    }

    @Test
    void aSmallerWindowOrThresholdFromSettingsChangesTheAnswer() {
        List<Fact> facts = List.of(fact(TODAY.minusMonths(2), "OPEN", null), fact(TODAY.minusMonths(1), "OPEN", null));
        assertThat(RepeatRule.isRepeat(facts, TODAY, 1, 2)).isFalse();
        assertThat(RepeatRule.isRepeat(facts, TODAY, 3, 2)).isTrue();
        assertThat(RepeatRule.isRepeat(facts, TODAY, 3, 1)).isTrue();
        assertThat(RepeatRule.isRepeat(List.of(), TODAY, 6, 1)).isFalse();
    }
}
