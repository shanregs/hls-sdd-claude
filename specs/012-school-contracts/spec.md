# Feature Specification: School Contracts (MoU)

**Feature Branch**: `012-school-contracts`

**Created**: 2026-10-05

**Status**: Draft

**Input**: User description: "012-school-contracts: The contract between HLS and a partner School, which is the MoU. It records the School, the number of Teachers, start and end date, whether the salary the School pays is the same for all Teachers or different for each, and the signing details (date signed, School signatories, HLS Zone Manager and Director). Recruited Teachers are mapped to the contract's positions afterwards, replacing the interim Teacher–School placement of spec 005. Contracts are maintained here before any billing: the month-end billing of spec 022 starts from the contract. Module `schoolbilling`. Full source: docs/spec-inputs/012-school-contracts.md."

## Clarifications

### Session 2026-10-05

- Q: What does the contract cover? → A: The contract is the MoU between HLS and the School: the School, the number
  of Teachers, start and end date, whether every Teacher has the same salary or each has a different one, and the
  signing details (the date signed, the School's signatories, and HLS's signatories: the Zone Manager and/or a
  Director). A lump sum is
  not a basis. Recruited Teachers are mapped to the contract's Teacher positions afterwards.
- Q: Can a Manager create or change a contract's salary and terms? → A: No. Only Admin and Director create or edit
  contracts. The Zone Manager can view the contracts of their Schools and map Teachers to them.
- Q: Who is the responsible Manager? → A: The School's Zone Manager (spec 005), read from there and never stored on
  the contract. "Manager" in this system means the Zone Manager.
- Q: What is the order of the business process? → A: MoU contract, then Teacher mapping, then attendance capture
  (spec 008), then month-end billing and salary computation. This spec covers the first two steps and makes
  attendance use the mapping. Billing the School (spec 022) starts from the contract's start month and only for a
  signed MoU; the Teacher's pay (spec 013) comes after.
- Q: When the first MoU is recorded on a School whose carried-over Teachers have no position, how are they put into
  positions? → A: One by one, with the same mapping action as User Story 3. The one-step re-map (FR-007) applies only
  when a new MoU replaces an earlier one that had positions.
- Q: If a new MoU has fewer positions than the Teachers currently placed, what happens to the extra Teachers? → A:
  The new MoU is accepted. Teachers who get no position stay placed and show as "not mapped to the current MoU"
  (FR-007a) until an Admin or Director moves or exits them; nothing is ended automatically.
- Q: Can a Teacher still in training be mapped to a position? → A: No (decided 2026-10-05 from the business-flow doc,
  rule 9: training must be completed before School assignment). Mapping requires the Teacher to be active or on
  leave; a Teacher in training or exited is refused. Induction sign-off (spec 016) is what makes a recruit active.
  This replaces an earlier answer that allowed any non-exited Teacher. Carried-over placements of Teachers already
  in training are kept as they are (they keep working), but no new mapping of a trainee is accepted.
- Q: Can a Teacher who has finished training wait without a School? → A: Yes (decided 2026-10-05). After induction
  sign-off (spec 016) a Teacher is active and "ready to deploy", and may stay unplaced for any length of time; being
  unplaced is a normal state, not an error. Such a Teacher receives **no salary until mapped to a School**: salary
  starts on the first assignment (the School reporting date, D2), and training is unpaid. Nothing in this spec pays or
  blocks a waiting Teacher; it only records the assignment whose first start date payroll (spec 013) uses.
- Q: Can a contract's end date be removed again? → A: Yes. An Admin or Director can set, move or clear the end date
  at any time while no later MoU exists for the School; each change is audited with the prior and new value.
- Q: Where does the MoU come from, and where do the Teachers come from? → A: HLS runs two parallel activities. School
  marketing (spec 023: visits, proposals, negotiation by the Director, Zone Managers and the marketing team) wins the
  School and hands over to this spec to record the signed MoU. Campus recruitment (spec 016: drives, offers with a
  package, one-month induction) produces the Teachers who are then mapped here. This spec stays the only record of
  the signed MoU and of the mapping; it does not hold prospects, visits, candidates, offers or training. Individual
  salary for each Teacher and the same salary for all Teachers are both supported (FR-001).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Record a School's MoU Contract (Priority: P1) 🎯 MVP

