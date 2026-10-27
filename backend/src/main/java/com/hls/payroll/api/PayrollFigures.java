package com.hls.payroll.api;

import com.hls.payroll.api.PayRules.Kind;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-only figures of the pay runs, for reports (spec 014a, decided amendment to 013b; 014b reads the same). It answers
 * from the stored run lines and totals only, so a figure here equals the run total and the run export. Nothing here is exposed
 * over HTTP and nothing can change a run.
 */
public interface PayrollFigures {

    /** Where a run is; {@code PAID} is an approved run with every payable line paid. */
    enum RunState {
        DRAFT,
        AWAITING_APPROVAL,
        APPROVED,
        PAID
    }

    /** The totals of the month run, the same numbers as the run header. */
    record RunSummary(
            UUID runId,
            YearMonth month,
            RunState state,
            BigDecimal gross,
            BigDecimal lossOfPay,
            BigDecimal reimbursements,
            BigDecimal net,
            int payable,
            int cannotPay) {}

    /**
     * One person line. The amounts are null for a person who cannot be paid (never zero for missing); {@code zoneId} and
     * {@code schoolId} are where the person was placed (a Teacher in the month, a Manager now) and may be null.
     */
    record PersonFigures(
            UUID personId,
            Kind kind,
            String name,
            UUID zoneId,
            UUID schoolId,
            boolean payable,
            BigDecimal gross,
            BigDecimal lossOfPay,
            BigDecimal reimbursements,
            BigDecimal net) {}

    /** One month of a person own history. */
    record OwnMonth(
            YearMonth month,
            RunState state,
            boolean payable,
            BigDecimal gross,
            BigDecimal lossOfPay,
            BigDecimal reimbursements,
            BigDecimal net) {}

    /** The run of the month (the latest that is not cancelled); empty when there is none. */
    Optional<RunSummary> runOf(YearMonth month);

    /** The people of the run of the month with their figures; empty when the month has no run. */
    List<PersonFigures> peopleOf(YearMonth month);

    /** The runs of the months {@code from..to} inclusive, oldest first; a month with no run is absent. */
    List<RunSummary> runsBetween(YearMonth from, YearMonth to);

    /** The newest month with an approved run, if any. */
    Optional<YearMonth> lastApprovedMonth();

    /** The person own lines in approved runs, newest month first (their payslip history). */
    List<OwnMonth> historyOf(Kind kind, UUID personId);
}
