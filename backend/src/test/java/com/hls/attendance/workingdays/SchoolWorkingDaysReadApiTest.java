package com.hls.attendance.workingdays;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.attendance.api.SchoolWorkingDays;
import com.hls.attendance.api.SchoolWorkingDays.MonthDays;
import com.hls.attendance.api.SchoolWorkingDays.Source;
import com.hls.attendance.api.WorkingCalendar;
import com.hls.identity.user.Role;
import com.hls.support.WorkingDaysTestBase;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Spec 037 US3: the published interface answers the stored figure or the calendar-derived count, labelled. */
class SchoolWorkingDaysReadApiTest extends WorkingDaysTestBase {

    @Autowired
    private SchoolWorkingDays workingDays;

    @Autowired
    private WorkingCalendar calendar;

    @Autowired
    private SessionFactory sessionFactory;

    /** A year far enough ahead that no other test has put a holiday in it. */
    private int year() {
        return thisYear() + 3;
    }

    private UUID school(String admin) {
        return schoolInNewZone(admin)[2];
    }

    private int calendarCount(UUID school, YearMonth month) {
        return calendar.workingDays(school, month.atDay(1), month.atEndOfMonth()).size();
    }

    @Test
    void aStoredFigureWinsOverTheCalendarAndIsLabelledStored() {
        String admin = signInAs(Role.ADMIN).token();
        UUID school = school(admin);
        createRowOk(admin, school, year(), 3, 26.5);

        MonthDays answer = workingDays.forMonth(school, YearMonth.of(year(), 3));
        assertThat(answer.workingDays()).isEqualByComparingTo("26.5");
        assertThat(answer.source()).isEqualTo(Source.STORED);
        assertThat(answer.schoolId()).isEqualTo(school);
        assertThat(answer.month()).isEqualTo(YearMonth.of(year(), 3));
        assertThat(calendarCount(school, YearMonth.of(year(), 3))).isNotEqualTo(26);
    }

    @Test
    void withoutARowTheCalendarCountIsReturnedLabelledCalendarDerived() {
        String admin = signInAs(Role.ADMIN).token();
        UUID school = school(admin);
        YearMonth month = YearMonth.of(year(), 4);

        MonthDays answer = workingDays.forMonth(school, month);
        assertThat(answer.source()).isEqualTo(Source.CALENDAR_DERIVED);
        assertThat(answer.workingDays()).isEqualByComparingTo(BigDecimal.valueOf(calendarCount(school, month)));
    }

    @Test
    void aSchoolWithAWeeklyOffOverrideGetsItsOwnCalendarCount() {
        String admin = signInAs(Role.ADMIN).token();
        UUID school = school(admin);
        YearMonth month = YearMonth.of(year(), 5);
        assertThat(put("/api/v1/attendance/calendar/schools/" + school, admin, Map.of("weeklyOff", List.of())).status())
                .isEqualTo(200);
        try {
            MonthDays everyDay = workingDays.forMonth(school, month);
            assertThat(everyDay.source()).isEqualTo(Source.CALENDAR_DERIVED);
            assertThat(everyDay.workingDays()).isEqualByComparingTo(BigDecimal.valueOf(calendarCount(school, month)));
            assertThat(everyDay.workingDays()).isGreaterThanOrEqualTo(BigDecimal.valueOf(month.lengthOfMonth() - 2));

            assertThat(put("/api/v1/attendance/calendar/schools/" + school, admin, Map.of("weeklyOff", List.of("SUN", "SAT")))
                            .status())
                    .isEqualTo(200);
            MonthDays weekdaysOnly = workingDays.forMonth(school, month);
            assertThat(weekdaysOnly.workingDays()).isLessThan(everyDay.workingDays());
        } finally {
            delete("/api/v1/attendance/calendar/schools/" + school, admin);
        }
    }

    @Test
    void aStoredZeroIsReturnedAsZeroStored() {
        String admin = signInAs(Role.ADMIN).token();
        UUID school = school(admin);
        createRowOk(admin, school, year(), 6, 0);

        MonthDays answer = workingDays.forMonth(school, YearMonth.of(year(), 6));
        assertThat(answer.workingDays()).isEqualByComparingTo("0");
        assertThat(answer.source()).isEqualTo(Source.STORED);
    }