An Admin or Director opens a School and records its contract, the MoU between HLS and the School: the number of
Teachers it covers, a start date and an optional end date, whether the salary the School pays is the **same for
every Teacher** (one amount) or **different for each** (an amount for each Teacher position), and the **signing
details**: the date the MoU was signed, who signed for the School (name and designation), and who signed for HLS
(the School's Zone Manager, a Director, or both). The contract has one position for each Teacher it covers. When the
terms change, a new MoU is recorded and the old one ends, so the earlier terms and signatures stay visible.

**Why this priority**: Everything after it, mapping Teachers, attendance, billing and salary, reads the contract.
Without a signed contract there is nothing to build on.

**Independent Test**: Create a contract for a School for 3 Teachers at different salaries with its signing
details, then record a new MoU from a later date and see both contracts, with their positions and signatories, in
the School's history and only the new one active.

**Acceptance Scenarios**:

1. **Given** a School with no contract, **When** an Admin creates a contract for 4 Teachers at one salary for all,
   with a start date and the signing details, **Then** the contract is active from that date, has 4 positions at
   that salary, and is shown on the School with its signatories.
2. **Given** the same form, **When** the salary is set to "different for each Teacher", **Then** an amount is
   asked for each of the N positions, and the contract cannot be saved until every position has an amount.
3. **Given** the form, **When** the signed date, a School signatory (name and designation), or any HLS
   signatory (the Zone Manager and/or a Director) is missing, or the signed date is in the future, **Then** the contract is not
   saved and the missing item is named.
4. **Given** a School with an active contract, **When** the user records a new MoU from a later date, **Then**
   the old contract ends the day before, the new one is active, and both appear in the history unchanged.
5. **Given** a School with no Zone Manager, **When** a contract is created, **Then** it is refused and the user is
   told to assign a Zone Manager first (spec 005).
6. **Given** a Zone Manager, **When** they open the School's contracts, **Then** they can see them but not create
   or change one.

---

### User Story 2 - Replace the Interim Placement (Priority: P1)

The interim Teacher–School placement from spec 005 stops being a separate thing. Existing placements are carried
over as assignments, the screens that showed "interim" now show the contract assignment, and a Teacher still has
exactly one current School at a time. Nothing is lost: each carried-over assignment keeps its start date and its
history, and attendance and leave work exactly as before.

**Why this priority**: Two sources for "which School is this Teacher at" would disagree. The constitution names the
contract as the replacement; this story makes the switch safe before anything is built on it.

**Independent Test**: With existing placements in the database, open a Teacher and a School before and after the
change and see the same current School, the same start date and the same history, now labelled as a contract
assignment; open My Attendance and Leave and see no difference.

**Acceptance Scenarios**:

1. **Given** Teachers with interim placements (current, scheduled, ended, cancelled), **When** the feature is first
   used, **Then** each becomes an assignment with the same School, dates and status.
2. **Given** a placed School that has no contract yet, **When** its assignments are carried over, **Then** a
   contract marked "MoU pending" holds them, and the School shows "no MoU yet" until someone records one.
3. **Given** any screen or report that showed a Teacher's interim placement, **When** it is opened, **Then** it
   shows the contract assignment and no longer says "interim".
4. **Given** a Zone Manager, **When** they look at Teachers, **Then** the scope is the same as before: only
   Teachers of their assigned Schools.
5. **Given** the attendance grid, leave preview and Manager scope queries, **When** they ask which School a Teacher
   was at on a date, **Then** the answer is the same as before the change.

---

### User Story 3 - Map Recruited Teachers to the Contract (Priority: P1)

When Teachers are recruited for a School, an Admin, a Director or the School's Zone Manager maps each Teacher to
a vacant position of the School's contract, from a start date (and optionally an end date). For a contract with
different salaries the position, and so the salary the School pays for that Teacher, is chosen. A School whose
contract has no vacant position cannot take another Teacher until a new MoU adds positions. When a new MoU replaces
the old one, the School's current Teachers are mapped to the new contract's positions in one step, and no Teacher's
placement has a gap.

**Why this priority**: The mapping is what ties a Teacher to the salary the School pays, which attendance, billing
and payroll all use later.

**Independent Test**: Map two Teachers to two positions of a 3-position contract and see one position vacant; try
a third and a fourth Teacher and see the fourth refused; record a new MoU and re-map the three Teachers to its
positions and see their assignments continue without a gap.

**Acceptance Scenarios**:

1. **Given** an active contract with a vacant position, **When** the Zone Manager maps a Teacher from a start date,
   **Then** the Teacher's current School is that School and the position shows as filled.
2. **Given** a "different for each" contract, **When** a Teacher is mapped, **Then** a position must be chosen and
   its salary is shown; for a "same for all" contract the next vacant position is used.
3. **Given** a contract with every position filled, **When** another Teacher is mapped, **Then** it is refused
   with "All N positions are filled; record a new contract to add Teachers."
3a. **Given** a Teacher whose status is in training, **When** a user tries to map them, **Then** it is refused with "This Teacher is still in training; mapping is possible after induction is signed off"; an active or on-leave Teacher is accepted.
3b. **Given** a Teacher who has finished training and is active but not yet placed, **When** nobody has mapped them
   to a position, **Then** they stay valid with no School and no error, they appear as available to be mapped, and
   they have no assignment, so no salary starts (spec 013 pays only from the first assignment).
4. **Given** a Teacher already mapped at another School on overlapping dates, **When** they are mapped, **Then**
   it is refused; a move to another School on a later date is accepted as a scheduled move.
5. **Given** a School with no contract covering the start date, **When** a Teacher is mapped to it, **Then** a
   "MoU pending" contract is created for the School from that date and the Teacher is assigned without a position
   (the School shows "no MoU yet"); once the School has an active MoU, a vacant position is required.
6. **Given** a new contract replacing the old one, **When** the user maps the School's Teachers to its positions,
   **Then** each Teacher's old assignment ends the day before and a new one starts under the new position, with no
   gap, and the whole step succeeds or none of it does.
7. **Given** a Zone Manager, **When** they try to map a Teacher to a School outside their scope, **Then** it is
   refused (not found).

---

### User Story 4 - See and Maintain the Contracts (Priority: P2)

An Admin or Director opens School Contracts and sees every School with its contract status: the active contract,
its dates, how many positions are filled and how many are vacant, Schools whose contract is "MoU pending" or that
have none, and Schools whose contract ends soon. They can open a School to see the contract history with
positions, signatories and the Teachers mapped, and end a contract by setting an end date. A Zone Manager sees
the same for their own Schools.

**Why this priority**: Contracts must be maintained, not just entered; this is how gaps are noticed before billing
starts. It builds on the first three stories.

**Independent Test**: With three Schools (one with a full contract, one with vacancies, one "MoU pending"), see
each state in the list, filter by status, and see a Zone Manager's list limited to their Schools.

**Acceptance Scenarios**:

1. **Given** Schools in different states, **When** the list is opened, **Then** each shows its contract status,
   filled and vacant positions and end date, and the list can be filtered by status and by Zone Manager.
2. **Given** an active contract, **When** an Admin sets an end date, **Then** the contract ends then, its
   positions' assignments are not cut short (Teachers stay until their own end), and the School shows "ends on".
3. **Given** a contract ending within 30 days, **When** the list is opened, **Then** it is marked "ends soon".
4. **Given** a Zone Manager, **When** they open the list, **Then** they see only their Schools, in totals and
   search too.
5. **Given** no data, **When** the list is opened, **Then** an empty state explains that no contracts exist yet.

---

### Edge Cases

- A Teacher who has finished training waits for a School: active, no assignment, no salary. The wait may be long; it
  ends when a Zone Manager, Admin or Director maps the Teacher, and salary starts from that assignment's start date.
- A contract has fewer Teachers mapped than positions: the vacant positions show as vacant, nothing else changes.
- More Teachers than positions: refused until a new contract adds positions.
- A new MoU with fewer positions than the Teachers placed: it is accepted; the re-map covers as many Teachers as there
  are positions, and the rest stay placed as "not mapped to the current MoU" and are counted as unmapped.
- A contract's salary is wrong for one position: it is a new MoU (a new contract), never an edit.
- The School's signatory changes after signing: the signatories of the signed contract never change; the new
  person appears on the next MoU.
- A School's Zone Manager changes while a contract is active: nothing on the contract changes; the new Zone Manager
  is responsible from that moment. The HLS signatory stays as signed.
- A new contract starts mid-month: the old one ends the day before; Teachers are re-mapped from the start date.
- A Teacher exits: their assignment ends on the exit date and the position becomes vacant from the next day.
- Carried-over placements have no position yet: they stay as "not mapped" until someone maps them under a signed
  MoU; they keep working for attendance and leave in the meantime. Mapping them is one Teacher at a time.
- Two users map different Teachers to the same position at the same moment: the second is refused (a position
  holds one Teacher on any date).
- A School is deactivated (spec 005): it cannot be deactivated while it has current or future assignments, as
  before.
- Amounts are rupees with Indian digit grouping and two decimals; dates DD/MM/YYYY.

## Requirements *(mandatory)*

### Functional Requirements

**The MoU contract**

- **FR-001**: The system MUST let an Admin or Director (not a Zone Manager) create a contract (the MoU) for a
  School with: the number of Teachers it covers (1 to 500), the salary mode (**same for all Teachers**: one
  monthly amount; or **different for each Teacher**: a monthly amount for each of the N positions), the billing
  cycle (monthly), a start date and an optional end date. Amounts MUST be positive. The contract MUST have exactly
  N positions, each with the salary the School pays for that Teacher.
- **FR-002**: The contract MUST record its signing details: the date signed (not in the future), at least one
  School signatory (name and designation), and at least one HLS signatory, each being either the School's Zone
  Manager or an active Director (the Zone Manager, a Director, or both; more than one Director is allowed), with
  the designation shown. Signing details are part of the signed
  contract and MUST NOT change afterwards; a different signatory means a new contract.
- **FR-003**: A School MUST have at most one active contract on any date. Recording a new MoU MUST end the current
  contract the day before it starts; earlier contracts, their positions and signatories MUST remain readable and
  unchanged. A mistaken contract that no Teacher is mapped to MAY be cancelled.
- **FR-004**: The Manager responsible for a contract MUST be the School's Zone Manager as recorded in spec 005,
  read when needed and never stored as a contract field. The system MUST refuse to create a contract for a School
  that has no Zone Manager.

**Mapping Teachers**

- **FR-005**: The system MUST let an Admin, a Director or the School's Zone Manager map a Teacher to a vacant
  position of the School's contract, with a start date and an optional end date, and MUST refuse overlapping
  assignments for the same Teacher, a position that is already filled on those dates, an exited Teacher, and a Teacher still in training. When
  the School has no contract covering the start date, the system MUST create a "MoU pending" contract from that
  date and assign the Teacher without a position; once an active MoU covers the date a vacant position MUST be used. A School's contract MUST NOT have more Teachers mapped at once than it has positions.
- **FR-006**: For a "different for each" contract the position MUST be chosen when mapping; for a "same for all"
  contract the next vacant position MUST be used when none is named.
- **FR-007**: When a new contract replaces the current one, the system MUST let the user map the School's current
  Teachers to the new contract's positions in one step: each Teacher's assignment ends the day before the new
  contract starts and a new one starts under the new position, with no gap in the Teacher's placement. The step
  MUST be all or nothing.
- **FR-007a**: A Teacher still assigned to a position of a contract that has ended or been replaced MUST stay
  placed in the School (attendance and leave keep working) and MUST be shown as "not mapped to the current MoU"
  until mapped to a position of the contract in effect; such Teachers MUST be counted as unmapped in the contracts
  list and in `unmappedTeachers`.
- **FR-008**: A Teacher MUST have at most one current School; a scheduled move to another School on a later date
  MUST be supported and MUST NOT disturb the current assignment until it takes effect.

**Replacing the interim placement**

- **FR-009**: The system MUST carry over every existing interim placement of spec 005 as an assignment with the
  same School, start date, end date, status and identifier, and MUST show a Teacher's current School from
  assignments only.
- **FR-010**: For a School that has placements but no contract, the system MUST hold them under a contract marked
  "MoU pending" (no salary, count or signing details) until an Admin or Director records the MoU on it; such a
  School MUST be listed as "no MoU yet". Once the MoU is recorded, each carried-over Teacher MUST be mapped to a
  position individually (FR-005, FR-006); there is no bulk map for them.
- **FR-011**: Every place that labelled a placement "interim" MUST show the contract assignment instead, and the
  Teacher, School, attendance and leave behaviour of specs 005, 008 and 009 MUST be unchanged.

**Maintaining contracts**

- **FR-012**: The system MUST list every School with its contract status (active, ends soon, MoU pending, none),
  filled and vacant positions and dates, filterable by status and Zone Manager ("ends soon" means an end date within 30 days), and MUST show a School's contract
  history with positions, signatories and mapped Teachers.
- **FR-013**: An Admin or Director MUST be able to set, move or clear a contract's end date (never before its
  start, and not once a later MoU exists for the School); setting one ends the contract then. Teachers' assignments are
  not shortened by it. Clearing the end date makes the contract open-ended again.

