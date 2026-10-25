# Feature Specification: School Billing and Receivables

**Feature Branch**: `022-school-billing`

**Created**: 2026-10-26

**Status**: Draft

**Input**: User description: "022-school-billing: Invoicing Schools and collecting what they owe, for the Teachers mapped under the MoUs of spec 012. Invoices derived from active assignments (D6), credit notes, receipts, ageing, collections, follow-up tasks through spec 025, escalation to the Director, no GST (D10). Full source: docs/spec-inputs/022-school-billing.md."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Raise a Monthly Invoice from Assignments (Priority: P1)

An Admin or Director raises the invoice for a School and a month. Each line is one Teacher mapped to a position of the School's contract in that month, at that position's salary. A Teacher assigned for the whole month is billed in full; one who joined, was transferred in or out, or exited during the month is billed for the part of the month at the School. Absences do not reduce the invoice. Teachers at the School with no position, and Schools with no signed MoU, are listed as "not billed" so nothing is missed.

**Why this priority**: Without an invoice there is nothing to collect; this is the core of the receivables process.

**Independent Test**: With a School that has a contract and three mapped Teachers (one joined mid-month), raise the invoice; the lines, pro-rated amount and total match a hand calculation, an absence changes nothing, and the unmapped Teacher appears in "not billed".

**Acceptance Scenarios**:

1. **Given** a School whose contract has two Teachers mapped for the whole month, **When** the invoice is raised, **Then** it has one line per Teacher at the position salary and the total is their sum.
2. **Given** a Teacher who joined the School on day 11 of a 26-working-day month, **When** the invoice is raised, **Then** the line is the salary times the Teacher's working days at the School over the month's working days, and the line shows the days and the working.
3. **Given** a Teacher absent for five days in the month, **When** the invoice is raised, **Then** the line is unchanged.
4. **Given** a Teacher transferred from School A to School B on a date in the month, **When** both invoices are raised, **Then** each School is billed its part and the two parts add up to one month for the position salaries.
5. **Given** a School that already has a non-cancelled invoice for the month, **When** a second is raised, **Then** it is refused and the existing invoice is shown.
6. **Given** a School with no signed MoU or a Teacher with no position, **When** the invoice screen is opened, **Then** they appear in "not billed" with the reason.
7. **Given** a raised invoice, **When** anyone looks for a way to edit its lines or amounts, **Then** none exists; it shows how each amount was worked out.

---

### User Story 2 - Send, Number and Track an Invoice (Priority: P1)

Each invoice has a number, an invoice date and a due date (invoice date plus the payment due days, a setting). The user marks it sent to the School with the date. The invoice shows its status (raised, sent, part paid, paid, cancelled) and a printable view. A wrong invoice is cancelled with a reason and stays visible; a corrected one can then be raised for the same month.

**Why this priority**: The due date drives collection, ageing and escalation.

**Independent Test**: Raise an invoice, mark it sent, print it, cancel it with a reason, raise a replacement; both remain listed with their statuses.

**Acceptance Scenarios**:

1. **Given** the setting is 15 payment due days, **When** an invoice dated 05/11/2026 is raised, **Then** its due date is 20/11/2026.
2. **Given** a raised invoice, **When** the user marks it sent with a date, **Then** the status is sent and the date is recorded and audited.
3. **Given** an invoice with receipts allocated, **When** a user cancels it, **Then** it is refused until those allocations are reversed.
4. **Given** a cancelled invoice, **When** a corrected invoice is raised for the same month, **Then** both are visible and only the corrected one counts in totals.

---

### User Story 3 - Record Receipts and Allocate Them (Priority: P1)

The School's Zone Manager (own Schools), Admin or Director records a receipt: amount, date, mode (Bank, Cash, Cheque, UPI), reference, receiver and comment. The receipt is allocated to one or more invoices, part or full. A receipt larger than what is owed is kept as an advance credit for the School. Receipts are never edited or deleted; a correction is a reversing entry with a reason. A likely duplicate is warned about, not refused.

**Why this priority**: Collections are the reason for the module.

**Independent Test**: Record a receipt that fully pays one invoice and partly pays another; statuses and balances update, and reversing it restores them.

**Acceptance Scenarios**:

