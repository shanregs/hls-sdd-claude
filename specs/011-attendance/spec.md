# Feature Specification: Daily Attendance Capture & Monthly Rollup

**Feature Branch**: `011-attendance`

**Created**: 2026-09-22

**Status**: Draft

**Input**: User description: "Build the daily attendance capture and monthly rollup system that replaces the "STAFF ATTENDANCE REGISTER" spreadsheet. Teachers mark their own daily attendance from the mobile app (optionally with geo-tag/photo/check-in code), or a Manager marks it on their behalf; attendance is tagged to a specific school assignment and supports half-day (fractional) values. The system must support at least Present, Leave, Training-day, and a non-working-day status as a configurable set of codes, and automatically compute per-teacher monthly rollups: training days total vs. attended, days worked, days leave, overall working days, and final weighted attendance total. Every mark and edit must be attributable to who made it and when. Monthly attendance locks once payroll runs for that month, with an explicit re-open/correction workflow rather than silent edits (Requirements §3; Constitution Principle I, II)."

## Clarifications

### Session 2026-09-22

- Q: Is a non-working day (e.g., a declared holiday) applied automatically to every Teacher from a shared calendar, or does each Teacher (or their Manager) still have to individually mark that date as non-working? → A: Shared calendar, auto-applied — an Admin configures non-working dates once; every Teacher's rollup automatically excludes those dates, no per-teacher mark needed (an explicit mark on that date, if one exists, still takes precedence — see Edge Cases).
- Q: How many days back can a Teacher or Manager still mark or edit attendance for a past date, once that date's month hasn't locked yet? → A: Any day within the current calendar month — no fixed trailing-day limit; the only boundary is whether that date's teacher-month has locked (FR-011), not how many days ago it was.
- Q: Which role(s) can add new attendance status codes to the configurable set beyond the required four? → A: Admin or Director (both, not Admin-only).
- Q: Who should be able to view a Teacher's attendance evidence (geo-tag, photo, check-in code) once it's submitted? → A: Same as the mark itself — no extra restriction beyond FR-015's existing viewing scope (Director, the Teacher's accountable Manager, and the Teacher themself).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Teacher Marks Their Own Daily Attendance (Priority: P1)

A Teacher opens the mobile app and marks their attendance for a given day against their current school assignment, choosing a status (Present, Leave, Training-day, a half-day/fractional value, or another configured code), optionally attaching a geo-tag, photo, or check-in code as supporting evidence.

**Why this priority**: This is the daily, highest-frequency action the entire feature exists to capture — without it, there is no attendance data for anything downstream (rollups, payroll, audit) to work with.

**Independent Test**: Can be fully tested by having a Teacher mark today's attendance from the mobile app and confirming the mark is saved, tagged to their current school assignment, and immediately retrievable with the exact status and any attached evidence.

**Acceptance Scenarios**:

1. **Given** a Teacher has not yet marked today's attendance, **When** they select "Present" and submit, **Then** a mark is saved for today, tagged to their current school assignment, attributed to the Teacher, timestamped, and immediately retrievable.
2. **Given** a Teacher worked only half the day, **When** they mark attendance with a half-day (fractional) value, **Then** the mark is saved with that fractional value and reflected as such in later rollups.
3. **Given** a Teacher wants to attach evidence, **When** they optionally add a geo-tag, a photo, or a check-in code before submitting, **Then** the mark is saved together with whichever evidence was provided.
4. **Given** a Teacher has already marked attendance for a day, **When** they attempt to mark that same day again before the month is locked, **Then** the system treats this as an edit to the existing mark (preserving the prior value in history) rather than creating a second, conflicting mark for the same day.

---

### User Story 2 - Manager Marks Attendance on a Teacher's Behalf (Priority: P1)

A Manager marks attendance for a Teacher who is currently accountable to them, on a day the Teacher did not (or could not) mark it themselves — for example when a Teacher has no working phone or missed the window — recording the same status codes and fractional values available to the Teacher.

**Why this priority**: Self-marking alone leaves gaps whenever a Teacher can't use the app; without a Manager fallback, this feature can't fully replace the spreadsheet, which never had that gap.

**Independent Test**: Can be fully tested by having a Manager mark attendance for a Teacher currently assigned to them and confirming the mark is saved, tagged to that Teacher's school assignment, and attributed to the Manager (not the Teacher) with a timestamp.

**Acceptance Scenarios**:

