package com.hls.attendance;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.support.AttendanceTestBase;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 008 US2: the Manager grid and supervisor marking, scoped to assigned Teachers. */
class TeacherAttendanceControllerTest extends AttendanceTestBase {

    private static final String BASE = "/api/v1/attendance";

    private String teacherUrl(UUID teacherId) {
        return BASE + "/teachers/" + teacherId;
    }

    private Resp supervisorMark(String token, UUID teacherId, LocalDate date, String code, double value) {
        return put(teacherUrl(teacherId) + "/marks/" + date, token, Map.of("statusCode", code, "dayValue", value));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> content(String token, String url) {
        Resp resp = get(url, token);
        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        return (List<Map<String, Object>>) resp.map().get("content");
    }

    @Test
    void aManagerSeesOnlyTheirPlacedTeachersWithTheRealNumberOfDays() {
        World w = newWorld();
        UUID unplaced = teacher(w.admin());
        YearMonth month = YearMonth.from(today());

        Resp grid = get(BASE + "/teacher-grid?month=" + month, w.managerA().token());

        assertThat(grid.status()).as(grid.body()).isEqualTo(200);
        assertThat(grid.map()).containsEntry("days", month.lengthOfMonth());
        List<Map<String, Object>> rows = content(w.managerA().token(), BASE + "/teacher-grid?month=" + month);
        assertThat(rows).extracting(r -> r.get("teacherId")).containsExactly(w.teacherA().teacherId().toString());
        assertThat(grid.body()).doesNotContain(unplaced.toString()).doesNotContain(w.teacherB().teacherId().toString());
        @SuppressWarnings("unchecked")
        List<Object> cells = (List<Object>) rows.get(0).get("cells");
        assertThat(cells).hasSize(month.lengthOfMonth());
    }

    @Test
    void aManagerMarkIsAttributedToTheManagerAndAllowsDatesOlderThanTheTeacherWindow() {
        World w = newWorld();
        LocalDate old = today().minusDays(10);

        Resp resp = supervisorMark(w.managerA().token(), w.teacherA().teacherId(), old, "P", 1);

        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        assertThat(resp.map()).containsEntry("setByKind", "SUPERVISOR");
        assertThat(resp.map().get("setByUserId")).isEqualTo(w.managerA().signed().userId().toString());
        assertThat(resp.map().get("schoolId")).isEqualTo(w.schoolA().toString());
    }

    @Test
    void onceAManagerSetsADayTheTeacherCanNoLongerChangeIt() {
        World w = newWorld();
        put("/api/v1/attendance/me/marks/" + today(), w.teacherA().token(), Map.of("statusCode", "P", "dayValue", 1));

        Resp byManager = supervisorMark(w.managerA().token(), w.teacherA().teacherId(), today(), "L", 1);
        Resp byTeacher = put(
                "/api/v1/attendance/me/marks/" + today(), w.teacherA().token(), Map.of("statusCode", "P", "dayValue", 1));

        assertThat(byManager.status()).as(byManager.body()).isEqualTo(200);
        assertThat(byTeacher.status()).isEqualTo(409);
        assertThat(byTeacher.body()).contains("set by your Manager");
    }

    @Test
    void anotherManagersTeacherIsIndistinguishableFromAMissingOne() {
        World w = newWorld();
        String managerA = w.managerA().token();
        UUID foreign = w.teacherB().teacherId();
        UUID missing = UUID.randomUUID();
        String month = YearMonth.from(today()).toString();

        for (String suffix : List.of("?month=" + month, "/marks/" + today() + "/history")) {
            Resp outOfScope = get(teacherUrl(foreign) + suffix, managerA);
            Resp nonexistent = get(teacherUrl(missing) + suffix, managerA);
            assertThat(outOfScope.status()).as(suffix).isEqualTo(404);
            assertThat(outOfScope.body()).isEqualTo(nonexistent.body());
        }
        Resp mark = supervisorMark(managerA, foreign, today(), "P", 1);
        Resp missingMark = supervisorMark(managerA, missing, today(), "P", 1);
        assertThat(mark.status()).isEqualTo(404);
        assertThat(mark.body()).isEqualTo(missingMark.body());
        assertThat(delete(teacherUrl(foreign) + "/marks/" + today(), managerA).status()).isEqualTo(404);
        assertThat(jdbc.queryForObject(
                        "select count(*) from attendance_mark where teacher_id = ?", Long.class, foreign))
                .isZero();
    }

    @Test
    void clearingAMarkLeavesTheDayUnmarkedAndKeepsItInHistory() {
        World w = newWorld();
        UUID teacher = w.teacherA().teacherId();
        supervisorMark(w.managerA().token(), teacher, today(), "P", 1);

        Resp cleared = delete(teacherUrl(teacher) + "/marks/" + today(), w.managerA().token());

        assertThat(cleared.status()).isEqualTo(204);
        assertThat(jdbc.queryForObject("select count(*) from attendance_mark where teacher_id = ?", Long.class, teacher))
                .isZero();
        List<Map<String, Object>> history = historyOf(w.managerA().token(), teacher, today());
        assertThat(history).extracting(h -> h.get("action")).containsExactly("CLEARED", "CREATED");
        assertThat(delete(teacherUrl(teacher) + "/marks/" + today(), w.managerA().token()).status()).isEqualTo(404);
    }

    @Test
    void adminMayClearButDirectorMustMarkOverInstead() {
        World w = newWorld();
        UUID teacher = w.teacherA().teacherId();
        supervisorMark(w.managerA().token(), teacher, today(), "P", 1);

        Resp byDirector = delete(teacherUrl(teacher) + "/marks/" + today(), w.director());
        Resp overwrite = supervisorMark(w.director(), teacher, today(), "L", 1);
        Resp byAdmin = delete(teacherUrl(teacher) + "/marks/" + today(), w.admin());

        assertThat(byDirector.status()).isEqualTo(403);
        assertThat(overwrite.status()).as(overwrite.body()).isEqualTo(200);
        assertThat(byAdmin.status()).isEqualTo(204);
    }

    @Test
    void historyListsEveryValueNewestFirst() {
        World w = newWorld();
        UUID teacher = w.teacherA().teacherId();
        Resp first = supervisorMark(w.managerA().token(), teacher, today(), "P", 1);
        long version = ((Number) first.map().get("version")).longValue();
        put(
                teacherUrl(teacher) + "/marks/" + today(),
                w.managerA().token(),
                Map.of("statusCode", "L", "dayValue", 1, "version", version));

        List<Map<String, Object>> history = historyOf(w.managerA().token(), teacher, today());

        assertThat(history).extracting(h -> h.get("action")).containsExactly("CORRECTED", "CREATED");
        assertThat(history.get(0)).containsEntry("code", "L");
        assertThat(history.get(1)).containsEntry("code", "P");
    }

    @Test
    void aLockedMonthRefusesTheManager() {
        World w = newWorld();
        UUID teacher = w.teacherA().teacherId();
        jdbc.update(
                "insert into attendance_teacher_month (id, teacher_id, year_month, state, working_days, days_worked,"
                        + " days_leave, training_available, training_attended, unmarked, weighted_total, changed_at, version)"
                        + " values (?, ?, ?, 'LOCKED', 0, 0, 0, 0, 0, 0, 0, now(), 0)",
                UUID.randomUUID(),
                teacher,
                YearMonth.from(today()).toString());

        Resp mark = supervisorMark(w.managerA().token(), teacher, today(), "P", 1);
        Resp clear = delete(teacherUrl(teacher) + "/marks/" + today(), w.managerA().token());

        assertThat(mark.status()).isEqualTo(409);
        assertThat(mark.body()).contains("locked");
        assertThat(clear.status()).isIn(404, 409);
    }

    @Test
    void roleMatrixOnTheManagerEndpoints() {
        World w = newWorld();
        UUID teacher = w.teacherA().teacherId();
        String month = YearMonth.from(today()).toString();
        String grid = BASE + "/teacher-grid?month=" + month;

        for (String token : List.of(w.teacherA().token(), signInAs(Role.SYSTEM).token())) {
            assertThat(get(grid, token).status()).isEqualTo(403);
            assertThat(get(teacherUrl(teacher) + "?month=" + month, token).status()).isEqualTo(403);
            assertThat(supervisorMark(token, teacher, today(), "P", 1).status()).isEqualTo(403);
            assertThat(delete(teacherUrl(teacher) + "/marks/" + today(), token).status()).isEqualTo(403);
        }
        // Admin and Director use the organization-wide grid instead of the Manager grid.
        assertThat(get(grid, w.admin()).status()).isEqualTo(403);
        assertThat(get(grid, w.director()).status()).isEqualTo(403);
        assertThat(get(grid, null).status()).isEqualTo(401);
    }

    @Test
    void adminAndDirectorCanReadAnyTeachersMonthAndMarkAnyDay() {
        World w = newWorld();
        String month = YearMonth.from(today()).toString();

        for (String token : List.of(w.admin(), w.director())) {
            Resp view = get(teacherUrl(w.teacherB().teacherId()) + "?month=" + month, token);
            assertThat(view.status()).as(view.body()).isEqualTo(200);
            assertThat(supervisorMark(token, w.teacherB().teacherId(), today().minusDays(8), "P", 1).status())
                    .isEqualTo(200);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> historyOf(String token, UUID teacher, LocalDate date) {
        Resp resp = get(teacherUrl(teacher) + "/marks/" + date + "/history", token);
        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        return (List<Map<String, Object>>)
                (List<?>) org.springframework.boot.json.JsonParserFactory.getJsonParser().parseList(resp.body());
    }
}
