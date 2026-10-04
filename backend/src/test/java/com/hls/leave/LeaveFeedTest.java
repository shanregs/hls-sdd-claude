package com.hls.leave;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.attendance.api.AttendanceReadApi;
import com.hls.attendance.api.MarkView;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Spec 009 US4: approved leave becomes Leave marks, and revoke or Teacher cancel remove exactly those. */
class LeaveFeedTest extends LeaveTestBase {

    @Autowired
    private AttendanceReadApi attendance;

    private String approve(World w, String id) {
        Resp resp = post(SUP + "/" + id + "/approve", w.managerA().token(), Map.of("version", 0));
        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        return resp.body();
    }

    private Resp revoke(String token, String id, String reason, long version) {
        return post(SUP + "/" + id + "/revoke", token, Map.of("reason", reason, "version", version));
    }

    private int leaveMarks(String id) {
        return jdbc.queryForObject("select count(*) from attendance_mark where leave_request_id = ?::uuid", Integer.class, id);
    }

    private void supervisorMark(World w, LocalDate date, String code) {
        Resp resp = put(
                "/api/v1/attendance/teachers/" + w.teacherA().teacherId() + "/marks/" + date,
                w.managerA().token(),
                Map.of("statusCode", code, "dayValue", 1));
        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
    }

    @Test
    void approvalMarksOnlyWorkingDaysWithHalvesAttributedToTheApprover() {
        World w = newWorld();
        UUID teacher = w.teacherA().teacherId();
        LocalDate mon = monday();
        Map<String, Object> body = draft(mon, mon.plusDays(6));
        body.put("halfDayEnd", true);
        String id = field(post(ME, w.teacherA().token(), body).body(), "id");

        approve(w, id);

        assertThat(leaveMarks(id)).isEqualTo(6);
        assertThat(markRow(teacher, mon.plusDays(6))).as("the Sunday is not marked").isNull();
        Map<String, Object> last = markRow(teacher, mon.plusDays(5));
        assertThat(last.get("short_code")).isEqualTo("L");
        assertThat((BigDecimal) last.get("day_value")).isEqualByComparingTo("0.5");
        assertThat(last.get("set_by_kind")).isEqualTo("SUPERVISOR");
        assertThat((BigDecimal) markRow(teacher, mon).get("day_value")).isEqualByComparingTo("1");

        Integer byApprover = jdbc.queryForObject(
                "select count(*) from attendance_mark m join app_user u on u.id = m.set_by_user_id"
                        + " where m.leave_request_id = ?::uuid and u.id = ?::uuid",
                Integer.class,
                id,
                w.managerA().signed().userId().toString());
        assertThat(byApprover).isEqualTo(6);
    }

    @Test
    void theLeaveMarksAppearInTheAttendanceReadContractWithTheirRequest() {
        World w = newWorld();
        UUID teacher = w.teacherA().teacherId();
        LocalDate mon = monday();
        String id = apply(w.teacherA(), mon, mon.plusDays(1));
        approve(w, id);

        List<MarkView> marks = attendance.marksOf(teacher, YearMonth.from(mon));

        assertThat(marks).filteredOn(m -> mon.equals(m.date()) || mon.plusDays(1).equals(m.date()))
                .hasSize(2)
                .allSatisfy(m -> {
                    assertThat(m.code()).isEqualTo("L");
                    assertThat(m.leaveRequestId()).isEqualTo(UUID.fromString(id));
                });
        assertThat(attendance.rollupOf(teacher, YearMonth.from(mon)).daysLeave()).isGreaterThanOrEqualTo(new BigDecimal("2"));
    }

    @Test
    void anApprovalReplacesATeachersOwnMarkAndKeepsItInTheHistory() {
        World w = newWorld();
        UUID teacher = w.teacherA().teacherId();
        LocalDate mon = monday();
        supervisorMark(w, mon, "P");
        jdbc.update("update attendance_mark set set_by_kind = 'SELF' where teacher_id = ? and mark_date = ?", teacher, mon);
        String id = apply(w.teacherA(), mon, mon.plusDays(1));

        approve(w, id);

        assertThat(markRow(teacher, mon).get("short_code")).isEqualTo("L");
        Integer earlier = jdbc.queryForObject(
                "select count(*) from attendance_mark_history h join attendance_status_code c on c.id = h.status_code_id"
                        + " where h.teacher_id = ? and h.mark_date = ? and c.short_code = 'P'",
                Integer.class,
                teacher,
                mon);
        assertThat(earlier).isEqualTo(1);
    }

