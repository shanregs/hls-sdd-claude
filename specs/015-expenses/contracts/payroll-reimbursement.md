# Contract: Interfaces between Expenses and other modules

All additive. Nothing here changes an approved pay run, a payslip, or an existing endpoint.

## `payroll.api.ReimbursementIntake` (new, implemented in `payroll.run.internal`)

```java
public interface ReimbursementIntake {
    /** Records a WAITING REIMBURSEMENT adjustment for the person, source_ref = sourceRef. Idempotent per sourceRef. */
    OfferResult offer(UUID actor, PersonKind kind, UUID personId, BigDecimal amount, String reason, String sourceRef);

    /** Withdraws the adjustment. Refused (false) when the run holding it is awaiting approval or it is already applied. */
    boolean withdraw(UUID actor, String sourceRef, String reason);

    OfferStatus status(String sourceRef);
}
enum OfferStatus { NONE, WAITING, HELD_BY_DRAFT, HELD_AWAITING_APPROVAL, APPLIED, WITHDRAWN }
```

- `reason` is plain text, at most 500 characters, naming the claim (for example "Expense claim 12 Oct, Travel and Fuel").
- `amount` is the approved claim amount, positive. The adjustment uses the existing rules: attached to a draft run at
  compute, released on cancel or when the person is left out, applied at approval.
- `PersonKind` is the existing payroll kind (`TEACHER`, `MANAGER`, `STAFF`). A claimant with no payroll person answers
  `NONE` with a refused offer and expenses tells the Admin to record the claim paid outside.

## `payroll.api.PayrollAdjustmentApplied` (new event)

`PayrollAdjustmentApplied(String sourceRef, AdjustmentKind kind, UUID runId, LocalDate month)`, published by
`ApprovalService.approve` inside the approval transaction, once per applied adjustment that has a `sourceRef`. Expenses
listens for kind `REIMBURSEMENT` and, in its own transaction step after commit of the run, sets the claim to Paid with
payroll, `payroll_run_id` and `payroll_month`. The listener is idempotent per `sourceRef`.

## `recruitment.api.WonSchools` (new)

```java
public interface WonSchools {
    List<WonSchool> wonBetween(LocalDate from, LocalDate to, UUID zoneIdOrNull);
}
record WonSchool(UUID prospectId, UUID schoolIdOrNull, LocalDate wonOn, UUID zoneId) {}
```

Reads `Prospect.wonAt` converted to the business date (Asia/Kolkata).

## `training.api.InductionBatches` (one method added)

`int completedTeachers(UUID batchId)`, and `List<InductionBatchView> endingBetween(LocalDate from, LocalDate to)` if no
equivalent exists. `batch(UUID)` is used for links as it exists today.

## `expenses.api.ExpenseQueries` (new, for 013b, 014, 028, 036)

```java
public interface ExpenseQueries {
    List<OwedClaim> owedTo(UUID personUserId);                       // Approved, personally paid, unpaid
    List<SpendRow> approvedSpend(YearMonth from, YearMonth to, UUID zoneIdOrNull, String categoryOrNull);
    PendingSummary pending();                                        // counts and totals by age bucket
    CostMeasure costPerSchool(LocalDate from, LocalDate to, UUID zoneIdOrNull);
    CostMeasure costPerTeacher(LocalDate from, LocalDate to, UUID batchIdOrNull);
}
```

Read-only; callers apply their own permission and scope. Events published by expenses: `ExpenseSubmitted(claimId,
claimantId)` and `ExpenseDecided(claimId, claimantId, outcome, reason)` for the notification module.

## For the Android app (spec 036)

- Use `clientRequestId` on create so a retry creates one claim.
- Convert HEIC and WebP photos to jpg and keep each file under 10 MB before upload; the server accepts jpg, png and pdf
  only.
- Show only the actions in `allowedActions`.
- Teacher menu: My Expenses is a normal server-provided item; no client logic by role.
