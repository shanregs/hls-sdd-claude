# Feature Specification: Training Calendar (Weekend Refreshers and Monthly Sessions)

**Feature Branch**: `024-training-calendar`

**Created**: 2026-10-23

**Status**: Draft

**Input**: User description: "024-training-calendar: Weekend refresher sessions and the standing monthly training calendar. Admin and Director schedule sessions with topic, trainer and venue; existing Teachers register for open weekend sessions; Admin and Director assign specific Teachers and Managers to a monthly session, which creates an expected-attendance obligation; expected versus attended is tracked per person; assigned but absent is shown as a non-compliance exception; each Teacher has a training completion history. Sessions are planned activities (contract C1) that feed the Manager Activity Calendar (spec 035). Depends on 008 attendance and 016 recruitment induction. Sources: docs/spec-roadmap.md row 024, docs/HLS Teacher Management System — Requirements.md section 8, docs/hls-speckit-specify-inputs.md section 9."

## Clarifications

### Session 2026-10-23 (defaults chosen without asking the user)

No questions were asked. The defaults below were taken from the project documents and are listed again, with the real open points, in "Open Questions for the User" at the end of this file.

- Q: Which training is in this spec? → A: Weekend refresher sessions (open to register) and the standing monthly sessions (assigned). The one-month induction for new recruits stays in spec 016; this spec shows it as read-only history only.
- Q: Is training paid? → A: No. Decided D2 (2026-10-05): training is unpaid. There is no stipend field, and payroll pays nothing for training days.

### Session 2026-10-24

- Q: Who may mark attendance for a training session? → A: Admin and Director for any session, and a Manager for the Teachers
  placed under them. A Manager's own training attendance is still recorded by Admin or Director.
- Q: Should a Manager's training attendance be written into Manager attendance (032)? → A: No. It stays in the training
  register and 032 reads it to show the day as a training field day; 024 writes nothing into 032 records.
- Q: What happens when a Teacher is already marked Present at school on the training day? → A: The system shows the clash and
  the half-day rules of spec 008 decide; nothing is overwritten silently and nothing is blocked.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Schedule Weekend Refresher Sessions and Teachers Register (Priority: P1) 🎯 MVP

An Admin or Director creates a weekend refresher session: a topic, the date and start and end time, a trainer, a venue (a physical place or a virtual link) and an optional seat limit. Once published, existing Teachers see it under their upcoming training and can register or cancel their registration until the session starts. The Admin or Director sees who registered, can add or remove a registration on someone's behalf, and can cancel or move the session; registered people are told.

**Why this priority**: The weekend refresher is the first named training programme in the requirements. Scheduling and registration are the smallest slice that gives Teachers something to see and gives HLS a list of who will come.

**Independent Test**: Create and publish a weekend session with a seat limit of 3, register 3 Teachers, try a fourth, cancel one registration, and confirm the fourth can then register and the Admin sees the right roster.

**Acceptance Scenarios**:

1. **Given** a draft session with topic, date, trainer and venue, **When** the Admin publishes it, **Then** it appears in every active Teacher's upcoming training and in the training calendar.
2. **Given** a published session with a free seat, **When** a Teacher registers, **Then** they are on the roster as Registered, once only.
3. **Given** a session at its seat limit, **When** another Teacher tries to register, **Then** it is refused with a clear message (no waiting list in version 1).
4. **Given** a registered Teacher, **When** they cancel before the session starts, **Then** the seat is freed and the cancellation is recorded; after the session starts, they cannot cancel.
5. **Given** a published session, **When** the Admin moves it to another date, **Then** the registrations are kept, registered people are notified, and the earlier date is kept in the session's history.
6. **Given** a Teacher who is exited or in induction (not yet active), **When** they look for sessions to register, **Then** they cannot register for a refresher.

---

### User Story 2 - The Standing Monthly Training Calendar (Priority: P1)

An Admin or Director sets up a standing monthly session, for example "second Saturday of every month, 10:00 to 13:00, with this trainer and venue", once. The system then creates that month's session on its date, each month, without anyone remembering to schedule it. The Admin or Director can change one month's date, topic, trainer or venue without disturbing the standing pattern, skip a month with a reason, and stop the standing pattern from a given month onwards (past sessions stay). The next months' sessions are visible on the training calendar ahead of time.

