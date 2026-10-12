# Feature Specification: Manager Attendance and Field Days

**Feature Branch**: `032-manager-attendance`

**Created**: 2026-10-11

**Status**: Draft

**Input**: User description: "032-manager-attendance: Daily attendance for Managers, kept the way the Director deck describes it: check in, check out, say what kind of day it was, and leave that the Director approves. Module `attendance` (Manager part), separate from the Teacher attendance of spec 008. Full source: docs/spec-inputs/032-manager-attendance.md. Since 2026-10-11 spec 013a pays every person their own salary and reports a Manager's and a staff member's unpaid days as 'not available' until their attendance exists, so this spec gives payroll the Manager unpaid days and states what is decided for Admin, Director and System staff. The shared files module (spec 023) now exists for a check-in photo."

## Clarifications

### Session 2026-10-11

- Q: Do Admin, Director and System staff have attendance tracked? → A: No. Attendance is tracked for Managers only. An Admin or Director is paid in full from their joining date to their exit date, with no per-day deduction, and their unpaid days are reported as explicitly **none** (not "not available"). Any deduction for them is decided outside the system.
- Q: If a Manager checks in but never checks out, is the day still a worked, paid day? → A: Yes. The day counts as worked and paid and is only flagged "no check-out" until it is completed; a missing check-out never causes a deduction.
- Q: How far back may a Manager ask to add a forgotten check-in? → A: Any past day, as long as that day's month is not locked; there is no other time limit.
- Q: On a day of approved half-day leave, can the Manager check in for the other half, and how is the day counted? → A: Yes. One check-in is allowed on a half-day leave day, and the day counts as half leave plus half worked; the leave half is unpaid only if the leave type is Loss-of-Pay, otherwise the whole day is paid. A full-day leave still refuses a check-in.
- Q: What happens to a Manager with no joining date recorded? → A: They cannot check in or request leave until a joining date exists (spec 005a), and the month figures for them report "no joining date" instead of unpaid days; no day is counted against them until it is recorded.
- Q: Can a month be locked while leave or forgotten-check-in requests for it are still pending? → A: No. The lock is refused until every such request is decided, and the refusal lists the pending requests.
- Q: Are System accounts paid? → A: No. A System account is a technical account with no salary; it is not a payroll person at all (this also corrects spec 013a, which listed System users as staff).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Check In and Check Out for the Day (Priority: P1) 🎯 MVP

On a working day a Manager checks in, saying whether it is an Office day or a Field day, and later checks out. (A Leave
day is never chosen at check-in; it comes only from leave the Director or Admin has approved.) Each
records the time and the device location; the location is required. A photo may be added. A Manager has one check-in a
day; a missing check-out is flagged for that day and can be completed with a reason.

**Why this priority**: A Manager's pay depends on knowing which working days they were present. Without a check-in
there is nothing to pay from or deduct against, and payroll (013b) cannot run for Managers.

**Independent Test**: As a Manager, check in on a working day as Office, check out in the evening, and see the day
shown as worked with both times and the location in the month view; try a second check-in the same day and a check-in
without a location and see both refused.

**Acceptance Scenarios**:

1. **Given** a Manager on a working day, **When** they check in as Office with a location, **Then** the day is recorded
   as worked with the time, the location and the day type, and the Manager sees it at once.
2. **Given** a Manager who has checked in, **When** they check out, **Then** the check-out time and location are added to
   the same day; the day shows the hours between the two.
3. **Given** a Manager who checked in and did not check out, **When** the day has ended, **Then** the day is flagged
   "no check-out" but still counts as a worked, paid day, and the Manager (or an Admin or Director) can complete it with
   a check-out time and a reason.
4. **Given** a check-in or check-out with no location, **When** it is sent, **Then** it is refused and the reason says a
   location is required.
5. **Given** a Manager who already checked in today, **When** they try to check in again, **Then** it is refused; the
   existing check-in is shown.
6. **Given** a check-in with a photo, **When** it is saved, **Then** the photo is kept with the check-in and shown to the
   Manager, Admin and Director; a check-in without a photo is equally valid.
7. **Given** a weekly off or a holiday on the default calendar, **When** a Manager opens check-in, **Then** the day is
   shown as not a working day and no check-in is expected; a Manager who is working that day may still check in, and it
   is recorded as worked on a non-working day.

