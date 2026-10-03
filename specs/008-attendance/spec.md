# Feature Specification: Attendance

**Feature Branch**: `008-attendance`

**Created**: 2026-10-03

**Status**: Draft

**Input**: User description: "008-attendance: Attendance module. Teacher: MY ATTENDANCE → My Attendance, Attendance History. Manager: OPERATIONS → Teacher Attendance (assigned Teachers only). Admin/Director: OPERATIONS → Attendance (grid, status codes, non-working calendar, month lock/reopen, monthly rollups, CSV export). Per docs/spec-roadmap.md row 008 and Constitution v2.3.0 (Principles I, II, III, IV, IX): attendance is operational truth and must be audited; Manager scope and Teacher own-scope must come from the shared scope APIs built in spec 005 (organization.api.ScopeQueries, teacher.api.TeacherScopeQueries), never re-implemented. Depends on 005-master-data (Teachers, Schools, placements, scope) and 003-audit. Leave approval of leave and its effect on attendance to the later leave spec (009); payroll consumption to 013. Earlier project documents describe the original attendance requirements and may be used as reference."

## Clarifications

### Session 2026-10-03

- Q: Does every Teacher share one weekly off day (Sunday by default), or can weekly off days differ
  by School? → A: An organization-wide default weekly off day set, which any School can override
  with its own weekly off days. A Teacher's working days follow the School of their placement on
  each date. Specific non-working dates (holidays) remain organization-wide.
- Q: If a Teacher works half a day at school and the other half is leave or training, should that
  day be one mark or two? → A: One mark per Teacher per date. A mixed day records the larger share
  as the status and the rest in a note.
- Q: Should locking a month be refused while some Teachers still have unmarked working days? →
  A: Yes. A month cannot be locked until every working day of every Teacher placed in it is marked;
  the refusal lists the Teachers and days still unmarked. Because future days cannot be marked, a
  month can be locked only after it has ended. The same rule applies when a reopened Teacher-month
  is locked again.
- Q: How far back may a Teacher mark or change their own attendance, as long as the month is not
  locked? → A: Today and the previous 3 days. Older dates in an unlocked month can be marked or
  corrected only by a Manager (for Teachers in scope), Admin or Director.
- Q: Once a Manager, Admin or Director has set or corrected a day, can the Teacher change that day
  again? → A: No. After a supervisor has set a day, only a Manager (in scope), Admin or Director
  can change it. The Teacher sees it as set by their Manager, and can ask them to correct it.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - A Teacher Marks Their Own Attendance (Priority: P1) 🎯 MVP

A Teacher opens MY ATTENDANCE → My Attendance, sees the current month as a calendar, and marks
today or one of the previous 3 days with a status (for example Present, Leave, Training day) and a day value (a whole day or a half
day). The mark is recorded against the School they are placed in on that date.

**Why this priority**: it is the daily, highest-frequency action. Without it there is no attendance
for anything else (rollups, grid, lock, payroll later) to work with.

**Independent Test**: sign in as a placed Teacher, mark today as Present, mark yesterday as a half
day, and confirm both appear on their calendar with the School they were placed in.

**Acceptance Scenarios**:

1. **Given** a Teacher placed in a School who has not marked today, **When** they mark today as
   Present for a whole day, **Then** the mark is saved for today against that School and shown on
   their calendar, attributed to them with the time.
2. **Given** a Teacher who worked half a day, **When** they mark a half-day value, **Then** the mark
   is saved with that value and counts as half in their rollup.
3. **Given** a Teacher who already marked a date, **When** they mark that date again, **Then** it
   becomes a correction of the existing mark (one current mark per Teacher per date), and the
   earlier value stays in the mark's history with who changed it and when.
4. **Given** a Teacher who is not placed in any School on a date, **When** they try to mark that
   date, **Then** the system refuses and explains that attendance needs a School placement.
