package com.hls.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hls.attendance.internal.MarkService;
import com.hls.attendance.internal.SetByKind;
import com.hls.identity.user.Role;
import com.hls.school.api.ConflictException;
import com.hls.support.AttendanceTestBase;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Spec 008 US1 and US7: a Teacher marks and reviews their own attendance. */
class MyAttendanceControllerTest extends AttendanceTestBase {

    private static final String ME = "/api/v1/attendance/me";

    @Autowired
    private MarkService markService;

    private Map<String, Object> body(String code, double value) {
        return Map.of("statusCode", code, "dayValue", value);
    }

    private Resp mark(String token, LocalDate date, String code, double value) {
        return put(ME + "/marks/" + date, token, body(code, value));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> day(String token, LocalDate date) {
        Resp resp = get(ME + "?month=" + YearMonth.from(date), token);
        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        List<Map<String, Object>> days = (List<Map<String, Object>>) resp.map().get("days");
        return days.stream().filter(d -> date.toString().equals(d.get("date"))).findFirst().orElseThrow();
    }

    private long historyRows(UUID teacherId, LocalDate date) {
        return jdbc.queryForObject(
                "select count(*) from attendance_mark_history where teacher_id = ? and mark_date = ?",
                Long.class,
                teacherId,
                date);
    }

    private void lockMonth(UUID teacherId, YearMonth month) {
        jdbc.update(
                "insert into attendance_teacher_month (id, teacher_id, year_month, state, working_days, days_worked,"
                        + " days_leave, training_available, training_attended, unmarked, weighted_total, changed_at, version)"
                        + " values (?, ?, ?, 'LOCKED', 0, 0, 0, 0, 0, 0, 0, now(), 0)",
                UUID.randomUUID(),
                teacherId,
                month.toString());
    }

    @Test
    void aTeacherMarksTodayWholeAndYesterdayHalfWithTheirSchool() {
        String admin = signInAs(Role.ADMIN).token();
        UUID school = schoolInNewZone(admin)[2];
        TeacherCtx teacher = newTeacher(admin, school, 30);

        Resp todayMark = mark(teacher.token(), today(), "P", 1);
        Resp yesterday = mark(teacher.token(), today().minusDays(1), "P", 0.5);

        assertThat(todayMark.status()).as(todayMark.body()).isEqualTo(200);
        assertThat(yesterday.status()).as(yesterday.body()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        Map<String, Object> mark = (Map<String, Object>) day(teacher.token(), today()).get("mark");
        assertThat(mark).containsEntry("code", "P").containsEntry("setByKind", "SELF").containsEntry("schoolId", school.toString());
        assertThat(mark.get("setByName")).asString().startsWith("Tester");
        assertThat(day(teacher.token(), today()).get("state")).isEqualTo("MARKED");
        assertThat(day(teacher.token(), today()).get("editableBy")).isEqualTo("SELF");
        assertChangeRecorded(admin, "ATTENDANCE_MARK", teacher.teacherId() + ":" + today(), "mark");
    }

    @Test
    void markingAgainIsACorrectionAndKeepsTheEarlierValueInHistory() {
        String admin = signInAs(Role.ADMIN).token();
        TeacherCtx teacher = newTeacher(admin, schoolInNewZone(admin)[2], 30);
        Resp first = mark(teacher.token(), today(), "P", 1);
        long version = ((Number) first.map().get("version")).longValue();

        Resp second = put(ME + "/marks/" + today(), teacher.token(), Map.of("statusCode", "L", "dayValue", 1, "version", version));

        assertThat(second.status()).as(second.body()).isEqualTo(200);
        assertThat(second.map()).containsEntry("code", "L");
        assertThat(historyRows(teacher.teacherId(), today())).isEqualTo(2L);
        assertThat(jdbc.queryForObject("select count(*) from attendance_mark where teacher_id = ?", Long.class, teacher.teacherId()))
                .isEqualTo(1L);
    }

    @Test
    void aStaleVersionIsRefused() {
        String admin = signInAs(Role.ADMIN).token();
        TeacherCtx teacher = newTeacher(admin, schoolInNewZone(admin)[2], 30);
        mark(teacher.token(), today(), "P", 1);

        Resp stale = put(ME + "/marks/" + today(), teacher.token(), Map.of("statusCode", "L", "dayValue", 1, "version", 42));

        assertThat(stale.status()).isEqualTo(409);
        assertThat(stale.body()).contains("changed by someone else");
    }

    @Test
    void datesOutsideTheSelfMarkWindowAreRefusedWithAReason() {
        String admin = signInAs(Role.ADMIN).token();
        TeacherCtx teacher = newTeacher(admin, schoolInNewZone(admin)[2], 30);

        Resp future = mark(teacher.token(), today().plusDays(1), "P", 1);
        Resp tooOld = mark(teacher.token(), today().minusDays(4), "P", 1);
        Resp edgeOfWindow = mark(teacher.token(), today().minusDays(3), "P", 1);

        assertThat(future.status()).isEqualTo(409);
        assertThat(future.body()).contains("future");
        assertThat(tooOld.status()).isEqualTo(409);
        assertThat(tooOld.body()).contains("Ask your Manager");
        assertThat(edgeOfWindow.status()).as(edgeOfWindow.body()).isEqualTo(200);
    }

    @Test
    void aDateWithoutAPlacementIsRefused() {
        String admin = signInAs(Role.ADMIN).token();
        TeacherCtx unplaced = newTeacher(admin, null, 0);

        Resp resp = mark(unplaced.token(), today(), "P", 1);

        assertThat(resp.status()).isEqualTo(409);
        assertThat(resp.body()).contains("School placement");
    }

    @Test
    void aDateBeforeThePlacementStartedIsRefused() {
        String admin = signInAs(Role.ADMIN).token();
        TeacherCtx teacher = newTeacher(admin, null, 0);
        placeTeacherRaw(admin, teacher.teacherId(), schoolInNewZone(admin)[2], LocalDate.now().minusDays(1));

        Resp resp = mark(teacher.token(), today().minusDays(3), "P", 1);

        assertThat(resp.status()).isEqualTo(409);
        assertThat(resp.body()).contains("School placement");
    }

    @Test
    void anExitedTeacherCannotBeMarkedOnOrAfterTheirExit() {
        String admin = signInAs(Role.ADMIN).token();
        TeacherCtx teacher = newTeacher(admin, schoolInNewZone(admin)[2], 30);
        Resp exited = exitTeacher(admin, teacher.teacherId(), LocalDate.now().minusDays(1));
        assertThat(exited.status()).as(exited.body()).isEqualTo(200);

        assertThatThrownBy(() -> markService.setMark(
                        teacher.signed().userId(),
                        teacher.teacherId(),
                        today(),
                        "P",
                        new java.math.BigDecimal("1"),
                        null,
                        null,
                        SetByKind.SUPERVISOR))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("exited");
        // The exit also unlinks the account, so the Teacher is told their profile is not set up.
        assertThat(mark(teacher.token(), today(), "P", 1).status()).isEqualTo(404);
    }

    @Test
    void aLockedMonthRefusesTheMark() {
        String admin = signInAs(Role.ADMIN).token();
        TeacherCtx teacher = newTeacher(admin, schoolInNewZone(admin)[2], 30);
        lockMonth(teacher.teacherId(), YearMonth.from(today()));

        Resp resp = mark(teacher.token(), today(), "P", 1);

        assertThat(resp.status()).isEqualTo(409);
        assertThat(resp.body()).contains("locked");
        assertThat(day(teacher.token(), today()).get("editableBy")).isEqualTo("NONE");
    }

    @Test
    void aDaySetByASupervisorCannotBeChangedByTheTeacher() {
        String admin = signInAs(Role.ADMIN).token();
        TeacherCtx teacher = newTeacher(admin, schoolInNewZone(admin)[2], 30);
        mark(teacher.token(), today(), "P", 1);
        jdbc.update("update attendance_mark set set_by_kind = 'SUPERVISOR', version = version + 1 where teacher_id = ?", teacher.teacherId());

        Resp resp = mark(teacher.token(), today(), "L", 1);

        assertThat(resp.status()).isEqualTo(409);
        assertThat(resp.body()).contains("set by your Manager");
        assertThat(day(teacher.token(), today()).get("editableBy")).isEqualTo("NONE");
    }

    @Test
    void anInactiveOrUnknownStatusIsRefused() {
        String admin = signInAs(Role.ADMIN).token();
        TeacherCtx teacher = newTeacher(admin, schoolInNewZone(admin)[2], 30);
        String code = "Y" + ThreadLocalRandom.current().nextInt(100000, 999999);
        Resp created = post("/api/v1/attendance/status-codes", admin, Map.of("shortCode", code, "name", "Temp", "category", "LEAVE", "weight", 0));
        long version = ((Number) created.map().get("version")).longValue();
        put("/api/v1/attendance/status-codes/" + created.id(), admin, Map.of("name", "Temp", "weight", 0, "active", false, "version", version));

        Resp inactive = mark(teacher.token(), today(), code, 1);
        Resp unknown = mark(teacher.token(), today(), "ZZZZ", 1);

        assertThat(inactive.status()).isEqualTo(409);
        assertThat(unknown.status()).isEqualTo(400);
    }

    @Test
    void invalidDayValueAndLongNoteAreRefused() {
        String admin = signInAs(Role.ADMIN).token();
        TeacherCtx teacher = newTeacher(admin, schoolInNewZone(admin)[2], 30);

        Resp badValue = mark(teacher.token(), today(), "P", 0.75);
        Resp longNote = put(ME + "/marks/" + today(), teacher.token(), Map.of("statusCode", "P", "dayValue", 1, "note", "x".repeat(501)));
        Resp noStatus = put(ME + "/marks/" + today(), teacher.token(), Map.of("dayValue", 1));

        assertThat(badValue.status()).isEqualTo(400);
        assertThat(longNote.status()).isEqualTo(400);
        assertThat(noStatus.status()).isEqualTo(400);
    }

    @Test
    void aUserWithoutALinkedTeacherSeesAProfileNotSetUpMessage() {
        Signed unlinked = signInAs(Role.TEACHER);

        Resp resp = get(ME, unlinked.token());

        assertThat(resp.status()).isEqualTo(404);
        assertThat(resp.body()).contains("Your profile has not been set up yet.");
    }

    @Test
    void everyOtherRoleAndAnonymousCallersAreRefused() {
        for (Role role : new Role[] {Role.ADMIN, Role.DIRECTOR, Role.MANAGER, Role.SYSTEM}) {
            String token = signInAs(role).token();
            assertThat(get(ME, token).status()).as("%s GET", role).isEqualTo(403);
            assertThat(mark(token, today(), "P", 1).status()).as("%s PUT", role).isEqualTo(403);
        }
        assertThat(get(ME, null).status()).isEqualTo(401);
        assertThat(mark(null, today(), "P", 1).status()).isEqualTo(401);
    }

    @Test
    void anEarlierMonthIsReadableAndAMalformedMonthIsRefused() {
        String admin = signInAs(Role.ADMIN).token();
        TeacherCtx teacher = newTeacher(admin, schoolInNewZone(admin)[2], 90);

        Resp earlier = get(ME + "?month=" + YearMonth.from(today()).minusMonths(1), teacher.token());
        Resp bad = get(ME + "?month=2026-13", teacher.token());

        assertThat(earlier.status()).isEqualTo(200);
        assertThat(earlier.body()).contains("\"rollup\"");
        assertThat(bad.status()).isEqualTo(400);
    }

    @Test
    void aLockedMonthShowsTheFrozenRollupAndNothingEditable() {
        String admin = signInAs(Role.ADMIN).token();
        TeacherCtx teacher = newTeacher(admin, schoolInNewZone(admin)[2], 90);
        YearMonth previous = YearMonth.from(today()).minusMonths(1);
        lockMonth(teacher.teacherId(), previous);

        Resp resp = get(ME + "?month=" + previous, teacher.token());

        assertThat(resp.status()).isEqualTo(200);
        assertThat(resp.map()).containsEntry("locked", true);
        assertThat(resp.body()).contains("\"frozen\":true").doesNotContain("\"editableBy\":\"SELF\"");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> days = (List<Map<String, Object>>) resp.map().get("days");
        assertThat(days).isNotEmpty().allSatisfy(d -> assertThat(d).containsEntry("editableBy", "NONE"));
    }

    @Test
    void anEarlierMonthShowsTheSameFiguresTheTeachersManagerSees() {
        World w = newWorld();
        YearMonth previous = YearMonth.from(today()).minusMonths(1);
        LocalDate day = previous.atDay(10);
        String teacherId = w.teacherA().teacherId().toString();
        Resp marked = put("/api/v1/attendance/teachers/" + teacherId + "/marks/" + day, w.managerA().token(), body("P", 1));
        assertThat(marked.status()).as(marked.body()).isEqualTo(200);

        Resp mine = get(ME + "?month=" + previous, w.teacherA().token());
        Resp theirs = get("/api/v1/attendance/teachers/" + teacherId + "?month=" + previous, w.managerA().token());

        assertThat(mine.status()).isEqualTo(200);
        assertThat(theirs.status()).isEqualTo(200);
        assertThat(mine.map().get("rollup")).isEqualTo(theirs.map().get("rollup"));
        assertThat(mine.body()).contains("\"daysWorked\":1");
    }

    @Test
    void aTeacherCanNeverReadAnotherTeachersMonth() {
        World w = newWorld();
        String otherTeacher = w.teacherB().teacherId().toString();
        String month = YearMonth.from(today()).toString();

        Resp asTeacher = get("/api/v1/attendance/teachers/" + otherTeacher + "?month=" + month, w.teacherA().token());
        Resp asWrongManager = get("/api/v1/attendance/teachers/" + otherTeacher + "?month=" + month, w.managerA().token());
        Resp missing = get(
                "/api/v1/attendance/teachers/" + UUID.randomUUID() + "?month=" + month, w.managerA().token());

        assertThat(asTeacher.status()).isEqualTo(403);
        assertThat(asWrongManager.status()).isEqualTo(404);
        assertThat(asWrongManager.body()).isEqualTo(missing.body());
        // /me only ever returns the caller's own record, whatever the Teacher id of someone else is.
        Resp mine = get(ME + "?month=" + month, w.teacherA().token());
        assertThat(mine.body()).contains(w.teacherA().teacherId().toString()).doesNotContain(otherTeacher);
    }
}
