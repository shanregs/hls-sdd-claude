# Feature Specification: Organization — Manager/School/Teacher Accountability

**Feature Branch**: `003-organization-scoping`

**Created**: 2026-09-21

**Status**: Draft

**Input**: User description: "Build the module that records which Manager/Area Coordinator is responsible for which schools and teachers. The Director assigns managers to one or more schools; each teacher and school has exactly one accountable manager at a time, though the assignment can change over time and history must be preserved. This assignment is the source of truth every other module uses to enforce \"a Manager sees and acts only on their own assigned teachers/schools\" (Requirements §2, Constitution Principle II) — it does not itself perform any business transaction, it only answers \"who owns this teacher/school right now, and who owned it as of a given date.\""

## Clarifications

### Session 2026-09-21

- Q: Who should be allowed to create or change a Manager's School/Teacher assignment? → A: Director and Admin — assignment is master-data maintenance, which Constitution Principle II explicitly scopes the Admin role to (distinct from payroll approval authority).
- Q: When a School's accountable Manager changes, should its already-assigned Teachers automatically move to the new Manager too? → A: No — School-level and Teacher-level accountability stay fully independent tracks; each must be reassigned separately, matching the source spreadsheet's independent "Handled By" changes.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Assign a Manager to Schools and Teachers (Priority: P1)

The Director or Admin assigns a Manager to be accountable for one or more Schools and one or more Teachers, so that every other part of the system (Attendance, SchoolBilling, Payroll, Substitution, Reporting) can correctly restrict that Manager to only their own portfolio.

**Why this priority**: Without this, no other module can enforce Constitution Principle II ("managers operate only within assigned schools and teachers") — this is the foundational scoping data every downstream module reads.

**Independent Test**: Can be fully tested by having the Director or Admin assign a Manager to a School and a Teacher, then querying "who is accountable for this School/Teacher right now" and getting the assigned Manager back — no other module needs to exist yet.

**Acceptance Scenarios**:

1. **Given** a School with no current accountable Manager, **When** the Director or Admin assigns a Manager to it, **Then** querying the School's current accountable Manager returns that Manager immediately.
2. **Given** a Teacher with no current accountable Manager, **When** the Director or Admin assigns a Manager to it, **Then** querying the Teacher's current accountable Manager returns that Manager immediately.
3. **Given** a Manager already accountable for a School, **When** the Director or Admin assigns that same Manager to the same School again, **Then** the system treats it as a no-op and does not create a duplicate assignment record.

---

### User Story 2 - Reassign Accountability Without Losing History (Priority: P1)

The Director or Admin reassigns a School or Teacher from one Manager to another — because a Manager left, a workload rebalance happened, or a teacher moved between areas — and the system preserves a complete, queryable record of who was accountable before, when the change happened, and who made it.

**Why this priority**: The source spreadsheet already shows "Handled By" changes mid-month; scoping decisions, payroll accountability, and audit questions ("who approved this collection in July?") all depend on being able to answer accountability as of a past date, not just today.

**Independent Test**: Can be fully tested by assigning Manager A to a School, then reassigning it to Manager B, then verifying that a query for "today" returns Manager B while a query for a date before the reassignment returns Manager A.

**Acceptance Scenarios**:

1. **Given** Manager A is currently accountable for a School, **When** the Director or Admin reassigns that School to Manager B, **Then** the current-accountability query returns Manager B, and a query for any date before the reassignment still returns Manager A.
2. **Given** a School's accountability has been reassigned three times, **When** the Director or Admin requests its full assignment history, **Then** all four periods (three past, one current) are returned in order with no gaps and no overlaps.
3. **Given** any reassignment, **When** it is recorded, **Then** the system captures who performed the reassignment and when, independent of the effective date of the change itself.
4. **Given** a Teacher is individually accountable to Manager A and currently assigned to a School that is accountable to Manager B, **When** the Director or Admin reassigns that School's accountable Manager to Manager C, **Then** the Teacher's own accountable Manager remains Manager A, unchanged.

---

### User Story 3 - See a Manager's Current Portfolio and Catch Unassigned Schools/Teachers (Priority: P2)

The Director or Admin views a Manager's full current portfolio (every School and Teacher currently assigned to them), and separately sees a list of any School or Teacher that currently has no accountable Manager at all, so gaps in coverage are caught rather than silently causing scoping failures downstream.

