package com.hls.attendance;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.attendance.internal.Rollup;
import com.hls.attendance.internal.RollupCalculator;
import com.hls.attendance.internal.RollupCalculator.MarkFacts;
import com.hls.attendance.internal.RollupCalculator.WeeklyOffRules;
import com.hls.attendance.internal.StatusCategory;
import com.hls.teacher.api.TeacherDirectory.PlacementSpan;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 008 research.md section 4: the table-driven rollup rules (SC-003). October 2026 starts on a Thursday. */
class RollupCalculatorTest {

    private static final YearMonth OCT = YearMonth.of(2026, 10);
    private static final UUID TEACHER = UUID.randomUUID();
    private static final UUID SCHOOL_A = UUID.randomUUID();
    private static final UUID SCHOOL_B = UUID.randomUUID();
    private static final LocalDate AFTER_MONTH = LocalDate.of(2026, 11, 5);

    private static final WeeklyOffRules SUNDAY_OFF = new WeeklyOffRules(Set.of(DayOfWeek.SUNDAY), Map.of());

    private static final MarkFacts PRESENT = facts(StatusCategory.WORKED, "1.00", "1.00");
    private static final MarkFacts PRESENT_HALF = facts(StatusCategory.WORKED, "0.50", "1.00");
    private static final MarkFacts LEAVE = facts(StatusCategory.LEAVE, "1.00", "0.00");
    private static final MarkFacts TRAINING = facts(StatusCategory.TRAINING, "1.00", "1.00");
    private static final MarkFacts NON_WORKING = facts(StatusCategory.NON_WORKING, "1.00", "0.00");

    private static MarkFacts facts(StatusCategory category, String value, String weight) {
        return new MarkFacts(category, new BigDecimal(value), new BigDecimal(weight));
    }

    private static PlacementSpan placement(UUID school, LocalDate from, LocalDate to) {
        return new PlacementSpan(TEACHER, school, from, to);
    }

    private static List<PlacementSpan> wholeMonthAtA() {
        return List.of(placement(SCHOOL_A, LocalDate.of(2026, 1, 1), null));
    }

    private static LocalDate oct(int day) {
        return LocalDate.of(2026, 10, day);
    }

    private static void assertRollup(
            Rollup r, String working, String worked, String leave, String available, String attended, int unmarked, String total) {
        assertThat(r.workingDays()).as("workingDays").isEqualByComparingTo(working);
        assertThat(r.daysWorked()).as("daysWorked").isEqualByComparingTo(worked);
        assertThat(r.daysLeave()).as("daysLeave").isEqualByComparingTo(leave);
        assertThat(r.trainingAvailable()).as("trainingAvailable").isEqualByComparingTo(available);
        assertThat(r.trainingAttended()).as("trainingAttended").isEqualByComparingTo(attended);
        assertThat(r.unmarked()).as("unmarked").isEqualTo(unmarked);
        assertThat(r.weightedTotal()).as("weightedTotal").isEqualByComparingTo(total);
    }

    @Test
    void anUnmarkedPastMonthCountsEveryWorkingDayAsUnmarked() {
        Rollup r = RollupCalculator.compute(OCT, wholeMonthAtA(), Map.of(), SUNDAY_OFF, Set.of(), AFTER_MONTH);

        // 31 days minus Sundays 4, 11, 18, 25.
        assertRollup(r, "27", "0", "0", "0", "0", 27, "0");
    }

    @Test
    void daysAfterTodayAreNotUnmarkedButStillWorkingDays() {
        Rollup r = RollupCalculator.compute(OCT, wholeMonthAtA(), Map.of(), SUNDAY_OFF, Set.of(), oct(10));

        // Oct 1-10 minus Sunday 4 = 9 days up to today.
        assertRollup(r, "27", "0", "0", "0", "0", 9, "0");
    }

    @Test
    void aKnownMixOfWholeHalfLeaveAndTrainingDaysRollsUpToAFractionalTotal() {
        Map<LocalDate, MarkFacts> marks = new HashMap<>();
        int[] whole = {1, 2, 3, 5, 6, 7, 8, 9, 10, 12};
        for (int d : whole) {
            marks.put(oct(d), PRESENT);
        }
        marks.put(oct(13), PRESENT_HALF);
        marks.put(oct(14), PRESENT_HALF);
        marks.put(oct(15), LEAVE);
        marks.put(oct(16), LEAVE);
        marks.put(oct(19), TRAINING);

        Rollup r = RollupCalculator.compute(OCT, wholeMonthAtA(), marks, SUNDAY_OFF, Set.of(), AFTER_MONTH);

        // worked 10 + 2 halves = 11, leave 2, training 1; weighted = 11 + 1.
        assertRollup(r, "27", "11", "2", "1", "1", 27 - 15, "12");
    }

