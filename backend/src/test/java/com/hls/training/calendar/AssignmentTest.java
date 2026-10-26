package com.hls.training.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 024 T033 (SC-002): assignments are one obligation per person, notified once, removable until the session is held. */
class AssignmentTest extends TrainingCalendarTestBase {

    private Map<String, Object> group(String kind, UUID ref) {
        Map<String, Object> body = new HashMap<>();
        body.put("groupKind", kind);
        body.put("groupRef", ref);
        body.put("confirm", true);
        return body;
    }

    private Map<String, Object> individuals(String kind, boolean confirm, UUID... ids) {
        Map<String, Object> body = new HashMap<>();
        body.put("personKind", kind);
        body.put("ids", List.of(ids));
        body.put("confirm", confirm);
        return body;
    }

    private int required(UUID session) {
        return jdbc.queryForObject(
                "select count(*) from training_assignment where session_id = ? and status = 'REQUIRED'", Integer.class, session);
    }

    private int rowsFor(UUID session, UUID person) {
        return jdbc.queryForObject(
                "select count(*) from training_assignment where session_id = ? and person_id = ?", Integer.class, session, person);
    }

    @Test
    void aHundredPeopleAreAssignedThroughAGroupInUnderAMinute() {
        World w = newWorld();
        int extra = 99;
        for (int i = 0; i < extra; i++) {
            UUID t = teacher(w.admin());
            assertThat(placeTeacherRaw(w.admin(), t, w.schoolA(), today().minusDays(30)).status()).isEqualTo(200);
        }
        UUID session = publishedSession(w.admin(), "MONTHLY", in(10), null);

        Resp preview = post(API + "/sessions/" + session + "/assignments/preview", w.admin(), group("SCHOOL", w.schoolA()));
        assertThat(preview.status()).as(preview.body()).isEqualTo(200);
        assertThat(((Number) preview.map().get("newCount")).intValue()).isEqualTo(extra + 1);

        Instant start = Instant.now();
        Resp done = post(API + "/sessions/" + session + "/assignments", w.admin(), group("SCHOOL", w.schoolA()));
        assertThat(Duration.between(start, Instant.now())).isLessThan(Duration.ofMinutes(1));
        assertThat(done.status()).as(done.body()).isEqualTo(201);
        assertThat(required(session)).isEqualTo(extra + 1);
        assertThat(notifications(w.teacherA().signed().userId(), "TRAINING_ASSIGNED")).isEqualTo(1);

        // assigning the same group again adds nobody and notifies nobody
        Resp again = post(API + "/sessions/" + session + "/assignments", w.admin(), group("SCHOOL", w.schoolA()));
        assertThat(((Number) again.map().get("assigned")).intValue()).isZero();
        assertThat(required(session)).isEqualTo(extra + 1);
        assertThat(notifications(w.teacherA().signed().userId(), "TRAINING_ASSIGNED")).isEqualTo(1);
    }

    @Test
    void assigningIndividuallyAndThroughAGroupKeepsOneRow() {
        World w = newWorld();
        UUID session = publishedSession(w.admin(), "MONTHLY", in(10), null);
        Resp one = post(API + "/sessions/" + session + "/assignments", w.admin(), individuals("TEACHER", true, w.teacherA().teacherId()));
        assertThat(one.status()).as(one.body()).isEqualTo(201);
        post(API + "/sessions/" + session + "/assignments", w.admin(), group("SCHOOL", w.schoolA()));
        assertThat(rowsFor(session, w.teacherA().teacherId())).isEqualTo(1);
        assertThat(notifications(w.teacherA().signed().userId(), "TRAINING_ASSIGNED")).isEqualTo(1);
    }

    @Test
    void thePreviewShowsRefusalsAndAssigningNeedsConfirmation() {
        World w = newWorld();
        UUID inTraining = teacher(w.admin());
        setTeacherStatus(inTraining, "IN_TRAINING");
        UUID session = publishedSession(w.admin(), "MONTHLY", in(10), null);
        Resp preview = post(
                API + "/sessions/" + session + "/assignments/preview", w.admin(),
                individuals("TEACHER", false, w.teacherA().teacherId(), inTraining));
        assertThat(((Number) preview.map().get("newCount")).intValue()).isEqualTo(1);
        assertThat(((Number) preview.map().get("refusedCount")).intValue()).isEqualTo(1);
        // no confirmation, no assignment
        Resp unconfirmed = post(
                API + "/sessions/" + session + "/assignments", w.admin(),
                individuals("TEACHER", false, w.teacherA().teacherId()));
        assertThat(unconfirmed.status()).isEqualTo(400);
        assertThat(required(session)).isZero();
        Resp confirmed = post(
                API + "/sessions/" + session + "/assignments", w.admin(),
                individuals("TEACHER", true, w.teacherA().teacherId(), inTraining));
        assertThat(((Number) confirmed.map().get("refused")).intValue()).isEqualTo(1);
        assertThat(rowsFor(session, inTraining)).isZero();
    }