**Scope, access and audit**

- **FR-014**: Every list, search and detail MUST be filtered by the caller's scope (a Zone Manager sees only their
  Schools), with no leakage between Zone Managers (Constitution Principle III).
- **FR-015**: Every contract creation, cancellation, end, mapping, re-mapping and move MUST be written to the audit
  store of spec 003 with the actor, roles, time, and prior and new values (Constitution Principle I).
- **FR-016**: Menu items and actions MUST be offered only when the server's access model grants them, and every
  endpoint MUST be tested per role and per scope boundary.
- **FR-017**: The system MUST expose, through the `schoolbilling` public interface, the contract in effect for a
  School on a date, its positions with their salaries, and the Teacher mapped to each position on a date, so that
  billing (spec 022) and payroll (spec 013) read contracts without reading its tables. The same interface MUST let
  the marketing pipeline (spec 023) see, for a School, whether a contract exists, its dates, positions and how many
  are filled, so a won School can be shown as still needing Teachers.
- **FR-018**: Amounts MUST show in rupees with Indian digit grouping and dates as DD/MM/YYYY; screens MUST meet
  WCAG 2.2 AA, have loading, empty and error states, and work at phone width.

### Key Entities *(include if feature involves data)*

- **Contract (MoU)**: the signed agreement between HLS and one School for a period: School, number of Teachers,
  salary mode (same for all or different for each), cycle, start and end date, the signing details (date signed
  and signatories), and a "MoU pending" state for Schools whose placements were carried over before an MoU was
  recorded. Contracts are never overwritten; a change is a new contract that ends the old one.
