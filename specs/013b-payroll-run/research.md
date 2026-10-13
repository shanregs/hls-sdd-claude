# Research: Payroll Run

Decisions taken before design. Each has the rationale and what was rejected. Nothing here is left as "to be decided".

## 1. Where the run lives, and what it may read

**Decision**: the run is a sub-package of the existing `payroll` module (`payroll.run.internal`, `payroll.run.web`). It
reads the pay rules directly (same module), attendance only through `attendance.api` (`AttendanceReadApi.isLocked` for
a Teacher, `ManagerAttendanceReadApi.month` for a Manager), people only through the existing `PersonDirectory` port,
payslip files through `files.api.FileStore`, and talks to `notification` and `audit` with events.

**Rationale**: the salary rows, the policy and the payable-day logic are `payroll`-internal. Exporting them to a new
module would widen `payroll.api` for one consumer. Constitution VII (no table reads across modules) is met by the same
rule the existing code follows.

**Alternatives**: a new `payrun` module (rejected: duplicates the port and needs the internals exported); reading
attendance tables for the lock (rejected by Principle VII).

## 2. Earned pay and the one method added to PayRules

**Decision**: add `PayRules.earnedPay(person, month)` returning a sealed `EarnedPay`: `Found(rounded, unrounded, days)`
where each payable date carries its salary, its divisor (the working days of the month on the calendar of that date's
School) and its amount, or `Cannot(reason)`. It is built from the same `Computed` object as `totalLossOfPay`, so both
use the same divisor for the same date. The run then holds: earned = `EarnedPay.rounded`, deduction =
`TotalLossOfPay.rounded`, net = earned - deduction + adjustments.

**Rationale**: the public `PayableDays` carries one `workingDaysInMonth` (the last School's), but a Teacher who changed
School mid-month has a different divisor for each School, and `totalLossOfPay` already values each unpaid day with
its own School's divisor. Computing earned pay in the run from `PayableDays` would give a different divisor for the same
date than the deduction. One method, one source, fixes that.

**Consequence worth stating**: a Teacher who changed School mid-month and is payable all month earns the sum of
`salary / divisorOfThatSchool` over their dates, which can differ from the plain salary by a few rupees. That is the
same valuation spec 013a already uses for the deduction, and it is listed for the financial review (quickstart.md).

**Alternatives**: compute `salary * payableDays / workingDays` in the run (rejected: two divisors for one date, and a
mid-month raise would need its own splitting code); use the last School's divisor for everything (rejected: the deduction
would then disagree for the same Teacher).

## 3. The background batch

**Decision**:
- `POST /runs` inserts the run in state `DRAFT` with `compute_state = QUEUED`, commits, and then hands the run id to
  `RunComputation` on a virtual thread. The request returns `202` with the run.
- `RunComputation` claims the run with a conditional update (`compute_state` `QUEUED` or a stale `RUNNING` to `RUNNING`,
  setting a heartbeat); only the winner continues, so a double click or a second node cannot compute twice.
- It lists the candidates, reads in bulk what has a bulk form (`payableDays`, `salariesOn`, the Manager months), then
  runs one virtual-thread task per person to do the per-person reads (`unpaidDays`, `totalLossOfPay`, `earnedPay`, the
  lock). A `Semaphore` limits concurrent tasks to the connection pool size so 1,000 tasks cannot starve the pool. Each
  task returns an immutable `PersonResult`.
- The coordinating thread alone writes the lines, in batches of 100, and updates `progress_done` and the heartbeat, so
  there is no concurrent writer and no shared mutable state.
- A task that throws becomes a "cannot be paid" line with the reason "Could not be worked out: <short message>" and an
  error log; it never fails the run (FR-010).
- If the node stops mid-run the heartbeat goes stale; the run page then offers "Compute again" (the same recompute
  path), which is safe because recompute is a full replace of a draft computed by the same function.

**Rationale**: Principle VIII asks for virtual-thread-per-task. Reads are the cost (several queries a person); writes
stay single-threaded and simple. Progress is a counter the screen polls.

**Alternatives**: `parallelStream` (rejected: the common pool, no concurrency limit); a job table with a scheduler picking
runs up (rejected: more moving parts than the heartbeat and is not needed for an on-demand run); computing inside the
request (rejected by FR-010).

## 4. Who is in a run

**Decision**: candidates are all Teachers, Managers and staff from `PersonDirectory`. A person is **in the run** when they
have at least one payable date in the month (`PayRules.payableDays`, bulk). A Manager with no joining date has no
payable dates by that rule, so they are added as a candidate explicitly and listed as "no joining date" (FR-006,
SC-004); this is the case that would otherwise vanish silently. A person with no payable date and no defect (not yet
joined, already left) is simply not in the run and is not listed.

