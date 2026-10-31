# Feature Specification: Payroll Run

**Feature Branch**: `013b-payroll-run`

**Created**: 2026-10-13

**Status**: Draft

**Input**: User description: "013b Payroll run. OPERATIONS → Payroll (Admin: Process; Director: Approve). A monthly pay run for every payroll person (Teachers, Managers, and staff who hold the Admin or Director role) built only from locked attendance and the salary structures of spec 013a, with the loss-of-pay deduction, giving a net pay per person. The system computes, the Admin reviews, the Director approves, then the run is locked; a person whose month cannot be computed is listed with the reason and does not block the others. Corrections after approval are adjustments carried into the next run. Payslips as PDF, payment history per person, CSV export, manual payment status (decision D8). The run uses only months that have ended and whose attendance is locked, and freezes its divisor and the unpaid days it used. Incentives (030), reimbursements (015) and substitute credit (017b) are not built: the run has a place for their adjustment lines but builds none of them."

## Clarifications

### Session 2026-10-13

- Q: How is a person left out of an approved run paid later? → A: Through a supplementary run for the same month that
  contains only the people not yet paid for it, with its own review, approval, freezing and payslips. The first run is
  never changed. A supplementary run can start only after the month's primary run is approved; there can be more than
  one over time, one open at a time.
- Q: Who may see a payslip? → A: Only the Admin and the Director, through the Payroll screens. A person's own view (My
  Pay) and mobile access are left to a later spec.

## Why this exists

Specs 008, 009, 013a and 032 now answer, for every person and every month, what their salary is, which days they were
payable for, and which days were unpaid. Nothing yet turns those answers into the month's pay. Today that is a
spreadsheet. This spec is the monthly run: one place that computes everyone's pay from locked attendance, shows the
working so it can be checked, takes it through approval, and then keeps it unchangeable. The run also settles two points
left open by the Manager attendance financial review: only ended, locked months are paid, and the divisor and the unpaid
days are frozen with the run.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Prepare the Pay Run for an Ended Month (Priority: P1) 🎯 MVP

On or after the first day of a month, an Admin opens OPERATIONS → Payroll and starts the run for the previous month (or
any earlier month without a run). The system works out, for every payroll person, their pay for that month and shows one
line each: salary, payable days, the month's working days, unpaid days, what was earned, the loss-of-pay deduction, any
adjustments, and the net pay. A person whose pay cannot be worked out is listed apart with the reason, and the others
are not held back. The Admin can compute the run again while it is a draft, for example after locking a person's
attendance, and sees what changed.

**Why this priority**: Without a computed run there is nothing to review, approve, export or pay. This is the smallest
slice that replaces the spreadsheet's arithmetic.

**Independent Test**: With one Teacher, one Manager and one Director whose attendance for last month is locked and who
have salaries, start the run for last month and see three lines whose figures equal the hand-computed ones; add a fourth
person with no salary and see them listed apart with the reason while the other three are unchanged.

**Acceptance Scenarios**:

1. **Given** a month that has ended where every payroll person has a salary and a locked attendance month, **When** the
   Admin starts the run, **Then** one line per person is shown with salary, payable days, working days, unpaid days,
   earned pay, deduction, adjustments and net pay, and the run is a draft with the totals at the top.
2. **Given** the current month or a future month, **When** the Admin tries to start a run, **Then** it is refused because
   the month has not ended.
3. **Given** a month for which a run already exists (not cancelled), **When** the Admin tries to start another, **Then**
   it is refused and the existing run is shown.
4. **Given** a Teacher whose attendance month is not locked, a Manager whose month is not locked, a Manager with no
   joining date, and a person with no salary on a day they were payable, **When** the run is computed, **Then** each is
   listed under "Cannot be paid in this run" with the specific reason, and every other person's line is computed.
5. **Given** a draft run with a person left out because their attendance month was not locked, **When** that month is
   locked and the Admin computes the run again, **Then** the person now has a line and every other line is unchanged
   unless their own inputs changed, and the changes are listed.
6. **Given** a draft run, **When** the Admin cancels it with a reason, **Then** it becomes cancelled, keeps its history,
   and a new run for the month can be started.

