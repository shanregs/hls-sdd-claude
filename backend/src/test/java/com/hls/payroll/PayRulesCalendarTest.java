package com.hls.payroll;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.payroll.api.LossOfPayValue;
import com.hls.payroll.api.PayRules;
import com.hls.payroll.api.PayRules.Kind;
import com.hls.payroll.api.PayRules.PersonRef;
import com.hls.payroll.api.PayableDays;
import com.hls.payroll.api.TotalLossOfPay;
import com.hls.support.PayrollTestBase;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Spec 013a SC-003 (T031): the calendar-dependent fixtures of quickstart section 4 — Schools with different weekly offs,
 * an exit in the middle of a month, a holiday, and a month with no working days — through the public interface.
 */
class PayRulesCalendarTest extends PayrollTestBase {

    private static final String CALENDAR = "/api/v1/attendance/calendar";

    @Autowired
    private PayRules payRules;

    private YearMonth month;

    @BeforeEach
    void previousBusinessMonth() {
        month = YearMonth.from(today()).minusMonths(1);
    }

    private UUID schoolWithWeeklyOff(String admin, List<String> days) {
        UUID[] ids = schoolInNewZone(admin);
        Resp set = put(CALENDAR + "/schools/" + ids[2], admin, Map.of("weeklyOff", days));
        assertThat(set.status()).as(set.body()).isEqualTo(200);
        return ids[2];
    }

    private UUID teacherAt(String admin, UUID school, LocalDate from) {
        UUID teacher = teacher(admin);
        Resp placed = placeTeacherRaw(admin, teacher, school, from);
        assertThat(placed.status()).as(placed.body()).isEqualTo(200);
        return teacher;
    }

    private void salary(String admin, UUID teacher, String amount) {
        Resp resp = post(
                "/api/v1/teachers/" + teacher + "/salary",
                admin,
                Map.of("amount", new BigDecimal(amount), "effectiveOn", LocalDate.now().minusDays(400).toString()));
        assertThat(resp.status()).as(resp.body()).isEqualTo(201);
    }

    private BigDecimal perDay(String salary, int workingDays) {
        return new BigDecimal(salary).divide(BigDecimal.valueOf(workingDays), 10, RoundingMode.HALF_EVEN);
    }

    @Test
    void aTransferBetweenSchoolsWithDifferentWeeklyOffsCountsEachSpanOnItsOwnCalendar() {
        String admin = signInAs(Role.ADMIN).token();
        UUID sundaysOff = schoolWithWeeklyOff(admin, List.of("SUN"));
        UUID fridaysOff = schoolWithWeeklyOff(admin, List.of("FRI"));
        UUID teacher = teacherAt(admin, sundaysOff, LocalDate.now().minusDays(200));
        assertThat(placeTeacherRaw(admin, teacher, fridaysOff, month.atDay(16)).status()).isEqualTo(200);

        PayableDays payable = payRules.payableDays(new PersonRef(Kind.TEACHER, teacher), month);

        assertThat(payable.dates()).doesNotHaveDuplicates();
        assertThat(payable.dates())
                .filteredOn(d -> d.getDayOfMonth() <= 15)
                .noneMatch(d -> d.getDayOfWeek() == DayOfWeek.SUNDAY);
        assertThat(payable.dates())
                .filteredOn(d -> d.getDayOfMonth() >= 16)
                .noneMatch(d -> d.getDayOfWeek() == DayOfWeek.FRIDAY);
        // a Friday in the first span and a Sunday in the second are working days there (one may be a holiday)
        assertThat(payable.dates())
                .anyMatch(d -> d.getDayOfMonth() <= 15 && d.getDayOfWeek() == DayOfWeek.FRIDAY);
        assertThat(payable.dates())
                .anyMatch(d -> d.getDayOfMonth() >= 16 && d.getDayOfWeek() == DayOfWeek.SUNDAY);
        List<LocalDate> expected = new java.util.ArrayList<>(workingCalendar.workingDays(sundaysOff, month.atDay(1), month.atDay(15)));
        expected.addAll(workingCalendar.workingDays(fridaysOff, month.atDay(16), month.atEndOfMonth()));
        assertThat(payable.dates()).containsExactlyElementsOf(expected);
        assertThat(payable.spans()).hasSize(2);
        assertThat(payable.spans().stream().mapToInt(com.hls.payroll.api.Span::workingDays).sum())
                .isEqualTo(payable.dates().size());
    }

