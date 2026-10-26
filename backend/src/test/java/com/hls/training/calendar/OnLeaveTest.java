package com.hls.training.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 024 T042: a person on approved leave shows as on leave and is never put on the exceptions list. */
class OnLeaveTest extends TrainingCalendarTestBase {

    private int exceptions(UUID session, UUID person) {
        return jdbc.queryForObject(
                "select count(*) from training_exception where session_id = ? and person_id = ? and status <> 'WITHDRAWN'",
                Integer.class, session, person);
    }

    @Test
    @SuppressWarnings("unchecked")
    void aTeacherOnLeaveIsSuggestedAsOnLeaveAndRaisesNoException() {
        World w = newWorld();
        UUID session = publishedSession(w.admin(), "MONTHLY", today(), null);
        UUID away = placedTeacher(w.admin(), w.schoolA());
        UUID absent = placedTeacher(w.admin(), w.schoolA());
        assignRow(session, "TEACHER", away);
        assignRow(session, "TEACHER", absent);
        assertThat(put("/api/v1/attendance/teachers/" + away + "/marks/" + today(), w.admin(), Map.of("statusCode", "A", "dayValue", 1)).status())
                .isEqualTo(200);

        // before anything is saved the roster already suggests it
        List<Map<String, Object>> roster = list(get(API + "/sessions/" + session + "/attendance", w.admin()));
        assertThat(roster.stream().filter(r -> away.toString().equals(r.get("personId"))).findFirst().orElseThrow()).containsEntry("onLeave", true);
        assertThat(roster.stream().filter(r -> absent.toString().equals(r.get("personId"))).findFirst().orElseThrow()).containsEntry("onLeave", false);

        Resp saved = saveAttendance(
                w.admin(), session, row("TEACHER", away, "ABSENT", null, null), row("TEACHER", absent, "ABSENT", null, "No show"));
        assertThat(resultOf(saved, away)).isEqualTo("SAVED");
        assertThat(exceptions(session, away)).isZero();
        assertThat(exceptions(session, absent)).isEqualTo(1);
    }

    @Test
    void aManagerWithLeaveShowsAsOnLeaveAndRaisesNoException() {
        World w = newWorld();
        UUID session = publishedSession(w.admin(), "MONTHLY", today(), null);
        UUID manager = w.managerA().managerId();
        UUID other = w.managerB().managerId();
        assignRow(session, "MANAGER", manager);
        assignRow(session, "MANAGER", other);
        jdbc.update(
                "insert into manager_day (id, manager_id, day_date, leave_request_id, leave_fraction, leave_unpaid, version)"
                        + " values (?, ?, ?, ?, ?, false, 0)",
                UUID.randomUUID(), manager, today(), UUID.randomUUID(), new BigDecimal("1.0"));

        List<Map<String, Object>> roster = list(get(API + "/sessions/" + session + "/attendance", w.admin()));
        assertThat(roster.stream().filter(r -> manager.toString().equals(r.get("personId"))).findFirst().orElseThrow()).containsEntry("onLeave", true);

        Resp saved = saveAttendance(
                w.admin(), session, row("MANAGER", manager, "ABSENT", null, null), row("MANAGER", other, "ABSENT", null, "Unwell"));
        assertThat(resultOf(saved, manager)).isEqualTo("SAVED");
        assertThat(exceptions(session, manager)).isZero();
        assertThat(exceptions(session, other)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "select reason from training_attendance_entry where session_id = ? and person_id = ?", String.class, session, manager))
                .isEqualTo("On leave");
    }
}
