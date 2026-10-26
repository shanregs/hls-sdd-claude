package com.hls.training.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 024 T053: a person's training history, the yearly figure and who may read it. */
class HistoryTest extends TrainingCalendarTestBase {

    private LocalDate onJanuary(int day) {
        return LocalDate.of(today().getYear(), 1, day);
    }

    /** A session recorded today for the person, then dated back to the given day. */
    private UUID held(World w, String type, UUID teacher, boolean assigned, LocalDate date, Map<String, Object> row) {
        UUID session = publishedSession(w.admin(), type, today(), "WEEKEND".equals(type) ? 10 : null);
        if (assigned) {
            assignRow(session, "TEACHER", teacher);
        } else {
            registerRow(session, teacher);
        }
        assertThat(saveAttendance(w.admin(), session, row).status()).isEqualTo(200);
        jdbc.update("update training_session set session_date = ? where id = ?", date, session);
        return session;
    }

    private void induction(UUID teacher) {
        UUID batch = UUID.randomUUID();
        jdbc.update(
                "insert into induction_batch (id, name, starts_on, ends_on, trainer, venue_type, venue, seat_limit, status, version, created_at)"
                        + " values (?, 'Induction 7', ?, ?, 'Trainer', 'PHYSICAL', 'HQ', 20, 'COMPLETED', 0, now())",
                batch, LocalDate.of(2020, 3, 2), LocalDate.of(2020, 3, 13));
        jdbc.update(
                "insert into induction_enrolment (id, batch_id, teacher_id, starts_on, ends_on, enrolled_by, enrolled_at, result, signed_at, version)"
                        + " values (?, ?, ?, ?, ?, ?, now(), 'COMPLETED', now(), 0)",
                UUID.randomUUID(), batch, teacher, LocalDate.of(2020, 3, 2), LocalDate.of(2020, 3, 13), UUID.randomUUID());
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> entries(Resp resp) {
        return (List<Map<String, Object>>) resp.map().get("entries");
    }

    @Test
    @SuppressWarnings("unchecked")
    void eachSessionAppearsOnceNewestFirstWithTheInductionAndTheYearlyFigure() {
        World w = newWorld();
        UUID t = w.teacherA().teacherId();
        induction(t);
        UUID m1 = held(w, "MONTHLY", t, true, onJanuary(5), row("TEACHER", t, "PRESENT", 1.0, null));
        UUID m2 = held(w, "MONTHLY", t, true, onJanuary(12), row("TEACHER", t, "PRESENT", 0.5, null));
        Map<String, Object> excused = row("TEACHER", t, "EXCUSED", null, "Medical");
        excused.put("certificateNote", "Doctor note seen");
        excused.put("internalRemark", "Second time this year");
        UUID m3 = held(w, "MONTHLY", t, true, onJanuary(19), excused);
        UUID weekend = held(w, "WEEKEND", t, false, onJanuary(26), row("TEACHER", t, "PRESENT", 1.0, null));

        Resp resp = get(API + "/people/TEACHER/" + t + "/history?year=" + today().getYear(), w.admin());
        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        List<Map<String, Object>> list = entries(resp);
        assertThat(list).extracting(e -> e.get("sessionId"))
                .containsExactly(weekend.toString(), m3.toString(), m2.toString(), m1.toString());
        assertThat(list.get(1)).containsEntry("outcome", "EXCUSED").containsEntry("internalRemark", "Second time this year");
        assertThat(list.get(0)).containsEntry("role", "REGISTERED");
        List<Map<String, Object>> induction = (List<Map<String, Object>>) resp.map().get("induction");
        assertThat(induction).hasSize(1);
        assertThat(induction.get(0)).containsEntry("batchName", "Induction 7").containsEntry("result", "COMPLETED");
        // three required sessions held; 1 + 0.5 attended; the optional weekend is not in the expected figure
        Map<String, Object> yearly = (Map<String, Object>) resp.map().get("yearly");
        assertThat(((Number) yearly.get("expected")).doubleValue()).isEqualTo(3.0);
        assertThat(((Number) yearly.get("attended")).doubleValue()).isEqualTo(1.5);
    }

    @Test
    void theTeacherSeesTheCertificateNoteButNeverTheRemarkOrTheException() {
        World w = newWorld();
        UUID t = w.teacherA().teacherId();
        Map<String, Object> absent = row("TEACHER", t, "ABSENT", null, "Did not come");
        absent.put("certificateNote", "Please attend next time");
        absent.put("internalRemark", "Watch this one");
        held(w, "MONTHLY", t, true, onJanuary(5), absent);

        Resp own = get(API + "/me/history?year=" + today().getYear(), w.teacherA().token());
        assertThat(own.status()).as(own.body()).isEqualTo(200);
        assertThat(own.body()).contains("Please attend next time").doesNotContain("Watch this one");
        Map<String, Object> entry = entries(own).get(0);
        assertThat(entry.get("internalRemark")).isNull();
        assertThat(entry.get("exceptionStatus")).isNull();
        // staff do see the exception
        Resp staff = get(API + "/people/TEACHER/" + t + "/history?year=" + today().getYear(), w.admin());
        assertThat(entries(staff).get(0)).containsEntry("exceptionStatus", "OPEN").containsEntry("internalRemark", "Watch this one");
    }

    @Test
    void aManagerReachesOwnTeachersAndSelfButNotOthers() {
        World w = newWorld();
        String token = w.managerA().token();
        assertThat(get(API + "/people/TEACHER/" + w.teacherA().teacherId() + "/history", token).status()).isEqualTo(200);
        assertThat(get(API + "/people/TEACHER/" + w.teacherB().teacherId() + "/history", token).status()).isEqualTo(404);
        assertThat(get(API + "/people/MANAGER/" + w.managerA().managerId() + "/history", token).status()).isEqualTo(200);
        assertThat(get(API + "/people/MANAGER/" + w.managerB().managerId() + "/history", token).status()).isEqualTo(404);
        assertThat(get(API + "/me/history", token).status()).isEqualTo(200);
        assertThat(get(API + "/people/TEACHER/" + w.teacherA().teacherId() + "/history", w.teacherA().token()).status()).isEqualTo(403);
    }

    @Test
    void aPersonWithNoTrainingGetsAnEmptyHistoryNotAnError() {
        World w = newWorld();
        Resp resp = get(API + "/people/TEACHER/" + w.teacherB().teacherId() + "/history", w.admin());
        assertThat(resp.status()).isEqualTo(200);
        assertThat(entries(resp)).isEmpty();
    }

    @Test
    void onlyAdminAndDirectorExport() {
        World w = newWorld();
        String path = API + "/people/TEACHER/" + w.teacherA().teacherId() + "/history/export";
        Resp csv = get(path, w.admin());
        assertThat(csv.status()).isEqualTo(200);
        assertThat(csv.body()).startsWith("Date,Topic");
        assertThat(get(path, w.director()).status()).isEqualTo(200);
        assertThat(get(path, w.managerA().token()).status()).isEqualTo(403);
        assertThat(get(path, w.teacherA().token()).status()).isEqualTo(403);
    }
}