    @Test
    void eachUnpaidDayIsValuedOverTheWorkingDaysOfItsOwnSchoolsMonth() {
        String admin = signInAs(Role.ADMIN).token();
        UUID schoolA = schoolWithWeeklyOff(admin, List.of("SUN"));
        UUID schoolB = schoolWithWeeklyOff(admin, List.of("SAT", "SUN"));
        UUID teacher = teacherAt(admin, schoolA, LocalDate.now().minusDays(200));
        salary(admin, teacher, "30000");
        assertThat(placeTeacherRaw(admin, teacher, schoolB, month.atDay(16)).status()).isEqualTo(200);

        LocalDate inA = workingCalendar.workingDays(schoolA, month.atDay(1), month.atDay(15)).get(2);
        LocalDate inB = workingCalendar.workingDays(schoolB, month.atDay(16), month.atEndOfMonth()).get(2);
        for (LocalDate day : List.of(inA, inB)) {
            Resp marked = put(
                    "/api/v1/attendance/teachers/" + teacher + "/marks/" + day,
                    admin,
                    Map.of("statusCode", "A", "dayValue", 1));
            assertThat(marked.status()).as(marked.body()).isEqualTo(200);
        }

        int workingA = workingCalendar.workingDays(schoolA, month.atDay(1), month.atEndOfMonth()).size();
        int workingB = workingCalendar.workingDays(schoolB, month.atDay(1), month.atEndOfMonth()).size();
        TotalLossOfPay.Found total =
                (TotalLossOfPay.Found) payRules.totalLossOfPay(new PersonRef(Kind.TEACHER, teacher), month);

        BigDecimal expected = perDay("30000", workingA).add(perDay("30000", workingB));
        assertThat(total.unrounded()).isEqualByComparingTo(expected);
        assertThat(total.days()).extracting(d -> d.workingDays()).containsExactly(workingA, workingB);
    }

    @Test
    void aTeacherWhoExitedMidMonthIsPayableOnlyUntilTheExit() {
        String admin = signInAs(Role.ADMIN).token();
        UUID school = schoolWithWeeklyOff(admin, List.of("SUN"));
        UUID teacher = teacherAt(admin, school, LocalDate.now().minusDays(200));
        LocalDate exit = month.atDay(20);
        Resp exited = post("/api/v1/teachers/" + teacher + "/status", admin,
                Map.of("status", "EXITED", "effectiveOn", exit.toString()));
        assertThat(exited.status()).as(exited.body()).isEqualTo(200);

        PayableDays payable = payRules.payableDays(new PersonRef(Kind.TEACHER, teacher), month);
        assertThat(payable.dates()).isNotEmpty().allMatch(d -> !d.isAfter(exit));
        assertThat(payable.dates()).contains(workingCalendar.workingDays(school, month.atDay(1), exit).getLast());
    }

    @Test
    void aHolidayIsNotCountedAsAPayableDay() {
        String admin = signInAs(Role.ADMIN).token();
        UUID school = schoolWithWeeklyOff(admin, List.of("SUN"));
        UUID teacher = teacherAt(admin, school, LocalDate.now().minusDays(200));
        PersonRef person = new PersonRef(Kind.TEACHER, teacher);
        int before = payRules.payableDays(person, month).dates().size();
        LocalDate holiday = workingCalendar.workingDays(school, month.atDay(1), month.atEndOfMonth()).get(4);

        Resp added = post(CALENDAR + "/non-working-dates", admin, Map.of("onDate", holiday.toString(), "description", "Test holiday"));
        assertThat(added.status()).as(added.body()).isEqualTo(201);
        try {
            PayableDays payable = payRules.payableDays(person, month);
            assertThat(payable.dates()).doesNotContain(holiday).hasSize(before - 1);
            assertThat(payable.workingDaysInMonth()).isEqualTo(before - 1);
        } finally {
            delete(CALENDAR + "/non-working-dates/" + holiday, admin);
        }
    }

    @Test
    void aMonthWithNoWorkingDaysAnswersNoWorkingDaysAndDoesNotDivide() {
        String admin = signInAs(Role.ADMIN).token();
        UUID school = schoolWithWeeklyOff(admin, List.of("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"));
        UUID teacher = teacherAt(admin, school, LocalDate.now().minusDays(200));
        salary(admin, teacher, "26000");
        PersonRef person = new PersonRef(Kind.TEACHER, teacher);

        LossOfPayValue value = payRules.lossOfPayValueOn(person, month.atDay(15));
        assertThat(value).isEqualTo(new LossOfPayValue.NoWorkingDays(month));
        PayableDays payable = payRules.payableDays(person, month);
        assertThat(payable.dates()).isEmpty();
        assertThat(payable.workingDaysInMonth()).isZero();
    }
}