---

### User Story 2 - Review Line by Line and Approve (Priority: P1)

The Admin opens any line and sees the working: the salary rows used on each day, the payable dates, the month's working
days used as the divisor, each unpaid day with its date, its value and why it is unpaid (absence, Loss-of-Pay leave),
the rounding that was applied and the pay policy version. When satisfied, the Admin submits the run for approval. The
Director opens the submitted run, sees the same lines and totals, and approves it or sends it back with a reason. An
approved run is locked: it can no longer change, and its payslips are issued.

**Why this priority**: Money leaves the company on the strength of this approval, so the working must be visible and the
approval must be a separate step by a different person (Constitution Principle IX).

**Independent Test**: Submit a draft run as an Admin and approve it as the Director; confirm the status, who and when,
that no figure can be edited afterwards, and that the same person cannot submit and approve.

**Acceptance Scenarios**:

1. **Given** a draft run, **When** the Admin opens a line, **Then** the working is shown in full, in rupees with Indian
   digit grouping and dates as DD/MM/YYYY, and reproduces the line's figures.
2. **Given** a draft run with no negative net pay, **When** the Admin submits it, **Then** it becomes "awaiting approval",
   the Director is notified, and the run can no longer be recomputed or adjusted until it is returned.
3. **Given** a run awaiting approval, **When** the Director approves it, **Then** it becomes approved and locked with the
   approver and time recorded, and payslips are issued for every paid line.
4. **Given** a run awaiting approval, **When** the Director sends it back with a reason, **Then** it returns to draft, the
   Admin is notified with the reason, and the reason stays in the run's history.
5. **Given** a user who holds both the Admin and Director roles and submitted the run, **When** they try to approve it,
   **Then** it is refused: whoever processed a run cannot approve it.
6. **Given** a draft run in which one line has a negative net pay, **When** the Admin tries to submit it, **Then** it is
   refused naming the line, and the Admin corrects it with an adjustment first.
7. **Given** an approved run, **When** anyone tries to change a line, recompute, cancel or delete it, **Then** it is
   refused; the run can only be added to (payment status) or corrected through adjustments in a later run.

---

### User Story 3 - An Approved Run Never Changes (Priority: P1)

A run stores, with each line, the figures it used: the salary rows applied day by day, the payable dates, the month's
working-day count it divided by, the unpaid days with their values, and the pay policy it rounded with. After approval, a
holiday added to the calendar, a Teacher's or Manager's attendance month being reopened, a salary being backdated, or a
policy change does not alter the run, its payslips or its export. Anything that should change pay is dealt with as an
adjustment.

**Why this priority**: This is the point of the two findings from the Manager attendance financial review: without it a
later calendar change would silently change pay already approved.

**Independent Test**: Approve a run; then add a holiday to the month, reopen a Manager's attendance month and record a
new salary effective in the month; confirm every figure, payslip and export row of the run is identical to before.

**Acceptance Scenarios**:

1. **Given** an approved run, **When** a holiday is added to that month's calendar, **Then** the run's working-day count,
   values and totals are unchanged.
2. **Given** an approved run, **When** a locked attendance month of a person in it is reopened and changed, **Then** the
   run's unpaid days and amounts for that person are unchanged, and the Admin can record an adjustment for the
   difference.
3. **Given** an approved run, **When** a new salary effective inside that month is recorded, **Then** the run still shows
   the salary it used, and the person's next run is unaffected unless the new salary applies to it.
4. **Given** a draft run, **When** the underlying attendance or calendar changes, **Then** the draft does not change by
   itself, and recomputing it picks the change up and lists it.

---

### User Story 4 - Adjustments and Corrections for the Next Run (Priority: P2)

The Admin records an adjustment for a person: a signed amount in rupees, a kind and a reason. Adjustments wait against
the person until a run takes them: the next draft run for any month includes every unapplied adjustment for people in it
as a separate line item, shown with its reason, and approval of the run approves them. A mistake found after approval is
corrected this way, not by editing the approved run. The kinds are "Correction" (used now) and three reserved kinds,
"Incentive" (spec 030), "Reimbursement" (spec 015) and "Substitute credit" (spec 017b), which this spec lists but does
not produce: nothing creates them yet.

