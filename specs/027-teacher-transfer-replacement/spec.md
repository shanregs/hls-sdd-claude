# Feature Specification: Teacher Transfer and Replacement

**Feature Branch**: `027-teacher-transfer-replacement`

**Created**: 2026-10-18

**Status**: Draft

**Input**: User description: "027-teacher-transfer-replacement: Move a Teacher from one School to another (transfer) and put a new Teacher in the place of one who leaves a School (replacement). A Zone Manager, Admin or Director raises a request with the reason, the Teacher, the School, the replacement Teacher where there is one, and the effective date. The Director or the Zone Manager approves it (decision D5). On approval the placements change on the effective date with no gap and no overlap, the School's contract position stays filled, and the move shows in the Teacher's and the School's history. MVP priority 1."

## Clarifications

### Session 2026-10-18

These were decided from the roadmap, the business-flow document and the code that exists today, because no one could be asked. Each is repeated under Assumptions as "to confirm in /speckit-clarify".

- Q: What is a transfer and what is a replacement? → A: A **transfer** moves one Teacher who is placed in a School to another School. A **replacement** takes one Teacher out of a School position and puts another, who is not placed anywhere, into the same position at the same School. Both are one request, one decision and one effective date, and each changes placements as a single step.
- Q: Who may ask, and who decides? → A: Admin, Director and a Zone Manager (for the Teachers and Schools in their scope) may raise a request. The **Director or a Zone Manager decides** (D5). Admin raises and reads but does not decide. Nobody decides a request they raised themselves.
- Q: Which Zone Manager decides when two Schools are involved? → A: The Director, or a Zone Manager of the School the Teacher is leaving. A Zone Manager of the receiving School is told and may read the request, but is not needed for the decision.
- Q: May the effective date be in the past? → A: No. It is today or later. A Teacher's days already recorded are never rewritten by a transfer.
- Q: What happens to the Teacher who is replaced? → A: At the effective date they stop being placed in that School and become available (unplaced), or, when the request names it, they are placed at another School from the same date. Recording a Teacher's exit stays in the Teacher exit flow; it is not done here.
- Q: What happens to attendance and leave already recorded? → A: Everything before the effective date stays exactly as it was, with the old School. A day on or after the effective date that already holds approved leave moves to the new School with the Teacher. A transfer is refused for a date on which the Teacher already has attendance recorded at the old School that is not leave (for example today), so the day is never split.
- Q: What happens to pay? → A: Nothing new is calculated here. A Teacher's salary is a person's fixed monthly amount and is paid only for days placed in a School (D12, D13); the transfer guarantees there is no day with two Schools or none, and payroll (013a, 013b) pro-rates from the placement dates that exist.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Request a Transfer and Have It Decided (Priority: P1) 🎯 MVP

A School asks for a Teacher to be moved, or the Manager decides to reshuffle. The Zone Manager opens OPERATIONS → Transfers & Replacements, chooses "Request a transfer", picks the Teacher (placed in one of their Schools), the receiving School, the position when the School has an MoU with named positions, the effective date, a reason category and a note. The request goes to the Director and to the Zone Manager who may decide it. One of them approves or rejects with a reason. On approval the Teacher leaves the old School the day before the effective date and starts at the new School on it.

**Why this priority**: This is the core need of the roadmap row and the first half of the business flow ("Transfer / Replace"). Without it moving a Teacher stays a quiet edit with no reason, no approval and no trail.

**Independent Test**: With one placed Teacher and a second School with a vacant position, raise a transfer for a date next week, approve it as the Director, and read the Teacher's history: old School ending the day before, new School starting on the date, with the reason and the approver.

**Acceptance Scenarios**:

1. **Given** a Teacher placed in School A and School B with a vacant position (or a "MoU pending" contract), **When** a Zone Manager of School A raises a transfer to B for a future date with a reason, **Then** one Pending request exists, the deciders are notified, and no placement has changed yet.
2. **Given** a Pending request, **When** the Director or a Zone Manager of School A (not the requester) approves it, **Then** the request becomes Approved and the move is recorded for the effective date (applied at once when the date is today, scheduled when later) and the Teacher is notified.
3. **Given** a Pending request, **When** the requester tries to approve it, **Then** it is refused; **When** an Admin tries to approve it, **Then** it is refused; **When** a Zone Manager outside School A's scope calls the server directly, **Then** the answer is "not found".
4. **Given** a Pending request, **When** it is rejected without a reason, **Then** it is refused; with a reason it becomes Rejected, nothing moves, and the requester is notified.
5. **Given** a request, **When** the receiving School has no vacant position on the effective date, is inactive, or the Teacher is still in training or has exited, **Then** raising it is refused with the reason, and the same checks run again on approval.
6. **Given** a Teacher who already has a Pending request, **When** a second request is raised for them, **Then** it is refused (one open request per Teacher).
7. **Given** an Approved request whose move is still in the future, **When** the requester, the Director or a deciding Zone Manager cancels it with a reason, **Then** the scheduled move is removed, the Teacher stays where they are, and the history shows the cancellation.

