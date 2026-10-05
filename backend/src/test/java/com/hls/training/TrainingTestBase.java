package com.hls.training;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.recruitment.RecruitmentTestBase;
import com.hls.teacher.api.TeacherRegistry;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;

/** Helpers for the induction tests: batches, recruits who accepted an offer, and a clean slate of open batches. */
public abstract class TrainingTestBase extends RecruitmentTestBase {

    protected static final String IND = "/api/v1/induction";

    @Autowired
    protected TeacherRegistry registry;

    /** The database is shared by every test, so earlier tests' batches must not catch this test's recruits. */
    @BeforeEach
    void closeEarlierBatches() {
        jdbc.update("update induction_batch set status = 'CANCELLED' where status <> 'CANCELLED'");
    }

    protected Map<String, Object> batchBody(String name, LocalDate startsOn, LocalDate endsOn, int seats) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("startsOn", startsOn.toString());
        body.put("endsOn", endsOn.toString());
        body.put("trainer", "Trainer T");
        body.put("venueType", "PHYSICAL");
        body.put("venue", "Head office");
        body.put("seatLimit", seats);
        return body;
    }

    /** A batch that started a few days ago and runs for a few more, so attendance and sign-off are possible. */
    protected UUID runningBatch(String token, int seats) {
        Resp resp = post(IND + "/batches", token, batchBody(uniqueName("Batch"), today.minusDays(5), today.plusDays(20), seats));
        assertThat(resp.status()).as(resp.body()).isEqualTo(201);
        return resp.id();
    }

    /** Accepts an offer for a fresh candidate and returns the new Teacher (in training). */
    protected UUID recruit(String admin, String director) {
        UUID candidate = selected(admin, phone());
        UUID offer = issued(director, candidate, "16000");
        Resp accepted = post(BASE + "/offers/" + offer + "/accept", director, Map.of());
        assertThat(accepted.status()).as(accepted.body()).isEqualTo(200);
        return UUID.fromString((String) accepted.map().get("teacherId"));
    }

    protected UUID enrolmentOf(UUID batch, UUID teacher) {
        return jdbc.queryForObject("select id from induction_enrolment where batch_id = ? and teacher_id = ?", UUID.class, batch, teacher);
    }

    protected Resp enrol(String token, UUID batch, UUID teacher) {
        return post(IND + "/batches/" + batch + "/enrol", token, Map.of("teacherId", teacher));
    }

    protected Resp attend(String token, UUID enrolment, LocalDate date, String status, String reason) {
        Map<String, Object> body = new HashMap<>();
        body.put("date", date.toString());
        body.put("status", status);
        body.put("reason", reason);
        return post(IND + "/enrolments/" + enrolment + "/attendance", token, body);
    }
}
