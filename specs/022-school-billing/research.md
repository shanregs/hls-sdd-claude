# Research: School Billing and Receivables

## R1. Where the Teacher's days come from (proration)

Decision: for each ACTIVE assignment that overlaps the month (`TeacherPlacementSource.spansOverlapping`), clip it to the month and to the contract's start and end (`SchoolContracts.contractsOverlapping`). If the clipped span is the whole month, the line is the position salary exactly. Otherwise the line is `salary x workedDays / monthDays`, where `workedDays` is the count of dates in the clipped span returned by `WorkingCalendar.workingDays(schoolId, from, to)` and `monthDays` is `SchoolWorkingDays.forMonth(schoolId, month).workingDays()` (spec 037, stored figure else calendar). `workedDays` is capped at `monthDays`. Rounded HALF_UP to 2 decimals per line; the line stores salary, workedDays, monthDays and the span as its working.
Rationale: D6 and the spec both say "working days at the School over the month's working days", and 037 makes the denominator the figure payroll uses, so the receivable and the pay agree.
Alternatives rejected: calendar-day proration (disagrees with payroll); using attendance (D6 forbids).

## R2. Position and salary per Teacher

Decision: the position a Teacher fills on each date comes from `SchoolContracts.positionOf(teacherId, date)`; for a month with a mid-month position change the line is split per assignment span, one line per (Teacher, position, span). A Teacher with an assignment but no position, or a School whose contract state is RATE_PENDING, goes to "not billed" with a reason (`NO_POSITION`, `MOU_PENDING`).
Rationale: keeps billing on the public interfaces and mirrors how 012 stores salary.

## R3. Transfer, exit, replacement, substitute

Decision: nothing special. Transfers and exits end/start assignments (027, `endForExit`), so each School's due clips its own span. A replacement in the same position is another span of the same position. A substitute has no assignment at the billed School and does not change the due (017a credits are not read).

## R4. Immutability and cancellation

Decision: `monthly_due` and `due_line` rows are never updated except one write-once cancellation (`cancelled_at`, `cancelled_by`, `cancel_reason`, set by a single UPDATE the trigger permits only when they are null). Status (OPEN, PART_PAID, PAID, CANCELLED) and overdue are derived in queries from lines, adjustments and live allocations. One non-cancelled due per (school, month) is a partial unique index on `cancelled_at is null`.
Alternative rejected: stored status column; it goes stale and needs repair when a collection is reversed.

## R5. Ledger entries and reversal

Decision: `due_adjustment` and `collection` rows are insert-only; a reversal is another row pointing at the original (`reverses_id`) with a reason, and a reversed entry counts as zero. `collection_allocation` is insert-only too: an allocation amount is positive, and un-allocating or reversing inserts a negative allocation row. Balance of a due = total - sum(active adjustments) - sum(allocations). Advance credit of a School = sum(non-reversed collections) - sum(allocations of them).
Rationale: simple sums, no mutable counters, matches the "never typed" rule (FR-013).

## R6. Allocation default and concurrency

Decision: the collection request carries explicit allocations; if none are given the service allocates oldest-due-first (by due date, then month) up to the amount, and keeps any excess as advance credit. A later "allocate advance" action does the same from the credit. A transaction-scoped Postgres advisory lock on the School id serialises allocations so two users cannot over-allocate the same collection or due.
Rule: an allocation cannot exceed the due's balance or the collection's unallocated amount (400).

## R7. Duplicate-collection warning

Decision: the create call is refused with 409 and a `warning: LIKELY_DUPLICATE` payload when a non-reversed collection of the same School, amount, date, mode and reference exists, unless the request sets `confirmDuplicate=true`. Warned, not blocked.

## R8. Overdue job and follow-up task

