# Research: Expenses

Decisions taken before design, with the real code as the evidence (checked 2026-10-20 on main).

## 1. A new `expenses` module

- **Decision**: new module `com.hls.expenses` with `api`, `internal`, `web`, like `tasks` and `payroll`.
- **Rationale**: own entities, permission keys and screens; four later consumers (013b, 014, 028, 036). Spring Modulith and
  an ArchUnit rule test per module are the house pattern (`TasksModuleRulesTest`).
- **Alternatives**: inside `payroll` (rejected: payroll must receive amounts, not own claims); inside `tasks`
  (rejected: unrelated lifecycle).

## 2. Bills in the shared file store

- **Decision**: `FileStore.store(actor, "EXPENSE_CLAIM", claimId, name, bytes)`. `expenses` keeps an `expense_bill` row per
  file (file id, added by, added at, removal fields) and counts live bills itself.
- **Rationale**: the store checks type by extension and content and keeps removed files with who, when and why
  (`remove(actor, fileId, reason)`). It accepts docx, xlsx and txt as well, so `expenses` rejects anything but jpg, png and
  pdf before storing. Linking is by free-text owner type plus id, so access control stays with the caller; the download
  endpoint checks the claim first (pattern: tasks `/files/{fileId}`).
- **Alternatives**: a bytea column (rejected: duplicates the store); allowing docx (rejected by the spec).
- **Consequence for spec 036**: HEIC and WebP are refused by the store; 036 converts to jpg before upload. Recorded in the
  contract.

## 3. Getting reimbursements into payroll

- **Finding**: `PayAdjustment.AdjustmentKind` already has `REIMBURSEMENT` (reserved "by spec 015"); the table has
  `source_ref VARCHAR(100)` unused so far. `AdjustmentService.record` is public but lives in `payroll.run.internal` (not
  reachable from another module) and refuses reserved kinds. Run flow: `RunComputation` attaches WAITING adjustments to a
  draft at compute time; `RunService.cancel` detaches them back to WAITING; `ApprovalService.approve` marks them APPLIED.
  `PayrollRunApproved` carries no adjustment ids. A WAITING adjustment can be withdrawn unless its run is awaiting
  approval.
- **Decision**: add to `payroll.api` (additive, no change to frozen runs):
  `ReimbursementIntake.offer(actor, personKind, personId, amount, reason, sourceRef)` creating a WAITING `REIMBURSEMENT`
  adjustment with `source_ref = claim id`, idempotent per `sourceRef`; `withdraw(actor, sourceRef, reason)` returning
  refused when the adjustment is held by a run awaiting approval or already applied; `status(sourceRef)`
  (`WAITING`, `HELD_BY_DRAFT`, `HELD_AWAITING_APPROVAL`, `APPLIED`, `WITHDRAWN`, `NONE`); and event
  `PayrollAdjustmentApplied(sourceRef, kind, runId, month)` published by `ApprovalService.approve` in the same
  transaction for each applied adjustment.
- **Rationale**: payroll's existing attach, detach and apply already produce the spec's rules: a claim is offered once;
  a cancelled draft frees it; an approved run settles it; a person left out of a run keeps it waiting. 015 only decides
  the claim's own status. The approved claim is "owed" until the event arrives. A claim approved for a person with no pay
  run (left the company) stays WAITING and the Admin records it paid outside (which withdraws the adjustment).
- **Person mapping**: Teacher user to `TEACHER`, Manager to `MANAGER`, Admin or Director to `STAFF` (the three payroll
  person kinds). Verify in the foundation slice that an Admin or Director claimant resolves to a payroll person; if no
  payroll person exists the offer is refused with a clear reason and the Admin records it paid outside.
- **Alternatives**: payroll pulls owed claims at run start (rejected: needs an owed-claims port plus new attach logic and
  breaks "cancel releases"); 015 writes the payroll table (rejected: Principle VII).
- **Process**: the 013b spec and plan already say 015 will create these; add a short amendment note to 013b's spec and
  contract rather than reopening it.

## 4. Never two claims from one submission

- **Decision**: client sends `clientRequestId` (UUID) on create; unique index on (claimant, clientRequestId) returns the
  existing claim on a repeat (same body) or refuses on a different body.
- **Rationale**: no idempotency-key mechanism exists in the codebase; duplicate protection elsewhere is a database key.
  Spec 036's offline sync needs this (SC-010).

## 5. Duplicate warning

- **Decision**: on submit the server looks for another non-Rejected claim of the same claimant, date, amount and category
  (not itself). If found and `confirmDuplicate` is not true, answer 409 with the matching claim ids; when confirmed, the
  confirmation (who, when, which claims) is stored on the claim history.
- **Rationale**: FR-008 asks for a recorded confirmation; a conditional error keeps the rule on the server for web and
  Android.

## 6. Cost measures: sources and the meaning of "became active"

- **Finding**: no dated "became active" read interface exists. `Prospect.wonAt` (`won_at`) is set when a prospect is
  won, and `ProspectWon` is published by `WinService`. The School record has only an `active` flag.
