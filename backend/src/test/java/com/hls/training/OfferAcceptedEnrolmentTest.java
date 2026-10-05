package com.hls.training;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/** Accepting an offer enrols the new recruit through the OfferAccepted event; recruitment never calls training. */
class OfferAcceptedEnrolmentTest extends TrainingTestBase {

    @Test
    void acceptanceEnrolsTheRecruitInTheNextBatchWithRoom() {
        String admin = admin();
        String director = directorToken();
        UUID batch = runningBatch(director, 5);

        UUID teacher = recruit(admin, director);

        assertThat(enrolmentOf(batch, teacher)).isNotNull();
        assertThat(get(IND + "/to-be-enrolled", director).body()).doesNotContain(teacher.toString());
    }

    @Test
    void withNoBatchWithRoomTheRecruitWaitsInTheToBeEnrolledList() {
        String admin = admin();
        String director = directorToken();
        UUID teacher = recruit(admin, director);

        assertThat(jdbc.queryForObject("select count(*) from induction_enrolment where teacher_id = ?", Integer.class, teacher)).isZero();
        assertThat(get(IND + "/to-be-enrolled", director).body()).contains(teacher.toString());
        // a batch created later takes them on request
        UUID batch = runningBatch(director, 5);
        assertThat(enrol(director, batch, teacher).status()).isEqualTo(201);
        assertThat(get(IND + "/to-be-enrolled", director).body()).doesNotContain(teacher.toString());
    }

    @Test
    void twoSimultaneousAcceptancesForTheLastSeatEnrolOneAndLeaveTheOtherWaiting() throws Exception {
        String admin = admin();
        String director = directorToken();
        UUID batch = runningBatch(director, 1);
        UUID offerA = issued(director, selected(admin, phone()), "16000");
        UUID offerB = issued(director, selected(admin, phone()), "16000");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Callable<Integer>> calls = List.of(
                    () -> post(BASE + "/offers/" + offerA + "/accept", director, Map.of()).status(),
                    () -> post(BASE + "/offers/" + offerB + "/accept", director, Map.of()).status());
            for (Future<Integer> f : pool.invokeAll(calls)) {
                assertThat(f.get()).isEqualTo(200);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(jdbc.queryForObject("select count(*) from induction_enrolment where batch_id = ?", Integer.class, batch)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "select count(*) from teacher t join job_offer o on o.teacher_id = t.id where o.id in (?, ?)", Integer.class, offerA, offerB))
                .isEqualTo(2);
    }
}
