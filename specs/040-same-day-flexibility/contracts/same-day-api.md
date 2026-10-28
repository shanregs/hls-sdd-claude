# Contract: Same-Day Flexibility API

All paths are under `/api/v1`, JSON, JWT-authenticated. Errors use the existing shapes (400 invalid, 403 forbidden
field/permission, 404 not found or out of scope, 409 conflict). "Today" is the server's business date (Asia/Kolkata);
no endpoint here accepts the date of the activity from the client except the offline replay in section 6.

## 1. Leave

### Teacher: `POST /me/leave` (existing, behaviour extended)

Same body as today (`leaveTypeId, firstDate, lastDate, halfDayStart, halfDayEnd, reason`). When `firstDate` is today:

- `lastDate == firstDate`: `201`, one request with `status: "APPROVED"`, `sameDay: true`.
- `lastDate > firstDate`: `201`, the response is today's part (APPROVED, `sameDay: true`) with
  `otherPart: { id, firstDate, lastDate, status: "PENDING" }` for the later days, so existing clients keep working.
- Refusals (409/400) are the existing ones, plus "Attendance for today was set by your Manager."
- A second request for the same dates: `409` "These dates overlap your approved request ..." (SC-006).

`POST /me/leave/preview` (existing POST) gains `takesEffectAtOnce: boolean` and, for a range past today, `parts: [{from, to, outcome}]`.

### Recorder: `POST /leave/same-day` (new)

Needs Approve on LEAVE_MANAGEMENT (that module has only View and Approve; no new action). Roles: Manager (assigned Teachers), Admin, Director (all).

Request: `{ teacherId, leaveTypeId, firstDate, lastDate, halfDayStart, halfDayEnd, reason }` - `firstDate` must be today.

`201` the leave view (same shape as the management list) with `sameDay: true`, `recordedBy`, `status: "APPROVED"`.
`404` Teacher outside scope. `400` `firstDate` is not today. `409` the guards of spec 009 and D4 (locked month, not a
working day, overlap, supervisor-set non-L, non-Absent mark).

### Lists

`GET /leave` (management) and `GET /me/leave` gain `sameDay=true|false`. Each item gains `sameDay`, `recordedBy`
(`{userId, name}`), and `otherPart` (nullable). `POST /me/leave/{id}/cancel` and `POST /leave/{id}/revoke` allow a
same-day request on its own business day (data-model.md).

## 2. Substitution

- `POST /substitutions/{id}/cover` (existing; Edit on SUBSTITUTION, unchanged): body unchanged. The result gains `sameDay`. It is
  accepted from `OPEN` or `ASSIGNED` with no earlier step, on or after the duty date within the existing limits.
- `GET /substitutions/manager-covers?from=&to=&managerId=&schoolId=&sameDay=&page=&size=` (new; View on SUBSTITUTION,
  scope applied): items `{ requirementId, date, school {id,name}, managerId, managerName, replacedTeacher {id,name},
  dayValue, sameDay, state: "ACTIVE"|"ENDED", endedBy: "TEACHER_ASSIGNED"|"RELEASED"|"ABSENCE_WITHDRAWN"|null,
  label: "Manager cover" }`. Teacher credit endpoints are unchanged and never include these.
- `GET /substitutions` gains `sameDay`.

## 3. Tasks

- `POST /tasks/same-day` (new; Create on TASKS; Manager only for themselves, Admin/Director refused with 403 because the
  spec limits logging to the Manager's own work):
  `{ type, title, description?, linkedType?, linkedId?, linkedLabel?, place?, status: "IN_PROGRESS"|"COMPLETED",
  completionNote? }`. `completionNote` (10-2000) is required for `COMPLETED`. `201 { id, status, sameDay: true }`.
  `COMPLETED` is stored as `VERIFIED` (research D7). For `type = OBSERVATION` with a Teacher link, the open observation
  due is satisfied. No assignee and no date in the body.
- `POST /tasks/{id}/done-on-the-spot` (new; Edit on TASKS; the assignee only; the task is open and due today):
  `{ completionNote }` (10-2000). `200` the task detail (as the other task actions), now `VERIFIED` with `sameDay: true`. `409` if not open or not due today.
- `GET /tasks` gains `sameDay`; items gain `sameDay`. Same-day items are never returned by the verification queue.

## 4. Visits

- `POST /marketing/activities/same-day` (new; Create on MARKETING; Manager for themselves):
  `{ kind: "SCHOOL_VISIT"|"SCHOOL_CALL"|..., targetType, targetId, outcome, notes, followUpOn? }`. `201` the activity with
  `status: "COMPLETED"`, `sameDay: true`, `activityDate` = today. The target must be in scope (`404` otherwise).
- ``GET /marketing/activities` and `GET /activity-calendar` gain `sameDay`; entries gain `sameDay`.

## 5. Report

`GET /reports/same-day?month=YYYY-MM` (new; a Zone filter is not built yet). Returns only the sections the caller has View on:
`{ month, leave: Rows, covers: Rows, tasks: Rows, visits: Rows }` where `Rows = { byManager: [{managerId, name, count}],
bySchool: [{schoolId, name, count}] }`. Scope applies (a Manager sees assigned Managers/Schools only). A section the
caller cannot view is omitted, not empty.

## 6. Offline replay (mobile)

`POST /tasks/same-day` and `POST /marketing/activities/same-day` accept the optional header `Idempotency-Key` (a UUID
generated on the phone, stored in the record's `client_key` column) and an optional body field `capturedOn` (date).
`capturedOn` is honoured only when the header is present and the date is not in the future and not more than **7 days**
old; otherwise `409` `{ reason: "DATE_NOT_ALLOWED" }`. A repeated key (found by `client_key`) returns the original record
and creates nothing. Without the header,
`capturedOn` is ignored and the server's today is used.

## 7. Web and mobile surface

| Role | Web | Mobile |
| ---- | --- | ------ |
| Teacher | Apply Leave: choosing today shows "takes effect at once" before Save | none |
| Manager | Leave Management: "Record same-day leave"; Substitutions: "I covered"; My Tasks: "Log for today", "Done on the spot"; visits in the Marketing and Activity Calendars; filter "Same-day" | Tasks and Visits: "Log for today" (offline allowed) |
| Admin / Director | same as Manager for any Teacher/Manager in scope; filter and the same-day report | none |
| System | none | none |

Each control is hidden when the permission is not granted; no new menu item.


## 8. As built (2026-10-28)

- Leave types for the recorder: `GET /leave/types` (Approve on LEAVE_MANAGEMENT).
- `POST /me/leave/preview` returns `takesEffectAtOnce`; the per-part `parts` list is not built.
- The report counts leave days, cover days (half day 0.5), tasks and visits. Per-Manager for leave is the School's Manager.
- Phone: tasks can be logged offline; a visit can be queued only if the connection is lost after the School was chosen.
