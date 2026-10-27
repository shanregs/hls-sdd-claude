# Research: Manager Incentives

## 1. What the code actually offers (read on `main`, 2026-10-27)

- `schoolbilling.api.MouApprovals.stateOn/stateOfAll` returns `MouApprovalState(schoolId, contractId, state, approvedOn, documentAttached,
  countsAsApproved)`. `approvedOn` is the decision time of an approval taken in the approval step; it is **null** for a MoU shown as
  `APPROVED_BEFORE_APPROVAL_STEP` (recorded before spec 031) and for any state that is not approved.
- Events (`schoolbilling.api`): `MouSubmitted(contractId, schoolId, submittedBy, resubmitted)` published on submit, on resubmit, and
  when a document replacement resets an approved MoU to pending (`resubmitted = true`); `MouDecided(contractId, schoolId, outcome,
  reason, submittedBy, decidedBy, termsAmended)` published in the decider's transaction. **`MouDecided` has no date.**
- `recruitment.api.ProspectOwners.ownerOfSchool(schoolId)` returns the owner's user id of the won prospect linked to a School.
- `payroll`: `PayAdjustment` has kind `INCENTIVE` (allowed by the `V53` check constraint), a `source_ref` (100 characters) and statuses
  WAITING, APPLIED, WITHDRAWN. `AdjustmentService.RESERVED_BY` refuses manual `INCENTIVE`. `ReimbursementIntake` (015) is the working
  pattern: idempotent offer by source reference, withdraw refused when the run awaits approval or has applied it, status derived from the
  attached run. `ApprovalService` publishes `PayrollAdjustmentApplied(sourceRef, kind, runId, month)` for each applied adjustment with a source reference.
- Settings: runtime catalog `SettingKey` (spec 011), code constants. Permissions: `PermissionModule` enum, `PermissionEligibility`,
  `PermissionMatrixService` seeds, `NavigationCatalog`.
- There is no shared approval class; 015 and 027 each have a private decision service.

## 2. Decisions