---

### User Story 2 - Field Days Linked to Planned Activities (Priority: P1)

A Manager who is out of the office chooses Field at check-in and says why: a School visit, a college drive or a
training. A field day is linked to the planned activities of the calendar (a visit from spec 023, a drive from spec 016,
a session from spec 024, a task from spec 025) so the Director can see what the day was for. Until those links exist
the Manager writes the purpose in words.

**Why this priority**: Field days are most of a Zone Manager's work and the deck asks for them to be told apart from
office days; without this a Manager out on a visit looks identical to one at a desk.

**Independent Test**: Check in as Field with a purpose, see it on the day in the month view and in the Director's grid;
once a planned activity exists, link it and see the activity on the day.

**Acceptance Scenarios**:

1. **Given** a Manager checking in, **When** they choose Field and write a purpose, **Then** the day is recorded as a
   field day with that purpose.
2. **Given** planned activities exist for the Manager on that day, **When** they choose Field, **Then** they can link one
   or more of them to the day, and the day shows them.
3. **Given** a Field day with no purpose and no linked activity, **When** it is sent, **Then** it is refused and the
   reason says what is missing.
4. **Given** the planned-activity links are available, **When** a Manager checks in as Field with only a purpose text,
   **Then** the day is accepted and flagged "not linked to a planned activity" for the Director's review.
5. **Given** a Field day, **When** the Admin or Director opens the day, **Then** they see the purpose, the linked
   activities, the check-in and check-out times and the locations.

---

### User Story 3 - Manager Leave Approved by the Director (Priority: P1)

A Manager asks for leave (a leave type, the dates and a reason). The Director or an Admin approves it, or rejects it
with a reason. Approved leave marks those working days as Leave. A working day with no check-in and no approved leave is
an unpaid absence.

**Why this priority**: Without leave there is no way to tell a justified absence from an unpaid one, and payroll must
deduct only the unpaid ones.

**Independent Test**: A Manager requests two days of casual leave; the Director approves; those days show as Leave and
count as paid; a third working day with no check-in and no leave shows as an unpaid absence.

**Acceptance Scenarios**:

1. **Given** a Manager, **When** they request leave with a type, a first and last date and a reason, **Then** the
   request is created as pending and the approver is notified.
2. **Given** a pending request, **When** the Director or an Admin approves it, **Then** every working day in the range
   is marked Leave, weekly offs and holidays inside the range are not counted as leave days, and the Manager is told.
3. **Given** a pending request, **When** it is rejected, **Then** a reason is required, the Manager sees it, and no day
   is changed.
4. **Given** an approved request, **When** the Director or an Admin revokes it with a reason, **Then** the days return to
   having no leave; the Manager is told.
5. **Given** a Manager's own request, **When** a Manager tries to approve it, **Then** it is refused; a Manager never
   decides leave, their own or anyone's.
6. **Given** a user who holds both the Manager role and the Admin or Director role, **When** they try to decide their
   own leave request, **Then** it is refused; nobody ever decides their own request. (Leave is for Managers only: an
   Admin or Director has no attendance or leave here.)
7. **Given** a Manager with approved full-day leave on a day, **When** they try to check in that day, **Then** it is
   refused with the leave shown; the approver can revoke the leave first if the Manager is in fact working.
   **Given** approved half-day leave on a day, **When** the Manager checks in, **Then** it is accepted and the day counts
   as half leave plus half worked.
8. **Given** a leave type that is the Loss-of-Pay type, **When** it is approved, **Then** its days are unpaid days for
   payroll; any other approved type is paid.

---

### User Story 4 - Missed Check-in Alert (Priority: P2)

If a Manager has not checked in by a cut-off time on a working day and has no approved leave, the Manager, the Director
and the Admin are told once that day, and the day shows as a missed check-in on the Director's dashboard.

**Why this priority**: It catches the problem the same day, while it can still be fixed, instead of at month end. It
adds nothing to the record itself, so it ranks after the record.

**Independent Test**: Leave a Manager without a check-in past the cut-off on a working day and see one notification to
each of the three and the day on the Director's list of missed check-ins; check in later and see the day leave the list.

**Acceptance Scenarios**:

1. **Given** a working day, a Manager with no check-in and no approved leave, **When** the cut-off time passes, **Then**
   the Manager, the Director and the Admin are each notified once for that day.
