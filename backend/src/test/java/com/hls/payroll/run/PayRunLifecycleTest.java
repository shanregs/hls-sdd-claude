package com.hls.payroll.run;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.support.PayrollRunTestBase;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Spec 013b T018: start, compute, cancel and the rules around them (FR-001, FR-009, FR-010, FR-012). */
class PayRunLifecycleTest extends PayrollRunTestBase {

    @BeforeEach
    void clean() {
        resetRuns();
    }

    @Test
    void startForTheCurrentMonthAndForAFutureMonthIsRefused() {
        String admin = signInAs(Role.ADMIN).token();

        Resp current = post(RUNS, admin, Map.of("month", YearMonth.from(today()).toString()));
        Resp future = post(RUNS, admin, Map.of("month", YearMonth.from(today()).plusMonths(2).toString()));
        Resp bad = post(RUNS, admin, Map.of("month", "September"));

        assertThat(current.status()).isEqualTo(400);
        assertThat(current.body()).contains("has not ended");
        assertThat(future.status()).isEqualTo(400);
        assertThat(bad.status()).isEqualTo(400);
        assertThat(bad.body()).contains("yyyy-MM");
        assertThat(jdbc.queryForObject("select count(*) from payroll_run", Long.class)).isZero();
    }

    @Test
    void startForAnEndedMonthCreatesAQueuedDraftThatFinishesWithItsProgressAtTheTotal() {
        String admin = signInAs(Role.ADMIN).token();
        Site site = site(admin);
        UUID teacher = teacherAt(admin, site.schoolId(), LocalDate.now().minusDays(200));
        teacherSalary(admin, teacher, "20000", LocalDate.now().minusDays(300));
        lockTeacherMonth(teacher);

        Resp started = startRun(admin);

        assertThat(started.status()).as(started.body()).isEqualTo(202);
        assertThat(started.map().get("status")).isEqualTo("DRAFT");
        assertThat(started.map().get("computeState")).isEqualTo("QUEUED");
        assertThat(started.map().get("kind")).isEqualTo("PRIMARY");
        assertThat(started.map().get("month")).isEqualTo(month.toString());
        Map<String, Object> done = awaitComputed(admin, started.id());
        assertThat(done.get("computeState")).isEqualTo("DONE");
        assertThat(done.get("progressDone")).isEqualTo(done.get("progressTotal"));
        assertThat(((Number) done.get("progressTotal")).intValue()).isPositive();
        assertThat(allLines(admin, started.id())).hasSize(((Number) done.get("progressTotal")).intValue());
    }

    @Test
    void aSecondPrimaryRunForTheMonthIsRefusedWhileOneIsNotCancelled() {
        String admin = signInAs(Role.ADMIN).token();
        UUID first = startAndCompute(admin);

        Resp second = startRun(admin);

        assertThat(second.status()).isEqualTo(409);
        assertThat(second.body()).contains("already exists");
        assertThat(jdbc.queryForObject("select count(*) from payroll_run where id <> ?", Long.class, first)).isZero();
    }

    @Test
    void twoSimultaneousStartsCreateExactlyOneRun() throws Exception {
        String admin = signInAs(Role.ADMIN).token();
        CountDownLatch go = new CountDownLatch(1);
        List<CompletableFuture<Resp>> calls = List.of(
                CompletableFuture.supplyAsync(() -> awaitThenStart(go, admin)),
                CompletableFuture.supplyAsync(() -> awaitThenStart(go, admin)));
        go.countDown();

        List<Integer> statuses = calls.stream().map(c -> c.join().status()).sorted().toList();

        assertThat(statuses).containsExactly(202, 409);
        assertThat(jdbc.queryForObject("select count(*) from payroll_run", Long.class)).isEqualTo(1L);
    }

