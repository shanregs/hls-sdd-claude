# API Contract: Attendance

All endpoints need a valid session and enforce **both** a permission and the caller's data scope
server-side (`PermissionGuard`, `ScopeQueries`, `TeacherScopeQueries`). `404` also means "outside
your scope" (indistinguishable). Failures are `{"reason": "..."}`; `409` is a business-rule or
stale-version refusal; `400` invalid input. Dates are `YYYY-MM-DD`, months `YYYY-MM`, lists use the
`{content,page,size,totalElements}` envelope with `size` capped at 100.

## Status codes and setup (module `ATTENDANCE_SETUP`)

| Method | Path | Permission | Notes |
| --- | --- | --- | --- |
| GET | `/api/v1/attendance/status-codes` | any of `ATTENDANCE.VIEW`, `TEACHER_ATTENDANCE.VIEW`, `MY_ATTENDANCE.VIEW`, `ATTENDANCE_SETUP.VIEW` | `activeOnly` default true |
| POST | `/api/v1/attendance/status-codes` | `ATTENDANCE_SETUP.EDIT` | `{shortCode,name,category,weight}` -> 201; 409 duplicate short code |
| PUT | `/api/v1/attendance/status-codes/{id}` | `ATTENDANCE_SETUP.EDIT` | `{name,weight,active,version}`; a system code cannot be deactivated |
| GET | `/api/v1/attendance/calendar` | `HOLIDAY_CALENDAR.VIEW` (every role) | `{defaultWeeklyOff:[..], defaultVersion, schoolOverrides:[{schoolId,schoolName,weeklyOff:[..]}], nonWorkingDates:[{date,description}]}`; the Holiday Calendar screen builds its yearly and monthly views and the PDF from this one response |
| PUT | `/api/v1/attendance/calendar/default` | `HOLIDAY_CALENDAR.EDIT` (Admin, Director) | `{weeklyOff:["SUN"],version}` |
| PUT | `/api/v1/attendance/calendar/schools/{schoolId}` | `HOLIDAY_CALENDAR.EDIT` | `{weeklyOff:["SUN","SAT"]}` creates/replaces the override |
| DELETE | `/api/v1/attendance/calendar/schools/{schoolId}` | `HOLIDAY_CALENDAR.EDIT` | back to the default |
| POST | `/api/v1/attendance/calendar/non-working-dates` | `HOLIDAY_CALENDAR.EDIT` | `{onDate,description}`; 409 duplicate |
| DELETE | `/api/v1/attendance/calendar/non-working-dates/{onDate}` | `HOLIDAY_CALENDAR.EDIT` | |

The PDF is produced in the browser; there is no server endpoint for it.

Changing the calendar never alters a locked Teacher-month.

## Teacher self-service (`MY_ATTENDANCE`, own record only)

| Method | Path | Permission | Notes |
| --- | --- | --- | --- |
| GET | `/api/v1/attendance/me?month=YYYY-MM` | `MY_ATTENDANCE.VIEW` | the caller's `TeacherMonthView` (below); 404 `"Your profile has not been set up yet."` if no linked Teacher; the History page uses earlier months of the same endpoint |
| PUT | `/api/v1/attendance/me/marks/{date}` | `MY_ATTENDANCE.CREATE` (new) / `.EDIT` (correct) | `{statusCode,dayValue,note,version?}`; 409 reasons: no placement that date, future date, older than 3 days, locked month, already set by a supervisor, exited |

## Supervisors (Manager in scope; Admin/Director all)

| Method | Path | Permission | Notes |
| --- | --- | --- | --- |
| GET | `/api/v1/attendance/teachers/{teacherId}?month=` | `TEACHER_ATTENDANCE.VIEW` or `ATTENDANCE.VIEW` | `TeacherMonthView`; 404 out of scope |
| PUT | `/api/v1/attendance/teachers/{teacherId}/marks/{date}` | `TEACHER_ATTENDANCE.EDIT` or `ATTENDANCE.EDIT` | same body; writes `SUPERVISOR`; any unlocked date inside a placement |
| DELETE | `/api/v1/attendance/teachers/{teacherId}/marks/{date}` | same | clears the mark (history row `CLEARED`); 409 if locked |
| GET | `/api/v1/attendance/teachers/{teacherId}/marks/{date}/history` | same as the view | every value of the day, newest first |