---

### User Story 2 - Replace a Teacher at a School (Priority: P1)

A School reports a problem with a Teacher and, after the Manager's review (and any observation), the Manager decides the Teacher should be replaced. The Zone Manager opens "Replace a Teacher", chooses the School and the Teacher to replace, then picks the replacement from the Teachers who are active and not placed on the effective date, and gives the date, reason and note. On approval the outgoing Teacher ends the day before, the replacement takes the same position from the effective date, and the School never has the position empty or double-filled.

**Why this priority**: This is the other half of the roadmap row and the workflow in section 33 of the business-flow document ("Find Available Teacher, Assign Replacement, Update School, Update Teacher History"). Billing continuity depends on the position staying filled.

**Independent Test**: At a School with an active MoU, replace its Teacher with an unplaced Teacher for a future date, approve it, and check on the day before, on the date, and the School's filled-versus-vacant count each day: filled on both sides, never double.

**Acceptance Scenarios**:

1. **Given** a placed Teacher at an MoU School, **When** a replacement request is raised, **Then** the candidates offered are the active Teachers not placed on the effective date and not holding another open request, each shown with name and current status, and the form shows the position being filled.
2. **Given** a replacement request, **When** a candidate who is placed elsewhere on the date, in training, or exited is named (even by calling the server directly), **Then** it is refused with the reason.
3. **Given** an Approved replacement, **When** the effective date arrives, **Then** the outgoing Teacher's last day at the School is the day before, the replacement's first day is the effective date, both on the same position, and the School's occupied count is the same on every day around the date.
4. **Given** an Approved replacement, **When** the request named a new School for the outgoing Teacher, **Then** that Teacher is placed there from the effective date, subject to a vacant position there; **When** it named none, **Then** they become available from the effective date and show in the available list.
5. **Given** a replacement and the School's position is also being changed by an MoU re-mapping on the same dates, **When** approval is attempted, **Then** it is refused with the conflict and nothing is partly applied.
6. **Given** a replacement that fails any step on approval, **Then** nothing changes for either Teacher (all or nothing).
7. **Given** a School with a "MoU pending" contract and no positions, **When** a replacement is approved, **Then** the replacement is placed at the School from the effective date with no position, as direct placement does today.

---

### User Story 3 - Placement Rules Hold Across the Move Date (Priority: P1)

The people who mark attendance, approve leave, find substitutes and run payroll must see one consistent picture on either side of the date: on every day the Teacher is placed in exactly one School (or none, only where the request says so), history before the date is untouched, and days after belong to the new School.

**Why this priority**: The roadmap says a transfer must keep billing continuous and pro-rate pay; a bug here silently mis-pays a Teacher or loses a day of a School's billing.

**Independent Test**: Approve a transfer for a date. Mark the Teacher present on the day before (old School) and on the date (new School); approve leave across the date; run the substitution and payroll reads for the month and compare.

**Acceptance Scenarios**:

1. **Given** an applied transfer, **When** attendance is marked for the day before the effective date, **Then** it carries the old School; **When** marked on the effective date, **Then** it carries the new School.
2. **Given** a Teacher with approved leave spanning the effective date, **When** the transfer is applied, **Then** leave days before the date stay with the old School and leave days from the date carry the new School; nothing is lost or double counted.
3. **Given** a transfer for a date on which the Teacher already has a recorded attendance mark that is not leave, **When** raising or approving, **Then** it is refused and the request asks for a later date.
4. **Given** a Teacher with an open substitution requirement at the old School on or after the effective date, **When** the transfer is applied, **Then** the requirement is cancelled with the reason "Teacher moved", any substitute is told, and it is raised again for the new School when the absence still stands.
5. **Given** a transfer in the middle of a month, **When** payroll reads the Teacher's placements for the month, **Then** it sees two consecutive spans meeting on the effective date with no overlap and no gap, so payable days are counted once.
6. **Given** an effective date that falls in an attendance month already locked for the Teacher, **When** a request is raised, **Then** it is refused; a past month, a locked month and an approved payroll run are otherwise never reached because the date is never in the past.
7. **Given** an approved payroll run or a locked month, **Then** a move never changes either.