1. **Given** a Teacher currently accountable to a Manager has not marked a given day, **When** the Manager marks attendance for that Teacher and day, **Then** the mark is saved, tagged to the Teacher's school assignment, and attributed to the Manager as the person who made it.
2. **Given** a Teacher is not currently accountable to a Manager, **When** that Manager attempts to mark attendance for the Teacher, **Then** the action is denied.
3. **Given** a Teacher already marked a day themselves, **When** their Manager subsequently changes that day's mark, **Then** the prior value (including that it was originally entered by the Teacher) remains retrievable as history, and the new value is attributed to the Manager.

---

### User Story 3 - Automatic Monthly Rollup Per Teacher (Priority: P1)

At any point in the month, the system automatically computes, for each Teacher, a monthly rollup from that Teacher's daily marks: total training days on the calendar vs. training days actually attended, days worked, days on leave, overall working days for the month, and a final weighted attendance total.

**Why this priority**: This is the core deliverable the spreadsheet's manual monthly summary currently exists to produce — the daily marks in Stories 1-2 have no operational value to Payroll, Managers, or the Director until they're rolled up.

**Independent Test**: Can be fully tested by marking a full month of attendance for a Teacher with a known mix of statuses and confirming the computed rollup's five figures (training total vs. attended, days worked, days leave, overall working days, weighted total) exactly match what that mix should produce.

**Acceptance Scenarios**:

1. **Given** a Teacher's attendance marks for a month, **When** the rollup is computed, **Then** it reports training days total vs. attended, days worked, days leave, overall working days, and a final weighted attendance total that are all consistent with the underlying marks.
2. **Given** a Teacher has unmarked days in an otherwise-complete month, **When** the rollup is computed, **Then** those unmarked days are visibly called out rather than silently counted as any particular status.
3. **Given** an attendance mark for the month is added, changed, or removed before the month is locked, **When** the rollup is next viewed, **Then** it reflects the update — it is never a stale, one-time snapshot while the month remains open.
4. **Given** the Director, a Teacher's accountable Manager, or the Teacher themselves requests the rollup, **When** access is checked, **Then** the Director and the accountable Manager can view it, the Teacher can view only their own, and no one else can.

---

### User Story 4 - Monthly Lock and Explicit Re-open/Correction (Priority: P2)

Once payroll has run for a given month, that month's attendance locks against further direct edits. If a correction is later needed, an authorized user explicitly reopens that teacher-month through a correction workflow — never by silently editing the locked data — and the reopen, the correction, and the re-lock are all recorded.

**Why this priority**: Protects the financial integrity Stories 1-3 build up — without this, a mark could be changed after payroll already paid against it with no trace, which is exactly the silent-edit risk this feature must not reintroduce.

**Independent Test**: Can be fully tested by locking a teacher-month, confirming a direct edit attempt is rejected, then running the reopen workflow and confirming the correction succeeds and is fully recorded (who reopened it, why, what changed, and when it was re-locked).

**Acceptance Scenarios**:

1. **Given** payroll has run for a teacher-month, **When** that run completes, **Then** the teacher-month locks and further direct edits to its attendance marks are rejected.
2. **Given** a locked teacher-month, **When** any user attempts to directly add, change, or delete a mark within it, **Then** the attempt is rejected with a clear explanation that the month is locked.
3. **Given** a locked teacher-month needs a correction, **When** an authorized user reopens it with a stated reason, **Then** the teacher-month becomes editable again, the reopen (who, when, why) is recorded, and the rollup is marked as having been corrected post-payroll.
4. **Given** a reopened teacher-month has had its corrections made, **When** it is re-locked, **Then** the full sequence — original marks, the reopen, every correction, and the re-lock — remains retrievable as history.

---

### User Story 5 - Grid View of a Month's Attendance Across Teachers, Editable in Place (Priority: P2)

A Director, Manager, or Admin selects a month and sees a single grid — one row per Teacher, one column per calendar day of that month — with each cell showing that Teacher's status for that day (Present, Leave, Training-day, Non-working, or a clear "unmarked" indicator), the same at-a-glance shape as the paper/spreadsheet register this feature replaces. A Manager (for their own accountable Teachers) or an Admin (for any Teacher) can also correct a cell directly in the grid — the same underlying mark as US1/US2, just edited from this multi-teacher view instead of one Teacher at a time.

**Why this priority**: Individual marking (US1/US2) and per-teacher rollups (US3) are what actually captures and computes attendance correctly — the grid (read or edited) is a convenience layered on top of data those stories already fully own, valuable for day-to-day oversight and bulk correction but not blocking the feature's core capture/audit/payroll-input guarantees.

**Independent Test**: Mark a mix of attendance for several Teachers across a month, request the grid for that month, confirm each Teacher's row shows the correct status per day, then edit one cell and confirm the change is saved, attributed, and immediately reflected in the grid.