2. **Given** a Manager with approved leave, or a weekly off or a holiday, **When** the cut-off passes, **Then** nobody
   is notified.
3. **Given** a missed check-in, **When** the Manager checks in afterwards the same day, **Then** the day is recorded as
   worked (marked late) and leaves the missed list.
4. **Given** the cut-off time, **When** an Admin or Director changes the setting, **Then** the change applies to later
   days and is audited.

---

### User Story 5 - See and Correct the Month (Priority: P1)

A Manager sees their own month and today's status. An Admin or Director sees all Managers by month, the missed check-ins,
and who is on leave today, and can correct a Manager's day with a reason.

**Why this priority**: The Director needs the picture across Managers, and a wrong day (a forgotten check-in, a wrong
type) must be fixable before payroll reads it.

**Independent Test**: As a Manager, see each day of the month with its status; as an Admin, open the grid, filter to one
Zone, correct one Manager's forgotten check-in with a reason, and see the correction with who made it.

**Acceptance Scenarios**:

1. **Given** a Manager, **When** they open their month, **Then** each day shows its status (worked, field, leave,
   absent, weekly off, holiday, missed, no check-out), the times and the field-day links, and today's status is shown (whether today is a working day, the check-in and
   check-out so far, the cut-off time, and any leave covering today).
2. **Given** an Admin or Director, **When** they open the grid, **Then** every Manager is listed by month with a status
   for each day, filterable by Zone, with the list of missed check-ins and the Managers on leave today.
3. **Given** a day with no record or a wrong record, **When** an Admin or Director corrects it with a reason, **Then**
   the day shows the corrected value, the original and the correction stay in the day's history with who and when, and
   the Manager is told.
4. **Given** a past day in a month that is not locked, **When** a Manager asks to add a check-in they forgot, **Then**
   the request goes to an Admin or Director with a reason and a proposed time and day type; if approved the day is
   recorded as worked and marked "added later", if rejected the reason is shown; a day in a locked month is refused
   until an Admin reopens the month.
5. **Given** a Zone Manager, **When** they open another Manager's attendance, **Then** it is refused: a Manager sees only
   their own.

---

### User Story 6 - Lock the Month for Payroll (Priority: P2)

An Admin locks a Manager's month, or every Manager's, after corrections. Locking freezes the figures payroll reads; a
reopen needs a reason.

**Why this priority**: Payroll needs figures that cannot move under it, but nothing is paid until payroll exists, so the
lock follows the record.

**Independent Test**: Lock one month for all Managers, see check-ins, corrections and leave for it refused, reopen with
a reason and see them allowed again, with both actions audited.

**Acceptance Scenarios**:

1. **Given** a month that has ended, **When** an Admin locks it for one Manager or for all, **Then** the figures are
   frozen and no check-in, correction or leave decision changes that month's days.
2. **Given** a locked month, **When** someone tries to change a day in it, **Then** it is refused and the reason says the
   month is locked.
3. **Given** a locked month, **When** an Admin reopens it with a reason, **Then** the days can be changed again, and the
   reopen is audited with the reason; a lock again freezes the new figures.
4. **Given** a month that has not ended, **When** a lock is tried, **Then** it is refused.
5. **Given** a leave request or a forgotten-check-in request for the month that is still pending, **When** an Admin
   tries to lock it, **Then** the lock is refused and the reason lists the pending requests; once each is decided the
   lock is allowed.

---

### User Story 7 - Payroll Reads Manager Attendance (Priority: P1)

The payroll run (013b) and reports ask one place for a Manager's month: the working days, the days worked, the leave
days, the unpaid days, the field days, and whether the month is locked. The unpaid days feed spec 013a, which until now
reported a Manager's unpaid days as "not available".

**Why this priority**: This is how the attendance becomes pay. Without it a Manager's loss of pay can never be worked
out.

**Independent Test**: For a Manager with a known month (a full month, a day on approved paid leave, a day on
Loss-of-Pay leave, a day with no check-in and no leave, a holiday) the answers equal the hand-computed figures, and
`PayRules` in spec 013a returns the unpaid days instead of "not available".

**Acceptance Scenarios**:

1. **Given** a Manager's month, **When** the figures are asked for, **Then** working days, days worked, leave days,
   unpaid days and field days are returned, with whether the month is locked.
