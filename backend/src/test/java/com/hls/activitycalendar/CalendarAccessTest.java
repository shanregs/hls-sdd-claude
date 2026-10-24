package com.hls.activitycalendar;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import org.junit.jupiter.api.Test;

/** Spec 035 T029: Admin and Director see the calendar, Manager own scope, Teacher and System are refused, no token is 401. */
class CalendarAccessTest extends ActivityCalendarTestBase {

    @Test
    void adminAndDirectorGetTheOrganizationWideCalendar() {
        for (Role role : new Role[] {Role.ADMIN, Role.DIRECTOR}) {
            Resp resp = calendar(signInAs(role).token(), "2019-02");
            assertThat(resp.status()).as(role + " " + resp.body()).isEqualTo(200);
            assertThat(resp.map()).containsKeys("month", "today", "partial", "sources", "summary", "items");
            assertThat(summary(resp)).containsKey("byManager");
        }
    }

    @Test
    void aManagerGetsTheCalendarWithoutThePerManagerTable() {
        Resp resp = calendar(newManager(admin(), zone(admin())).token(), "2019-02");
        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        assertThat(summary(resp)).doesNotContainKey("byManager");
    }

    @Test
    void aTeacherAndSystemAreRefusedBeforeAnyLookup() {
        for (Role role : new Role[] {Role.TEACHER, Role.SYSTEM}) {
            // a bad month would be a 400 if any lookup happened first
            assertThat(calendar(signInAs(role).token(), "not-a-month").status()).as(role.name()).isEqualTo(403);
            assertThat(calendar(signInAs(role).token(), "2019-02").status()).as(role.name()).isEqualTo(403);
        }
    }

    @Test
    void noTokenIsUnauthorized() {
        assertThat(get(CAL + "?month=2019-02", null).status()).isEqualTo(401);
    }

    @Test
    void aBadMonthAnswers400() {
        assertThat(calendar(admin(), "2019-13").status()).isEqualTo(400);
        assertThat(calendar(admin(), "October").status()).isEqualTo(400);
    }
}
