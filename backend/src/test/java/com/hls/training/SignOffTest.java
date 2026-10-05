package com.hls.training;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SignOffTest extends TrainingTestBase {

    private String status(UUID teacher) {
        return jdbc.queryForObject("select status from teacher where id = ?", String.class, teacher);
    }

    @Test
    void completedMakesTheTeacherActiveAndAResultCannotBeSignedTwice() {
        String admin = admin();
        String director = directorToken();
        UUID batch = runningBatch(director, 5);
        UUID teacher = recruit(admin, director);
        UUID enrolment = enrolmentOf(batch, teacher);

        Resp done = post(IND + "/enrolments/" + enrolment + "/signoff", director, Map.of("result", "COMPLETED", "remarks", "Strong"));

        assertThat(done.status()).as(done.body()).isEqualTo(200);
        assertThat(done.map()).containsEntry("result", "COMPLETED");
        assertThat(status(teacher)).isEqualTo("ACTIVE");
        assertThat(post(IND + "/enrolments/" + enrolment + "/signoff", director, Map.of("result", "COMPLETED")).status()).isEqualTo(409);
        assertThat(post(IND + "/enrolments/" + enrolment + "/signoff", director, Map.of("result", "MAYBE")).status()).isEqualTo(400);
        assertChangeRecorded(admin, "INDUCTION_SIGNOFF", enrolment, "result");
    }

    @Test
    void notCompletedThenNextBatchEnrolsAgainKeepingTheEarlierSignOffAndDays() {
        String admin = admin();
        String director = directorToken();
        UUID first = runningBatch(director, 5);
        UUID teacher = recruit(admin, director);
        UUID enrolment = enrolmentOf(first, teacher);
        attend(director, enrolment, today.minusDays(2), "PRESENT", null);
        post(IND + "/enrolments/" + enrolment + "/signoff", director, Map.of("result", "NOT_COMPLETED", "remarks", "Needs more"));
        assertThat(status(teacher)).isEqualTo("IN_TRAINING");
        assertThat(post(IND + "/enrolments/" + enrolment + "/follow-up", director, Map.of("action", "NEXT_BATCH")).status()).isEqualTo(409);

        Resp next = post(IND + "/batches", director, batchBody(uniqueName("Next"), today.plusDays(1), today.plusDays(30), 5));
        assertThat(next.status()).isEqualTo(201);
        Resp moved = post(IND + "/enrolments/" + enrolment + "/follow-up", director, Map.of("action", "NEXT_BATCH"));

        assertThat(moved.status()).as(moved.body()).isEqualTo(200);
        assertThat(moved.map()).containsEntry("followUp", "NEXT_BATCH");
        UUID newEnrolment = enrolmentOf(next.id(), teacher);
        assertThat(newEnrolment).isNotEqualTo(enrolment);
        assertThat(jdbc.queryForObject("select result from induction_enrolment where id = ?", String.class, enrolment)).isEqualTo("NOT_COMPLETED");
        assertThat(jdbc.queryForObject("select count(*) from attendance_mark where teacher_id = ?", Integer.class, teacher)).isEqualTo(1);
        assertThat(post(IND + "/enrolments/" + enrolment + "/follow-up", director, Map.of("action", "RELEASE", "reason", "x")).status()).isEqualTo(409);
        // the new enrolment has its own sign-off
        assertThat(post(IND + "/enrolments/" + newEnrolment + "/signoff", director, Map.of("result", "COMPLETED")).status()).isEqualTo(409);
    }

    @Test
    void releaseExitsTheTeacherWithTheReasonAndNeedsOne() {
        String admin = admin();
        String director = directorToken();
        UUID batch = runningBatch(director, 5);
        UUID teacher = recruit(admin, director);
        UUID enrolment = enrolmentOf(batch, teacher);
        post(IND + "/enrolments/" + enrolment + "/signoff", director, Map.of("result", "NOT_COMPLETED"));

        assertThat(post(IND + "/enrolments/" + enrolment + "/follow-up", director, Map.of("action", "RELEASE", "reason", " ")).status()).isEqualTo(400);
        Resp released = post(IND + "/enrolments/" + enrolment + "/follow-up", director, Map.of("action", "RELEASE", "reason", "Did not attend"));

        assertThat(released.status()).as(released.body()).isEqualTo(200);
        assertThat(status(teacher)).isEqualTo("EXITED");
        assertChangeRecorded(admin, "TEACHER", teacher, "exitReason");
    }

    @Test
    void aFollowUpOnACompletedRecruitIsRefused() {
        String admin = admin();
        String director = directorToken();
        UUID batch = runningBatch(director, 5);
        UUID teacher = recruit(admin, director);
        UUID enrolment = enrolmentOf(batch, teacher);
        post(IND + "/enrolments/" + enrolment + "/signoff", director, Map.of("result", "COMPLETED"));

        assertThat(post(IND + "/enrolments/" + enrolment + "/follow-up", director, Map.of("action", "RELEASE", "reason", "x")).status()).isEqualTo(409);
    }
}