**Acceptance Scenarios**:

1. **Given** several Teachers with attendance marked for a month, **When** a Director requests the grid for that month, **Then** it shows one row per Teacher and one column per calendar day in that month, each cell showing that day's status.
2. **Given** a Manager requests the grid, **When** access is checked, **Then** only Teachers currently accountable to that Manager appear as rows — never a Teacher outside their portfolio (consistent with FR-015).
3. **Given** a day within the grid has no attendance mark for a Teacher, **When** the grid is rendered, **Then** that cell clearly shows "unmarked" rather than defaulting to any status (consistent with FR-010).
4. **Given** a Director wants to narrow the grid to one Manager's team, **When** they filter by Manager, **Then** only that Manager's currently-accountable Teachers appear as rows.
5. **Given** a Manager or Admin is viewing the grid, **When** they edit a cell for a Teacher/day they're authorized to mark (the Manager's own portfolio, or any Teacher for Admin), **Then** the change is saved the same way as the single-mark endpoint — attributed to that Manager/Admin, audited, and immediately reflected in that cell.
6. **Given** a cell falls within a teacher-month that is currently locked, **When** a Manager or Admin attempts to edit it in the grid, **Then** the cell is shown as non-editable and the edit is rejected, consistent with FR-012 — never a silent bypass of the lock.

---

### Edge Cases

