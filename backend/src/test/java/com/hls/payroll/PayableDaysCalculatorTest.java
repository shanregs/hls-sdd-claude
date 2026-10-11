package com.hls.payroll;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.payroll.api.Span;
import com.hls.payroll.internal.PayableDaysCalculator;
import com.hls.payroll.internal.PayableDaysCalculator.Employment;
import com.hls.payroll.internal.PayableDaysCalculator.Result;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 013a FR-010, FR-011, SC-003: each date once, on the calendar of the place it was worked (T029). */
class PayableDaysCalculatorTest {

    private static final YearMonth NOV = YearMonth.of(2026, 11);
    private static final UUID SCHOOL_A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID SCHOOL_B = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    private static LocalDate nov(int day) {
        return NOV.atDay(day);
    }

    /** The month's dates that are neither a weekly off day nor one of the holidays. */
    private static List<LocalDate> calendar(Set<DayOfWeek> weeklyOff, Set<LocalDate> holidays) {
        List<LocalDate> days = new ArrayList<>();
        for (int d = 1; d <= NOV.lengthOfMonth(); d++) {
            LocalDate date = nov(d);
            if (!weeklyOff.contains(date.getDayOfWeek()) && !holidays.contains(date)) {
                days.add(date);
            }
        }
        return days;
    }

    private static Map<UUID, List<LocalDate>> sundaysOff() {
        Map<UUID, List<LocalDate>> map = new HashMap<>();
        map.put(null, calendar(Set.of(DayOfWeek.SUNDAY), Set.of()));
        map.put(SCHOOL_A, calendar(Set.of(DayOfWeek.SUNDAY), Set.of()));
        map.put(SCHOOL_B, calendar(Set.of(DayOfWeek.FRIDAY), Set.of())); // a School with a different weekly off
        return map;
    }

    @Test
    void aFullMonthIsEveryWorkingDay() {
        Result r = PayableDaysCalculator.compute(NOV, List.of(new Employment(SCHOOL_A, nov(1), null)), sundaysOff());
        // November 2026 has 30 days and 4 Sundays (1, 8, 15, 22, 29 = 5 Sundays)
        assertThat(r.dates()).hasSize(25).doesNotContain(nov(1), nov(8), nov(15), nov(22), nov(29));
        assertThat(r.spans()).containsExactly(new Span(SCHOOL_A, nov(1), nov(30), 25));
    }

    @Test
    void aTeacherWhoseFirstAssignmentIsThe11thIsNotPayableBefore() {
        Result r = PayableDaysCalculator.compute(NOV, List.of(new Employment(SCHOOL_A, nov(11), null)), sundaysOff());
        assertThat(r.dates()).allMatch(d -> !d.isBefore(nov(11)));
        assertThat(r.dates()).hasSize(17); // 11..30 is 20 days minus Sundays 15, 22, 29
        assertThat(r.spans()).containsExactly(new Span(SCHOOL_A, nov(11), nov(30), 17));
    }

    @Test
    void aTransferBetweenSchoolsWithDifferentWeeklyOffsCountsEachSpanOnItsOwnCalendar() {
        // School A (Sunday off) until the 14th, School B (Friday off) from the 15th: the 15th is a Sunday
        List<Employment> employment = List.of(
                new Employment(SCHOOL_A, nov(1), nov(14)), new Employment(SCHOOL_B, nov(15), null));
        Result r = PayableDaysCalculator.compute(NOV, employment, sundaysOff());

        // in B the 15th (Sunday) is a working day, and Fridays 20 and 27 are off
        assertThat(r.dates()).contains(nov(15)).doesNotContain(nov(8), nov(20), nov(27));
        assertThat(r.schoolByDate().get(nov(14))).isEqualTo(SCHOOL_A);
        assertThat(r.schoolByDate().get(nov(15))).isEqualTo(SCHOOL_B);
        // no date twice, none lost: every date is in exactly one span's count
        assertThat(r.dates()).doesNotHaveDuplicates();
        assertThat(r.spans().stream().mapToInt(Span::workingDays).sum()).isEqualTo(r.dates().size());
        assertThat(r.lastSchoolId()).isEqualTo(SCHOOL_B);
    }

