# Research: School Contracts and Billing (spec 012)

Every open point from the spec is settled here. No `NEEDS CLARIFICATION` remains.

## 1. Who owns "which School is this Teacher in?"

- **Decision**: `schoolbilling` owns the assignment rows (`contract_assignment`). `teacher` keeps asking the
  same question through a new interface in `teacher.api` (`TeacherPlacementSource`), which `schoolbilling`
  implements. `teacher.internal` stops owning `teacher_placement`.
- **Rationale**: the constitution gives the Teacher–School–Manager contract to `schoolbilling` (Principle VII)
  and calls the placement "interim". `schoolbilling` must read Teacher data through `teacher.api`, so
  `teacher` cannot call `schoolbilling` back; an interface owned by `teacher` and implemented by
  `schoolbilling` keeps the dependency pointing one way. `attendance`, `leave` and `organization` keep using
  `TeacherDirectory` unchanged, so nothing downstream moves.
- **Alternatives**: (a) keep `teacher_placement` where it is and derive the contract from School and date.
  Least change, but the assignment would stay outside the module that prices it, and 013 and 017 need "the
  rate that applied to this Teacher on this day" from one place. (b) `teacher` calls `schoolbilling`
  directly. Creates a module cycle; Spring Modulith would fail the build.

## 2. How the existing placements move

- **Decision**: one Flyway migration copies every `teacher_placement` row (all statuses, same ids) into
  `contract_assignment`, creates a `RATE_PENDING` contract for each School that has placements, then a second
  migration drops `teacher_placement`. A rollback is a restore from backup, as for earlier migrations.
- **Rationale**: same ids keep audit entries (`TEACHER_PLACEMENT`, entity id = Teacher) valid. Two migrations
  keep the copy reviewable apart from the drop. The `RATE_PENDING` contract starts on the School's earliest
  placement date and stays open-ended; Admin enters the rate afterwards. The migration writes these contracts directly, so a School with no Zone Manager still gets
  one; the "needs a Zone Manager" check applies to contracts created through the screen.
- **Alternatives**: a view called `teacher_placement` over the new table (rejected: hides the change and keeps
  the old name alive); leaving the old table as a read-only archive (rejected: two truths).

## 3. Placement behaviour is kept, now against positions

- **Decision**: the rules already proven in spec 005 are moved, not redesigned: dated rows, a future date
  schedules a move, "current" is evaluated against today, rows are never overwritten (`ACTIVE`, `CANCELLED`,
  `CORRECTED`), and an exclusion constraint stops overlap for one Teacher. The service becomes
  `AssignmentService` in `schoolbilling.internal`; the endpoints `POST /api/v1/teachers/{id}/placements` and
  `DELETE /api/v1/teachers/{id}/placements/pending` stay at the same paths, so the Teachers screen keeps working,
  and are served by a thin `teacher.web` handler that calls `TeacherPlacementSource`.
- **New rules**: an assignment maps the Teacher to a vacant position of the School's contract covering its
  start date; if the request names no position and the contract is `SAME_FOR_ALL`, the next vacant position is
  used; for `PER_TEACHER` the position must be named. Without a contract, or with all positions filled, the user
  gets a plain refusal. A migrated placement has no position until it is mapped.
- **Rationale**: it is the least risky way to satisfy FR-005 and FR-007, and it adds only what the MoU needs.
- **Re-mapping to a new contract (FR-004a)**: one transaction that, for each Teacher listed, ends the current
  assignment the day before the new contract starts and inserts a new one under the chosen new position; the
  placement span the rest of the system sees is continuous.

## 4. The contract is the MoU

