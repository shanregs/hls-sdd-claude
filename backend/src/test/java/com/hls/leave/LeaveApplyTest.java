package com.hls.leave;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 009 US1: preview and submit, every refusal, and the per-role authorization of the Teacher endpoints. */
class LeaveApplyTest extends LeaveTestBase {

    @Test
    void previewCountsOnlyWorkingDaysAndSubmitSavesAPendingRequest() {
        World w = newWorld();
        LocalDate mon = monday();
        Map<String, Object> body = draft(mon, mon.plusDays(6));
        body.put("halfDayEnd", true);

        Resp preview = post(ME + "/preview", w.teacherA().token(), body);
        assertThat(preview.status()).as(preview.body()).isEqualTo(200);
        assertThat(new java.math.BigDecimal(field(preview.body(), "workingDays"))).isEqualByComparingTo("5.5");
        assertThat(preview.body().split("\"date\"", -1).length - 1).isEqualTo(6);
        assertThat(preview.body()).contains("\"problems\":[]");

        Resp submitted = post(ME, w.teacherA().token(), body);
        assertThat(submitted.status()).as(submitted.body()).isEqualTo(201);
        assertThat(field(submitted.body(), "status")).isEqualTo("PENDING");
        assertThat(new java.math.BigDecimal(field(submitted.body(), "workingDays"))).isEqualByComparingTo("5.5");
        assertThat(submitted.body()).contains("\"allowedActions\":[\"CANCEL\"]");
        assertThat(field(submitted.body(), "schoolName")).isNotBlank();
    }

    @Test
    void aRangeWithNoWorkingDayIsRefused() {
        World w = newWorld();
        LocalDate sunday = monday().plusDays(6);

        Resp resp = post(ME, w.teacherA().token(), draft(sunday, sunday));

        assertThat(resp.status()).isEqualTo(409);
        assertThat(resp.body()).contains("working day");
    }

    @Test
    void anOverlappingRequestIsRefusedNamingTheClashingOne() {
        World w = newWorld();
        LocalDate mon = monday();
        assertThat(post(ME, w.teacherA().token(), draft(mon, mon.plusDays(2))).status()).isEqualTo(201);

        Resp clash = post(ME, w.teacherA().token(), draft(mon.plusDays(2), mon.plusDays(3)));

        assertThat(clash.status()).isEqualTo(409);
        assertThat(clash.body()).contains("overlap").contains(mon.toString());
    }

    @Test
    void cancelledAndRejectedRequestsDoNotBlockANewOne() {
        World w = newWorld();
        LocalDate mon = monday();
        String id = apply(w.teacherA(), mon, mon.plusDays(1));
        assertThat(post(ME + "/" + id + "/cancel", w.teacherA().token(), Map.of()).status()).isEqualTo(200);

        assertThat(post(ME, w.teacherA().token(), draft(mon, mon.plusDays(1))).status()).isEqualTo(201);
    }

    @Test
    void aStartMoreThanThirtyDaysAgoIsRefused() {
        World w = newWorld();
        LocalDate old = today().minusDays(31);

        Resp resp = post(ME, w.teacherA().token(), draft(old, old.plusDays(1)));

        assertThat(resp.status()).isEqualTo(409);
        assertThat(resp.body()).contains("30 days");
    }

    @Test
    void aLockedMonthIsRefused() {
        World w = newWorld();
        LocalDate mon = monday();
        jdbc.update(
                "insert into attendance_teacher_month (id, teacher_id, year_month, state, working_days, days_worked,"
                        + " days_leave, training_available, training_attended, unmarked, weighted_total, changed_at, version)"
                        + " values (?, ?, ?, 'LOCKED', 20, 7, 1, 0, 0, 0, 7, now(), 0)",
                UUID.randomUUID(),
                w.teacherA().teacherId(),
                java.time.YearMonth.from(mon).toString());

        Resp resp = post(ME, w.teacherA().token(), draft(mon, mon.plusDays(1)));

        assertThat(resp.status()).isEqualTo(409);
        assertThat(resp.body()).contains("locked");
    }

    @Test
    void invalidInputsAreRefusedWith400() {
        World w = newWorld();
        LocalDate mon = monday();

        assertThat(post(ME, w.teacherA().token(), draft(mon.plusDays(3), mon)).status()).isEqualTo(400);
        assertThat(post(ME, w.teacherA().token(), draft(mon, mon.plusDays(95))).status()).isEqualTo(400);
        Map<String, Object> noReason = draft(mon, mon.plusDays(1));
        noReason.put("reason", " ");
        assertThat(post(ME, w.teacherA().token(), noReason).status()).isEqualTo(400);
        Map<String, Object> longReason = draft(mon, mon.plusDays(1));
        longReason.put("reason", "x".repeat(501));
        assertThat(post(ME, w.teacherA().token(), longReason).status()).isEqualTo(400);
        Map<String, Object> noType = draft(mon, mon.plusDays(1));
        noType.put("leaveTypeId", null);
        assertThat(post(ME, w.teacherA().token(), noType).status()).isEqualTo(400);
        Map<String, Object> bothHalves = draft(mon, mon);
        bothHalves.put("halfDayStart", true);
        bothHalves.put("halfDayEnd", true);
        assertThat(post(ME, w.teacherA().token(), bothHalves).status()).isEqualTo(409);
    }

    @Test
    void aTeacherWithoutAPlacementCannotApply() {
        World w = newWorld();
        TeacherCtx unplaced = newTeacher(w.admin(), null, 0);
        LocalDate mon = monday();

        Resp resp = post(ME, unplaced.token(), draft(mon, mon.plusDays(1)));

        assertThat(resp.status()).isEqualTo(409);
        assertThat(resp.body()).contains("not placed");
    }

    @Test
    void twoSimultaneousOverlappingSubmissionsYieldExactlyOneSuccess() throws Exception {
        World w = newWorld();
        LocalDate mon = monday();
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var latch = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.Callable<Integer> call = () -> {
                latch.await();
                return post(ME, w.teacherA().token(), draft(mon, mon.plusDays(2))).status();
            };
            var a = pool.submit(call);
            var b = pool.submit(call);
            latch.countDown();
            var statuses = java.util.List.of(a.get(), b.get());
            assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void onlyTeachersMayApplyAndTypesArePerRoleToo() {
        World w = newWorld();
        LocalDate mon = monday();
        Map<String, String> tokens = Map.of(
                "admin", w.admin(), "director", w.director(), "manager", w.managerA().token());
        tokens.forEach((role, token) -> {
            assertThat(get(ME + "/types", token).status()).as(role + " types").isEqualTo(403);
            assertThat(post(ME + "/preview", token, draft(mon, mon)).status()).as(role + " preview").isEqualTo(403);
            assertThat(post(ME, token, draft(mon, mon)).status()).as(role + " submit").isEqualTo(403);
            assertThat(get(ME, token).status()).as(role + " list").isEqualTo(403);
        });
        String system = signInAs(com.hls.identity.user.Role.SYSTEM).token();
        assertThat(post(ME, system, draft(mon, mon)).status()).as("system submit").isEqualTo(403);
        assertThat(get(ME + "/types", w.teacherA().token()).status()).isEqualTo(200);
    }
}