    @Test
    void aTrainingMarkOnADayTheTeacherIsNotPlacedCountsOnlyAsTraining() {
        Map<LocalDate, MarkFacts> marks = new HashMap<>();
        for (int d : new int[] {5, 6, 7, 8, 9}) {
            marks.put(oct(d), TRAINING);
        }

        Rollup r = RollupCalculator.compute(OCT, List.of(), marks, SUNDAY_OFF, Set.of(), AFTER_MONTH);

        // five induction days, no placement: no working days, no weighted total, no unmarked days.
        assertRollup(r, "0", "0", "0", "5", "5", 0, "0");
    }

    @Test
    void aHalfTrainingDayWithoutAPlacementCountsHalfAttendedAndOneAvailable() {
        Rollup r = RollupCalculator.compute(
                OCT, List.of(), Map.of(oct(6), facts(StatusCategory.TRAINING, "0.50", "1.00")), SUNDAY_OFF, Set.of(), AFTER_MONTH);

        assertRollup(r, "0", "0", "0", "1", "0.5", 0, "0");
    }

    @Test
    void aNonTrainingMarkOnAnUnplacedDayStillCountsForNothing() {
        Rollup r = RollupCalculator.compute(OCT, List.of(), Map.of(oct(6), PRESENT), SUNDAY_OFF, Set.of(), AFTER_MONTH);

        assertRollup(r, "0", "0", "0", "0", "0", 0, "0");
    }

    @Test
    void anAnUnplacedTrainingDayBeforeAPlacementDoesNotChangeTheWorkingDaysAfterIt() {
        Map<LocalDate, MarkFacts> marks = new HashMap<>();
        marks.put(oct(5), TRAINING);
        marks.put(oct(6), TRAINING);
        marks.put(oct(12), PRESENT);

        Rollup r = RollupCalculator.compute(
                OCT, List.of(placement(SCHOOL_A, oct(12), null)), marks, SUNDAY_OFF, Set.of(), AFTER_MONTH);

        // placed from Oct 12: Oct 12-31 minus Sundays 18, 25 = 18 working days; two induction days sit outside them.
        assertRollup(r, "18", "1", "0", "2", "2", 17, "1");
    }

    @Test
    void anExplicitMarkOnAWeeklyOffDayCountsAndAddsAWorkingDay() {
        Map<LocalDate, MarkFacts> marks = Map.of(oct(4), PRESENT);

        Rollup r = RollupCalculator.compute(OCT, wholeMonthAtA(), marks, SUNDAY_OFF, Set.of(), AFTER_MONTH);

        assertRollup(r, "28", "1", "0", "0", "0", 27, "1");
    }

    @Test
    void anOrganizationWideHolidayRemovesAWorkingDayUnlessTheTeacherMarkedIt() {
        Set<LocalDate> holidays = Set.of(oct(2));

        Rollup unmarked = RollupCalculator.compute(OCT, wholeMonthAtA(), Map.of(), SUNDAY_OFF, holidays, AFTER_MONTH);
        Rollup marked =
                RollupCalculator.compute(OCT, wholeMonthAtA(), Map.of(oct(2), PRESENT), SUNDAY_OFF, holidays, AFTER_MONTH);

        assertRollup(unmarked, "26", "0", "0", "0", "0", 26, "0");
        assertRollup(marked, "27", "1", "0", "0", "0", 26, "1");
    }

    @Test
    void anExplicitNonWorkingMarkRemovesTheDateFromWorkingDays() {
        Rollup r = RollupCalculator.compute(
                OCT, wholeMonthAtA(), Map.of(oct(7), NON_WORKING), SUNDAY_OFF, Set.of(), AFTER_MONTH);

        assertRollup(r, "26", "0", "0", "0", "0", 26, "0");
    }

    @Test
    void aSchoolOverrideReplacesTheDefaultWeeklyOffDays() {
        WeeklyOffRules monToSat = new WeeklyOffRules(Set.of(DayOfWeek.SUNDAY), Map.of(SCHOOL_A, Set.of()));

        Rollup r = RollupCalculator.compute(OCT, wholeMonthAtA(), Map.of(), monToSat, Set.of(), AFTER_MONTH);

        assertRollup(r, "31", "0", "0", "0", "0", 31, "0");
    }

    @Test
    void aPlacementChangeMidMonthFollowsEachDatesSchool() {
        WeeklyOffRules rules = new WeeklyOffRules(
                Set.of(DayOfWeek.SUNDAY), Map.of(SCHOOL_B, Set.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)));
        List<PlacementSpan> placements = List.of(
                placement(SCHOOL_A, LocalDate.of(2026, 1, 1), oct(15)), placement(SCHOOL_B, oct(16), null));