    private Resp awaitThenStart(CountDownLatch go, String token) {
        try {
            go.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return startRun(token);
    }

    @Test
    void cancellingADraftNeedsAReasonKeepsTheLinesAndEventsAndAllowsANewRun() {
        String admin = signInAs(Role.ADMIN).token();
        Site site = site(admin);
        UUID teacher = teacherAt(admin, site.schoolId(), LocalDate.now().minusDays(200));
        teacherSalary(admin, teacher, "20000", LocalDate.now().minusDays(300));
        lockTeacherMonth(teacher);
        UUID run = startAndCompute(admin);
        int lineCount = allLines(admin, run).size();

        Resp noReason = post(RUNS + "/" + run + "/cancel", admin, Map.of());
        Resp blank = post(RUNS + "/" + run + "/cancel", admin, Map.of("reason", "   "));
        Resp tooLong = post(RUNS + "/" + run + "/cancel", admin, Map.of("reason", "x".repeat(501)));
        Resp cancelled = post(RUNS + "/" + run + "/cancel", admin, Map.of("reason", "Wrong month picked"));

        assertThat(noReason.status()).isEqualTo(400);
        assertThat(blank.status()).isEqualTo(400);
        assertThat(tooLong.status()).isEqualTo(400);
        assertThat(cancelled.status()).as(cancelled.body()).isEqualTo(200);
        assertThat(cancelled.map().get("status")).isEqualTo("CANCELLED");
        assertThat(cancelled.map().get("cancelReason")).isEqualTo("Wrong month picked");
        assertThat(allLines(admin, run)).hasSize(lineCount);
        assertThat(get(RUNS + "/" + run, admin).body()).contains("CANCELLED").contains("Wrong month picked");
        // a cancelled run no longer blocks a new one
        Resp again = startRun(admin);
        assertThat(again.status()).as(again.body()).isEqualTo(202);
        assertThat(again.map().get("sequence")).isEqualTo(2);
        awaitComputed(admin, again.id());
        // and a cancelled run cannot be cancelled twice or computed again
        assertThat(post(RUNS + "/" + run + "/cancel", admin, Map.of("reason", "again")).status()).isEqualTo(409);
        assertThat(post(RUNS + "/" + run + "/recompute", admin, Map.of()).status()).isEqualTo(409);
    }

    @Test
    void anApprovedRunCannotBeComputedAgainOrCancelled() {
        String admin = signInAs(Role.ADMIN).token();
        UUID run = startAndCompute(admin);
        jdbc.update(
                "update payroll_run set status = 'APPROVED', submitted_by = ?, approved_by = ?, approved_at = now() where id = ?",
                UUID.randomUUID(),
                UUID.randomUUID(),
                run);

        Resp recompute = post(RUNS + "/" + run + "/recompute", admin, Map.of());
        Resp cancel = post(RUNS + "/" + run + "/cancel", admin, Map.of("reason", "too late"));

        assertThat(recompute.status()).isEqualTo(409);
        assertThat(cancel.status()).isEqualTo(409);
    }

    @Test
    void anUnknownRunAnswers404() {
        String admin = signInAs(Role.ADMIN).token();

        assertThat(get(RUNS + "/" + UUID.randomUUID(), admin).status()).isEqualTo(404);
        assertThat(get(RUNS + "/" + UUID.randomUUID() + "/lines", admin).status()).isEqualTo(404);
        assertThat(post(RUNS + "/" + UUID.randomUUID() + "/recompute", admin, Map.of()).status()).isEqualTo(404);
    }

    @Test
    void theRunsListShowsNewestMonthFirstAndFiltersByMonthAndStatus() {
        String admin = signInAs(Role.ADMIN).token();
        UUID run = startAndCompute(admin);

        Resp all = get(RUNS, admin);
        Resp byMonth = get(RUNS + "?month=" + month, admin);
        Resp otherMonth = get(RUNS + "?month=" + month.minusMonths(5), admin);
        Resp cancelledOnly = get(RUNS + "?status=CANCELLED", admin);

        assertThat(content(all)).extracting(r -> r.get("id")).contains(run.toString());
        assertThat(content(byMonth)).hasSize(1);
        assertThat(content(otherMonth)).isEmpty();
        assertThat(content(cancelledOnly)).isEmpty();
        assertThat(get(RUNS + "?status=NOPE", admin).status()).isEqualTo(400);
    }
}
