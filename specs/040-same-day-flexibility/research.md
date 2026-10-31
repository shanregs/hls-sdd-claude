# Research: Same-Day Flexibility Without Prior Approval

Each section: **Decision**, **Rationale**, **Alternatives considered**. Code facts below were read from the current
`main`.

## D1. Every Teacher leave day is unpaid at the payroll run

**Decision**: In `UnpaidDayClassifier.classify`, drop the condition that a LEAVE-category mark must be unbacked or
Loss-of-Pay. Every LEAVE-category mark on a payable date is an unpaid day, valued 1 or the policy half-day fraction.
`PayRulesImpl.teacherUnpaid` stops calling `leaveTypes.lossOfPayRequests`. The classifier is only reached for Teachers
(`teacherUnpaid`); the Manager path (`managerUnpaid`, spec 032) is untouched, which keeps the Assumption "Manager
attendance keeps its own unpaid-day rules".

**Rationale**: Today the classifier reads the mark's *category* and the request's Loss-of-Pay flag. The spec makes the
type a reason only, so the flag stops mattering. Doing it in the one pure class means one place to test (SC-003) and no
change to what `PayRules` returns. The `leave_type.loss_of_pay` column and `attendance.apply(..., lossOfPay)` argument
stay; they are harmless and removing them would widen the change into spec 008/009 data.

**Check before tasks**: that no other LEAVE-category status exists that is meant to be paid (for example a paid holiday
code). The category list is in `attendance`'s status-code seed; the task list includes reading it and, if one exists,
narrowing the rule to the Leave and Absent codes. Marks made by **training** and **holiday** are other categories.

**Alternatives**: a new "deductible" flag on each leave type (rejected: the spec says the type decides nothing); keeping
Loss-of-Pay for approved leave and deducting only same-day leave (rejected: FR-003 and the clarification say every day).

## D2. A request that runs past today is split into two linked requests

**Decision**: When a Teacher (or a recorder) submits a range that **starts today** and ends later, `LeaveRequestService`
creates two `leave_request` rows in one transaction: today's part Approved with `same_day = true`, and the later part
Pending, normal flow. The later part carries `linked_request_id` pointing at today's part; the view of either shows
"Part of a request that also covers ..." with a link. A half-day *start* flag goes to today's part and a half-day *end*
flag to the later part. A range that **starts before today** is not same-day and follows the 30-day look-back rules
unchanged.

**Rationale**: The exclusion constraint `ex_leave_request_no_overlap`, the state machine (`PENDING -> APPROVED/REJECTED/
CANCELLED`) and `LeaveCounter` all assume one status per request. A single request with a half-approved status would
change all of them. Two rows reuse every existing rule; cancelling or rejecting the later part leaves today's leave
standing, which is what FR-005 asks for.

**Alternatives**: one request with per-day statuses (rejected: touches every list, the counter and the attendance
feed); refusing a range that includes today (rejected by FR-005).

## D3. Who may record same-day leave, and how a Teacher's own call changes

**Decision**: The Teacher's own `POST /api/v1/me/leave` is unchanged in shape. After validation, if the first date equals
`BusinessDate.today()` the service takes the same-day path (D2 when the range is longer). For a recorder, a new
`POST /api/v1/leave/same-day` takes `teacherId` and the same draft fields; it needs the existing **Approve** action on
`LEAVE_MANAGEMENT` (the module has only View and Approve; Admin, Director and Manager already hold Approve), checks `LeaveScope` (Manager: assigned Teachers; Admin and Director: all) and answers "not found"
outside scope. Both paths run the same `evaluate` plus the same-day rules and write the recorder as
`created_by_user_id` and `decided_by_user_id`, with `decision_note = "same-day, auto-accepted"`.

**Rationale**: `created_by_user_id` and `decided_by_user_id` already hold "who recorded it" and "who accepted it"; no new
column is needed beyond the flag. The existing preview endpoint keeps working and now tells the user "takes effect at
once".

**Alternatives**: letting a Manager create leave through `/me/leave` (rejected: that path resolves the Teacher from the
JWT user); a separate "approve on submit" setting (rejected: not asked for, and no caps or configuration are wanted).

## D4. Replacing an Absent mark, and the guard in scenario 6