        Rollup r = RollupCalculator.compute(OCT, placements, Map.of(), rules, Set.of(), AFTER_MONTH);

        // Oct 1-15 at A: 15 days minus Sundays 4, 11 = 13. Oct 16-31 at B: 16 days minus Sat 17, 24, 31 and Sun 18, 25 = 11.
        assertRollup(r, "24", "0", "0", "0", "0", 24, "0");
    }

    @Test
    void datesOutsideAnyPlacementAreNotCounted() {
        List<PlacementSpan> placements = List.of(placement(SCHOOL_A, oct(20), null));

        Rollup r = RollupCalculator.compute(OCT, placements, Map.of(), SUNDAY_OFF, Set.of(), AFTER_MONTH);

        // Oct 20-31 is 12 days minus Sunday 25 = 11.
        assertRollup(r, "11", "0", "0", "0", "0", 11, "0");
    }

    @Test
    void aTeacherWhoExitsMidMonthOnlyNeedsDaysUpToTheirPlacementEnd() {
        // Exit on the 15th: the placement ends on the 14th.
        List<PlacementSpan> placements = List.of(placement(SCHOOL_A, LocalDate.of(2026, 1, 1), oct(14)));

        Rollup r = RollupCalculator.compute(OCT, placements, Map.of(), SUNDAY_OFF, Set.of(), AFTER_MONTH);

        // Oct 1-14 minus Sundays 4, 11 = 12.
        assertRollup(r, "12", "0", "0", "0", "0", 12, "0");
    }

    @Test
    void aMonthWithNoPlacementIsAllZeros() {
        Rollup r = RollupCalculator.compute(OCT, List.of(), Map.of(), SUNDAY_OFF, Set.of(), AFTER_MONTH);

        assertRollup(r, "0", "0", "0", "0", "0", 0, "0");
    }

    @Test
    void shortAndLeapFebruariesUseTheirRealLength() {
        Rollup feb2026 = RollupCalculator.compute(
                YearMonth.of(2026, 2), wholeMonthAtA(), Map.of(), SUNDAY_OFF, Set.of(), LocalDate.of(2026, 3, 5));
        Rollup feb2028 = RollupCalculator.compute(
                YearMonth.of(2028, 2), wholeMonthAtA(), Map.of(), SUNDAY_OFF, Set.of(), LocalDate.of(2028, 3, 5));

        // Feb 2026: 28 days, 4 Sundays. Feb 2028: 29 days, Sundays 6, 13, 20, 27.
        assertThat(feb2026.workingDays()).isEqualByComparingTo("24");
        assertThat(feb2028.workingDays()).isEqualByComparingTo("25");
    }

    @Test
    void aHalfDayOfLeaveCountsAsHalfALeaveDay() {
        MarkFacts halfLeave = facts(StatusCategory.LEAVE, "0.50", "0.00");

        Rollup r = RollupCalculator.compute(
                OCT, wholeMonthAtA(), Map.of(oct(7), halfLeave), SUNDAY_OFF, Set.of(), AFTER_MONTH);

        assertRollup(r, "27", "0", "0.5", "0", "0", 26, "0");
    }

    @Test
    void aCodeWeightScalesTheWeightedTotal() {
        MarkFacts partial = facts(StatusCategory.WORKED, "1.00", "0.50");

        Rollup r = RollupCalculator.compute(
                OCT, wholeMonthAtA(), Map.of(oct(7), partial), SUNDAY_OFF, Set.of(), AFTER_MONTH);

        assertRollup(r, "27", "1", "0", "0", "0", 26, "0.5");
    }

    @Test
    void theDayPlanLabelsEachKindOfDate() {
        var plans = RollupCalculator.plan(
                OCT, List.of(placement(SCHOOL_A, oct(2), null)), SUNDAY_OFF, Set.of(oct(5)));

        assertThat(plans).hasSize(31);
        assertThat(plans.get(0).kind()).isEqualTo(RollupCalculator.DayKind.NOT_PLACED); // Oct 1
        assertThat(plans.get(1).kind()).isEqualTo(RollupCalculator.DayKind.WORKING); // Oct 2
        assertThat(plans.get(3).kind()).isEqualTo(RollupCalculator.DayKind.WEEKLY_OFF); // Oct 4 Sunday
        assertThat(plans.get(4).kind()).isEqualTo(RollupCalculator.DayKind.NON_WORKING); // Oct 5 holiday
    }
}
