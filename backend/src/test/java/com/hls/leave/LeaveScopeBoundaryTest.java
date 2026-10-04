package com.hls.leave;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Spec 009 US3/US5 and Constitution Principle III: a Manager sees and decides only their own Teachers. */
class LeaveScopeBoundaryTest extends LeaveTestBase {

    @Test
    void eachManagerListsOnlyTheirOwnTeachersRequestsAndAdminAndDirectorSeeAll() {
        World w = newWorld();
        LocalDate mon = monday();
        String a = apply(w.teacherA(), mon, mon.plusDays(1));
        String b = apply(w.teacherB(), mon, mon.plusDays(1));

        String asA = get(SUP + "?size=100", w.managerA().token()).body();
        assertThat(asA).contains(a).doesNotContain(b);
        String asB = get(SUP + "?size=100", w.managerB().token()).body();
        assertThat(asB).contains(b).doesNotContain(a);
        for (String token : new String[] {w.admin(), w.director()}) {
            String all = get(SUP + "?size=100", token).body();
            assertThat(all).contains(a).contains(b);
        }
    }

    @Test
    void anOutOfScopeRequestIsNotFoundForDetailApproveRejectAndRevoke() {
        World w = newWorld();
        LocalDate mon = monday();
        String b = apply(w.teacherB(), mon, mon.plusDays(1));
        String manager = w.managerA().token();

        assertThat(get(SUP + "/" + b, manager).status()).isEqualTo(404);
        assertThat(post(SUP + "/" + b + "/approve", manager, Map.of("version", 0)).status()).isEqualTo(404);
        assertThat(post(SUP + "/" + b + "/reject", manager, Map.of("reason", "no", "version", 0)).status())
                .isEqualTo(404);
        assertThat(post(SUP + "/" + b + "/revoke", manager, Map.of("reason", "no", "version", 0)).status())
                .isEqualTo(404);
        assertThat(jdbc.queryForObject("select status from leave_request where id = ?::uuid", String.class, b))
                .isEqualTo("PENDING");
    }

    @Test
    void theTeacherAndSchoolFiltersNarrowTheListWithinScope() {
        World w = newWorld();
        LocalDate mon = monday();
        String a = apply(w.teacherA(), mon, mon.plusDays(1));
        String b = apply(w.teacherB(), mon, mon.plusDays(1));

        String byTeacher = get(SUP + "?teacherId=" + w.teacherB().teacherId(), w.admin()).body();
        assertThat(byTeacher).contains(b).doesNotContain(a);
        String bySchool = get(SUP + "?schoolId=" + w.schoolA(), w.admin()).body();
        assertThat(bySchool).contains(a).doesNotContain(b);
        // A filter cannot reach outside the Manager's scope.
        String outside = get(SUP + "?teacherId=" + w.teacherB().teacherId(), w.managerA().token()).body();
        assertThat(outside).contains("\"totalElements\":0");
    }

    @Test
    void thePendingCountCoversOnlyTheCallersScope() {
        World w = newWorld();
        LocalDate mon = monday();
        apply(w.teacherA(), mon, mon.plusDays(1));
        apply(w.teacherA(), mon.plusDays(7), mon.plusDays(8));
        apply(w.teacherB(), mon, mon.plusDays(1));

        assertThat(field(get(SUP, w.managerA().token()).body(), "pendingCount")).isEqualTo("2");
        assertThat(field(get(SUP, w.managerB().token()).body(), "pendingCount")).isEqualTo("1");
    }

    @Test
    void theMonthFilterKeepsRequestsThatTouchThatMonth() {
        World w = newWorld();
        LocalDate mon = monday();
        String id = apply(w.teacherA(), mon, mon.plusDays(1));
        String month = java.time.YearMonth.from(mon).toString();
        String other = java.time.YearMonth.from(mon).minusMonths(3).toString();

        assertThat(get(SUP + "?month=" + month, w.admin()).body()).contains(id);
        assertThat(get(SUP + "?month=" + other, w.admin()).body()).doesNotContain(id);
        assertThat(get(SUP + "?month=2026-13", w.admin()).status()).isEqualTo(400);
    }
}
