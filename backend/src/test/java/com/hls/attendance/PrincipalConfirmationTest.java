package com.hls.attendance;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.support.SubstitutionTestBase;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 008 amendment A2, decision D7: the optional Principal confirmation a supervisor records on a day. */
class PrincipalConfirmationTest extends SubstitutionTestBase {

    private String url(UUID teacher, LocalDate date) {
        return "/api/v1/attendance/teachers/" + teacher + "/marks/" + date + "/principal-confirmation";
    }

    private Map<String, Object> body(boolean confirmed) {
        return Map.of("confirmed", confirmed, "principalName", "Mrs. Rani", "note", "Seen at assembly");
    }

    @Test
    void aManagerRecordsChangesAndClearsTheConfirmationWithoutTouchingTheMark() {
        World w = newWorld();
        UUID teacher = w.teacherA().teacherId();
        LocalDate day = monday();
        markAs(w.managerA().token(), teacher, day, "P", 1);

        Resp recorded = put(url(teacher, day), w.managerA().token(), body(true));

        assertThat(recorded.status()).as(recorded.body()).isEqualTo(200);
        assertThat(recorded.body()).contains("\"code\":\"P\"").contains("\"confirmed\":true").contains("Mrs. Rani");
        assertThat(recorded.body()).contains("\"recordedByName\"");

        Resp changed = put(url(teacher, day), w.managerA().token(), body(false));
        assertThat(changed.body()).contains("\"confirmed\":false");

        Resp cleared = delete(url(teacher, day), w.managerA().token());
        assertThat(cleared.status()).isEqualTo(200);
        assertThat(cleared.body()).contains("\"principalConfirmation\":null").contains("\"code\":\"P\"");
    }

    @Test
    void adminAndDirectorCanRecordItForAnyTeacherButAnotherManagerAndTheTeacherCannot() {
        World w = newWorld();
        UUID teacher = w.teacherA().teacherId();
        LocalDate day = monday();
        markAs(w.managerA().token(), teacher, day, "P", 1);

        assertThat(put(url(teacher, day), w.admin(), body(true)).status()).isEqualTo(200);
        assertThat(put(url(teacher, day), w.director(), body(true)).status()).isEqualTo(200);
        assertThat(put(url(teacher, day), w.managerB().token(), body(true)).status()).as("out of scope").isEqualTo(404);
        assertThat(put(url(teacher, day), w.teacherA().token(), body(true)).status()).as("Teacher").isEqualTo(403);
        assertThat(put(url(teacher, day), signInAs(com.hls.identity.user.Role.SYSTEM).token(), body(true)).status())
                .isEqualTo(403);
        assertThat(delete(url(teacher, day), w.teacherA().token()).status()).isEqualTo(403);
    }

    @Test
    void thereMustBeAMarkAndTheAnswerMustBeGiven() {
        World w = newWorld();
        UUID teacher = w.teacherA().teacherId();
        LocalDate day = monday();

        assertThat(put(url(teacher, day), w.managerA().token(), body(true)).status()).as("no mark").isEqualTo(404);
        markAs(w.managerA().token(), teacher, day, "P", 1);
        assertThat(put(url(teacher, day), w.managerA().token(), Map.of("principalName", "x")).status()).isEqualTo(400);
        assertThat(put(url(teacher, day), w.managerA().token(), Map.of("confirmed", true, "note", "n".repeat(501))).status())
                .isEqualTo(400);
    }

    @Test
    void aLockedMonthRefusesItAndAChangedStatusClearsTheOldConfirmation() {
        World w = newWorld();
        UUID teacher = w.teacherA().teacherId();
        LocalDate day = monday();
        markAs(w.managerA().token(), teacher, day, "P", 1);
        put(url(teacher, day), w.managerA().token(), body(true));

        markAs(w.managerA().token(), teacher, day, "A", 1);
        assertThat(jdbc.queryForObject(
                        "select principal_confirmed is null from attendance_mark where teacher_id = ? and mark_date = ?",
                        Boolean.class,
                        teacher,
                        day))
                .as("the status changed, so the confirmation of the old status is gone")
                .isTrue();

        lockMonth(teacher, YearMonth.from(day));
        Resp locked = put(url(teacher, day), w.managerA().token(), body(true));
        assertThat(locked.status()).isEqualTo(409);
        assertThat(locked.body()).contains("locked");
    }

    @Test
    void everyChangeIsAudited() {
        World w = newWorld();
        UUID teacher = w.teacherA().teacherId();
        LocalDate day = monday();
        markAs(w.managerA().token(), teacher, day, "P", 1);

        put(url(teacher, day), w.managerA().token(), body(true));

        assertChangeRecorded(w.admin(), "ATTENDANCE_MARK", teacher + ":" + day, "principalConfirmation");
    }
}
