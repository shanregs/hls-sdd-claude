package com.hls.attendance.manager;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 032 US1: a missing check-out is flagged, never a deduction, and can be completed. */
class MissingCheckOutTest extends ManagerAttendanceTestBase {

    private Instant at(LocalDate date, int hour, int minute) {
        return date.atTime(hour, minute).atZone(businessCalendar.zone()).toInstant();
    }

    private String completeUri(String path, LocalDate day) {
        return path + "/days/" + day + "/complete-check-out";
    }

    @Test
    void aDayWithACheckInAndNoCheckOutIsFlaggedAfterTheDayEnds() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = joinedManager(admin);
        LocalDate yesterday = today().minusDays(1);
        seedDay(manager.managerId(), yesterday, at(yesterday, 9, 0), null);

        Resp status = get(ME + "/today", manager.token());

        assertThat(status.status()).isEqualTo(200);
        assertThat((List<Object>) status.map().get("missingCheckOutDates")).contains(yesterday.toString());
    }

    @Test
    void todayWithoutACheckOutIsNotFlaggedYet() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = joinedManager(admin);
        checkIn(manager.token(), "OFFICE");

        Resp status = get(ME + "/today", manager.token());

        assertThat((List<Object>) status.map().get("missingCheckOutDates")).isEmpty();
        assertThat((List<Object>) ((Map<?, ?>) status.map().get("day")).get("flags")).doesNotContain("NO_CHECK_OUT");
    }

    @Test
    void theManagerCompletesTheirOwnMissingCheckOutWithATimeAndAReason() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = joinedManager(admin);
        LocalDate yesterday = today().minusDays(1);
        UUID day = seedDay(manager.managerId(), yesterday, at(yesterday, 9, 0), null);

        Resp resp = post(completeUri(ME, yesterday), manager.token(), Map.of("time", "18:05", "reason", "Forgot to tap out"));

        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        assertThat(resp.map().get("checkOutAt")).isNotNull();
        assertThat(historyRows(manager.managerId(), "COMPLETE_CHECK_OUT")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select check_out_at is not null from manager_day where id = ?", Boolean.class, day))
                .isTrue();
        List<Object> missing = (List<Object>) get(ME + "/today", manager.token()).map().get("missingCheckOutDates");
        assertThat(missing).doesNotContain(yesterday.toString());
    }

    @Test
    void anAdminAndADirectorCompleteItToo() {
        String admin = signInAs(Role.ADMIN).token();
        String director = signInAs(Role.DIRECTOR).token();
        ManagerCtx manager = joinedManager(admin);
        LocalDate d1 = today().minusDays(1);
        LocalDate d2 = today().minusDays(2);
        seedDay(manager.managerId(), d1, at(d1, 9, 0), null);
        seedDay(manager.managerId(), d2, at(d2, 9, 0), null);
        String base = ATT + "/managers/" + manager.managerId();

        Resp byManager = post(completeUri(base, d1), manager.token(), Map.of("time", "17:30", "reason", "x"));
        Resp byTeacher = post(completeUri(base, d1), teacher().token(), Map.of("time", "17:30", "reason", "x"));
        Resp byAdmin = post(completeUri(base, d1), admin, Map.of("time", "17:30", "reason", "Left a note"));
        Resp byDirector = post(completeUri(base, d2), director, Map.of("time", "17:45", "reason", "Left a note"));

        assertThat(byManager.status()).isEqualTo(403);
        assertThat(byTeacher.status()).isEqualTo(403);
        assertThat(byAdmin.status()).as(byAdmin.body()).isEqualTo(200);
        assertThat(byDirector.status()).as(byDirector.body()).isEqualTo(200);
        assertThat(historyRows(manager.managerId(), "COMPLETE_CHECK_OUT")).isEqualTo(2);
    }

    @Test
    void theCompletionRefusalsNameTheProblem() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = joinedManager(admin);
        LocalDate yesterday = today().minusDays(1);
        seedDay(manager.managerId(), yesterday, at(yesterday, 9, 0), null);
        String uri = completeUri(ME, yesterday);
        String token = manager.token();

        assertThat(post(uri, token, Map.of("time", "18:05")).status()).isEqualTo(400);
        assertThat(post(uri, token, Map.of("time", "18:05", "reason", " ")).status()).isEqualTo(400);
        assertThat(post(uri, token, Map.of("time", "18:05", "reason", "x".repeat(501))).status()).isEqualTo(400);
        assertThat(post(uri, token, Map.of("time", "08:00", "reason", "before in")).status()).isEqualTo(400);
        assertThat(post(uri, token, Map.of("time", "late", "reason", "x")).status()).isEqualTo(400);
        assertThat(post(completeUri(ME, today()), token, Map.of("time", "00:00", "reason", "x")).status()).isEqualTo(400);
        assertThat(post(completeUri(ME, today().minusDays(5)), token, Map.of("time", "18:00", "reason", "x")).status())
                .isEqualTo(400);
        assertThat(historyRows(manager.managerId(), "COMPLETE_CHECK_OUT")).isZero();

        assertThat(post(uri, token, Map.of("time", "18:05", "reason", "ok")).status()).isEqualTo(200);
        assertThat(post(uri, token, Map.of("time", "18:10", "reason", "again")).status()).isEqualTo(409);
    }

    @Test
    void aLockedMonthRefusesTheCompletion() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = joinedManager(admin);
        LocalDate yesterday = today().minusDays(1);
        seedDay(manager.managerId(), yesterday, at(yesterday, 9, 0), null);
        lockMonth(manager.managerId(), YearMonth.from(yesterday));

        Resp resp = post(completeUri(ME, yesterday), manager.token(), Map.of("time", "18:05", "reason", "x"));

        assertThat(resp.status()).isEqualTo(409);
        assertThat(historyRows(manager.managerId(), "COMPLETE_CHECK_OUT")).isZero();
    }
}
