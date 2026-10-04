# Data Model: Mobile Attendance

This feature stores **no data**: no table, no column, no migration, and nothing persisted on the phone.
It reads and writes the attendance records of spec 008 through the server. This file describes the
shapes the app holds in memory while a screen is open, copied from the server's responses so the two
stay in step (source of truth: `specs/008-attendance/contracts/attendance-api.md` and
`frontend/src/features/attendance/attendanceApi.ts`).

## Server shapes used (unchanged)

### StatusCode
`{ id, shortCode, name, category, weight, active, system, inUse, version }`, where `category` is one of
`WORKED`, `LEAVE`, `TRAINING`, `NON_WORKING`. The app uses `shortCode`, `name`, `category` and the order
given. Weight is shown nowhere on the phone.

### TeacherMonthView
`{ teacherId, name, month ("YYYY-MM"), locked, state ("OPEN"|"LOCKED"), rollup, days[] }`

- **rollup**: `{ workingDays, daysWorked, daysLeave, trainingAvailable, trainingAttended, unmarked,
  weightedTotal, locked, frozen }`. The app shows working days, days worked, leave and unmarked, and
  never recomputes any figure.
- **days[]** (one per calendar day): `{ date ("YYYY-MM-DD"), state, mark, editableBy }`
  - `state`: `MARKED`, `UNMARKED`, `NOT_PLACED`, `WEEKLY_OFF`, `NON_WORKING`, `FUTURE`.
  - `editableBy`: `SELF`, `SUPERVISOR` or `NONE`. This single field decides whether the day sheet offers
    Save (FR-003) and for whom; the app never computes the 3-day window.
  - `mark` (present when `MARKED`): `{ date, code, codeName, category, dayValue, schoolId, schoolName,
    setByKind ("SELF"|"SUPERVISOR"), setByUserId, setByName, setAt, note, version }`.

### AttendanceCalendar
`{ defaultWeeklyOff[], defaultVersion, schoolOverrides[{schoolId, schoolName, weeklyOff[]}],
nonWorkingDates[{date, description}] }`. Weekly-off values are three-letter weekdays (`MON`..`SUN`).

### TeacherGridRow (Manager list)
One row per in-scope Teacher: `{ teacherId, name, status, school, manager, locked, rollup, cells[] }`.
The app uses `teacherId`, `name`, `school`, `locked` and the rollup; the per-day `cells` are not used on
the phone.

### MarkRequest
`{ statusCode, dayValue (1 or 0.5), note?, version? }` for create and correct; the `version` of the mark
being replaced is sent when one exists. Clearing is a DELETE with no body.

### DayHistoryEntry
Every earlier value of a day, newest first: the value, who changed it, when, and whether it was a
create, correction or clear (server shape; the app renders it as a list).

## In-memory view state (never stored)

| Name | Fields | Rules |
| ---- | ------ | ----- |
| MonthSelection | `year`, `month`, `range` | `range` is the current year; Attendance History also allows the previous year (FR-002, FR-006). The default is the current month in the business time zone (research §3). |
| DaySheetDraft | `date`, `statusCode`, `dayValue`, `note`, `baseVersion` | Kept on screen after a failed save so the user can retry (FR-005). Cleared only when the server confirms. |
| ServerClock | `offsetMs` | Difference between the server's `Date` header and the phone clock, used only to choose the default month. |
| ListState | `query`, `page`, `rows`, `status` | Manager list: debounced search text, paging, loading / empty / error. |

## Validation rules (quoted from the spec so they are tested, not guessed)

- `dayValue` is whole (1) or half (0.5); no other value is offered (FR-003).
- The note is optional and plain text; the server's length limit is shown as given if it refuses.
- A save is offered only when the day's `editableBy` is `SELF` (Teacher screens) or the Manager
  screens' days are inside a placement and the month is not locked (the server's `state` and `locked`
  say so).
- Months outside the allowed range are not offered (FR-002).
- A mark is "saved" only after a 2xx response (FR-005, SC-006).

## State transitions of a day (as seen by the app)

`UNMARKED` → (Teacher or Manager saves) → `MARKED` by SELF or SUPERVISOR → (Teacher corrects, only if SELF)
→ `MARKED` → (Manager corrects) → `MARKED` by SUPERVISOR → (Manager clears) → `UNMARKED`. A supervisor mark
makes `editableBy` become `SUPERVISOR` for the Teacher. A locked month makes every day `NONE`. All of
this is decided by the server and merely displayed.

## Unchanged

Teacher, School, placement, scope, status codes, calendars, locks, audit and the API Access trail are
the entities of specs 003, 005, 008 and 018.
