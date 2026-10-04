# Contract: Notifications API

All paths under `/api/v1/me/notifications`. JSON. The recipient is always the signed-in user; no endpoint
takes a user id. Errors use the house format (`{ "reason": "..." }`): 400 invalid input, 403 not permitted,
404 not found (also for another user's notification).

| Method | Path | Action | Purpose |
| --- | --- | --- | --- |
| GET | `?unread=true&page=0&size=25` | VIEW | own notifications, newest first; `unread=true` keeps unread only |
| GET | `/unread-count` | VIEW | `{ "unread": 3 }` |
| POST | `/{id}/read` | VIEW | marks one read (idempotent), returns the notification |
| POST | `/read-all` | VIEW | marks all own unread as read, returns `{ "marked": 3 }` |
| DELETE | `/{id}` | DELETE | deletes one; 404 when it is not the caller's |
| DELETE | `/read` | DELETE | deletes every read notification of the caller, returns `{ "deleted": 5 }` |

List response (the shared page envelope):

```json
{ "content": [
    { "id": "uuid", "type": "LEAVE_DECIDED", "title": "Your leave was rejected",
      "message": "Your Sick leave on 28/09/2026 was rejected: Annual exams are on that day.",
      "link": "/leave/history", "channel": "IN_APP", "read": false,
      "createdAt": "2026-10-05T08:30:00Z", "updatedAt": "2026-10-05T08:30:00Z" } ],
  "page": 0, "size": 25, "totalElements": 1, "unread": 1 }
```

`size` is capped at 100. The list response also carries the caller's `unread` count so the page needs one
request. `link` is an app route (starts with `/`) or null.

## Role matrix (default grants)

| Endpoint group | Admin | Director | Manager | Teacher | System |
| --- | --- | --- | --- | --- | --- |
| list, unread-count, read, read-all | allowed | allowed | allowed | allowed | 403 |
| delete, clear read | allowed | allowed | allowed | allowed | 403 |

Each role sees only its own notifications; the permission is runtime-editable, so a role without `DELETE`
can read but not delete.

## Event contract (internal, in the producers' `api` packages)

```java
// leave.api
record LeaveRequested(UUID requestId, UUID teacherId, UUID schoolId, LocalDate firstDate, LocalDate lastDate, String teacherName) {}
record LeaveDecided(UUID requestId, UUID teacherId, Decision decision, LocalDate firstDate, LocalDate lastDate, String reason) {}
record LeaveCancelled(UUID requestId, UUID teacherId, UUID schoolId, LocalDate firstDate, LocalDate lastDate, String teacherName, boolean wasApproved) {}
// attendance.api
record AttendanceMarkChanged(UUID teacherId, LocalDate date, UUID actorUserId, String actorName) {}
record AttendanceMonthLocked(UUID teacherId, YearMonth month, UUID actorUserId) {}
record AttendanceMonthReopened(UUID teacherId, YearMonth month, UUID actorUserId, String reason) {}
```

Producers publish these inside their transaction with `ApplicationEventPublisher`; the `notification`
module's synchronous `@EventListener` methods create the rows. (Exact signatures are fixed in tasks; the
shape above is the contract.)

## Notification texts (title / message / link)

| Type | Title | Message (example) | Link |
| --- | --- | --- | --- |
| LEAVE_DECIDED approved | Your leave was approved | Your leave 12/10/2026 to 13/10/2026 was approved. | /leave/history |
| LEAVE_DECIDED rejected | Your leave was rejected | Your leave 28/09/2026 was rejected: {reason}. | /leave/history |
| LEAVE_DECIDED revoked | Your approved leave was revoked | Your approved leave 26/10/2026 to 27/10/2026 was revoked: {reason}. | /leave/history |
| LEAVE_REQUESTED | New leave request | {Teacher} asked for leave 12/10/2026 to 13/10/2026. | /operations/leave |
| LEAVE_CANCELLED | Leave request withdrawn | {Teacher} cancelled their leave 12/10/2026 to 13/10/2026. | /operations/leave |
| ATTENDANCE_CHANGED | Your attendance was updated | {Actor} updated your attendance for 05/10/2026. (merged: updated {n} days of your attendance in October 2026) | /my-attendance?month=2026-10 |
| ATTENDANCE_MONTH_LOCKED | Your attendance month was locked | October 2026 is locked; changes now need a reopen. | /my-attendance?month=2026-10 |
| ATTENDANCE_MONTH_REOPENED | Your attendance month was reopened | October 2026 was reopened: {reason}. | /my-attendance?month=2026-10 |
