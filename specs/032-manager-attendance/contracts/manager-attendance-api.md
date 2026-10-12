# Contract: Manager Attendance API (HTTP, for the screens and spec 033)

All paths under `/api/v1/manager-attendance`. JSON, except the photo upload. Dates are ISO (`2026-11-03`) and shown as
DD/MM/YYYY; times are ISO instants from the server, shown in the business time zone. Errors use the house format
(`{ "reason": "..." }`): 400 invalid input (the reason names the problem), 403 not permitted, 404 not found, 409 conflict
(a second check-in, a locked month, a stale version). Every endpoint refuses a caller without the stated permission on
the server, whatever the menu shows. Teacher and System always get 403.

Permissions: `MY` = `MY_MANAGER_ATTENDANCE` (Manager, own data only); `ATT` = `MANAGER_ATTENDANCE` (Admin, Director);
`LEAVE` = `MANAGER_LEAVE` (Admin, Director). The actor comes from the JWT; a Manager is resolved from their user.

## The Manager's own day (MY)

| Method | Path | Action | Purpose |
| --- | --- | --- | --- |
| GET | `/me/today` | VIEW | today's record (if any), whether today is a working day, the cut-off and late times, and whether leave covers today |
| POST | `/me/check-in` | CREATE | check in; body below |
| POST | `/me/check-out` | CREATE | check out today's record; body below |
| POST | `/me/days/{date}/photo` | CREATE | attach a photo (multipart `file`, jpg or png) to the Manager's own check-in of that date |
| GET | `/me/months/{yyyy-MM}` | VIEW | each day with its status, times, purpose, flags and leave, plus the month figures |
| GET | `/me/regularizations` | VIEW | the Manager's forgotten check-in requests |
| POST | `/me/regularizations` | CREATE | ask to add a forgotten check-in |
| DELETE | `/me/regularizations/{id}` | DELETE | cancel a pending request |

```json
{ "dayType": "FIELD", "purpose": "Visit to St Mary's School", "latitude": 12.971599, "longitude": 77.594566, "accuracyMeters": 18.5 }
```

Check-in refused with 400 and the reason: no location, a latitude or longitude out of range, a day type other than
`OFFICE` or `FIELD`, a Field day with no purpose, no joining date recorded (spec 005a), approved full-day leave that day,
a joining date after the day, or an exit date before it; 409 for a second check-in or a locked month. On approved
half-day leave one check-in is accepted. Response `201`:

```json
{ "id": "uuid", "date": "2026-11-03", "dayType": "FIELD", "purpose": "Visit to St Mary's School",
  "checkInAt": "2026-11-03T03:42:11Z", "late": false, "checkOutAt": null, "leave": null, "flags": [] }
```

Check-out body: `{ "latitude": 12.97, "longitude": 77.59, "accuracyMeters": 20 }`. 400 without a location or without a
check-in; 409 if already checked out.

Forgotten check-in body: `{ "date": "2026-11-02", "proposedTime": "09:15", "dayType": "OFFICE", "purpose": null, "reason": "Phone was off" }`.
Refused with 400 for a future day or time, a day in a locked month (409), a day that already has a check-in, a pending
request for the same date, or a missing reason.

## Manager leave (MY, served by the leave module)

| Method | Path | Action | Purpose |
| --- | --- | --- | --- |
| GET | `/me/leave/types` | VIEW | the active leave types of spec 009, with which one is Loss-of-Pay |
| GET | `/me/leave` | VIEW | the Manager's requests, newest first |
| POST | `/me/leave` | CREATE | request leave: `{ "leaveTypeId", "firstDate", "lastDate", "firstHalf", "lastHalf", "reason" }` |
| DELETE | `/me/leave/{id}` | DELETE | cancel a pending request (an approved one is revoked by the approver) |

Refused with 400: dates reversed, a range with no working day, a date before the joining date, no joining date, a
missing reason, a half day on a day that is not the first or last; 409 for an overlap with another pending or approved
request.

## Admin and Director: the grid and corrections (ATT)

| Method | Path | Action | Purpose |
| --- | --- | --- | --- |
| GET | `/grid?month=2026-11&zoneId=&q=` | VIEW | every Manager's status for each day of the month, with the month figures and lock state; `zoneId` filters by the Zones the Manager holds |
| GET | `/missed?date=` | VIEW | working days of the date on which a Manager has no check-in past the cut-off and no leave (default: today) |
| GET | `/on-leave?date=` | VIEW | Managers on approved leave on the date |
| GET | `/managers/{managerId}/months/{yyyy-MM}` | VIEW | one Manager's days in full, with locations, the photo link and each day's history |
| PUT | `/managers/{managerId}/days/{date}` | EDIT | correct a day (type, purpose, check-in or check-out time, or clear it) with a `reason` |
| POST | `/managers/{managerId}/days/{date}/complete-check-out` | EDIT | add a missing check-out time with a `reason` |
| POST | `/months/{yyyy-MM}/lock` | PROCESS | lock one Manager (`{ "managerId" }`) or all (`{}`) once the month has ended |
| POST | `/months/{yyyy-MM}/reopen` | PROCESS | reopen one Manager's month with `{ "managerId", "reason" }` |
| GET | `/settings` | VIEW | the cut-off and late times |
| PUT | `/settings` | EDIT | change them: `{ "missedCutoff": "10:30", "lateAfter": "09:30", "version": 3 }` |

A correction or completion in a locked month is refused with 409. A lock is refused with 409 while the month has not
ended, or while any leave or forgotten-check-in request touching it is pending; the response lists the pending requests.
The lock of all Managers locks each that is lockable and returns the ones refused with the reason for each.

## Admin and Director: decisions (LEAVE)

| Method | Path | Action | Purpose |
| --- | --- | --- | --- |
| GET | `/leave?status=PENDING&managerId=` | VIEW | Managers' leave requests |
| POST | `/leave/{id}/approve` | APPROVE | approve; writes the leave days onto the Manager's records |
| POST | `/leave/{id}/reject` | APPROVE | reject with a `reason` |
| POST | `/leave/{id}/revoke` | APPROVE | revoke an approved request with a `reason`; removes the leave days |
| GET | `/regularizations?status=PENDING&managerId=` | VIEW | forgotten check-in requests |
| POST | `/regularizations/{id}/approve` | APPROVE | approve; the day becomes worked, marked "added later" |
| POST | `/regularizations/{id}/reject` | APPROVE | reject with a `reason` |

A decision is refused with 403 when the decider is the requester, and with 403 when a Director's request is decided by
a Director (an Admin decides it) or an Admin's by an Admin (a Director decides it). A decision that would write a day
into a locked month is refused with 409 and changes nothing.

## Audit

Every write above writes its audit entries in the same transaction (data-model.md, "Audit entity types"); they appear in
the existing Audit → Change History screen. This API adds no audit endpoint.