- **Decision**: a contract records the MoU as the business signs it: School, N Teachers, `SAME_FOR_ALL` (one
  salary) or `PER_TEACHER` (a salary for each of N positions), dates, `signed_on`, and the signatories (School:
  name and designation, one or more; HLS: the School's Zone Manager and one Director). It holds N rows in
  `contract_position`. A lump sum is dropped as a basis (user direction, 2026-10-05).
- **Versioning**: a contract is immutable once created, except that its end date can be set; a change of salary,
  count or signatory is a new contract that ends the old one the day before. A `RATE_PENDING` contract (carried
  over from the placements) takes its MoU in place, once, while no receivable exists.
- **Zone Manager**: not stored as a contract field. The responsible Manager is the School's Zone Manager from
  `ManagerQueries.managerOfSchool` whenever needed. The HLS signatory for the Manager is that person at signing,
  stored as a name, designation and user id in `contract_signatory` (a record of who signed, so it never changes
  when the Zone Manager does). A School with no Zone Manager cannot get a contract through the screen.
- **HLS Director**: chosen from the active Directors through the same public `identity` lookup spec 010's
  recipient resolver uses; the contract keeps the user id and name.
- **Rationale**: the signed MoU is a legal-business record, so it is kept whole and unchangeable; later specs
  (013 payroll, 017 substitution) can show "the salary the School pays for this position" without guessing.
- **Alternatives**: keep the lump sum as a third mode (dropped: the user listed two salary modes and a lump sum
  would need its own proration rule); per-Teacher salary on the assignment instead of the position (rejected:
  the salary belongs to the MoU and must exist before a Teacher is recruited).

## 5. Pro-rating and the working days

- **Decision**: a Teacher's line for a month is the sum, over the contract periods the Teacher was mapped in, of
  `position salary x (the Teacher's working days at the School within that period / the Teacher's working days
  in the month)`. The working days come from the Teacher's own attendance (spec 008), which follows the School's
  calendar for every day the Teacher is placed, so billing has no School calendar of its own. Add
  `AttendanceReadApi.billingDays(UUID teacherId, YearMonth month)` returning `BillingDays(Map<UUID,
  List<LocalDate>> workingDatesBySchool, int workingDaysInMonth)`, built from the same day plan the rollup uses
  (`RollupCalculator.plan`). It returns the dates, not just counts, so a month can be split between two
  contracts of one School. Days when the Teacher is not placed count toward the month only when the default
  calendar says they are working days, so a Teacher who joins mid-month is billed for the part they were there
  (13 of 26 is half).
- **Rationale**: the user's direction (2026-10-05) is that no School-specific calendar is needed in billing
  because the Teacher's attendance reflects it. One source for working days keeps billing, attendance and
  payroll (spec 013) agreeing. Amounts are `NUMERIC(12,2)`, computed in `BigDecimal` and rounded half-up to
  paise once per Teacher line, so the total is the sum of the printed lines.
- **Alternatives**: a School calendar API (dropped: duplicates what attendance does per Teacher); the leave
  module's working-days method (rejected: it drops the days before placement, so a mid-month joiner would be
  billed in full).

## 6. Month close

- **Decision**: a `billing_month` row per month (`OPEN`/`CLOSED`). Closing is one Admin action for the whole
  organization month. It is refused unless, for every Teacher assigned in that month,
  `AttendanceReadApi.isLocked(teacherId, month)` is true, and the refusal lists the Teachers still open.
  Closing freezes the `CURRENT` receivables of the month. A month can only be closed when the previous
  billable month is closed.
- **Rationale**: spec 008 locks per Teacher-month, so "attendance is locked" has to mean all Teachers in
  the month. One close per month matches how payroll will run (spec 013). Reopening is not offered; the
  correction path is an adjustment line in an open month.
- **Alternatives**: closing per School (rejected: many small actions, and payroll needs one clean boundary).

## 7. Receivable generation

- **Decision**: `ReceivableService.generate(month)` is Admin- or Director-triggered ("Calculate") and also run
  for a single School after an assignment or contract change in an open month. It writes a new `CURRENT` row
  and marks the previous one `SUPERSEDED`; the breakdown (per Teacher: dates, working days, fraction, amount;
  contract id, rate) is stored as JSON on the row. The first billable month is `billing_settings.
  first_billable_month`, set by the migration to the month in which it runs (FR-010a).