    @Test
    void futureDaysAreMarkedToo() {
        World w = newWorld();
        UUID teacher = w.teacherA().teacherId();
        LocalDate future = today().plusDays(10);
        String id = apply(w.teacherA(), future, future.plusDays(3));

        approve(w, id);

        assertThat(leaveMarks(id)).isGreaterThanOrEqualTo(2);
        assertThat(jdbc.queryForObject(
                        "select count(*) from attendance_mark where leave_request_id = ?::uuid and mark_date > ?",
                        Integer.class,
                        id,
                        today()))
                .isEqualTo(leaveMarks(id));
        assertThat(teacher).isNotNull();
    }

    @Test
    void aSupervisorRevokesAnApprovedRequestAndOnlyHandChangedDaysStay() {
        World w = newWorld();
        UUID teacher = w.teacherA().teacherId();
        LocalDate mon = monday();
        String id = apply(w.teacherA(), mon, mon.plusDays(2));
        approve(w, id);
        supervisorMark(w, mon.plusDays(1), "P");

        assertThat(revoke(w.managerA().token(), id, " ", 1).status()).isEqualTo(400);
        Resp revoked = revoke(w.managerA().token(), id, "Teacher is needed for exams", 1);

        assertThat(revoked.status()).as(revoked.body()).isEqualTo(200);
        assertThat(field(revoked.body(), "status")).isEqualTo("CANCELLED");
        assertThat(field(revoked.body(), "cancelledBy")).isEqualTo("SUPERVISOR");
        assertThat(revoked.body()).contains("Teacher is needed for exams");
        assertThat(markRow(teacher, mon)).isNull();
        assertThat(markRow(teacher, mon.plusDays(2))).isNull();
        assertThat(markRow(teacher, mon.plusDays(1)).get("short_code")).isEqualTo("P");
        assertThat(get(ME, w.teacherA().token()).body()).contains("Teacher is needed for exams");
    }

    @Test
    void aRevokeIsRefusedForALockedMonthAndChangesNothing() {
        World w = newWorld();
        UUID teacher = w.teacherA().teacherId();
        LocalDate mon = monday();
        String id = apply(w.teacherA(), mon, mon.plusDays(1));
        approve(w, id);
        lockMonth(teacher, YearMonth.from(mon));

        Resp resp = revoke(w.managerA().token(), id, "Changed plans", 1);

        assertThat(resp.status()).isEqualTo(409);
        assertThat(resp.body()).contains("month locked");
        assertThat(leaveMarks(id)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select status from leave_request where id = ?::uuid", String.class, id))
                .isEqualTo("APPROVED");
    }

    @Test
    void onlyAnApprovedRequestCanBeRevokedAndTheDatesAreFreeAfterwards() {
        World w = newWorld();
        LocalDate mon = monday();
        String pending = apply(w.teacherA(), mon, mon.plusDays(1));
        assertThat(revoke(w.managerA().token(), pending, "x", 0).status()).isEqualTo(409);

        approve(w, pending);
        assertThat(revoke(w.managerA().token(), pending, "Plans changed", 1).status()).isEqualTo(200);

        assertThat(post(ME, w.teacherA().token(), draft(mon, mon.plusDays(1))).status()).isEqualTo(201);
    }

    @Test
    void aTeacherCancelsApprovedLeaveThatHasNotStartedAndItsMarksGo() {
        World w = newWorld();
        UUID teacher = w.teacherA().teacherId();
        LocalDate future = today().plusDays(12);
        String id = apply(w.teacherA(), future, future.plusDays(2));
        approve(w, id);
        assertThat(get(ME, w.teacherA().token()).body()).contains("\"allowedActions\":[\"CANCEL\"]");
        assertThat(leaveMarks(id)).isPositive();

        Resp cancelled = post(ME + "/" + id + "/cancel", w.teacherA().token(), Map.of());

        assertThat(cancelled.status()).as(cancelled.body()).isEqualTo(200);
        assertThat(field(cancelled.body(), "status")).isEqualTo("CANCELLED");
        assertThat(field(cancelled.body(), "cancelledBy")).isEqualTo("TEACHER");
        assertThat(leaveMarks(id)).isZero();
        assertThat(markRow(teacher, future)).isNull();
    }

    @Test
    void aTeacherCannotCancelApprovedLeaveThatHasStarted() {
        World w = newWorld();
        LocalDate mon = monday();
        String id = apply(w.teacherA(), mon, mon.plusDays(1));
        approve(w, id);

        Resp resp = post(ME + "/" + id + "/cancel", w.teacherA().token(), Map.of());

        assertThat(resp.status()).isEqualTo(409);
        assertThat(resp.body()).contains("already started");
        assertThat(get(ME, w.teacherA().token()).body()).contains("\"allowedActions\":[]");
        assertThat(leaveMarks(id)).isEqualTo(2);
    }
}
