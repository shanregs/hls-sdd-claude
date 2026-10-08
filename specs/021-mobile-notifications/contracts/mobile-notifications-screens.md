# Contract: Mobile Notifications Screens

What the app shows, and the server calls behind it. The server calls are spec 010's, unchanged (see
`specs/010-notifications/contracts/notifications-api.md`). Every call carries `Authorization`, `X-HLS-Client` and
either `X-HLS-Location` or `X-HLS-Location-Status` (spec 018). Errors use `{ "reason": "..." }`.

## Menu route (from the server's navigation, mapped by the screen registry)

| Route | Menu item | Screen |
| ----- | --------- | ------ |
| `/account/notifications` | ACCOUNT → Notifications | NotificationsScreen |

The bell opens the same route through the shell's `openRoute`. Nothing chooses the screen or the bell by role name.

## Server calls

| Where | Call | Notes |
| ----- | ---- | ----- |
| Bell | `GET /api/v1/me/notifications/unread-count` | on mount, on foreground, every 30 s while foreground; `{ unread }` |
| List | `GET /api/v1/me/notifications?page=&size=25` | All; adds `unread=true` for Unread only; reads `unread` from the response |
| Open / Mark read | `POST /api/v1/me/notifications/{id}/read` | only for an unread row; idempotent |
| Mark all read | `POST /api/v1/me/notifications/read-all` | |
| Delete one | `DELETE /api/v1/me/notifications/{id}` | only when the item's `actions` contains `DELETE` |
| Clear read | `DELETE /api/v1/me/notifications/read` | same condition; after confirmation |

## Bell

- An `Appbar.Action` in the shell header, shown when the access model offers `/account/notifications`.
- Number: hidden when `count` is 0 or `null`; the exact number up to 99; `99+` above 99.
- Accessibility label: `Notifications, N unread` (`Notifications` with no number when none or unknown).
- Tap opens the Notifications screen. 48 dp target.

## Notifications screen

- Header line: `N unread` (exact number) and the filter chips **All** and **Unread only**.
- Actions: **Mark all as read** (when `unread` > 0); **Clear read** (only with Delete; asks to confirm).
- Row: title (bold when unread, with an unread marker that is not colour alone), two lines of message, date and time
  `DD/MM/YYYY HH:mm`. Overflow actions: **Mark as read** (when unread) and **Delete** (only with Delete).
- Tap a row: mark read if unread, then follow the link when it maps (below) and the menu offers it; otherwise open the
  detail dialog (full title, message, time, Close).
- States: loading, empty ("No notifications" / "No unread notifications"), error with Retry, no connection with
  Retry. **Load more** at the end of a full page. Pull-to-refresh reloads from page 0 and refreshes the count.
- Messages (plain language, nothing shown as done first): "Couldn't mark it as read. Try again.",
  "Couldn't delete it. Try again.", "You're not allowed to delete notifications.", "This notification no longer
  exists." (404, row removed), "No connection. Nothing was changed."

## Link mapping

| Server link | Opens | Condition |
| ----------- | ----- | --------- |
| `/leave/history` | My Leave History | menu offers `/leave/history` |
| `/operations/leave` | Leave Management | menu offers `/operations/leave` |
| `/my-attendance` | My Attendance (current month) | menu offers `/my-attendance` |
| `/my-attendance?month=YYYY-MM` | My Attendance for that month | as above; month 01 to 12; outside the picker's range opens the current month |
| anything else | nothing (detail dialog) | |

The destination screen applies the user's own permissions and scope; the mapping never widens access.