    @Test
    void removalBeforeHeldNotifiesAndAfterHeldIsRefused() {
        World w = newWorld();
        UUID session = publishedSession(w.admin(), "MONTHLY", in(10), null);
        post(API + "/sessions/" + session + "/assignments", w.admin(), individuals("TEACHER", true, w.teacherA().teacherId(), w.teacherB().teacherId()));
        UUID rowA = assignmentIdOf(w.admin(), session, w.teacherA().teacherId());
        UUID rowB = assignmentIdOf(w.admin(), session, w.teacherB().teacherId());

        assertThat(send(org.springframework.http.HttpMethod.DELETE, API + "/sessions/" + session + "/assignments/" + rowA, w.admin(), Map.of("reason", "")).status()).isEqualTo(400);
        Resp removed = send(org.springframework.http.HttpMethod.DELETE, API + "/sessions/" + session + "/assignments/" + rowA, w.admin(), Map.of("reason", "Clash with exams"));
        assertThat(removed.status()).as(removed.body()).isEqualTo(204);
        assertThat(jdbc.queryForObject("select status from training_assignment where id = ?", String.class, rowA)).isEqualTo("REMOVED");
        assertThat(notifications(w.teacherA().signed().userId(), "TRAINING_ASSIGNMENT_REMOVED")).isEqualTo(1);

        jdbc.update("update training_session set status = 'HELD' where id = ?", session);
        Resp late = send(org.springframework.http.HttpMethod.DELETE, API + "/sessions/" + session + "/assignments/" + rowB, w.admin(), Map.of("reason", "Too late"));
        assertThat(late.status()).isEqualTo(409);
        assertThat(jdbc.queryForObject("select status from training_assignment where id = ?", String.class, rowB)).isEqualTo("REQUIRED");
    }

    @Test
    void aWeekendSessionMakesOnlyTheAssignedPeopleRequired() {
        World w = newWorld();
        UUID session = publishedSession(w.admin(), "WEEKEND", in(10), 5);
        post(API + "/sessions/" + session + "/assignments", w.admin(), individuals("TEACHER", true, w.teacherA().teacherId()));
        assertThat(session(w.teacherA().token(), session).map()).extracting("my").asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("status", "REQUIRED");
        Map<String, Object> mineB = (Map<String, Object>) session(w.teacherB().token(), session).map().get("my");
        assertThat(mineB.get("status")).isNull();
        assertThat(mineB.get("open")).isEqualTo(true);
    }

    @Test
    void aCannotAttendNoteNotifiesStaffAndChangesNothing() {
        World w = newWorld();
        UUID session = publishedSession(w.admin(), "MONTHLY", in(10), null);
        post(API + "/sessions/" + session + "/assignments", w.admin(), individuals("TEACHER", true, w.teacherA().teacherId()));
        int before = jdbc.queryForObject("select count(*) from notification where type = 'TRAINING_CANNOT_ATTEND'", Integer.class);

        Resp note = post(API + "/sessions/" + session + "/assignments/me/cannot-attend", w.teacherA().token(), Map.of("note", "Family function"));
        assertThat(note.status()).as(note.body()).isEqualTo(204);
        int after = jdbc.queryForObject("select count(*) from notification where type = 'TRAINING_CANNOT_ATTEND'", Integer.class);
        assertThat(after).isGreaterThan(before);
        assertThat(jdbc.queryForObject(
                        "select status || ':' || cannot_attend_note from training_assignment where session_id = ? and person_id = ?",
                        String.class, session, w.teacherA().teacherId()))
                .isEqualTo("REQUIRED:Family function");
        // someone who is not assigned has nothing to decline
        assertThat(post(API + "/sessions/" + session + "/assignments/me/cannot-attend", w.teacherB().token(), Map.of("note", "x")).status())
                .isEqualTo(404);
    }

    @Test
    void onlyAdminAndDirectorAssign() {
        World w = newWorld();
        UUID session = publishedSession(w.admin(), "MONTHLY", in(10), null);
        Map<String, Object> body = individuals("TEACHER", true, w.teacherA().teacherId());
        assertThat(post(API + "/sessions/" + session + "/assignments", w.managerA().token(), body).status()).isEqualTo(403);
        assertThat(post(API + "/sessions/" + session + "/assignments", w.teacherA().token(), body).status()).isEqualTo(403);
        assertThat(post(API + "/sessions/" + session + "/assignments", w.director(), body).status()).isEqualTo(201);
    }

    private UUID assignmentIdOf(String token, UUID session, UUID person) {
        return list(get(API + "/sessions/" + session + "/assignments", token)).stream()
                .filter(r -> person.toString().equals(r.get("personId")))
                .map(r -> UUID.fromString((String) r.get("id")))
                .findFirst()
                .orElseThrow();
    }
}
