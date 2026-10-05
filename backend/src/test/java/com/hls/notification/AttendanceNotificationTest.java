package com.hls.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.support.AttendanceTestBase;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Spec 010 US4: supervisor changes, month lock and reopen notify the Teacher, with merging. */
class AttendanceNotificationTest extends AttendanceTestBase {

    private static final AtomicInteger NEXT_MONTH = new AtomicInteger(0);
    private static final String PRESENT = "00000000-0000-0000-0008-000000000001";
    private static final String BASE = "/api/v1/attendance";

    private Resp mark(String token, UUID teacher, LocalDate date) {
        return put(BASE + "/teachers/" + teacher + "/marks/" + date, token, Map.of("statusCode", "P", "dayValue", 1));
    }

    private List<Map<String, Object>> rows(UUID userId, String type) {
        return jdbc.queryForList(
                "select title, message, link, read_at, detail from notification"
                        + " where recipient_user_id = ? and type = ? order by created_at",
                userId,
                type);
    }

    private Map<String, Object> leaveDraft(LocalDate first) {
        UUID casual = jdbc.queryForObject("select id from leave_type where code = 'CASUAL'", UUID.class);
        return Map.of(
                "leaveTypeId", casual.toString(),
                "firstDate", first.toString(),
                "lastDate", first.plusDays(1).toString(),
                "halfDayStart", false,
                "halfDayEnd", false,
                "reason", "Family function");
    }