1. **Given** two open invoices, **When** a receipt is allocated to the first in full and the second in part, **Then** the first is paid, the second is part paid and both balances are right.
2. **Given** a receipt of more than the School owes, **When** it is allocated, **Then** the excess is held as advance credit and shown on the School.
3. **Given** a receipt with the same School, amount, date, mode and reference as an earlier one, **When** it is saved, **Then** the user is warned and can continue.
4. **Given** a recorded receipt, **When** a user reverses it with a reason, **Then** the allocations are undone by a new entry and the original stays visible.
5. **Given** a Zone Manager, **When** they record a receipt for a School outside their area, **Then** it is refused.

---

### User Story 4 - Credit Notes for Agreed Deductions (Priority: P2)

When HLS and a School agree a deduction (D6: absences are never deducted automatically), an authorised user adds a credit note against an invoice with an amount and a reason. It is append-only; a mistake is corrected by a reversing entry. A credit note cannot take an invoice below zero.

**Why this priority**: It is the only way an invoice amount goes down, so it matters, but invoices and receipts work without it.

**Independent Test**: Add a credit note to an invoice; the balance falls by that amount; reverse it and the balance returns.

**Acceptance Scenarios**:

1. **Given** an invoice of 50,000 with nothing paid, **When** a credit note of 5,000 with a reason is added, **Then** the balance is 45,000 and the invoice shows the credit note.
2. **Given** a credit note larger than the invoice balance, **When** it is saved, **Then** it is refused.
3. **Given** a credit note that clears an invoice, **When** it is added, **Then** the invoice is paid and any open follow-up task closes.

---

### User Story 5 - Balances and Ageing by School, Area and Manager (Priority: P2)

Admin and Director see billed, collected and outstanding for a month, organization-wide and by area, Zone Manager and School, with an ageing table (0 to 30, 31 to 60, 61 to 90, over 90 days past due). A Zone Manager sees the same for their own Schools only. A School page lists its invoices, credit notes, receipts, advance credit and balance. The lists can be exported to CSV in the same scope.

**Why this priority**: It gives management the receivables picture, building on P1 data.

**Independent Test**: Seed invoices with known due dates and receipts; the ageing buckets and totals match a hand calculation, and a Zone Manager sees only their Schools in every figure and the export.

**Acceptance Scenarios**:

1. **Given** an invoice 45 days past due with a balance, **When** the ageing table is opened, **Then** its balance is in the 31 to 60 bucket.
2. **Given** two Zone Managers, **When** each opens School Billing, **Then** each sees only their own Schools in lists, totals, ageing and export.
3. **Given** a School's Zone Manager changed, **When** the new Zone Manager opens the School, **Then** they see all past months and the previous Zone Manager sees nothing.

---

### User Story 6 - Overdue Invoices Create Follow-up and Escalate (Priority: P2)

When an invoice passes its due date with a balance, the system creates a follow-up task for the School's Zone Manager through the Manager Tasks interface (spec 025) and notifies them (spec 010). If the invoice is still unpaid after a set number of days (a setting), it escalates to the Director. There is one task and one escalation per invoice, and both close when the invoice is paid or cancelled.

**Why this priority**: It turns ageing into action, but depends on the invoice and receipt data.

**Independent Test**: Let an invoice pass its due date; one task and one notification appear for the Zone Manager; run the daily check again and nothing is duplicated; after the escalation days the Director is told; pay the invoice and the task closes.

**Acceptance Scenarios**:

1. **Given** an unpaid invoice due yesterday, **When** the daily check runs, **Then** one follow-up task exists for the Zone Manager, linked to the invoice, and the Zone Manager is notified.
2. **Given** the check runs again the next day, **When** the invoice is still overdue, **Then** no second task is created.
3. **Given** an invoice overdue for the escalation days, **When** the check runs, **Then** the Director is notified once.
4. **Given** the invoice is paid in full or cancelled, **When** the status changes, **Then** the open follow-up task is completed or cancelled.
5. **Given** a School with no Zone Manager, **When** an invoice goes overdue, **Then** the Director and Admin are notified and no task is created.

---

### User Story 7 - Billing Figures for Payroll Margin and Reports (Priority: P3)

Payroll (013b) and reports (014) read, through a public interface, the amount billed per Teacher per month and each School's collected amount and balance, so margin (School fee minus Teacher pay) can be shown to the Director, Admin and, for their own Schools, the Zone Manager (D14). A month where the School has not paid is flagged.

**Why this priority**: Consumers are not built yet; the interface can ship with this spec and be used later.

