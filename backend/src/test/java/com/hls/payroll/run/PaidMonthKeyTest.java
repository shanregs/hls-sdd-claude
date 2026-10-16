package com.hls.payroll.run;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.support.PayrollRunTestBase;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Spec 013b T043 (SC-007): the key of {@code payroll_paid_month} makes "a person is paid once per month" a fact. Two runs
 * that both contain the same person are built straight in the database (the service would not start the second), in a
 * month nothing else uses.
 */
class PaidMonthKeyTest extends PayrollRunTestBase {

    private static final LocalDate MONTH = LocalDate.of(1991, 6, 1);

    /** A valid stored working, so the payslip can be drawn from it. */
    private static final String WORKING = "{\"state\":\"PAYABLE\",\"policy\":{\"id\":\"p\",\"effectiveOn\":\"2000-01-01\","
            + "\"rounding\":\"NEAREST_RUPEE\",\"halfDayFraction\":\"0.500\"},"
            + "\"salaryRows\":[{\"from\":\"1991-06-03\",\"to\":\"1991-06-28\",\"monthly\":\"20000.00\"}],"
            + "\"earned\":{\"unrounded\":\"20000\",\"rounded\":\"20000.00\"},"
            + "\"deduction\":{\"unrounded\":\"0\",\"rounded\":\"0.00\"},\"unpaid\":[],\"adjustments\":[]}";

    @BeforeEach
    void clean() {
        resetRuns();
    }

    private UUID awaitingRun(String kind, int sequence) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into payroll_run (id, month, kind, sequence, status, compute_state, started_by, started_at,"
                        + " submitted_by, submitted_at, payable_count) values (?, ?, ?, ?, 'AWAITING_APPROVAL', 'DONE', ?, ?, ?, ?, 1)",
                id,
                MONTH,
                kind,
                sequence,
                UUID.randomUUID(),
                Timestamp.from(Instant.now()),
                UUID.randomUUID(),
                Timestamp.from(Instant.now()));
        return id;
    }

    private void payableLine(UUID run, UUID person) {
        jdbc.update(
                "insert into payroll_run_line (id, run_id, person_kind, person_id, person_name, person_label, state,"
                        + " payable_days, working_days, unpaid_days, salary, earned, deduction, adjustments, net, working,"
                        + " fingerprint, computed_at) values (?, ?, 'TEACHER', ?, 'Zed Teacher', 'Teacher', 'PAYABLE', 20, 20, 0,"
                        + " 20000, 20000, 0, 0, 20000, ?::jsonb, 'f', now())",
                UUID.randomUUID(),
                run,
                person,
                WORKING);
    }

    private long paidRows(UUID person) {
        return jdbc.queryForObject("select count(*) from payroll_paid_month where person_id = ?", Long.class, person);
    }

    @Test
    void aSecondApprovedRunWithTheSamePersonFailsWithAClearMessageAndChangesNothing() {
        String director = signInAs(Role.DIRECTOR).token();
        UUID person = UUID.randomUUID();
        UUID first = awaitingRun("PRIMARY", 1);
        payableLine(first, person);
        UUID second = awaitingRun("SUPPLEMENTARY", 1);
        payableLine(second, person);

        assertThat(approveRun(director, first).status()).isEqualTo(200);
        Resp refused = approveRun(director, second);

        assertThat(refused.status()).isEqualTo(409);
        assertThat(refused.body()).contains("already been paid").contains("Nothing was approved");
        assertThat(paidRows(person)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select status from payroll_run where id = ?", String.class, second))
                .isEqualTo("AWAITING_APPROVAL");
        assertThat(jdbc.queryForObject("select count(*) from payroll_payslip where run_id = ?", Long.class, second)).isZero();
    }

    @Test
    void twoSimultaneousApprovalsOfRunsWithTheSamePersonLeaveOnePaidAndOneFailing() {
        String directorA = signInAs(Role.DIRECTOR).token();
        String directorB = signInAs(Role.DIRECTOR).token();
        UUID person = UUID.randomUUID();
        UUID first = awaitingRun("PRIMARY", 1);
        payableLine(first, person);
        UUID second = awaitingRun("SUPPLEMENTARY", 1);
        payableLine(second, person);
        CountDownLatch go = new CountDownLatch(1);
        CompletableFuture<Integer> a = CompletableFuture.supplyAsync(() -> after(go, () -> approveRun(directorA, first)));
        CompletableFuture<Integer> b = CompletableFuture.supplyAsync(() -> after(go, () -> approveRun(directorB, second)));
        go.countDown();

        List<Integer> statuses = List.of(a.join(), b.join());

        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        assertThat(paidRows(person)).isEqualTo(1L);
        assertThat(jdbc.queryForObject(
                        "select count(*) from payroll_run where month = ? and status = 'APPROVED'", Long.class, MONTH))
                .isEqualTo(1L);
        assertThat(jdbc.queryForObject("select count(*) from payroll_payslip", Long.class)).isEqualTo(1L);
    }

    @Test
    void theKeyItselfRefusesASecondRowForThePersonAndMonth() {
        UUID person = UUID.randomUUID();
        UUID run = awaitingRun("PRIMARY", 1);
        payableLine(run, person);
        UUID line = jdbc.queryForObject("select id from payroll_run_line where run_id = ?", UUID.class, run);
        jdbc.update(
                "insert into payroll_paid_month (month, person_kind, person_id, run_id, line_id) values (?, 'TEACHER', ?, ?, ?)",
                MONTH,
                person,
                run,
                line);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update(
                        "insert into payroll_paid_month (month, person_kind, person_id, run_id, line_id) values (?, 'TEACHER', ?, ?, ?)",
                        MONTH,
                        person,
                        run,
                        line))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
    }

    private static int after(CountDownLatch go, java.util.function.Supplier<Resp> call) {
        try {
            go.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return call.get().status();
    }
}