    @Test
    void aYearHasTwelveEntriesJanuaryFirstMixingStoredAndDerived() {
        String admin = signInAs(Role.ADMIN).token();
        UUID school = school(admin);
        createRowOk(admin, school, year(), 3, 26);
        createRowOk(admin, school, year(), 11, 22.5);

        List<MonthDays> months = workingDays.forYear(List.of(school), year()).get(school);
        assertThat(months).hasSize(12);
        assertThat(months.get(0).month()).isEqualTo(YearMonth.of(year(), 1));
        assertThat(months.get(11).month()).isEqualTo(YearMonth.of(year(), 12));
        assertThat(months.get(2).source()).isEqualTo(Source.STORED);
        assertThat(months.get(10).source()).isEqualTo(Source.STORED);
        assertThat(months.get(10).workingDays()).isEqualByComparingTo("22.5");
        assertThat(months.get(0).source()).isEqualTo(Source.CALENDAR_DERIVED);
        assertThat(months.stream().filter(m -> m.source() == Source.STORED)).hasSize(2);
    }

    @Test
    void aRangeMayCrossAYearBoundaryOldestFirst() {
        String admin = signInAs(Role.ADMIN).token();
        UUID school = school(admin);
        createRowOk(admin, school, year(), 12, 20);
        createRowOk(admin, school, year() + 1, 1, 19);

        List<MonthDays> months =
                workingDays.forRange(List.of(school), YearMonth.of(year(), 12), YearMonth.of(year() + 1, 2)).get(school);
        assertThat(months).extracting(MonthDays::month)
                .containsExactly(YearMonth.of(year(), 12), YearMonth.of(year() + 1, 1), YearMonth.of(year() + 1, 2));
        assertThat(months).extracting(MonthDays::source)
                .containsExactly(Source.STORED, Source.STORED, Source.CALENDAR_DERIVED);
        assertThat(workingDays.forRange(List.of(school), YearMonth.of(year(), 2), YearMonth.of(year(), 1))).isEmpty();
    }

    @Test
    void anUnknownSchoolGetsTheDefaultCalendarCount() {
        UUID unknown = UUID.randomUUID();
        YearMonth month = YearMonth.of(year(), 7);
        MonthDays answer = workingDays.forMonth(unknown, month);
        assertThat(answer.source()).isEqualTo(Source.CALENDAR_DERIVED);
        assertThat(answer.workingDays()).isEqualByComparingTo(BigDecimal.valueOf(calendarCount(null, month)));
    }

    @Test
    void aChangedOrDeletedRowIsReflectedOnTheNextCall() {
        String admin = signInAs(Role.ADMIN).token();
        UUID school = school(admin);
        YearMonth month = YearMonth.of(year(), 8);
        Resp created = createRow(admin, school, year(), 8, 20);
        assertThat(workingDays.forMonth(school, month).workingDays()).isEqualByComparingTo("20");

        Resp edited = editRow(admin, created.id(), 21, versionOf(created));
        assertThat(workingDays.forMonth(school, month).workingDays()).isEqualByComparingTo("21");

        assertThat(deleteRow(admin, created.id(), versionOf(edited)).status()).isEqualTo(204);
        MonthDays back = workingDays.forMonth(school, month);
        assertThat(back.source()).isEqualTo(Source.CALENDAR_DERIVED);
        assertThat(back.workingDays()).isEqualByComparingTo(BigDecimal.valueOf(calendarCount(school, month)));
    }

    @Test
    void theNumberOfStatementsDoesNotGrowWithTheNumberOfSchools() {
        String admin = signInAs(Role.ADMIN).token();
        List<UUID> schools = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            UUID school = school(admin);
            schools.add(school);
            if (i % 2 == 0) {
                createRowOk(admin, school, year(), 3, 20);
            }
        }
        workingDays.forYear(schools.subList(0, 1), year()); // warm the calendar rules cache
        var stats = sessionFactory.getStatistics();
        stats.setStatisticsEnabled(true);
        stats.clear();
        workingDays.forYear(schools.subList(0, 1), year());
        long one = stats.getPrepareStatementCount();
        stats.clear();
        Map<UUID, List<MonthDays>> all = workingDays.forYear(schools, year());
        long many = stats.getPrepareStatementCount();
        stats.setStatisticsEnabled(false);

        assertThat(all).hasSize(50);
        assertThat(many).isEqualTo(one);
    }

    @Test
    void theAnswerNeedsNoSignedInUserAndCarriesNoPersonData() {
        // called here with no security context at all: a module-to-module read (FR-013)
        UUID school = UUID.randomUUID();
        MonthDays answer = workingDays.forMonth(school, YearMonth.of(year(), 9));
        assertThat(answer.getClass().getRecordComponents())
                .extracting(c -> c.getName())
                .containsExactly("schoolId", "month", "workingDays", "source");
    }
}