- What happens when a Teacher's school assignment changes partway through a month? Each mark stays tagged to the school assignment that was current when it was made; the monthly rollup aggregates all of that Teacher's marks for the month regardless of how many assignments they span.
- What happens when both the Teacher and their Manager try to mark the same day? The most recent mark wins as the current value; every prior value remains retrievable as history with who made it and when.
- What happens on a shared-calendar non-working day (e.g., a declared holiday)? It's automatically excluded from overall working days, days worked, and days leave for every Teacher, with no per-teacher mark required — but it remains a visible entry on the calendar. If a Teacher (or their Manager) has an explicit mark on that same date (e.g., the school stayed open and the Teacher actually worked), that explicit mark takes precedence over the calendar for that Teacher's day.
- What happens when a Teacher attempts to mark a date far in the past or the future? A date can be marked or edited as long as its teacher-month has not locked yet (FR-011) — there is no separate trailing-day limit; a date whose teacher-month is locked is rejected regardless of how recent it is (FR-012), and a date too far in the future (beyond the current teacher-month) is rejected.
- What happens if a payroll run is triggered while a teacher-month still has unmarked days? The rollup surfaces those gaps; locking does not silently treat an unmarked day as any particular status.
- What happens when an evidence attachment (geo-tag/photo/check-in code) fails to capture or upload? The attendance mark itself can still be saved without evidence; evidence is supporting detail, not a precondition for recording a status.
- What happens for a month with fewer than 31 days (28/29/30)? The grid's column count matches the actual number of days in the selected month, never a fixed 31.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST allow a Teacher to mark their own daily attendance from the mobile app, tagged to their current school assignment.
- **FR-002**: The system MUST allow a Manager to mark daily attendance on behalf of a Teacher currently accountable to that Manager, tagged to that Teacher's current school assignment, and MUST deny this action for a Teacher not currently accountable to that Manager.
- **FR-003**: The system MUST allow an attendance mark to optionally include a geo-tag, a photo, and/or a check-in code as supporting evidence, and MUST allow the mark to be saved without any of them.
- **FR-004**: The system MUST support fractional (e.g., half-day) attendance values in addition to whole-day values.
- **FR-005**: The system MUST support, as a minimum, four attendance status codes — Present, Leave, Training-day, and a non-working-day status — managed as a configurable set that an Admin or Director can extend with additional codes.
- **FR-006**: The system MUST record, for every attendance mark and every subsequent edit to it, who made it and when, and MUST preserve every prior value as retrievable history rather than overwriting it.
- **FR-007**: The system MUST automatically compute, per Teacher per month, a rollup consisting of: total training days vs. training days attended, days worked, days on leave, overall working days for the month, and a final weighted attendance total.
- **FR-008**: The system MUST recompute a teacher-month's rollup to reflect any mark added, changed, or removed while that teacher-month remains unlocked.
- **FR-009**: The system MUST exclude non-working days from days worked, days leave, and overall working days, while still surfacing them on the attendance calendar. A date on the shared non-working calendar (FR-022) applies automatically to every Teacher without requiring an individual mark, except where a Teacher has an explicit mark of their own for that date, which takes precedence.
- **FR-010**: The system MUST distinguish, within a teacher-month's rollup, any day that has no attendance mark and is not on the non-working calendar (FR-022) from a day explicitly marked with a status, and MUST surface such unmarked days rather than treating them as any particular status by default. A day covered by the non-working calendar is never counted as unmarked, even without an individual mark.
- **FR-011**: The system MUST lock a teacher-month's attendance against direct edits once a payroll run completes for that teacher-month. Since locking stands in for an actual payroll run (no `payroll` module exists yet — Assumptions), triggering it is restricted to the Director, the same role FR-014 restricts reopening to — Constitution Principle II's restriction of payroll-approval-adjacent authority away from the System Assistant/Admin role applies to both ends of the lock/reopen cycle, not just reopening (`/speckit-analyze` finding C1/I1).
- **FR-012**: The system MUST reject any direct add, edit, or delete of an attendance mark within a locked teacher-month.
- **FR-013**: The system MUST provide an explicit reopen/correction workflow that allows an authorized user to reopen a locked teacher-month with a stated reason, make corrections, and re-lock it, with the reopen, every correction, and the re-lock all recorded as retrievable history.
- **FR-014**: The system MUST restrict who can reopen a locked teacher-month to a role with payroll-correction authority (Director), consistent with Constitution Principle II's restriction of payroll approval authority away from the System Assistant/Admin role.
- **FR-015**: The system MUST allow a Director to view any Teacher's attendance marks and monthly rollup, MUST allow a Manager to view them only for Teachers currently accountable to that Manager, and MUST allow a Teacher to view only their own. This scope applies uniformly to a mark's evidence (geo-tag, photo, check-in code) too — no narrower visibility rule applies to evidence than to the mark it belongs to.
- **FR-016**: The system MUST allow mobile attendance capture to work with temporary offline storage, syncing marks (and any attached evidence) once connectivity is restored, consistent with the Constitution's offline-readiness requirement.
- **FR-017**: The system MUST make each teacher-month's rollup available to the Payroll module as the authoritative input for computing that Teacher's monthly salary, replacing the manual "STAFF ATTENDANCE REGISTER" spreadsheet summary.
- **FR-018**: The system MUST support exporting a Teacher's or a school's attendance and rollup data (e.g., CSV/PDF), consistent with the Constitution's export requirement.
- **FR-019**: The system MUST provide a grid view of a selected month's attendance across multiple Teachers at once — one row per Teacher, one column per calendar day in that month, each cell showing that day's status (or a clear unmarked indicator).
- **FR-020**: The system MUST scope the grid's Teacher rows the same as FR-015 — a Director sees any Teacher (optionally narrowed to one Manager's portfolio), a Manager sees only Teachers currently accountable to them, and MUST NOT allow a Manager's grid to include a Teacher outside their portfolio.
- **FR-021**: The system MUST allow a Director to filter the grid to a single Manager's currently-accountable Teachers.
- **FR-022**: The system MUST allow an Admin to configure a shared, organization-wide calendar of non-working dates, applied automatically to every Teacher's rollup and grid (FR-009) without requiring an individual mark per Teacher.
- **FR-023**: The system MUST allow marking or editing any date whose teacher-month has not yet locked, regardless of how many days in the past that date is, and MUST reject a date beyond the current teacher-month (in the future).
- **FR-024**: The system MUST allow an Admin to mark or edit attendance on behalf of any Teacher, without requiring the accountability relationship FR-002 requires of a Manager.
- **FR-025**: The system MUST allow a Manager or Admin to edit an attendance mark directly from the grid view (User Story 5), for any Teacher/day they are authorized to mark under FR-002/FR-024, persisting through the same write path as the single-mark endpoints (FR-001/FR-002/FR-024) — same attribution and audit trail (FR-006), same lock enforcement (FR-012), with a locked cell shown as visibly non-editable rather than silently rejected only after the fact.

### Key Entities *(include if feature involves data)*

