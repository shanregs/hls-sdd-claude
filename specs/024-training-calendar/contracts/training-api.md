# Contract: Training REST API

Base path `/api/v1/training`. Dates ISO (`2026-11-14`), times `HH:mm`; screens show DD/MM/YYYY. Errors use the existing shape
(400 invalid input, 403 no grant, 404 out of scope or unknown, 409 conflict or stale `version`). System is refused with 403.
Every response that a Teacher can read omits internal remarks, exceptions and other people's rows.

## Sessions (`TRAINING`; reads also for Manager scoped, Teacher via /me)

| Method and path | Grant | Purpose |
| --- | --- | --- |
| `GET /sessions?from&to&type&status&trainer&personId` | TRAINING View | Calendar list (month grid uses the same call). Manager: only sessions they are assigned to or that include their Teachers. `personId` is Admin and Director only. |
| `POST /sessions` | Create | Create WEEKEND or MONTHLY session as DRAFT (or `publish: true`). |
| `GET /sessions/{id}` | View | Detail: fields, history, counts, `actions`, `holidayNotice`, `attendanceNotRecorded`, `groupNotFullyAssigned`. |
| `PUT /sessions/{id}` | Edit | Edit topic, trainer, venue, seat limit, description, date and time (a date or time change is a move and needs `reason`); `version` required; refused when HELD or CANCELLED. Sets `handEdited`. |
| `POST /sessions/{id}/publish` | Edit | DRAFT to PUBLISHED. |
| `POST /sessions/{id}/cancel` | Edit | `{reason}`; registered and assigned people are notified. |
| `DELETE /sessions/{id}` | Delete | DRAFT only. |

Session body: `{type, topic, date, startsAt, endsAt, trainerName, trainerContact, venueKind, venue, seatLimit, description,
version}`. Session view adds `status, patternId, patternMonth, handEdited, earlierDates[], counts{expected, attended, absent,
excused, walkIns, noShows, rate}`.

## Patterns (`TRAINING`)

| Method and path | Grant | Purpose |
| --- | --- | --- |
| `GET /patterns` | View | Active and stopped patterns. |
| `POST /patterns` | Create | `{ruleKind, nth, weekday, dayOfMonth, startsAt, endsAt, topic, trainer..., venue..., defaultGroup{kind, ref}, startMonth}`; generates sessions up to the horizon. Duplicate rule and time: 409. |
| `POST /patterns/preview` | Create | Same body; returns the next months' dates and notes (month-end clamp, holiday notice). Writes nothing. |
| `PUT /patterns/{id}` | Edit | `{fromMonth, ...fields, version}`; rewrites untouched sessions from that month. |
| `POST /patterns/{id}/skips` | Edit | `{month, reason}`. `DELETE /patterns/{id}/skips/{month}` removes a skip. |
| `POST /patterns/{id}/stop` | Edit | `{fromMonth}`; untouched later sessions removed, held ones kept. |

## Registration (Teacher `TRAINING_REGISTRATION`; Admin and Director `TRAINING` Create/Edit on behalf)

| Method and path | Purpose |
| --- | --- |
| `POST /sessions/{id}/registrations` | Teacher: body empty (self). Admin or Director: `{teacherId}`. Refused when not WEEKEND, not PUBLISHED, started, full (409 "no seats left"), Teacher not ACTIVE; already registered is idempotent 200. |
| `DELETE /sessions/{id}/registrations/me` and `/{teacherId}` | Cancel before the session starts; after that 409. |
| `GET /sessions/{id}/registrations` | TRAINING View: roster. |

## Assignment (`TRAINING`)

