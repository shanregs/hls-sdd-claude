# Contract: Leave API

All paths under `/api/v1`. JSON. Dates are ISO `YYYY-MM-DD`. Errors use the house format
(`{ "reason": "..." }`): 400 invalid input, 403 not permitted, 404 not found (also for out of
scope), 409 conflict (state, overlap, lock, supervisor-set day).

## Teacher (module `MY_LEAVE`, scope: own)

| Method | Path | Action | Purpose |
| --- | --- | --- | --- |
| GET | `/me/leave/types` | VIEW | active leave types |
| POST | `/me/leave/preview` | CREATE | working-day count and refusals for a draft, no write |
| POST | `/me/leave` | CREATE | submit a request (PENDING) |
| GET | `/me/leave?status=&page=&size=` | VIEW | own requests, newest first |
| POST | `/me/leave/{id}/cancel` | DELETE | cancel own PENDING, or own APPROVED whose first day is in the future |

Request body for preview and submit:

```json
{ "leaveTypeId": "uuid", "firstDate": "2026-01-14", "lastDate": "2026-01-17",
  "halfDayStart": false, "halfDayEnd": true, "reason": "Pongal travel" }
```

Preview response: `{ "workingDays": 2.5, "days": [{ "date": "...", "kind": "WORKING|WEEKLY_OFF|HOLIDAY|UNPLACED", "value": 1.0 }], "problems": ["..."] }`.
Submit returns the request view (201). Refusals: no working day; overlap (names the clashing
request); first date more than 30 days before today; locked month; longer than 90 days; last before
first; both half-day flags on a one-working-day range; Teacher not placed.

Request view:

```json
{ "id": "uuid", "teacherId": "uuid", "teacherName": "...", "schoolName": "...",
  "leaveType": "Casual", "firstDate": "...", "lastDate": "...",
  "halfDayStart": false, "halfDayEnd": true, "workingDays": 2.5, "reason": "...",
  "status": "PENDING", "decidedByName": null, "decidedAt": null, "decisionNote": null,
  "cancelledBy": null, "createdAt": "...", "version": 0,
  "allowedActions": ["CANCEL"] }
```

`allowedActions` is computed by the server for the caller (CANCEL, APPROVE, REJECT, REVOKE).

## Supervisor (module `LEAVE_MANAGEMENT`, scope: assigned / org-wide)

| Method | Path | Action | Purpose |
| --- | --- | --- | --- |
| GET | `/leave?status=&teacherId=&schoolId=&month=YYYY-MM&page=&size=` | VIEW | requests in scope; default status PENDING; response includes `pendingCount` |
| GET | `/leave/{id}` | VIEW | one request, with the preview of days it would mark |
| POST | `/leave/{id}/approve` | APPROVE | body `{ "note": "...", "version": 0 }` |
| POST | `/leave/{id}/reject` | APPROVE | body `{ "reason": "...", "version": 0 }` (reason required) |
| POST | `/leave/{id}/revoke` | APPROVE | body `{ "reason": "...", "version": 0 }` (APPROVED only; reason required) |

Approve conflicts (409) list the problem: `days set by a supervisor: 2026-01-15, 2026-01-16`, or
`month locked: 2026-01`. A request already decided or cancelled returns 409 `already decided` /
`cancelled`. A stale `version` returns 409 stale.

## Internal contract: `attendance.api.LeaveAttendance`

```java
public interface LeaveAttendance {
    LocalDate businessToday();
    List<LeaveDay> workingDays(UUID teacherId, LocalDate from, LocalDate to);   // working days only, with School
    List<String> problems(UUID teacherId, List<LeaveDay> days);                  // locked months, supervisor-set days
    void apply(UUID requestId, UUID approverUserId, UUID teacherId, List<LeaveDay> days);
    RemoveResult remove(UUID requestId, UUID actorUserId);                       // refuses when a mark's month is locked
    record LeaveDay(LocalDate date, UUID schoolId, BigDecimal value) {}
    record RemoveResult(List<LocalDate> removed, List<LocalDate> keptBecauseChanged) {}
}
```

(The exact signatures are fixed in tasks; the shape above is the contract.)

## Role matrix (default grants)

| Endpoint group | Admin | Director | Manager | Teacher | System |
| --- | --- | --- | --- | --- | --- |
| `/me/leave/**` | 403 | 403 | 403 | allowed | 403 |
| `/leave` list/detail | allowed (all) | allowed (all) | allowed (in scope) | 403 | 403 |
| `/leave/{id}/approve|reject|revoke` | allowed | allowed | allowed (in scope) | 403 | 403 |

## Audit events

`LEAVE_REQUEST` entity changes: `created`, `status` (before to after, with the reason or note), and
`cancelledBy`. Leave-made marks: `ATTENDANCE_MARK` changes whose detail names the request.

## Amendment A3: Loss-of-Pay type

`GET /api/v1/me/leave/types` now also returns `{"code": "LOP", "name": "Loss of Pay"}` after the four original
types. Nothing else in this contract changes. In-process (not HTTP) for other modules: `leave.api.LeaveTypes`
(`LOSS_OF_PAY_CODE`, `isLossOfPay(requestId)`, `lossOfPayRequests(requestIds)`).
