# Feature Specification: School Billing and Receivables

**Feature Branch**: `022-school-billing`

**Created**: 2026-10-26

**Status**: Draft

**Input**: User description: "022-school-billing: An internal receivables ledger. For each School and month, record how much the School owes HLS for the Teachers placed there, derived from the active assignments (D6). Adjustments for agreed deductions, collections recorded against what is owed, ageing, follow-up tasks through spec 025, escalation to the Director, no GST (D10). Nothing is sent to the School. Full source: docs/spec-inputs/022-school-billing.md."

## Clarifications

### Session 2026-10-26

The owner answered "defaults" to all five questions (option A each); the answers are folded into the requirements below.

- Q: When is a monthly due raised? -> A: After the month starts, by one "raise all Schools" action plus a per-School action; Schools already raised are skipped.
- Q: How is the due date set? -> A: Raised date plus 15 days; the follow-up task is created the day after the due date; escalation to the Director is 7 days after the due date (both numbers are settings).
- Q: Who adds or reverses adjustments and cancels or replaces a wrong due? -> A: Admin and Director only; a Zone Manager can see but not change them. A wrong due is cancelled with a reason and a corrected one raised; both stay visible.
- Q: Must a collection be allocated when recorded? -> A: Allocation is chosen when recorded and defaults to the oldest due first; any excess is kept as advance credit and can be allocated later by the same users.
- Q: From when does ageing count, and does an overdue School block anything? -> A: Days past the due date (amounts not yet due show as "not yet due"); overdue is information only and blocks nothing.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Work Out What a School Owes for a Month (Priority: P1)

An Admin or Director raises the monthly due for a School and a month. It is an internal record of what the School owes HLS. Each line is one Teacher mapped to a position of the School's contract in that month, at that position's salary. A Teacher assigned for the whole month counts in full; one who joined, was transferred in or out, or exited during the month counts for the part of the month at the School. Absences do not reduce the amount. Teachers at the School with no position, and Schools with no signed MoU, are listed as "not billed" so nothing is missed.

**Why this priority**: Without the amount owed there is nothing to collect; this is the core of the ledger.

**Independent Test**: With a School that has a contract and three mapped Teachers (one joined mid-month), raise the monthly due; the lines, pro-rated amount and total match a hand calculation, an absence changes nothing, and the unmapped Teacher appears in "not billed".

**Acceptance Scenarios**:

1. **Given** a School whose contract has two Teachers mapped for the whole month, **When** the monthly due is raised, **Then** it has one line per Teacher at the position salary and the total is their sum.
2. **Given** a Teacher who joined the School on day 11 of a 26-working-day month, **When** it is raised, **Then** the line is the salary times the Teacher's working days at the School over the month's working days, and the line shows the days and the working.
3. **Given** a Teacher absent for five days in the month, **When** it is raised, **Then** the line is unchanged.
4. **Given** a Teacher transferred from School A to School B on a date in the month, **When** both dues are raised, **Then** each School owes its part and the two parts add up to one month of the position salaries.
5. **Given** a School that already has a non-cancelled due for the month, **When** a second is raised, **Then** it is refused and the existing one is shown.
6. **Given** a School with no signed MoU or a Teacher with no position, **When** the screen is opened, **Then** they appear in "not billed" with the reason.
7. **Given** a raised due, **When** anyone looks for a way to edit its lines or amounts, **Then** none exists; it shows how each amount was worked out.

---

### User Story 2 - Track the Status and Due Date of Each Monthly Due (Priority: P1)

Each monthly due has a simple internal reference, a raised date and a due date (a number of days after the raised date, a setting). Its status is open, part paid, paid or cancelled. A wrong due is cancelled with a reason and stays visible; a corrected one can then be raised for the same month. Nothing is sent to the School.

**Why this priority**: The due date drives collection, ageing and escalation.

**Independent Test**: Raise a due, cancel it with a reason, raise a replacement; both remain listed with their statuses and only the replacement counts in totals.

**Acceptance Scenarios**:

1. **Given** the setting is 15 days, **When** a due is raised on 05/11/2026, **Then** its due date is 20/11/2026.
2. **Given** a due with collections allocated, **When** a user cancels it, **Then** it is refused until those allocations are reversed.
3. **Given** a cancelled due, **When** a corrected one is raised for the same month, **Then** both are visible and only the corrected one counts in totals.

---

### User Story 3 - Record Collections and Allocate Them (Priority: P1)

