package com.hls.payroll.api;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * The pay rules as payroll needs them (spec 013a, contracts/pay-rules-interface.md). Results are sealed types so a
 * caller must handle the "no answer" case and can show its reason (FR-008, FR-009, SC-007); no method returns zero to
 * mean "missing". The same inputs and the same stored rows always give the same answer. Bulk forms answer in a fixed
 * number of queries however many people are asked about.
 */
public interface PayRules {

    enum Kind {
        TEACHER,
        MANAGER
    }

    record PersonRef(Kind kind, UUID id) {}

    /** FR-008: the monthly salary in effect on a date. */
    SalaryInEffect salaryOn(PersonRef person, LocalDate date);

    /** FR-009: what one day and a half day of loss of pay are worth on a date. Unrounded. */
    LossOfPayValue lossOfPayValueOn(PersonRef person, LocalDate date);

    /** FR-010, FR-011: the dates of the month the person is payable for. */
    PayableDays payableDays(PersonRef person, YearMonth month);

    /** FR-012: the payable dates the person was unpaid on, with their day values. */
    UnpaidDays unpaidDays(PersonRef person, YearMonth month);

    /**
     * FR-006, FR-007: the policy version in effect on a date; ties on one date go to the version recorded last.
     * The earliest version starts on 2000-01-01, so a date before it still gets that version.
     */
    PayPolicyView policyOn(LocalDate date);

    /**
     * FR-018: the month total deducted. Each unpaid day is valued at the salary in effect on that day, the values are
     * summed, and the sum is rounded once by the policy in effect on the last day of the month.
     */
    TotalLossOfPay totalLossOfPay(PersonRef person, YearMonth month);

    /** Bulk form of {@link #salaryOn}; every asked person is in the map. */
    Map<PersonRef, SalaryInEffect> salariesOn(Collection<PersonRef> people, LocalDate date);

    /** Bulk form of {@link #payableDays(PersonRef, YearMonth)}; every asked person is in the map. */
    Map<PersonRef, PayableDays> payableDays(Collection<PersonRef> people, YearMonth month);
}