| Method and path | Purpose |
| --- | --- |
| `POST /sessions/{id}/assignments/preview` | `{personKind, ids[]}` or `{groupKind, groupRef}`: returns the people and how many are new, already assigned or refused (with reasons). |
| `POST /sessions/{id}/assignments` | Same body plus `confirm: true`; one obligation per person, notifications sent; not allowed when HELD, CANCELLED or SKIPPED. |
| `DELETE /sessions/{id}/assignments/{assignmentId}` | Remove (`reason`), only before HELD; person notified. |
| `GET /sessions/{id}/assignments` | Roster of obligations. |
| `POST /sessions/{id}/assignments/me/cannot-attend` | The assigned person (a Teacher with `TRAINING_REGISTRATION` Create; a Manager with `TRAINING` View) sends `{note}`; Admin and Director notified; status unchanged. |

## Attendance (`TRAINING_ATTENDANCE`)

| Method and path | Purpose |
| --- | --- |
| `GET /sessions/{id}/attendance` | Roster rows (assigned, registered, walk-ins) with outcome, `onLeave` suggestion, `syncState`, `clashNote`, and history. Manager: only their Teachers. |
| `PUT /sessions/{id}/attendance` | Bulk save: `{rows:[{personKind, personId, outcome, dayValue, reason, certificateNote, internalRemark, version}]}`. Session date must be today or past. One transaction. |
| `POST /sessions/{id}/walk-ins` | `{personKind, personId, dayValue}`; recorded Present, not counted as expected. |
| `GET /people?kind&q` | `TRAINING` Edit or `TRAINING_ATTENDANCE` Edit | Person picker for individual assignment and walk-ins: up to 20 `{kind, id, name}`, Teachers (ACTIVE or ON_LEAVE) and active Managers. Manager: only their Teachers. Teacher: 403. |
| `POST /sessions/{id}/attendance/{entryId}/sync` | Retry the 008 write after a clash is resolved. |

`PUT` response: `{results:[{personId, status: SAVED | SAVED_CLASH | REFUSED, code, message}], counts}`. REFUSED codes:
`MONTH_LOCKED` (message from 008), `NOT_IN_SCOPE` (a Manager for a Teacher not placed under them, or for any Manager including
themselves), `NOT_ON_ROSTER`, `STALE`, `REASON_REQUIRED`. Refused rows save nothing; the others save. The first save sets the
session HELD. A Teacher PRESENT writes the 008 training-day mark (1.00 or 0.50); `SAVED_CLASH` means the person already has
another mark that day, nothing was overwritten, and `clashNote` says what it is.

## Exceptions (`TRAINING` View; closing needs Edit; Manager View for own Teachers and self)

| Method and path | Purpose |
| --- | --- |
| `GET /exceptions?month&zoneId&managerId&role&status&page&size` | List with `repeat`, `countInWindow`, person, session, Manager and Zone. |
| `POST /exceptions/{id}/close` | `{kind: EXCUSED, MADE_UP or ACKNOWLEDGED, note, makeupSessionId, version}`. |
| `GET /exceptions/export` | CSV, Export grant (Admin, Director). |

## History and my training

| Method and path | Grant | Purpose |
| --- | --- | --- |
| `GET /people/{TEACHER or MANAGER}/{id}/history?year` | TRAINING View (scoped) | Entries newest first, induction entry, `yearly{expected, attended}`; internal remarks only for staff. |
| `GET /people/{kind}/{id}/history/export?year` | TRAINING Export | CSV. |
| `GET /me/upcoming` | TRAINING_REGISTRATION View (Teacher), TRAINING View (Manager) | Sessions the caller is Required at (`REQUIRED`), or may register for with a free seat (`OPEN`, Teacher only), date order. |
| `GET /me/history?year` | same | The caller's own history without internal remarks. |

## Audit and security notes

All writes record Change History (`TRAINING_SESSION`, `TRAINING_PATTERN`, `TRAINING_ASSIGNMENT`, `TRAINING_REGISTRATION`,
`TRAINING_ATTENDANCE`, `TRAINING_EXCEPTION`). Text fields capped (topic 200, reasons 300, remarks 500); venue links must be
https. No payroll or pay field exists in any request or response.