**Why this priority**: It is the only route to fix an approved run and to leave room for the later pay inputs without
changing the run again. It is P2 because a first run can be approved without any.

**Independent Test**: Record a +₹2,000 correction with a reason for a person; start the next run and see it as its own
line item on that person's line, with the net pay including it; approve; see the adjustment marked as applied to that run
and no longer available.

**Acceptance Scenarios**:

1. **Given** a person, **When** the Admin records an adjustment with a non-zero amount, a kind and a reason, **Then** it
   is saved against the person as "waiting", with who recorded it and when.
2. **Given** waiting adjustments for people in a run, **When** the run is started or recomputed, **Then** each appears as
   a line item on its person's line and in the net pay, and the run's totals include them.
3. **Given** an adjustment waiting for a person who cannot be paid in the run, **When** the run is computed, **Then** the
   adjustment stays waiting and is not applied.
4. **Given** an adjustment applied by an approved run, **When** anyone tries to edit or delete it, **Then** it is refused.
5. **Given** a waiting adjustment, **When** the Admin withdraws it with a reason, **Then** it is marked withdrawn and kept
   in the history, and no run takes it.
6. **Given** the form, **When** the amount is zero or has more than two decimals, the reason is empty, or the kind is one
   of the reserved kinds, **Then** it is refused and the problem is named.

---

### User Story 5 - Pay the People Left Out Through a Supplementary Run (Priority: P2)

A run is approved even if some people were listed under "Cannot be paid in this run" (for example their attendance month
was not yet locked). Those people must still be paid for that month, and never twice. Once the reason is resolved the
Admin starts a supplementary run for the same month: it contains only the people not yet paid for that month, is
computed by the same rules, and goes through the same review, approval, freezing and payslips as any run. The first run
is never changed.

**Why this priority**: Without this, one late attendance lock either delays everyone's pay or leaves a person unpaid
with no record that they are owed.

**Independent Test**: Approve a run with one person left out; lock their attendance; start the supplementary run for
the month and see only that person, approve it, and see them paid for that month exactly once with the original run
unchanged.

**Acceptance Scenarios**:

1. **Given** an approved run that left a person out, **When** the Admin views the run, **Then** the people not yet paid
   for that month are listed with the reason they were left out and whether it has since been resolved.
2. **Given** an approved run for a month with people still owed, **When** the Admin starts a supplementary run for that
   month, **Then** it contains only the people not yet paid for the month, each computed by the same rules, and people
   still not computable are listed with their reason again.
3. **Given** a month whose primary run is still a draft or awaiting approval, **When** the Admin tries to start a
   supplementary run, **Then** it is refused.
4. **Given** a supplementary run is open (draft or awaiting approval), **When** the Admin tries to start another for the
   same month, **Then** it is refused.
5. **Given** an approved supplementary run, **When** its month is looked at, **Then** every person paid appears in
   exactly one approved run of the month, and nobody appears in two.
6. **Given** a month where everyone was paid in the primary run, **When** the Admin tries to start a supplementary run,
   **Then** it is refused because nobody is owed.

---

### User Story 6 - Payslips (Priority: P2)

When a run is approved, a payslip is issued for every person paid in it, as a PDF. It shows the person's name and
designation or role, the month, their salary, the payable days and working days, the earned pay, each unpaid day and the
deduction, each adjustment line, and the net pay, with the run's approval date. A payslip is never regenerated with
different figures: it always shows what was approved. Anyone allowed to see it can download it again at any time.

**Why this priority**: Payslips are the record the person receives; the run is complete without them for the company but
not for the person.

**Independent Test**: Approve a run, download a person's payslip, change the calendar and download it again; the two
files show the same figures; a person excluded from the run has none.

**Acceptance Scenarios**:

1. **Given** an approved run, **When** the Admin or Director opens a person's line, **Then** they can download that
   person's payslip as a PDF whose figures equal the line.
2. **Given** an approved run, **When** the Admin downloads all payslips, **Then** they receive every issued payslip for
   the run together.