- **Contract Position**: one of a contract's N Teacher positions, with the monthly salary the School pays for it
  and the Teacher mapped to it, if any. It is vacant until a Teacher is mapped.
- **Signatory**: a person who signed a contract for one side: School (name, designation) or HLS (the Zone Manager,
  a Director). Part of the signed contract; never edited.
- **Teacher Assignment**: a dated link from a Teacher to a School and to a position of its contract. It replaces
  the interim Teacher Placement of spec 005 and keeps its history.

## Role & Permission Impact *(mandatory — Constitution Principles II–IV)*

| Role     | Menu (section → item) | Default actions | Data scope |
| -------- | --------------------- | --------------- | ---------- |
| Admin    | OPERATIONS → School Contracts | View, Create, Edit (contracts); map Teachers | Org-wide |
| Director | OPERATIONS → School Contracts | View, Create, Edit (contracts); map Teachers | Org-wide |
| Manager (Zone Manager) | OPERATIONS → School Contracts | View contracts; map Teachers | Assigned |
| Teacher  | none | none | None |
| System   | none (System MUST NOT see school or teacher business data) | none | None |

**New permission keys**: module `SCHOOL_CONTRACTS` with actions `VIEW`, `CREATE`, `EDIT` (the MoU, its end date and
cancellation). Seeded as in the table; the Zone Manager gets `VIEW` only; not eligible for Teacher or System.
Mapping a Teacher reuses the existing `TEACHERS` `EDIT` permission and its scope, as the interim placement did.
Runtime-editable in Role & Permissions. Only Admin, Director and System may edit the role→permission matrix; this
spec does not change that. The constitution's Default role access matrix has no School Contracts row yet; a row is
added when this spec merges (see Assumptions).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: After the switch from interim placements, 100% of Teachers show the same current School, start date
  and history as before, and attendance and leave results for a test month are identical.
