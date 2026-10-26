package com.hls.training.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 024 T049: non-compliance exceptions, from the absence that raises one to the way it is closed. */
class ExceptionTest extends TrainingCalendarTestBase {

    private int notificationsOfKind(String type) {
        return jdbc.queryForObject("select count(*) from notification where type = ?", Integer.class, type);
    }

    private String exceptionStatus(UUID session, UUID person) {
        List<String> found = jdbc.queryForList(
                "select status from training_exception where session_id = ? and person_id = ?", String.class, session, person);
        return found.isEmpty() ? null : found.get(0);
    }

    private UUID exceptionId(UUID session, UUID person) {
        return jdbc.queryForObject(
                "select id from training_exception where session_id = ? and person_id = ?", UUID.class, session, person);
    }

    private long exceptionVersion(String token, UUID id) {
        return ((Number) content(get(API + "/exceptions?status=OPEN&size=200", token)).stream()
                .filter(r -> id.toString().equals(r.get("id")))
                .findFirst()
                .orElseThrow()
                .get("version")).longValue();
    }

    private UUID absentAssigned(World w, UUID session, UUID teacher) {
        assignRow(session, "TEACHER", teacher);
        saveAttendance(w.admin(), session, row("TEACHER", teacher, "ABSENT", null, "Did not come"));
        return exceptionId(session, teacher);
    }

    @Test
    void anAbsentAssignedPersonRaisesOneOpenExceptionAndANoShowRaisesNone() {
        World w = newWorld();
        UUID session = publishedSession(w.admin(), "WEEKEND", today(), 10);
        UUID assigned = placedTeacher(w.admin(), w.schoolA());
        UUID noShow = placedTeacher(w.admin(), w.schoolA());
        assignRow(session, "TEACHER", assigned);
        registerRow(session, noShow);
        int before = notificationsOfKind("TRAINING_EXCEPTION");

        saveAttendance(w.admin(), session, row("TEACHER", assigned, "ABSENT", null, "Away"), row("TEACHER", noShow, "ABSENT", null, "Did not come"));
        assertThat(exceptionStatus(session, assigned)).isEqualTo("OPEN");
        assertThat(exceptionStatus(session, noShow)).isNull();
        assertThat(notificationsOfKind("TRAINING_EXCEPTION")).isGreaterThan(before);

        // saving the same thing again does not raise a second one
        saveAttendance(w.admin(), session, row("TEACHER", assigned, "ABSENT", null, "Away again"));
        assertThat(jdbc.queryForObject("select count(*) from training_exception where session_id = ?", Integer.class, session)).isEqualTo(1);
    }

    @Test
    void correctingTheAbsenceWithdrawsItAndCorrectingBackReopensTheSameRow() {
        World w = newWorld();
        UUID session = publishedSession(w.admin(), "MONTHLY", today(), null);
        UUID t = placedTeacher(w.admin(), w.schoolA());
        UUID id = absentAssigned(w, session, t);
        saveAttendance(w.admin(), session, row("TEACHER", t, "PRESENT", 1.0, null));
        assertThat(exceptionStatus(session, t)).isEqualTo("WITHDRAWN");
        saveAttendance(w.admin(), session, row("TEACHER", t, "ABSENT", null, "Actually absent"));
        assertThat(exceptionStatus(session, t)).isEqualTo("OPEN");
        assertThat(exceptionId(session, t)).isEqualTo(id);
    }

    @Test
    void closingAsExcusedNeedsANoteAndKeepsWhoAndWhen() {
        World w = newWorld();
        UUID session = publishedSession(w.admin(), "MONTHLY", today(), null);
        UUID t = placedTeacher(w.admin(), w.schoolA());
        UUID id = absentAssigned(w, session, t);
        long version = exceptionVersion(w.admin(), id);

        assertThat(post(API + "/exceptions/" + id + "/close", w.admin(), Map.of("kind", "EXCUSED", "note", " ", "version", version)).status()).isEqualTo(400);
        Resp closed = post(API + "/exceptions/" + id + "/close", w.director(), Map.of("kind", "EXCUSED", "note", "Medical", "version", version));
        assertThat(closed.status()).as(closed.body()).isEqualTo(200);
        assertThat(closed.map()).containsEntry("status", "CLOSED").containsEntry("closureKind", "EXCUSED");
        assertThat(closed.map().get("closedBy")).isNotNull();
        assertThat(closed.map().get("closedAt")).isNotNull();
        // it cannot be closed twice
        assertThat(post(API + "/exceptions/" + id + "/close", w.admin(), Map.of("kind", "ACKNOWLEDGED", "note", "x", "version", closed.map().get("version"))).status())
                .isEqualTo(409);
    }