3. **Given** a draft or awaiting-approval run, **When** anyone tries to download a payslip, **Then** none is available.
4. **Given** the period after approval, **When** a payslip is downloaded again, **Then** its figures are the same as the
   first time.
5. **Given** a Manager, a Teacher or a System user, **When** they try to open a payslip or the Payroll screens, **Then**
   they are refused and receive no figures.

---

### User Story 7 - Record Payment (Priority: P2)

Payment happens outside the system. After approval, the Admin records for each paid line whether it is paid, the date,
the mode (bank transfer, cash, UPI, cheque or other) and a reference. The run shows how many lines are paid and unpaid
and the amounts, and each person's page shows their payment history across runs.

**Why this priority**: Without it nobody can tell what has actually been paid; it follows approval and so is P2.

**Independent Test**: Approve a run, mark two lines paid with a date, mode and reference, and see the run's counts and
amounts update and each person's history show the entry; try to mark a line paid in a draft run and be refused.

**Acceptance Scenarios**:

1. **Given** an approved run, **When** the Admin marks a line paid with a date that is not in the future, a mode and a
   reference, **Then** the line shows paid with those details, who entered them and when.
2. **Given** a paid line entered by mistake, **When** the Admin corrects the payment details, **Then** the old details are
   kept in the line's payment history and the new ones apply; the amounts of the run never change.
3. **Given** a draft or awaiting-approval run, **When** the Admin tries to record a payment, **Then** it is refused.
4. **Given** a person, **When** the Admin opens their payroll history, **Then** each run they were in is listed with
   the month, net pay, status and payment details.

---

### User Story 8 - Export for the Accountant (Priority: P3)

At any time the Admin or Director exports a run as a CSV file with one row per person: identification, kind, the month,
salary, payable days, working days, unpaid days, earned pay, deduction, adjustments, net pay and payment status. The file
says whether the run was a draft, awaiting approval or approved, so a draft cannot be mistaken for the final figures.

**Why this priority**: The accountant works from a file; the screens and payslips already carry the same figures.

**Independent Test**: Export a draft and an approved run; confirm both carry the run state, the approved file's rows
match the screen and the payslips, and each export is recorded in the audit trail.

**Acceptance Scenarios**:

1. **Given** any run, **When** the Admin or Director exports it, **Then** a CSV is downloaded whose first rows or column
   state the month and the run's status, and whose figures equal the screen.
2. **Given** people who could not be paid in the run, **When** the run is exported, **Then** they appear with the reason
   and no amounts.
3. **Given** an export, **When** it is made, **Then** who exported which run and when is recorded.

---

### Edge Cases

- A person who exited, or joined, in the middle of the month is paid only for the dates they were payable (spec 013a),
  each at the salary in effect that day.
- A salary changes in the middle of the month: each day is valued at its own day's salary, both for what is earned and
  for the deduction.
- A month with no working days for a person's calendar: the person is listed under "Cannot be paid" with that reason; no
  division by zero.
- A person is both a Teacher and holds the Admin role: they are one payroll person per kind of record they have in
  spec 013a; the run shows each separately and never pays the same kind twice.
- An adjustment makes a person's net pay negative: the run cannot be submitted until the Admin corrects it.
- A person's net pay is zero (fully unpaid month): the line is valid and a payslip is issued showing zero.
- Two Admins start a run for the same month at nearly the same time: exactly one run is created; the other sees the
  refusal. The same holds for two supplementary runs.
- A person left out of the primary run is also computable at the time a supplementary run is started: they are paid once,
  in the supplementary run, and appear in no other approved run of that month.
- The pay policy changes between starting a run and approving it: a recompute uses the policy in effect for that month
  and says so; an approved run keeps the policy it used.
- A reopened attendance month of a person already in an approved run: the run is unchanged, and the Admin records an
  adjustment if pay should change.
- The run is large: it is computed in the background, the Admin sees progress and can leave the page, and a failure on one
  person lists that person with the reason without stopping the others.
- A user without the right to see pay opens the Payroll URL directly: they see "not authorized" and receive no figures.

## Requirements *(mandatory)*

### Functional Requirements

