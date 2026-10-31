package com.hls.leave;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 040 US1: leave recorded for today is Approved at once; who may record it; the guards of spec 009. */
class SameDayLeaveApiTest extends LeaveTestBase {

    private static final String SAME_DAY = SUP + "/same-day";

    /** The tests need today to be a working day for the Teacher; on a weekly off or holiday they are skipped. */
    private void requireWorkingToday(World w) {
        Resp preview = post(ME + "/preview", w.teacherA().token(), draft(today(), today()));
        assumeTrue(preview.status() == 200 && preview.body().contains("\"problems\":[]"), "today is not a working day");
    }

    private Map<String, Object> recordBody(UUID teacherId, LocalDate first, LocalDate last) {
        Map<String, Object> body = new HashMap<>(draft(first, last));
        body.put("teacherId", teacherId.toString());
        return body;
    }

    private void supervisorMark(UUID teacher, LocalDate date, String shortCode, UUID school, UUID byUser) {
        jdbc.update(
                "insert into attendance_mark (id, teacher_id, mark_date, status_code_id, day_value, school_id,"
                        + " set_by_user_id, set_by_kind, set_at, version)"
                        + " select ?, ?, ?, c.id, 1.00, ?, ?, 'SUPERVISOR', now(), 0 from attendance_status_code c"
                        + " where c.short_code = ?",
                UUID.randomUUID(), teacher, date, school, byUser, shortCode);
    }

    @Test
    void aTeacherAppliesForTodayAndItIsApprovedAtOnce() {
        World w = newWorld();
        requireWorkingToday(w);

        Resp resp = post(ME, w.teacherA().token(), draft(today(), today()));

        assertThat(resp.status()).as(resp.body()).isEqualTo(201);
        assertThat(resp.body()).contains("\"status\":\"APPROVED\"").contains("\"sameDay\":true");
        Map<String, Object> mark = markRow(w.teacherA().teacherId(), today());
        assertThat(mark.get("short_code")).isEqualTo("L");
        assertThat(mark.get("leave_request_id")).isEqualTo(UUID.fromString(field(resp.body(), "id")));
        assertThat(jdbc.queryForObject(
                        "select count(*) from substitution_requirement where teacher_id = ? and duty_date = ?",
                        Integer.class,
                        w.teacherA().teacherId(),
                        today()))
                .isEqualTo(1);
    }

    @Test
    void aSecondRequestForTodayIsRefused() {
        World w = newWorld();
        requireWorkingToday(w);
        assertThat(post(ME, w.teacherA().token(), draft(today(), today())).status()).isEqualTo(201);

        Resp again = post(ME, w.teacherA().token(), draft(today(), today()));

        assertThat(again.status()).as(again.body()).isEqualTo(409);
    }

    @Test
    void leaveForAnEarlierDayStillWaitsForApprovalAndIsNotSameDay() {
        World w = newWorld();
        LocalDate mon = monday();

        Resp resp = post(ME, w.teacherA().token(), draft(mon, mon));

        assertThat(resp.status()).as(resp.body()).isEqualTo(201);
        assertThat(resp.body()).contains("\"status\":\"PENDING\"").contains("\"sameDay\":false");
    }

    @Test
    void aManagerRecordsForAnAssignedTeacherAndTheAuditNamesTheRecorder() {
        World w = newWorld();
        requireWorkingToday(w);

        Resp resp = post(SAME_DAY, w.managerA().token(), recordBody(w.teacherA().teacherId(), today(), today()));

        assertThat(resp.status()).as(resp.body()).isEqualTo(201);
        assertThat(resp.body()).contains("\"status\":\"APPROVED\"").contains("\"sameDay\":true");
        String id = field(resp.body(), "id");
        Map<String, Object> row = jdbc.queryForMap(
                "select created_by_user_id, decided_by_user_id, decision_note from leave_request where id = ?",
                UUID.fromString(id));
        assertThat(row.get("created_by_user_id")).isEqualTo(row.get("decided_by_user_id"));
        assertThat(row.get("decision_note")).isEqualTo("same-day, auto-accepted");
        assertThat(jdbc.queryForObject(
                        "select count(*) from change_history_entry where entity_id = ? and after_value like '%sameDay=true%auto-accepted%'",
                        Integer.class,
                        id))
                .isGreaterThanOrEqualTo(1);
    }

