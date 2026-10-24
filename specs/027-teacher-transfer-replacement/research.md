# Research: Teacher Transfer and Replacement

Decisions taken while planning, with what was read in the code. Format: Decision, Rationale, Alternatives.

## 1. What the code offers today

- Placements are `contract_assignment` rows (module `schoolbilling`: teacher, school, optional position, `starts_on`,
  `ends_on`, status ACTIVE, CANCELLED or CORRECTED). `teacher.api.TeacherPlacementSource` is the port (owned by `teacher`,
  implemented by `schoolbilling`). "Current" is always evaluated against a date; there is no scheduler.
- `AssignmentService.assign` moves or schedules one Teacher. It checks status (not EXITED, not IN_TRAINING), an active School,
  picks or validates a position (`resolvePosition`), ends the old row on the day before, inserts the new row, and relies on
  the exclusion constraints (`ex_assignment_position_dates` and the per-Teacher overlap) for no double fill. A move on the same
  date as the old row's start marks the old row CORRECTED. `cancelPending` removes one scheduled move; `endForExit` ends a row.
- There is no operation that ends a placement without starting another, and none that changes two Teachers as one step.
- `assign` runs under `TEACHERS` `EDIT` with no reason, approval or request record; Zone Managers can already move Teachers in
  scope immediately.
- Attendance: `attendance_mark` has one row per (teacher, date) with `school_id`; leave marks approved ahead of time exist as
  real marks (`LeaveAttendance.apply`). `AttendanceReadApi` exposes `marksOf`, `teachersAwayOn` and `isLocked(teacher, month)`.
- Substitution: requirements are unique per (teacher, duty date), carry `school_id`, statuses OPEN, ASSIGNED, MANAGER_COVER,
  CANCELLED, and publish `SubstitutionNeeded` and `SubstitutionEnded`.
- Notifications: modules publish events from their `api` package; `notification` has one listener per source module.
- Managers: `organization.api.ScopeQueries` and `ManagerQueries` give a Manager's Schools and a School's Manager; Zone Manager
  here means a Manager whose scope covers the School.
- Migrations on main stop at V53; V60 is planned by 026.

## 2. Where the logic lives: a `transfer` module over a `PlacementMoves` port

