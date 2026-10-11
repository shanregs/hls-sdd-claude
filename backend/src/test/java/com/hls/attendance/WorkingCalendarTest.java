package com.hls.attendance;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.attendance.api.WorkingCalendar;
import com.hls.attendance.internal.BusinessCalendar;
import com.hls.identity.user.Role;
import com.hls.support.MasterDataTestBase;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Spec 008 amendment A6 (spec 013a T008): working days of a range for a School or the default calendar. */
class WorkingCalendarTest extends MasterDataTestBase {

    private static final String CALENDAR = "/api/v1/attendance/calendar";

    @Autowired
    private WorkingCalendar calendar;

    @Autowired
    private BusinessCalendar business;

    private static LocalDate d(String iso) {
        return LocalDate.parse(iso);
    }

    private UUID schoolWithWeekendOff(String admin) {
        UUID school = schoolInNewZone(admin)[2];
        Resp put = put(CALENDAR + "/schools/" + school, admin, Map.of("weeklyOff", List.of("SAT", "SUN")));
        assertThat(put.status()).as(put.body()).isEqualTo(200);
        return school;
    }

    @Test
    void aSchoolOverrideAndAHolidayAreLeftOut() {
        String admin = signInAs(Role.ADMIN).token();
        UUID school = schoolWithWeekendOff(admin);
        // 2031-03-03 is a Monday; the range is two whole weeks
        assertThat(calendar.workingDays(school, d("2031-03-03"), d("2031-03-16"))).hasSize(10);

        assertThat(post(CALENDAR + "/non-working-dates", admin, Map.of("onDate", "2031-03-11", "description", "Test")).status())
                .isEqualTo(201);
        try {
            List<LocalDate> days = calendar.workingDays(school, d("2031-03-03"), d("2031-03-16"));
            assertThat(days).hasSize(9).doesNotContain(d("2031-03-11"), d("2031-03-08"), d("2031-03-09"));
            assertThat(days).isSorted();
        } finally {
            delete(CALENDAR + "/non-working-dates/2031-03-11", admin);
        }
    }

    @Test
    void aRangeCrossingMonthsIsCountedAcrossTheBoundary() {
        String admin = signInAs(Role.ADMIN).token();
        UUID school = schoolWithWeekendOff(admin);
        assertThat(calendar.workingDays(school, d("2031-03-28"), d("2031-04-02")))
                .containsExactly(d("2031-03-28"), d("2031-03-31"), d("2031-04-01"), d("2031-04-02"));
    }

    @Test
    void aSchoolWithoutAnOverrideUsesTheDefaultCalendar() {
        String admin = signInAs(Role.ADMIN).token();
        UUID plain = schoolInNewZone(admin)[2];
        assertThat(calendar.workingDays(plain, d("2031-03-03"), d("2031-03-16")))
                .isEqualTo(calendar.workingDays(null, d("2031-03-03"), d("2031-03-16")));
    }

    @Test
    void fromAfterToGivesAnEmptyListNotAnError() {
        assertThat(calendar.workingDays(null, d("2031-03-16"), d("2031-03-03"))).isEmpty();
        assertThat(calendar.workingDaysBySchool(List.of(UUID.randomUUID()), d("2031-03-16"), d("2031-03-03")).values())
                .allMatch(List::isEmpty);
    }

    @Test
    void theBulkFormEqualsTheSingleFormForEachSchoolAndForTheDefaultCalendar() {
        String admin = signInAs(Role.ADMIN).token();
        UUID weekend = schoolWithWeekendOff(admin);
        UUID plain = schoolInNewZone(admin)[2];
        List<UUID> asked = new java.util.ArrayList<>(List.of(weekend, plain));
        asked.add(null);

        Map<UUID, List<LocalDate>> bulk = calendar.workingDaysBySchool(asked, d("2031-03-03"), d("2031-03-30"));
        assertThat(bulk).containsOnlyKeys(asked);
        for (UUID school : asked) {
            assertThat(bulk.get(school)).as("school " + school).isEqualTo(calendar.workingDays(school, d("2031-03-03"), d("2031-03-30")));
        }
        assertThat(bulk.get(weekend)).hasSize(20);
    }

    @Test
    void businessTodayIsTheIndianDate() {
        assertThat(calendar.businessToday()).isEqualTo(business.today());
    }
}