**The run**

- **FR-001**: The system MUST let an Admin start a pay run for one calendar month and MUST refuse a month that has not
  ended. A month has one primary run; a second primary run is refused while one exists that is not cancelled.
- **FR-002**: A run MUST include every payroll person who has at least one payable date in the month: Teachers (placed
  in a School), Managers (inside their employment) and staff (users holding the Admin or Director role with employment
  dates), using the payable dates of spec 013a.
- **FR-003**: For each person the system MUST compute, using the rules of spec 013a and the attendance specs only
  through their published answers: the salary in effect on each payable date; the month's working days (the divisor); the
  unpaid days and their day values; the earned pay; the loss-of-pay deduction; waiting adjustments; and the net pay.
- **FR-004**: Earned pay MUST be the sum, over the person's payable dates, of the salary in effect on that date divided by
  the month's working days. The loss-of-pay deduction MUST be the amount spec 013a defines (each unpaid day at that day's
  salary over the month's working days, times its day value, summed). Earned pay and the deduction MUST each be rounded
  once by the pay policy in effect on the month's last day. Net pay MUST be earned pay, minus the deduction, plus the sum
  of the adjustment lines.
- **FR-005**: A person MUST be included only if their attendance for the month is final: a Teacher's attendance month is
  locked (spec 008) and a Manager's month is locked (spec 032); staff need none. Otherwise they MUST be listed under
  "Cannot be paid in this run" with that reason.
- **FR-006**: A person whose pay cannot be worked out for any other reason (no joining date, no salary on a payable day,
  no working days, an unknown record) MUST be listed under "Cannot be paid in this run" with the specific reason. The
  system MUST never show such a person with zero pay.
- **FR-007**: A person who cannot be paid MUST NOT stop the run: every other person's line MUST be computed and shown.
- **FR-008**: While a run is a draft, the Admin MUST be able to compute it again; the system MUST list which lines and
  totals changed and why, and MUST change a line only if its own inputs changed.
- **FR-009**: The Admin MUST be able to cancel a draft run with a reason; a cancelled run is kept and no longer blocks a new
  run for the month.
- **FR-010**: The run MUST be computed in the background so a run for up to 1,000 people does not make the screens
  unresponsive; the Admin MUST see progress, MUST be able to leave and return, and a failure for one person MUST list
  that person with the reason and MUST NOT stop the others.

**Review and approval**

- **FR-011**: Every line MUST show its working: the salary rows used and the dates they applied to, the payable dates,
  the divisor, each unpaid day with its date, day value and reason, the amounts before and after rounding, the pay policy
  version, and each adjustment line.
- **FR-012**: A run MUST move through these states only: draft, awaiting approval, approved, and cancelled (from draft
  only). A run awaiting approval MUST be returnable to draft by the Director with a required reason.
- **FR-013**: The Admin MUST be able to submit a draft run for approval only when no line has a negative net pay; the
  Director MUST be notified when a run is submitted, and the Admin MUST be notified when it is approved or returned.
- **FR-014**: Only the Director (or a role granted Approve) MAY approve a run, and the user who submitted it MUST NOT
  approve it, even if they hold both roles. Approval MUST record who and when.
- **FR-015**: An approved run MUST be immutable: no line, total, adjustment line, working or divisor can be changed,
  recomputed, cancelled or deleted. Only payment details (User Story 7) may be added to it.

**Freezing**

- **FR-016**: A run MUST store, for each line, the figures it was computed from: the salary rows applied day by day, the
  payable dates, the divisor, the unpaid days with their values and reasons, the amounts, and the pay policy used
  (rounding and half-day fraction). An approved run MUST show these stored figures and MUST NOT read the calendar,
  attendance, salaries or policy again.
- **FR-017**: A change to the calendar, to an attendance month (including reopening it), to a salary or to the pay policy
  MUST NOT change an approved run, its payslips or its exports.

**Adjustments**

- **FR-018**: The Admin MUST be able to record an adjustment for a person: a non-zero signed amount in rupees (at most two
  decimals), a kind and a required reason of at most 500 characters. The kinds MUST be Correction, and the reserved kinds
  Incentive, Reimbursement and Substitute credit, which the system lists but never creates in this spec.
