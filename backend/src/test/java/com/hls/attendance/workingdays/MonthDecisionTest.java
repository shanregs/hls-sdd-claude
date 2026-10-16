package com.hls.attendance.workingdays;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.attendance.workingdays.internal.MonthDecision;
import com.hls.attendance.workingdays.internal.MonthDecision.Kind;
import com.hls.attendance.workingdays.internal.SchoolWorkingDaysRow;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** Spec 038 data-model.md "Month status rules": every row of the table, plus the order of the checks. */
class MonthDecisionTest {

    private static final int YEAR = 2027;
    private static final int MARCH = 3;

    private static BigDecimal d(String value) {
        return new BigDecimal(value);
    }

    private static SchoolWorkingDaysRow stored(String figure, long version) {
        SchoolWorkingDaysRow row =
                new SchoolWorkingDaysRow(UUID.randomUUID(), YEAR, MARCH, d(figure), UUID.randomUUID(), Instant.now());
        ReflectionTestUtils.setField(row, "version", version);
        return row;
    }

    private static Kind decide(String typed, Long version, SchoolWorkingDaysRow current) {
        return MonthDecision.decide(d(typed), version, current, true, true, YEAR, MARCH).kind();
    }

    @Test
    void aMonthWithNoFigureIsCreated() {
        assertThat(decide("26.0", null, null)).isEqualTo(Kind.CREATE);
    }

    @Test
    void aRowThatAppearedMeanwhileIsUnchangedWhenEqualAndAlreadyExistsWhenDifferent() {
        assertThat(decide("26.0", null, stored("26.0", 0))).isEqualTo(Kind.UNCHANGED);
        assertThat(decide("26", null, stored("26.0", 0))).isEqualTo(Kind.UNCHANGED);
        assertThat(decide("25.5", null, stored("26.0", 0))).isEqualTo(Kind.ALREADY_EXISTS);
    }

    @Test
    void aMatchingVersionIsUnchangedWhenEqualAndUpdatedWhenDifferent() {
        assertThat(decide("26.0", 2L, stored("26.0", 2))).isEqualTo(Kind.UNCHANGED);
        assertThat(decide("25.5", 2L, stored("26.0", 2))).isEqualTo(Kind.UPDATE);
    }

    @Test
    void aDifferingVersionIsUnchangedWhenEqualAndStaleWhenDifferent() {
        assertThat(decide("26.0", 2L, stored("26.0", 3))).isEqualTo(Kind.UNCHANGED);
        assertThat(decide("25.5", 2L, stored("26.0", 3))).isEqualTo(Kind.STALE);
    }

    @Test
    void aRowDeletedMeanwhileIsNeverRecreated() {
        assertThat(decide("26.0", 2L, null)).isEqualTo(Kind.ROW_DELETED);
    }

    @Test
    void aFigureOutsideTheRangeOrNotAHalfStepIsInvalidWhateverTheState() {
        for (String bad : new String[] {"31.5", "32", "-1", "25.3", "25.25"}) {
            assertThat(decide(bad, null, null)).as(bad).isEqualTo(Kind.INVALID_FIGURE);
            assertThat(decide(bad, 2L, stored("26.0", 2))).as(bad).isEqualTo(Kind.INVALID_FIGURE);
        }
        MonthDecision refused = MonthDecision.decide(d("32"), null, null, true, true, YEAR, MARCH);
        assertThat(refused.message()).contains("0 to 31");
    }

    @Test
    void zeroAndTheDaysInTheMonthAreValid() {
        assertThat(decide("0", null, null)).isEqualTo(Kind.CREATE);
        assertThat(decide("31", null, null)).isEqualTo(Kind.CREATE);
        assertThat(MonthDecision.decide(d("29"), null, null, true, true, 2028, 2).kind()).isEqualTo(Kind.CREATE);
        assertThat(MonthDecision.decide(d("29"), null, null, true, true, 2027, 2).kind()).isEqualTo(Kind.INVALID_FIGURE);
    }

    @Test
    void aMissingFigureIsInvalid() {
        assertThat(MonthDecision.decide(null, null, null, true, true, YEAR, MARCH).kind())
                .isEqualTo(Kind.INVALID_FIGURE);
    }

    @Test
    void creatingNeedsCreateAndChangingNeedsEdit() {
        assertThat(MonthDecision.decide(d("26"), null, null, false, true, YEAR, MARCH).kind())
                .isEqualTo(Kind.NOT_PERMITTED);
        assertThat(MonthDecision.decide(d("25.5"), 2L, stored("26.0", 2), true, false, YEAR, MARCH).kind())
                .isEqualTo(Kind.NOT_PERMITTED);
        assertThat(MonthDecision.decide(d("26"), null, null, true, false, YEAR, MARCH).kind()).isEqualTo(Kind.CREATE);
        assertThat(MonthDecision.decide(d("25.5"), 2L, stored("26.0", 2), false, true, YEAR, MARCH).kind())
                .isEqualTo(Kind.UPDATE);
    }

    @Test
    void theFigureIsCheckedBeforeThePermissionAndThePermissionBeforeTheStoredState() {
        assertThat(MonthDecision.decide(d("32"), null, null, false, false, YEAR, MARCH).kind())
                .isEqualTo(Kind.INVALID_FIGURE);
        assertThat(MonthDecision.decide(d("26"), 2L, null, true, false, YEAR, MARCH).kind())
                .isEqualTo(Kind.NOT_PERMITTED);
        assertThat(MonthDecision.decide(d("26"), null, stored("26.0", 0), false, true, YEAR, MARCH).kind())
                .isEqualTo(Kind.NOT_PERMITTED);
    }
}
