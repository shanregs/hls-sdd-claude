package com.hls.training.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 024 T040 (SC-003, SC-004, SC-008): expected versus attended, and the Teachers' days in attendance. */
class AttendanceRecordTest extends TrainingCalendarTestBase {

    private Map<String, Object> counts(String token, UUID session) {
        return (Map<String, Object>) session(token, session).map().get("counts");
    }

    private int n(Map<String, Object> counts, String key) {
        return ((Number) counts.get(key)).intValue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void aPreparedSessionGivesTheHandCountedFigures() {
        World w = newWorld();
        String admin = w.admin();
        UUID session = publishedSession(admin, "WEEKEND", today(), 20);
        UUID a1 = placedTeacher(admin, w.schoolA());
        UUID a2 = placedTeacher(admin, w.schoolA());
        UUID a3 = placedTeacher(admin, w.schoolA()); // on leave
        UUID a4 = placedTeacher(admin, w.schoolA()); // excused
        UUID a5 = placedTeacher(admin, w.schoolA()); // absent
        UUID a6 = placedTeacher(admin, w.schoolA());
        UUID r1 = placedTeacher(admin, w.schoolA()); // registered, came
        UUID r2 = placedTeacher(admin, w.schoolA()); // registered, did not come
        UUID walk = placedTeacher(admin, w.schoolA());
        for (UUID a : List.of(a1, a2, a3, a4, a5, a6)) {
            assignRow(session, "TEACHER", a);
        }
        registerRow(session, r1);
        registerRow(session, r2);
        assertThat(put("/api/v1/attendance/teachers/" + a3 + "/marks/" + today(), admin, Map.of("statusCode", "A", "dayValue", 1)).status())
                .isEqualTo(200);

        Resp saved = saveAttendance(
                admin, session,
                row("TEACHER", a1, "PRESENT", 1.0, null),
                row("TEACHER", a2, "PRESENT", 0.5, null),
                row("TEACHER", a3, "ABSENT", null, null),
                row("TEACHER", a4, "EXCUSED", null, "Medical certificate"),
                row("TEACHER", a5, "ABSENT", null, "No reason given"),
                row("TEACHER", a6, "PRESENT", 1.0, null),
                row("TEACHER", r1, "PRESENT", 1.0, null),
                row("TEACHER", r2, "ABSENT", null, "Did not come"));
        assertThat(saved.status()).as(saved.body()).isEqualTo(200);
        Resp walkIn = post(API + "/sessions/" + session + "/walk-ins", admin, Map.of("personKind", "TEACHER", "personId", walk, "dayValue", 1.0));
        assertThat(walkIn.status()).as(walkIn.body()).isEqualTo(200);

        Map<String, Object> c = counts(admin, session);
        assertThat(n(c, "expected")).isEqualTo(8);
        assertThat(n(c, "attended")).isEqualTo(4);
        assertThat(n(c, "absent")).isEqualTo(3);
        assertThat(n(c, "excused")).isEqualTo(1);
        assertThat(n(c, "walkIns")).isEqualTo(1);
        assertThat(n(c, "noShows")).isEqualTo(1);
        assertThat(n(c, "onLeave")).isEqualTo(1);
        assertThat(session(admin, session).map()).containsEntry("status", "HELD");

        // the training days are in attendance with the right values
        assertThat(trainingDay(a1, today())).isEqualByComparingTo("1.00");
        assertThat(trainingDay(a2, today())).isEqualByComparingTo("0.50");
        assertThat(trainingDay(walk, today())).isEqualByComparingTo("1.00");
        assertThat(trainingDay(a5, today())).isNull();
    }

    @Test
    void aCorrectionIsInBothHistories() {
        World w = newWorld();
        UUID session = publishedSession(w.admin(), "MONTHLY", today(), null);
        UUID t = placedTeacher(w.admin(), w.schoolA());
        assignRow(session, "TEACHER", t);
        saveAttendance(w.admin(), session, row("TEACHER", t, "PRESENT", 1.0, null));
        assertThat(trainingDay(t, today())).isEqualByComparingTo("1.00");

        // corrected to half a day, then to absent
        assertThat(resultOf(saveAttendance(w.admin(), session, row("TEACHER", t, "PRESENT", 0.5, null)), t)).isEqualTo("SAVED");
        assertThat(trainingDay(t, today())).isEqualByComparingTo("0.50");
        assertThat(resultOf(saveAttendance(w.admin(), session, row("TEACHER", t, "ABSENT", null, "Left early")), t)).isEqualTo("SAVED");
        assertThat(trainingDay(t, today())).isNull();

        assertThat(jdbc.queryForObject(
                        "select count(*) from training_attendance_history h join training_attendance_entry e on e.id = h.entry_id"
                                + " where e.session_id = ? and e.person_id = ?",
                        Integer.class, session, t))
                .isEqualTo(3);
        assertThat(jdbc.queryForObject(
                        "select count(*) from attendance_mark_history where teacher_id = ? and mark_date = ?", Integer.class, t, today()))
                .isGreaterThanOrEqualTo(2);
    }

    @Test
    void aSchoolMarkIsAClashAndTheSyncWritesTheDayOnceItIsGone() {
        World w = newWorld();
        UUID session = publishedSession(w.admin(), "MONTHLY", today(), null);
        UUID t = placedTeacher(w.admin(), w.schoolA());
        assignRow(session, "TEACHER", t);
        assertThat(put("/api/v1/attendance/teachers/" + t + "/marks/" + today(), w.admin(), Map.of("statusCode", "P", "dayValue", 1)).status())
                .isEqualTo(200);

        Resp saved = saveAttendance(w.admin(), session, row("TEACHER", t, "PRESENT", 1.0, null));
        assertThat(resultOf(saved, t)).isEqualTo("SAVED_CLASH:CLASH");
        assertThat(jdbc.queryForObject(
                        "select sync_state from training_attendance_entry where session_id = ? and person_id = ?", String.class, session, t))
                .isEqualTo("CLASH");
        assertThat(trainingDay(t, today())).isNull();
        UUID entry = jdbc.queryForObject(
                "select id from training_attendance_entry where session_id = ? and person_id = ?", UUID.class, session, t);

        // still a clash while the school mark is there
        Resp again = post(API + "/sessions/" + session + "/attendance/" + entry + "/sync", w.admin(), Map.of());
        assertThat(resultOf(again, t)).isEqualTo("SAVED_CLASH:CLASH");

        // the school mark is removed, then the sync writes the day
        jdbc.update("delete from attendance_mark where teacher_id = ? and mark_date = ?", t, today());
        Resp fixed = post(API + "/sessions/" + session + "/attendance/" + entry + "/sync", w.admin(), Map.of());
        assertThat(resultOf(fixed, t)).isEqualTo("SAVED");
        assertThat(trainingDay(t, today())).isEqualByComparingTo("1.00");
    }

    @Test
    void aLockedMonthRefusesOnlyThatRow() {
        World w = newWorld();
        UUID session = publishedSession(w.admin(), "MONTHLY", today(), null);
        UUID locked = placedTeacher(w.admin(), w.schoolA());
        UUID open = placedTeacher(w.admin(), w.schoolA());
        assignRow(session, "TEACHER", locked);
        assignRow(session, "TEACHER", open);
        lockMonth(locked, today());

        Resp saved = saveAttendance(w.admin(), session, row("TEACHER", locked, "PRESENT", 1.0, null), row("TEACHER", open, "PRESENT", 1.0, null));
        assertThat(saved.status()).as(saved.body()).isEqualTo(200);
        assertThat(resultOf(saved, locked)).isEqualTo("REFUSED:MONTH_LOCKED");
        assertThat(resultOf(saved, open)).isEqualTo("SAVED");
        assertThat(jdbc.queryForObject("select count(*) from training_attendance_entry where session_id = ?", Integer.class, session)).isEqualTo(1);
        assertThat(trainingDay(open, today())).isEqualByComparingTo("1.00");
    }

    @Test
    void aHundredRowsSaveInUnderThreeSeconds() {
        World w = newWorld();
        UUID session = publishedSession(w.admin(), "MONTHLY", today(), null);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            UUID t = placedTeacher(w.admin(), w.schoolA());
            assignRow(session, "TEACHER", t);
            rows.add(row("TEACHER", t, "PRESENT", 1.0, null));
        }
        Instant start = Instant.now();
        Resp saved = put(API + "/sessions/" + session + "/attendance", w.admin(), Map.of("rows", rows));
        Duration took = Duration.between(start, Instant.now());
        assertThat(saved.status()).as(saved.body()).isEqualTo(200);
        assertThat(took).isLessThan(Duration.ofSeconds(3));
        assertThat(jdbc.queryForObject("select count(*) from training_attendance_entry where session_id = ?", Integer.class, session)).isEqualTo(100);
    }