2. **Given** a working day with no check-in and no approved leave, **When** the unpaid days are asked for, **Then** the
   day is an unpaid day.
3. **Given** a day of approved Loss-of-Pay leave, **When** the unpaid days are asked for, **Then** it is an unpaid day;
   a day of any other approved leave is not.
4. **Given** a weekly off or a holiday on the default calendar, **When** the figures are asked for, **Then** it is
   neither worked nor unpaid.
5. **Given** days before a Manager's joining date or after their exit date (spec 005a), **When** the figures are asked
   for, **Then** they are not working days for that Manager and are never unpaid.
6. **Given** a half-day leave, **When** the unpaid days are asked for, **Then** it counts as half a day, unpaid only if
   the type is Loss-of-Pay.
7. **Given** a Manager with no data for a month (not yet joined, or the module just installed), **When** the figures are
   asked for, **Then** the answer says so; a month with no information is never reported as having no unpaid days.
8. **Given** a Manager with no joining date recorded, **When** the figures are asked for, **Then** the answer says "no
   joining date" and gives no unpaid days; when they try to check in or request leave, it is refused and the reason
   says the joining date must be recorded first.

---

### Edge Cases

- A Manager checks in on a weekly off or a holiday: allowed and recorded as worked on a non-working day; it is not a
  deduction either way and payroll treats it as informational.
- A check-in after the "late" time: recorded as worked and marked late; there is no pay effect, because the deck gives
  none.
- Check-in time is taken from the server clock in the business time zone (Asia/Kolkata), never from the device clock.
- A Manager has two check-ins in the same instant from two devices: one is recorded, the other is refused as a second
  check-in.
- A Manager's leave range spans a month that is locked: the days in the locked month are not changed and the decision is
  refused with the month named.
- A Manager's joining date is mid-month: days before it are not working days; their leave or check-in on those days is
  refused.
- A Manager moves Zone: their attendance stays theirs; the Zone filter in the grid uses the Zones they hold now.
- A Manager is deactivated mid-month: days after the exit date (spec 005a) are not working days for them.
- A holiday is added to the calendar after a Manager was already marked absent for that day: the day stops being a
  working day and stops being unpaid.
- The location is unavailable (device off or permission refused): the check-in is refused; there is no "skip location".
- A photo that is too large or not an image: refused with the reason; the check-in itself can be sent without it.
- An Admin or Director opens Manager Attendance: they see Managers only, never themselves; there is no check-in for
  them.
- The Director is also the only approver in some months (the Admin is on leave): a pending request waits; a Director
  never decides their own.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST let a Manager check in once per day, recording the time (from the server, in the business
  time zone), the device location, the day type (Office or Field; see FR-006) and optionally a photo.
- **FR-002**: The system MUST refuse a check-in or check-out that has no location, and a second check-in on the same
  day.
- **FR-003**: The system MUST let a Manager check out once, adding the time and the location to that day's record, and
  MUST flag a day that ended with a check-in and no check-out; such a day MUST still count as worked and paid, and a
  missing check-out MUST NOT cause a deduction or block the lock of the month.
- **FR-004**: The system MUST let the Manager, an Admin or a Director complete a missing check-out with a time and a
  reason, audited.
- **FR-005**: The system MUST treat working days as the default calendar of spec 008 (weekly offs and non-working
  dates); a Manager has no School.
- **FR-006**: The system MUST record the day type chosen at check-in as Office or Field; a Leave day comes only from
  approved leave (FR-010), never from a check-in.
- **FR-007**: The system MUST require a Field day to carry a purpose text or a link to at least one planned activity,
  and once the planned-activity links exist MUST accept a purpose-only Field day but flag it for review. (A purpose-only Field day is accepted and flagged for review; training-session links wait for spec 024.)
- **FR-008**: The system MUST let a Manager request leave with a leave type, a first and last date and a reason, and a
  half day at either end.
- **FR-009**: The system MUST let an Admin or Director approve a Manager's request or reject it with a reason, and MUST
  let them revoke an approved request with a reason; no one decides their own request, and a Manager never decides any.
- **FR-010**: The system MUST mark every working day of an approved request as Leave, MUST NOT count weekly offs or
  holidays inside the range, and MUST refuse a check-in on an approved full-day leave day; on an approved half-day leave day it MUST allow one
  check-in, and the day counts as half leave plus half worked (the leave half unpaid only for the Loss-of-Pay type).