- **FR-019**: A waiting adjustment MUST be taken by the next run that includes its person, shown as its own line item, and
  marked applied by that run's approval. A waiting adjustment for a person who cannot be paid in a run MUST stay waiting.
- **FR-020**: An applied adjustment MUST NOT be editable or deletable. The Admin MUST be able to withdraw a waiting
  adjustment with a reason; a withdrawn adjustment is kept and never applied.

**People left out**

- **FR-021**: A person left out of an approved run MUST be shown as owed until paid, with the reason they were left out and
  whether it is resolved. After the month's primary run is approved the Admin MUST be able to start a supplementary run
  for the same month that contains only the people not yet paid for it, computed by the same rules and going through
  the same review, approval, freezing and payslips. It MUST be refused while the primary run is not approved, while
  another supplementary run for the month is open, and when nobody is owed. A person MUST appear in exactly one approved
  run of a month, never two.

**Payslips**

- **FR-022**: On approval the system MUST issue a payslip, as a PDF, for every person paid in the run. A payslip MUST show
  the person, the month, salary, payable days, working days, earned pay, each unpaid day and the deduction, each
  adjustment line and the net pay, with the approval date, in rupees with Indian digit grouping and dates as DD/MM/YYYY.
- **FR-023**: A payslip MUST always show the figures that were approved and MUST be downloadable again at any time by those
  allowed to see it, one at a time or for the whole run together. No payslip exists for a draft or awaiting run, or for a
  person who was not paid.
- **FR-024**: Payslips MUST be available only to users with the right to view payroll (Admin and Director by default),
  through the Payroll screens. A person's own view of their pay and mobile access are out of scope.

**Payment**

- **FR-025**: After approval the Admin MUST be able to record per line: paid, the payment date (not in the future), the mode
  (bank transfer, cash, UPI, cheque or other) and a reference. A correction MUST keep the earlier details in a history
  and MUST NOT change any amount of the run.
- **FR-026**: The run MUST show how many lines and how much money are paid and unpaid, and each person MUST have a payroll
  history of the runs they were in with month, net pay, status and payment details.

**Export, visibility, audit**

- **FR-027**: The Admin and Director MUST be able to export a run as CSV at any time, with one row per person including
  those who could not be paid (reason, no amounts), stating the month and the run's status in the file, and showing
  figures equal to the screen. Each export MUST be audited with who, which run and when.
- **FR-028**: An individual's pay MUST be visible only to users with the right to view payroll (Admin and Director by
  default). A Manager, a Teacher and System MUST NOT see anyone's pay, including their own. Salary figures MUST NOT be
  returned to a user without the right.
- **FR-029**: The system MUST audit: starting, recomputing, submitting, returning, approving and cancelling a run; each
  adjustment recorded or withdrawn; each payment detail entered or corrected; each export; and the issue of payslips,
  each with who and when and, where there is one, the reason.
- **FR-030**: The run MUST use only the published answers of the other modules (spec 013a's pay rules, the attendance and
  Manager attendance read contracts, and the attendance lock state); it MUST NOT read their tables (Constitution
  Principle VII).

### Key Entities *(include if feature involves data)*

- **Pay run**: a primary run (one per month, cancelled runs aside) or a supplementary run (for the people still owed):
  the month, its kind and number, its status, who started, submitted, approved and when, the totals (earned, deduction,
  adjustments, net) and the pay policy used.
- **Run line**: one per payroll person in a run: person (kind and identity), the frozen working, the amounts, and its
  payment details. A line is either payable or "cannot be paid" with a reason.
- **Adjustment**: a signed amount with a kind and reason against a person; waiting, applied (to a run) or withdrawn.
- **Payslip**: the issued PDF of one paid line, fixed at approval.
- **Payment record**: the paid flag, date, mode and reference of a line, with its history of corrections.

## Role & Permission Impact *(mandatory — Constitution Principles II–IV)*

