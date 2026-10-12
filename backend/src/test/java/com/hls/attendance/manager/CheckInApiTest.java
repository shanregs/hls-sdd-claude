package com.hls.attendance.manager;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/** Spec 032 US1: check-in and check-out with a location, the server time, and the refusals. */
class CheckInApiTest extends ManagerAttendanceTestBase {

    @Test
    void checkInRecordsTheServerTimeLocationAndDayType() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = joinedManager(admin);

        Resp resp = checkIn(manager.token(), "OFFICE");

        assertThat(resp.status()).as(resp.body()).isEqualTo(201);
        assertThat(resp.map()).containsEntry("dayType", "OFFICE").containsEntry("date", today().toString());
        assertThat(resp.map().get("checkInAt")).isNotNull();
        assertThat(resp.map().get("checkOutAt")).isNull();
        assertThat(resp.body()).doesNotContain("latitude");
        Map<String, Object> row = jdbc.queryForMap(
                "select day_type, check_in_lat, check_in_lng, check_in_accuracy_m from manager_day where manager_id = ?",
                manager.managerId());
        assertThat(row.get("day_type")).isEqualTo("OFFICE");
        assertThat(((Number) row.get("check_in_lat")).doubleValue()).isEqualTo(12.971599);
        assertThat(((Number) row.get("check_in_lng")).doubleValue()).isEqualTo(77.594566);
        assertThat(((Number) row.get("check_in_accuracy_m")).doubleValue()).isEqualTo(18.5);
    }

    @Test
    void aSecondCheckInIsRefusedWithAConflict() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = joinedManager(admin);
        checkIn(manager.token(), "OFFICE");

        Resp second = checkIn(manager.token(), "FIELD");

        assertThat(second.status()).isEqualTo(409);
        assertThat(dayRows(manager.managerId())).isEqualTo(1);
    }

    @Test
    void aLocationIsRequiredAndMustBeInRange() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = joinedManager(admin);

        Map<String, Object> noLocation = new HashMap<>();
        noLocation.put("dayType", "OFFICE");
        Resp none = post(ME + "/check-in", manager.token(), noLocation);
        Map<String, Object> badLat = here("OFFICE");
        badLat.put("latitude", 91);
        Map<String, Object> badLng = here("OFFICE");
        badLng.put("longitude", 181);
        Map<String, Object> badLatNegative = here("OFFICE");
        badLatNegative.put("latitude", -91);

        assertThat(none.status()).isEqualTo(400);
        assertThat(none.body()).contains("location");
        assertThat(post(ME + "/check-in", manager.token(), badLat).status()).isEqualTo(400);
        assertThat(post(ME + "/check-in", manager.token(), badLng).body()).contains("longitude");
        assertThat(post(ME + "/check-in", manager.token(), badLatNegative).status()).isEqualTo(400);
        assertThat(dayRows(manager.managerId())).isZero();
    }

    @Test
    void aDayTypeOtherThanOfficeOrFieldIsRefused() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = joinedManager(admin);

        assertThat(checkIn(manager.token(), "LEAVE").status()).isEqualTo(400);
        assertThat(checkIn(manager.token(), "HOLIDAY").body()).contains("OFFICE or FIELD");
        Map<String, Object> missing = here("OFFICE");
        missing.remove("dayType");
        assertThat(post(ME + "/check-in", manager.token(), missing).status()).isEqualTo(400);
    }

    @Test
    void checkOutNeedsACheckInAndOnlyHappensOnce() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = joinedManager(admin);

        Resp early = checkOut(manager.token());
        assertThat(early.status()).isEqualTo(400);

        checkIn(manager.token(), "OFFICE");
        Map<String, Object> noLocation = new HashMap<>();
        assertThat(post(ME + "/check-out", manager.token(), noLocation).status()).isEqualTo(400);

        Resp out = checkOut(manager.token());
        assertThat(out.status()).as(out.body()).isEqualTo(200);
        assertThat(out.map().get("checkOutAt")).isNotNull();
        Map<String, Object> times = jdbc.queryForMap(
                "select check_in_at, check_out_at from manager_day where manager_id = ?", manager.managerId());
        assertThat(((java.sql.Timestamp) times.get("check_out_at")).toInstant())
                .isAfterOrEqualTo(((java.sql.Timestamp) times.get("check_in_at")).toInstant());

        assertThat(checkOut(manager.token()).status()).isEqualTo(409);
    }

    @Test
    void aCheckInAfterTheLateTimeIsMarkedLateAndOneBeforeItIsNot() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx late = joinedManager(admin);
        ManagerCtx onTime = joinedManager(admin);

        jdbc.update("update manager_attendance_setting set missed_cutoff = time '23:59', late_after = time '00:00' where id = 1");
        Resp lateResp = checkIn(late.token(), "OFFICE");
        jdbc.update("update manager_attendance_setting set missed_cutoff = time '23:59:59', late_after = time '23:59:58' where id = 1");
        Resp onTimeResp = checkIn(onTime.token(), "OFFICE");

        assertThat(lateResp.map()).containsEntry("late", true);
        assertThat((List<Object>) lateResp.map().get("flags")).contains("LATE");
        assertThat(onTimeResp.map()).containsEntry("late", false);
    }

    @Test
    void aCheckInOnAHolidayIsAcceptedAndRecorded() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = joinedManager(admin);
        int inserted = jdbc.update(
                "insert into attendance_non_working_date (id, on_date, description, created_by) values (?, ?, 'Test holiday', ?)"
                        + " on conflict (on_date) do nothing",
                UUID.randomUUID(),
                today(),
                UUID.randomUUID());
        try {
            Resp resp = checkIn(manager.token(), "OFFICE");

            assertThat(resp.status()).as(resp.body()).isEqualTo(201);
            Resp status = get(ME + "/today", manager.token());
            assertThat(status.map()).containsEntry("workingDay", false);
        } finally {
            if (inserted > 0) {
                jdbc.update("delete from attendance_non_working_date where on_date = ? and description = 'Test holiday'", today());
            }
        }
    }

    @Test
    void aManagerWithNoJoiningDateIsRefusedWithTheReason() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = newManager(admin);

        Resp resp = checkIn(manager.token(), "OFFICE");

        assertThat(resp.status()).isEqualTo(400);
        assertThat(resp.body()).contains("joining date");
        assertThat(dayRows(manager.managerId())).isZero();
    }

    @Test
    void aDayBeforeTheJoiningDateAndAfterTheExitDateAreRefused() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx notYet = newManager(admin);
        setJoining(notYet.managerId(), today().plusDays(1), null);
        ManagerCtx gone = newManager(admin);
        setJoining(gone.managerId(), today().minusDays(60), today().minusDays(1));

        Resp before = checkIn(notYet.token(), "OFFICE");
        Resp after = checkIn(gone.token(), "OFFICE");

        assertThat(before.status()).isEqualTo(400);
        assertThat(before.body()).contains("before your joining date");
        assertThat(after.status()).isEqualTo(400);
        assertThat(after.body()).contains("after your exit date");
    }

    @Test
    void fullDayLeaveRefusesACheckInAndHalfDayLeaveAcceptsOne() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx full = joinedManager(admin);
        ManagerCtx half = joinedManager(admin);
        seedLeave(full.managerId(), today(), 1.0);
        seedLeave(half.managerId(), today(), 0.5);

        Resp refused = checkIn(full.token(), "OFFICE");
        Resp accepted = checkIn(half.token(), "OFFICE");

        assertThat(refused.status()).isEqualTo(400);
        assertThat(refused.body()).contains("leave");
        assertThat(accepted.status()).as(accepted.body()).isEqualTo(201);
        assertThat(accepted.map().get("leave")).isNotNull();
        assertThat(dayRows(half.managerId())).isEqualTo(1);
    }

    @Test
    void aLockedMonthRefusesCheckInAndCheckOut() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = joinedManager(admin);
        lockMonth(manager.managerId(), YearMonth.from(today()));

        Resp resp = checkIn(manager.token(), "OFFICE");

        assertThat(resp.status()).isEqualTo(409);
        assertThat(resp.body()).contains(YearMonth.from(today()).toString()).contains("locked");
        assertThat(checkOut(manager.token()).status()).isIn(400, 409);
        assertThat(dayRows(manager.managerId())).isZero();
    }

    @Test
    void twoCheckInsAtTheSameInstantRecordExactlyOne() throws Exception {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = joinedManager(admin);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        java.util.concurrent.CountDownLatch go = new java.util.concurrent.CountDownLatch(1);
        try {
            Callable<Resp> call = () -> {
                go.await();
                return checkIn(manager.token(), "OFFICE");
            };
            Future<Resp> a = pool.submit(call);
            Future<Resp> b = pool.submit(call);
            go.countDown();
            List<Integer> statuses = List.of(a.get().status(), b.get().status());

            assertThat(statuses).containsExactlyInAnyOrder(201, 409);
            assertThat(dayRows(manager.managerId())).isEqualTo(1);
            assertThat(historyRows(manager.managerId(), "CHECK_IN")).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void todayReportsTheStatusTheTimesAndTheRecordSoFar() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = joinedManager(admin);

        Resp before = get(ME + "/today", manager.token());
        checkIn(manager.token(), "OFFICE");
        Resp after = get(ME + "/today", manager.token());

        assertThat(before.status()).isEqualTo(200);
        assertThat(before.map().get("day")).isNull();
        assertThat(before.map()).containsEntry("missedCutoff", "10:30:00").containsEntry("lateAfter", "09:30:00");
        assertThat(after.map().get("day")).isNotNull();
    }
}