**Decision**: `LeaveAttendance.problems` today refuses any day with a *supervisor-set* mark that is not L. Scenario 8
needs an Absent mark (set by a Manager earlier the same day) to be replaced. Add a parameter `replaceAbsent` that is true
only for the same-day path: a supervisor-set mark whose status code is **Absent** is not a problem; any other
supervisor-set non-L mark (Present, Half day, Training, and so on) still refuses with the reason. Applying the leave
writes the new mark through `markService.setLeaveMark`, which already records the old value in the mark history. Payroll
counts the date once because classification is by date (D1).

**Rationale**: This keeps scenario 6 true in spirit (a Manager who set Present cannot be overridden by a Teacher's
self-service) while meeting scenario 8. Both are literal in the spec; they differ only in *which* non-L status.

**To settle at `/speckit-analyze`**: scenario 6 says "a status other than L" without exception; scenario 8 is the
exception. The plan reads them as "other than L and Absent".

**Alternatives**: allow replacing any status (rejected: a Teacher could erase a Present mark); require the Manager to
clear the Absent mark first (rejected: scenario 8 says the mark becomes leave).

## D5. Two people recording at once

**Decision**: No new lock. The partial exclusion constraint on live `leave_request` rows already makes the second insert
fail; `saveAndFlush` is wrapped as in `submit` (`DataIntegrityViolationException` or
`PessimisticLockingFailureException`) and the loser is told "same-day leave already exists for this date" with a link to
it. For the split case, today's part is inserted first so the loser fails before a Pending part is created, all in one
transaction.

**Rationale**: SC-006 (exactly one succeeds) is what the constraint already guarantees; the test proves it.

## D6. Manager cover: no new table

**Decision**: The "Manager substitution entry" is a `substitution_requirement` row in status `MANAGER_COVER` with its
`cover_user_id`, and its `substitution_history` rows. `cover()` already works from `OPEN` and from `ASSIGNED`, ends the
previous substitute and writes `COVERED`. Changes:

1. `cover` sets `same_day = true` when `BusinessDate.today()` equals the duty date (recorded on the day, including after
   the class). A cover recorded for a past duty date is not refused (end-of-day entry is the case; later days follow
   whatever limits spec 017a already has) and carries `same_day = false`.
2. `GET /api/v1/substitutions/manager-covers?from=&to=&managerId=&schoolId=&sameDay=` lists the entries, labelled "Manager
   cover", in scope. It is a separate list from Teacher credits (`SubstituteCredits`), so nothing is added to a Teacher's
   credit or pay (FR-008).
3. FR-009 needs no code: `assign` and `release` already move the status and keep the history; the list reads "ended"
   from the history.
4. FR-016 and the "leave later withdrawn" edge case are covered by `AbsenceListener.onWithdrawn`, which cancels the
   requirement and keeps history.
5. When an Admin or Director records the cover, `coverUser` resolves to the School's Manager (existing code), so the
   entry is attributed to that Manager. If the School has no Manager the existing behaviour (null cover user) applies and
   the entry shows "no Manager recorded".

**Alternatives**: a dedicated `manager_substitution` table (rejected: duplicates the requirement and risks the two
disagreeing).

## D7. Tasks, visits and observation; what "final" means; offline

**Decision**:

- **Tasks**. `logSameDay` creates a task with `source = "SAME_DAY"`, `assignee = caller`, `dueOn = today`, `sameDay = true`,
  and writes it as `IN_PROGRESS`, or as `VERIFIED` with history rows `CREATED`, `COMPLETED`, `VERIFIED` all by the
  Manager (note "same-day, auto-accepted"). Awaiting verification lists `COMPLETED` only, so it never appears.
  `markDoneOnTheSpot(taskId)` does the same for an open task assigned to the caller whose `dueOn` is today (acknowledge
  and start are skipped; the overdue-reason rule is not applied because the task is not overdue); the original assigner
  stays in `created_by`.
- **Completion note**: the existing `complete` requires a note of at least 10 characters. Same-day completion keeps that
  rule (a note is the record of what was done); In progress needs none.
- **Observation (FR-012)**: logging a same-day OBSERVATION linked to a Teacher calls the existing
  `TaskService.completeLinked(OBSERVATION, linkedType, linkedId, assigneeUserId, note)` for any open observation due,
  so the due is satisfied as when a planned observation is submitted. The full observation *form* of spec 026 is not
  repeated here; a same-day observation task is a task record. (Whether the form's ratings are also wanted is listed
  under D8.)
