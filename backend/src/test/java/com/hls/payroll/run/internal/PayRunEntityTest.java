package com.hls.payroll.run.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.payroll.api.PayRules.Kind;
import com.hls.payroll.api.PayRules.PersonRef;
import com.hls.support.MasterDataTestBase;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.skyscreamer.jsonassert.JSONAssert;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Spec 013b T015: the entities map the schema, and the frozen working comes back exactly as it was written. */
class PayRunEntityTest extends MasterDataTestBase {

    private static final YearMonth MONTH = YearMonth.of(1991, 5);

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transaction;

    @Autowired
    private PayRunRepository runs;

    @Autowired
    private PayRunLineRepository lines;

    @Autowired
    private PayAdjustmentRepository adjustments;

    @Autowired
    private LineAdjustmentRepository lineAdjustments;

    @Autowired
    private RunEventRepository events;

    @BeforeEach
    void clean() {
        transaction = new TransactionTemplate(transactionManager);
        jdbc.execute("truncate payroll_paid_month, payroll_payment_entry, payroll_payslip, payroll_run_event,"
                + " payroll_run_line_adjustment, payroll_adjustment, payroll_run_line, payroll_run");
    }

    private static PersonResult payable(UUID personId, String working) {
        return new PersonResult(
                new PersonRef(Kind.TEACHER, personId),
                "Tara Teacher",
                "Teacher",
                PersonResult.State.PAYABLE,
                null,
                3,
                new BigDecimal("20"),
                new BigDecimal("1.5"),
                new BigDecimal("30000.00"),
                new BigDecimal("4500.00"),
                new BigDecimal("1500.00"),
                new BigDecimal("0.00"),
                new BigDecimal("3000.00"),
                working,
                "abc",
                "careful",
                List.of());
    }

    @Test
    void aRunAndItsLineRoundTripWithTheWorkingTextUnchanged() throws Exception {
        PayRun run = runs.saveAndFlush(new PayRun(MONTH, PayRun.Kind.PRIMARY, 1, UUID.randomUUID(), Instant.now()));
        String working = "{\"state\":\"PAYABLE\",\"note\":\"He said \\\"ok\\\"\",\"days\":[{\"date\":\"1991-05-01\"}]}";
        PayRunLine saved = lines.saveAndFlush(new PayRunLine(run.getId(), payable(UUID.randomUUID(), working), Instant.now()));

        PayRunLine read = lines.findById(saved.getId()).orElseThrow();

        assertThat(read.getWorking()).isNotNull();
        // jsonb normalises spacing and the order of keys but never changes the content
        JSONAssert.assertEquals(working, read.getWorking(), true);
        assertThat(read.getNet()).isEqualByComparingTo("3000.00");
        assertThat(read.getState()).isEqualTo(PersonResult.State.PAYABLE);
        assertThat(read.getWarning()).isEqualTo("careful");
    }

    @Test
    void aNewRunIsADraftWaitingToBeComputedAndItsVersionAdvancesOnChange() {
        PayRun run = runs.saveAndFlush(new PayRun(MONTH, PayRun.Kind.PRIMARY, 1, UUID.randomUUID(), Instant.now()));
        assertThat(run.getStatus()).isEqualTo(PayRun.Status.DRAFT);
        assertThat(run.getComputeState()).isEqualTo(PayRun.ComputeState.QUEUED);
        assertThat(run.getMonth()).isEqualTo(MONTH);

        PayRun again = runs.findById(run.getId()).orElseThrow();
        again.setProgress(5, 10, Instant.now());
        runs.saveAndFlush(again);

        assertThat(runs.findById(run.getId()).orElseThrow().getProgressDone()).isEqualTo(5);
        assertThat(runs.findById(run.getId()).orElseThrow().getVersion()).isGreaterThan(run.getVersion());
    }

