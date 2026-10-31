# Contract: Payroll substitute credit intake (amendment B to spec 013b)

Additive. Same shape and rules as 015's `ReimbursementIntake` and 030's `IncentiveIntake`; 013b run behaviour does not change.

```java
package com.hls.payroll.api;

public interface SubstituteCreditIntake {
    /** Waiting SUBSTITUTE_CREDIT adjustment for a payroll Teacher. Idempotent per sourceRef (an existing non-withdrawn one is kept;
     *  an unapplied one with another amount is replaced). Refused with a reason when the Teacher is not a payroll person or amount <= 0. */
    ReimbursementIntake.OfferResult offer(UUID actor, UUID teacherId, BigDecimal amount, String reason, String sourceRef);

    /** true when nothing is left to pay (withdrawn now, already withdrawn, never offered); false when its run awaits approval or it is applied. */
    boolean withdraw(UUID actor, String sourceRef, String reason);

    ReimbursementIntake.OfferStatus status(String sourceRef);

    /** Amount and run of an applied adjustment, empty otherwise. */
    Optional<Applied> applied(String sourceRef);
    record Applied(UUID runId, LocalDate month, BigDecimal amount) {}
}

/** Admin-raised Correction linked to a credit (013b manual Correction rules). */
public interface CorrectionRaising {
    ReimbursementIntake.OfferResult raiseCorrection(UUID actor, UUID teacherId, BigDecimal amount, String reason, String sourceRef);
    ReimbursementIntake.OfferStatus status(String sourceRef);
}
```

```java
/** Read only: has the month ended and is attendance locked for payroll (013b rule). Added only if no equivalent exists (tasks T002, T009). */
public interface MonthLock {
    boolean isEndedAndLocked(YearMonth month);
}
```

Rules:

1. Kind `SUBSTITUTE_CREDIT`; `sourceRef` `SUBSTITUTE:<requirementId>:<substituteId>` (83 characters, at most 100); reason at most 500, default "Substitute cover: <School>, <DD/MM/YYYY>, <day value> day".
2. Looks up and creates only adjustments of its own kind; Correction refs start `SUBSTITUTE-CORRECTION:`.
3. Draft runs take waiting adjustments at compute; return or cancel frees them; approval applies and publishes `PayrollAdjustmentApplied(sourceRef, "SUBSTITUTE_CREDIT", runId, month)`.
4. An offer after a withdrawal creates a new waiting adjustment.
5. `AdjustmentService` keeps refusing manual `SUBSTITUTE_CREDIT`, reason text updated to "arrives through spec 017b".
6. Audit through `ChangeRecorder.recordLifecycle(actor, PAYROLL_ADJUSTMENT, id, "created", ...)` as 015 does.
7. The payslip and CSV show School, date and day value from the adjustment reason (the existing reserved label).
8. 015, 030 and 013b regression tests must stay green.
