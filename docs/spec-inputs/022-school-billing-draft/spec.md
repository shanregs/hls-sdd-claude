# Feature Specification: School Contracts and Billing

**Feature Branch**: `012-school-contracts-billing`

**Created**: 2026-10-05

**Status**: Draft

**Input**: User description: "012-school-contracts-billing: The commercial side of each partner school. The contract is the MoU between HLS and the School: number of Teachers, start and end date, the salary the School pays (same for all Teachers or different for each), and the signing details (date signed, School signatories, HLS Zone Manager and Director). Recruited Teachers are mapped to the contract's positions later, replacing the interim placement of spec 005. Monthly receivables are generated from mapped Teachers; payments are append-only with a computed balance; billing views for Admin, Director and Zone Manager; overdue alerts through spec 010. Module `schoolbilling`. Full source: docs/spec-inputs/012-school-contracts-billing.md."

## Clarifications

### Session 2026-10-05

- Q: What does the contract cover? (direction from the user, replacing the earlier "one basis, per-Teacher rate or
  lump sum" answer) -> A: The contract is the MoU between HLS and the School: the School, the number of Teachers,
  start and end date, whether every Teacher has the same salary or each has a different one, and the signing
  details (the date signed, the School's signatories, and HLS's Zone Manager and Director). A lump sum is no
  longer a basis. Recruited Teachers are mapped to the contract's Teacher positions afterwards.
- Q: Who closes a billing month, and does it depend on attendance? → A: An Admin closes it (Approve action),
  and only after that month's attendance is locked (spec 008).
- Q: Can a contract be back-dated so earlier months get receivables? → A: No. Receivables start from the first
  open month at go-live; earlier months are not billed in the system and no opening balance is entered.
- Q: After a Manager change, who sees the School's billing? → A: Only the current Manager, including past
  months. The previous Manager loses access; their name stays as receiver on the payments they recorded.
- Q: Can a Manager create or change a contract's rate and terms? → A: No. Only Admin and Director create or edit
  contracts and rates. A Manager can view them for their own Schools, assign Teachers and record payments.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Record a School's Contract and Its Teachers (Priority: P1) 🎯 MVP

An Admin or Director opens a School and records its contract, which is the MoU between HLS and the School:
the number of Teachers it covers, a start date and an optional end date, whether the salary the School pays is
the **same for every Teacher** (one amount) or **different for each** (an amount for each Teacher position), and
the **signing details**: the date the MoU was signed, who signed for the School (name and designation), and who
signed for HLS (the School's Zone Manager and a Director). The contract has one position for each Teacher it
covers. Teachers are mapped to its positions afterwards, as they are recruited, by an Admin, a Director or the
School's Zone Manager. When the terms change, a new MoU is recorded and the old one ends, so the earlier terms
and signatures stay visible.

**Why this priority**: Every other part of billing, and later payroll and reports, reads the rate and the
Teacher assignments from here. Without a contract there is nothing to bill.

**Independent Test**: Create a contract for a School for 3 Teachers at different salaries with its signing
details, map two recruited Teachers to two positions, then replace it with a new MoU and see both contracts, with
their signatories, in the School's history and only the new one active.

**Acceptance Scenarios**:

1. **Given** a School with no contract, **When** an Admin creates a contract for 4 Teachers at one salary for
   all, with a start date and the signing details, **Then** the contract is active from that date, has 4
   positions at that salary, and is shown on the School with its signatories.
1a. **Given** the same form, **When** the salary is set to "different for each Teacher", **Then** an amount is
   asked for each of the N positions, and the contract cannot be saved until every position has an amount.
1b. **Given** the form, **When** the signed date, a School signatory (name and designation), or the HLS Zone
   Manager or Director signatory is missing, or the signed date is in the future, **Then** the contract is not
   saved and the missing item is named.
2. **Given** a School with an active contract, **When** the user starts a new contract from a later date,
   **Then** the old contract ends the day before, the new one is active, and both appear in the history.
3. **Given** a School in a Zone, **When** a contract is created, **Then** the responsible Manager is the
   School's Zone Manager (the Manager assigned to it in spec 005); it is not chosen on the contract. A School
   with no Zone Manager cannot get a contract yet, and the user is told to assign one first.
4. **Given** an active contract, **When** a Teacher is assigned from a start date, **Then** the Teacher's
   current School is that School; assigning the same Teacher to a second School on overlapping dates is
   refused.
5. **Given** an active contract for 4 Teachers with all 4 positions filled, **When** a fifth Teacher is
   assigned, **Then** it is refused with "All 4 positions are filled; record a new contract to add Teachers."
6. **Given** a new Teacher recruited for a School whose contract has a vacant position, **When** the Zone
   Manager maps the Teacher to a position (for different salaries, the one the Teacher was recruited into),
   **Then** the Teacher is assigned to the School from the start date and the position shows as filled.
7. **Given** a new contract that replaces the old one, **When** the user maps the Teachers who are already at
   the School to its positions, **Then** each Teacher's assignment under the old contract ends the day before
   and a new one starts under the new contract's position, and the Teacher stays placed in the School with no
   gap.

---

### User Story 2 - Replace the Interim Placement (Priority: P1)

The interim Teacher–School placement from spec 005 stops being a separate thing. Existing placements are
carried over as assignments, the screens that showed "interim" now show the contract assignment, and a
Teacher still has exactly one current School at a time. Nothing is lost: each carried-over assignment keeps
its start date and its history.

**Why this priority**: Two sources for "which School is this Teacher at" would disagree. The constitution
names the contract as the replacement; this story makes the switch safe.

**Independent Test**: With existing placements in the database, open a Teacher and a School before and after
the change and see the same current School, the same start date, and the same history, now labelled as a
contract assignment.

**Acceptance Scenarios**:

1. **Given** Teachers with interim placements, **When** the feature is first used, **Then** each placement
   becomes an assignment with the same School and dates, under the School's contract.
2. **Given** a placed School that has no contract yet, **When** its assignments are carried over, **Then**
   a contract marked "MoU pending" holds them, and the School shows "no MoU yet" until someone records one.
3. **Given** any screen or report that showed a Teacher's interim placement, **When** it is opened, **Then**
   it shows the contract assignment and no longer says "interim".
4. **Given** a Manager, **When** they look at Teachers, **Then** the scope is the same as before: only
   Teachers of their assigned Schools.

---

### User Story 3 - A Monthly Receivable Appears Without Typing (Priority: P1)

For each School and month, the system generates the amount HLS expects to collect from the active contract
and the Teachers assigned in that month. Nobody types the expected amount. The user can open a School's month
and see how the figure was worked out: the rate, the Teachers counted, and the total. If assignments or the
contract change before the month is closed, the receivable can be recalculated, and each earlier figure stays
visible as history.

**Why this priority**: This is the heart of the spec (Constitution Principle V): receivables are generated,
not re-entered.

**Independent Test**: With a School on ₹15,000 per Teacher and 3 Teachers assigned for the whole of a month,
see an expected receivable of ₹45,000 for that month, with a breakdown naming the three Teachers.

**Acceptance Scenarios**:

1. **Given** a same-salary contract and 3 Teachers mapped for a full month, **When** the month's receivable
   is generated, **Then** it equals 3 times the salary and lists the Teachers.
2. **Given** a different-salary contract with 3 positions at ₹15,000, ₹18,000 and ₹20,000 and all filled for a
   full month, **When** the receivable is generated, **Then** it equals ₹53,000, each Teacher's line showing the
   salary of their position. A vacant position adds nothing.
3. **Given** an open month, **When** a Teacher is added or removed, **Then** the receivable can be
   recalculated, the new figure replaces the old as current, and the old figure remains in the history.
4. **Given** a closed month, **When** a user tries to recalculate, **Then** it is refused; a correction
   appears as an adjustment line in the next open month instead.
5. **Given** a Teacher assigned for only part of a month, **When** the receivable is generated, **Then** the
   amount follows the pro-rating rule of FR-008.

---

### User Story 4 - Record Payments and See the Balance (Priority: P2)

A Manager (for their Schools) or an Admin records a payment received from a School for a month: the date, the
mode (Bank, Cash, Cheque or UPI), the receiver, the amount and a comment. Several payments can go against
one month, including part-payments on different dates ("4,500 and then 9,000"). The School's outstanding
balance for the month, and carried forward from earlier months, is worked out by the system. A payment is
never edited or deleted; a mistake is corrected by a reversing entry that stays visible.

**Why this priority**: The receivable has no use until payments can be set against it, but contracts and
receivables can be built and tested first.

**Independent Test**: Against a ₹45,000 receivable, record ₹4,500 on one date and ₹9,000 on another, and see
₹31,500 outstanding; reverse the first payment and see ₹36,000 outstanding with both entries listed.

**Acceptance Scenarios**:

1. **Given** a ₹45,000 receivable, **When** a Manager records ₹4,500 by Cash, **Then** the balance is
   ₹40,500 and the payment shows its date, mode, receiver and comment.
2. **Given** a month with one payment, **When** a second part-payment is recorded, **Then** both are listed
   and the balance reflects both.
3. **Given** a recorded payment, **When** a user looks for Edit or Delete, **Then** neither is offered; the
   user can only reverse it, with a reason, and the reversal is a new entry.
4. **Given** a Manager, **When** they try to record a payment for a School outside their assignment,
   **Then** it is refused.
5. **Given** a payment larger than the outstanding balance, **When** it is recorded, **Then** it is accepted
   as an advance and shown as a credit on the School's next months.

---

### User Story 5 - Expected, Collected and Outstanding at a Glance (Priority: P2)

An Admin or Director opens School Billing and sees, for a chosen month, the total expected, collected and
outstanding, for the whole organization and broken down by Manager and by School. A Manager sees the same for
their own Schools only. Each figure opens the Schools and entries behind it.

**Why this priority**: It turns the data into the answer the Director asked for in the requirements, but it
depends on stories 3 and 4.

**Independent Test**: With three Schools under two Managers and a few payments, check that the organization
totals equal the sum of the Managers, and that each Manager sees only their own Schools.

**Acceptance Scenarios**:

1. **Given** payments across Schools, **When** an Admin opens a month, **Then** the three totals and the
   per-Manager and per-School breakdowns add up to the same organization figure.
2. **Given** a Manager with two Schools, **When** they open School Billing, **Then** they see those two
   Schools and no others, including in totals, search and export.
3. **Given** no data for a month, **When** it is opened, **Then** an empty state explains that nothing is
   billed yet.
4. **Given** the billing figures, **When** payroll or reports ask for a School's payment for a Teacher and
   month, **Then** they get it through the billing public interface, not from billing's tables.

---

### User Story 6 - Overdue Payments Raise an Alert (Priority: P3)

A School is overdue when it still owes money a set number of days after the month's expected payment date.
The number of days is configurable. Overdue Schools are listed on the billing screen, and the School's Manager
and the Directors get an in-app notification through spec 010, once for each School and month, not every day.

**Why this priority**: It is valuable but the figures it needs already exist after stories 3 to 5.

**Independent Test**: Set the threshold to 7 days, leave a School's balance unpaid 8 days after the expected
date, and see the School listed as overdue and its Manager notified once.

**Acceptance Scenarios**:

1. **Given** a threshold of 7 days and a balance above zero 8 days after the expected date, **When** the
   daily check runs, **Then** the School is flagged overdue and its Manager and the Directors are notified.
2. **Given** a School already flagged for that month, **When** the check runs again, **Then** no second
   notification is sent.
3. **Given** a flagged School, **When** the balance is paid in full, **Then** the flag clears.
4. **Given** the threshold is changed, **When** the next check runs, **Then** it uses the new number.

---

### Edge Cases

- A Teacher is assigned for part of a month: the receivable is pro-rated by working days (FR-008), so a
  Teacher assigned for 13 of a month's 26 working days is billed half the rate.
- A contract ends mid-month, or a new contract with a different rate starts mid-month: each part is billed
  at its own contract's terms, and the breakdown shows both.
- A School has Teachers assigned but no contract (or a "MoU pending" contract), or Teachers not yet mapped to a position: nothing is billed for them;
  the School is listed as "no MoU yet" so it cannot be missed.
- A contract has fewer Teachers mapped than positions: the vacant positions are shown as vacant and bill
  nothing. More Teachers than positions is refused until a new contract adds positions.
- A contract's salary is changed for one position: that is a new MoU (a new contract), never an edit.
- The School's signatory changes after signing: the signatories of the signed contract never change; the new
  person appears on the next MoU.
- A Teacher is moved from School A to School B during a month: A is billed up to the move, B from it.
- A payment is recorded against a month that has no receivable yet: it is held as an advance credit, not
  refused.
- A reversal of a payment from a closed month: it is allowed and shows in the current open month.
- A Manager is reassigned: the new Manager sees the School's full billing history; the old Manager no longer
  sees any of it (decided 2026-10-05), and the payments they recorded keep their name as receiver.
- Two users record the same payment at the same moment: both are kept (they are separate entries), but the
  second user sees a warning about a likely duplicate (same School, month, amount, date).
- The month's expected payment date falls on a holiday or Sunday: it is still the date used for overdue.
- A School's Zone Manager changes while a contract is active: nothing on the contract changes; the new Zone
  Manager is responsible from that moment (see the Manager-reassignment case above).
- Amounts are always in rupees with Indian digit grouping; paise are kept, and no rounding hides a difference.

## Requirements *(mandatory)*

### Functional Requirements

**Contracts**

- **FR-001**: The system MUST let an Admin or Director (not a Manager) create a contract (the MoU) for a School
  with: the number of Teachers it covers (at least 1), the salary mode (**same for all Teachers**: one monthly
  amount; or **different for each Teacher**: a monthly amount for each of the N positions), the billing cycle
  (monthly), a start date and an optional end date. Amounts MUST be positive. The contract MUST have exactly N
  positions, each with the salary the School pays for that Teacher.
- **FR-001a**: The contract MUST record its signing details: the date signed (not in the future), at least one
  School signatory (name and designation), and the HLS signatories: the School's Zone Manager and one Director
  (chosen from the active Directors), each with the designation shown. Signing details are part of the signed
  contract and MUST NOT change afterwards; a different signatory means a new contract.
- **FR-002**: A School MUST have at most one active contract on any date. Changing terms MUST end the current
  contract and start a new one; earlier contracts MUST remain readable and unchanged.
- **FR-003**: The Manager responsible for a contract MUST be the School's Zone Manager as recorded in spec 005, read
  when needed and never copied onto the contract. The system MUST refuse to create a contract for a School
  that has no Zone Manager.
- **FR-004**: The system MUST let an Admin, a Director or the School's Zone Manager map a Teacher to a vacant
  position of the School's contract, with a start date and an optional end date (this is how recruited Teachers
  are assigned to a School), and MUST refuse overlapping assignments for the same Teacher and a position that is
  already filled on those dates. A School's contract MUST NOT have more Teachers assigned at once than it has
  positions.
- **FR-004a**: When a new contract replaces the current one, the system MUST let the user map the School's
  current Teachers to the new contract's positions in one step: each Teacher's assignment ends the day before the
  new contract starts and a new one starts under the new position, with no gap in the Teacher's placement.

**Interim placement**

- **FR-005**: The system MUST carry over every existing interim placement of spec 005 as an assignment with
  the same School, start date and history, and MUST show a Teacher's current School from assignments only.
- **FR-006**: For a School that has placements but no contract, the system MUST hold them under a contract
  marked "MoU pending" and MUST generate no receivable for it until the MoU is recorded and the Teachers are mapped to its positions.
- **FR-007**: Every place that labelled a placement "interim" MUST show the contract assignment instead, and
  the Teacher and School scope rules of spec 005 MUST be unchanged.

**Receivables**

- **FR-008**: The system MUST generate each School's expected receivable per month from the active contract
  and the Teachers assigned in that month, with no manual entry of the amount. For a Teacher assigned for
  part of a month, the rate MUST be pro-rated by working days: the rate times the Teacher's working days
  assigned in that month divided by the month's working days, taken from the Teacher's attendance (spec 008), which
  already follows the School's calendar, so billing keeps no calendar of its own. A full month is the full rate.
- **FR-009**: Each Teacher's receivable line MUST use the salary of the position they are mapped to, from the
  contract in effect on each day (a position that is vacant adds nothing). Teachers who are at the School but
  not yet mapped to a position (carried over from the interim placement) add nothing and are listed as
  "not mapped", so they cannot be missed.
- **FR-010**: The system MUST show how each receivable was worked out (rate, Teachers counted, the days or
  basis used, the total).
- **FR-010a**: The system MUST NOT generate receivables for months before the first open month at go-live,
  whatever start date a contract or assignment has; earlier months stay outside the system.
- **FR-011**: Until a month is closed, a receivable MUST be recalculable; each earlier figure MUST be kept as
  history. After the month is closed, corrections MUST appear as adjustment lines in an open month. Only an Admin
  (Approve action) can close a month, and the system MUST refuse to close one whose attendance is not yet
  locked.

**Payments and balance**

- **FR-012**: The system MUST let an authorized user record a payment against a School and month with date,
  mode (Bank, Cash, Cheque or UPI), receiver, amount and a comment, and MUST allow several payments
  against one month.
- **FR-013**: Recorded payments MUST NOT be edited or deleted. A mistake MUST be corrected by a reversing
  entry with a required reason, and both entries MUST remain visible.
- **FR-014**: The system MUST compute the outstanding balance per School and month, and carried forward across
  months, from receivables, adjustments, payments and reversals; no balance is typed.
- **FR-015**: A payment above the balance, or against a month with no receivable yet, MUST be accepted and
  held as an advance credit that reduces later balances.
- **FR-016**: The system MUST warn, not refuse, when a payment looks like a duplicate (same School, month,
  amount and date).

**Views and integration**

- **FR-017**: Admin and Director MUST see expected, collected and outstanding totals for a chosen month,
  organization-wide and per Manager and per School; a Manager MUST see the same for their own Schools only.
- **FR-018**: The billing figures other specs need (a School's payment for a Teacher and month, balances,
  collected totals) MUST be available through the `schoolbilling` public interface, and no other module may
  read billing tables (Constitution Principle VII).
- **FR-019**: Scoping MUST apply to lists, search, totals, detail views and exports, with no leakage between
  Managers or Zones (Constitution Principle III).

**Overdue**

- **FR-020**: The system MUST treat a School as overdue when its balance is above zero more than a
  configurable number of days after the month's expected payment date, and MUST list overdue Schools on the
  billing screen.
- **FR-021**: The system MUST send one in-app notification per overdue School and month, through spec 010, to
  that School's Manager and to the Directors, and MUST clear the flag when the balance is paid in full.

**Access and audit**

- **FR-022**: Every contract change, assignment change, recalculation, payment and reversal MUST be written to
  the audit store of spec 003, with the actor, roles, time, and prior and new values (Constitution
  Principle I).
- **FR-023**: Menu items and actions MUST be offered only when the server's access model grants them, and
  every endpoint MUST be tested per role and per scope boundary.
- **FR-024**: Amounts MUST show in rupees with Indian digit grouping and dates as DD/MM/YYYY; screens MUST
  meet WCAG 2.2 AA, have loading, empty and error states, and work at phone width.

### Key Entities *(include if feature involves data)*

- **Contract (MoU)**: the signed agreement between HLS and one School for a period: School, number of
  Teachers, salary mode (same for all or different for each), cycle, start and end date, the signing details
  (signed date and signatories), and a "MoU pending" state for Schools whose placements were carried over
  before an MoU was recorded. Contracts are never overwritten; a change is a new contract that ends the old one.
- **Contract Position**: one of a contract's N Teacher positions, with the monthly salary the School pays for
  it and the Teacher mapped to it (if any). It is vacant until a Teacher is mapped.
- **Signatory**: a person who signed a contract for one side: School (name, designation) or HLS (the Zone
  Manager, a Director). It is part of the signed contract and never edited.
- **Teacher Assignment**: a dated link from a Teacher to a School and to a position of its contract. It
  replaces the interim Teacher Placement of spec 005.
- **Receivable**: the expected amount for one School and month, with its breakdown. It keeps its earlier
  versions and any adjustment lines.
- **Payment**: money received from a School for a month, with date, mode, receiver, amount and comment. It is
  append-only; a reversal is a separate linked entry with a reason.
- **Balance**: the computed outstanding amount per School and month and carried forward. It is derived, not
  stored as typed input.
- **Billing Settings**: the overdue threshold in days and the expected payment day, kept as configuration.

## Role & Permission Impact *(mandatory — Constitution Principles II–IV)*

| Role     | Menu (section → item) | Default actions | Data scope |
| -------- | --------------------- | --------------- | ---------- |
| Admin    | OPERATIONS → School Billing | View, Create, Edit, Approve (close a month), Export (contracts and rates: Create, Edit) | Org-wide |
| Director | OPERATIONS → School Billing | View, Create, Edit, Export (contracts and rates: Create, Edit) | Org-wide |
| Manager  | OPERATIONS → School Billing | View; Create and Edit for payments, reversals and Teacher assignments; contracts and rates View only | Assigned |
| Teacher  | none | none | None |
| System   | none (System MUST NOT see school or payment data) | none | None |

**New permission keys**: module `SCHOOL_CONTRACTS` with actions `VIEW`, `CREATE`, `EDIT` (contract terms and
rates), and module `SCHOOL_BILLING` with actions `VIEW`, `CREATE`, `EDIT`, `APPROVE`, `EXPORT` (receivables,
payments, Teacher assignments, closing a month). Manager gets `SCHOOL_CONTRACTS` `VIEW` only.
Granted by default as in the table above; not eligible for Teacher or System. Reversing a payment counts as
Create (it adds an entry) and requires a reason. Runtime-editable in Role & Permissions. Only Admin,
Director and System may edit the role→permission matrix; this spec does not change that. The Default role
access matrix in the constitution has no School Billing row yet; this spec adds one (see Assumptions).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: For every School and month in a test set, the expected receivable shown equals the figure
  worked out by hand from the contract and the Teachers assigned, in 100% of cases.
- **SC-002**: A user can record a payment for a School and see the new balance in under 1 minute from
  opening School Billing, without typing any balance.
- **SC-003**: The organization-wide expected, collected and outstanding totals equal the sum of the
  per-Manager totals and the sum of the per-School totals, with no difference even by one paisa.
- **SC-004**: After the switch from interim placements, 100% of Teachers show the same current School and
  start date as before, and no screen still says "interim".
- **SC-005**: A Manager sees none of another Manager's Schools, payments or totals in any list, search,
  total or export; this is proved by a test for each endpoint.
- **SC-006**: No recorded payment can be changed or removed by any user; every correction appears as a new
  linked entry, and every contract, assignment, recalculation, payment and reversal has an audit entry.
- **SC-007**: A School that stays unpaid beyond the threshold is flagged and its Manager notified by the next
  daily check, exactly once per month of arrears.
- **SC-008**: Spec 013 can read any School's payment for a Teacher and month through the billing public
  interface without a change to billing's tables, and the module-boundary checks pass.

## Assumptions

- Part-month billing is pro-rated by working days (decided 2026-10-05); attendance and leave do not change
  the receivable.
- The contract and the MoU are the same thing; the screens say "Contract (MoU)".
- Billing is for mapped positions only: a vacant position is not billed. Whether a School owes for the positions
  it has not yet filled is not decided here.
- Scanning or uploading the signed MoU document is out of scope; only the signing details are recorded.
- "Manager" means the Zone Manager, the term used across this system (spec 005): each School belongs to a
  Zone and has one of that Zone's Managers assigned.
- Users are Admin, Director and Manager; Teachers and System have no access. Managers act only inside their
  assigned Zones and Schools (spec 005 scope queries).
- Billing is monthly only. Other cycles are out of scope.
- A School has one active contract at a time and each Teacher has one current School at a time.
- The expected payment date is a day of the month held in Billing Settings (default the 5th of the next
  month) rather than per contract; a per-contract date can follow if the business needs it.
- The overdue threshold is one organization-wide setting (default 7 days after the expected date). Moving
  it into the System settings screen of spec 011 is a later change.
- Carried-forward balances are shown as a running total per School in addition to the month figure.
- Existing interim placements are carried over without a rate; Admin enters the rate afterwards.
- The Manager who recorded a payment is its receiver by default and can name another person.
- Spec 012 adds no mobile screens, invoices, tax, payment gateways or bank reconciliation, and no
  margin or payroll (spec 013).
- It depends on spec 005 (Schools, Managers, Teachers, scope queries and the interim placement), spec 003
  (audit) and spec 010 (notifications for overdue alerts).
- The constitution's Default role access matrix gets a School Billing row in the plan stage; no
  Constitution principle changes.
