# Research: Mobile Notifications

Decisions taken before design, checked against the real code of spec 010 (`backend/.../notification/`,
`NotificationController`, `NavigationCatalog`), its contract (`specs/010-notifications/contracts/notifications-api.md`),
the web `NotificationBell` and `notificationsApi.ts`, and the app after specs 018 to 020 (`mobile/`).

## 1. No server changes

- **Decision**: app-only. Endpoints used, all from spec 010 and already authorised for Admin, Director, Manager and
  Teacher (System gets 403): `GET /api/v1/me/notifications?unread=&page=&size=`, `GET .../unread-count`,
  `POST .../{id}/read`, `POST .../read-all`, `DELETE .../{id}`, `DELETE .../read`.
- **Rationale**: the server owns scope (own only), retention (90 days), permissions and texts. The links the server
  writes are fixed by `MessageFactory` (`/leave/history`, `/operations/leave`, `/my-attendance?month=YYYY-MM`).
- **Alternatives considered**: a push channel (FCM); rejected as out of scope in the spec (the `channel` field keeps the
  door open).

## 2. Open question: does 019 My Attendance accept a month?

- **Finding**: `MonthPane` already takes `initialMonth` (and `onMonthChange`), but `MyAttendanceScreen` does not pass
  it and the shell's `RouteState` has no month. The month picker offers only `allowedMonths(kind)`; `"current"` is the
  current year only.
- **Decision**: add a `month` to `RouteState`; `MyAttendanceScreen` takes an optional `initialMonth` and passes it to
  `MonthPane`, with a `key` on the month so a second link re-opens it. Use `kind="history"` (previous and current year)
  when the linked month is in the previous year, because a month-lock or reopen notification for December realistically
  arrives in early January; otherwise keep `"current"`. A month outside both years, or not shaped `YYYY-MM`, is ignored
  and the screen opens on the current month, as for an ordinary open. No fallback screen is needed.
- **Rationale**: smallest change to a shipped screen; nothing else in 019 moves.
- **Alternatives considered**: always `"history"` for My Attendance (changes the picker for everyone); rejected.

## 3. Open question: does the 018 shell have a shared header for the bell?

- **Finding**: yes. `AppShell.tsx` renders one `Appbar.Header` (menu action and title) for Home and every menu screen.
  Two sub-screens, Devices and Location privacy, return early with their own back header, and so do the loading, error
  and no-connection states; they have no menu header today.
- **Decision**: put the bell in that one header, to the right of the title, so every screen the user reaches from the
  menu shows it (Home, attendance, leave, profile, notifications, "Not authorized" with a menu). It is **not** added to
  the Devices and Location privacy sub-screens, the loading or error frames, Sign In or the update-required screen. This
  refines clarification Q3 ("every signed-in screen") to "every screen with the app header".
- **Alternatives considered**: restructuring the shell so every frame shares one header; rejected as a change to 018
  well beyond this spec.

## 4. Open question: is the list response's `unread` enough?

- **Finding**: yes. The list response is `{content, page, size, totalElements, unread}`, so opening the list needs
  one request. `GET /unread-count` (`{ "unread": n }`) is the cheap call for the bell's polling.
- **Decision**: the bell polls `/unread-count`; the list screen reads `unread` from every page response and pushes it
  into the same count, so list and bell never disagree after a load. After a read, read-all, delete or clear the list
  screen reloads from page 0 only after the server confirms, and the count comes from that response.

## 5. Where the count lives and when it refreshes

- **Decision**: a `NotificationsProvider` placed under `AccessModelProvider` in `AppShell` (not inside `ShellContent`,
  which returns early for overlays and would reset it). It is enabled only when the access model offers
  `/account/notifications`. It holds `count: number | null` in memory, refreshes on mount, on `AppState` change to
  `active`, on a 30 s interval started only while the state is `active`, and on demand (`refreshCount()`), with one
  request in flight at a time. A failure sets the count to `null` (no number, no error) and the next trigger retries.
  It stops the interval when the state is `background` or `inactive` (SC-009) and leaves nothing on disk. Signing out
  unmounts it with the shell, so the next user starts empty (FR-014).
