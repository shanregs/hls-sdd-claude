# Data Model: Mobile Notifications

No server data is added or changed and nothing is stored on the phone. These are the in-memory shapes the app
reads from spec 010 and the small state it keeps while the shell is mounted.

## Server shapes (spec 010, unchanged)

### Notification

| Field | Type | Notes |
| ----- | ---- | ----- |
| `id` | string (uuid) | |
| `type` | string | e.g. `LEAVE_DECIDED`, `LEAVE_REQUESTED`, `ATTENDANCE_CHANGED`; shown as text only, never branched on |
| `title` | string | |
| `message` | string | shown in full in the detail, two lines in a row |
| `link` | string or null | an app route starting with `/`, may carry `?month=YYYY-MM` |
| `channel` | string | `IN_APP`; ignored |
| `read` | boolean | |
| `createdAt`, `updatedAt` | ISO 8601 UTC | the app shows `createdAt` only |

### NotificationPage

`{ content: Notification[], page, size, totalElements, unread }`. `unread` is the caller's whole unread count, not the
page's.

### Other responses

`{ unread }` from `/unread-count`; `{ marked }` from `/read-all`; `{ deleted }` from `DELETE /read`. Errors are
`{ reason }` with 400, 403 or 404.

## App state

### UnreadCount (NotificationsProvider)

| Field | Type | Rules |
| ----- | ---- | ----- |
| `enabled` | boolean | true only when the access model offers `/account/notifications` |
| `count` | number or null | `null` before the first answer and after a failed one: the bell shows no number |
| `canDelete` | boolean | the Notifications menu item's `actions` contains `DELETE` |

Transitions: any trigger (mount, foreground, 30 s tick while active, `refreshCount()`) sets `count` from the server;
a list page response sets it from `unread`. Background stops the tick. Unmount (sign-out) discards it.

### ListState (NotificationsScreen)

`filter: "all" | "unread"`, `rows: Notification[]`, `page`, `hasMore`, `unread` (from the last page), and a status of
`loading | ready | error | noConnection`. A failed load drops the rows (never shown as current). Row-level pending
flags prevent a second request for the same notification.

### LinkTarget

`{ route: string; month?: string }` or `null`. Produced by `linkTarget(link)`; `month` is present only for
`/my-attendance` and only when it matches `YYYY-MM` (month 01 to 12). `RouteState` in the shell gains
`month?: string`, used by My Attendance.

## Validation rules (all server-side except as noted)

- Own notifications only; another user's id is a 404 (shown as "no longer exists").
- Delete and Clear read need the Delete permission (403 otherwise).
- App-side checks: only the link parsing above; nothing about permissions or counts is computed in the app.