- **Rationale**: no scheduler, matching Principle VIII (work is triggered on demand). Regenerating all Schools
  uses virtual-thread-per-task for the per-School calculations, as Principle VIII requires for batch work.
- **Alternatives**: a nightly schedule (rejected: the constitution says no fixed schedule for this kind of
  work); computing on read with no stored row (rejected: figures must not move after the month closes, and the
  history of recalculations must be kept).

## 8. Payments are append-only

- **Decision**: no update or delete path in code, plus a database trigger that rejects `UPDATE` and `DELETE`
  on `school_payment`. A reversal is a new row with `kind = REVERSAL`, `reverses_id` and a required reason; a
  payment can be reversed once (unique index on `reverses_id`). Balance =
  Σ current receivables (including adjustments) − Σ payments + Σ reversals, per School, up to the month.
- **Rationale**: Principle I and spec FR-013. The trigger is a second line of defence against a future code
  change; audit tables rely on code only, so this is a deliberate extra here.
- **Advance credit**: a payment above the balance, or against a month with no receivable, is simply an
  unmatched credit in the same sum, so no separate "advance" table is needed. The screen labels a negative
  balance "Advance".

## 9. Overdue

- **Decision**: a daily `OverdueJob` (`hls.billing.overdue.enabled`, off in `IntegrationTestBase`, same
  pattern as the notification retention job) finds School-months with a positive balance more than
  `overdue_days` after `expected payment day` of the *next* month. It writes an `overdue_flag` row (unique per
  School and month) and publishes `SchoolPaymentOverdue` from `schoolbilling.api`. `notification` listens and
  notifies the School's Manager and every active Director. The flag clears in the same transaction as the
  payment that brings the balance to zero or below.
- **Rationale**: one row per School-month makes "once per month of arrears" a database guarantee. Same event
  pattern as spec 010, so `schoolbilling` never depends on `notification`.
- **Settings**: `billing_settings` holds `overdue_days` (default 7), `expected_payment_day` (default 5) and
  `first_billable_month`. Spec 011 will absorb the editing screen later; until then Admin edits them on the
  School Billing screen's Settings tab with `SCHOOL_CONTRACTS` `EDIT`.

## 10. Permissions

- **Decision**: two new modules in `PermissionModule`: `SCHOOL_CONTRACTS(VIEW, CREATE, EDIT)` and
  `SCHOOL_BILLING(VIEW, CREATE, EDIT, APPROVE, EXPORT)`. Both are ineligible for Teacher and System
  (`PermissionEligibility`). Seeds follow the spec's table. One navigation item, OPERATIONS → School Billing,
  shown when the user has `SCHOOL_BILLING` `VIEW`; the contract tab inside it needs `SCHOOL_CONTRACTS` `VIEW`.
- **Rationale**: a single module cannot say "Manager records payments but cannot change the rate"
  (clarification 5). The constitution's Default role access matrix gets a "School Billing" row (Principle II
  wording is unchanged), recorded in the constitution's change note when this spec merges.

## 11. Frontend

- **Decision**: one feature folder `features/schoolbilling/` with a tabbed page: Overview (totals, per Manager
  and School, overdue list), School detail (contract history, assignments, months with payments), Settings.
  The Teachers screen's "interim" wording and `PlacementDialog` move to "assignment", reusing the same
  dialog and endpoints.
- **Alternatives**: separate pages per tab (rejected: more routes and menu items for one workflow).

## 12. Money type and display

- **Decision**: `NUMERIC(12,2)` in the database, `BigDecimal` in Java, strings in JSON (`"45000.00"`) so the
  browser never rounds a figure; the UI formats with Indian digit grouping.