## Grids

| Method | Path | Permission | Notes |
| --- | --- | --- | --- |
| GET | `/api/v1/attendance/teacher-grid?month=&query=&page=&size=` | `TEACHER_ATTENDANCE.VIEW` | Manager: Teachers in scope placed during the month |
| GET | `/api/v1/attendance/grid?month=&query=&zoneId=&schoolId=&managerId=&status=&page=&size=` | `ATTENDANCE.VIEW` | Admin/Director: all placed Teachers; filters combine with AND |

**GridResponse**: `{ month, days: <actual number of days>, content: [ { teacherId, name, status,
school, manager, locked, rollup, cells: [ { date, code, category, dayValue, setByKind, state } ] }
], page, size, totalElements }` where `state` is one of `MARKED`, `UNMARKED`, `NOT_PLACED`,
`WEEKLY_OFF`, `NON_WORKING`, `FUTURE`, and `category` (only on `MARKED` cells) is the mark's status
category: `WORKED`, `LEAVE`, `TRAINING` or `NON_WORKING`. The screen labels `WEEKLY_OFF` with the
weekday abbreviation, `NON_WORKING` with `H`, and colours leave/absent (`LEAVE`) and holiday
(`NON_WORKING`) marks.

## Month, lock and reopen (module `ATTENDANCE`, action `PROCESS`)

| Method | Path | Notes |
| --- | --- | --- |
| POST | `/api/v1/attendance/months/{yearMonth}/lock` | locks every Teacher-month in the month. **409** with `{reason, unmarked:[{teacherId,name,dates:[..]}]}` if the month has not ended or any placed Teacher has an unmarked working day; nothing is locked. 200 `{locked: N}` |
| POST | `/api/v1/attendance/teachers/{teacherId}/months/{yearMonth}/reopen` | `{reason}` (required) -> 200 |
| POST | `/api/v1/attendance/teachers/{teacherId}/months/{yearMonth}/relock` | 409 listing unmarked dates, else 200 with the refrozen rollup |
| GET | `/api/v1/attendance/teachers/{teacherId}/months/{yearMonth}/events` | lock/reopen/relock history |

## Export (`ATTENDANCE.EXPORT`)

`GET /api/v1/attendance/export?month=&zoneId=&schoolId=&managerId=&status=&query=` -> `text/csv`, one
row per Teacher: name, status, School, Manager, rollup figures, then one column per day (short
code, `0.5` suffix for half days; a weekly off day shows the weekday abbreviation such as `Sun`, a
non-working date shows `H`). Built in page-sized batches; records one audit event.

## TeacherMonthView

`{ teacherId, name, month, locked, state, rollup:{workingDays,daysWorked,daysLeave,
trainingAvailable,trainingAttended,unmarked,weightedTotal,frozen:boolean}, days:[ {date, state,
code, dayValue, school, setByKind, setByName, setAt, note, editableBy:"SELF"|"SUPERVISOR"|"NONE"} ] }`.

## Shared Java contract for later modules

`attendance.api.AttendanceReadApi`: `rollupOf(teacherId, yearMonth)` (frozen if locked, with a
`locked` flag), `marksOf(teacherId, yearMonth)`, `isLocked(teacherId, yearMonth)`. Leave, payroll
and reports MUST use it and MUST NOT recompute attendance.

## Audit side effects

Every write above publishes `EntityChanged` (`ATTENDANCE_MARK`, `ATTENDANCE_MONTH`,
`ATTENDANCE_CODE`, `ATTENDANCE_CALENDAR`, `ATTENDANCE_EXPORT`) with actor, time and previous/new
values; System sees none of them (`AuditVisibility`).