- **SC-002**: No screen, report or API response still says "interim" for a Teacher's School.
- **SC-003**: A contract cannot be saved without its signed date, a School signatory, and an HLS signatory (the Zone Manager
  and/or a Director); the signing details of a saved contract cannot be changed through any screen or API.
- **SC-004**: A contract never has more Teachers mapped at once than positions, and a position never holds two
  Teachers on one date, including under two simultaneous requests.
- **SC-005**: A Zone Manager sees none of another Zone Manager's Schools or contracts in any list, search or
  detail; this is proved by a test for each endpoint.
- **SC-006**: Every contract creation, end, cancellation and Teacher mapping or move has an audit entry with actor
  and prior and new values.
- **SC-007**: An Admin can record a 4-Teacher MoU with signing details in under 3 minutes, and a Zone Manager can
  map a recruited Teacher to a vacant position in under 1 minute.
- **SC-008**: Spec 022 can read the contract in effect, its position salaries and the Teacher mapped to each
  position through the public interface without reading contract tables, and the module-boundary checks pass.

## Assumptions

- "Manager" means the Zone Manager, the term used across this system (spec 005): each School belongs to a Zone and
  has one of that Zone's Managers assigned.
- The contract and the MoU are the same thing; the screens say "Contract (MoU)".
- Users are Admin, Director and Zone Manager; Teachers and System have no access.
- A School has one active contract at a time and each Teacher has one current School at a time.
- Billing the School (spec 022) starts from the contract's start month and only for a signed MoU; this spec records
  and maintains the contracts and the mapping, and bills nothing. Attendance (spec 008) keeps working through the
  mapping, and the Teacher's pay (spec 013) reads the contract's salary only for margin.