**Why this priority**: Requirement 8.6 says the training cadence must not depend on someone remembering to schedule it. It is the core of the "standing calendar".

**Independent Test**: Create a standing pattern for the second Saturday of each month, view the calendar for the next three months and see three sessions on the right dates; change one month's venue, skip another, and see the third unchanged.

**Acceptance Scenarios**:

1. **Given** a standing pattern (second Saturday, 10:00 to 13:00), **When** the Admin views the next three months, **Then** three sessions appear on the correct dates, each marked as from the standing pattern.
2. **Given** a month's session generated from a pattern, **When** the Admin edits that month's trainer, **Then** only that month changes and later months keep the pattern's trainer.
3. **Given** a month, **When** the Admin skips it with a reason, **Then** that month shows as Skipped on the calendar with the reason, and no one is expected to attend.
4. **Given** a pattern, **When** the Admin edits the pattern itself from a chosen month on, **Then** months from then on follow the new pattern and sessions already held or already edited by hand are untouched.
5. **Given** a pattern that falls on a date marked non-working or a holiday in the working calendar (spec 008), **When** the month is generated, **Then** the session is still created (training is not a school working day) and a notice shows the date coincides with a holiday so the Admin can move it.
6. **Given** a pattern, **When** it is stopped from a month onwards, **Then** no later sessions are created, untouched later sessions already created are removed from the calendar, and held ones stay as history.

---

### User Story 3 - Assign Teachers and Managers to a Monthly Session (Priority: P1)