The School's Zone Manager (own Schools), Admin or Director records a collection (receipt): amount, date, mode (Bank, Cash, Cheque, UPI), reference, receiver and comment. It is allocated to one or more monthly dues, part or full. An amount larger than what is owed is kept as an advance credit for the School. Collections are never edited or deleted; a correction is a reversing entry with a reason. A likely duplicate is warned about, not refused.

**Why this priority**: Collections are the reason for the module.

**Independent Test**: Record a collection that fully pays one due and partly pays another; statuses and balances update, and reversing it restores them.

**Acceptance Scenarios**:

1. **Given** two open dues, **When** a collection is allocated to the first in full and the second in part, **Then** the first is paid, the second is part paid and both balances are right.
2. **Given** a collection of more than the School owes, **When** it is allocated, **Then** the excess is held as advance credit and shown on the School.
3. **Given** a collection with the same School, amount, date, mode and reference as an earlier one, **When** it is saved, **Then** the user is warned and can continue.
4. **Given** a recorded collection, **When** a user reverses it with a reason, **Then** the allocations are undone by a new entry and the original stays visible.
5. **Given** a Zone Manager, **When** they record a collection for a School outside their area, **Then** it is refused.

---

### User Story 4 - Adjustments for Agreed Deductions (Priority: P2)

When HLS and a School agree a deduction (D6: absences are never deducted automatically), an authorised user adds an adjustment (credit note) against a monthly due with an amount and a reason. It is append-only; a mistake is corrected by a reversing entry. An adjustment cannot take a due below zero.

**Why this priority**: It is the only way the amount owed goes down other than collection, but the ledger works without it.

**Independent Test**: Add an adjustment; the balance falls by that amount; reverse it and the balance returns.

**Acceptance Scenarios**:

1. **Given** a due of 50,000 with nothing collected, **When** an adjustment of 5,000 with a reason is added, **Then** the balance is 45,000 and the due shows the adjustment.
2. **Given** an adjustment larger than the balance, **When** it is saved, **Then** it is refused.
3. **Given** an adjustment that clears a due, **When** it is added, **Then** the due is paid and any open follow-up task closes.

---

### User Story 5 - Balances and Ageing by School, Area and Manager (Priority: P2)

Admin and Director see owed, collected and outstanding for a month, organization-wide and by area, Zone Manager and School, with an ageing table (0 to 30, 31 to 60, 61 to 90, over 90 days past due). A Zone Manager sees the same for their own Schools only. A School page lists its monthly dues, adjustments, collections, advance credit and balance. The lists can be exported to CSV in the same scope.

**Why this priority**: It gives management the receivables picture, building on P1 data.

**Independent Test**: Seed dues with known due dates and collections; the ageing buckets and totals match a hand calculation, and a Zone Manager sees only their Schools in every figure and the export.

**Acceptance Scenarios**:

1. **Given** a due 45 days past its due date with a balance, **When** the ageing table is opened, **Then** its balance is in the 31 to 60 bucket.
2. **Given** two Zone Managers, **When** each opens School Billing, **Then** each sees only their own Schools in lists, totals, ageing and export.
3. **Given** a School's Zone Manager changed, **When** the new Zone Manager opens the School, **Then** they see all past months and the previous Zone Manager sees nothing.

---

### User Story 6 - Overdue Dues Create Follow-up and Escalate (Priority: P2)

When a monthly due passes its due date with a balance, the system creates a follow-up task for the School's Zone Manager through the Manager Tasks interface (spec 025) and notifies them (spec 010). If it stays unpaid after a set number of days (a setting), it escalates to the Director. There is one task and one escalation per due, and both close when it is paid or cancelled.

**Why this priority**: It turns ageing into action, but depends on the ledger data.

**Independent Test**: Let a due pass its due date; one task and one notification appear for the Zone Manager; run the daily check again and nothing is duplicated; after the escalation days the Director is told; pay it and the task closes.

**Acceptance Scenarios**:

1. **Given** an unpaid due that was due yesterday, **When** the daily check runs, **Then** one follow-up task exists for the Zone Manager, linked to the due, and the Zone Manager is notified.
2. **Given** the check runs again the next day, **When** the due is still overdue, **Then** no second task is created.
3. **Given** a due overdue for the escalation days, **When** the check runs, **Then** the Director is notified once.
4. **Given** the due is paid in full or cancelled, **When** the status changes, **Then** the open follow-up task is completed or cancelled.
5. **Given** a School with no Zone Manager, **When** a due goes overdue, **Then** the Director and Admin are notified and no task is created.