    @Test
    void aSpanThatOverlapsTheNextCountsTheSharedDayOnce() {
        // the data should never overlap, but a shared day must still be counted only once
        List<Employment> employment = List.of(
                new Employment(SCHOOL_A, nov(1), nov(11)), new Employment(SCHOOL_A, nov(11), null));
        Result r = PayableDaysCalculator.compute(NOV, employment, sundaysOff());
        assertThat(r.dates()).hasSize(25).doesNotHaveDuplicates();
        assertThat(r.spans().stream().mapToInt(Span::workingDays).sum()).isEqualTo(25);
    }

    @Test
    void aTeacherWhoLeftMidMonthIsPayableOnlyUntilTheExit() {
        Result r = PayableDaysCalculator.compute(NOV, List.of(new Employment(SCHOOL_A, nov(1), nov(12))), sundaysOff());
        assertThat(r.dates()).allMatch(d -> !d.isAfter(nov(12)));
        assertThat(r.dates()).hasSize(10); // 1..12 is 12 days minus Sundays 1 and 8
    }

    @Test
    void aGapBetweenSchoolsIsNotPayable() {
        List<Employment> employment = List.of(
                new Employment(SCHOOL_A, nov(1), nov(9)), new Employment(SCHOOL_A, nov(16), null));
        Result r = PayableDaysCalculator.compute(NOV, employment, sundaysOff());
        assertThat(r.dates()).noneMatch(d -> !d.isBefore(nov(10)) && !d.isAfter(nov(15)));
    }

    @Test
    void aManagerWhoJoinedMidMonthOnTheDefaultCalendar() {
        Result r = PayableDaysCalculator.compute(NOV, List.of(new Employment(null, nov(10), null)), sundaysOff());
        assertThat(r.dates()).hasSize(18); // 10..30 is 21 days minus Sundays 15, 22, 29
        assertThat(r.spans()).containsExactly(new Span(null, nov(10), nov(30), 18));
    }

    @Test
    void aManagerWhoExitsMidMonth() {
        Result r = PayableDaysCalculator.compute(NOV, List.of(new Employment(null, null, nov(20))), sundaysOff());
        assertThat(r.dates()).allMatch(d -> !d.isAfter(nov(20)));
        assertThat(r.dates()).hasSize(17); // 1..20 is 20 days minus Sundays 1, 8, 15
    }

    @Test
    void aMonthWithAHolidayDoesNotCountIt() {
        Map<UUID, List<LocalDate>> withHoliday = new HashMap<>();
        withHoliday.put(SCHOOL_A, calendar(Set.of(DayOfWeek.SUNDAY), Set.of(nov(10))));
        Result r = PayableDaysCalculator.compute(NOV, List.of(new Employment(SCHOOL_A, null, null)), withHoliday);
        assertThat(r.dates()).hasSize(24).doesNotContain(nov(10));
    }

    @Test
    void aPersonWithNoEmploymentInTheMonthHasNoPayableDays() {
        Result r = PayableDaysCalculator.compute(NOV, List.of(), sundaysOff());
        assertThat(r.dates()).isEmpty();
        assertThat(r.spans()).isEmpty();
        assertThat(r.lastSchoolId()).isNull();
    }

    @Test
    void employmentOutsideTheMonthIsIgnored() {
        List<Employment> employment = List.of(
                new Employment(SCHOOL_A, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 31)),
                new Employment(SCHOOL_A, LocalDate.of(2026, 12, 1), null));
        Result r = PayableDaysCalculator.compute(NOV, employment, sundaysOff());
        assertThat(r.dates()).isEmpty();
        assertThat(r.spans()).isEmpty();
    }
}