    @Test
    void theRepositoriesFilterAndLockAndCountWithoutErrors() {
        PayRun run = runs.saveAndFlush(new PayRun(MONTH, PayRun.Kind.PRIMARY, 1, UUID.randomUUID(), Instant.now()));
        lines.saveAndFlush(new PayRunLine(run.getId(), payable(UUID.randomUUID(), "{}"), Instant.now()));

        java.util.Optional<PayRun> locked = transaction.execute(status -> runs.lockById(run.getId()));
        assertThat(locked).isPresent();
        assertThat(runs.search(null, PageRequest.of(0, 10)).getContent()).extracting(PayRun::getId).contains(run.getId());
        assertThat(runs.search(PayRun.Status.APPROVED, PageRequest.of(0, 10)).getContent())
                .extracting(PayRun::getId)
                .doesNotContain(run.getId());
        assertThat(runs.maxSequence(MONTH.atDay(1), PayRun.Kind.PRIMARY)).isEqualTo(1);
        assertThat(runs.maxSequence(MONTH.plusMonths(1).atDay(1), PayRun.Kind.PRIMARY)).isZero();
        assertThat(runs.approvedPrimary(MONTH.atDay(1))).isEmpty();

        var page = lines.search(
                run.getId(),
                List.of(PersonResult.State.PAYABLE, PersonResult.State.CANNOT_PAY),
                List.of(Kind.TEACHER, Kind.MANAGER, Kind.STAFF),
                "%tara%",
                PageRequest.of(0, 50));
        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(lines.search(
                                run.getId(),
                                List.of(PersonResult.State.CANNOT_PAY),
                                List.of(Kind.TEACHER),
                                "%",
                                PageRequest.of(0, 50))
                        .getTotalElements())
                .isZero();
    }

    @Test
    void adjustmentsAreFoundWhileWaitingAndCopiedIntoALine() {
        UUID person = UUID.randomUUID();
        PayAdjustment adjustment = adjustments.saveAndFlush(new PayAdjustment(
                Kind.TEACHER,
                person,
                "Tara Teacher",
                PayAdjustment.AdjustmentKind.CORRECTION,
                new BigDecimal("2000.00"),
                "Arrears",
                UUID.randomUUID(),
                Instant.now()));

        assertThat(adjustments.waitingFor(Kind.TEACHER, List.of(person))).extracting(PayAdjustment::getId).containsExactly(adjustment.getId());
        assertThat(adjustments.waitingFor(Kind.MANAGER, List.of(person))).isEmpty();

        PayRun run = runs.saveAndFlush(new PayRun(MONTH, PayRun.Kind.PRIMARY, 1, UUID.randomUUID(), Instant.now()));
        PayRunLine line = lines.saveAndFlush(new PayRunLine(run.getId(), payable(person, "{}"), Instant.now()));
        lineAdjustments.saveAndFlush(
                new LineAdjustment(line.getId(), adjustment.getId(), "CORRECTION", adjustment.getAmount(), adjustment.getReason()));
        adjustment.attachTo(run.getId());
        adjustments.saveAndFlush(adjustment);

        assertThat(adjustments.findByAttachedRunId(run.getId())).hasSize(1);
        assertThat(lineAdjustments.findByLineId(line.getId())).hasSize(1);
    }

    @Test
    void anEventKeepsItsReasonAndDetail() throws Exception {
        PayRun run = runs.saveAndFlush(new PayRun(MONTH, PayRun.Kind.PRIMARY, 1, UUID.randomUUID(), Instant.now()));
        events.saveAndFlush(new RunEvent(run.getId(), Instant.now(), UUID.randomUUID(), "RETURN", "Check the Manager lines", "{\"lines\":2}"));

        List<RunEvent> read = events.findByRunIdOrderByAtAsc(run.getId());

        assertThat(read).hasSize(1);
        assertThat(read.get(0).getReason()).isEqualTo("Check the Manager lines");
        JSONAssert.assertEquals("{\"lines\":2}", read.get(0).getDetail(), true);
    }
}
