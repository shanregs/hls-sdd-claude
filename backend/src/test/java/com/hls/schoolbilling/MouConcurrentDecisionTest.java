package com.hls.schoolbilling;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/** Spec 031 SC-005: two people deciding the same pending round record exactly one decision. */
class MouConcurrentDecisionTest extends MouTestBase {

    private List<Resp> decideTogether(Mou m, boolean secondRejects) throws Exception {
        submit(m.admin(), m.contractId());
        long v = versionOf(m.admin(), m.contractId());
        String dir = tokenOf(m.director());
        String manager = m.managerToken();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Resp> first = () -> {
                ready.countDown();
                go.await();
                return post(BASE + "/contracts/" + m.contractId() + "/approve", dir, Map.of("version", v));
            };
            Callable<Resp> second = () -> {
                ready.countDown();
                go.await();
                return secondRejects
                        ? post(BASE + "/contracts/" + m.contractId() + "/reject", manager, Map.of("version", v, "reason", "no"))
                        : post(BASE + "/contracts/" + m.contractId() + "/approve", manager, Map.of("version", v));
            };
            List<Future<Resp>> futures = new ArrayList<>();
            futures.add(pool.submit(first));
            futures.add(pool.submit(second));
            ready.await();
            go.countDown();
            List<Resp> results = new ArrayList<>();
            for (Future<Resp> f : futures) {
                results.add(f.get());
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private long decisionEvents(Mou m) {
        return jdbc.queryForObject(
                "select count(*) from mou_approval_event where contract_id = ? and action in ('APPROVED', 'REJECTED')",
                Long.class,
                m.contractId());
    }

    @Test
    void twoApprovalsRecordOneAndTheOtherIsToldWhoDecided() throws Exception {
        Mou m = mou();

        List<Resp> results = decideTogether(m, false);

        assertThat(results.stream().filter(r -> r.status() == 200)).hasSize(1);
        Resp loser = results.stream().filter(r -> r.status() != 200).findFirst().orElseThrow();
        assertThat(loser.status()).as(loser.body()).isEqualTo(409);
        assertThat(loser.body()).containsAnyOf("already decided", "changed by someone else");
        assertThat(decisionEvents(m)).isEqualTo(1);
    }

    @Test
    void anApprovalAndARejectionRecordOne() throws Exception {
        Mou m = mou();

        List<Resp> results = decideTogether(m, true);

        assertThat(results.stream().filter(r -> r.status() == 200)).hasSize(1);
        assertThat(results.stream().filter(r -> r.status() == 409)).hasSize(1);
        assertThat(decisionEvents(m)).isEqualTo(1);
        assertThat(stateOf(m.admin(), m.contractId())).isIn("APPROVED_WITHOUT_DOCUMENT", "REJECTED");
    }

    @Test
    void aDecisionAfterTheOtherOneCommittedNamesTheDecider() {
        Mou m = mou();
        submit(m.admin(), m.contractId());
        long v = versionOf(m.admin(), m.contractId());
        post(BASE + "/contracts/" + m.contractId() + "/approve", tokenOf(m.director()), Map.of("version", v));

        Resp late = post(BASE + "/contracts/" + m.contractId() + "/approve", m.managerToken(), Map.of("version", v));

        assertThat(late.status()).isEqualTo(409);
        assertThat(late.body()).contains("already decided").contains("Tester");
    }
}