---

### User Story 4 - Movement History for a Teacher and for a School (Priority: P2)

A Manager or the Director opens a Teacher and sees the full list of Schools they have been placed in (School, from, to, reason, who asked, who approved), and opens a School and sees the Teachers who came and left, including replacements. The Transferred and Replaced views of decision D3 are filters over this history.

**Why this priority**: The business-flow document says a Teacher's current School is never overwritten and management wants the full history, but the moves already work without this screen.

**Independent Test**: After a transfer and a replacement, open the moved Teacher and the School and read each side's list; filter the Teacher list by Transferred and by Replaced.

**Acceptance Scenarios**:

1. **Given** a Teacher with two placements, **When** their history is opened, **Then** both show with dates and, for the second, the reason and the approver; a first placement shows "Initial assignment".
2. **Given** a School, **When** its movement list is opened, **Then** arrivals and departures show with the other Teacher or School named, dates, and kind (Transfer, Replacement).
3. **Given** a Zone Manager, **When** they read either history, **Then** only Teachers and Schools in their scope show; others read as not found.
4. **Given** a rejected or cancelled request, **When** a history is read, **Then** the request stays visible in the request list but not as a placement.

---

### User Story 5 - Watch the Requests and Know About Them (Priority: P2)

The deciders and requesters need a list: Pending, Approved (scheduled), Applied, Rejected, Cancelled, with filters by status, School and dates, and notifications at each step (requested, decided, applied) so nothing waits unseen.

**Why this priority**: A request that no one sees stalls the School and keeps the problem open. The decision still works without the list because the notification links straight to the request.

**Independent Test**: Raise two requests, check the deciders' notifications and list, approve one, reject the other, and read each requester's notification and the list filters.

**Acceptance Scenarios**:

1. **Given** a new request, **When** it is raised, **Then** the Director and the deciding Zone Manager are notified once, and the Admin and requester see it in their list.
2. **Given** a decision, **When** it is taken, **Then** the requester and, on approval, the Teacher(s) and the Managers of the Schools involved are notified.
3. **Given** an Approved request whose date arrives, **When** the move takes effect, **Then** its status shows Applied (derived from the date, not from a job that must run).
4. **Given** the list, **When** filtered by status or School, **Then** only matching rows in the caller's scope show; export is out of scope.

---

### Edge Cases