**Why this priority**: An unassigned School or Teacher is a real operational risk (nobody can act on it under Principle II's scoping), but it's a secondary workflow to the assignment/reassignment mechanics themselves.

**Independent Test**: Can be fully tested by assigning a subset of Schools/Teachers to Managers, leaving at least one of each unassigned, and verifying the "unassigned" list surfaces exactly those.

**Acceptance Scenarios**:

1. **Given** a Manager with several assigned Schools and Teachers, **When** the Director requests that Manager's current portfolio, **Then** the system returns exactly the Schools and Teachers currently accountable to that Manager, and none they were previously but are no longer accountable for.
2. **Given** a School has never been assigned to any Manager, **When** the Director or Admin views the unassigned-items list, **Then** that School appears on it.
3. **Given** a School's only accountable Manager assignment is ended without a replacement being assigned, **When** the unassigned-items list is next viewed, **Then** that School appears on it.

### Edge Cases

- What happens when the Director or Admin tries to end a School's or Teacher's current assignment without immediately assigning a replacement? The item must become "unassigned" and appear on the exception list — it must not silently keep pointing at the ended assignment, and it must not block the removal from happening.
- What happens when two Directors/Admins attempt to reassign the same School's accountable Manager at (or near) the same time? The system must detect the conflict and reject the second write with a clear "this was just changed, refresh and retry" outcome rather than silently letting one overwrite the other.
- How does the system handle a request to query accountability for a School/Teacher identifier that doesn't exist yet (since Teacher Master Data and School Master Data are built after this module)? The query must return a clear "unknown" result rather than a false "unassigned" — the two must be distinguishable once those identifiers do exist.
- What happens when a Manager who still has active assignments is deactivated in Identity? This module does not perform the deactivation itself, but it must be possible to list every School/Teacher still pointing at a deactivated Manager, so the Director can be prompted to reassign them.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST allow a Director or an Admin to assign a Manager as the accountable Manager for one or more Schools.
- **FR-002**: System MUST allow a Director or an Admin to assign a Manager as the accountable Manager for one or more Teachers, independently of that Teacher's current School assignment.
- **FR-003**: System MUST ensure a School has at most one currently-active accountable Manager at any point in time; the same rule applies independently per Teacher.
- **FR-004**: System MUST allow a Director or an Admin to reassign a School's or a Teacher's accountable Manager, which ends the current assignment's effective period and begins a new one, without deleting or overwriting the ended assignment's record.
- **FR-005**: System MUST preserve the complete assignment history per School and per Teacher, such that "who was the accountable Manager as of date D" can be answered correctly for any past date, not only for the current date.
- **FR-006**: System MUST record, for every assignment and reassignment, who performed the action and when it was performed, in addition to the effective date of the change itself.
- **FR-007**: System MUST expose a query for "the current accountable Manager for a given School" and a separate query for "the current accountable Manager for a given Teacher," for use by every other module's scoping checks.
- **FR-008**: System MUST expose a query for "every School and Teacher currently accountable to a given Manager" (that Manager's current portfolio).
- **FR-009**: System MUST allow a Director or Admin to list every School and every Teacher that currently has no accountable Manager assigned.
- **FR-010**: System MUST treat "assigning the same Manager who is already the current accountable Manager" as a no-op that creates no new history entry.
- **FR-011**: System MUST detect and reject a reassignment attempt that conflicts with another reassignment already applied to the same School or Teacher since the requester last read its current state, rather than silently allowing one to overwrite the other.
- **FR-012**: System MUST distinguish, in its query results, between "this School/Teacher identifier is not known to the system" and "this School/Teacher identifier is known but currently unassigned."
- **FR-013**: System MUST keep School-level and Teacher-level accountability fully independent: reassigning a School's accountable Manager MUST NOT automatically change the accountable Manager of any Teacher currently assigned to that School, and vice versa.

### Key Entities

- **SchoolAssignment**: Links a School (by identifier) to an accountable Manager for a bounded time period — effective-from date, effective-to date (open-ended for the current assignment), who made the assignment, and when. Multiple SchoolAssignment records for the same School represent its full accountability history; at most one per School has no effective-to date at any given time.
- **TeacherAssignment**: The same relationship and history structure as SchoolAssignment, but linking a Teacher (by identifier) to an accountable Manager instead of a School. Tracked independently of the Teacher's current School, since accountability can change ("Handled By") without a school move.
- **Manager**: Referenced by this module only as an existing identity (owned by the Identity & Access module) — this module does not create, edit, or deactivate Manager accounts, only records what they are accountable for and when.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A Director can assign or reassign a School's or Teacher's accountable Manager in under 2 minutes, and the change is reflected in every "current accountable Manager" query immediately afterward — no delay, no re-login, no cache staleness.
- **SC-002**: For 100% of Schools and Teachers at any point in time, the system returns an unambiguous accountability answer — either a specific current Manager or an explicit "unassigned" state — with no identifier ever silently resolving to neither.
- **SC-003**: For 100% of recorded reassignments exercised in acceptance testing, a query for any date within a past assignment's effective period returns the Manager who was accountable during that period, not the current one.
- **SC-004**: Zero cases, across audit review of the assignment history, of two different Managers simultaneously holding an open-ended (current) assignment for the same School or the same Teacher.
- **SC-005**: 100% of Schools/Teachers that become unassigned (whether by an ended assignment with no replacement, or by never having been assigned) are visible on the Director/Admin's unassigned-items list within the same session the change occurs.

## Assumptions

- Manager and Director identities and roles are owned by the Identity & Access module (spec 002); this module references a Manager only by their existing user identity and never creates, edits, or deactivates a Manager's account.
- Teacher Master Data and School Master Data (specs to follow this one, per the tracker's dependency order) do not exist as implemented modules yet. This module defines the assignment relationship against Teacher/School identifiers that will resolve once those modules exist, and is expected to be exercised with representative test data in the meantime — the same forward-reference pattern already used in the Identity & Access spec (002) for Manager scoping.
- Each School and each Teacher has exactly one currently-active accountable Manager, or none ("unassigned") — never more than one active at a time. This single-ownership model matches the Requirements' description of each Manager managing "a group of teachers/schools" and Constitution Principle II's emphasis on unambiguous ownership to prevent cross-manager leakage. (See Clarifications for the resolved School/Teacher independence rule, now FR-013.)
- Assignments and reassignments take effect immediately upon being recorded; scheduling a Manager change for a future date is out of scope for v1.
- This module performs no business transactions of its own (no attendance, payment, or payroll data) — it is purely the accountability record other modules read to enforce their own scoping rules.