Decision: a daily `OverdueJob` (business date) selects non-cancelled dues with balance > 0 and `due_on < today` that have no `overdue_follow_up` row. For each it resolves the School's Zone Manager (`ManagerQueries.managerOfSchool`), calls `TaskCreation.createFollowUp` (type FOLLOW_UP, source `SCHOOL_DUE_OVERDUE`, sourceKey = due id, dueOn = today, `escalateAfterDays` = escalation days setting minus days already overdue, at least 1, so the Director hears 7 days after the due date by default, linked type `MONTHLY_DUE`), and stores one `overdue_follow_up` row (due id, task id, created on). Task idempotency comes from the source key, row idempotency from a unique index on due id. When the due is paid, cancelled, or reduced to zero balance, the service calls `TaskCreation.complete` (paid) or `cancel` (cancelled) on the same source key.
No Zone Manager: no task; the Director and Admin are notified once (`MonthlyDueOverdue` event with `noManager=true`), recorded by the same row with a null task id.
Escalation to the Director is 025's existing daily check (`TaskEscalated`), so 022 keeps no escalation state and cannot escalate twice.
Rationale: reuses the platform instead of duplicating deadline logic (roadmap section 5).
Risk: if the Zone Manager changes, the open task keeps the old assignee; the job calls `TaskCreation.reassign` when `managerOfSchool` differs.

## R9. Scope

Decision: every query takes a `ScopeView` from `ScopeQueries.scopeOf(user, roles)`; Admin and Director org-wide; a Zone Manager's School ids are the Schools of the Zone Manager's current assignment. Writes check the same set. A School outside scope is a 404. After a Zone Manager change, the old manager's scope no longer lists the School, past months included.

## R10. Settings

Decision: two settings added to `SettingKey` in the Financial group (`billing.payment_due_days`, default 15, range 1 to 120; `billing.escalation_days`, default 7, range 1 to 120, must be greater than 1 so the follow-up task exists before escalation). Read through `RuntimeSettingsService`, which already audits changes.

## R11. Ageing

Decision: bucket by `today - due_on` of each due with balance: NOT_YET_DUE (<=0), 1-30 shown as "0 to 30", 31-60, 61-90, over 90. Grouped by School, Zone, Zone Manager. One grouped SQL query per request.

## R12. Raising dues in bulk

Decision: `POST /dues/raise` takes a month and an optional School list; for each School it runs the single-School path in its own transaction, skips Schools that already have a non-cancelled due, and returns raised / skipped (with reason) / not billed lists. Only past or current months can be raised (the month must have started).

## R13. CSV export

Decision: server-side CSV streaming of the current filtered view with the same scope; cells beginning with `= + - @` are prefixed with a quote.

## R14. Notification types

Decision: add `SCHOOL_DUE_UNASSIGNED` (no Zone Manager, to Director and Admin). The Zone Manager already hears of the follow-up through 025's TASK_ASSIGNED, so no second notification is added for the common case.

## R15. Impacts on other specs

- 013b / 014 / 028: consume `SchoolReceivables`; no change to them now. The roadmap row for 022 is updated.
- 025: no code change. Uses `createFollowUp`, `complete`, `cancel`, `reassign` as they exist. If the Zone Manager has no user account the call refuses and the job falls back to the R8 no-manager path.
- 012: none. 027: none (read through `TeacherPlacementSource`).

## R16. Decisions confirmed by the owner (2026-10-26)

Escalation days count from the due date; a Zone Manager without a user account means no task and a notification to the Director and Admin; the partial-month denominator is the stored 037 figure (else calendar-derived); the reference is `MD-YYYYMM-<seq>`; any month that has started may be raised, including past months. No open decisions remain.

## R17. Implementation notes (2026-10-26)

- The two ports `BillingCalendar` (implemented in `attendance`) and `OverdueTasks` (implemented in `tasks`) replace the direct reads of `WorkingCalendar`, `SchoolWorkingDays` and `TaskCreation` named in R1 and R8: billing depending on them closed module cycles (attendance, recruitment and tasks all reach `schoolbilling`), which the Modulith verification refuses. The behaviour is unchanged.
- Billing reads its own assignment, position and contract tables for the calculation (same module) instead of the `TeacherPlacementSource` and `SchoolContracts` interfaces named in R1 and R2: those take Teacher ids, and the calculation needs every assignment at one School.
- The settings live in a small `billing_setting` table rather than the spec 011 settings catalog (R10), which has no group for module settings; changes are audited through Change History.
- Raising dues needs Edit (not Create) and allocating an advance needs Create, so a Zone Manager (View, Create, Export only) can record and allocate collections but not raise, adjust, reverse or cancel.