**D-1 Event plus read, plus a daily reconcile.** On `MouDecided` with outcome APPROVED the earning service reads
`MouApprovals.stateOn(schoolId, today)` for the approval date and whether it counts. The event is delivered after the MoU
transaction (async application-module listener) so a failure in 030 can never block approving a MoU. Because an async listener can
miss an event (crash), a daily `ReconcileJob` re-runs the same idempotent earning rule for every School whose MoU counts as approved with
a non-null approval date later than go-live. Alternative: a synchronous listener in the MoU transaction (rejected: couples 031 to 030
failures). Alternative: polling only (rejected: a day's delay and no precise timestamp on the first look).

**D-2 Closure date and the missing approval date.** The closure date is the business date (Clock zone) of `MouApprovalState.approvedOn`.
If a state counts as approved but `approvedOn` is null (state `APPROVED_BEFORE_APPROVAL_STEP`), 030 creates no incentive and records a
"no earner" row with reason `APPROVED_BEFORE_APPROVAL_STEP` ("excluded, approved before the approval step"); it is visible to the Director
and does not earn until a later real approval of a **new** or reset MoU for the same School gives a
non-null date and then counts as earning, because the rule is "one incentive per School after go-live", not "per legacy School". The
no-earner row is then marked resolved. Alternative: use the event time when the date is null (rejected: legacy MoUs never raise an event,
so there is no honest date, and clarification 5 says they do not earn).

**D-3 Go-live.** `V130` creates a one-row table `incentive_program` with `starts_at = now()` of the migration. An approval with
`approvedOn` before `starts_at` earns nothing and is recorded as no-earner reason `BEFORE_GO_LIVE`. This answers clarification 5 for MoUs approved
between the 031 release and this one, and makes the reconcile job safe. Alternative: a setting date (rejected: option C of Q5 was not chosen).

**D-4 Earner rule.** Owner user id from `ProspectOwners`; the earner is that user only if `ManagerQueries.managerByUserId` finds an
active Manager who is a payroll person (`payroll.api` accepts kind MANAGER). Otherwise no incentive: no-earner reasons `NO_PROSPECT`,
`NO_OWNER`, `OWNER_NOT_MANAGER`. The owner at approval time is the one that earns; the incentive stores the Manager and never moves.
A no-earner row is replaced by an incentive if a later check finds an earner (for example the prospect was linked afterwards).

**D-5 Unique per School.** `school_id` is unique on `incentive`, so duplicate events, the reconcile job and re-approval cannot create
a second row (an insert conflict is read back as "already earned"). A rejected incentive is final for that School (a rejection is a
business decision); the Director, or the Zone Manager for Schools in their Zone, may reopen it with a reason (tasks T038; decided 2026-10-27) so the rule "once per School" holds.

**D-6 Amount.** `SettingKey.INCENTIVE_AMOUNT` default 5000.00 read at creation and stored; later changes affect new rows only.

**D-7 Approval authority.** Director: any incentive. Manager: only if they are the Zone Manager of the School's Zone
(`ManagerQueries.managerOfSchool(schoolId)`), including when they are also the earner; the flag `approvedByEarner` is stored and
audited. Admin, Teacher, System refused (permission eligibility plus a server check). A School with no Zone Manager: Director only.
Rejection needs a reason. The decision takes a row lock and compares `version`.

**D-8 Payment link (amendment A to 013b).** `payroll.api.IncentiveIntake` mirrors `ReimbursementIntake`: `offer(actor, managerId,
amount, reason, sourceRef)`, `withdraw(actor, sourceRef, reason)`, `status(sourceRef)`, with `sourceRef = "INCENTIVE:" + incentiveId`. The
implementation reuses `AdjustmentService` rules (waiting adjustment attached to a draft at compute, applied at approval, stays waiting
for a person who cannot be paid). `PayrollAdjustmentApplied` with kind `INCENTIVE` is consumed by 030 to set `PAID_IN_RUN` with run id
and month. The month is simply the month of the run that applies it (clarification 2). Alternative: have 030 call
`AdjustmentService` directly (rejected: internal class, would break module boundaries). Alternative: generalise `ReimbursementIntake`
to all kinds (rejected: breaks 015's contract and tests; separate small interface is additive).

**D-9 Hold when the MoU stops counting as approved.** Payroll cannot read MoU state, so 030 withdraws the offer. On `MouSubmitted` (any
resubmit or reset) or `MouDecided` REJECTED for a School with an APPROVED incentive, 030 calls `IncentiveIntake.withdraw`. Result: true
=> status `HELD` ("MoU not approved"); false because the run awaits approval => the incentive stays APPROVED with a visible flag
`mouChanged` and the Director is notified (the run is theirs to return or approve; recorded in the audit), because releasing a line from a run under
review would change what the Director is approving; false because already applied => no change (US5 scenario 3). On a new `MouDecided`
APPROVED for a HELD incentive, 030 offers again with the same source reference; `ReimbursementIntake`'s rule "offer after withdrawn creates a
new waiting adjustment" is reused. The reconcile job re-checks both directions daily.

**D-10 Not-payable person.** If the Manager has no pay record or has left, payroll lists them as unable to be paid and keeps the
adjustment waiting (013b FR-019). 030 shows the incentive as APPROVED with the payroll status text from `IncentiveIntake.status`. No extra rule.

**D-11 Notifications.** New types `INCENTIVE_PENDING` (active Directors and the School's Zone Manager; the earner is not excluded when they are that Zone Manager, since they may approve) and `INCENTIVE_DECIDED` (the earner, minus the decider). Delivered by a new `IncentiveEventListener` in `notification` for api events `IncentiveEarned` and `IncentiveDecided`. The `NotificationType` column is already `VARCHAR(60)`.

**D-12 Dashboard (028).** `reporting` has a tile SPI and approvals-inbox SPI; 030 implements `incentive.api.IncentiveSummary`
(counts pending, approved awaiting payroll, amounts this month, no-earner count) and the inbox entry. If those SPI classes differ at
implementation they are adapted in `reporting`, not in `incentive`.

**D-13 CSV.** Same columns as the list; server neutralises leading `=`, `+`, `-`, `@` in text cells as 013b's export does.

## 3. Impacts on other specs

- 013b: amendment A (new `IncentiveIntake`, its implementation, a unit of tests, a contract note); no behaviour change for runs without incentives.
- 031: none (read-only). Note for 031's contract: `MouDecided` has no date, so 030 reads `approvedOn` from `MouApprovals`.
- 023: none (uses `ProspectOwners`).
- 028: tiles and inbox entry added by 030.
- 011: one new setting. 010: two new notification types. 002/004: new permission module and nav item. Constitution: PATCH 2.5.15.
- 014 (reports) may later read `IncentiveSummary` for Manager productivity.

## 4. Decided by the user on 2026-10-27

- A rejected incentive may be reopened with a reason by the Director or the Zone Manager of the School's Zone (D-5).
- A MoU withdrawn while a run awaits approval: the incentive keeps its line in the run, is flagged `mou_changed` and the Director is notified (D-9).
- Go-live is the time migration V130 runs (D-3).

No open decisions remain.
