package com.hls.attendance.manager;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.attendance.api.UnpaidDay;
import com.hls.attendance.manager.internal.ManagerMonthCalculator;
import com.hls.attendance.manager.internal.ManagerMonthCalculator.DayRecord;
import com.hls.attendance.manager.internal.ManagerMonthCalculator.Figures;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Spec 032 research.md section 3 (T010): the month figures, hand-computed. November 2026 has five Sundays (1, 8, 15,
 * 22, 29), so 25 working days on a Sunday-off calendar.
 */
class ManagerMonthCalculatorTest {

    private static final YearMonth NOV = YearMonth.of(2026, 11);
    /** A date after the month, so every day of it is over. */
    private static final LocalDate AFTER = LocalDate.of(2026, 12, 5);
    /** The calculator keeps one decimal place, so a whole day is 1.0 and a half day 0.5. */
    private static final BigDecimal ONE = new BigDecimal("1.0");

    private static LocalDate nov(int day) {
        return NOV.atDay(day);
    }

    private static List<LocalDate> workingDates(int fromDay) {
        List<LocalDate> dates = new ArrayList<>();
        for (int d = fromDay; d <= NOV.lengthOfMonth(); d++) {
            if (nov(d).getDayOfWeek() != DayOfWeek.SUNDAY) {
                dates.add(nov(d));
            }
        }
        return dates;
    }

    private static DayRecord in() {
        return new DayRecord(true, false, null, false);
    }

    private static DayRecord field() {
        return new DayRecord(true, true, null, false);
    }

    private static DayRecord leave(String fraction, boolean unpaid, boolean checkedIn) {
        return new DayRecord(checkedIn, false, new BigDecimal(fraction), unpaid);
    }

    private static Figures compute(List<LocalDate> working, Map<LocalDate, DayRecord> records, LocalDate today) {
        return ManagerMonthCalculator.compute(working, records, today);
    }

    private static void assertFigures(
            Figures f, String working, String worked, String leavePaid, String leaveUnpaid, String unpaid, String fieldDays) {
        assertThat(f.workingDays()).as("working").isEqualByComparingTo(working);
        assertThat(f.daysWorked()).as("worked").isEqualByComparingTo(worked);
        assertThat(f.leavePaid()).as("leave paid").isEqualByComparingTo(leavePaid);
        assertThat(f.leaveUnpaid()).as("leave unpaid").isEqualByComparingTo(leaveUnpaid);
        assertThat(f.unpaid()).as("unpaid").isEqualByComparingTo(unpaid);
        assertThat(f.fieldDays()).as("field days").isEqualByComparingTo(fieldDays);
    }

    @Test
    void aFullMonthOfCheckInsIsWorkedWithNothingUnpaid() {
        Map<LocalDate, DayRecord> records = new HashMap<>();
        workingDates(1).forEach(d -> records.put(d, in()));
        Figures f = compute(workingDates(1), records, AFTER);
        assertFigures(f, "25", "25", "0", "0", "0", "0");
        assertThat(f.unpaidDays()).isEmpty();
        assertThat(f.pending()).isEmpty();
    }

    @Test
    void aCheckInWithNoCheckOutIsAWorkedDay() {
        // the calculator never sees a check-out: a missing one cannot change any figure (clarification 1)
        Map<LocalDate, DayRecord> records = new HashMap<>(Map.of(nov(3), in()));
        Figures f = compute(List.of(nov(3)), records, AFTER);
        assertFigures(f, "1", "1", "0", "0", "0", "0");
    }

    @Test
    void aWorkingDayWithNoRecordIsUnpaid() {
        Figures f = compute(workingDates(1), Map.of(), AFTER);
        assertFigures(f, "25", "0", "0", "0", "25", "0");
        assertThat(f.unpaidDays()).hasSize(25);
        assertThat(f.unpaidDays().get(0)).isEqualTo(new UnpaidDay(nov(2), ONE));
    }

    @Test
    void aRowWithNeitherACheckInNorLeaveIsUnpaid() {
        Figures f = compute(List.of(nov(3)), Map.of(nov(3), new DayRecord(false, false, null, false)), AFTER);
        assertFigures(f, "1", "0", "0", "0", "1", "0");
    }