    @Test
    void closingAsMadeUpNeedsALaterSessionWhereThePersonWasPresent() {
        World w = newWorld();
        UUID original = publishedSession(w.admin(), "MONTHLY", today(), null);
        UUID t = placedTeacher(w.admin(), w.schoolA());
        UUID id = absentAssigned(w, original, t);
        jdbc.update("update training_session set session_date = ? where id = ?", today().minusDays(10), original);
        UUID makeup = publishedSession(w.admin(), "MONTHLY", today(), null);
        long version = exceptionVersion(w.admin(), id);

        // nobody has recorded the person at the make-up session yet
        Map<String, Object> body = Map.of("kind", "MADE_UP", "makeupSessionId", makeup, "version", version);
        assertThat(post(API + "/exceptions/" + id + "/close", w.admin(), body).status()).isEqualTo(400);
        assertThat(post(API + "/exceptions/" + id + "/close", w.admin(), Map.of("kind", "MADE_UP", "version", version)).status()).isEqualTo(400);

        assignRow(makeup, "TEACHER", t);
        saveAttendance(w.admin(), makeup, row("TEACHER", t, "PRESENT", 1.0, null));
        Resp closed = post(API + "/exceptions/" + id + "/close", w.admin(), body);
        assertThat(closed.status()).as(closed.body()).isEqualTo(200);
        assertThat(closed.map()).containsEntry("closureKind", "MADE_UP");
    }

    @Test
    void acknowledgingNeedsANoteAndStillCountsTowardsTheRepeatMarker() {
        World w = newWorld();
        UUID t = placedTeacher(w.admin(), w.schoolA());
        UUID first = publishedSession(w.admin(), "MONTHLY", today(), null);
        UUID second = publishedSession(w.admin(), "MONTHLY", today(), null);
        UUID firstId = absentAssigned(w, first, t);
        absentAssigned(w, second, t);
        Resp closed = post(
                API + "/exceptions/" + firstId + "/close", w.admin(),
                Map.of("kind", "ACKNOWLEDGED", "note", "Spoken to", "version", exceptionVersion(w.admin(), firstId)));
        assertThat(closed.status()).as(closed.body()).isEqualTo(200);

        List<Map<String, Object>> rows = content(get(API + "/exceptions?status=&size=200", w.admin())).stream()
                .filter(r -> t.toString().equals(r.get("personId")))
                .toList();
        assertThat(rows).hasSize(2).allSatisfy(r -> assertThat(r).containsEntry("repeat", true));
    }

    @Test
    void aManagerSeesOnlyOwnTeachersAndCannotCloseWhileATeacherIsRefused() {
        World w = newWorld();
        UUID session = publishedSession(w.admin(), "MONTHLY", today(), null);
        UUID ownId = absentAssigned(w, session, w.teacherA().teacherId());
        UUID otherId = absentAssigned(w, session, w.teacherB().teacherId());

        List<Map<String, Object>> seen = content(get(API + "/exceptions?size=200", w.managerA().token()));
        assertThat(seen).extracting(r -> r.get("id")).contains(ownId.toString()).doesNotContain(otherId.toString());
        assertThat(post(API + "/exceptions/" + ownId + "/close", w.managerA().token(), Map.of("kind", "EXCUSED", "note", "x", "version", 0)).status())
                .isEqualTo(403);
        assertThat(get(API + "/exceptions", w.teacherA().token()).status()).isEqualTo(403);
        assertThat(get(API + "/exceptions/export", w.managerA().token()).status()).isEqualTo(403);
    }

    @Test
    void filtersByMonthManagerRoleAndStatusAndExportsForAdminAndDirector() {
        World w = newWorld();
        UUID session = publishedSession(w.admin(), "MONTHLY", today(), null);
        UUID a = absentAssigned(w, session, w.teacherA().teacherId());
        UUID b = absentAssigned(w, session, w.teacherB().teacherId());
        String month = today().toString().substring(0, 7);

        List<Map<String, Object>> byManager = content(get(API + "/exceptions?managerId=" + w.managerA().managerId() + "&month=" + month + "&size=200", w.admin()));
        assertThat(byManager).extracting(r -> r.get("id")).contains(a.toString()).doesNotContain(b.toString());
        List<Map<String, Object>> byZone = content(get(API + "/exceptions?zoneId=" + w.zoneB() + "&size=200", w.director()));
        assertThat(byZone).extracting(r -> r.get("id")).contains(b.toString()).doesNotContain(a.toString());
        assertThat(content(get(API + "/exceptions?role=MANAGER&size=200", w.admin())).stream().filter(r -> a.toString().equals(r.get("id")))).isEmpty();
        assertThat(content(get(API + "/exceptions?status=CLOSED&size=200", w.admin())).stream().filter(r -> a.toString().equals(r.get("id")))).isEmpty();
        assertThat(content(get(API + "/exceptions?month=1999-01&size=200", w.admin()))).isEmpty();

        Resp csv = get(API + "/exceptions/export?month=" + month, w.admin());
        assertThat(csv.status()).isEqualTo(200);
        assertThat(csv.body()).startsWith("Person,Role,Session");
        assertThat(get(API + "/exceptions/export", w.director()).status()).isEqualTo(200);
    }
}
