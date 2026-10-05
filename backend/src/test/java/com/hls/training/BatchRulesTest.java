package com.hls.training;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

class BatchRulesTest extends TrainingTestBase {

    @Test
    void aBatchNeedsNameDatesAndASeat() {
        String director = directorToken();

        assertThat(post(IND + "/batches", director, batchBody(" ", today, today.plusDays(5), 5)).status()).isEqualTo(400);
        assertThat(post(IND + "/batches", director, batchBody("B", today.plusDays(5), today, 5)).status()).isEqualTo(400);
        assertThat(post(IND + "/batches", director, batchBody("B", today, today.plusDays(5), 0)).status()).isEqualTo(400);
        Resp ok = post(IND + "/batches", director, batchBody("Ok", today, today.plusDays(5), 3));
        assertThat(ok.status()).isEqualTo(201);
        assertThat(ok.map()).containsEntry("seatLimit", 3).containsEntry("enrolled", 0);
    }

    @Test
    void enrolmentPastTheSeatLimitIsRefused() {
        String admin = admin();
        String director = directorToken();
        UUID batch = runningBatch(director, 1);
        // the first recruit joins on acceptance (the batch has room), the second finds it full
        UUID first = recruit(admin, director);
        UUID second = recruit(admin, director);

        assertThat(jdbc.queryForObject("select count(*) from induction_enrolment where batch_id = ?", Integer.class, batch)).isEqualTo(1);
        assertThat(enrol(director, batch, second).status()).isEqualTo(409);
        assertThat(enrol(director, batch, second).body()).contains("full");
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void twoSimultaneousRequestsForTheLastSeatLetOneThrough() throws Exception {
        String admin = admin();
        String director = directorToken();
        UUID a = recruit(admin, director);
        UUID b = recruit(admin, director);
        UUID batch = runningBatch(director, 1);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> one = () -> enrol(director, batch, a).status();
            Callable<Integer> two = () -> enrol(director, batch, b).status();
            List<Future<Integer>> results = pool.invokeAll(List.of(one, two));
            List<Integer> statuses = List.of(results.get(0).get(), results.get(1).get());
            assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        } finally {
            pool.shutdownNow();
        }
        assertThat(jdbc.queryForObject("select count(*) from induction_enrolment where batch_id = ?", Integer.class, batch)).isEqualTo(1);
    }

    @Test
    void aRecruitCannotBeInTwoBatchesOnOverlappingDatesNotEvenBySql() {
        String admin = admin();
        String director = directorToken();
        UUID teacher = recruit(admin, director);
        UUID first = runningBatch(director, 5);
        assertThat(enrol(director, first, teacher).status()).isEqualTo(201);
        UUID second = runningBatch(director, 5);

        assertThat(enrol(director, second, teacher).status()).isEqualTo(409);
        assertThatThrownBy(() -> jdbc.update(
                        "insert into induction_enrolment (id, batch_id, teacher_id, starts_on, ends_on, enrolled_by, enrolled_at, version)"
                                + " values (?, ?, ?, ?, ?, ?, now(), 0)",
                        UUID.randomUUID(),
                        second,
                        teacher,
                        today.minusDays(5),
                        today.plusDays(20),
                        UUID.randomUUID()))
                .hasMessageContaining("ex_induction_enrolment_overlap");
    }

    @Test
    void onlyARecruitInTrainingCanBeEnrolled() {
        String admin = admin();
        String director = directorToken();
        UUID batch = runningBatch(director, 5);
        UUID active = teacher(admin);

        Resp refused = enrol(director, batch, active);

        assertThat(refused.status()).isEqualTo(409);
        assertThat(refused.body()).contains("in training");
        assertThat(enrol(director, batch, UUID.randomUUID()).status()).isEqualTo(404);
    }

    @Test
    void cancellingABatchLeavesItsRecruitsToBeEnrolled() {
        String admin = admin();
        String director = directorToken();
        UUID batch = runningBatch(director, 5);
        UUID teacher = recruit(admin, director);
        assertThat(jdbc.queryForObject("select count(*) from induction_enrolment where teacher_id = ?", Integer.class, teacher)).isEqualTo(1);

        Resp cancelled = post(IND + "/batches/" + batch + "/cancel", director, Map.of());

        assertThat(cancelled.status()).as(cancelled.body()).isEqualTo(200);
        assertThat(cancelled.map()).containsEntry("status", "CANCELLED");
        assertThat(jdbc.queryForObject("select count(*) from induction_enrolment where teacher_id = ?", Integer.class, teacher)).isZero();
        assertThat(get(IND + "/to-be-enrolled", director).body()).contains(teacher.toString());
        assertThat(post(IND + "/batches/" + batch + "/cancel", director, Map.of()).status()).isEqualTo(409);
    }
}
