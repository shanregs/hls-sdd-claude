package com.hls.training.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 024 T041 (SC-006): who may record attendance for whom, and what a Teacher never sees. */
class AttendanceScopeTest extends TrainingCalendarTestBase {

    @Test
    void aManagerRecordsOnlyForTheirOwnTeachers() {
        World w = newWorld();
        UUID session = publishedSession(w.admin(), "MONTHLY", today(), null);
        assignRow(session, "TEACHER", w.teacherA().teacherId());
        assignRow(session, "TEACHER", w.teacherB().teacherId());
        assignRow(session, "MANAGER", w.managerA().managerId());
        assignRow(session, "MANAGER", w.managerB().managerId());
        String token = w.managerA().token();

        Resp saved = saveAttendance(
                token, session,
                row("TEACHER", w.teacherA().teacherId(), "PRESENT", 1.0, null),
                row("TEACHER", w.teacherB().teacherId(), "PRESENT", 1.0, null),
                row("MANAGER", w.managerA().managerId(), "PRESENT", 1.0, null),
                row("MANAGER", w.managerB().managerId(), "PRESENT", 1.0, null));
        assertThat(saved.status()).as(saved.body()).isEqualTo(200);
        assertThat(resultOf(saved, w.teacherA().teacherId())).isEqualTo("SAVED");
        assertThat(resultOf(saved, w.teacherB().teacherId())).isEqualTo("REFUSED:NOT_IN_SCOPE");
        assertThat(resultOf(saved, w.managerA().managerId())).isEqualTo("REFUSED:NOT_IN_SCOPE"); // not even themselves
        assertThat(resultOf(saved, w.managerB().managerId())).isEqualTo("REFUSED:NOT_IN_SCOPE");
        assertThat(jdbc.queryForObject("select count(*) from training_attendance_entry where session_id = ?", Integer.class, session)).isEqualTo(1);
    }

    @Test
    void aManagerSeesOnlyTheirTeachersOnTheRoster() {
        World w = newWorld();
        UUID session = publishedSession(w.admin(), "MONTHLY", today(), null);
        assignRow(session, "TEACHER", w.teacherA().teacherId());
        assignRow(session, "TEACHER", w.teacherB().teacherId());
        assignRow(session, "MANAGER", w.managerA().managerId());
        List<Map<String, Object>> roster = list(get(API + "/sessions/" + session + "/attendance", w.managerA().token()));
        assertThat(roster).extracting(r -> r.get("personId")).containsExactly(w.teacherA().teacherId().toString());
        List<Map<String, Object>> all = list(get(API + "/sessions/" + session + "/attendance", w.admin()));
        assertThat(all).hasSize(3);
    }

    @Test
    void adminAndDirectorRecordForAnyoneIncludingManagers() {
        World w = newWorld();
        UUID session = publishedSession(w.admin(), "MONTHLY", today(), null);
        assignRow(session, "TEACHER", w.teacherB().teacherId());
        assignRow(session, "MANAGER", w.managerB().managerId());
        Resp admin = saveAttendance(
                w.admin(), session,
                row("TEACHER", w.teacherB().teacherId(), "PRESENT", 1.0, null),
                row("MANAGER", w.managerB().managerId(), "PRESENT", 1.0, null));
        assertThat(resultOf(admin, w.teacherB().teacherId())).isEqualTo("SAVED");
        assertThat(resultOf(admin, w.managerB().managerId())).isEqualTo("SAVED");
        Resp director = saveAttendance(w.director(), session, row("MANAGER", w.managerB().managerId(), "ABSENT", null, "Away"));
        assertThat(resultOf(director, w.managerB().managerId())).isEqualTo("SAVED");
        // a Manager's row is never written to Teacher attendance
        assertThat(jdbc.queryForObject(
                        "select sync_state from training_attendance_entry where session_id = ? and person_id = ?",
                        String.class, session, w.managerB().managerId()))
                .isEqualTo("NOT_APPLICABLE");
    }

    @Test
    void aTeacherCannotRecordOrReadAttendanceAndNeverSeesInternalRemarks() {
        World w = newWorld();
        UUID session = publishedSession(w.admin(), "MONTHLY", today(), null);
        assignRow(session, "TEACHER", w.teacherA().teacherId());
        Map<String, Object> withRemark = row("TEACHER", w.teacherA().teacherId(), "ABSENT", null, "Sick");
        withRemark.put("internalRemark", "Third time this term");
        withRemark.put("certificateNote", "Doctor's note seen");
        assertThat(resultOf(saveAttendance(w.admin(), session, withRemark), w.teacherA().teacherId())).isEqualTo("SAVED");

        String teacher = w.teacherA().token();
        assertThat(saveAttendance(teacher, session, row("TEACHER", w.teacherA().teacherId(), "PRESENT", 1.0, null)).status()).isEqualTo(403);
        assertThat(get(API + "/sessions/" + session + "/attendance", teacher).status()).isEqualTo(403);
        Resp detail = session(teacher, session);
        assertThat(detail.status()).isEqualTo(200);
        assertThat(detail.body()).doesNotContain("Third time this term").doesNotContain("internalRemark");
        // the staff roster does carry the remark
        assertThat(get(API + "/sessions/" + session + "/attendance", w.admin()).body()).contains("Third time this term");
    }
}
