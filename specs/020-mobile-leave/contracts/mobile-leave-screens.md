# Contract: Mobile Leave Screens

What the app shows, and the server calls behind it. The server calls are spec 009's, unchanged; shapes are in
[data-model.md](../data-model.md). Every call carries `Authorization`, `X-HLS-Client` and either
`X-HLS-Location` or `X-HLS-Location-Status` (spec 018). Errors use `{ "reason": "..." }`.

## Menu routes (from the server's navigation, mapped by the screen registry)

| Route | Menu item | Screen |
| ----- | --------- | ------ |
| `/leave/apply` | LEAVE → Apply Leave | ApplyLeaveScreen |
| `/leave/history` | LEAVE → My Leave History | MyLeaveHistoryScreen |
| `/operations/leave` | OPERATIONS → Leave Management | LeaveManagementScreen (and the detail) |

Nothing else opens these screens, and nothing chooses them by role name.

## Server calls

| Screen | Call | Notes |
| ------ | ---- | ----- |
| Apply Leave | `GET /api/v1/me/leave/types` | on open; the options of the type chooser |
| Apply Leave | `POST /api/v1/me/leave/preview` body `{leaveTypeId, firstDate, lastDate, halfDayStart, halfDayEnd, reason}` | on "Check"; creates nothing |
| Apply Leave | `POST /api/v1/me/leave` same body | 201 with the request; only then "Request submitted" |
| Apply Leave | `GET /api/v1/attendance/calendar` | only to label non-counted days (weekly off, holiday) |
| My Leave History | `GET /api/v1/me/leave?status=&page=&size=25` | no status = all; `status` upper case |
| My Leave History | `POST /api/v1/me/leave/{id}/cancel` | no body; offered only when `allowedActions` has `CANCEL` |
| Leave Management | `GET /api/v1/leave?status=&page=&size=25` | no status = Pending; includes `pendingCount` |
| Leave Management detail | `GET /api/v1/leave/{id}` | request, days it would mark, problems |
| Leave Management detail | `POST /api/v1/leave/{id}/approve` body `{note, version}` | when `APPROVE` is allowed |
| Leave Management detail | `POST /api/v1/leave/{id}/reject` body `{reason, version}` | when `REJECT` is allowed; reason required |
| Leave Management detail | `POST /api/v1/leave/{id}/revoke` body `{reason, version}` | when `REVOKE` is allowed; reason required |
| Home widget | `GET /api/v1/leave?status=PENDING&size=1` | reads `pendingCount`; only if the menu offers `/operations/leave` |

## Apply Leave

- Fields: Leave type (the server's list), First date, Last date, "Half day on the first day", "Half day on
  the last day", Reason (up to 500 characters, with a counter).
- App checks only: a type and both dates chosen, last date not before first, reason not empty (research §4).
- "Check" shows the preview: the server's working days, the counted days with their values, the other dates
  of the range as "Weekly off", "Holiday" (from the calendar) or "Not counted", and each problem the server
  lists. Any change to a field clears the preview.
- "Submit" is enabled only after a preview of the current draft that listed no problems. A refusal is shown
  in plain language; the draft stays. With no connection: "No connection. Your request was not submitted."
- On 201: the form is cleared and the app opens My Leave History with "Request submitted" and the new request
  at the top.
- A 404 on open ("Your profile has not been set up yet.") is shown instead of the form.

## My Leave History

- Filter chips: All, Pending, Approved, Rejected, Cancelled. List newest first, with "Load more".
- A card shows type, `DD/MM/YYYY – DD/MM/YYYY`, half-day marks, working days, status, who decided and when, and
  the note or reason. Tapping a card expands its details (reason, decision note).
- "Cancel request" shows only when `allowedActions` contains `CANCEL`; it confirms, calls cancel, then reloads
  the list.

## Leave Management

- Filter chips: Pending (default), Approved, Rejected, Cancelled; a "Pending: N" count from `pendingCount`.
- A row (Teacher, School, type, dates, working days, status) opens the detail; no action is offered on a row.
- Detail: the request, the days approving would mark (`days`), and any `problems` in plain language. Buttons
  Approve, Reject, Revoke show exactly for the actions in `allowedActions`. Each opens a dialog: Approve (optional
  note), Reject and Revoke (required reason, up to 500 characters). On success the list and count reload.
- After a refusal the dialog stays open with the reason; for a 409 (already decided, cancelled, stale version,
  supervisor-set days, locked month) the detail also reloads and shows the current state.
- A 404 on the detail shows "Not found" and no data.

## Home widget

- Shown only to a user whose menu offers `/operations/leave`: "Pending leave requests: N" (or "No pending
  leave requests"), opening Leave Management on Pending. A failed load shows "Could not load" and no number.
  It replaces the "coming soon" card for the OPERATIONS section when that section has a screen.

## Plain wording (see research §6)

Reworded: `days set by a supervisor: <dates>` → "Some of these days were marked by a supervisor (<dates>).
Correct them first, then approve."; `month locked: <YYYY-MM>` → "Attendance for <month name year> is locked.";
stale version → "This request was changed by someone else. It now shows the latest state." Every other server
text is shown as given.
