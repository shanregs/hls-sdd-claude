package com.hls.payroll.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hls.identity.user.Role;
import com.hls.support.PayrollRunTestBase;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;

/** Spec 013b T031: even through direct SQL, an approved run is final, while payment entries can still be added. */
class PayRunImmutabilityTriggerTest extends PayrollRunTestBase {

    @BeforeEach
    void clean() {
        resetRuns();
    }

    /** An approved run whose first line also carries a frozen adjustment copy. */
    private record Approved(UUID run, UUID line, UUID lineAdjustment) {}

    private Approved approvedRun() {
        String admin = signInAs(Role.ADMIN).token();
        Draft draft = readyDraft(admin);
        UUID line = UUID.fromString((String) lineOf(admin, draft.run(), draft.teacher()).get("id"));
        UUID adjustment = UUID.randomUUID();
        jdbc.update(
                "insert into payroll_adjustment (id, person_kind, person_id, person_name, kind, amount, reason, status,"
                        + " created_by, created_at) values (?, 'TEACHER', ?, 'Tara', 'CORRECTION', 100, 'test', 'WAITING', ?, now())",
                adjustment,
                draft.teacher(),
                UUID.randomUUID());
        UUID copy = UUID.randomUUID();
        jdbc.update(
                "insert into payroll_run_line_adjustment (id, line_id, adjustment_id, kind, amount, reason)"
                        + " values (?, ?, ?, 'CORRECTION', 100, 'test')",
                copy,
                line,
                adjustment);
        submitAndApprove(admin, draft.run());
        return new Approved(draft.run(), line, copy);
    }

    @Test
    void theLinesOfAnApprovedRunCannotBeUpdatedOrDeleted() {
        Approved a = approvedRun();

        assertThatThrownBy(() -> jdbc.update("update payroll_run_line set person_name = 'X' where id = ?", a.line()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("approved payroll run");
        assertThatThrownBy(() -> jdbc.update("delete from payroll_run_line where id = ?", a.line()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("approved payroll run");
        assertThatThrownBy(() -> jdbc.update("update payroll_run_line set net = net + 1, earned = earned + 1 where run_id = ?", a.run()))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void theLineAdjustmentsOfAnApprovedRunCannotBeUpdatedOrDeleted() {
        Approved a = approvedRun();

        assertThatThrownBy(() -> jdbc.update("update payroll_run_line_adjustment set amount = 5 where id = ?", a.lineAdjustment()))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("delete from payroll_run_line_adjustment where id = ?", a.lineAdjustment()))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void theApprovedRunRowCannotBeUpdatedOrDeleted() {
        Approved a = approvedRun();

        assertThatThrownBy(() -> jdbc.update("update payroll_run set total_net = 1 where id = ?", a.run()))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("update payroll_run set status = 'DRAFT' where id = ?", a.run()))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("delete from payroll_run where id = ?", a.run()))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void thePayslipsAndTheEventsOfAnApprovedRunCannotBeChanged() {
        Approved a = approvedRun();

        assertThatThrownBy(() -> jdbc.update("update payroll_payslip set issued_at = now() where run_id = ?", a.run()))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("delete from payroll_payslip where run_id = ?", a.run()))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("delete from payroll_run_event where run_id = ?", a.run()))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("update payroll_adjustment set amount = 7 where status = 'APPLIED'"))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void paymentEntriesCanStillBeAddedToAnApprovedRun() {
        Approved a = approvedRun();

        int added = jdbc.update(
                "insert into payroll_payment_entry (id, line_id, paid, paid_on, mode, reference, entered_by, entered_at)"
                        + " values (?, ?, true, current_date, 'CASH', 'r1', ?, ?)",
                UUID.randomUUID(),
                a.line(),
                UUID.randomUUID(),
                Timestamp.from(Instant.now()));

        assertThat(added).isEqualTo(1);
    }
}