    @Test
    void aManagerCannotRecordForATeacherOutsideTheirSchools() {
        World w = newWorld();

        Resp resp = post(SAME_DAY, w.managerA().token(), recordBody(w.teacherB().teacherId(), today(), today()));

        assertThat(resp.status()).isEqualTo(404);
    }

    @Test
    void adminAndDirectorRecordForAnyTeacherAndTeacherOrSystemCannotUseTheEndpoint() {
        World w = newWorld();
        requireWorkingToday(w);

        assertThat(post(SAME_DAY, w.admin(), recordBody(w.teacherA().teacherId(), today(), today())).status())
                .isEqualTo(201);
        assertThat(post(SAME_DAY, w.director(), recordBody(w.teacherB().teacherId(), today(), today())).status())
                .isIn(201, 409);
        assertThat(post(SAME_DAY, w.teacherA().token(), recordBody(w.teacherB().teacherId(), today(), today())).status())
                .isEqualTo(403);
        assertThat(post(SAME_DAY, signInAs(com.hls.identity.user.Role.SYSTEM).token(),
                        recordBody(w.teacherA().teacherId(), today(), today())).status())
                .isEqualTo(403);
    }

    @Test
    void theRecorderEndpointOnlyAcceptsToday() {
        World w = newWorld();
        LocalDate mon = monday();

        Resp resp = post(SAME_DAY, w.managerA().token(), recordBody(w.teacherA().teacherId(), mon, mon));

        assertThat(resp.status()).as(resp.body()).isEqualTo(400);
    }

    @Test
    void anAbsentMarkSetEarlierTodayIsReplacedAndAPresentMarkIsNot() {
        World w = newWorld();
        requireWorkingToday(w);
        supervisorMark(w.teacherA().teacherId(), today(), "A", w.schoolA(), w.managerA().signed().userId());

        Resp resp = post(ME, w.teacherA().token(), draft(today(), today()));

        assertThat(resp.status()).as(resp.body()).isEqualTo(201);
        assertThat(markRow(w.teacherA().teacherId(), today()).get("short_code")).isEqualTo("L");
        assertThat(jdbc.queryForObject(
                        "select count(*) from attendance_mark_history where teacher_id = ? and mark_date = ?",
                        Integer.class,
                        w.teacherA().teacherId(),
                        today()))
                .as("the earlier Absent stays in the day's history")
                .isGreaterThanOrEqualTo(1);

        World v = newWorld();
        supervisorMark(v.teacherA().teacherId(), today(), "P", v.schoolA(), v.managerA().signed().userId());
        Resp refused = post(ME, v.teacherA().token(), draft(today(), today()));
        assertThat(refused.status()).as(refused.body()).isEqualTo(409);
        assertThat(refused.body()).contains("set by a supervisor");
    }

    @Test
    void anyLeaveTypeIsAcceptedAndAHalfDayRaisesAHalfDayRequirement() {
        World w = newWorld();
        requireWorkingToday(w);
        Map<String, Object> body = new HashMap<>(draft(today(), today()));
        body.put("leaveTypeId", jdbc.queryForObject("select id from leave_type where code = 'SICK'", UUID.class).toString());
        body.put("halfDayStart", true);

        Resp resp = post(ME, w.teacherA().token(), body);

        assertThat(resp.status()).as(resp.body()).isEqualTo(201);
        assertThat(jdbc.queryForObject(
                        "select day_value from substitution_requirement where teacher_id = ? and duty_date = ?",
                        java.math.BigDecimal.class,
                        w.teacherA().teacherId(),
                        today()))
                .isEqualByComparingTo("0.50");
    }

    @Test
    void thereIsNoLeaveBalanceAnywhereInTheView() {
        World w = newWorld();
        requireWorkingToday(w);

        Resp resp = post(ME, w.teacherA().token(), draft(today(), today()));

        assertThat(resp.body()).doesNotContainIgnoringCase("balance");
    }
}