- The salary in the contract is what the School pays HLS for the Teacher; it is separate from the salary HLS
  offers the Teacher (spec 005 salary history).
- Existing placements are carried over without positions; an Admin or Director records the MoU on the "MoU
  pending" contract, then the Teachers are mapped to its positions.
- Scanning or uploading the signed MoU document is out of scope; only the signing details are recorded.
- Mobile screens and billing cycles other than monthly are out of scope. So are the two upstream workflows, which are
  their own specs: school marketing and the MoU pipeline (spec 023, which hands the won School to this spec, with
  the proposed terms pre-filled by it) and campus recruitment, offers and induction (spec 016, which creates the
  Teachers who are then mapped here).
- Teachers that can be mapped are those of spec 005 whose status is active or on leave. A Teacher in training or
  exited is refused. Spec 016's induction sign-off makes a recruit active, so "ready to deploy" means active with no
  current School assignment. A ready Teacher may wait unplaced for any length of time and is not paid until the first
  assignment; the earliest assignment start date is the reporting date that payroll (spec 013) reads through the
  existing placement queries, so this spec adds no salary rule and no new interface for it.
- An MoU recorded here may have been prepared in spec 023. The contract carries no link to the pipeline; spec 023
  keeps the link and reads the contract (existence, positions and filled count) through the public interface in FR-017.
  Only Admin and Director record the MoU, as in FR-001, whoever negotiated it.
- It depends on spec 005 (Schools, Zone Managers, Teachers, scope queries and the interim placement) and spec 003
  (audit); it changes how spec 008 and 009 find a Teacher's School but not what they do.
- The constitution's Default role access matrix gets a School Contracts row in the plan stage; no Constitution
  principle changes.