- **Decision**: cost per School uses **Schools won in the period** (by `wonAt` in the business time zone), through a new
  additive `recruitment.api.WonSchools.wonBetween(from, to)` returning prospect id, school id (when created) and won date,
  optionally narrowed by Zone. The spec's words "became active (a prospect won, spec 023)" mean this.
- **Numerator**: approved Marketing claims plus Travel and Fuel claims whose stored link kind is a marketing visit,
  meeting, call or campus drive, by expense date in the period. Link kinds come from `MarketingActivities`,
  `DriveActivities` (C1); the exact kind strings are produced in the `*Impl` classes and are copied into a constant
  at implementation. A Travel and Fuel claim linked to a task or unlinked is overhead and excluded.
- **Cost per Teacher trained**: approved Training Stay claims linked to induction batches whose end date falls in the
  period, divided by the Teachers who completed those batches. `InductionProgress.completedTeacherIds()` is global, so
  add `InductionBatches.completedTeachers(batchId)` (additive, in `training.api`) returning the count of completers of
  one batch. Confirm the training module can answer this per batch in the first task; if it cannot, the measure shows
  "No Teachers trained yet" and a task is raised in training.
- **Zero divisors**: both measures return the cost, the divisor and a state (`OK`, `NO_SCHOOLS`, `NO_TEACHERS`); never
  a division.

## 7. Budget arithmetic

- **Decision**: one `expense_area_budget` row per (area, month); area is a Zone id or null for Head office (unique index on
  `coalesce(zone_id, nil-uuid), month`). Actual counts claims in Approved, Paid and Paid by company by **expense date**
  in the month; pending counts Submitted claims by expense date; remaining = budget - actual (pending shown beside it,
  not subtracted); over = actual > budget. No budget row: `budget = null` and a "No budget set" state, never 0.
- **Reversal effect**: a reversed approval returns to Submitted, so it moves from actual to pending.
- **Computation**: SQL aggregates (sum, count) grouped by area, category and claimant; no per-claim loads. Money is
  `NUMERIC(14,2)`.

## 8. Approver rules and races

- **Decision**: approve, reject and reverse require `EXPENSES.APPROVE` (reverse also needs `EXPENSES.PROCESS` or
  `APPROVE`; Admin holds Process by default as in the spec's table; the Director reverses through Approve). A user
  cannot decide a claim whose claimant is the same user, even holding both roles. The decision is
  `UPDATE ... WHERE id = ? AND status = 'SUBMITTED'` plus `@Version`; zero rows means "already decided by X at T".
- **Rationale**: two approvers; a flaky double click; matches the payroll approval pattern.
- **Reversal guard**: allowed only while `ReimbursementIntake.status` is `WAITING` or `HELD_BY_DRAFT` (withdraw releases it
  from a draft), refused when awaiting approval or applied, and refused once Paid.

## 9. Link choices (C1)

- **Decision**: Travel and Fuel links come from `DriveActivities.plannedBetween`, `MarketingActivities.plannedBetween` and
  `TaskActivities.plannedBetween` for the claimant as owner over the window claim date -/+ 0 days (the spec offers the
  claimant's own activities, or any that fall on the claim's date). Training sessions (024) are not built, so that kind is
  absent. The selected link is stored as `link_kind`, `link_ref` (id), `link_date`, `link_label` (place or name), so it
  survives later cancellation (FR-011). Training Stay links to `InductionBatches.batch(id)`, stored the same way.
- **Alternatives**: store only the id (rejected: FR-011).

## 10. Permissions, menu and notifications

- `PermissionModule`: add `EXPENSES(VIEW, CREATE, EDIT, APPROVE, PROCESS, EXPORT)` and `MY_EXPENSES(VIEW, CREATE, EDIT,
  DELETE)`; eligibility: EXPENSES for Admin and Director only (a new restricted map, as for payroll), MY_EXPENSES for
  every role except System; seeds per the spec's table; `AccessModelService` scopes: EXPENSES org-wide, MY_EXPENSES own.
- `NavigationCatalog`: OPERATIONS → Expenses (Admin, Director) and OPERATIONS → My Expenses (Admin, Director, Manager,
  Teacher). Teacher already has Teacher-only entries; this is the first Teacher OPERATIONS item and the section must
  appear for Teacher without other items.
- `NotificationType`: `EXPENSE_SUBMITTED`, `EXPENSE_DECIDED`, `EXPENSE_REVERSED` (under the 60-character column limit);
  texts in `MessageFactory`; a listener on the `expenses.api` events.

## 11. Frontend pattern

Features folder per module, API file with typed helpers on `features/common/masterDataApi`, pages and dialogs, tests in
`__tests__` with `vitest-axe`; routes registered in `App.tsx` under `RouteGuard`; menu comes from the server. A
permission-derived `allowedActions` list on each claim (FR-031) drives the buttons.

## 12. Items left for the first tasks to verify

1. Admin or Director as claimant resolves to a payroll person (section 3).
2. Per-batch completed Teachers (section 6).
3. Exact C1 kind strings for marketing and drive links (section 6).
4. Whether a Manager may pick any Zone (spec assumption keeps "any active Zone or Head office"); if the owner prefers the
   assigned Zones only, it is one validation in `ClaimService`.