    @Test
    void aFutureSessionAndAMissingReasonAreRefused() {
        World w = newWorld();
        UUID future = publishedSession(w.admin(), "MONTHLY", in(5), null);
        UUID t = placedTeacher(w.admin(), w.schoolA());
        assignRow(future, "TEACHER", t);
        assertThat(saveAttendance(w.admin(), future, row("TEACHER", t, "PRESENT", 1.0, null)).status()).isEqualTo(409);

        UUID today = publishedSession(w.admin(), "MONTHLY", today(), null);
        assignRow(today, "TEACHER", t);
        Resp noReason = saveAttendance(w.admin(), today, row("TEACHER", t, "ABSENT", null, null));
        assertThat(resultOf(noReason, t)).isEqualTo("REFUSED:REASON_REQUIRED");
    }

    @Test
    void aPersonNotOnTheRosterIsRefusedAndAWalkInIsAllowed() {
        World w = newWorld();
        UUID session = publishedSession(w.admin(), "MONTHLY", today(), null);
        UUID stranger = placedTeacher(w.admin(), w.schoolA());
        Resp refused = saveAttendance(w.admin(), session, row("TEACHER", stranger, "PRESENT", 1.0, null));
        assertThat(resultOf(refused, stranger)).isEqualTo("REFUSED:NOT_ON_ROSTER");
        Resp walk = post(API + "/sessions/" + session + "/walk-ins", w.admin(), Map.of("personKind", "TEACHER", "personId", stranger, "dayValue", 0.5));
        assertThat(resultOf(walk, stranger)).isEqualTo("SAVED");
        assertThat(trainingDay(stranger, today())).isEqualByComparingTo(new BigDecimal("0.50"));
    }
}
