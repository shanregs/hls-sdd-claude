package com.hls.payroll.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hls.support.MasterDataTestBase;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;

/** Spec 013b T010: what the database itself refuses, whatever the application does. */
class PayRunSchemaTest extends MasterDataTestBase {

    private static final LocalDate MONTH = LocalDate.of(1990, 3, 1);

    /** TRUNCATE does not fire row triggers, so the tests can start clean although approved rows are otherwise final. */
    @BeforeEach
    void clean() {
        jdbc.execute("truncate payroll_paid_month, payroll_payment_entry, payroll_payslip, payroll_run_event,"
                + " payroll_run_line_adjustment, payroll_adjustment, payroll_run_line, payroll_run");
    }

    private UUID insertRun(LocalDate month, String kind, int sequence, String status, UUID submittedBy, UUID approvedBy) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into payroll_run (id, month, kind, sequence, status, compute_state, started_by, started_at,"
                        + " submitted_by, approved_by, approved_at, cancelled_by, cancelled_at, cancel_reason)"
                        + " values (?, ?, ?, ?, ?, 'DONE', ?, now(), ?, ?, ?, ?, ?, ?)",
                id,
                month,
                kind,
                sequence,
                status,
                UUID.randomUUID(),
                submittedBy,
                approvedBy,
                approvedBy == null ? null : java.sql.Timestamp.from(java.time.Instant.now()),
                "CANCELLED".equals(status) ? UUID.randomUUID() : null,
                "CANCELLED".equals(status) ? java.sql.Timestamp.from(java.time.Instant.now()) : null,
                "CANCELLED".equals(status) ? "test" : null);
        return id;
    }

    private UUID insertRun(LocalDate month, String kind, int sequence, String status) {
        return insertRun(month, kind, sequence, status, null, null);
    }

    private UUID insertPayableLine(UUID run, String earned, String deduction, String adjustments, String net) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into payroll_run_line (id, run_id, person_kind, person_id, person_name, state, earned, deduction,"
                        + " adjustments, net, fingerprint, computed_at) values (?, ?, 'TEACHER', ?, 'Tara', 'PAYABLE',"
                        + " ?::numeric, ?::numeric, ?::numeric, ?::numeric, 'f', now())",
                id,
                run,
                UUID.randomUUID(),
                earned,
                deduction,
                adjustments,
                net);
        return id;
    }

    private UUID approvedRunWithLine() {
        UUID submitter = UUID.randomUUID();
        UUID run = insertRun(MONTH, "PRIMARY", 1, "AWAITING_APPROVAL", submitter, null);
        UUID line = insertPayableLine(run, "1000.00", "100.00", "0.00", "900.00");
        jdbc.update(
                "update payroll_run set status = 'APPROVED', approved_by = ?, approved_at = now() where id = ?",
                UUID.randomUUID(),
                run);
        return line;
    }

    @Test
    void aSecondNonCancelledPrimaryRunForAMonthIsRefusedButACancelledOneDoesNotBlock() {
        insertRun(MONTH, "PRIMARY", 1, "DRAFT");
        assertThatThrownBy(() -> insertRun(MONTH, "PRIMARY", 2, "DRAFT")).isInstanceOf(DataAccessException.class);

        LocalDate other = MONTH.plusMonths(1);
        insertRun(other, "PRIMARY", 1, "CANCELLED");
        insertRun(other, "PRIMARY", 2, "DRAFT");
        // cancelled runs pile up without blocking
        insertRun(other, "PRIMARY", 3, "CANCELLED");
    }

    @Test
    void aSecondOpenSupplementaryRunIsRefusedButAnApprovedOneDoesNotBlock() {
        insertRun(MONTH, "SUPPLEMENTARY", 1, "DRAFT");
        assertThatThrownBy(() -> insertRun(MONTH, "SUPPLEMENTARY", 2, "AWAITING_APPROVAL"))
                .isInstanceOf(DataAccessException.class);

        LocalDate other = MONTH.plusMonths(2);
        insertRun(other, "SUPPLEMENTARY", 1, "APPROVED", UUID.randomUUID(), UUID.randomUUID());
        insertRun(other, "SUPPLEMENTARY", 2, "DRAFT");
        // a primary run of the same month is a different kind
        insertRun(other, "PRIMARY", 1, "DRAFT");
    }

    @Test
    void theApproverCannotBeTheSubmitter() {
        UUID same = UUID.randomUUID();
        assertThatThrownBy(() -> insertRun(MONTH, "PRIMARY", 1, "APPROVED", same, same))
                .isInstanceOf(DataAccessException.class);
        insertRun(MONTH, "PRIMARY", 1, "APPROVED", same, UUID.randomUUID());
    }

    @Test
    void aMonthMustBeTheFirstDay() {
        assertThatThrownBy(() -> insertRun(LocalDate.of(1990, 3, 15), "PRIMARY", 1, "DRAFT"))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void aPersonWhoCannotBePaidHasAReasonAndNoAmounts() {
        UUID run = insertRun(MONTH, "PRIMARY", 1, "DRAFT");
        assertThatThrownBy(() -> jdbc.update(
                        "insert into payroll_run_line (id, run_id, person_kind, person_id, person_name, state, reason,"
                                + " earned, fingerprint, computed_at) values (?, ?, 'MANAGER', ?, 'Manoj', 'CANNOT_PAY',"
                                + " 'no salary', 0, 'f', now())",
                        UUID.randomUUID(),
                        run,
                        UUID.randomUUID()))
                .as("zero is not the same as missing")
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update(
                        "insert into payroll_run_line (id, run_id, person_kind, person_id, person_name, state,"
                                + " fingerprint, computed_at) values (?, ?, 'MANAGER', ?, 'Manoj', 'CANNOT_PAY', 'f', now())",
                        UUID.randomUUID(),
                        run,
                        UUID.randomUUID()))
                .as("a reason is required")
                .isInstanceOf(DataAccessException.class);
        jdbc.update(
                "insert into payroll_run_line (id, run_id, person_kind, person_id, person_name, state, reason,"
                        + " fingerprint, computed_at) values (?, ?, 'MANAGER', ?, 'Manoj', 'CANNOT_PAY', 'no salary',"
                        + " 'f', now())",
                UUID.randomUUID(),
                run,
                UUID.randomUUID());
    }

    @Test
    void aLinesNetMustBeEarnedMinusDeductionPlusAdjustments() {
        UUID run = insertRun(MONTH, "PRIMARY", 1, "DRAFT");
        assertThatThrownBy(() -> insertPayableLine(run, "1000.00", "100.00", "50.00", "900.00"))
                .isInstanceOf(DataAccessException.class);
        insertPayableLine(run, "1000.00", "100.00", "50.00", "950.00");
        assertThatThrownBy(() -> jdbc.update(
                        "insert into payroll_run_line (id, run_id, person_kind, person_id, person_name, state, fingerprint,"
                                + " computed_at) values (?, ?, 'TEACHER', ?, 'Tara', 'PAYABLE', 'f', now())",
                        UUID.randomUUID(),
                        run,
                        UUID.randomUUID()))
                .as("a payable line needs its amounts")
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void aPersonIsOnceInARun() {
        UUID run = insertRun(MONTH, "PRIMARY", 1, "DRAFT");
        UUID person = UUID.randomUUID();
        String sql = "insert into payroll_run_line (id, run_id, person_kind, person_id, person_name, state, earned, deduction,"
                + " adjustments, net, fingerprint, computed_at) values (?, ?, 'TEACHER', ?, 'Tara', 'PAYABLE', 1, 0, 0, 1,"
                + " 'f', now())";
        jdbc.update(sql, UUID.randomUUID(), run, person);
        assertThatThrownBy(() -> jdbc.update(sql, UUID.randomUUID(), run, person)).isInstanceOf(DataAccessException.class);
    }

    @Test
    void anAdjustmentOfZeroIsRefused() {
        assertThatThrownBy(() -> insertAdjustment("0.00", "WAITING")).isInstanceOf(DataAccessException.class);
        insertAdjustment("-2000.00", "WAITING");
        assertThatThrownBy(() -> insertAdjustment("10.00", "SOMETHING_ELSE")).isInstanceOf(DataAccessException.class);
    }

    private UUID insertAdjustment(String amount, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into payroll_adjustment (id, person_kind, person_id, person_name, kind, amount, reason, status,"
                        + " created_by, created_at) values (?, 'TEACHER', ?, 'Tara', 'CORRECTION', ?::numeric, 'fix', ?, ?,"
                        + " now())",
                id,
                UUID.randomUUID(),
                amount,
                status,
                UUID.randomUUID());
        return id;
    }

    @Test
    void aPersonCanBePaidForAMonthOnlyOnce() {
        UUID run = insertRun(MONTH, "PRIMARY", 1, "DRAFT");
        UUID line = insertPayableLine(run, "1000.00", "0.00", "0.00", "1000.00");
        UUID person = UUID.randomUUID();
        String sql = "insert into payroll_paid_month (month, person_kind, person_id, run_id, line_id)"
                + " values (?, 'TEACHER', ?, ?, ?)";
        jdbc.update(sql, MONTH, person, run, line);
        UUID run2 = insertRun(MONTH, "SUPPLEMENTARY", 1, "DRAFT");
        UUID line2 = insertPayableLine(run2, "1000.00", "0.00", "0.00", "1000.00");
        assertThatThrownBy(() -> jdbc.update(sql, MONTH, person, run2, line2)).isInstanceOf(DataAccessException.class);
        jdbc.update(sql, MONTH.plusMonths(1), person, run2, line2);
    }

    @Test
    void theLinesOfAnApprovedRunCannotBeChangedOrDeleted() {
        UUID line = approvedRunWithLine();
        assertThatThrownBy(() -> jdbc.update("update payroll_run_line set net = 1, earned = 1, deduction = 0, adjustments = 0 where id = ?", line))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("approved payroll run");
        assertThatThrownBy(() -> jdbc.update("delete from payroll_run_line where id = ?", line))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("approved payroll run");
    }

    @Test
    void theLinesOfADraftRunCanBeChangedAndDeleted() {
        UUID run = insertRun(MONTH, "PRIMARY", 1, "DRAFT");
        UUID line = insertPayableLine(run, "1000.00", "100.00", "0.00", "900.00");
        jdbc.update("update payroll_run_line set warning = 'x' where id = ?", line);
        jdbc.update("delete from payroll_run_line where id = ?", line);
    }

    @Test
    void theAdjustmentLinesOfAnApprovedRunCannotBeChangedOrDeleted() {
        UUID submitter = UUID.randomUUID();
        UUID run = insertRun(MONTH, "PRIMARY", 1, "AWAITING_APPROVAL", submitter, null);
        UUID line = insertPayableLine(run, "1000.00", "0.00", "50.00", "1050.00");
        UUID adjustment = insertAdjustment("50.00", "WAITING");
        UUID copy = UUID.randomUUID();
        jdbc.update(
                "insert into payroll_run_line_adjustment (id, line_id, adjustment_id, kind, amount, reason) values (?, ?, ?,"
                        + " 'CORRECTION', 50, 'fix')",
                copy,
                line,
                adjustment);
        jdbc.update(
                "update payroll_run set status = 'APPROVED', approved_by = ?, approved_at = now() where id = ?",
                UUID.randomUUID(),
                run);
        assertThatThrownBy(() -> jdbc.update("update payroll_run_line_adjustment set amount = 1 where id = ?", copy))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("approved payroll run");
        assertThatThrownBy(() -> jdbc.update("delete from payroll_run_line_adjustment where id = ?", copy))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("approved payroll run");
    }

    @Test
    void anApprovedRunCannotBeChangedOrDeleted() {
        UUID line = approvedRunWithLine();
        UUID run = jdbc.queryForObject("select run_id from payroll_run_line where id = ?", UUID.class, line);
        assertThatThrownBy(() -> jdbc.update("update payroll_run set total_net = 1 where id = ?", run))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("approved payroll run");
        assertThatThrownBy(() -> jdbc.update("update payroll_run set status = 'DRAFT' where id = ?", run))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("delete from payroll_run where id = ?", run))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void paymentEntriesCanBeAddedToAnApprovedRunButNeverChanged() {
        UUID line = approvedRunWithLine();
        UUID entry = UUID.randomUUID();
        jdbc.update(
                "insert into payroll_payment_entry (id, line_id, paid, paid_on, mode, reference, entered_by, entered_at)"
                        + " values (?, ?, true, ?, 'UPI', 'ref-1', ?, now())",
                entry,
                line,
                LocalDate.of(1990, 4, 5),
                UUID.randomUUID());
        assertThatThrownBy(() -> jdbc.update("update payroll_payment_entry set reference = 'x' where id = ?", entry))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("delete from payroll_payment_entry where id = ?", entry))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
        // paid needs a date and a mode; an unknown mode is refused
        assertThatThrownBy(() -> jdbc.update(
                        "insert into payroll_payment_entry (id, line_id, paid, entered_by, entered_at) values (?, ?, true, ?,"
                                + " now())",
                        UUID.randomUUID(),
                        line,
                        UUID.randomUUID()))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update(
                        "insert into payroll_payment_entry (id, line_id, paid, paid_on, mode, entered_by, entered_at) values"
                                + " (?, ?, true, ?, 'BARTER', ?, now())",
                        UUID.randomUUID(),
                        line,
                        LocalDate.of(1990, 4, 5),
                        UUID.randomUUID()))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void eventsAndPayslipsAreAppendOnly() {
        UUID line = approvedRunWithLine();
        UUID run = jdbc.queryForObject("select run_id from payroll_run_line where id = ?", UUID.class, line);
        UUID event = UUID.randomUUID();
        jdbc.update(
                "insert into payroll_run_event (id, run_id, at, actor, kind) values (?, ?, now(), ?, 'START')",
                event,
                run,
                UUID.randomUUID());
        assertThatThrownBy(() -> jdbc.update("update payroll_run_event set reason = 'x' where id = ?", event))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("delete from payroll_run_event where id = ?", event))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");

        UUID payslip = UUID.randomUUID();
        jdbc.update(
                "insert into payroll_payslip (id, line_id, run_id, file_id, issued_at) values (?, ?, ?, ?, now())",
                payslip,
                line,
                run,
                UUID.randomUUID());
        assertThatThrownBy(() -> jdbc.update("update payroll_payslip set file_id = ? where id = ?", UUID.randomUUID(), payslip))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("delete from payroll_payslip where id = ?", payslip))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void anAdjustmentIsNeverDeletedAndAnAppliedOneNeverChanges() {
        UUID waiting = insertAdjustment("10.00", "WAITING");
        assertThatThrownBy(() -> jdbc.update("delete from payroll_adjustment where id = ?", waiting))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("cannot be deleted");
        // a waiting adjustment can move to withdrawn, and then it is final
        jdbc.update(
                "update payroll_adjustment set status = 'WITHDRAWN', withdrawn_by = ?, withdrawn_at = now(),"
                        + " withdraw_reason = 'mistake' where id = ?",
                UUID.randomUUID(),
                waiting);
        assertThatThrownBy(() -> jdbc.update("update payroll_adjustment set reason = 'x' where id = ?", waiting))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("cannot be changed");

        UUID line = approvedRunWithLine();
        UUID run = jdbc.queryForObject("select run_id from payroll_run_line where id = ?", UUID.class, line);
        UUID applied = insertAdjustment("20.00", "WAITING");
        jdbc.update(
                "update payroll_adjustment set status = 'APPLIED', applied_run_id = ?, applied_line_id = ? where id = ?",
                run,
                line,
                applied);
        assertThatThrownBy(() -> jdbc.update("update payroll_adjustment set amount = 99 where id = ?", applied))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("cannot be changed");
        assertThat(jdbc.queryForObject("select amount from payroll_adjustment where id = ?", java.math.BigDecimal.class, applied))
                .isEqualByComparingTo("20.00");
    }

    @Test
    void anAppliedAdjustmentNeedsItsRunAndLine() {
        UUID waiting = insertAdjustment("10.00", "WAITING");
        assertThatThrownBy(() -> jdbc.update("update payroll_adjustment set status = 'APPLIED' where id = ?", waiting))
                .isInstanceOf(DataAccessException.class);
    }
}