- **Visits**. A visit is a `marketing_activity` of kind SCHOOL_VISIT (spec 023; the mobile visits of spec 034 use the same
  service). `ActivityService.logSameDay` creates it already `COMPLETED` with date today, outcome and notes, and
  `same_day = true`. The marketing activity has no In-progress state, so a visit is logged as **Completed only**; this
  narrows FR-010 for visits and is consistent with how the activity calendar shows them. In progress remains available
  for tasks.
- **Today only (FR-013)**: the server sets the date from `BusinessDate.today()`; the request carries none. Another Manager
  cannot be named: the assignee is the JWT user.
- **Offline (FR-017)**: the mobile outbox (spec 036) stores the item with the **device-captured business date** and a
  client-generated idempotency key. Spec 036 has no retention window or idempotency store in the code, so this feature
  defines both: the server accepts `capturedOn` for a same-day offline item only if it is not in the future and not
  more than **7 days** old; it then records `same_day = true` and keeps that date as the activity date. Otherwise the item
  is returned as a conflict (`DATE_NOT_ALLOWED`) for the user to resolve. Idempotency is a nullable unique `client_key`
  column on `task` and `marketing_activity` (each module de-duplicates its own rows; no shared store, so no module
  boundary is crossed). A repeated send with the same key returns the first record.

**Rationale**: Reusing `VERIFIED` as "final, accepted" avoids a new status that every list, filter and tile would have to
learn; the spec's own words are "Completed, accepted" and "not in Awaiting verification".

**Alternatives**: a new status `COMPLETED_SAME_DAY` (rejected: more states to maintain for no behavioural difference);
keeping `COMPLETED` and hiding it from the verification queue by flag (rejected: every other COMPLETED consumer would
need the same exception).

## D8. Open points for `/speckit-analyze`

1. Scenario 6 and scenario 8 differ on which non-L status blocks same-day leave (D4).
2. Whether the spec 026 observation *form* (ratings) is also expected for a same-day observation or only the task record
   and the satisfied due (D7). The plan assumes the latter, which matches the spec text "logs ... a note".
3. Whether any paid LEAVE-category code exists (D1).

## D9. Reporting and the same-day filter

**Decision**: Each owning module adds a small public `SameDayCounts` interface (`count by manager`, `count by school` for a
month and optional Zone) implemented over its own repository. `reporting` adds `GET /api/v1/reports/same-day?month=
&zoneId=` that calls the interfaces the caller is allowed to see (Leave, Substitution, Manager Tasks, Visits View), and
returns per-Manager and per-School rows. A `sameDay=true|false` query parameter is added to the existing list endpoints
(leave management, substitutions, tasks, marketing activities, activity calendar). The audit trail needs no new filter:
the audit event detail carries `sameDay=true`, and the existing Change History search matches on detail text.

**Rationale**: Principle VII (reporting owns no data and reads public APIs) and the "no new permission key" rule.

**Alternatives**: one SQL view across modules (rejected: crosses module tables).

## D10. Notifications

**Decision**: Same-day leave publishes the existing `LeaveRequested` and `LeaveDecided` (Approved) events so the Manager
and the Teacher are notified by the current listeners; no new notification type. A Manager's own cover or task creates no
notification to themselves.

## D11. Prerequisites verified (T001-T003, 2026-10-28)

- Found as planned: `LeaveRequestService` (`submit`, `evaluate`, `cancelOwn`), `LeaveDecisionService` (`approve`, `revoke`), `LeaveAttendance` (`businessToday`, `workingDays`, `problems`, `apply`, `remove`), `SubstitutionService` (`cover`, `assign`, `release`), `AbsenceListener`, `TaskService` (`complete`, `verify`, `completeLinked`), `UnpaidDayClassifier`, `PayRulesImpl.teacherUnpaid`. The business date helper is `com.hls.designation.api.BusinessDate` (`today()`); leave uses `LeaveAttendance.businessToday()`.
- Grants: `LEAVE_MANAGEMENT` has View and Approve for Admin, Director, Manager (no Create exists). `SUBSTITUTION` has View and Edit for the same roles; the cover endpoint already needs Edit. `TASKS`: Admin and Director all actions, Manager View, Create, Edit. `MARKETING`: View, Create, Edit for the three roles.
- Status codes: only **L** (Leave) and **A** (Absent) have category LEAVE (V14, V15). No paid LEAVE-category code exists, so every LEAVE mark is deducted (D1 stands).
- Decided (D4, D7): an Absent mark set by a supervisor is replaced by same-day leave, any other supervisor-set non-L mark refuses; a same-day observation is a task record that satisfies the due, with no ratings form.
