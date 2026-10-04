# Phase 0 Research: Attendance

## 1. Module boundaries: `attendance -> teacher -> organization -> school`

**Decision**: a new `attendance` module reads Teachers, placements and Schools only through public
APIs. `teacher.api` gains `TeacherDirectory` (read-only): `teacherInfo(ids)`,
`teacherOfUser(userId)`, `placementsOverlapping(teacherIds, from, to)` (school per date range), and
`teachersPlacedDuring(from, to)` (ids with an ACTIVE placement overlapping a month). Scope comes from
`organization.api.ScopeQueries` and `teacher.api.TeacherScopeQueries`; School and Zone names from
`school.api.SchoolDirectory`; Manager names from `organization.api.ManagerQueries`. The audit
publisher `school.api.ChangeRecorder` is reused (it is the shared master-data/ops recorder).

**Rationale**: Principle VII and spec 005's explicit promise that later modules reuse the scope APIs.
No new SPI is needed in the opposite direction: nothing in `teacher`/`organization`/`school` needs
attendance (a Teacher with marks can still exit; marks keep their ids).

**Alternatives considered**: reading `teacher_placement` through SQL (rejected: breaks module
boundary); adding attendance fields to the Teacher module (rejected: Principle VII lists
`attendance` as its own context).

## 2. One current mark per Teacher per date plus an append-only history

**Decision**: `attendance_mark` holds the current mark with a unique `(teacher_id, mark_date)`;
`attendance_mark_history` receives one row for every create, correction and clear (status, value,
School, note, `set_by_user_id`, `set_by_kind` SELF/SUPERVISOR, `set_at`, action). Clearing a mark
(supervisors only) deletes the current row and writes a CLEARED history row, so the date is
unmarked again but nothing is lost.

**Rationale**: FR-003 (one current mark, history retained) and a simple unique constraint that makes
"two marks for one day" unrepresentable. Change History (audit) is the cross-cutting trail; the mark
history table is the domain-level answer to "show me every value of this day" without joining audit.

## 3. School on a mark is a snapshot of the placement that date

**Decision**: when a mark is created or corrected, `school_id` is resolved from
`TeacherDirectory.placementsOverlapping` for that date and stored on the mark. Later placement
changes do not rewrite it. Marking a date with no placement is refused (spec FR-002).

**Rationale**: spec edge cases (marks keep the School of their date) and payroll's need to know where
a day was worked. The rollup, however, recomputes *working days* from placements at read time
(section 4) while the lock freezes the result (section 7).

## 4. The rollup is a pure calculator with explicit definitions

**Decision**: `RollupCalculator.compute(month, placementsByDate, marksByDate, codes, settings,
nonWorkingDates, today)` returns `{workingDays, daysWorked, daysLeave, trainingAvailable,
trainingAttended, unmarked, weightedTotal}`:

- A date is **in play** for a Teacher only if a placement covers it (and it is not after the Teacher's
  exit).
- A date is a **working day** if in play and not a weekly off day for the *placement's School* (its
  override else the default) and not an organization-wide non-working date; **or** if the Teacher has
  an explicit WORKED or TRAINING mark on it (explicit mark wins, FR-009: it becomes a working day and
  counts). An explicit NON_WORKING mark removes a date from working days.
- `daysWorked` = sum of day values of WORKED marks on working days; `daysLeave` = sum of LEAVE marks;
  `trainingAttended` = sum of TRAINING marks; `trainingAvailable` = dates the Teacher has a TRAINING
  mark (the training calendar arrives with spec 016, so it equals attended for now).
- `weightedTotal` = sum of `dayValue * code.weight` over WORKED and TRAINING marks.
- `unmarked` = working days with no mark whose date is on or before today (future days are not
  "unmarked"; they are "not yet").

**Rationale**: matches the spreadsheet semantics (26 working days, fractional totals) and the
spec's FR-010/FR-011. A pure function with injected inputs is trivially table-testable (Principle IX
calculation logic first).

**Alternatives considered**: SQL aggregation (rejected: placements, per-School weekly offs and
precedence rules are clearer and safer in Java at this scale); storing the rollup on every write
(rejected: staleness bugs; compute live, freeze at lock only).

