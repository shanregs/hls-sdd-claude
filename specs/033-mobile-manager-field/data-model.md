# Data Model: Mobile Manager Field Day

The app stores nothing and the server gets no new table. This file lists the shapes the app reads and sends, copied from
the spec 032 responses and used by the fake server and the request-shape tests. The app computes none of the values it
shows. Dates are ISO (`2026-11-03`) and shown DD/MM/YYYY; times are ISO instants shown in the business time zone.

## Read

### Today (`GET /me/today`)

| Field | Meaning |
| --- | --- |
| `date` | the business date of today |
| `workingDay` | whether today is a working day (weekly offs and holidays are not) |
| `missedCutoff`, `lateAfter` | the times the Director set; shown only as "checked in after <lateAfter> is marked late" help text |
| `day` | today's Manager's day or `null` if not checked in |
| `leave` | approved leave covering today (`fraction` 1.0 or 0.5, `unpaid`) or `null` |
| `missingCheckOutDates` | past days (about the last 31) with a check-in and no check-out |
| `plannedActivities` | `{ kind, id, date, place, status }` for today, in the server's order |

### Manager's day (`day` above; also the reply to check-in, check-out, complete and photo)

`id`, `date`, `dayType` (`OFFICE` or `FIELD`), `purpose`, `checkInAt`, `late`, `checkOutAt`, `hasPhoto`, `leave`,
`flags` (`LATE`, `NO_CHECK_OUT`, `ADDED_LATER`, `CORRECTED`, `UNLINKED`), `activities` (`{ kind, id }` linked to a Field
day). The location is never part of the view.

### Month (`GET /me/months/{yyyy-MM}`)

`month`, `locked`, `figures` (`workingDays`, `daysWorked`, `leavePaid`, `leaveUnpaid`, `unpaid`, `fieldDays`, `pending`
dates), `figuresUnavailable` (a message, for example no joining date, or `null`), and `days`: one entry per date with
`status` (`WORKED`, `FIELD`, `LEAVE`, `HOLIDAY`, `WEEKLY_OFF`, `NOT_EMPLOYED`, `PENDING`, `MISSED`) and the day's fields
above (`id`, `dayType`, `purpose`, `checkInAt`, `checkOutAt`, `late`, `hasPhoto`, `leave`, `flags`, `activities`). In the
Manager's own month the places and the day history are empty.

### Photo (`GET /me/days/{date}/photo`)

Image bytes. The app reads `Content-Type`, `Content-Length` and the file name of `Content-Disposition` for display; the
server sends `Cache-Control: no-store`.

### Leave

- Types (`GET /me/leave/types`): `{ id, name, lossOfPay }`.
- Request (`GET /me/leave`, newest first): `id`, `leaveTypeId`, `leaveTypeName`, `lossOfPay`, `firstDate`, `lastDate`,
  `firstHalf`, `lastHalf`, `reason`, `status` (`PENDING`, `APPROVED`, `REJECTED`, `REVOKED`, `CANCELLED`), `requestedAt`,
  `decidedAt`, `decisionReason`. The decider's id and the Manager's name are not shown.

### Forgotten check-in request (`GET /me/regularizations`)

`id`, `date`, `proposedTime`, `dayType`, `purpose`, `reason`, `status` (`PENDING`, `APPROVED`, `REJECTED`, `CANCELLED`),
`requestedAt`, `decidedAt`, `decisionReason`.

## Sent

| Call | Body |
| --- | --- |
| Check in | `{ dayType, purpose?, activities: [{kind, id}], latitude, longitude, accuracyMeters }` |
| Check out | `{ latitude, longitude, accuracyMeters }` |
| Complete a check-out | `{ time: "18:05", reason }` for `/me/days/{date}/complete-check-out` |
| Photo | multipart `file` (jpg or png) to `/me/days/{date}/photo` |
| Request leave | `{ leaveTypeId, firstDate, lastDate, firstHalf, lastHalf, reason }` |
| Ask for a check-in | `{ date, proposedTime, dayType, purpose?, reason }` |

## In the app only (not stored)

- **Check-in draft**: day type, selected activities, purpose text, picked photo (in memory until uploaded or removed). Kept
  across a refusal and cleared only after the server confirms.
- **Action state**: idle, sending, done or failed for each button, so a tap during a request is ignored.
- **Location result**: a fix (`latitude`, `longitude`, `accuracyMeters`, `capturedAt`) or none with a reason
  (`PERMISSION_DENIED`, `SERVICES_OFF`, `NO_FIX`, `OTHER`); never kept past the call.

## State changes

The app causes the server's own transitions and shows them after the response: a day goes from no record to checked in
(Office or Field) to checked out; a past day with a check-in and no check-out is flagged `NO_CHECK_OUT` and becomes complete
through the completion call; a leave or forgotten-check-in request goes `PENDING` to `APPROVED`, `REJECTED` or `REVOKED`
(decided on the web) or `CANCELLED` (by the Manager).
