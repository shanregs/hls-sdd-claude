package com.hls.leave;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/** Spec 040 SC-006: when two people record same-day leave for one Teacher at once, exactly one succeeds. */
class SameDayLeaveConcurrencyTest extends LeaveTestBase {

    @Test
    void exactlyOneOfTwoSimultaneousRecordingsSucceeds() throws Exception {
        for (int trial = 0; trial < 5; trial++) {
            World w = newWorld();
            Resp preview = post(ME + "/preview", w.teacherA().token(), draft(today(), today()));
            assumeTrue(preview.status() == 200 && preview.body().contains("\"problems\":[]"), "today is not a working day");
            Map<String, Object> asManager = new HashMap<>(draft(today(), today()));
            asManager.put("teacherId", w.teacherA().teacherId().toString());

            ExecutorService pool = Executors.newFixedThreadPool(2);
            CountDownLatch go = new CountDownLatch(1);
            List<Callable<Resp>> calls = new ArrayList<>();
            calls.add(() -> {
                go.await();
                return post(ME, w.teacherA().token(), draft(today(), today()));
            });
            calls.add(() -> {
                go.await();
                return post(SUP + "/same-day", w.managerA().token(), asManager);
            });
            List<Future<Resp>> futures = new ArrayList<>();
            for (Callable<Resp> call : calls) {
                futures.add(pool.submit(call));
            }
            go.countDown();
            int created = 0;
            int conflicts = 0;
            for (Future<Resp> f : futures) {
                Resp r = f.get();
                if (r.status() == 201) {
                    created++;
                } else if (r.status() == 409) {
                    conflicts++;
                }
            }
            pool.shutdown();

            assertThat(created).as("trial " + trial).isEqualTo(1);
            assertThat(conflicts).as("trial " + trial).isEqualTo(1);
            assertThat(jdbc.queryForObject(
                            "select count(*) from leave_request where teacher_id = ? and status = 'APPROVED'",
                            Integer.class,
                            w.teacherA().teacherId()))
                    .isEqualTo(1);
        }
    }
}
