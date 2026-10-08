# Research: Leave Management

Decisions that the plan rests on. Format: Decision, Rationale, Alternatives considered.

## 1. How leave writes into attendance

**Decision**: `attendance.api.LeaveAttendance`, a new named interface implemented inside
`attendance.internal`, with four operations: `workingDays(teacherId, from, to)` (dates, School of
placement, day kind), `check(teacherId, days, approverKind)` (locked months and supervisor-set days),
`applyLeave(requestId, approverUserId, teacherId, days)` and `removeLeave(requestId, actorUserId)`.
Leave calls them inside its own transaction, so approval is atomic with the status change.

**Rationale**: Attendance owns marks, the calendar, month locks and the rollup rules; leave must not
re-implement any of them (Constitution VII, spec 008's read-contract intent). A synchronous call in
one transaction gives all-or-nothing without events or compensation.

**Alternatives**: (a) Event-driven (`LeaveApproved` consumed by attendance): asynchronous, so a
locked-month refusal could not fail the approval. (b) Leave calling `MarkService` per day: exposes
internals and loses the single pre-check across all days.

## 2. Tagging leave-made marks

**Decision**: nullable `leave_request_id` on `attendance_mark` and `attendance_mark_history`.
`MarkService.setMark` (every non-leave write) sets it to null. Revoke deletes only marks whose
`leave_request_id` still equals the request, so a day anyone has since changed by hand is left alone
without comparing timestamps.

**Rationale**: One cheap column answers both "show where this L came from" and "has this been touched
since". History rows keep the request id even after the mark changes.

**Alternatives**: Compare `set_at` to the approval time (fragile with clock skew); a separate link table
(a join per grid cell for no gain).

## 3. Future days and the 3-day window

**Decision**: leave uses a dedicated `setLeaveMark` path that skips the "no future date" and
"self-mark window" checks but keeps: month not locked, a placement covering the date, an active
status code, per-Teacher-month advisory lock. Marks are `SUPERVISOR` kind attributed to the approver,
so the Teacher cannot change a leave day afterwards (they cancel the leave instead).

**Rationale**: The spec requires future days to carry L. Everything else about marking stays
unchanged and tested.

## 4. Which days are "working days"

**Decision**: reuse `RollupCalculator.plan(...)` (placements per date, per-School weekly off days,
organization non-working dates, explicit marks) and take the days whose kind is a working day with a
placement. Days with no placement are not working days. Half-day flags apply to the **first and last
working day** of the range (not the literal first/last calendar day); on a one-working-day range at
most one flag may be set, and either gives 0.5.

**Rationale**: One definition of a working day for rollups and leave. Applying the flag to the
first/last working day avoids a half-day on a Sunday silently doing nothing.

**Alternatives**: Literal first/last date (a Sunday start would drop the flag); a separate leave
calendar (two truths).

## 5. Where "today" and dates come from

**Decision**: leave asks attendance for the business date (`LeaveAttendance.businessToday()`), which
wraps the Asia/Kolkata `BusinessCalendar`. The 30-day lookback and "first day in the future" tests
use it.

**Rationale**: Same IST/UTC boundary issue as spec 008 research section 9.

## 6. Preventing overlapping requests

**Decision**: a PostgreSQL exclusion constraint on `leave_request` using `btree_gist`:
`EXCLUDE USING gist (teacher_id WITH =, daterange(first_date, last_date, '[]') WITH &&) WHERE
(status IN ('PENDING','APPROVED'))`, plus a service pre-check that names the clashing request.
Violations map to a 409 with the same message. `btree_gist` is already installed (placements use it).

**Rationale**: Two simultaneous submissions cannot both pass a service check; the constraint is the
guarantee. Cancelled and Rejected requests drop out of the constraint automatically.

**Alternatives**: Advisory lock per Teacher only (works, but the constraint also protects any future
writer).

## 7. Concurrency of decisions

**Decision**: decisions load the request with a pessimistic write lock (`FOR UPDATE`), check
`status = PENDING`, then take the Teacher-month advisory locks (ascending month order), run the
attendance check and apply, and save. Teacher cancel takes the same row lock first, so approver and
Teacher cancel serialize; the loser sees the status and gets 409 with a clear message. The `@Version`
field additionally guards the screens' stale views.

**Rationale**: Row lock first, month locks second is a single global order, so no deadlock with
attendance writes (which only take month locks).

## 8. Revoke and Teacher cancel semantics

**Decision**: both call `removeLeave(requestId, actor)`, which deletes marks with the request id,
writes `CLEARED` history entries, and returns the removed and the left-alone dates. A locked month
anywhere among the marks it would remove refuses the whole operation. The earlier mark a leave
replaced (for example the Teacher's own Present) is **not** restored; the History shows it and the
Teacher or a supervisor re-marks if needed.

**Rationale**: Restoring needs a snapshot of pre-leave state and invites stale restores; the history
already preserves the information.

## 9. Permission model

**Decision**: `LEAVE_MANAGEMENT(VIEW, APPROVE)` eligible for Admin, Director, Manager;
`MY_LEAVE(VIEW, CREATE, DELETE)` eligible for Teacher only. Approve covers approve, reject and
revoke. The Teacher's "own" scope is derived from the signed-in user via `TeacherDirectory.teacherOfUser`
(as `MyAttendanceController` does).

**Alternatives**: Separate `REJECT`/`REVOKE` actions (the action set is fixed and eight-way
granularity helps nobody); a shared module for both audiences (their scopes differ).

## 10. Leave types

**Decision**: a small `leave_type` table seeded with Casual, Sick, Personal, Other (active flag, sort
order), read-only in this spec; every type maps to the attendance status code `L`.

**Rationale**: A table costs one migration now and avoids an enum change when types become editable
or map to different codes (for example paid vs unpaid) in the payroll spec.

## 11. Navigation placement

**Decision**: new **LEAVE** section for Teacher (items Apply Leave, My Leave History) directly after
MY ATTENDANCE; **OPERATIONS → Leave Management** for Admin, Director, Manager. Section order stays
server-driven by the catalog's `order` values; `NavigationSectionOrderTest` is extended.

## 12. Demo seed

**Decision**: `LeaveDevSeeder` (runs with the demo flag, idempotent by a fixed marker reason prefix)
creates, for Tara and the three attendance demo Teachers: one Pending future request (Pongal-style
travel), one Approved past request whose L marks are written through `LeaveAttendance` (a family
function, 2 days), and one Rejected request with a reason (a medical appointment that clashed with
exams). It runs after the attendance seeder.

## 13. Audit mapping

**Decision**: entity type `LEAVE_REQUEST` mapped in `AuditVisibility` to `LEAVE_MANAGEMENT` VIEW, so
Admin and Director (and Manager within the existing scope filter) see it in Change History. Leave
marks use the existing `ATTENDANCE_MARK` entity type with the request id in the detail.

## Amendment A3: how payroll recognises Loss-of-Pay

**Decision**: the type has the stable code `LOP`; `leave.api.LeaveTypes.lossOfPayRequests(ids)` answers which leave
request ids are Loss-of-Pay. **Why**: attendance marks already carry `leave_request_id` and are removed when a request
is cancelled, rejected or revoked, so "a mark with a Loss-of-Pay request id" is exactly an approved Loss-of-Pay day,
with no cross-module table access and no new column or event. **Alternative rejected**: a per-day flag on the
attendance mark (needs an attendance change and would duplicate the type).
