# Contract: Payroll day-rate work port and day-rate reads (amendment B to spec 013b, A to 013a)

Additive. Payroll owns the interfaces; `substitution` implements them, so `payroll` never depends on `substitution` (Principle VII).

```java
package com.hls.payroll.api;

/** Implemented by substitution (017b). Days of Substitute-type Teachers that are Present and in a locked substitute-month. */
public interface DayRateWork {
    record WorkedDay(UUID dayId, UUID teacherId, UUID schoolId, String schoolName, LocalDate date, BigDecimal dayValue) {}
    record NotReady(UUID teacherId, String reason) {}   // e.g. "attendance not locked", "not an Active Substitute"
    /** Present days in the month for locked substitute-months, grouped by Teacher; fixed query count. */
    Map<UUID, List<WorkedDay>> presentDays(YearMonth month);
    /** Substitutes with Present or unmarked days in the month that cannot be paid yet, with the reason. */
    List<NotReady> notReady(YearMonth month);
    /** Substitutes with unpaid Present days in a month whose primary run is approved (for the supplementary run). */
    Map<UUID, List<WorkedDay>> unpaidPresentDays(YearMonth month);
}

/** Implemented by payroll (013a amendment): the day rate of a designation on a date. */
public interface DayRates {
    sealed interface RateOn permits Found, None {}
    record Found(BigDecimal perDay, LocalDate effectiveOn) implements RateOn {}
    record None(String reason) implements RateOn {}
    RateOn rateOn(UUID designationId, LocalDate date);
    Map<UUID, RateOn> ratesOn(Collection<UUID> designationIds, LocalDate date);
}

/** Implemented by payroll (013b amendment): what an approved or open run holds for a substitute, for reconciliation. */
public interface DayRatePay {
    record PaidDay(UUID dayId, UUID runId, YearMonth runMonth, YearMonth workMonth, BigDecimal rate, BigDecimal amount, RunState state) {}
    enum RunState { DRAFT, AWAITING_APPROVAL, APPROVED }
    List<PaidDay> daysInRuns(Collection<UUID> dayIds);   // one row per day per run that holds it
}
```

Rules:

1. A new payroll person source "Substitute" is added (013b amendment): included when `presentDays(month)` has the Teacher; no salary, no base, no loss of pay; earned = sum of day value times the `DayRates` rate on each date for the designation the Teacher held that date (read through `designation.api`), rounded by the run's rule.
2. The line's frozen working stores each `WorkedDay` and the rate; the payslip renders "N days x ₹R" (one row per rate).
3. A Substitute that cannot be priced (no designation, no rate, not Active) is a `cannot pay` line with the reason, as 013b already does for other people; `notReady` Substitutes appear as "not ready".
4. A supplementary run (013b) for a month contains the Substitutes returned by `unpaidPresentDays(month)`.
5. Approval publishes the existing `PayrollRunApproved`; the reconciliation reads `DayRatePay.daysInRuns` to mark days Paid in run and to detect days changed after payment.
6. The reserved adjustment kind `SUBSTITUTE_CREDIT` stays unused by this feature (manual recording stays refused with the text "substitutes are paid by day rate, spec 017b"). Corrections use the existing Correction kind; `CorrectionRaising.raiseCorrection(actor, teacherId, amount, reason, sourceRef)` is added to `payroll.api` if no cross-module path exists (`sourceRef` = `SUBSTITUTE-CORRECTION:<dayId>`).
7. 013b, 015 and 030 regression tests must stay green; a run with no Substitute is byte-for-byte unchanged.
