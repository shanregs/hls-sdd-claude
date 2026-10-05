package com.hls.leave;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

/** Spec 009 US3: approve and reject, one winner under concurrency, all-or-nothing approval. */
class LeaveDecisionTest extends LeaveTestBase {

    private Map<String, Object> approve(long version) {
        return Map.of("note", "Enjoy", "version", version);
    }

    private Map<String, Object> reason(String text, long version) {
        return Map.of("reason", text, "version", version);
    }

    private void supervisorMark(World w, LocalDate date, String code) {
        Resp resp = put(
                "/api/v1/attendance/teachers/" + w.teacherA().teacherId() + "/marks/" + date,
                w.managerA().token(),
                Map.of("statusCode", code, "dayValue", 1));
        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
    }

    @Test
    void aManagerApprovesAndTheTeacherSeesTheOutcome() {
        World w = newWorld();
        LocalDate mon = monday();
        String id = apply(w.teacherA(), mon, mon.plusDays(2));

        Resp approved = post(SUP + "/" + id + "/approve", w.managerA().token(), approve(0));

        assertThat(approved.status()).as(approved.body()).isEqualTo(200);
        assertThat(field(approved.body(), "status")).isEqualTo("APPROVED");
        assertThat(field(approved.body(), "decidedByName")).isNotBlank();
        assertThat(approved.body()).contains("\"allowedActions\":[\"REVOKE\"]");
        assertThat(get(ME, w.teacherA().token()).body()).contains("\"status\":\"APPROVED\"");
    }

    @Test
    void rejectingNeedsAReasonAndTheTeacherSeesIt() {
        World w = newWorld();
        LocalDate mon = monday();
        String id = apply(w.teacherA(), mon, mon.plusDays(1));

        assertThat(post(SUP + "/" + id + "/reject", w.managerA().token(), reason(" ", 0)).status()).isEqualTo(400);
        assertThat(post(SUP + "/" + id + "/reject", w.managerA().token(), reason("x".repeat(501), 0)).status())
                .isEqualTo(400);

        Resp rejected = post(SUP + "/" + id + "/reject", w.managerA().token(), reason("Exams that week", 0));

        assertThat(rejected.status()).as(rejected.body()).isEqualTo(200);
        assertThat(field(rejected.body(), "status")).isEqualTo("REJECTED");
        String mine = get(ME, w.teacherA().token()).body();
        assertThat(mine).contains("\"status\":\"REJECTED\"").contains("Exams that week");
        assertThat(markRow(w.teacherA().teacherId(), mon)).isNull();
    }

    @Test
    void aDecidedRequestCannotBeDecidedAgain() {
        World w = newWorld();
        LocalDate mon = monday();
        String id = apply(w.teacherA(), mon, mon.plusDays(1));
        assertThat(post(SUP + "/" + id + "/approve", w.managerA().token(), approve(0)).status()).isEqualTo(200);

        Resp again = post(SUP + "/" + id + "/reject", w.managerA().token(), reason("Changed my mind", 1));

        assertThat(again.status()).isEqualTo(409);
        assertThat(again.body()).contains("already approved");
    }

    @Test
    void aRequestTheTeacherCancelledWhileOpenIsRefusedWithAClearMessage() {
        World w = newWorld();
        LocalDate mon = monday();
        String id = apply(w.teacherA(), mon, mon.plusDays(1));
        assertThat(post(ME + "/" + id + "/cancel", w.teacherA().token(), Map.of()).status()).isEqualTo(200);

        Resp resp = post(SUP + "/" + id + "/approve", w.managerA().token(), approve(1));

        assertThat(resp.status()).isEqualTo(409);
        assertThat(resp.body()).contains("cancelled by the Teacher");
    }

    @Test
    void aStaleVersionIsRefused() {
        World w = newWorld();
        LocalDate mon = monday();
        String id = apply(w.teacherA(), mon, mon.plusDays(1));

        assertThat(post(SUP + "/" + id + "/approve", w.managerA().token(), approve(7)).status()).isEqualTo(409);
        assertThat(post(SUP + "/" + id + "/approve", w.managerA().token(), Map.of("note", "x")).status())
                .isEqualTo(400);
    }

