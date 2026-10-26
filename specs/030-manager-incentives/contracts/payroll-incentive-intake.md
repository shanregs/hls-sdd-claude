# Contract: Payroll incentive intake (amendment A to spec 013b)

Additive. Same shape and rules as 015's `payroll.api.ReimbursementIntake`; 013b run behaviour does not change.

```java
package com.hls.payroll.api;

public interface IncentiveIntake {
    enum OfferStatus { NONE, WAITING, HELD_BY_DRAFT, HELD_AWAITING_APPROVAL, APPLIED, WITHDRAWN }

    /** Waiting INCENTIVE adjustment for a payroll Manager. Idempotent per sourceRef (an existing non-withdrawn one is kept).
     *  Refused with a reason when the Manager is not a payroll person or the amount is not above zero. */
    ReimbursementIntake.OfferResult offer(UUID actor, UUID managerId, BigDecimal amount, String reason, String sourceRef);

    /** true when nothing is left to pay (withdrawn now, already withdrawn, never offered); false when the run holding it
     *  awaits approval or it is already applied. */
    boolean withdraw(UUID actor, String sourceRef, String reason);

    OfferStatus status(String sourceRef);
}
```

Rules:

1. Kind is `INCENTIVE`; `sourceRef` is `INCENTIVE:<incentiveId>` (at most 100 characters); reason at most 500 characters, default "Manager incentive: closed School".
2. Looks up and creates only adjustments of kind `INCENTIVE` (`findBySourceRefAndKindOrderByCreatedAtDescIdDesc`).
3. A draft run takes the waiting adjustment at compute; return or cancel frees it; approval applies it and publishes `PayrollAdjustmentApplied(sourceRef, "INCENTIVE", runId, month)`.
4. An offer after a withdrawal creates a new waiting adjustment (re-offer after a hold).
5. `AdjustmentService` keeps refusing manual recording of `INCENTIVE` ("spec 030" reason text updated to say it arrives only through the intake).
6. Audit: `ChangeRecorder.recordLifecycle(actor, PAYROLL_ADJUSTMENT, id, "created", ...)` as 015 does.
7. The implementation reuses `ReimbursementIntakeImpl`'s status derivation. Preferred: extract a package-private helper shared by both intakes without changing 015 behaviour; its tests must stay green.