    @Test
    void fullDayPaidLeaveIsLeaveAndNotUnpaid() {
        Map<LocalDate, DayRecord> records = new HashMap<>();
        records.put(nov(3), leave("1.0", false, false));
        records.put(nov(4), leave("1.0", false, false));
        Figures f = compute(List.of(nov(3), nov(4)), records, AFTER);
        assertFigures(f, "2", "0", "2", "0", "0", "0");
    }

    @Test
    void fullDayLossOfPayLeaveIsUnpaid() {
        Figures f = compute(List.of(nov(3)), Map.of(nov(3), leave("1.0", true, false)), AFTER);
        assertFigures(f, "1", "0", "0", "1", "1", "0");
        assertThat(f.unpaidDays()).containsExactly(new UnpaidDay(nov(3), ONE));
    }

    @Test
    void halfDayPaidLeaveWithACheckInIsHalfLeavePlusHalfWorked() {
        Figures f = compute(List.of(nov(3)), Map.of(nov(3), leave("0.5", false, true)), AFTER);
        assertFigures(f, "1", "0.5", "0.5", "0", "0", "0");
    }

    @Test
    void halfDayLossOfPayLeaveWithACheckInIsHalfWorkedAndHalfUnpaid() {
        Figures f = compute(List.of(nov(3)), Map.of(nov(3), leave("0.5", true, true)), AFTER);
        assertFigures(f, "1", "0.5", "0", "0.5", "0.5", "0");
        assertThat(f.unpaidDays()).containsExactly(new UnpaidDay(nov(3), new BigDecimal("0.5")));
    }

    @Test
    void halfDayPaidLeaveWithNoCheckInLeavesTheOtherHalfUnpaid() {
        Figures f = compute(List.of(nov(3)), Map.of(nov(3), leave("0.5", false, false)), AFTER);
        assertFigures(f, "1", "0", "0.5", "0", "0.5", "0");
        assertThat(f.unpaidDays()).containsExactly(new UnpaidDay(nov(3), new BigDecimal("0.5")));
    }

    @Test
    void halfDayLossOfPayLeaveWithNoCheckInIsAWholeUnpaidDayOnOneDate() {
        Figures f = compute(List.of(nov(3)), Map.of(nov(3), leave("0.5", true, false)), AFTER);
        assertFigures(f, "1", "0", "0", "0.5", "1", "0");
        assertThat(f.unpaidDays()).containsExactly(new UnpaidDay(nov(3), ONE));
    }

    @Test
    void aCheckInOnAWeeklyOffOrOutsideEmploymentChangesNothing() {
        Map<LocalDate, DayRecord> records = new HashMap<>();
        records.put(nov(8), in()); // a Sunday: not a working date
        records.put(nov(2), in()); // before the joining date: not in the working dates given
        Figures f = compute(workingDates(10), records, AFTER);
        assertFigures(f, "18", "0", "0", "0", "18", "0");
    }

    @Test
    void aMidMonthJoinerHasOnlyTheirOwnWorkingDays() {
        Figures f = compute(workingDates(10), Map.of(), AFTER);
        // 10..30 is 21 days minus Sundays 15, 22 and 29
        assertFigures(f, "18", "0", "0", "0", "18", "0");
    }

    @Test
    void todayAndLaterDatesArePendingAndNeverUnpaid() {
        LocalDate today = nov(10);
        Figures f = compute(workingDates(9), Map.of(nov(9), in()), today);
        assertThat(f.unpaid()).isEqualByComparingTo("0");
        assertThat(f.daysWorked()).isEqualByComparingTo("1");
        assertThat(f.pending().get(0)).isEqualTo(nov(10));
        assertThat(f.pending()).hasSize(workingDates(10).size());
        // yesterday with no row is unpaid, today with no row is not
        Figures g = compute(workingDates(9), Map.of(), today);
        assertThat(g.unpaidDays()).containsExactly(new UnpaidDay(nov(9), ONE));
    }