**Rationale**: matches FR-002 and the 013a payable-days rule; the only silent-loss case is the missing joining date, which
is handled on purpose and tested.

**Open point to verify in the first task**: how `payableDays` answers for a Teacher with no joining date or an unknown
placement; the test fixture list in the foundation slice includes it, and the answer decides whether a second explicit
candidate case is needed.

## 5. Freezing

**Decision**: each line stores, as JSON, the working it used: the salary rows by span of dates, the payable dates, the
per-date divisors, the unpaid days with their values and reasons, the amounts before and after rounding, the pay policy
(id, rounding, half-day fraction), and the Manager's frozen figures where it applies. Numeric columns carry the same
totals for queries and the CSV. An approved run never calls `PayRules`, attendance or the calendar again: the working
screen, the payslip and the CSV are built from the stored line only.

A database trigger refuses `UPDATE` and `DELETE` on a line (and on its adjustment lines) whose run is `APPROVED`, and
refuses changes to an approved run's amounts and state. The services are the first line of defence, the trigger is the
last, because this is the invariant the money depends on (Principle IX).

**Rationale**: the two points in the spec 032 financial review (only locked months; divisor and unpaid days frozen) are
exactly "an approved run reads nothing live". A Manager's frozen unpaid *dates* are still divided by the live calendar
when the run computes; the run closes that by storing the divisor it used, and by showing a warning on the line when the
Manager's lock-time working-day count differs from the divisor used (a reviewer cue, not a block).

**Alternatives**: re-derive an approved run on demand and compare (rejected: it can only detect a change after the
fact); rely on service-level checks alone (rejected: one missed path changes approved pay).

## 6. Draft recompute and what "changed" means

**Decision**: recompute replaces the lines of a draft with freshly computed ones and stores a diff on the run:
people added, people removed, lines whose net changed (with old and new net), and people who moved between payable and
cannot-be-paid. A line is rewritten only when its input fingerprint (a hash of the working) changed, so a line whose
inputs did not change keeps its row (FR-008). The diff is shown to the Admin and kept as the run's last recompute summary
and as an event.

**Alternatives**: delete and recreate every line (rejected: cannot say what changed); never allow recompute (rejected:
an Admin who locks the last attendance month would have to cancel and restart).

## 7. Payslip PDF

**Decision**: Apache PDFBox 3.0.x (Apache License 2.0) with the bundled Noto Sans fonts (SIL Open Font License), one A4
page per payslip. The renderer is a pure class: stored line in, bytes out. The font is embedded because the standard
fonts of PDF have no rupee sign (U+20B9).

**Rationale**: no PDF library exists in the project. PDFBox is Apache-licensed, widely used and maintained, is a library
(no server or browser needed), and has no copyleft terms. This is the only dependency the spec adds; the pinned version is
confirmed against the dependency's current release notes at the start of the implementation task, and it is added to
the build with the same scrutiny as any new dependency (Principle X).

**Alternatives**: OpenPDF (rejected: LGPL/MPL dual terms are acceptable but its text and font handling for the rupee sign
needs the same font work, with less maintenance momentum); HTML to PDF through a headless browser (rejected: adds an
operating-system dependency); producing the payslip as HTML print view (rejected: spec says PDF).

## 8. Approval and the payslips, atomically

**Decision**: `approve` runs in three steps: (1) validate under a row lock that the run is `AWAITING_APPROVAL`, the actor
is not the submitter and holds Approve; (2) render every payslip for the paid lines in parallel on virtual threads from
the stored lines (pure CPU, no database write, the lines cannot change while the run is awaiting approval); (3) in one
short transaction, store the PDFs through `FileStore`, insert the `payroll_payslip` and `payroll_paid_month` rows,
mark the adjustments applied, set the run `APPROVED`, write the event and audit. If anything fails nothing is approved
and the run stays awaiting. The run row is locked again in step 3 and its version checked, so a concurrent return or
second approval cannot interleave.

**Rationale**: approval must be all-or-nothing; rendering outside the transaction keeps the transaction (and the lock)
short.

**Alternatives**: approve first and render payslips afterwards (rejected: leaves an approved run with missing payslips
if rendering fails, and a state the spec does not have); render on demand from the stored line (rejected: a later code
change could alter a payslip layout or figures presentation, which breaks "always what was approved").

## 9. Exactly-once payment per month

**Decision**: a table `payroll_paid_month` with primary key (month, person kind, person id), written at approval in the
same transaction as the status change, for every payable line. A second approved run that contains the same person for the
same month violates the key and the approval fails with a clear message. The supplementary run's candidate list is "the
people with no row in `payroll_paid_month` for the month".