An Admin or Director assigns specific Teachers and Managers to a monthly session: individually, by School, by Manager (all that Manager's Teachers), by Zone, or "all active Teachers and Managers". An assignment is not a registration: it creates an expected-attendance obligation for that person. The assigned person is told, sees the session as "Required" in their upcoming training, and cannot opt out (they can only tell the Admin they cannot attend, and the Admin decides whether to excuse them). Managers are assigned in the same way as Teachers; a monthly session can require both.

**Why this priority**: Assignment makes training an obligation, and the obligation is what the non-compliance exception is measured against. Without it the monthly calendar is only a notice board.

**Independent Test**: Assign 5 Teachers and 2 Managers to a monthly session, confirm all 7 see it as Required and are notified, assign the same person twice and see one obligation, and remove one before the session and see the obligation gone.

**Acceptance Scenarios**:

1. **Given** a monthly session, **When** the Admin assigns two Managers and all the Teachers of one Manager, **Then** each person gets one expected-attendance obligation and a notification.
2. **Given** a person already assigned, **When** they are assigned again (individually or through a group), **Then** no duplicate is created.
3. **Given** a Teacher assigned through a group, **When** they are later moved away from that group before the session, **Then** the assignment stays unless the Admin removes it (assignment is a snapshot, not a live rule; see Assumptions).
4. **Given** an assigned person who is on approved leave on the session date (spec 009 for Teachers, spec 032 for Managers), **When** the session is held, **Then** they are shown as "On leave" and not as non-compliant.
5. **Given** an assigned person, **When** the Admin removes the assignment before the session is held, **Then** the obligation is removed and the person is told; after the session is held, assignments cannot be removed (only excused).
6. **Given** a Manager, **When** they open their upcoming training, **Then** they see the sessions they are assigned to as Required; sessions open for registration are shown to Teachers only.
7. **Given** an open weekend session, **When** the Admin assigns people to it, **Then** the assignment is allowed too (a person assigned to a weekend session is Required, others stay optional); see Assumptions.

---

### User Story 4 - Record Who Attended: Expected Versus Attended (Priority: P1)

On the session day, the Admin or Director (or a Manager, for the Teachers placed under them) records attendance for the session's roster: Present, Absent (with a reason) or Excused, with a half day allowed for part attendance. A Teacher's Present mark is written as a training-day mark into their attendance record (spec 008) so it joins the monthly "training days attended" figure; a Manager's attendance is kept in the training register and is available to Manager attendance (spec 032). The session shows expected (assigned plus registered) against attended, with a count of each outcome. A mark can be corrected later; the earlier value stays in history.

**Why this priority**: Expected versus attended is the measurable result of the module and feeds the training-day figures and the exceptions list.

**Independent Test**: Hold a session with 6 assigned and 2 registered people, mark 5 Present, 1 half-day, 1 Absent with reason and 1 Excused; confirm the counts, confirm the Teachers' training-day marks appear in spec 008 with the right value, then correct one mark and see the history.

**Acceptance Scenarios**:

1. **Given** a session on its date, **When** attendance is recorded, **Then** every person on the roster has exactly one outcome (Present, Absent, Excused) and the session shows expected, attended, absent and excused counts.
2. **Given** a Teacher marked Present (whole day), **When** their attendance for the month is opened in spec 008, **Then** the day shows as a training day with the same weight rules as any training day, with no School needed.
3. **Given** a Teacher already marked Present at their School for that day, **When** training attendance is recorded, **Then** the system shows the clash to the user and does not silently overwrite; the half-day rules of spec 008 decide.
4. **Given** a month locked in spec 008, **When** training attendance for a day in it is recorded or corrected, **Then** the system follows the same lock and re-open rules as 008 and says so.
5. **Given** a recorded outcome, **When** a user corrects it, **Then** the new outcome replaces it, the earlier outcome, who changed it and when stay visible, and the Teacher's training-day mark in 008 is updated to match.
6. **Given** attendance is not recorded within 7 days after the session date, **When** the Admin opens the session, **Then** it is flagged "Attendance not recorded".
7. **Given** a person who turned up but was not on the roster, **When** the Admin adds them as a walk-in, **Then** they are recorded as Present with the mark "walk-in" and not counted as expected.

---

### User Story 5 - Non-Compliance Exceptions (Priority: P2)

The Director and Admin see an exceptions list of assigned people who were Absent from a session without being excused: who, which session, which date, their Manager and Zone, and how many times in the last six months. The list can be filtered by month, Zone, Manager and role (Teacher or Manager). An exception is Open until someone records a response (excused with a reason, made up at a later session, or acknowledged), after which it is Closed. A Manager sees the exceptions of their own Teachers (and their own) so they can follow up. Repeated non-compliance (default: 2 or more in 6 months) is marked.

**Why this priority**: The source documents call out surfacing "assigned but absent" as an exception to Director and Admin. It depends on stories 3 and 4 and adds value on top of them.

**Independent Test**: With a session where 3 assigned people were absent (one excused later), open the exceptions list, see 2 open and 1 closed, close one with a make-up session and see the repeat mark for a person with an earlier absence.

**Acceptance Scenarios**:

1. **Given** an assigned person marked Absent, **When** the session's attendance is saved, **Then** an Open exception appears for them.
2. **Given** an Open exception, **When** the Admin excuses it with a reason, **Then** it becomes Closed, the reason and who closed it are kept, and the person no longer counts as non-compliant for that session.
3. **Given** an open-registration weekend session, **When** a registered Teacher does not attend, **Then** this is shown on the session as a no-show but is **not** a non-compliance exception (only assigned people are held to attend).
4. **Given** an assigned person who attends a later session, **When** the Admin links it as the make-up, **Then** the exception is Closed as "made up".
5. **Given** a Manager, **When** they open exceptions, **Then** they see only their own Teachers' and their own exceptions, never other Managers'.
6. **Given** a person with two unexcused exceptions in the last 6 months, **When** the list is shown, **Then** they are marked as repeated.

---

### User Story 6 - Training Completion History per Person (Priority: P2)

For each Teacher (and Manager), the system keeps a training history: every session they were expected at or registered for, with date, topic, trainer, outcome (attended, part, absent, excused) and any certificate note or remark the Admin records. The induction (from spec 016) appears as a read-only entry with its sign-off. The Director and Admin can open anyone's history; a Manager can open their Teachers' and their own; a Teacher can open their own. A yearly attended-versus-expected figure is shown. The history can be exported as a file by Admin and Director.

**Why this priority**: Requirement 8.4 asks for the history viewable by the Director and the Teacher. It is read-only on top of what is already recorded.

**Independent Test**: For a Teacher with an induction, three monthly sessions (two attended, one absent excused) and one weekend session, open their history and check the entries, their outcomes and the yearly figure.

**Acceptance Scenarios**:

1. **Given** a Teacher with sessions attended and missed, **When** the Director opens their history, **Then** each session appears once with its outcome, newest first, plus the induction entry.
2. **Given** a Teacher, **When** they open their own history, **Then** they see only their own entries, and no remarks marked internal.
3. **Given** a certificate note and an internal remark on an attendance, **When** the Teacher opens the history, **Then** the certificate note is visible and the internal remark is not.
4. **Given** a Manager, **When** they open a Teacher who is not in their scope, **Then** access is refused.
5. **Given** a person with no training yet, **When** their history is opened, **Then** an empty state is shown, not an error.

---

### User Story 7 - Sessions on the Planned-Activity Feed and the Upcoming Training List (Priority: P2)

Training sessions are planned activities. The training module exposes them through the shared planned-activity contract (C1) so that the Manager Activity Calendar (spec 035), Manager field-day linking (spec 032) and later the Director dashboard (spec 028) can read them. Each session is a planned activity of kind Training, owned by its assigned Managers (one entry per assigned Manager), dated on its date, with its venue as the place and a status (planned, held, cancelled), including a record of any date change. A Teacher sees an "Upcoming training" list with Required and Open sessions; assigned and registered people also get a reminder before the session.

**Why this priority**: The roadmap marks sessions as planned activities (C1) and spec 035 already expects training to appear "with no change to how the calendar works". It is a thin read layer once sessions exist.

**Independent Test**: With a monthly session assigned to two Managers, read the C1 feed for each Manager and see the session on its date with its status; move the date and see the earlier date recorded; confirm a Teacher sees only sessions they are required at or may register for.

**Acceptance Scenarios**:

1. **Given** a monthly session assigned to Managers A and B, **When** the planned-activity feed is read for A, **Then** the session is returned once as kind Training with date, venue and status.
2. **Given** a session that was held, cancelled or moved, **When** the feed is read, **Then** its status is Held or Cancelled, and a moved session carries its earlier date.
3. **Given** a Manager's field day (spec 032), **When** they link it to planned activities, **Then** a Training session they are assigned to is offered.
4. **Given** a session two days away, **When** the reminder time arrives, **Then** each assigned or registered person gets one reminder (spec 010); a cancelled or moved session sends a change notice instead.
5. **Given** a Teacher, **When** they open Upcoming training, **Then** sessions they are required at are marked Required and open weekend sessions with free seats are marked Open, in date order.

---

### Edge Cases

- **Holiday and non-working dates**: sessions may be placed on any date, including holidays and Sundays; the system shows a notice only.
- **Session cancelled after assignment**: assignments and registrations are kept as history, nobody is expected, no exception is raised, and everyone is notified.
- **Session moved after attendance is recorded**: refused; only a session not yet held can be moved.
- **Trainer changed**: the trainer can be changed until the session is held; the earlier trainer stays in the history.
- **Trainer is an external person**: a trainer is a name and optional contact; it does not have to be a user (see Assumptions).
- **Person assigned who later exits or loses the role**: the open obligation for a session not yet held is removed automatically and noted; a past session's outcome stays.
- **Teacher in induction**: induction attendance belongs to spec 016; a Teacher with status "in training" cannot be assigned to refreshers or monthly sessions.
- **Pattern edge dates**: a pattern on "the 31st" or "fifth Saturday" in a month that has none is created on the last matching date of that month; the pattern preview says so.
- **Same person on two sessions on the same day**: allowed with a warning; the attended value for that day cannot exceed one day in total.
- **Half day**: the Admin chooses whole or half day when recording; a session shorter than half a day defaults to a half day.
- **Virtual sessions**: the venue is a link; attendance is recorded by roll call, not by log-in tracking.
- **Late attendance entry**: allowed for any past session, within the lock rules of spec 008.
- **Large rosters**: up to 500 people on a session can be assigned, listed and marked in one pass.
- **Duplicate pattern**: two active patterns with the same date rule and time are refused as a probable duplicate.
- **Time zone**: sessions use local calendar dates and times with no conversion.

## Requirements *(mandatory)*

### Functional Requirements

**Sessions and the standing calendar**

- **FR-001**: The system MUST let Admin and Director create, edit, publish, move, cancel and (while draft) delete training sessions of two types: **Weekend refresher** (open to register) and **Monthly session** (assigned), each with a topic, date, start and end time, trainer, venue (physical place or virtual link), an optional seat limit and a description.
- **FR-002**: The system MUST let Admin and Director define a **standing monthly pattern** (for example "second Saturday", "last Saturday", or "day 15") with time, topic, trainer, venue, optional default assignment group and a start month, and MUST create each month's session from it without manual action, at least three months ahead.
- **FR-003**: A generated session MUST be editable on its own without changing the pattern; the pattern MUST be editable from a chosen month on without changing sessions already held or hand-edited.
- **FR-004**: Admin and Director MUST be able to skip a month of a pattern with a reason, and stop a pattern from a month onwards; held sessions MUST always remain as history.
- **FR-005**: The system MUST NOT create two sessions from one pattern for the same month, even if generation runs twice.
- **FR-006**: A session MUST have the statuses Draft, Published, Held and Cancelled (and Skipped for a skipped pattern month); it MUST be moved or edited only before it is Held; moving MUST keep the earlier dates as history.
- **FR-007**: The system MUST provide a **training calendar** (month grid and list) of sessions with filters by type, status, trainer and, for Admin and Director, by person.

**Registration and assignment**

- **FR-008**: Active Teachers MUST be able to register and cancel their registration for a published Weekend refresher until it starts, within the seat limit; the system MUST refuse a duplicate registration and one beyond the seat limit.
- **FR-009**: Admin and Director MUST be able to **assign** Teachers and Managers to a session individually, by School, by Manager, by Zone, or all active Teachers and/or Managers; an assignment MUST create exactly one expected-attendance obligation per person per session.
- **FR-010**: An assignment MUST be distinct from a registration: the person sees it as Required, cannot cancel it, and can only tell the Admin they cannot attend; only Admin and Director excuse.
- **FR-011**: Group assignment MUST be a snapshot of who is in the group on the day it is made; later changes to the group do not change assignments.
- **FR-012**: Assignments MUST be removable only before the session is Held; the person MUST be notified. People who exit or lose the role MUST have their open obligations for sessions not yet held removed automatically.
- **FR-013**: Teachers in induction (status "in training") and exited people MUST NOT be assignable or able to register.

**Attendance, expected versus attended**

- **FR-014**: Admin and Director MUST be able to record, per session, one outcome per person on the roster: Present (whole or half day), Absent with a reason, or Excused with a reason; and to add walk-ins as Present. A Manager MUST be able to do the same only for Teachers placed under them (and to add them as walk-ins), and MUST NOT record or change attendance for other people, including their own.
- **FR-015**: The system MUST show for every session the counts of expected (assigned plus registered), attended, absent, excused and walk-ins, and the attended-versus-expected rate for assigned people.
- **FR-016**: A Teacher's Present outcome MUST be written as a training-day mark (whole or half) in the attendance records of spec 008, with no School, using the existing training status code; corrections MUST update that mark and keep the earlier value in history. Spec 008's month lock and re-open rules MUST apply.
- **FR-017**: A Manager's Present outcome MUST be kept in the training register and MUST be available to Manager attendance (spec 032) to show the day as a training field day; this spec MUST NOT write into Manager attendance records itself.
- **FR-018**: Every attendance entry and correction MUST record who made it and when, and keep earlier values.
- **FR-019**: A session whose attendance is not recorded within 7 days of its date MUST be flagged for Admin and Director.
- **FR-020**: Approved leave on the session date MUST show the person as "On leave" and MUST NOT make them non-compliant.

**Exceptions and history**

- **FR-021**: When an assigned person is recorded Absent, the system MUST raise an Open **non-compliance exception**; a registered but absent Teacher at an open weekend session MUST show as a no-show only.
- **FR-022**: An exception MUST be closable by Admin or Director as Excused (with reason), Made up (linked to a later session the person attended) or Acknowledged (with note); the closer and time MUST be kept.
- **FR-023**: The exceptions list MUST be filterable by month, Zone, Manager, role and status, and MUST mark a person with repeated non-compliance (default threshold 2 in 6 months, set in System Settings).
- **FR-024**: The system MUST keep a training history per Teacher and Manager listing every session with outcome and any certificate note or remark; the induction from spec 016 MUST appear as a read-only entry; the history MUST show attended-versus-expected for a chosen year.
- **FR-025**: Remarks MUST be markable as internal; internal remarks MUST NOT be shown to the person concerned.

**Planned-activity contract and notifications**

- **FR-026**: The training module MUST expose its sessions through the planned-activity contract (C1) as kind Training: one entry per assigned or registered Manager, with id, owner Manager, date, venue as place, status (planned, held, cancelled) and, for a moved session, the earlier date. It MUST be readable by spec 035 and spec 032 without any change to how they read other kinds.
- **FR-027**: The system MUST send notifications (spec 010) for: new assignment, assignment removed, session moved or cancelled, a reminder 2 days and 1 day before (defaults, set in System Settings), registration confirmed, and a new exception to Admin and Director. Notices to Teachers MUST also be visible in the mobile notification list (spec 021).

**Access, audit and privacy**

- **FR-028**: Every create, edit, publish, move, cancel, assignment, removal, attendance entry, correction and exception closure MUST write an audit entry (spec 003) with who, when, the before and after values and the reason where one is asked.
- **FR-029**: A Manager MUST see and open only their own Teachers' and their own sessions, assignments, exceptions and history, using the shared scope interfaces of spec 005; a Teacher MUST see only their own.
- **FR-030**: The system MUST NOT show payroll or pay figures anywhere in this module, since training is unpaid.
- **FR-031**: The history and exceptions lists MUST be exportable to a file by Admin and Director only.

### Key Entities *(include if feature involves data)*

- **Training Session**: one dated event: type (Weekend refresher or Monthly), topic, date, start and end time, trainer, venue, optional seat limit, status, source pattern if generated, earlier dates.
- **Monthly Pattern**: the standing rule: day rule, time, topic, trainer, venue, default group, start month, end month (optional), skipped months with reasons.
- **Trainer**: a name and optional contact, optionally linked to a user.
- **Registration**: a Teacher's own sign-up for an open session: Registered or Cancelled.
- **Assignment (Expected Attendance)**: a person (Teacher or Manager) required at a session; source (individual or group); status Required or Removed.
- **Attendance Entry**: a person's outcome at a session (Present whole or half, Absent, Excused, Walk-in) with reason, who recorded it, when, and the history of earlier outcomes.
- **Non-compliance Exception**: an assigned person's unexcused absence: Open or Closed (Excused, Made up, Acknowledged) with closer, note and the make-up session if any.
- **Training History Entry (view)**: one line of a person's history, drawn from the above plus the induction from spec 016.

## Role & Permission Impact *(mandatory — Constitution Principles II–IV)*

| Role     | Menu (section → item) | Default actions | Data scope |
| -------- | --------------------- | --------------- | ---------- |
| Admin    | OPERATIONS → Training Calendar, Training Exceptions | View, Create, Edit, Delete (drafts), Export | Org-wide |
| Director | OPERATIONS → Training Calendar, Training Exceptions | View, Create, Edit, Delete (drafts), Export | Org-wide |
| Manager (Zone Manager) | MY WORK → My Training (own required sessions, own Teachers' attendance and exceptions, own history) | View; record attendance for own Teachers | Assigned (their Teachers) and own |
| Teacher  | MY TRAINING → Upcoming Training, My Training History | View; register and cancel own registration | Own |
| System   | none | none | None |

**New permission keys**: `TRAINING_ATTENDANCE` (`VIEW`, `EDIT`: record attendance for Teachers placed under the Manager), `TRAINING` (`VIEW`, `CREATE`, `EDIT`, `DELETE`, `EXPORT`: sessions, patterns, assignments, attendance, exceptions) and `TRAINING_REGISTRATION` (`VIEW`, `CREATE`, `EDIT`: a Teacher's own registration and history). Seeded: Admin and Director have `TRAINING` in full; the Zone Manager has `TRAINING` `VIEW` scoped as above and `TRAINING_ATTENDANCE` `VIEW` and `EDIT` limited to Teachers placed under them, and no other write action; the Teacher has `TRAINING_REGISTRATION` `VIEW`, `CREATE`, `EDIT` on own records; System has neither. Only Admin, Director and System edit the role-to-permission matrix. The constitution's Default role access matrix gets a Training row when this spec merges.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: An Admin sets up a standing monthly session once and the next three months of sessions exist on the correct dates with no further action; checked over a 12-month simulated run with no duplicates and no missed months.
- **SC-002**: An Admin can assign a group of 100 people to a monthly session in under 1 minute, with every one notified and exactly one obligation each.
- **SC-003**: For a prepared session (assigned, registered, on leave, walk-in, excused, absent), the expected, attended, absent and excused counts and the exceptions list match the hand-counted figures exactly.
- **SC-004**: A recorded Teacher training day appears in that Teacher's month in spec 008 as a training day with the right value in 100% of tests, including after a correction.
- **SC-005**: A Director can find every unexcused absence for a month, by Zone, in under 30 seconds from the exceptions list.
- **SC-006**: A Manager never sees another Manager's Teachers' sessions, exceptions or history, and a Teacher never sees another Teacher's, proved by tests with two Managers and two Teachers.
- **SC-007**: A training session assigned to a Manager appears in the Manager Activity Calendar (spec 035) on its date with the right status, with no change to that calendar.
- **SC-008**: Marking attendance for a session of 100 people takes a single pass and saves in under 3 seconds.
- **SC-009**: Every change to a session, an assignment, an attendance entry or an exception has an audit entry showing who, when and what changed.

## Assumptions

Defaults chosen without asking the user; those marked **(open)** are repeated in "Open Questions for the User".

- **Documents followed**: docs/spec-roadmap.md row 024 and contract C1; Requirements section 8 (items 1 to 8); speckit input section 9; spec 016 (induction stays there; D2: training is unpaid); spec 008 (training-day mark, month lock); spec 035 (reads training through C1 and expects it with no change).
- **Scope split**: induction batches, induction attendance and sign-off are in spec 016 and not repeated here; this spec only shows them in the history. A certificate is a text note per attendance, not a generated document.
- **Module**: the `training` module (already planned by 016 for induction) holds sessions, patterns, assignments, attendance and exceptions; it reads Teachers, Managers, Schools and Zones only through the public interfaces of spec 005, and writes training-day marks to spec 008 only through its public interface.
- **Weekend refresher is open registration; monthly session is assigned**; either type may also have assignments (US3 scenario 7) **(open)**.
- **Assignment is a snapshot** of a group on the day made, not a live rule **(open)**.
- **Who records attendance**: Admin and Director for any session; a Manager for the Teachers placed under them. A Manager's own attendance is recorded by Admin or Director.
- **Teacher attendance goes to 008 as a training-day mark with no School**, following the 016 pattern; **Manager** training attendance is kept in the training register and read by spec 032 rather than written into it **(open)**.
- **Clash with school attendance on the same day**: warn the user and let the half-day rules of spec 008 decide; no automatic overwrite **(open)**.
- **Non-compliance** means an assigned person marked Absent; registered no-shows are only shown **(open)**. The repeat threshold (2 in 6 months) and the reminder times (2 days and 1 day) are defaults held in System Settings. The 7-day missing-attendance flag is a default.
- **Seat limit** is optional; there is no waiting list in version 1.
- **Trainer** is a name and optional contact, not necessarily a user; trainers do not log in **(open)**.
- **Pattern day rules** supported: nth weekday of the month (first to fourth, or last) and a fixed day of the month; weekly or fortnightly patterns are not in version 1 **(open)**.
- **Unpaid**: no stipend or allowance field, in line with decision D2; a stipend would be a separate spec.
- **Mobile**: this spec delivers the web screens, including the Teacher and Manager views of upcoming training and history; phone screens (register, upcoming training, history) are a later mobile spec on the same API. Teacher notifications already reach the phone through spec 021 **(open)**.
- **Not included**: induction, payroll for training, training cost (food, stay), which stays in Expenses (spec 015) linked to the session **(open)**; course material, assessments and scores, waiting lists, attendance by log-in tracking, and the dashboard tile (spec 028).

## Open Questions for the User

1. **Assignment on open sessions**: may an Admin also assign people to an open weekend refresher, making them Required (default: yes), or are assignments for monthly sessions only?
2. **Group assignment**: a snapshot on the day made (default), or a live rule that follows the group (new joiners added automatically)?
3. **Registered no-show at a weekend refresher**: only shown (default), or also an exception?
4. **Repeat non-compliance**: is "2 in 6 months" right, and should it create a task for the Manager (via spec 025) or only mark the list (default)?
5. **Make-up sessions**: close an exception as "made up" by linking any later session (default), or is a separate make-up session type needed?
6. **Certificates**: is a text note per attendance enough (default), or is an issued, printable certificate needed for some sessions?
7. **Mobile**: web first, with phone screens in a later mobile spec (default), or part of this spec?
8. **Pattern rules**: nth weekday and fixed day of month (default); are weekly or fortnightly patterns needed?
9. **Trainers**: free-text name and contact (default), or a managed list of trainers (and should trainers have a login)?
10. **Training cost**: confirm food and stay for sessions stay in Expenses (015), linked to the session, with no cost kept here (default).