**Independent Test**: Ask the interface for a Teacher's billed amount for a month and a School's balance; they equal the screen figures; asking for a month with no invoice returns nothing billed.

**Acceptance Scenarios**:

1. **Given** a raised invoice, **When** the interface is asked for a Teacher's billed amount in that month, **Then** it returns the invoice line amount.
2. **Given** a cancelled invoice, **When** asked, **Then** it contributes nothing.

---

### Edge Cases

- A Teacher is mapped to a position midway and the contract ends midway: the part billed is only the days inside both the assignment and the contract.
- A School with a "MoU pending" placeholder contract (spec 012): not billed, listed in "not billed".
- A position that is vacant all month adds nothing; a replacement Teacher in the same position continues billing; a substitute covering an absence does not change the invoice.
- A month with zero working days for a Teacher (for example a vacation month at the School): the pro-rated line is zero and shown as such.
- A receipt reversed after its invoice was cancelled, or an allocation to an invoice that is later cancelled: the amount returns to advance credit.
- An invoice raised for a month whose working-day calendar is not yet set: the invoice is refused with a clear message naming the School and month.
- Two users raise the invoice for the same School and month at the same time: only one succeeds.
- A School closed or its contract ended: invoices already raised remain collectable and still age.
- The overdue check runs several times a day or is missed for a day: no duplicate task, and the next run catches up.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: An Admin or Director MUST be able to raise one invoice per School per month; each line is one Teacher mapped to a contract position in that month at that position's salary, with the line's days, working days and working shown.
- **FR-002**: A Teacher assigned for the whole month MUST be billed in full; a Teacher who joined, moved or exited mid-month MUST be billed the salary times (Teacher's working days at the School) over (working days in the month), using the spec 008 calendar.
- **FR-003**: Absences, leave and substitute cover MUST NOT change an invoice amount (D6); a vacant position adds nothing; a replacement in the same position continues billing.
- **FR-004**: A raised invoice MUST be immutable; its lines and total never change.
- **FR-005**: At most one non-cancelled invoice MUST exist per School and month.
- **FR-006**: The system MUST list Teachers with no position and Schools with no signed MoU as "not billed", with the reason.
- **FR-007**: Each invoice MUST have a unique number, an invoice date, and a due date equal to the invoice date plus the payment due days setting.
- **FR-008**: An invoice MUST support the statuses raised, sent (with the sent date), part paid, paid and cancelled; "overdue" MUST be derived from the due date and balance, never stored.
- **FR-009**: Cancelling an invoice MUST require a reason, MUST be refused while receipts are allocated to it, and the cancelled invoice MUST stay visible.
- **FR-010**: A printable invoice view MUST be available.
- **FR-011**: A credit note MUST reduce an invoice by an amount with a reason, MUST NOT take the balance below zero, and MUST be append-only, corrected only by a reversing entry.
- **FR-012**: A receipt MUST record amount, date, mode (Bank, Cash, Cheque, UPI), reference, receiver and comment, and MUST be allocated to one or more invoices in part or full; any excess MUST be held as advance credit for the School.
- **FR-013**: Receipts and allocations MUST be append-only; a correction MUST be a reversing entry with a reason. A likely duplicate (same School, amount, date, mode, reference) MUST warn but not refuse.
- **FR-014**: Balances (per invoice and per School) MUST be computed from invoices, credit notes and receipts and never typed.
- **FR-015**: The system MUST show ageing buckets 0 to 30, 31 to 60, 61 to 90 and over 90 days past due, by School, by area and by Zone Manager.
- **FR-016**: Admin and Director MUST see billed, collected and outstanding for a month in total and per area, Zone Manager and School; a Zone Manager MUST see the same for their own Schools only.
- **FR-017**: When an invoice passes its due date with a balance, the system MUST create one follow-up task for the School's Zone Manager through the Manager Tasks interface (spec 025) and notify them (spec 010); with no Zone Manager it MUST notify the Director and Admin instead.
- **FR-018**: When an invoice stays unpaid for the escalation days setting after its due date, the system MUST notify the Director once; the follow-up task and escalation MUST close or be cancelled when the invoice is paid or cancelled.
- **FR-019**: The daily overdue check MUST be idempotent: repeated runs create no duplicate task or notification.
- **FR-020**: The system MUST expose a public interface giving the amount billed per Teacher per month and each School's collected amount and balance, for specs 013b, 014 and 028, and MUST read contracts and assignments only through their public interfaces.
- **FR-021**: Lists, totals, ageing, the "not billed" list and CSV exports MUST be filtered by the caller's scope; a Zone Manager MUST never see or act on a School outside their area, and after a Zone Manager change only the current one sees the School's billing, past months included.
- **FR-022**: Every invoice, send, cancellation, credit note, receipt, allocation, reversal and setting change MUST be written to the audit trail (spec 003) with actor, time and before and after values.
- **FR-023**: Money MUST be stored exactly to two decimals and shown in rupees with Indian digit grouping; dates MUST show as DD/MM/YYYY; no GST or tax lines exist (D10).
- **FR-024**: Menu items and actions MUST be offered only when the access model grants them, and every endpoint MUST enforce the same permission and scope server-side.

### Key Entities

- **Invoice**: one School and month; number, invoice date, due date, status, sent date, cancellation reason, total; never edited after raising.
- **Invoice line**: one Teacher and position in the invoice; salary, days at the School, working days in the month, amount, working.
- **Credit note**: an append-only reduction of one invoice with amount, reason and optional reversal.
- **Receipt**: money received from a School with amount, date, mode, reference, receiver, comment; append-only with reversal.
- **Allocation**: the part of a receipt applied to one invoice; reversible.
- **Advance credit**: the unallocated part of receipts held for a School.
- **Billing settings**: payment due days and escalation days.
- **Overdue follow-up**: the link between an overdue invoice, its task in spec 025 and its escalation state.

## Role & Permission Impact *(mandatory — Constitution Principles II–IV)*

New permission module **School Billing** (`SCHOOL_BILLING`) with actions View, Create, Edit, Approve, Export.

| Role     | Menu (section → item) | Default actions | Data scope |
| -------- | --------------------- | --------------- | ---------- |
| Admin    | OPERATIONS → School Billing | View, Create, Edit, Approve, Export | Org-wide |
| Director | OPERATIONS → School Billing | View, Create, Edit, Approve, Export | Org-wide; receives escalations |
| Manager (Zone Manager) | OPERATIONS → School Billing | View, Export; Create receipts for own Schools | Assigned (own Schools) |
| Teacher  | none | none, cannot be granted | None |
| System   | none | none, cannot be granted | None (business data) |

**New permission keys**: `SCHOOL_BILLING.view`, `.create`, `.edit`, `.approve`, `.export`. Raising, cancelling and credit notes are Admin and Director only; the exact action behind each is settled in planning. The constitution's default access matrix gains a School Billing row.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: An Admin raises a School's monthly invoice in under 1 minute, and every line's amount matches a hand calculation in 100% of test cases.
- **SC-002**: A receipt is recorded and allocated to invoices in under 1 minute.
- **SC-003**: Balances and ageing totals equal the sum of invoices less credit notes and receipts for every School, with no manual entry anywhere.
- **SC-004**: No invoice, credit note or receipt can be edited or deleted; 100% of corrections show as separate entries, each audited with actor, time and reason.
- **SC-005**: Each overdue invoice produces exactly one follow-up task and at most one escalation, however many times the daily check runs.
- **SC-006**: A Zone Manager never sees a School outside their area in any list, total, ageing figure or export (verified for every endpoint).
- **SC-007**: The Director is told of an overdue invoice on the day the escalation days are reached.
- **SC-008**: The billing overview and ageing for 500 Schools with 12 months of invoices load in under 3 seconds.

## Assumptions

- Version 1 is monthly per Teacher, plain invoices, no GST (D10); per-term or annual fees are the deferred spec 012b.
- Working defaults, each put to the owner in `clarification-questions.md`: the invoice is raised after the month begins (a "raise all" action plus per-School raising); numbers are a yearly-resetting sequence; payment due days 15 and escalation 7 days after the due date; credit notes by Admin and Director only; browser print is enough for the printable view; an overdue School blocks nothing.
- No PDF file is generated and nothing is sent by email or SMS; the user records that the invoice was sent.
- Out of scope: GST, per-term or annual billing, payment gateways and bank reconciliation, other currencies, mobile screens, Teacher pay and payslips (013), substitute payout (017b).
- Dependencies: 012 (contract positions, assignments), 008 and 037/038 (working-day calendar), 005 (Schools, Zone Manager, scope), 003 (audit), 010 (notifications), 025 (follow-up tasks).