- **FR-011**: The system MUST use the leave types of spec 009 (including the Loss-of-Pay type) for a Manager's leave,
  and MUST treat approved Loss-of-Pay leave as unpaid and any other approved leave as paid.
- **FR-012**: The system MUST treat a working day inside a Manager's employment with no check-in and no approved leave
  as an unpaid absence.
- **FR-013**: The system MUST notify the Manager, the Director and the Admin once per working day when a Manager has not
  checked in by a cut-off time, with no approved leave, and MUST show the day on the Director's missed check-in list.
- **FR-014**: The system MUST keep the cut-off time and the late time as settings that an Admin or Director changes,
  audited, applying to later days.
- **FR-015**: The system MUST show a Manager their own month and today's status (whether today is a working day, the check-in and
  check-out so far, the cut-off time and any leave covering today), and MUST show an Admin or Director the
  grid of all Managers by month with a Zone filter, the missed check-ins and the Managers on leave today.
- **FR-016**: The system MUST let an Admin or Director correct a Manager's day with a reason, keeping the original and
  the correction in the day's history with who and when.
- **FR-017**: The system MUST let a Manager ask for a forgotten check-in on any past day whose month is not locked,
  with a reason, a proposed time and a day type, MUST refuse a request for a day in a locked month, MUST send it to an
  Admin or Director, and MUST record the day as "added later" only when approved.
- **FR-018**: The system MUST let an Admin lock a Manager's month, or all Managers' months, once the month has ended,
  freezing the figures payroll reads; MUST refuse the lock while any leave or forgotten-check-in request touching that
  month is pending, listing them; MUST refuse changes to a locked month; and MUST let an Admin reopen with a reason.
- **FR-019**: The system MUST provide payroll and reports one public interface answering, for a Manager and a month: the
  working days, the days worked, the leave days (paid and unpaid), the unpaid days, the field days and whether the month
  is locked; days outside the Manager's employment (spec 005a) are not working days; and a month with no data MUST be
  reported as such, never as no unpaid days.
- **FR-020**: The system MUST make spec 013a's pay rules (`PayRules`) use that interface so a Manager's unpaid days and
  total loss of pay are answered instead of "not available".
- **FR-021**: The system MUST audit every check-in, check-out, completion, correction, leave request, decision and
  revoke, regularization, lock, reopen and setting change with who, when and the prior and new values.
- **FR-022**: The system MUST refuse every request outside the caller's permission on the server, not only in the menu:
  a Manager reaches only their own attendance and leave; Admin and Director reach all Managers'; Teacher and System
  reach none.
- **FR-023**: The system MUST track attendance for Managers only. For an Admin or Director (the other staff that spec
  013a pays from their own salary) it MUST report in `PayRules` that they have **no unpaid days** by rule, as a stated
  answer (they are paid in full from joining to exit), never as "not available"; a System account is not a payroll
  person and has no salary, attendance or unpaid days.
- **FR-024**: The system MUST refuse a check-in and a leave request from a Manager whose joining date (spec 005a) is not
  recorded, saying why, and MUST report such a Manager's month as "no joining date" in the interface of FR-019, never
  as unpaid days.

### Key Entities *(include if feature involves data)*

- **Manager Day**: one Manager's record for one date: the check-in and check-out times and locations, the day type
  (Office or Field), the purpose, the linked planned activities, an optional photo, whether it was late, added later,
  corrected or missing a check-out. At most one per Manager per date.
- **Manager Leave Request**: a Manager's request with a leave type, dates, half days, a reason, its status (pending,
  approved, rejected, revoked), who decided and why.
- **Forgotten Check-in Request** (called a regularization request in the design documents): a Manager's request to add a forgotten past check-in, with a reason, a proposed time and
  day type, its status and who decided.
- **Day History Entry**: an original value, a correction, who made it and when, with the reason.
- **Manager Month**: a Manager's month with its status (open or locked), the lock and reopen events and the frozen
  figures.
- **Attendance Settings**: the cut-off time for a missed check-in and the late time.
- **Manager Month Figures** (derived): working days, days worked, leave days, unpaid days, field days, locked.

## Role & Permission Impact *(mandatory — Constitution Principles II–IV)*