- **Rationale**: matches the web (spec 010 FR-008) and the existing `AccessModelProvider` use of `AppState`.
- **Alternatives considered**: polling from the bell component itself (resets on every screen change); a global store
  (a new dependency for one number).

## 6. The Delete permission

- **Finding**: each item in the server's access model carries `actions`, the granted actions of its module for the
  caller's roles (`AccessModelService.grantedActions`). The Notifications item is gated on `NOTIFICATIONS`/VIEW.
- **Decision**: offer Delete and Clear read only when the `/account/notifications` item's `actions` contains `DELETE`.
  Marking read needs `VIEW`, which the item's presence proves. The server still answers 403 if the matrix changed
  meanwhile, shown in plain language (FR-011).

## 7. Link handling

- **Decision**: a small pure function parses `link` into a path and optional `month` and returns a target or `null`:
  `/leave/history`, `/operations/leave`, `/my-attendance` (with a valid `month`, or without one). Anything else, a
  query other than `month`, or a non-`/` link returns `null`. A target is followed only when `canOpen(path)` is true
  (the menu offers it and the app has the screen). The shell's `openRoute` would show "Not authorized" for a refusal, so
  the notification screen checks `canOpen` first and shows the text instead (FR-005); a destination that becomes
  unavailable later shows its own state.
- **Rationale**: the mapping grants nothing (FR-006).

## 8. Opening a notification and the detail

- **Decision**: tapping a row calls `POST .../{id}/read` when it is unread; a read one skips the call. Whatever the
  result, a followable link is followed. When there is no followable link, a dialog shows the full title, message and
  time with Close. The row becomes read, and the count falls, only after the server confirms (FR-007, FR-011); a failed
  read shows a short notice and the row stays unread. Several quick taps send one request while one is in flight.
  A 404 (deleted elsewhere) removes the row and shows "This notification no longer exists."

## 9. List behaviour

- **Decision**: pages of 25 (`size=25`); the next page loads when the list reaches its end ("Load more" button, which
  is also the accessible path). Filter chips All and Unread only (the `unread=true` query). Pull-to-refresh reloads
  from page 0 and refreshes the count. A poll updates only the bell, never the list, so the list does not move under
  the user's finger (edge case). Rows show title, a two-line message, and the server's `createdAt` formatted as
  DD/MM/YYYY HH:mm with the existing `formatDateTime`, in the phone's time zone like the other screens; no relative times, so the phone clock decides nothing (edge case).

## 10. Confirmation and destructive actions

- **Decision**: Clear read opens a confirmation dialog (the existing `leave/ReasonDialog`, which supports a plain confirmation
  with no text field, so no new dialog is built); delete one acts at once. Both update the screen only after the server confirms. "Clear read"
  is disabled when the loaded list has no read notification and the filter is Unread only, to avoid an empty action.

## 11. Dependencies and tooling

- **Decision**: no new package. Bell uses React Native Paper `Appbar.Action` with `Badge`; list uses `FlatList`
  inside the existing screen frame, or `Screen`'s `ScrollView` with mapped rows (decide at tasks: the existing leave
  lists map inside `Screen`, so do the same for consistency and the existing pull-to-refresh).
- **Alternatives considered**: expo-notifications for push; out of scope.

## 12. Testing approach

- **Decision**: Jest and React Native Testing Library against the in-memory `FakeServer` with notification routes
  (`__tests__/support/notificationsServer.ts`) and fixtures copying the real response shape; fake timers for polling
  and `AppState` mocking for foreground/background; request-shape contract test; the existing role-name scan and
  accessibility checks extended to the new files. Manual: emulator pass and TalkBack.