**Rationale**: SC-007 says never twice and never lost. A key enforces it under concurrent approvals; a service-only
check would race. The same table answers "who is still owed" without scanning runs.

## 10. Owed people

**Decision**: "owed" for a month is: the people who were candidates of the month's primary run (or of any earlier
supplementary run) as cannot-be-paid lines, minus those in `payroll_paid_month`. The page shows the stored reason from
the line they were left out with, and a live "resolved" flag computed by asking the same checks again (lock state,
joining date, salary). That live flag is a hint for the Admin, never an input to any approved figure.

## 11. Adjustments

**Decision**: a waiting adjustment is taken by the next draft run that includes its person: at compute time it is copied
into the line as a frozen adjustment line (kind, amount, reason) and the adjustment is **attached** (`attached_run_id`)
but stays `WAITING`; approval marks it `APPLIED` with the line id. Withdrawing an attached adjustment: allowed while the
run is a draft (the line's adjustment copy is removed and the net recalculated by arithmetic on the stored line, with an
event), refused while the run is awaiting approval or approved (the message says why). A new adjustment recorded while a
draft is open is not in that draft until the Admin recomputes; the run page shows "1 adjustment waiting since this was
computed". Cancelling a draft detaches its adjustments; they wait again. A person who cannot be paid in a run keeps their
adjustment waiting (spec 4.3).

**Rationale**: keeps the approved run immutable and the draft honest; avoids an adjustment appearing in the middle of a
run someone is reviewing.

## 12. Submit, approve, return: separation of duties

**Decision**: `submitted_by` is recorded at submit; `approve` refuses if the caller equals `submitted_by` (FR-014), even
with both roles. A run returned and submitted again records the new submitter. A run needs `PAYROLL.APPROVE` to approve or
return. The Admin's `PROCESS` permission never includes approve; a custom role granted both still cannot approve its own
submission.

## 13. Payment records

**Decision**: append-only `payroll_payment_entry` rows per line; the current state of a line is its newest entry; a
correction is a new entry and the earlier ones stay. The date cannot be in the future (business time zone); the mode is
one of BANK_TRANSFER, CASH, UPI, CHEQUE, OTHER; the reference is plain text up to 100 characters, trimmed. Payment
entries are allowed only on lines of an approved run and never touch an amount.

## 14. CSV export

**Decision**: a small writer in `payroll.run.internal` (the existing streaming exporter lives in `audit.support` and is
audit-specific). Columns: month, run number, run status, person kind, person name, designation, salary (the monthly rate at
the last payable date), payable days, working days, unpaid days, earned pay, deduction, adjustments, net pay, payment
status, paid on, mode, reference, reason when cannot be paid. The first two rows state the month and the run status.
Text cells that start with `=`, `+`, `-` or `@` are prefixed with an apostrophe so a spreadsheet does not run them; numeric
cells are not changed. Every export is audited. Encoding UTF-8 with a byte-order mark so Excel reads rupee and Indian names.

## 15. Notifications

**Decision**: three events (`PayrollRunSubmitted`, `PayrollRunApproved`, `PayrollRunReturned`) in `payroll.api`; the
`notification` module listens and tells the Director(s) on submit, and the submitter on approve or return (with the reason).
Notification types are added to `NotificationType` (the column is already 60 characters since V50). The message holds the
month and the status and never an amount.

## 16. Permission, menu and audit wiring (lessons from spec 032)

- Add `PAYROLL` to `PermissionModule`, `PermissionEligibility` (Admin and Director), `PermissionMatrixService` seeds,
  `NavigationCatalog` (OPERATIONS → Payroll, `/operations/payroll`) and `AccessModelService` (org-wide scope).
- Add the four audited entity types to `AuditVisibility` with the permission `PAYROLL.VIEW`; a missing mapping failed
  silently in spec 032 and was only caught in review, so a test asserts every audited type has a mapping.
- Constitution default-access rows for Payroll are added at the end (a patch note, as for 032).

## 17. Frontend

**Decision**: the run page polls the run every 2 seconds while `compute_state` is `QUEUED` or `RUNNING` and stops when it is
`DONE` or `FAILED`; the lines are paged (50) with filters (kind, state, search by name). The working opens in a dialog and
is built from the stored line only. Reserved adjustment kinds are shown, disabled, with the spec that will create them.
MUI v9 note from spec 032: `Stack` needs `sx` for gap, wrap and alignment.

## 18. Things deliberately left out

Tax, provident fund, statutory deductions, bonuses, arrears (done through adjustments), a person's own pay view and
mobile access, automatic scheduling, email or SMS, multi-level approval, and bank file generation.