| Role     | Menu (section → item) | Default actions | Data scope |
| -------- | --------------------- | --------------- | ---------- |
| Admin    | OPERATIONS → Manager Attendance (grid, missed, on leave today, corrections, lock); OPERATIONS → Manager Leave (decisions) | View, Create (corrections), Edit (corrections), Approve (leave and regularization), Process (lock and reopen) | Org-wide |
| Director | OPERATIONS → Manager Attendance; OPERATIONS → Manager Leave | View, Create, Edit, Approve; no lock by default | Org-wide |
| Manager (Zone Manager) | MY ATTENDANCE → Check-in and My Month; MY ATTENDANCE → My Leave | View, Create (check-in, check-out, leave and regularization requests), Delete (cancel own pending request) | Own |
| Teacher  | none | none | None |
| System   | none (System MUST NOT see business data) | none | None |

**New permission keys**: `MANAGER_ATTENDANCE` (View, Create, Edit, Process; the all-Managers grid, corrections,
lock), `MANAGER_LEAVE` (View, Approve; decisions on Managers' leave and regularization), `MY_MANAGER_ATTENDANCE` (View,
Create, Delete; a Manager's own check-in, month and requests). Seeded as above; Teacher and System are not eligible. Only
Admin, Director and System edit the matrix. The constitution's Default role access matrix gets a Manager Attendance row
when this spec merges.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A Manager can check in, with a location, in under 30 seconds from opening the screen, and check out in
  under 20 seconds.
- **SC-002**: For a Manager's month, the unpaid days, leave days and days worked match the hand-computed figures for a
  full month, a paid leave, a Loss-of-Pay leave, a day with no check-in, a half-day leave, a holiday, a weekly off and a
  mid-month joiner, with no day counted twice.
- **SC-003**: After a locked month, no check-in, correction or leave decision can change a day in it, proved by a test
  on each path; a reopen needs a reason and is audited.
- **SC-004**: A Manager reaches only their own attendance and leave, and a Teacher and System user reach none, proved by
  a test for each endpoint.
- **SC-005**: A missed check-in alert reaches the Manager, Director and Admin once, within 5 minutes of the cut-off, and
  never twice for the same day.
- **SC-006**: An Admin or Director can find every Manager with a missed check-in today, and every Manager on leave today,
  in one screen.
- **SC-007**: Every change to a day or a decision is traceable: who, when, why and the prior and new value, with the
  original always retained.
- **SC-008**: A month with no information is never reported as zero unpaid days; payroll always receives either a figure
  or a stated reason.

## Assumptions

- The cut-off time for a missed check-in defaults to 10:30 and the late time to 09:30 in the business time zone, both
  settings an Admin or Director can change.
- The location is supplied by the device (the browser here, the Android app in spec 033); no distance or geofence check
  is made, and no shifts, overtime or break tracking exist.
- A check-in photo is stored with the shared `files` module (spec 023), is optional, and is limited in size and to
  images as that module defines.
- A Manager's leave uses the leave types and the Loss-of-Pay type of spec 009; spec 009's own request flow is for
  Teachers, so Manager leave is a separate request flow in this spec that reads the same types.
- The default calendar of spec 008 (weekly offs and non-working dates) gives a Manager's working days; a Manager's
  joining and exit dates come from spec 005a, and a day outside them is not a working day for that Manager.
- Planned-activity links to visits (023), drives (016) and tasks (025) come from the shared planned-activity interface
  (contract C1), which now exists for those three; training sessions (024) are not built, so that kind is absent until it
  is. A Field day links to them or carries a purpose text.
- Spec 013a is amended by this spec in two ways: `PayRules` returns a Manager's unpaid days and total loss of pay from
  this module instead of "not available", and it returns an Admin's or Director's unpaid days as an explicit "none".
  The earlier 013a rule that System accounts are paid staff is withdrawn: System accounts have no salary.
- An Admin or Director who does not work some days is handled outside the system; recording per-day leave or
  deductions for them is a later spec if wanted.
- Spec 010 (notifications) delivers the missed check-in alert and the leave and regularization messages, and spec 003
  records the audit.
- Out of scope: the Android screens (spec 033), geofencing and distance checks, shifts and overtime, the salary itself
  and the payroll run (013b), travel claims (015), and Teacher attendance (spec 008).