---

### User Story 7 - Receivable Figures for Payroll Margin and Reports (Priority: P3)

Payroll (013b) and reports (014) read, through a public interface, the amount owed per Teacher per month and each School's collected amount and balance, so margin (School fee minus Teacher pay) can be shown to the Director, Admin and, for their own Schools, the Zone Manager (D14). A month where the School has not paid is flagged.

**Why this priority**: Consumers are not built yet; the interface can ship with this spec and be used later.

**Independent Test**: Ask the interface for a Teacher's amount for a month and a School's balance; they equal the screen figures; a month with no due returns nothing.

**Acceptance Scenarios**:

1. **Given** a raised due, **When** the interface is asked for a Teacher's amount in that month, **Then** it returns the line amount.
2. **Given** a cancelled due, **When** asked, **Then** it contributes nothing.

---

### Edge Cases

- A Teacher is mapped to a position midway and the contract ends midway: only the days inside both the assignment and the contract count.
- A School with a "MoU pending" placeholder contract (spec 012): not billed, listed in "not billed".
- A position vacant all month adds nothing; a replacement Teacher in the same position continues; a substitute covering an absence does not change the amount.
- A month with zero working days for a Teacher (for example a vacation month): the pro-rated line is zero and shown as such.
- A collection reversed after its due was cancelled, or allocated to a due that is later cancelled: the amount returns to advance credit.
- A month whose working-day calendar is not yet set: raising is refused with a clear message naming the School and month.
- Two users raise the due for the same School and month at the same time: only one succeeds.
- A School closed or its contract ended: dues already raised remain collectable and still age.
- The overdue check runs several times a day or is missed for a day: no duplicate task, and the next run catches up.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: An Admin or Director MUST be able to raise one monthly due per School per month; each line is one Teacher mapped to a contract position in that month at that position's salary, with the line's days, working days and working shown.
- **FR-002**: A Teacher assigned for the whole month MUST count in full; a Teacher who joined, moved or exited mid-month MUST count the salary times (Teacher's working days at the School) over (working days in the month), using the spec 008 calendar.
- **FR-003**: Absences, leave and substitute cover MUST NOT change the amount (D6); a vacant position adds nothing; a replacement in the same position continues.
- **FR-004**: A raised due MUST be immutable; its lines and total never change.
- **FR-005**: At most one non-cancelled monthly due MUST exist per School and month.
- **FR-006**: The system MUST list Teachers with no position and Schools with no signed MoU as "not billed", with the reason.
- **FR-007**: Each due MUST have a simple internal reference, a raised date, and a due date equal to the raised date plus the payment due days setting.
- **FR-008**: A due MUST support the statuses open, part paid, paid and cancelled; "overdue" MUST be derived from the due date and balance, never stored.
- **FR-009**: Cancelling a due MUST require a reason, MUST be refused while collections are allocated to it, and the cancelled due MUST stay visible.
- **FR-010**: An adjustment (credit note) MUST reduce a due by an amount with a reason, MUST NOT take the balance below zero, and MUST be append-only, corrected only by a reversing entry.
- **FR-011**: A collection MUST record amount, date, mode (Bank, Cash, Cheque, UPI), reference, receiver and comment, and MUST be allocated to one or more dues in part or full; any excess MUST be held as advance credit for the School.
- **FR-012**: Collections and allocations MUST be append-only; a correction MUST be a reversing entry with a reason. A likely duplicate (same School, amount, date, mode, reference) MUST warn but not refuse.
- **FR-013**: Balances (per due and per School) MUST be computed from dues, adjustments and collections and never typed.
- **FR-014**: The system MUST show ageing buckets 0 to 30, 31 to 60, 61 to 90 and over 90 days past due, by School, by area and by Zone Manager.
- **FR-015**: Admin and Director MUST see owed, collected and outstanding for a month in total and per area, Zone Manager and School; a Zone Manager MUST see the same for their own Schools only.
- **FR-016**: When a due passes its due date with a balance, the system MUST create one follow-up task for the School's Zone Manager through the Manager Tasks interface (spec 025) and notify them (spec 010); with no Zone Manager it MUST notify the Director and Admin instead.
- **FR-017**: When a due stays unpaid for the escalation days setting after its due date, the system MUST notify the Director once; the follow-up task and escalation MUST close or be cancelled when the due is paid or cancelled.
- **FR-018**: The daily overdue check MUST be idempotent: repeated runs create no duplicate task or notification.
- **FR-019**: The system MUST expose a public interface giving the amount owed per Teacher per month and each School's collected amount and balance, for specs 013b, 014 and 028, and MUST read contracts and assignments only through their public interfaces.
- **FR-020**: Lists, totals, ageing, the "not billed" list and CSV exports MUST be filtered by the caller's scope; a Zone Manager MUST never see or act on a School outside their area, and after a Zone Manager change only the current one sees the School's ledger, past months included.
- **FR-021**: Every raise, cancellation, adjustment, collection, allocation, reversal and setting change MUST be written to the audit trail (spec 003) with actor, time and before and after values.
- **FR-022**: Money MUST be stored exactly to two decimals and shown in rupees with Indian digit grouping; dates MUST show as DD/MM/YYYY; no GST or tax lines exist (D10).
- **FR-023**: Menu items and actions MUST be offered only when the access model grants them, and every endpoint MUST enforce the same permission and scope server-side.

### Key Entities

- **Monthly due (receivable)**: one School and month; internal reference, raised date, due date, status, cancellation reason, total; never edited after raising.
- **Due line**: one Teacher and position; salary, days at the School, working days in the month, amount, working.
- **Adjustment (credit note)**: an append-only reduction of one due with amount, reason and optional reversal.
- **Collection (receipt)**: money received from a School with amount, date, mode, reference, receiver, comment; append-only with reversal.
- **Allocation**: the part of a collection applied to one due; reversible.
- **Advance credit**: the unallocated part of collections held for a School.
- **Billing settings**: payment due days and escalation days.
- **Overdue follow-up**: the link between an overdue due, its task in spec 025 and its escalation state.

## Role & Permission Impact *(mandatory — Constitution Principles II–IV)*

New permission module **School Billing** (`SCHOOL_BILLING`) with actions View, Create, Edit, Approve, Export.

| Role     | Menu (section → item) | Default actions | Data scope |
| -------- | --------------------- | --------------- | ---------- |
| Admin    | OPERATIONS → School Billing | View, Create, Edit, Approve, Export | Org-wide |
| Director | OPERATIONS → School Billing | View, Create, Edit, Approve, Export | Org-wide; receives escalations |
| Manager (Zone Manager) | OPERATIONS → School Billing | View, Export; Create collections for own Schools | Assigned (own Schools) |
| Teacher  | none | none, cannot be granted | None |
| System   | none | none, cannot be granted | None (business data) |

**New permission keys**: `SCHOOL_BILLING.view`, `.create`, `.edit`, `.approve`, `.export`. Raising, cancelling and adjustments are Admin and Director only; the exact action behind each is settled in planning. The constitution's default access matrix gains a School Billing row.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: An Admin raises a School's monthly due in under 1 minute, and every line's amount matches a hand calculation in 100% of test cases.
- **SC-002**: A collection is recorded and allocated in under 1 minute.
- **SC-003**: Balances and ageing totals equal dues less adjustments and collections for every School, with no manual entry anywhere.
- **SC-004**: No due, adjustment or collection can be edited or deleted; 100% of corrections show as separate entries, each audited with actor, time and reason.
- **SC-005**: Each overdue due produces exactly one follow-up task and at most one escalation, however many times the daily check runs.
- **SC-006**: A Zone Manager never sees a School outside their area in any list, total, ageing figure or export (verified for every endpoint).
- **SC-007**: The Director is told of an overdue due on the day the escalation days are reached.
- **SC-008**: The overview and ageing for 500 Schools with 12 months of dues load in under 3 seconds.

## Assumptions

- Version 1 is monthly per Teacher, no GST (D10); per-term or annual fees are the deferred spec 012b.
- This is an internal ledger: nothing is sent, emailed, printed for or shared with the School, and there is no invoice document or external invoice numbering.
- Working defaults, each put to the owner in `clarification-questions.md`: dues are raised after the month starts (a "raise all" action plus per-School raising); due date is the raised date plus 15 days and escalation is 7 days after the due date; adjustments and cancellations by Admin and Director only; collections are allocated when recorded, oldest first, with any excess kept as advance credit; ageing counts from the due date; an overdue School blocks nothing.
- Out of scope: GST, per-term or annual billing, payment gateways and bank reconciliation, other currencies, mobile screens, Teacher pay and payslips (013), substitute payout (017b).
- Dependencies: 012 (contract positions, assignments), 008 and 037/038 (working-day calendar), 005 (Schools, Zone Manager, scope), 003 (audit), 010 (notifications), 025 (follow-up tasks).