## 5. Calendar model

**Decision**: `attendance_calendar_setting` has one default row (`school_id` null) and optional
per-School rows, each with a set of weekly off weekdays; `attendance_non_working_date` holds
organization-wide dates with a description. Resolution for a date and School: School override if
present else default. Changing weekly off days affects only months that are not locked (locked
months use the frozen rollup).

**Rationale**: spec clarification Q1 (default plus per-School override, dates org-wide). Storing
weekdays as a small text set (`MON,SAT`) keeps the table trivial; a bitmask would add nothing at this
scale.

## 6. Authority: self vs supervisor marks, and the 3-day window

**Decision**: each mark and history row carries `set_by_kind`. Rules enforced in
`MarkService`: a Teacher may create/correct only their own mark, only for today and the previous 3
business days, only if the current mark (if any) has `set_by_kind = SELF`; Manager (in scope), Admin
and Director may mark/clear any unlocked date inside a placement, and their writes set
`SUPERVISOR`. Unknown or out-of-scope Teacher ids return 404 before any rule is evaluated.

**Rationale**: spec clarifications Q4 and Q5 and FR-001/003/004/005; `set_by_kind` makes the
Teacher-protection rule a one-line check and a visible attribute on the calendar.

## 7. Locking, reopen and concurrency

**Decision**: `attendance_teacher_month` rows exist only once a Teacher-month has been locked
(absence means open). Lock month = for every Teacher placed during the month, verify all working
days up to the month end are marked (else 409 listing Teachers and dates, nothing locked), then in
one transaction insert LOCKED rows with the frozen rollup values and write LOCKED events. Reopen
(reason required) flips one row to OPEN, records a REOPENED event; relock re-validates the
unmarked rule, recomputes and refreezes, and writes RELOCKED. A month can be locked only if its last
day (Asia/Kolkata) has passed.
To keep a concurrent mark from slipping between validation and lock, every mark write, lock,
reopen and relock for a Teacher-month takes `pg_advisory_xact_lock(hashtextextended(teacherId ||
':' || yearMonth, 0))` first and re-reads the lock state under it.

**Rationale**: FR-016..FR-018, SC-006 ("100% refused"). A row-less open state avoids creating
Teacher-month rows for every Teacher every month; an advisory lock gives a lock on something that
does not yet exist, which a row lock cannot.

**Alternatives considered**: a Teacher-month row created lazily on first mark (rejected: needless
writes and still needs locking); optimistic checks only (rejected: the validate-then-lock window is
a genuine race).

## 8. Permissions, navigation and scope entries

**Decision**: modules `ATTENDANCE` (Admin/Director: VIEW, CREATE, EDIT, PROCESS, EXPORT; Admin also
DELETE), `TEACHER_ATTENDANCE` (Manager VIEW, CREATE, EDIT), `MY_ATTENDANCE` (Teacher VIEW, CREATE,
EDIT), `ATTENDANCE_SETUP` (Admin/Director VIEW, EDIT). Navigation: OPERATIONS -> "Attendance"
(`/operations/attendance`) for Admin/Director; MASTER DATA -> "Attendance Setup"
(`/master-data/attendance-setup`) for Admin/Director and "Holiday Calendar"
(`/master-data/holiday-calendar`) for everyone;
OPERATIONS -> "Teacher Attendance" (`/operations/teacher-attendance`) for Manager; MY ATTENDANCE ->
"My Attendance" (`/my-attendance`), "Attendance History" (`/my-attendance/history`) for Teacher. The
access model reports `ATTENDANCE` org-wide, `TEACHER_ATTENDANCE` assigned, `MY_ATTENDANCE` own.
A user holding several roles gets the union of grants (existing rule).

**Rationale**: Constitution default matrix and the spec's Role & Permission Impact table; four
modules keep each screen's menu item and grants independent and runtime-editable.

## 9. Business dates use Asia/Kolkata, not UTC

**Decision**: `BusinessCalendar` wraps the application `Clock` with `ZoneId.of("Asia/Kolkata")`
(configurable via `hls.business-timezone`) and is the only source of "today" in `attendance`. The
3-day window, "no future dates", "month has ended" and "unmarked up to today" all use it.

