# Research: Substitute Payout Reconciliation

Read from the code on main (2026-11-01) and the neighbouring specs.

## R1. What 017a gives us

`SubstituteCredits.creditsOf(...)` returns only live credits (status ASSIGNED). Voided credits are not readable, so the record kept
here must remember every credit it saw. Events: `SubstitutionAssigned(requirementId, substituteTeacherId, schoolId, schoolName, date)` and `SubstitutionEnded(..., reason)`. The day value is not in `SubstitutionAssigned`, so on any event the listener reads the live credit through `SubstituteCredits` (day value, absent Teacher). Decision: credit key is `(requirementId, substituteTeacherId)`; a reopened requirement with the same substitute reuses the record (new offer after withdrawal, 013b intake rule 4).

## R2. Pricing (Q1 option A)

`payroll.api.PayRules.lossOfPayValueOn(person, date)` is the value of one day for that Teacher on that date, the same value the run deducts. Decision: amount = that value times day value, rounded with the run's rounding (`PayPolicyView`). It returns a "cannot" reason for no salary or no divisor; that reason is stored as the mismatch. Alternatives: flat setting (Q1 B), absent Teacher's value (Q1 C). Risk: an unplaced Teacher may have no working-day divisor; first walkthrough must confirm the value exists for unplaced Teachers (open question to verify in code at implementation; the mismatch path handles it).

## R3. Month ended and locked

013b only runs ended, locked months. Payroll needs to be asked per person and month. Decision: use an existing lock read if `payroll.api` or `attendance.api` has one; otherwise add a minimal `payroll.api.MonthLock.isLocked(YearMonth)` (read only). Alternative (offer immediately, Q3 B) rejected as default: the credit could still change.

## R4. Intake pattern

Same as 015 and 030: waiting `SUBSTITUTE_CREDIT` adjustment, `source_ref = "SUBSTITUTE:" + requirementId + ":" + substituteId` (83 characters, limit 100), idempotent offer, `withdraw` returns false when the run awaits approval or it is applied, `status(sourceRef)`. Attach/release/apply stay 013b behaviour. `AdjustmentService` keeps refusing manual `SUBSTITUTE_CREDIT`. Shared status derivation with the other intakes (extract a package-private helper only if 030 has already done it; otherwise copy and leave a note, to avoid touching 015/030 in this spec).

## R5. Status derivation

Status is derived from (credit live?, month locked?, start month, pricing result, `SubstituteCreditIntake.status`): NONE/WAITING/HELD_BY_DRAFT/HELD_AWAITING_APPROVAL/APPLIED/WITHDRAWN. Table of combinations in data-model.md. A listener on `PayrollAdjustmentApplied` (kind `SUBSTITUTE_CREDIT`) stores the run and month; the daily job re-derives to repair misses.

## R6. After approval

No clawback (Q4 A). The Admin raises a Correction through a payroll public method that wraps the existing manual Correction rules and stores `source_ref = "SUBSTITUTE-CORRECTION:" + payoutId`; its applied event moves the record to Corrected. Alternative (automatic negative adjustment) rejected: wrong deductions are worse than a visible list.

## R7. Teacher view

My Substitutions (017a) calls the payout service for the Teacher's own credits and shows plain words; amounts only when the run is approved (the status APPLIED). No absent Teacher name.

## R8. Reporting boundary

014b reads `substitution.api.SubstitutionCover`; not changed. Payout totals are exposed to others only through a new read interface if a future spec needs it (not now).

## R9. Migration and settings

V160 (V150 highest on main; V130 incentives, V140 preferences). Re-check at implementation. Setting `substitution.payout-start-month` added to `SettingKey` (default the migration month; edited by Admin).

## R10. Open technical items to confirm in code at implementation

1. Does `PayRules.lossOfPayValueOn` work for a Teacher with no School placement on the date? (Fallback: mismatch, already designed.)
2. Is there an existing public month-lock read? (R3.)
3. Does 013b let a Correction be created by another module with a `source_ref`? (R6; else add `CorrectionRaising`.)