- The effective date is today and the move is applied at once; if the Teacher already has a non-leave mark today the request is refused (see User Story 3).
- The requester or the deciding Zone Manager changes during the wait: the approver is whoever has authority at the time of the decision.
- The Teacher exits, becomes inactive or is moved by another route while the request is Pending: approval is refused as stale and the request is closed as Cancelled with the reason "Teacher no longer placed there".
- The effective date passes before anyone decides: the request can still be approved only after its date is changed to today or later; it is never applied to a past date.
- Two people decide at the same time: one succeeds, the other is told the request changed (version check).
- A replacement Teacher is also named in another open request: refused, one open request per Teacher.
- A School that is inactive or whose MoU ends before the effective date: refused for the receiving School; the outgoing School is not changed by being left.
- A swap (A to B's School and B to A's School) is two requests; no combined request exists in version 1.
- A new MoU is signed and re-maps the School's Teachers on the effective date: the later change wins, and a still-Pending request is checked again at approval.
- A Teacher who is the only Teacher of a School is replaced: allowed; the position stays filled by the replacement.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST let Admin, Director and a Zone Manager raise a **transfer** request for a Teacher currently placed in a School in the requester's scope, naming the receiving School, a position when the receiving School has an MoU whose positions have their own salaries, the effective date, a reason category and a note.
- **FR-002**: The system MUST let the same roles raise a **replacement** request for a placed Teacher, naming the replacement Teacher, the effective date, a reason category and a note, and optionally a School for the outgoing Teacher to move to.
- **FR-003**: The reason categories MUST be: School asked for a change (including performance), Teacher asked for a change, Manager rebalancing, After review or observation, and Other; a note is required for Other and for any School-initiated request.
- **FR-004**: The effective date MUST be today or later. A request MUST be refused when the Teacher already has a recorded non-leave attendance mark at the old School on or after the effective date, or when the date falls in an attendance month already locked for the Teacher.
- **FR-005**: The system MUST check, when raising and again when approving, that: the Teacher is active and placed in the named School; the replacement or moving Teacher is active (not in training, not exited) and not placed anywhere on the effective date; the receiving School is active; and, where the receiving School has a contract in effect on the date, a position is vacant or named and vacant. A failed check MUST refuse the step with a plain reason and change nothing.
- **FR-006**: A Teacher MUST have at most one open (Pending) request, whether as the moving, the replaced or the replacement Teacher.
- **FR-007**: The Director or a Zone Manager of the School the Teacher is leaving MUST be able to approve or reject a Pending request; rejection MUST require a reason. A user MUST NOT decide a request they raised. Admin MUST NOT decide. A decision MUST use an optimistic version check.
- **FR-008**: On approval the system MUST change the placements in one all-or-nothing step: the outgoing placement ends the day before the effective date; the new placement starts on the effective date; for a replacement both use the same position; a future date schedules the move and today applies it at once. No day may have two Schools and, apart from the outgoing Teacher of a replacement becoming available as the request says, no day may have none.
- **FR-009**: The system MUST NOT overwrite or delete placement history. Every transfer or replacement adds placement rows and a request row; a cancelled scheduled move is marked cancelled, not erased.
- **FR-010**: Marks already written for days before the effective date MUST NOT change. Approved leave days on or after the effective date MUST carry the receiving School.
- **FR-011**: Open and assigned substitution requirements raised for the moving Teacher's absences at the old School on or after the effective date MUST be cancelled with the reason "Teacher moved", with any substitute told, and raised again for the new School when the absence still stands.
- **FR-012**: The system MUST let the requester, the Director, or a deciding Zone Manager cancel a Pending or a still-future Approved request with a reason; a request already applied MUST NOT be cancellable.
- **FR-013**: The system MUST show OPERATIONS → Transfers & Replacements: the request list (filter by status, School, date range; default Pending and Approved), a request detail with checks, decision and history, and "Request a transfer" and "Replace a Teacher" actions.
- **FR-014**: The system MUST show a placement history on each Teacher (School, from, to, kind, reason, requester, approver) and a movement list on each School, and MUST support Transferred and Replaced as filters of the Teacher list (decision D3).
- **FR-015**: The system MUST notify: deciders when a request is raised; the requester when it is decided or cancelled; the moving Teacher, the replaced Teacher and the Managers of the Schools involved when the request is approved and when it takes effect.
- **FR-016**: The system MUST scope every read and write by the caller's role and Zones; out-of-scope ids MUST read as not found; System has no access; a Teacher sees no requests (their own placement history stays on their profile as today). Checked on the server on every endpoint.
- **FR-017**: The system MUST audit raise, approve, reject, cancel and apply with who, when, and prior and new values, and MUST keep an append-only history of each request's status changes.
- **FR-018**: The system MUST NOT bill, pay or compute salary; it MUST keep the placement data in the form those specs read (dated, non-overlapping, per position) so billing (022) and payroll (013a, 013b) stay continuous without extra rules.
- **FR-019**: Dates MUST be shown DD/MM/YYYY and every screen MUST follow the shared design system, light and dark themes, keyboard use and loading, empty and error states.

### Key Entities

- **Movement Request**: kind (Transfer or Replacement); the Teacher; the School they leave; the receiving School (transfer) or the replacement Teacher (replacement) and the outgoing Teacher's own destination if any; position; effective date; reason category and note; status (Pending, Approved, Rejected, Cancelled, Applied); requester; decider and decision reason; version.
- **Request History Entry**: append-only row for each status change: from, to, actor, time, note.
- **Teacher Placement** (existing): dated row from a Teacher to a School and position, with status; this feature adds rows to it, never edits history.
- **Movement** (derived): one line of a Teacher's or a School's history, joining a placement row to the request that caused it.

## Role & Permission Impact *(mandatory — Constitution Principles II–IV)*

| Role | Menu (section → item) | Default actions | Data scope |
| ---- | --------------------- | --------------- | ---------- |
| Admin | OPERATIONS → Transfers & Replacements | View, Create, Edit (raise, cancel) | Org-wide |
| Director | OPERATIONS → Transfers & Replacements | View, Create, Edit, Approve | Org-wide |
| Manager (Zone Manager) | OPERATIONS → Transfers & Replacements | View, Create, Edit, Approve | Assigned (their Zones and Schools) |
| Teacher | none (own placement history stays on My Profile) | none | Own history only |
| System | none (System MUST NOT see business data) | none | None |