**Rationale**: the application `Clock` is `Clock.systemUTC()`; between 00:00 and 05:30 IST the UTC
date is still yesterday, so an Indian Teacher marking early in the morning would be refused or
mis-dated. **Follow-up flagged**: spec 005's placement and scheduling logic still uses the UTC date;
the same helper should replace it in a small dedicated change (not part of this spec's scope, but a
single day of drift there is possible near midnight IST).

## 10. Grid composition and scope

**Decision**: one `AttendanceGridService` builds a grid page: (1) resolve the allowed Teacher set -
Admin/Director: all Teachers placed during the month; Manager: `TeacherScopeQueries` ids intersected
with those placed during the month; (2) apply filters (Zone/School/Manager/status/name) using the
placement data and `ManagerQueries`; (3) page; (4) load marks, placements and settings for just that
page in bulk and compute rollups with the calculator. The Manager endpoint and the Admin/Director
endpoint share the service and differ only in permission module and filter set.

**Rationale**: SC-007 (no per-Teacher queries) and Principle III (one scope source). A Manager
sees Teachers in their *current* scope, so a Teacher who moved away takes their month with them.

## 11. CSV export

**Decision**: `GET /api/v1/attendance/export` streams CSV through the same service in page-sized
batches, with one row per Teacher (name, status, rollup figures, then one column per day with the
short code or blank), honoring the grid filters. A single `EntityChanged` (`ATTENDANCE_EXPORT`)
records who exported which month and filter.

**Rationale**: FR-020, FR-021, SC-007; streaming keeps memory flat.

## 12. Read contract for later modules

**Decision**: `attendance.api.AttendanceReadApi` exposes `rollupOf(teacherId, yearMonth)` (frozen if
locked, live otherwise, with a `locked` flag), `marksOf(teacherId, yearMonth)` and
`isLocked(teacherId, yearMonth)`. Payroll (spec 013) will read through it and will lock
Teacher-months through the same lock service (a `lockTeacherMonth(teacherId, yearMonth, actor)`
method added then).

**Rationale**: FR-023 and the spec's Assumptions; later modules must not recompute attendance.

## 13. Status codes and seeding

**Decision**: the four defaults (PRESENT category WORKED weight 1, LEAVE category LEAVE weight 0,
TRAINING category TRAINING weight 1, NON_WORKING category NON_WORKING weight 0) are inserted by the
migration with `system = true`; they can be renamed and re-weighted but not deleted or
deactivated. Custom codes can be added and, once used, only deactivated. Code `shortCode` is unique
case-insensitively.

**Rationale**: FR-007; shipping working defaults means marking works from the first run.

## 14. Audit visibility and entity types

**Decision**: new `entity_type` values `ATTENDANCE_MARK`, `ATTENDANCE_MONTH`, `ATTENDANCE_CODE`,
`ATTENDANCE_CALENDAR`, `ATTENDANCE_EXPORT`. `AuditVisibility` maps `ATTENDANCE_MARK`,
`ATTENDANCE_MONTH` and `ATTENDANCE_EXPORT` to `ATTENDANCE.VIEW`, and `ATTENDANCE_CODE` and
`ATTENDANCE_CALENDAR` to `ATTENDANCE_SETUP.VIEW`, so System (no attendance grants) never sees them.

**Rationale**: Constitution Principle II (System sees no business data), consistent with spec 005
section 16.

## 15. Frontend

**Decision**: one `AttendanceGrid` component (table, sticky Teacher column, one cell per day showing
the code's short letter and a half-day mark, locked/not-placed/off-day variants with text and an
accessible name, not colour alone) used by the Manager and Admin/Director pages; a month picker
(DD/MM/YYYY labels) shared with Teacher pages; `MarkDialog` for status, value and note; a side
`TeacherMonthPanel` with the rollup, mark history and, for Admin/Director, lock/reopen controls. Lock
month is a toolbar action that lists unmarked Teachers/days when refused.

**Rationale**: spec User Stories 2, 4, 7 and the accessibility success criterion; one grid avoids two
diverging implementations.