5. **Given** a Teacher, **When** they try to mark a date in the future, a date in a locked
   month, or a date more than 3 days before today, **Then** the system refuses with the reason (for
   an older date, that their Manager must record it).
6. **Given** a Teacher whose status is exited, **When** they try to mark a date after their exit,
   **Then** the system refuses.
7. **Given** a day that a Manager, Admin or Director has set or corrected, **When** the Teacher
   tries to change it, **Then** the system refuses and explains that their Manager must correct it;
   the calendar shows who set that day.

---

### User Story 2 - A Manager Marks and Corrects Attendance for Their Assigned Teachers (Priority: P1)

A Manager opens OPERATIONS → Teacher Attendance and sees a month grid for the Teachers placed in
their assigned Schools: one row per Teacher, one column per day. They mark a Teacher who could not
mark themselves, or correct an existing mark, directly in the grid.

**Why this priority**: self-marking alone leaves gaps whenever a Teacher cannot mark. This is also
the Manager's main attendance tool and the first place Manager data scope matters for attendance.

**Independent Test**: as a Manager with two assigned Teachers and another Manager's Teacher,
confirm the grid lists only the two, mark a day for one, and confirm the other Manager's Teacher is
unreachable by list or direct request.

**Acceptance Scenarios**:

1. **Given** a Manager with assigned Teachers, **When** they open Teacher Attendance for a month,
   **Then** they see only Teachers placed (on the dates shown) in their assigned Schools.
2. **Given** an unmarked cell for an assigned Teacher, **When** the Manager marks it, **Then** the
   mark is saved against the Teacher's School on that date and attributed to the Manager, not the
   Teacher.
3. **Given** a day the Teacher already marked, **When** the Manager changes it, **Then** the new
   value is current, the earlier value with who set it stays in history, and from then on the
   Teacher can no longer change that day.
4. **Given** a Teacher who is not assigned to this Manager, **When** the Manager tries to read or
   mark that Teacher by any route, **Then** the response is the same as for a Teacher that does not
   exist.
5. **Given** a cell in a locked month, **When** the Manager looks at it, **Then** it is shown as
   locked and cannot be edited; a direct attempt is refused.
6. **Given** a mark for an assigned Teacher, **When** the Manager clears it, **Then** the date
   becomes unmarked and the cleared value stays in history.

---

### User Story 3 - Monthly Rollup per Teacher (Priority: P1)

For any Teacher and month, the system shows a rollup computed from their daily marks: the working
days available in the month, days worked, days of leave, training days available and attended,
unmarked days, and the final weighted attendance total (which can be a fraction such as 21.5).

**Why this priority**: the daily marks have no value to Managers, the Director, or the later payroll
spec until they are rolled up consistently.

**Independent Test**: mark a month with a known mix (whole days, half days, leave, training, a
holiday), and confirm each figure of the rollup matches the hand-computed expectation.

**Acceptance Scenarios**:

1. **Given** a month of marks, **When** the rollup is shown, **Then** it reports working days
   (calendar days minus weekly off days and non-working dates), days worked, days of leave,
   training days attended against training days available, unmarked days, and the final weighted
   total, all consistent with the marks.
2. **Given** a mark is added, changed or removed in an unlocked month, **When** the rollup is shown
   again, **Then** it reflects the change at once.
3. **Given** a working day with no mark, **When** the rollup is shown, **Then** it is counted and
   shown as unmarked, never silently treated as present or absent.
4. **Given** a Teacher placed in different Schools during the month, **When** the rollup is shown,
   **Then** one rollup covers the whole month with each mark still tied to its School.
5. **Given** the rollup is requested, **When** access is checked, **Then** the Teacher sees only
   their own, a Manager only for Teachers in their scope, Admin and Director all, and System none.

---

### User Story 4 - Admin and Director Oversee Attendance in a Month Grid (Priority: P1)