**New permission key**: `TEACHER_TRANSFERS` with `VIEW`, `CREATE`, `EDIT` (cancel) and `APPROVE` (approve, reject). `APPROVE` is eligible for Director and Manager only, never Admin (decision D5); Admin, Director and System edit the matrix. The existing `TEACHERS` `EDIT` key keeps governing direct placement of an unplaced Teacher; a School-to-School move or a replacement goes only through this feature (see Assumptions). New notification types: `TRANSFER_REQUESTED`, `TRANSFER_DECIDED`, `TRANSFER_APPLIED`. The constitution's Default role access matrix gets a Transfers & Replacements row when this spec merges.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: For every approved transfer or replacement, 100% of days around the effective date have exactly one School for the Teacher, and 100% of replacements leave the School's filled-position count unchanged on every day; verified over today, future, month-end and year-boundary dates.
- **SC-002**: No attendance mark, leave day or payroll month dated before the effective date is changed by a move; proved by comparing before and after in tests.
- **SC-003**: A Zone Manager can raise a transfer or a replacement in under 2 minutes and a decider can decide it in under 1 minute from the notification.
- **SC-004**: Nobody can decide their own request, Admin can never decide, and a Zone Manager outside the Teacher's current School scope cannot see or decide it; proved by a test per endpoint and role.
- **SC-005**: A request that fails a rule (no vacant position, replacement placed elsewhere, locked month, recorded mark that day, stale Teacher) is refused with a reason in 100% of cases and leaves no partial change.
- **SC-006**: A Teacher's and a School's full movement history can be read in one place, and every change in it traces to a request with a reason and an approver.
- **SC-007**: Existing placement, attendance, leave, substitution and payroll behaviour and tests stay unchanged apart from the added entry point and the School tagging of future leave days.

## Assumptions

All defaults below are marked for the clarify session.

- Specs 005 (Teachers, Schools, Managers), 008 (attendance), 009 (leave), 010 (notifications), 012 (School contracts), 013a (salary and pay policy) and 017a (substitution) are implemented. 013b (payroll run) is in progress; 022 (billing) and 026 (observations) are not built, so this spec only keeps the placement data valid for them. A request may mention an observation in its note; no link to spec 026 exists yet.
- **What the code does today, which differs from the planning documents.**
  - Placements are not only an `assignment (schoolbilling)` of the roadmap row: they are dated contract-assignment rows in the School contracts module, behind the teacher module's placement interface, with statuses Active, Cancelled and Corrected. A move today is one placement call under `TEACHERS` `EDIT` with no reason, no approval and no request record; a future date schedules the move; one scheduled move at a time can be cancelled.
  - The roadmap says a Zone Manager "views and maps", but the code lets a Zone Manager move Teachers between Schools in their scope immediately, from today onward, with no approval. Admin and Director may also set a past date. This is the gap the feature fills.
  - There is no operation to end a placement without starting another (only the exit path ends one). A replacement's outgoing Teacher becoming available therefore needs a new, narrow capability to end a placement on a date.
  - The Teacher states "Transferred" and "Replaced" of D3 are not stored; they are derived views and no code derives them yet. Stored statuses are In training, Active, On leave and Exited.
  - Pay is not pro-rated per School: payroll reads placement spans and counts payable days, and the monthly divisor in a transfer month follows the last School's calendar. This spec does not change that.
  - Attendance needs a placement on the date to accept a mark and stamps the mark with that School; nothing re-tags existing marks, and nothing stops a placement change in a locked month. Leave days approved ahead of time are written as marks and would carry the old School until re-tagged.
  - The notice period the documents leave open (business-flow item list) has no answer anywhere: no minimum notice is enforced; the effective date is today or later. To confirm in /speckit-clarify.
- Direct School-to-School moves through the existing placement action are withdrawn from the Teacher screens (they become "Request a transfer"); the existing action stays for the first placement of an available Teacher and for Admin or Director correction of data. To confirm in /speckit-clarify.
- Approvers: the Director or a Zone Manager of the School the Teacher is leaving; a Zone Manager of the receiving School is told but need not agree; nobody decides their own request. Whether the receiving Manager must also agree for a cross-Zone move is to confirm in /speckit-clarify.
- The outgoing Teacher of a replacement becomes available or is placed at another named School; a Teacher exit is recorded separately in the exit flow. To confirm in /speckit-clarify.
- The replacement Teacher must be unplaced on the effective date; a Teacher already placed elsewhere is moved by their own transfer request, not by being named as a replacement. To confirm in /speckit-clarify.
- Open substitution requirements of the moving Teacher at the old School on or after the date are cancelled and raised again for the new School; an approved leave day moves to the new School. To confirm in /speckit-clarify.
- A swap of two Teachers is two requests; combined swaps are not in version 1.
- Billing continuity means the contract position is occupied on every day; money is spec 022's. Salary stays the Teacher's own and does not change with the School.
- Out of scope: mobile screens, Teacher exit, notice letters, SMS or email, a link to observations (026), exports, School-side portal.
