# Feature Specification: Leave Management

**Feature Branch**: `009-leave`

**Created**: 2026-10-04

**Status**: Draft

**Input**: User description: "009-leave: Leave management. Teacher: LEAVE menu with Apply Leave and My Leave History (own requests only; cancel a pending request). Manager (assigned teachers), Admin and Director: OPERATIONS → Leave Management to list, approve or reject requests in scope, with a reason on rejection. Leave types and half-day support, date-range validation (no overlap with an existing request, not in a locked attendance month, working days only per the Holiday Calendar and Sundays), and approved leave feeds spec 008 attendance by marking the covered working days with the Leave (L) status code (attributed to the approver, respecting month lock). Role & Permission Impact, audit events, per-role authorization and scope-boundary tests, and Tamil Nadu-friendly demo seed data. Follows docs/spec-roadmap.md row 009; depends on 008."

## Clarifications

### Session 2026-10-04

- Q: When approved leave covers a day a supervisor has already marked, should approval overwrite it?
  → A: Only days that are unmarked or were marked by the Teacher themself are overwritten (the earlier
  value stays in the day's history). If any covered day was set by a Manager, Admin or Director with
  a status other than L, the whole approval is refused and lists those days; the approver corrects
  them on purpose and approves again.

- Q: How far back may a Teacher apply for leave? → A: Up to 30 days before today (a start date older
  than that is refused). Approval still follows the locked-month and supervisor-set-day rules, so
  older days that were already marked or locked are refused at approval, not at application.

- Q: May a Teacher cancel their own Approved leave? → A: Yes, but only while its first day is still in
  the future; this removes the marks the request created, is audited as a Teacher cancellation, and
  needs no reason. Once the first day has arrived only a supervisor can revoke it.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - A Teacher Applies for Leave (Priority: P1) 🎯 MVP

A Teacher opens LEAVE → Apply Leave, chooses a leave type, a first and last date, optionally a half
day at the start or the end, and a short reason, and submits. The system shows how many working days
the request covers (Sundays, the School's weekly off days and holidays are not counted) and saves it
as **Pending**.

**Why this priority**: Without a way to ask for leave nothing else in this spec has input.

**Independent Test**: sign in as a placed Teacher, apply for three days that include a Sunday and a
holiday, and see a Pending request counting only the working days.

**Acceptance Scenarios**:

1. **Given** a placed Teacher, **When** they submit a valid request, **Then** it is saved as Pending
   with its working-day count, and their Manager (and Admin and Director) can see it.
2. **Given** a range with no working day in it, **When** they submit, **Then** it is refused with
   the reason.
3. **Given** an existing Pending or Approved request overlapping any of the dates, **When** they
   submit, **Then** it is refused naming the clashing request.
4. **Given** a first date in a locked attendance month, or more than 30 days before today,
   **When** they submit, **Then** it is refused with the reason.
5. **Given** a Teacher with no current placement, **When** they open Apply Leave, **Then** they are
   told they cannot apply until they are placed.

---

### User Story 2 - A Teacher Follows and Cancels Their Requests (Priority: P1)

LEAVE → My Leave History lists the Teacher's own requests, newest first, with type, dates, working
days, status (Pending, Approved, Rejected, Cancelled), who decided and when, and the rejection
reason. A Teacher can cancel a Pending request, or an Approved one whose first day is still in the
future.

**Why this priority**: The Teacher must see the outcome, and must be able to withdraw a mistake.

**Independent Test**: apply, see it in History as Pending, cancel it, see Cancelled; a Manager sees
it gone from the pending list.

**Acceptance Scenarios**:

1. **Given** a Pending request, **When** the Teacher cancels it, **Then** its status is Cancelled and
   it can no longer be approved.
2. **Given** an Approved request whose first day is still in the future, **When** the Teacher cancels
   it, **Then** it becomes Cancelled and the L marks it created are removed.
   2a. **Given** an Approved request that has started, or a Rejected one, **When** the Teacher looks
   at it, **Then** there is no cancel action; to withdraw started leave they ask a supervisor
   (User Story 4).
3. **Given** two Teachers, **When** one opens History, **Then** they see only their own requests.

---

### User Story 3 - A Manager, Admin or Director Decides Requests (Priority: P1)

OPERATIONS → Leave Management lists requests in the caller's scope (a Manager: Teachers in their
assigned Zones and Schools; Admin and Director: everyone), filterable by status (Pending by default),
Teacher, School and month. The approver opens a request and **approves** it, or **rejects** it with a
mandatory reason. A request already decided or cancelled cannot be decided again.

**Why this priority**: The decision is the core business step.

**Independent Test**: as Manager, approve one in-scope request and reject another with a reason; the
Teacher sees both outcomes; the Manager cannot see or decide an out-of-scope Teacher's request.

**Acceptance Scenarios**:

1. **Given** a Pending request in scope, **When** the approver approves, **Then** it becomes Approved,
   recording the approver and time, and its days are applied to attendance (User Story 4).
2. **Given** a Pending request, **When** the approver rejects without a reason, **Then** the
   rejection is refused; with a reason it becomes Rejected and the Teacher sees the reason.
3. **Given** a Manager and a request from a Teacher outside their assignment, **When** they list or
   try to decide it by direct call, **Then** it is absent from the list and the call is refused as
   not found.
4. **Given** two approvers acting on the same request at once, **When** both submit, **Then** exactly
   one succeeds and the other is told it has already been decided.
5. **Given** the Teacher cancelled the request while the approver had it open, **When** the approver
   submits, **Then** they are told it was cancelled.

---

### User Story 4 - Approved Leave Appears in Attendance (Priority: P1)

When a request is approved, each covered working day gets the Leave (**L**) mark in attendance for
that Teacher, with a day value of 1, or 0.5 for a half-day start or end. The mark is attributed to
the approver, links back to the request, and shows in the Teacher's calendar, the supervisors' grids
and the monthly rollup exactly like any other leave mark. A day already marked with another status
is replaced (the earlier mark stays in that day's history). A supervisor can **revoke** an approved
request, which removes the marks it created (unless the day was changed by someone after) and sets
the request to Cancelled with a reason.

**Why this priority**: "Approved leave feeds attendance" is the point of the roadmap row.

**Independent Test**: approve a five-day request that includes a holiday; the four working days show
L in the grid and rollup; revoke it and they are unmarked again; a locked month in the range blocks
approval.

**Acceptance Scenarios**:

1. **Given** an approved request, **When** the Manager opens the attendance grid, **Then** the
   covered working days show L, set by the approver and tagged as from leave.
2. **Given** a covered day the Teacher marked themself as Present, **When** the request is approved,
   **Then** the day becomes L and its history shows the earlier Present.
   2a. **Given** a covered day a supervisor set to anything other than L, **When** the request is
   approved, **Then** the approval is refused as a whole, listing those days, and nothing changes.
3. **Given** a covered day in a locked month, **When** the approver approves, **Then** the approval is
   refused as a whole with the locked months named and nothing changes.
4. **Given** future days in the range, **When** approved, **Then** they are marked L too (leave is
   planned ahead; this is the one case where future days carry a mark).
5. **Given** an approved request, **When** a supervisor revokes it with a reason, **Then** its marks
   are removed, except days a person has since changed by hand, which stay as they are; a revoke
   touching a locked month is refused.
6. **Given** a Teacher whose placement changes during the range, **When** approved, **Then** each
   day's mark follows the School they are placed in on that date.

---

### User Story 5 - Everything Is Audited and Scoped (Priority: P2)

Every application, cancellation, approval, rejection and revoke appears in Change History with who,
when, and the previous and new status, and each attendance mark made from leave is traceable to the
request. Roles see only what their permissions and scope allow.

**Why this priority**: Constitution Principles I–III; required, but built on the stories above.

**Independent Test**: perform each action and find its audit entry; run the per-role and
two-Manager boundary tests.

**Acceptance Scenarios**:

1. **Given** any state change of a request, **When** it completes, **Then** an audit entry exists
   with actor, time, request, Teacher and old and new status (and the reason when given).
2. **Given** a role without the permission, **When** it calls a leave endpoint, **Then** it is
   refused, and the menu item is not shown.

### Edge Cases

- A range crossing a month end where the later month is locked and the earlier is not: refused as a
  whole (all-or-nothing), naming the locked month.
- Half day on a single-day request: allowed as one half-day (0.5); a half day on both ends of a
  one-day range is not meaningful and is refused.
- A request whose first or last date is itself a non-working day: allowed; non-working days are
  simply not counted or marked.
- A holiday added to the calendar after approval: the existing L mark stays (an explicit mark
  prevails over the calendar, as in spec 008); the request's working-day count is not recomputed.
- A Teacher with no Manager assigned: their requests go to Admin and Director only.
- A Teacher becoming inactive or exited with Pending requests: Pending requests can still be
  decided or cancelled by supervisors; approval for days after exit is refused.
- Very long ranges: refused beyond 90 calendar days in one request.
- An approver approving a request that has become stale because the Teacher's earlier approved leave
  was revoked and re-applied: the overlap rule is evaluated at approval time as well as at apply time.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: A placed Teacher MUST be able to submit a leave request with a leave type, a first
  date, a last date, optional half-day-at-start and half-day-at-end flags, and a reason (up to 500
  characters). The request MUST be saved as Pending and show its working-day count.
- **FR-002**: The system MUST provide default leave types **Casual**, **Sick**, **Personal** and
  **Other**. The type is informational in this spec: every approved type is recorded in attendance as
  Leave (**L**). Leave types are seeded data, not editable in this spec.
- **FR-003**: A request's working days MUST be computed from the Teacher's School weekly off days and
  the organization non-working calendar (Holiday Calendar) as spec 008 defines them, per date and per
  the School of placement on that date. A request with no working days MUST be refused.
- **FR-004**: A request MUST be refused when any of its dates overlaps another Pending or Approved
  request of the same Teacher; falls in a locked attendance month; starts more than 30 days before
  today; or when the range is longer than 90 calendar days, or the last
  date precedes the first. The overlap rule MUST be re-checked at approval.
- **FR-005**: A Teacher MUST be able to list only their own requests (My Leave History) and cancel a
  Pending one. Nobody can edit a submitted request; a Teacher cancels and re-applies instead.
- **FR-006**: A Manager MUST be able to list, view, approve and reject requests only of Teachers in
  their data scope, obtained from the shared scope APIs of spec 005, never re-implemented. Admin and
  Director MUST be able to do so for every Teacher. Out-of-scope requests MUST be absent from lists
  and answered as not found on direct access.
- **FR-007**: Rejecting a request MUST require a reason (up to 500 characters). Approving MAY carry a
  note. A request MUST be decidable only while Pending; concurrent decisions MUST resolve to exactly
  one winner.
- **FR-008**: Approving a request MUST, in one atomic step, mark every covered working day Leave (L)
  with a day value of 1 (0.5 for a half-day start or end), attributed to the approver, tagged with
  the request, for the School of placement on that date, replacing a mark that is absent or was made by the Teacher themself (the earlier value stays in that
  day's history). If any covered day was set by a supervisor to a status other than L, or is in a
  locked month, the whole approval
  MUST be refused with nothing changed. Future days within the request MUST be marked too.
- **FR-009**: A supervisor in scope (and Admin and Director) MUST be able to revoke an Approved
  request with a mandatory reason. Revoking removes the marks that the request created and that no
  one has changed since, sets the request to Cancelled, and is refused when any such mark lies in a
  locked month. A Teacher MAY cancel their own Approved request only while its first day is in the future (same
  effect, no reason needed, audited as a Teacher cancellation); after that only a supervisor can revoke.
- **FR-010**: Leave marks MUST appear in the Teacher's attendance calendar, the supervisors' grids,
  the monthly rollup and the CSV export exactly like other L marks, and MUST be shown as set from
  leave with a link to the request wherever the mark's history is shown.
- **FR-011**: Every create, cancel, approve, reject and revoke MUST be recorded in the audit trail
  (Change History) with actor, time, Teacher, request, previous and new status and any reason, within
  5 seconds; each attendance mark created or removed by leave MUST be audited like any attendance
  change and refer to the request.
- **FR-012**: The system MUST add the permission modules and navigation items listed in Role &
  Permission Impact to the seeded matrix and the server-provided navigation model, idempotently, and
  every endpoint and screen MUST enforce them (a role without the action gets 403 and no menu item).
- **FR-013**: The Leave Management screen MUST show a count of Pending requests in scope, list
  Pending first by default, and let the approver filter by status, Teacher, School and month, with
  paging.
- **FR-014**: Demo data (when the demo flag is on) MUST include, idempotently, for the existing
  demo Teachers: a Pending request, an Approved request in the past with its L marks, and a Rejected
  request with a reason, using Tamil Nadu-flavoured reasons (for example Pongal travel, a family
  function, a medical appointment), so every screen has something to show.

### Key Entities

- **Leave Request**: a Teacher's ask for leave: type, first and last date, half-day-start and
  half-day-end flags, reason, working-day count, status (Pending, Approved, Rejected, Cancelled),
  decided by, decided at, decision note or rejection reason.
- **Leave Type**: Casual, Sick, Personal, Other (seeded); **Loss of Pay** (code `LOP`) added by amendment A3.
- **Leave Mark Link**: the tie between an attendance mark and the request that created it, so a revoke
  and the history can find it.

## Role & Permission Impact *(mandatory — Constitution Principles II–IV)*

| Role     | Menu (section → item) | Default actions | Data scope |
| -------- | --------------------- | --------------- | ---------- |
| Admin    | OPERATIONS → Leave Management | View, Approve (approve, reject, revoke) | Org-wide |
| Director | OPERATIONS → Leave Management | View, Approve | Org-wide |
| Manager  | OPERATIONS → Leave Management | View, Approve for Teachers in scope | Assigned (Zones → Schools → Teachers) |
| Teacher  | LEAVE → Apply Leave, My Leave History | View, Create, Delete (cancel own Pending, or Approved not yet started) | Own |
| System   | none | none (System MUST NOT see teacher or school business data) | None |

**New permission keys**: modules `LEAVE_MANAGEMENT` (actions `VIEW`, `APPROVE`; Admin, Director and
Manager) and `MY_LEAVE` (actions `VIEW`, `CREATE`, `DELETE`; Teacher). All false for the other roles;
all runtime-editable in Role & Permissions, where the eligibility rules keep System out of both. Only
Admin, Director and System may edit the role→permission matrix; this spec does not change that. The
navigation gains a new **LEAVE** section for Teacher, placed after the main business sections and
before ACCOUNT, and an **OPERATIONS → Leave Management** item.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A Teacher can submit a leave request in under 1 minute from opening Apply Leave.
- **SC-002**: A Manager can find and decide a Pending request in under 30 seconds.
- **SC-003**: 100% of approved requests produce exactly the expected L marks (working days only, half
  days at 0.5), and 100% of revokes remove exactly those marks, verified against hand-computed cases
  including holidays, Sundays and month ends.
- **SC-004**: In testing with at least two Managers with different assignments, 100% of lists,
  details and decisions return or accept only in-scope requests; no leaked record is found.
- **SC-005**: 100% of request state changes and leave-made attendance changes appear in the audit
  trail within 5 seconds with actor, time and old and new values.
- **SC-006**: 100% of approvals or revokes touching a locked month are refused with nothing changed.
- **SC-007**: When two approvers decide the same request at once, exactly one succeeds in 100% of
  trials.
- **SC-008**: Every leave screen is usable by keyboard alone and passes the same accessibility checks
  as earlier screens in light and dark themes.

## Assumptions

- Attendance (spec 008) provides the marks, status code L, month lock, holiday calendar, weekly off
  days, placement-by-date and scope APIs; this spec adds no new attendance rules except that leave
  may mark future days.
- There is no leave balance or entitlement tracking, no pay or deduction logic and no document
  attachment in this spec; payroll (013) later reads the L marks. Balances may follow in a later spec.
- Notifications of decisions to the Teacher are out of scope; spec 010 will add them. Until then the
  Teacher sees the outcome in My Leave History.
- One approval level only: the Manager in scope, Admin or Director decide; any one of them suffices.
- Only supervisors approve; a Teacher cannot approve at all, so self-approval does not arise.
- The Android app (019 and later) will reuse the same APIs; no mobile screen is built here.
- Demo Teachers and the 2026 Tamil Nadu holiday calendar from spec 008 are available for the seed.

## Amendment A3: Loss-of-Pay leave type

Added for spec 013a (salary structures), which treats an approved day of Loss-of-Pay leave as an unpaid day.

- A fifth seeded leave type, **Loss of Pay**, code `LOP`, sort order 5, active. Migration
  `V25__add_loss_of_pay_leave_type.sql` (idempotent). It is listed on Apply Leave like the others (the type list is
  server-driven, so web and mobile need no change).
- It behaves exactly like Casual, Sick, Personal and Other: same limits, preview, approval, cancel and revoke, and
  approval writes the same Leave (L) marks. Leave adds no pay logic (the "no pay or deduction logic" assumption above
  stands); payroll decides what an unpaid day is worth.
- `leave.api.LeaveTypes` exposes the code (`LOSS_OF_PAY_CODE = "LOP"`) and `isLossOfPay(requestId)` /
  `lossOfPayRequests(requestIds)`. A leave mark in attendance carries its request id (`MarkView.leaveRequestId`), and
  attendance keeps a leave mark only while the request is approved, so other modules identify an approved Loss-of-Pay
  day without reading leave tables. A half day keeps its day value on the mark.
- No other requirement changes.