An Admin or Director opens OPERATIONS → Attendance and sees the month grid for all Teachers, can
filter by Zone, School, Manager, or Teacher status, search by name, open any Teacher's rollup, and
mark or correct any cell.

**Why this priority**: it is the organization-wide counterpart of User Story 2 and the screen where
the Director checks that a month is complete before it is locked.

**Independent Test**: open the grid for a month with several Teachers across two Schools, filter by
School, correct one cell, and confirm the correction is attributed to the Admin and shows in the
Teacher's own calendar.

**Acceptance Scenarios**:

1. **Given** Teachers across Zones, **When** an Admin or Director opens the grid, **Then** every
   Teacher placed during that month appears, one column per actual day of the month (28 to 31).
2. **Given** the grid, **When** they filter by Zone, School or Manager, or search by name, **Then**
   only the matching Teachers remain, and the page count and totals follow the filter.
3. **Given** a cell, **When** they mark or correct it, **Then** the same rules apply as for a
   Manager (School from the Teacher's placement on that date, history kept, lock respected).
4. **Given** the grid, **When** they open a Teacher, **Then** they see that Teacher's rollup and
   the history of each mark.
   4a. **Given** a mark, **When** an Admin clears it, **Then** the date becomes unmarked and the
   cleared value stays in history; a Director corrects a day by marking over it.
5. **Given** a user with only Teacher or System access, **When** they try to reach Attendance by
   menu or direct link, **Then** nothing is shown and the route says "not authorized".

---

### User Story 5 - Configure Status Codes and the Non-Working Calendar (Priority: P2)

An Admin or Director maintains the set of attendance status codes (name, short code, category, and
weight) and the non-working calendar: the organization-wide default weekly off day(s), optional
per-School overrides of them, and specific organization-wide dates such as declared holidays. All
of them feed every rollup and grid automatically.

**Why this priority**: the system ships with a working default set (Present, Half-day via value,
Leave, Training day, Non-working), so marking and rollups work from day one; this story lets the
business adapt them without a developer.

**Independent Test**: add a holiday date and confirm every Teacher's working days drop by one for
that month with no individual marks; add a custom code and mark with it.

**Acceptance Scenarios**:

1. **Given** the defaults, **When** a user opens the code list, **Then** Present, Leave, Training
   day and Non-working exist, each with a category (worked, leave, training, non-working) and a
   weight.
2. **Given** an Admin, **When** they add a code with a name, short code, category and weight,
   **Then** it can be used in marks from then on; codes already used in marks cannot be deleted,
   only deactivated.
3. **Given** a declared holiday, **When** an Admin adds the date to the non-working calendar,
   **Then** that date is not a working day for any Teacher, without individual marks; a Teacher
   who has an explicit mark that day keeps it and it counts as worked.
4. **Given** the default weekly off day setting (Sunday by default), **When** it is changed,
   **Then** working days for months that are still unlocked follow the change, while locked months
   keep the values they locked with.
5. **Given** a School that works Monday to Saturday, **When** an Admin gives that School its own
   weekly off days (for example none, or Sunday only), **Then** Teachers placed there have those
   as their off days on dates they are placed there, while Teachers at other Schools keep the
   default; removing the override returns the School to the default.
6. **Given** a Teacher placed in a Monday-to-Saturday School for part of a month and a
   Monday-to-Friday School for the rest, **When** their rollup is shown, **Then** each date's
   working-day status follows the School of the placement on that date.
7. **Given** a Manager or Teacher, **When** they try to change codes or the calendar, **Then** it is
   refused.

---

### User Story 6 - Lock a Month and Reopen It for Corrections (Priority: P2)

An Admin or Director locks a completed month's attendance so it can no longer be changed
directly (for example once the month is verified, and later when payroll is run), and can reopen a
locked Teacher-month with a stated reason for a correction, after which it is locked again. A month
can only be locked when every working day of every placed Teacher is marked.

**Why this priority**: it protects what later payroll will pay against, but marking and rollups are
usable without it.

**Independent Test**: lock a month, confirm a Manager's edit is refused, reopen one Teacher-month
with a reason, correct a mark, re-lock, and confirm the history shows every step.

**Acceptance Scenarios**:

1. **Given** a month that has ended and in which every placed Teacher's working days are marked,
   **When** an Admin or Director locks it, **Then** all Teacher-months in it become locked and the
   rollups are frozen with the values at lock time.
   1a. **Given** a month with Teachers who still have unmarked working days, **When** an Admin or
   Director tries to lock it, **Then** the lock is refused, nothing is locked, and the response lists
   each Teacher and the unmarked days so they can be filled in.
2. **Given** a locked Teacher-month, **When** anyone tries to add, change or remove a mark
   directly, **Then** it is refused with a clear explanation.
3. **Given** a locked Teacher-month, **When** an Admin or Director reopens it with a reason,
   **Then** it becomes editable for that Teacher only, and the reopen (who, when, why) is recorded.
4. **Given** a reopened Teacher-month, **When** corrections are made and it is locked again,
   **Then** the rollup is re-frozen and the history shows the original marks, the reopen, each
   correction, and the re-lock. The re-lock is refused if the correction left a working day
   unmarked.
5. **Given** a Manager or Teacher, **When** they try to lock or reopen, **Then** it is refused.
6. **Given** a lock request for the current or a future month, **When** it is submitted, **Then**
   it is refused; only a month that has ended can be locked.

---

### User Story 7 - A Teacher Reviews Their Attendance History (Priority: P2)

A Teacher opens MY ATTENDANCE → Attendance History to browse earlier months: the calendar with
each day's status and the month's rollup, read-only for locked months.

**Why this priority**: it gives the Teacher transparency over their own record but depends on the
marking and rollup stories existing first.

**Independent Test**: sign in as a Teacher with two earlier months, browse each, and confirm the
calendar and rollup match what the Manager sees for them.

**Acceptance Scenarios**:

1. **Given** a Teacher with marks in earlier months, **When** they open Attendance History and
   choose a month, **Then** they see each day's status, the School it was tied to, and the rollup.
2. **Given** a locked month, **When** the Teacher views it, **Then** it is clearly shown as locked
   and cannot be edited.
3. **Given** a Teacher, **When** they try to view another Teacher's history by any route, **Then**
   the response is the same as for a record that does not exist.
4. **Given** a Teacher whose user has no linked Teacher record, **When** they open My Attendance,
   **Then** they see a message that their profile is not set up, not an error.

---

### User Story 8 - Export a Month's Attendance to CSV (Priority: P3)

An Admin or Director exports a month's attendance to CSV for the Teachers in the current filter:
one row per Teacher with the rollup figures and optionally each day's status.

**Why this priority**: useful for reporting and hand-off, but nothing else depends on it.

**Independent Test**: export a filtered month and compare the file with the grid and rollups.

**Acceptance Scenarios**:

1. **Given** the grid with a filter, **When** an Admin or Director exports, **Then** the file lists
   exactly those Teachers with their rollup figures and per-day statuses for the chosen month.
2. **Given** a Manager, Teacher or System user, **When** they try to export, **Then** it is refused.
3. **Given** any export, **When** it is produced, **Then** the export is recorded in the audit trail
   with who exported what and when.

---

### Edge Cases

- A Teacher is placed in a School for part of a month: only dates inside a placement can be marked;
  other dates are shown as not placed and are not counted as unmarked working days for that Teacher.
- A placement changes mid-month: each mark keeps the School of the placement in effect on its
  date, even if the placement later changes; a corrected placement does not rewrite old marks.
- A Teacher moves to another Manager's School: their marks follow the Teacher; the previous
  Manager no longer sees them and the new Manager sees the whole month.
- Teacher and Manager both mark the same day: a Teacher's mark is current until a Manager, Admin
  or Director sets that day; from then on only supervisors can change it. Every earlier value stays
  in history with who set it.
- A date is both on the non-working calendar and explicitly marked for a Teacher: the explicit mark
  wins for that Teacher.
- A mark uses a custom code that is later deactivated: existing marks stay valid and visible;
  the code simply cannot be chosen for new marks.
- A day split between two statuses (for example half present, half leave): still one mark per
  Teacher per date; it records the larger share as the status and the other share in the note
  (equal halves: the user picks which status is recorded).
- A weekly off day (default or a School override) is changed after months have locked: locked
  months keep their frozen values.
- A Teacher's off day differs between two Schools they were placed in during one month: each date
  follows the School of the placement in effect on that date.
- A month has no placed Teachers: the grid shows an empty state, not an error, and locking it
  succeeds trivially.
- A Teacher left unmarked for the whole month blocks the lock until someone marks the days
  (a Manager, Admin or Director can mark them as Leave or Non-working, whichever is true); the
  lock never guesses a status.
- A Teacher exited mid-month: only the working days up to the exit date must be marked.
- Two people edit the same cell at the same time: the later save is refused with a message that
  the cell changed, instead of silently overwriting.

## Requirements *(mandatory)*

### Functional Requirements

**Marking**

- **FR-001**: A Teacher MUST be able to mark their own attendance for today and the previous 3
  days, provided the date falls in an unlocked month, choosing a status code and a day value (a
  whole day or a half day). Older dates in an unlocked month MUST be markable only by a Manager
  (for Teachers in their scope), Admin or Director (FR-004, FR-005).
- **FR-002**: A mark MUST be tied to the School the Teacher is placed in on that date; the system
  MUST refuse a mark for a date with no placement, for a date after the Teacher's exit, and for a
  future date.
- **FR-003**: There MUST be exactly one current mark per Teacher per date. Marking a date again is
  a correction: the earlier value MUST remain in the mark's history with who set it and when. A
  date can never carry two marks, so a day split between two statuses is one mark plus a note.
  Once a Manager, Admin or Director has set or corrected a date, the Teacher MUST NOT be able to
  change it again.
- **FR-004**: A Manager MUST be able to mark and correct attendance for Teachers in their data
  scope (placed at their assigned Schools), and MUST NOT be able to read or mark any other
  Teacher; an out-of-scope Teacher MUST be indistinguishable from a missing one.
- **FR-005**: Admin and Director MUST be able to mark and correct attendance for any Teacher.
- **FR-006**: A Teacher MUST NOT be able to mark or read any other Teacher's attendance.

**Status codes and calendar**

- **FR-007**: The system MUST provide default status codes — Present, Leave, Training day, and
  Non-working — each with a category (worked, leave, training, non-working) and a weight used in
  the weighted total, and MUST let Admin and Director add codes and deactivate (not delete) codes
  that have been used.
- **FR-008**: The system MUST keep a non-working calendar made of an organization-wide default
  weekly off day set (Sunday by default), optional per-School overrides of the weekly off days, and
  specific organization-wide non-working dates, all maintained by Admin and Director. A Teacher's
  weekly off days on a date MUST follow the School of their placement on that date (its override if
  any, otherwise the default), and MUST apply to their working days without individual marks.
- **FR-009**: An explicit mark on a date that is on the non-working calendar MUST take precedence
  for that Teacher.

**Rollup**

- **FR-010**: For each Teacher and month, the system MUST compute: working days (calendar days
  minus weekly off days for the School of that date's placement and organization-wide
  non-working dates, limited to days the Teacher was placed), days
  worked, days of leave, training days available and attended, unmarked working days, and the
  final weighted attendance total (the sum of day value × code weight over worked and training
  marks), allowing fractions.
- **FR-011**: The rollup MUST reflect any mark added, changed or removed while the month is
  unlocked, and MUST surface unmarked working days instead of treating them as any status.
- **FR-012**: The rollup MUST be visible to the Teacher for themself, to a Manager for Teachers in
  scope, and to Admin and Director for all; System MUST NOT see attendance.

**Grids and history**

- **FR-013**: Manager, Admin and Director MUST get a month grid (one row per Teacher, one column
  per actual day of the month) scoped to their data scope, with search by Teacher name; Admin and
  Director MUST additionally be able to filter by Zone, School, Manager and Teacher status. The
  grid MUST page its rows.
- **FR-014**: A Teacher MUST get a calendar of the current month (My Attendance) and an Attendance
  History browser for earlier months showing each day's status, School, and the month's rollup.
- **FR-015**: A cell in a locked month MUST be shown as locked and not editable, and any direct
  attempt MUST be refused.

**Lock and reopen**

- **FR-016**: Admin and Director MUST be able to lock a month that has ended, which locks every
  Teacher-month in it and freezes each Teacher's rollup with the values at lock time. The system
  MUST refuse to lock a month that has not ended, or in which any placed Teacher has an unmarked
  working day, locking nothing and listing the Teachers and days that are unmarked.
- **FR-017**: The system MUST reject any direct add, change or removal of a mark in a locked
  Teacher-month.
- **FR-018**: Admin and Director MUST be able to reopen a locked Teacher-month with a stated
  reason for corrections and lock it again; the reopen, every correction and the re-lock MUST be
  recorded and retrievable in order. A re-lock MUST be refused while the Teacher-month has an
  unmarked working day.
- **FR-019**: Locking and reopening MUST be refused for Manager, Teacher and System.

**Export, audit, and shared contract**

- **FR-020**: Admin and Director MUST be able to export a month for the current filter to CSV
  (one row per Teacher with rollup figures and per-day statuses).
- **FR-021**: Every mark, correction, lock, reopen, code or calendar change, and export MUST be
  recorded in the audit trail with the actor, time, and previous and new values; no module in this
  spec writes its own audit tables.
- **FR-022**: Concurrent edits to the same mark MUST NOT silently overwrite each other; the later
  save is refused with a clear message.
- **FR-023**: The system MUST offer later modules (leave, payroll, reports) one stable way to read
  a Teacher-month's marks, rollup and lock state, so they do not recompute attendance themselves.
- **FR-024**: All lists and grids MUST support search, pagination, and consistent loading, empty
  and error states, and every form MUST show inline validation; dates are DD/MM/YYYY.

### Key Entities

- **Attendance Mark**: one Teacher's current status for one date — the status code, the day value
  (whole or half), the School of the placement in effect that date, an optional note, who last set
  it and when. Its earlier values are kept as history.
- **Attendance Status Code**: a name and short code with a category (worked, leave, training,
  non-working), a weight, and an active flag; defaults are provided and extendable.
- **Non-Working Calendar**: the default weekly off day(s), any per-School override of them, and the
  list of specific organization-wide non-working dates with a short description.
- **Teacher-Month**: the state of one Teacher's attendance for one month — open or locked, the
  frozen rollup values when locked, and the history of lock, reopen (with reason) and re-lock.
- **Monthly Attendance Rollup**: the computed per-Teacher, per-month figures; live while open,
  frozen while locked.

## Role & Permission Impact *(mandatory — Constitution Principles II–IV)*

| Role     | Menu (section → item) | Default actions | Data scope |
| -------- | --------------------- | --------------- | ---------- |
| Admin    | OPERATIONS → Attendance; Attendance Setup (status codes, non-working calendar) | All, including lock/reopen (Process) and Export | Org-wide |
| Director | OPERATIONS → Attendance; Attendance Setup | View, Create, Edit, Process (lock/reopen), Export | Org-wide |
| Manager  | OPERATIONS → Teacher Attendance | View, Create, Edit for Teachers in scope; no Process, Export or Setup | Assigned (Zones → Schools → Teachers) |
| Teacher  | MY ATTENDANCE → My Attendance, Attendance History | View, Create, Edit own marks in unlocked months | Own |
| System   | none | none | None (System MUST NOT see teacher or school business data) |

**New permission keys**: modules `ATTENDANCE` (Admin and Director: `VIEW`, `CREATE`, `EDIT`,
`PROCESS`, `EXPORT`; Admin also `DELETE`), `TEACHER_ATTENDANCE` (Manager: `VIEW`, `CREATE`, `EDIT`
within scope), `MY_ATTENDANCE` (Teacher: `VIEW`, `CREATE`, `EDIT` own), and `ATTENDANCE_SETUP`
(Admin and Director: `VIEW`, `EDIT`). All false for the other roles; all runtime-editable. Only
Admin, Director and System may edit the role→permission matrix; this spec does not change that.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A Teacher can mark today's attendance in under 15 seconds from opening My Attendance.
- **SC-002**: A Manager can review a full month of attendance for their whole team on one screen and
  correct a mark in under 30 seconds, without exporting to a spreadsheet.
- **SC-003**: For a month marked with a known mix of statuses, 100% of the rollup figures match the
  hand-computed values, including half days and fractional totals.
- **SC-004**: In testing with at least two Managers with different assignments, 100% of grids,
  rollups, marks and exports return only the caller's data; no leaked record is found.
- **SC-005**: 100% of marks, corrections, locks, reopens, setup changes and exports appear in the
  audit trail within 5 seconds with actor, time, and previous and new values.
- **SC-006**: 100% of direct changes to a locked Teacher-month are refused, and 100% of changes to
  it after a reopen are traceable to the reopen and its reason.
- **SC-007**: The month grid for 500 Teachers loads its first page in under 3 seconds and a CSV
  export for the same filter completes in under 30 seconds.
- **SC-008**: Every attendance screen is usable by keyboard alone and passes the same accessibility
  checks as earlier screens in both light and dark themes.

## Assumptions

- Attendance is captured in the responsive web application. A native mobile app, offline capture
  with later sync, and optional evidence (geo-tag, photo, school check-in code) from the earlier
  requirements are deferred to a later spec; this version records status, value, School and an
  optional note.
- A mark is tied to the School of the Teacher's placement in effect on that date, read through the
  spec 005 placement data. The Teacher–School placement is still the interim assignment and will be
  replaced later by the school contract without changing the attendance rule.
- A Manager's and a Teacher's scope comes only from the shared scope APIs delivered in spec 005
  (`ScopeQueries`, `TeacherScopeQueries`); this spec adds no scoping of its own. Because that scope
  follows each Teacher's current placement, a Teacher's whole month follows them to a new Manager.
- Approval workflows are out of scope: a Teacher's mark is the record, and the Manager, Admin or
  Director corrects it afterwards with history. Leave requests and their approval, and the effect of
  approved leave on attendance, belong to the later leave spec; here "Leave" is only a status a
  user may mark.
- Training days are marked with the Training status; the training calendar, sessions and
  registration belong to the later HR calendars spec. "Training days available" for a month is the
  number of dates marked as training for that Teacher; until that spec exists, available equals
  attended.
- Weights default to Present = 1, Training = 1 (counted in the total and shown separately), Leave =
  0, Non-working excluded. A half-day mark has value 0.5. A day split between two statuses is
  recorded as one mark with the larger share and a note.
- Lock and reopen are manual Admin/Director actions in this spec. When the payroll spec arrives it
  will lock Teacher-months as part of a payroll run through the same mechanism, and will read
  attendance only through the stable read contract (FR-023).
- CSV only; PDF export is not part of this version. Salary, margin and payslips belong to the
  payroll spec.
- Specs 002 (access model), 003 (audit), 004 (users) and 005 (master data) are implemented and
  reused as-is. Dates are shown as DD/MM/YYYY.