- **Attendance Mark**: One Teacher's recorded status for one calendar day — the status code, the fractional value (e.g., 1.0, 0.5), the school assignment it's tagged to, optional evidence (geo-tag, photo, check-in code), who made or last edited it, and when. Superseded values remain retrievable as history rather than being deleted.
- **Attendance Status Code**: A configurable status a mark can carry (Present, Leave, Training-day, a non-working-day status, and any additional codes an administrator defines), each with a category (worked / leave / training / non-working) and a default weight used in the weighted attendance total.
- **Monthly Attendance Rollup**: The computed, per-Teacher, per-month summary — training days total vs. attended, days worked, days leave, overall working days, and final weighted attendance total — derived from that Teacher's Attendance Marks and recomputed whenever they change while the teacher-month is unlocked.
- **Teacher-Month Lock**: The locked/unlocked state of one Teacher's attendance for one month, including when and by which payroll run it locked, and the full history of any reopen (who, when, why), corrections made while reopened, and subsequent re-lock.
- **School Assignment** *(reference, owned by other modules)*: The Teacher-to-school relationship an Attendance Mark is tagged to; Attendance reads this rather than owning it.
- **Attendance Grid** *(derived, not persisted; editable)*: A month's attendance for a set of Teachers, arranged as rows (Teachers) × columns (calendar days), each cell a status (or unmarked) — composed at read time from Attendance Marks and the same accountability scoping as the per-Teacher rollup. A Manager or Admin can edit a cell in place (FR-025); the edit writes the same underlying Attendance Mark US1/US2 write, the grid itself holds no separate state.
- **Non-Working Calendar**: An Admin-configured, organization-wide set of dates (e.g., declared holidays) that automatically count as non-working for every Teacher's rollup and grid, without an individual Attendance Mark — unless a Teacher has their own explicit mark for that date, which overrides the calendar for that Teacher only.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A Teacher can mark a full day's attendance, including any optional evidence, in under 30 seconds from opening the mobile app.
- **SC-002**: 100% of attendance marks and edits are attributable to a specific person and timestamp, retrievable on demand.
- **SC-003**: A Teacher's monthly rollup reflects the latest marks within 1 minute of the most recent change, for every month that is still unlocked.
- **SC-004**: 100% of changes to a locked teacher-month occur through the reopen/correction workflow, with zero instances of a locked mark changing without a recorded reopen.
- **SC-005**: Producing a Director-ready monthly attendance summary for the full teacher roster takes under 5 minutes of user effort, down from the multi-hour manual spreadsheet process it replaces.
- **SC-006**: Managers and Teachers report the mobile marking experience as easy to use in at least 90% of post-rollout feedback.
- **SC-007**: A Director or Manager can review a full month's attendance across their entire team in a single screen, without exporting to or opening a spreadsheet.

## Assumptions

- **Weighted attendance total**: each Attendance Status Code carries a configurable weight (defaulting to Present = 1.0, half-day = 0.5, Leave = 0, Training-day counted separately from the "worked" weight, non-working days excluded from the denominator); the final weighted attendance total is the sum of (fractional value × code weight) across the month's marks. An Admin or Director can adjust these defaults per code (FR-005).
- **Check-in code**: a short, school- or session-specific code a Teacher enters as evidence of on-site presence, functioning as a lightweight alternative to geo-tag/photo capture where device capability or connectivity is limited.
- **"School assignment"**: this feature reads a Teacher's current school assignment as reference data from wherever it's currently authoritative (the interim direct Teacher-to-Manager/School assignment described in Constitution Principle II, pending the formal Teacher-School-Manager Contract); Attendance does not own or duplicate that assignment data.
- **Lock and reopen authority**: both locking a teacher-month (FR-011) and reopening one (FR-013) are restricted to the Director, since Constitution Principle II reserves payroll approval authority away from the System Assistant/Admin role and locking stands in for an actual payroll run (research.md §2) — the same authority boundary applies at both ends of the cycle, not only at reopen. A Manager or Admin may request a lock or a reopen, but only the Director can trigger either.
- **Lock granularity**: locking applies per Teacher per month (a "teacher-month"), matching how payroll is run per Teacher, rather than locking an entire school or the whole organization for a month at once.
- **Non-working day scope**: a non-working day (whether from the shared calendar, FR-022, or an individual `NON_WORKING` mark for a one-off exception) represents a day that doesn't count as worked, leave, or training for a given Teacher; it is distinct from a Teacher simply having no mark and no calendar coverage for a day (FR-010).
- **Existing dependencies**: this feature depends on Teacher master data (`specs/005-teacher`) and organization/manager scoping (`specs/003-organization-scoping`, `specs/006-zone-scoping`) already existing, and supplies its output to a future Payroll module rather than computing salary itself.
- **Grid filter is by Manager, not Zone/School**: FR-021 narrows the Director's grid filter to "one Manager's portfolio" rather than Zone/School, since there is no queryable "Teacher's current School" data yet (see the School Assignment key entity above) — filtering by Manager uses accountability data that already exists. A future spec can widen this to Zone/School once that data exists.
- **Grid editing is Manager + Admin, not Director**: FR-024/FR-025 add Admin as a new, unscoped on-behalf marker (alongside Manager's existing scoped one, FR-002) so the grid can be corrected in place — Director's role stays what FR-014/FR-015 already gives it (unscoped viewing, reopen authority) rather than gaining a new general attendance-editing capability. If Director-editing is wanted later, it's a small, additive extension of FR-024's pattern, not a redesign.