**Decision**: a new `transfer` module owns requests, approval rules, history and notifications. Placement rows are written only
by `schoolbilling`, through a new interface `schoolbilling.api.PlacementMoves` with `check(MoveSpec)` (dry run, returns plain
problems) and `apply(actor, MoveSpec)` (writes in the caller's transaction, returns the ids of rows it ended and created) and
`reverse(actor, AppliedMove)` (cancels rows still in the future and restores the open end of the outgoing row). The
implementation reuses the private rules of `AssignmentService` (contract lookup, `resolvePosition`, flush and constraint
translation) by being a method group in the same package.
**Rationale**: the position and overlap rules must stay in one place so a request can never accept what a direct move would
refuse, and the module boundary (Principle VII) forbids `transfer` from reading placement tables. One `check` used by raise,
approve and the form's live check removes drift between the three.
**Alternatives**: call `TeacherPlacementSource.assign` twice from `transfer` (not atomic for two Teachers, cannot end a row,
cannot keep the same position for a replacement); put the whole feature inside `schoolbilling` (mixes approvals and
notifications into a billing module, and `teacher` could not read it without a cycle).

## 3. Applying a move: rows written at approval, effective by date

**Decision**: on approval `apply` writes the dated rows at once (outgoing row `ends_on = effective - 1`; new row
`starts_on = effective`), whether the date is today or later. A future date is therefore a scheduled move exactly as today's
direct route schedules one. The request then shows Approved until the date, and Applied from the date (derived, not stored by
a job). Cancelling a still-future Approved request calls `reverse`.
**Rationale**: attendance and leave read placement by date, so marks written after approval for dates from the effective date
automatically carry the new School; no scheduler is needed (the existing design principle). Doing nothing at approval and
applying later would need a job that must not fail, and would leave a window where marking for the future date is refused.
**Alternatives**: apply on the effective date by a job (fragile, and the spec says status is derived from the date).

Rules inside `apply`:
- Transfer: the moving Teacher's current row ends the day before; a new row starts on the date at the receiving School. The
  position is the named one, or `resolvePosition` with none (PER_TEACHER contracts require a named position; "MoU pending"
  gives none).
- Replacement: the outgoing row ends the day before; the replacement gets a row starting on the date at the same School and the
  same `position_id` (null when the School has no positions). The exclusion constraint accepts it because the ranges touch but
  do not overlap. The outgoing Teacher gets a further row at the named destination School from the date, or none.
- The effective date must be after the outgoing row's `starts_on` (a Teacher placed today cannot be moved today); this avoids
  the CORRECTED path of `moveNow`, which would hide history.
- A Teacher who already has a scheduled move (a future ACTIVE row) is refused: one scheduled move at a time.

## 4. Who may be moved or named

**Decision**: moving or outgoing Teacher: status ACTIVE or ON_LEAVE (not IN_TRAINING, not EXITED) and placed at the named School
on the request date and on the effective date. Replacement or destination-bound Teacher: ACTIVE with no ACTIVE row covering the
effective date and no future row. Receiving School: active, with a vacant position on the date when its contract has positions
(counted as `SchoolContracts.occupancyOf` on the date, ignoring the moving Teacher's own position for a same-School move).
**Rationale**: spec FR-005, edge cases. `ON_LEAVE` is allowed because leave is exactly when a School may ask for a change.
**Alternatives**: refuse ON_LEAVE (blocks the common case).

## 5. Attendance and leave

**Decision**: a new `attendance.api.PlacementAttendance` with
`boolean hasNonLeaveMarkOnOrAfter(teacherId, date)`, `boolean anyMonthLocked(teacherId, fromDate)` (the effective month and the
later months that already hold marks), and `int retagLeaveMarks(teacherId, from, newSchoolId, actor)` that changes `school_id` on
the Teacher's leave-category marks dated on or after `from`, writing an `attendance_mark_history` row for each (action
CORRECTED) in the caller's transaction. Marks before `from` are never touched.
**Rationale**: FR-004 and FR-010 need exactly these three facts; `AttendanceReadApi` has `isLocked` per month but nothing for
"any non-leave mark after a date" and no way to change the School of a mark. Re-tagging at approval, not at the effective date,
keeps it in the same transaction as the placement and needs no job. Marks made after approval take the School of the placement
row for that day, which is already the new one.
**Alternatives**: iterate `marksOf` over many months from `transfer` (reads attendance data across the boundary, slow, and
cannot write); leave days stay with the old School (breaks FR-010 and payroll School tagging).

## 6. Substitution requirements

**Decision**: a new `substitution.api.PlacementSubstitution.relocate(actor, teacherId, from, newSchoolId, reason)`. For each
non-cancelled requirement of the Teacher with `duty_date >= from` it sets the School to the new one; an ASSIGNED requirement
loses its substitute (history entry, `SubstitutionEnded` to the substitute) and returns to OPEN; `SubstitutionNeeded` is
published to the new School's Manager. When `newSchoolId` is null (a replaced Teacher who becomes available) the requirements
are cancelled with reason "Teacher moved". This is the spec's "cancelled and raised again", done as one row because
requirements are unique per (teacher, date) and the existing `AbsenceListener` already reopens and retags in place.
**Rationale**: keeps history in the substitution module's own tables and reuses its notification events.
**Alternatives**: cancel then raise a second row (violates the unique key; the listener would reopen the same row anyway).

## 7. Approvers, self-decision and scope

**Decision**: `APPROVE` on `TEACHER_TRANSFERS`, eligible for Director and Manager only. The server then checks, in order:
grant; request visible to the caller; caller is not the requester; for a Manager, the leaving School is in their scope (the
receiving School alone is not enough); version matches. Admin lacks the grant, so cannot reach approve.
**Rationale**: D5 and FR-007; one place (`MovementScope`) for read and write scope so lists and endpoints cannot disagree.
**Alternatives**: a role check in code instead of the matrix (breaks the configurable-permission principle).

## 8. Request lifecycle and "Applied"

**Decision**: stored statuses PENDING, APPROVED, REJECTED, CANCELLED. APPLIED is derived: APPROVED with `effective_on <= today`
and all linked rows still ACTIVE. If a linked row is no longer ACTIVE (a direct change replaced it), the request is stale: the
daily `MovementEffectJob` closes it as CANCELLED with "Teacher no longer placed there", and until then reads show it as
Cancelled. The same job, once per request, sends the "took effect" notice and writes an APPLIED history entry; if it fails or
is late, nothing else changes. A Pending request whose effective date has passed can only be approved after its date is changed
to today or later (a `PATCH` by the requester or a decider, version-checked).
**Rationale**: matches spec User Story 5 scenario 3 and the edge cases; correctness never depends on the job.
**Alternatives**: a stored APPLIED set by the job (a late job would show a wrong status).

## 9. One open request per Teacher

**Decision**: a partial unique index on `movement_participant (teacher_id)` where the request is PENDING, with one participant row
per Teacher involved (moving, outgoing, replacement, and the outgoing Teacher's destination is the same Teacher). The check also
runs in `MovementChecks` for a readable message; the index closes the race.
**Rationale**: FR-006 across all three roles of a Teacher in a request cannot be a single column index.
**Alternatives**: three nullable columns with three partial indexes (cannot stop a Teacher being outgoing in one and
replacement in another).

## 10. Teacher list filters Transferred and Replaced

**Decision**: `teacher.api.TeacherMovementSource` with `Set<UUID> transferred()` and `Set<UUID> replaced()` (Teacher ids), implemented
by `transfer` and used by the Teacher list as `movement=TRANSFERRED|REPLACED`, still within the caller's scope. Transferred is a
Teacher with an Approved or Applied transfer request, or two or more ACTIVE placement rows at different Schools (so direct moves
show too, "without a request"). Replaced is a Teacher who was the outgoing Teacher of an Approved or Applied replacement.
**Rationale**: D3 views are derived (spec assumption); `teacher` cannot depend on `transfer`, so the port is owned by `teacher`.
**Alternatives**: store a status on the Teacher (rejected: they are views, and a stored status would drift).

## 11. History reads

**Decision**: Teacher history = the placement rows from `TeacherPlacementSource.historyOf` (each row starts a line) joined to the
request that created it by `movement_assignment.assignment_id`; rows with no request show "Initial assignment" or "Direct move".
School history = the same join over the School's rows plus the request. Rejected and cancelled requests appear only in the
request list. The Teacher's own profile keeps its existing placement history.
**Rationale**: FR-014 and Story 4; no duplicated placement data.
**Alternatives**: copy placement lines into a movement table (duplicates and can drift).

## 12. Notifications

**Decision**: three events from `transfer.api` (`TransferRequested`, `TransferDecided`, `TransferApplied`) and one listener in
`notification` with types `TRANSFER_REQUESTED`, `TRANSFER_DECIDED`, `TRANSFER_APPLIED` (no table change; V50 already widened the
type column). Recipients: deciders = Director users plus Managers whose scope covers the leaving School (excluding the
requester); on decision, the requester plus, on approval, the Teachers' own users and the Managers of both Schools; on
effect, the same set.
**Rationale**: existing pattern; the Teacher is told, as the spec requires.

## 13. What is not decided here

Pay, billing, exit, SMS and mobile stay out of scope. Payroll's monthly divisor behaviour in a transfer month is unchanged
(spec assumption); this spec only guarantees consecutive non-overlapping spans.

### Names confirmed at implementation (T001)

- `teacher.api.TeacherPlacementSource` (`historyOf`, `currentOf`, `spansOverlapping`, `teachersAssignedDuring`), `schoolbilling.internal.AssignmentService` (`assign`, `cancelPending`, `endForExit`; `contractFor` and `resolvePosition` made package-private for reuse by `PlacementMovesImpl`), `SchoolContracts.occupancyOf`.
- `attendance_mark`, `attendance_mark_history`, `attendance.internal.TeacherMonthLock.isLocked`; `substitution.api.SubstitutionNeeded` and `SubstitutionEnded`.
- `organization.api.ScopeQueries` and `ManagerQueries`, `school.api.ChangeRecorder`, `designation.api.BusinessDate`.
- Highest migration on `main` was V60 (026); 035 took V70, so this spec uses **V80**. REST base path follows the repo convention `/api/v1/transfers` (the contract says `/api/transfers`).