    @Test
    void aCheckInTodayCountsAsWorkedAlready() {
        LocalDate today = nov(10);
        Figures f = compute(List.of(nov(10)), Map.of(nov(10), in()), today);
        assertThat(f.daysWorked()).isEqualByComparingTo("1");
        assertThat(f.unpaid()).isEqualByComparingTo("0");
    }

    @Test
    void fieldDaysAreCountedAsWorkedFieldDays() {
        Map<LocalDate, DayRecord> records = new HashMap<>();
        records.put(nov(3), field());
        records.put(nov(4), in());
        records.put(nov(5), field());
        Figures f = compute(List.of(nov(3), nov(4), nov(5)), records, AFTER);
        assertFigures(f, "3", "3", "0", "0", "0", "2");
    }

    @Test
    void theSameInputsAlwaysGiveTheSameFigures() {
        Map<LocalDate, DayRecord> records = new HashMap<>();
        records.put(nov(3), in());
        records.put(nov(4), leave("1.0", true, false));
        Figures first = compute(workingDates(1), records, AFTER);
        for (int i = 0; i < 5; i++) {
            assertThat(compute(workingDates(1), records, AFTER)).isEqualTo(first);
        }
    }

    @Test
    void aMixedMonthAddsUpToTheHandComputedFigures() {
        // 25 working days: 15 checked in (3 of them Field), 2 paid leave, 1 Loss-of-Pay, 1 half paid + check-in,
        // 1 half LOP no check-in, and 5 with no record
        Map<LocalDate, DayRecord> records = new HashMap<>();
        List<LocalDate> days = workingDates(1);
        for (int i = 0; i < 15; i++) {
            records.put(days.get(i), i < 3 ? field() : in());
        }
        records.put(days.get(15), leave("1.0", false, false));
        records.put(days.get(16), leave("1.0", false, false));
        records.put(days.get(17), leave("1.0", true, false));
        records.put(days.get(18), leave("0.5", false, true));
        records.put(days.get(19), leave("0.5", true, false));
        Figures f = compute(days, records, AFTER);
        // worked 15 + 0.5; leave paid 2 + 0.5; leave unpaid 1 + 0.5; unpaid = 1 + 1 + 5 = 7
        assertFigures(f, "25", "15.5", "2.5", "1.5", "7", "3");
    }

    // ------------------------------------------------------------------ amended by spec 024: attended training days

    private static DayRecord training(String value) {
        return new DayRecord(false, false, null, false, new BigDecimal(value));
    }

    @Test
    void aWholeTrainingDayWithNoCheckInCountsAsWorked() {
        Figures f = compute(List.of(nov(3)), Map.of(nov(3), training("1.00")), AFTER);
        assertFigures(f, "1", "1", "0", "0", "0", "0");
    }

    @Test
    void aHalfTrainingDayCountsHalfAndTheRestIsUnpaid() {
        Figures f = compute(List.of(nov(3)), Map.of(nov(3), training("0.50")), AFTER);
        assertFigures(f, "1", "0.5", "0", "0", "0.5", "0");
    }

    @Test
    void aCheckInAndATrainingDayAreNotCountedTwice() {
        Figures f = compute(List.of(nov(3)), Map.of(nov(3), new DayRecord(true, false, null, false, new BigDecimal("1.00"))), AFTER);
        assertFigures(f, "1", "1", "0", "0", "0", "0");
    }

    @Test
    void trainingOnlyFillsTheHalfNotOnLeave() {
        Figures f = compute(List.of(nov(3)), Map.of(nov(3), new DayRecord(false, false, new BigDecimal("0.5"), false, new BigDecimal("1.00"))), AFTER);
        assertFigures(f, "1", "0.5", "0.5", "0", "0", "0");
    }

    @Test
    void trainingOnADayThatIsNotOverYetIsNeverUnpaid() {
        LocalDate today = nov(3);
        Figures f = compute(List.of(today), Map.of(today, training("0.50")), today);
        assertThat(f.unpaid()).isEqualByComparingTo("0");
        assertThat(f.daysWorked()).isEqualByComparingTo("0.5");
    }
}