    @Test
    void approvalIsRefusedAsAWholeForASupervisorSetDayWithNothingChanged() {
        World w = newWorld();
        LocalDate mon = monday();
        String id = apply(w.teacherA(), mon, mon.plusDays(2));
        supervisorMark(w, mon.plusDays(1), "T");

        Resp resp = post(SUP + "/" + id + "/approve", w.managerA().token(), approve(0));

        assertThat(resp.status()).isEqualTo(409);
        assertThat(resp.body()).contains("set by a supervisor").contains(mon.plusDays(1).toString());
        assertThat(markRow(w.teacherA().teacherId(), mon)).isNull();
        assertThat(jdbc.queryForObject("select status from leave_request where id = ?::uuid", String.class, id))
                .isEqualTo("PENDING");
    }

    @Test
    void approvalIsRefusedForALockedMonth() {
        World w = newWorld();
        LocalDate mon = monday();
        String id = apply(w.teacherA(), mon, mon.plusDays(1));
        lockMonth(w.teacherA().teacherId(), java.time.YearMonth.from(mon));

        Resp resp = post(SUP + "/" + id + "/approve", w.managerA().token(), approve(0));

        assertThat(resp.status()).isEqualTo(409);
        assertThat(resp.body()).contains("month locked");
        assertThat(markRow(w.teacherA().teacherId(), mon)).isNull();
    }

    @Test
    void twoApproversAtOnceYieldExactlyOneSuccess() throws Exception {
        World w = newWorld();
        LocalDate mon = monday();
        String id = apply(w.teacherA(), mon, mon.plusDays(1));
        var pool = Executors.newFixedThreadPool(2);
        try {
            var latch = new CountDownLatch(1);
            Callable<Integer> asManager = () -> {
                latch.await();
                return post(SUP + "/" + id + "/approve", w.managerA().token(), approve(0)).status();
            };
            Callable<Integer> asAdmin = () -> {
                latch.await();
                return post(SUP + "/" + id + "/approve", w.admin(), approve(0)).status();
            };
            var a = pool.submit(asManager);
            var b = pool.submit(asAdmin);
            latch.countDown();
            assertThat(java.util.List.of(a.get(), b.get())).containsExactlyInAnyOrder(200, 409);
        } finally {
            pool.shutdownNow();
        }
        assertThat(jdbc.queryForObject(
                        "select count(*) from attendance_mark where leave_request_id = ?::uuid", Integer.class, id))
                .isEqualTo(2);
    }

    @Test
    void approverAndTeacherCancelAtOnceYieldOneOutcomeAndNoStrayMarks() throws Exception {
        World w = newWorld();
        LocalDate future = today().plusDays(10);
        UUID teacher = w.teacherA().teacherId();
        String id = apply(w.teacherA(), future, future.plusDays(1));
        var pool = Executors.newFixedThreadPool(2);
        try {
            var latch = new CountDownLatch(1);
            Callable<Integer> approver = () -> {
                latch.await();
                return post(SUP + "/" + id + "/approve", w.managerA().token(), approve(0)).status();
            };
            Callable<Integer> teacherCancel = () -> {
                latch.await();
                return post(ME + "/" + id + "/cancel", w.teacherA().token(), Map.of()).status();
            };
            var a = pool.submit(approver);
            var b = pool.submit(teacherCancel);
            latch.countDown();
            a.get();
            b.get();
        } finally {
            pool.shutdownNow();
        }
        String status = jdbc.queryForObject("select status from leave_request where id = ?::uuid", String.class, id);
        Integer marks = jdbc.queryForObject(
                "select count(*) from attendance_mark where leave_request_id = ?::uuid", Integer.class, id);
        if ("CANCELLED".equals(status)) {
            assertThat(marks).isZero();
        } else {
            assertThat(status).isEqualTo("APPROVED");
            assertThat(marks).isPositive();
        }
        assertThat(teacher).isNotNull();
    }

    @Test
    void theApprovalActionsAreForSupervisorsOnly() {
        World w = newWorld();
        LocalDate mon = monday();
        String id = apply(w.teacherA(), mon, mon.plusDays(1));
        String system = signInAs(com.hls.identity.user.Role.SYSTEM).token();

        for (String token : new String[] {w.teacherA().token(), system}) {
            assertThat(get(SUP, token).status()).isEqualTo(403);
            assertThat(get(SUP + "/" + id, token).status()).isEqualTo(403);
            assertThat(post(SUP + "/" + id + "/approve", token, approve(0)).status()).isEqualTo(403);
            assertThat(post(SUP + "/" + id + "/reject", token, reason("no", 0)).status()).isEqualTo(403);
            assertThat(post(SUP + "/" + id + "/revoke", token, reason("no", 0)).status()).isEqualTo(403);
        }
    }
}