| Role     | Menu (section → item) | Default actions | Data scope |
| -------- | --------------------- | --------------- | ---------- |
| Admin    | OPERATIONS → Payroll | View, Process (start, recompute, cancel, submit, record adjustments, record payment), Export | Org-wide |
| Director | OPERATIONS → Payroll | View, Approve (approve, return), Export | Org-wide |
| Manager  | None | None | None |
| Teacher  | None | None | None |
| System   | None | None | None |

**New permission keys**: `PAYROLL.VIEW`, `PAYROLL.PROCESS`, `PAYROLL.APPROVE`, `PAYROLL.EXPORT`. Only users holding the
right are shown the menu item; every endpoint checks it on the server.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: For a month with a known set of people, the run's figures for every person (earned pay, deduction, net pay)
  equal the hand-computed ones to the rupee in 100% of a set of fixtures covering a full month, a joiner, an exit, a
  mid-month raise, a half day, Loss-of-Pay leave, a Manager with unpaid days and a Director.
- **SC-002**: A run for 1,000 people is computed in under 2 minutes, and the Admin can use other screens while it runs.
- **SC-003**: After approval, 100% of the run's figures, payslips and export rows are identical before and after a holiday
  is added, an attendance month is reopened, a salary is backdated and the policy is changed.
- **SC-004**: No person is ever shown with zero pay because something was missing: every person who cannot be paid appears
  with a reason, in 100% of the cases listed in FR-005 and FR-006.
- **SC-005**: A reviewer can reproduce any line's net pay from its working screen alone, without opening another screen,
  in under 2 minutes.
- **SC-006**: No run can be approved by the person who submitted it, and no approved run can be changed, in 100% of the
  attempts tested.
- **SC-007**: Every person paid in an approved run has exactly one payslip, and every person left out of it is paid
  exactly once later, never twice and never lost, in 100% of the fixtures.
- **SC-008**: The accountant's file matches the screen and the payslips for every person in the run.

## Assumptions

- Spec 013a's rules are used as defined there: a fixed monthly salary per person versioned by effective date, a day of
  loss of pay valued at that day's salary over the month's working days, one rounding of the month's total by the policy
  in effect on the month's last day, and payable days with pro-rata for joining, transfer and exit.
- Earned pay is prorated the same way a day of loss of pay is valued (each payable day at its own day's salary over the
  month's working days), so a person at a constant salary who is payable all month earns exactly their salary, and a
  joiner or leaver earns the same per-day value as the deduction uses. Earned pay and the deduction are rounded
  separately, once each, by the same policy.
- The divisor is the month's working-day count that spec 013a's pay rules return for the person (a Teacher's School
  calendar, the default calendar for Managers and staff).
- A Teacher's final attendance for a month is their locked attendance month (spec 008); a Manager's is the locked Manager
  month (spec 032); an Admin or Director has no attendance and so no unpaid days, by the rule of spec 013a.
- The run covers one calendar month and runs on demand; no automatic scheduling. Any ended month without a run may be
  chosen, so the first run can be for any recent month.
- The pay amounts are in rupees to two decimals; the policy's rounding decides how the month's total is rounded.
- Payment is entirely outside the system (decision D8); the system never moves money.
- Incentives (030), reimbursements (015) and substitute credit (017b) are not built. Their adjustment kinds are reserved
  and shown, and nothing creates them; each later spec will create adjustments of its kind.
- Mobile access to pay, tax, provident fund and statutory deductions, bonuses and arrears of a salary change are out of
  scope; arrears are done through adjustments.
- One approver per run is enough; there is no multi-level approval.
- Notifications use the existing in-app notification feature (spec 010); no email or SMS is sent.

> **Amendment (spec 015, Expenses):** the reserved kind `REIMBURSEMENT` is created by spec 015 through the additive
> `payroll.api.ReimbursementIntake` (offer, withdraw, status) with `source_ref` set to the claim id, never by hand. Approving a
> run publishes `payroll.api.PayrollAdjustmentApplied` once per applied adjustment that has a `source_ref`, in the approval
> transaction, so the claim becomes Paid with payroll together with the run. Nothing here changes an approved run.

> **Amended by spec 040 (2026-10-28):** The unpaid days it reads from 013a now include every Teacher leave day, whatever the leave type.