    @Test
    void aManagerMarkingADayNotifiesTheTeacher() {
        World w = newWorld();
        LocalDate day = today().minusDays(2);

        Resp resp = mark(w.managerA().token(), w.teacherA().teacherId(), day);

        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        var rows = rows(w.teacherA().signed().userId(), "ATTENDANCE_CHANGED");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0))
                .containsEntry("title", "Your attendance was updated")
                .containsEntry("link", "/my-attendance?month=" + YearMonth.from(day));
        assertThat((String) rows.get(0).get("message")).contains("updated your attendance for");
    }

    @Test
    void theTeachersOwnMarkAndALeaveMadeMarkCreateNone() {
        World w = newWorld();
        UUID user = w.teacherA().signed().userId();

        Resp own = put(
                BASE + "/me/marks/" + today().minusDays(1),
                w.teacherA().token(),
                Map.of("statusCode", "P", "dayValue", 1));
        assertThat(own.status()).as(own.body()).isEqualTo(200);
        Resp applied = post("/api/v1/me/leave", w.teacherA().token(), leaveDraft(today().plusDays(10)));
        assertThat(applied.status()).as(applied.body()).isEqualTo(201);
        String id = applied.body().replaceAll(".*\"id\":\"([^\"]*)\".*", "$1");
        Resp approved = post("/api/v1/leave/" + id + "/approve", w.managerA().token(), Map.of("version", 0));
        assertThat(approved.status()).as(approved.body()).isEqualTo(200);

        assertThat(rows(user, "ATTENDANCE_CHANGED")).isEmpty();
    }

    @Test
    void threeMarksByTheSameManagerMergeIntoOneUnreadNotification() {
        World w = newWorld();
        UUID teacher = w.teacherA().teacherId();

        for (int back = 1; back <= 3; back++) {
            assertThat(mark(w.managerA().token(), teacher, today().minusDays(back)).status())
                    .isEqualTo(200);
        }

        var rows = rows(w.teacherA().signed().userId(), "ATTENDANCE_CHANGED");
        assertThat(rows).hasSize(1);
        assertThat((String) rows.get(0).get("message")).contains("updated 3 days of your attendance");
        assertThat(rows.get(0).get("read_at")).isNull();
    }

    @Test
    void aMarkAfterTheTeacherReadItStartsANewNotification() {
        World w = newWorld();
        UUID teacher = w.teacherA().teacherId();
        mark(w.managerA().token(), teacher, today().minusDays(1));
        assertThat(post("/api/v1/me/notifications/read-all", w.teacherA().token(), Map.of()).status())
                .isEqualTo(200);

        mark(w.managerA().token(), teacher, today().minusDays(2));

        assertThat(rows(w.teacherA().signed().userId(), "ATTENDANCE_CHANGED")).hasSize(2);
    }

    @Test
    void anotherSupervisorsMarkIsASeparateNotification() {
        World w = newWorld();
        UUID teacher = w.teacherA().teacherId();
        mark(w.managerA().token(), teacher, today().minusDays(1));

        assertThat(mark(w.admin(), teacher, today().minusDays(2)).status()).isEqualTo(200);

        assertThat(rows(w.teacherA().signed().userId(), "ATTENDANCE_CHANGED")).hasSize(2);
    }

    @Test
    void aMarkMoreThanTenMinutesLaterIsSeparate() {
        World w = newWorld();
        UUID teacher = w.teacherA().teacherId();
        UUID user = w.teacherA().signed().userId();
        mark(w.managerA().token(), teacher, today().minusDays(1));
        jdbc.update(
                "update notification set updated_at = updated_at - interval '11 minutes' where recipient_user_id = ?",
                user);

        mark(w.managerA().token(), teacher, today().minusDays(2));

        assertThat(rows(user, "ATTENDANCE_CHANGED")).hasSize(2);
    }

    @Test
    void clearingAMarkNotifiesToo() {
        World w = newWorld();
        UUID teacher = w.teacherA().teacherId();
        mark(w.managerA().token(), teacher, today().minusDays(1));
        jdbc.update("delete from notification where recipient_user_id = ?", w.teacherA().signed().userId());

        Resp cleared = delete(BASE + "/teachers/" + teacher + "/marks/" + today().minusDays(1), w.managerA().token());

        assertThat(cleared.status()).as(cleared.body()).isIn(200, 204);
        assertThat(rows(w.teacherA().signed().userId(), "ATTENDANCE_CHANGED")).hasSize(1);
    }

    @Test
    void aMarkRefusedForALockedMonthCreatesNone() {
        World w = newWorld();
        UUID teacher = w.teacherA().teacherId();
        LocalDate day = today().minusDays(1);
        jdbc.update(
                "insert into attendance_teacher_month (id, teacher_id, year_month, state, working_days, days_worked,"
                        + " days_leave, training_available, training_attended, unmarked, weighted_total, changed_at, version)"
                        + " values (?, ?, ?, 'LOCKED', 20, 7, 1, 0, 0, 0, 7, now(), 0)",
                UUID.randomUUID(),
                teacher,
                YearMonth.from(day).toString());

        Resp refused = mark(w.managerA().token(), teacher, day);

        assertThat(refused.status()).isEqualTo(409);
        assertThat(rows(w.teacherA().signed().userId(), "ATTENDANCE_CHANGED")).isEmpty();
    }

    @Test
    void lockingAMonthNotifiesEachTeacherOnceAndReopenNotifiesOnce() {
        String admin = signInAs(Role.ADMIN).token();
        UUID school = schoolInNewZone(admin)[2];
        YearMonth month = YearMonth.of(2004, 1).plusMonths(NEXT_MONTH.getAndIncrement());
        TeacherCtx one = placedForMonth(admin, school, month);
        TeacherCtx two = placedForMonth(admin, school, month);

        Resp locked = post(BASE + "/months/" + month + "/lock", admin, Map.of());

        assertThat(locked.status()).as(locked.body()).isEqualTo(200);
        for (TeacherCtx t : List.of(one, two)) {
            var rows = rows(t.signed().userId(), "ATTENDANCE_MONTH_LOCKED");
            assertThat(rows).hasSize(1);
            assertThat((String) rows.get(0).get("message")).contains("January 2004 is locked");
        }

        Resp reopened = post(
                BASE + "/teachers/" + one.teacherId() + "/months/" + month + "/reopen",
                admin,
                Map.of("reason", "Wrong day"));

        assertThat(reopened.status()).as(reopened.body()).isEqualTo(200);
        var reopenRows = rows(one.signed().userId(), "ATTENDANCE_MONTH_REOPENED");
        assertThat(reopenRows).hasSize(1);
        assertThat((String) reopenRows.get(0).get("message")).contains("was reopened: Wrong day.");
        assertThat(rows(two.signed().userId(), "ATTENDANCE_MONTH_REOPENED")).isEmpty();
    }

    /** A Teacher placed for the whole month with every non-Sunday marked Present, so the month can lock. */
    private TeacherCtx placedForMonth(String admin, UUID school, YearMonth month) {
        TeacherCtx teacher = newTeacher(admin, null, 0);
        jdbc.update(
                "insert into teacher_placement (id, teacher_id, school_id, starts_on, ends_on, status, created_at)"
                        + " values (?, ?, ?, ?, ?, 'ACTIVE', now())",
                UUID.randomUUID(),
                teacher.teacherId(),
                school,
                month.atDay(1),
                month.atEndOfMonth());
        jdbc.update(
                "insert into attendance_mark (id, teacher_id, mark_date, status_code_id, day_value, school_id,"
                        + " set_by_user_id, set_by_kind, set_at, version)"
                        + " select gen_random_uuid(), ?, d::date, ?::uuid, 1.00, ?, ?, 'SUPERVISOR', now(), 0"
                        + " from generate_series(?::date, ?::date, interval '1 day') d"
                        + " where extract(isodow from d) <> 7",
                teacher.teacherId(),
                PRESENT,
                school,
                UUID.randomUUID(),
                month.atDay(1),
                month.atEndOfMonth());
        return teacher;
    }
}
