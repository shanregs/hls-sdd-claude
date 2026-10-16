package com.hls.payroll.run;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.support.PayrollRunTestBase;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Spec 013b T029: two people acting on one run at the same moment leave one consistent outcome. */
class PayRunConcurrencyTest extends PayrollRunTestBase {

    @BeforeEach
    void clean() {
        resetRuns();
    }

    /** Runs both calls together, released by one latch, and returns their statuses in order. */
    private List<Integer> together(Supplier<Resp> first, Supplier<Resp> second) {
        CountDownLatch go = new CountDownLatch(1);
        CompletableFuture<Integer> a = CompletableFuture.supplyAsync(() -> afterLatch(go, first));
        CompletableFuture<Integer> b = CompletableFuture.supplyAsync(() -> afterLatch(go, second));
        go.countDown();
        return List.of(a.join(), b.join());
    }

    private static int afterLatch(CountDownLatch go, Supplier<Resp> call) {
        try {
            go.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return call.get().status();
    }

    private long payslips(UUID run) {
        return jdbc.queryForObject("select count(*) from payroll_payslip where run_id = ?", Long.class, run);
    }

    @Test
    void twoSimultaneousApprovalsProduceOneApprovalAndOne409() {
        String admin = signInAs(Role.ADMIN).token();
        String directorA = signInAs(Role.DIRECTOR).token();
        String directorB = signInAs(Role.DIRECTOR).token();
        Draft draft = readyDraft(admin);
        submitRun(admin, draft.run());

        List<Integer> statuses = together(() -> approveRun(directorA, draft.run()), () -> approveRun(directorB, draft.run()));

        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        assertThat(runOf(admin, draft.run()).get("status")).isEqualTo("APPROVED");
        int paid = ((Number) runOf(admin, draft.run()).get("payableCount")).intValue();
        assertThat(payslips(draft.run())).as("one set of payslips, not two").isEqualTo(paid);
        assertThat(jdbc.queryForObject("select count(*) from payroll_paid_month where run_id = ?", Long.class, draft.run()))
                .isEqualTo((long) paid);
    }

    @Test
    void aReturnRacingAnApprovalLeavesOneConsistentOutcome() {
        String admin = signInAs(Role.ADMIN).token();
        String directorA = signInAs(Role.DIRECTOR).token();
        String directorB = signInAs(Role.DIRECTOR).token();
        Draft draft = readyDraft(admin);
        submitRun(admin, draft.run());

        List<Integer> statuses =
                together(() -> approveRun(directorA, draft.run()), () -> returnRun(directorB, draft.run(), "Hold on"));

        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        String status = (String) runOf(admin, draft.run()).get("status");
        if ("APPROVED".equals(status)) {
            assertThat(payslips(draft.run())).isPositive();
        } else {
            assertThat(status).isEqualTo("DRAFT");
            assertThat(payslips(draft.run())).as("a returned run has issued nothing").isZero();
            assertThat(jdbc.queryForObject("select count(*) from payroll_paid_month where run_id = ?", Long.class, draft.run()))
                    .isZero();
        }
    }

    @Test
    void twoSimultaneousStartsForAMonthProduceOneRun() {
        String admin = signInAs(Role.ADMIN).token();

        List<Integer> statuses = together(() -> startRun(admin), () -> startRun(admin));

        assertThat(statuses).containsExactlyInAnyOrder(202, 409);
        assertThat(jdbc.queryForObject("select count(*) from payroll_run", Long.class)).isEqualTo(1L);
    }
}
