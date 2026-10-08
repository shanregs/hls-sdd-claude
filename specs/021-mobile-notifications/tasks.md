---

description: "Task list for feature implementation"
---

# Tasks: Mobile Notifications

**Input**: Design documents from `/specs/021-mobile-notifications/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/mobile-notifications-screens.md,
quickstart.md, and **specs 010, 018, 019 and 020 implemented** (this feature adds a bell and a screen to the app
shell of 018, opens screens of 019 and 020 from notification links, and calls the notification endpoints of 010
unchanged; there is no backend work).

**Tests**: included as first-class tasks, written before the code they cover. Constitution Principle IX requires
per-scope tests (two users), menu-visibility tests, and tests that every refusal leaves things unchanged and that
nothing is shown as done before the server confirms it (SC-004 to SC-007); SC-009 needs a test that nothing is
requested in the background.

**Organization**: grouped by user story in priority order (spec.md US1-US4). Shared pieces (API client, wording,
provider-independent helpers, route) come first because every story uses them.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: US1-US4; absent for Setup/Foundational/Polish

## Path Conventions

`MOB` = `mobile/src`, `MOBT` = `mobile/__tests__`. All work is under `mobile/`; `backend/` and `frontend/` are not
changed. Run commands from the `mobile` folder with JDK 17 (see `docs/running-mobile.md`). The server shapes are in
`data-model.md` and the 010 contract (`specs/010-notifications/contracts/notifications-api.md`).

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: folders and realistic test data.

- [X] T001 Create the folders `MOB/notifications/` and `MOBT/notifications/`, and confirm `npm test`, `npm run lint` and `npm run typecheck` pass from `mobile/` before any change
- [X] T002 [P] Create `MOBT/support/notificationsFixtures.ts` with builders that copy the real shapes of data-model.md: `notification({id, type, title, message, link, read, createdAt})` (defaults `channel: "IN_APP"`, `updatedAt` equal to `createdAt`; `link` is an app route starting with `/` or null), `notificationPage(content, {page, size, totalElements, unread})`, and ready-made texts copied from the 010 contract table (leave approved, rejected and revoked linking `/leave/history`; new request and withdrawn request linking `/operations/leave`; attendance changed, month locked and month reopened linking `/my-attendance?month=2026-10`)
- [X] T003 [P] Create `MOBT/support/notificationsServer.ts` that registers the notification routes on the existing `FakeServer` (`GET /api/v1/me/notifications`, `GET /api/v1/me/notifications/unread-count`, `POST .../{id}/read`, `POST .../read-all`, `DELETE .../{id}`, `DELETE .../read`) for the signed-in user only, applies reads, deletes and clears to its state so a reload shows the result, newest first, honours `unread=true`, `page` and `size`, returns `unread` on every page, answers 404 for another user's id, records calls with their headers, lets a test hold a route pending, make it answer a refusal such as `{status: 403, body: {reason}}`, or go offline, and lets a test add a notification while the app is open; add an `openNotificationsScreen(setup)` helper (with an `open?: "Notifications" | "home"` option, so bell tests can start on Home) beside `MOBT/support/leaveApp.ts` (a sibling file) that signs in, installs the attendance, leave and notification routes and opens Notifications from the drawer

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: the API client, constants, wording, route and test menu entries every story uses.

**⚠️ CRITICAL**: no user story work can begin until this phase is complete.

- [X] T004 Create `MOB/api/notificationsApi.ts` with the types of data-model.md (`NotificationRow {id, type, title, message, link: string | null, channel, read, createdAt, updatedAt}`, `NotificationPage {content, page, size, totalElements, unread}`) and typed calls: `listNotifications({unreadOnly, page, size = 25})` (adds `unread=true` only when `unreadOnly`), `getUnreadCount()` returning the `unread` number, `markRead(id)`, `markAllRead()`, `deleteNotification(id)`, `clearRead()`; all through `api` from `MOB/api/httpClient.ts` so the location headers of spec 018 are added
- [X] T005 [P] Add to `MOB/config/constants.ts` `NOTIFICATION_POLL_MS = 30_000` and `NOTIFICATION_PAGE_SIZE = 25` with a one-line comment each (matching the web `POLL_MS` of spec 010 FR-008)
- [X] T006 [P] Create `MOB/notifications/notificationMessages.ts`: `notificationFailureText(action, error)` for `"read" | "readAll" | "delete" | "clear"` returning "Couldn't mark it as read. Try again.", "Couldn't mark them as read. Try again.", "Couldn't delete it. Try again.", "Couldn't clear the read notifications. Try again.", "You're not allowed to delete notifications." for a 403 on delete or clear, "No connection. Nothing was changed." for `NoConnectionError`, and `isGone(error)` true for a 404 (message "This notification no longer exists."); add `MOBT/notifications/notificationMessages.test.ts` covering each branch
- [X] T007 Register the screen: in `MOB/access/screenRegistry.ts` add `ScreenKey` `"notifications"` mapped from `/account/notifications` and export `NOTIFICATIONS_ROUTE = "/account/notifications"`; in `MOB/navigation/AppShell.tsx` render a placeholder `NotificationsScreen` (create `MOB/screens/NotificationsScreen.tsx` exporting a component that renders a header text only) with title "Notifications"; update `MOBT/support/fixtures.ts` so the Teacher, Manager and Director menus gain ACCOUNT → `Notifications` at `/account/notifications` with actions `["VIEW", "DELETE"]` (and one fixture variant with `["VIEW"]` only), and update `MOBT/access/menuFromAccessModel.test.tsx` and the Home test in `MOBT/screens/notAuthorized.test.tsx` for the new entry

**Checkpoint**: API client, constants, wording and route exist; the screen is a placeholder.

---

## Phase 3: User Story 1 - The Bell Shows What Is New (Priority: P1) 🎯 MVP

**Goal**: a signed-in Teacher, Manager or Director sees a bell with the unread count in the app header, kept up to date, which opens the Notifications screen.

**Independent Test**: as a Manager, create a notification on the fake server while the app is open; within 30 seconds the bell shows 1; background the app and no request is made; return and the bell updates at once (quickstart scenario 1).

### Tests for User Story 1

- [X] T008 [P] [US1] `MOBT/notifications/bell.test.tsx`: the bell shows the count from `GET /unread-count`, hides at 0 and while unknown, shows `99+` above 99 (exact `99` at 99), has the accessibility label "Notifications, 3 unread" ("Notifications, 0 unread" at zero and "Notifications, count unavailable" while unknown, never the bare "Notifications" which is the drawer item's name), is at least 48 dp, and tapping it opens the Notifications screen through `openRoute`; there is no bell and no Notifications menu item when the access model lacks `/account/notifications` (a System-like model, or a role without View); with two roles that both offer the item there is one bell and one item
- [X] T009 [P] [US1] `MOBT/notifications/polling.test.tsx` (fake timers and a mocked `AppState`): one count request on mount; a notification added on the fake server appears on the bell after 30 seconds without any tap; going to `background` stops the timer and no request is made for 5 minutes (SC-009); returning to `active` requests at once and restarts the timer; a failed request leaves no number and no error text on screen and the next tick retries; only one request is in flight at a time (a slow response does not queue a second); unmounting the shell (sign-out) stops the timer
- [X] T010 [P] [US1] `MOBT/notifications/shellHeader.test.tsx`: the bell is in the shell header on Home, My Attendance and a leave screen; it stays and keeps its count when the user opens Devices or Location privacy and returns (the provider is not reset by those full-screen sub-screens); those sub-screens show no bell (research §3); the bell also shows on the "Not authorized" screen, because it is in the same header, and keeps its count

### Implementation for User Story 1

- [X] T011 [US1] Create `MOB/notifications/NotificationsProvider.tsx`: a context `{ enabled, count: number | null, canDelete, refreshCount(), setCount(n) }`; `enabled` is true when `useAccessModel().model` contains `/account/notifications`; `canDelete` is true when that item's `actions` contains `"DELETE"`; when enabled it calls `getUnreadCount()` on mount, on `AppState` change to `active`, and every `NOTIFICATION_POLL_MS` while the state is `active` (clear the interval on `background` and `inactive` and on unmount); one request in flight (ignore a tick while pending); when a poll and a list response overlap, the later response sets the count; a failure sets `count` to `null` and is not shown; `setCount` is for list responses; export `useNotifications()` that throws outside the provider; keep everything in memory (no storage)
- [X] T012 [P] [US1] Create `MOB/notifications/NotificationBell.tsx`: an `Appbar.Action` with icon `bell-outline` and a Paper `Badge` showing the count (hidden when 0 or `null`, `99+` above 99), `accessibilityLabel` "Notifications, N unread" or "Notifications", a 48 dp target (`minTouchTarget` from `MOB/theme/tokens.ts`), `onPress` calls a given `onOpen`; renders nothing when `enabled` is false
- [X] T013 [US1] In `MOB/navigation/AppShell.tsx` wrap `ShellContent` in `NotificationsProvider` inside `AccessModelProvider` (so the early returns for overlays do not reset it) and add `<NotificationBell onOpen={() => openRoute(NOTIFICATIONS_ROUTE)} />` to the `Appbar.Header` after `Appbar.Content`; leave the Devices and Location privacy sub-screens and the loading and error frames without a bell

**Checkpoint**: US1 is complete and demonstrable on its own (the MVP): the bell shows, refreshes and opens a placeholder screen.

---

## Phase 4: User Story 2 - Read and Open Notifications (Priority: P1)

**Goal**: the user opens the list of their own notifications, filters to unread, opens one (marks it read) and lands on the screen its link points to, or sees its full text.

**Independent Test**: with a Teacher who has an unread "leave was rejected" and an unread "attendance updated" notification, open the list, tap each, and confirm it becomes read and My Leave History / My Attendance on the linked month opens (quickstart scenarios 2 and 3).

### Tests for User Story 2

- [X] T014 [P] [US2] `MOBT/notifications/linkTarget.test.ts`: `linkTarget("/leave/history")`, `"/operations/leave"`, `"/my-attendance"` and `"/my-attendance?month=2026-10"` map to their route (with `month` only for the last); `/my-attendance?month=2026-13`, `?month=2026-00`, `?month=2026-1`, `?month=abc` and `?month=` give `{route: "/my-attendance"}` with no month (the screen opens on the current month); any other query parameter (`?month=2026-10&x=1`, `?x=1`) gives `null`, as does a query on `/leave/history` or `/operations/leave`; `null`, `""`, `"leave/history"`, `"https://example.com/leave/history"`, `"//evil.example"` and `"/unknown"` give `null`; plus a table-driven case over the eight link values in T002's fixtures, each giving a target or `null` and never throwing
- [X] T015 [P] [US2] `MOBT/notifications/notificationsList.test.tsx`: opening the list from the bell and from ACCOUNT → Notifications shows only the signed-in user's own rows newest first with title, a two-line message and the date and time (`formatDateTime` of the server's `createdAt`, not a relative time; the test fixes the time zone through the jest setup so the expected text is stable), unread rows marked in a way that is not colour alone and listed in the row's accessibility label, the exact unread count in the list header (also above 99) and Load more at the end of a full page that appends the next page (25 per request); All and Unread only chips send `unread=true` only for Unread only; pull-to-refresh reloads from page 0 and refreshes the bell; the loading, empty ("No notifications" and "No unread notifications"), error (Retry) and no-connection (Retry) states; a failed load drops the rows (never stale data shown as current); a new notification added on the server while the list is open does not move the rows until a refresh, while the bell updates
- [X] T016 [P] [US2] `MOBT/notifications/openNotification.test.tsx`: tapping an unread row sends one `POST /read`, then the row is read and the bell count falls by one, and the linked screen opens (`/leave/history` My Leave History, `/operations/leave` Leave Management, `/my-attendance?month=2026-10` My Attendance on October 2026); tapping a read row sends no read call but still follows the link; a link the menu does not offer, a link the app has no screen for (`/something-else`) and a notification with no link mark the row read and open the detail dialog with the full title, message and time, and nothing else, with no error; when marking read fails (500 or offline) the link is still followed, the row stays unread and a short notice appears; two quick taps send one read request; a 404 on read removes the row and says "This notification no longer exists."; opening a notification whose destination the user can no longer see shows that screen's own "Not authorized" or "not found" state and the notification stays in the list; a table-driven case opens each of the eight fixture notifications of T002 and checks that it either opens the expected screen or shows the detail dialog, with no error (SC-005)
- [X] T017 [P] [US2] `MOBT/attendance/myAttendanceMonth.test.tsx`: My Attendance given `initialMonth` "2026-10" opens that month; a month in the previous year opens with the history range (the previous year's month is selectable); a month outside the previous and current year, or one not shaped `YYYY-MM`, opens the current month; a second link to another month re-initialises the screen; opening My Attendance from the drawer is unchanged

### Implementation for User Story 2

- [X] T018 [P] [US2] Create `MOB/notifications/linkTarget.ts`: `linkTarget(link: string | null): { route: string; month?: string } | null` per research §7 and the Contract's link table (accepted routes `/leave/history`, `/operations/leave` and `/my-attendance`, the first two with no query; `/my-attendance` may carry only `month`, kept when it matches `^\d{4}-(0[1-9]|1[0-2])$` and dropped otherwise; any other query parameter, any other route and any link not starting with a single `/` return `null`); pure, no I/O
- [X] T019 [P] [US2] Create `MOB/notifications/useNotificationList.ts`: `useNotificationList(unreadOnly)` returns `{state: "loading" | "ready" | "error" | "noConnection", items, unread, hasMore, loadMore, reload, loadingMore, remove(id), markLocalRead(id)}`; a result belongs to the filter it was loaded for (another filter shows loading, never old rows), a failed load drops the rows, pages of `NOTIFICATION_PAGE_SIZE` are appended, a page failure keeps the earlier pages and shows the error; every response's `unread` is passed to `setCount` of the provider
- [X] T020 [P] [US2] Create `MOB/notifications/NotificationRow.tsx` (title bold when unread, two-line message, `formatDateTime(createdAt)` from `MOB/formats/dates.ts`, an unread marker that is not colour alone, `accessibilityLabel` such as "Unread. Your leave was rejected. 05/10/2026 08:30", `onPress`, disabled while its own request is in flight) and `MOB/notifications/NotificationDetailDialog.tsx` (Paper dialog with the full title, message, time and a Close button, reachable by TalkBack)
- [X] T021 [US2] Make the app screen real in `MOB/screens/NotificationsScreen.tsx` (replace the placeholder of T007): a `Screen` with `onRefresh` that reloads the list and the count, the header line "N unread", the All / Unread only chips, the rows of T020, Load more, and the states of T015; opening a row calls `markRead(id)` (unread rows only; one request per row at a time), on success updates the row and calls `refreshCount()`, then resolves `linkTarget(row.link)` and, only when it is not null and `canOpen(target.route)` is true, calls `openRoute(target.route, { month: target.month })`; otherwise opens the detail dialog; a failed read shows `notificationFailureText("read", error)`, leaves the row unread and still follows a followable link; a 404 removes the row (`isGone`)
- [X] T022 [US2] Pass the month through the shell: in `MOB/navigation/AppShell.tsx` add `month?: string` to `RouteState`, pass `routeState?.month` to `MyAttendanceScreen`, and render `<NotificationsScreen openRoute={openRoute} canOpen={canOpen} />`; in `MOB/screens/MyAttendanceScreen.tsx` accept optional `initialMonth`, pass it to `MonthPane` with `key={initialMonth ?? "current"}`, and use `kind="history"` when `initialMonth` is in the previous business year, otherwise `"current"` (research §2); an out-of-range or malformed month is ignored

**Checkpoint**: US2 is complete: the bell leads to a working list, and links open the right screens, on their own without US3.

---

## Phase 5: User Story 3 - Mark, Delete and Clear (Priority: P2)

**Goal**: the user can mark one notification read without following its link, mark all read, delete one, and clear read ones, with Delete offered only where the server says so.

**Independent Test**: with three notifications (two unread), mark all read and confirm the bell hides; delete one; clear read and confirm the list is empty (quickstart scenario 4).

### Tests for User Story 3

- [X] T023 [P] [US3] `MOBT/notifications/markAndDelete.test.tsx`: the row action "Mark as read" (unread rows only) sends `POST /read`, un-highlights the row, drops the bell by one and does not follow the link; "Mark all as read" (shown only when unread > 0) sends `POST /read-all`, and the bell hides and every row shows read; "Delete" sends `DELETE /{id}` without a confirmation, the row disappears and the count is corrected if it was unread; for a user whose Notifications item has actions `["VIEW"]` only, no Delete and no Clear read are shown while Mark as read and Mark all as read still work, and the decision uses the item's actions, never a role name
- [X] T024 [P] [US3] `MOBT/notifications/clearRead.test.tsx`: "Clear read" opens a confirmation (title, text that unread ones are kept, Cancel and Confirm); Cancel sends nothing and removes nothing; Confirm sends `DELETE /read`, removes every read row and keeps the unread ones; the action is hidden without Delete; it is disabled when there is nothing read to clear in the loaded list
- [X] T025 [P] [US3] `MOBT/notifications/notificationRefusals.test.tsx`: for mark, mark all, delete and clear, a 403, a 500 and an offline server each show the plain message of T006, leave the rows and the count unchanged (nothing shown as done, SC-006) and allow the same action again, which then succeeds; deleting a notification that no longer exists (404) removes it and says so; another user's notification id is a 404 and shows no data (SC-004)

### Implementation for User Story 3

- [X] T026 [P] [US3] Create `MOB/notifications/ConfirmDialog.tsx`: a Paper dialog `{title, text, confirmLabel, onConfirm(): Promise<string | null>, onCancel}` disabled while sending, with an error line (role alert) and the dialog kept open when `onConfirm` resolves with a message; add `MOBT/notifications/ConfirmDialog.test.tsx` (closes only on success, error shown and kept, Cancel closes, accessible names) **Done by reuse:** no `ConfirmDialog` was built; Clear read uses `leave/ReasonDialog` in its no-text mode (title, message, confirm and dismiss labels, error line, kept open on failure), which `__tests__/leave/ReasonDialog.test.tsx` already covers.
- [X] T027 [US3] In `MOB/notifications/NotificationRow.tsx` add the overflow actions "Mark as read" (unread only) and "Delete" (only when `canDelete`), each with an accessible name that includes the title; in `MOB/screens/NotificationsScreen.tsx` add "Mark all as read" and "Clear read" (the latter only when `canDelete`, through `ConfirmDialog`), wire `markRead`, `markAllRead`, `deleteNotification` and `clearRead` so the screen changes only after a 2xx, refresh the list state and `refreshCount()` afterwards, show failures with `notificationFailureText` and keep the rows on failure

**Checkpoint**: US3 is complete and testable on its own.

---

## Phase 6: User Story 4 - Menus, Scope and Safety Come From the Server (Priority: P2)

**Goal**: prove the bell and screen follow the server's access model, each user sees only their own notifications, every call carries the location or its reason, and nothing survives sign-out.

**Independent Test**: sign in as a Teacher, a Manager and a Director and confirm each has the bell and sees only their own notifications; remove View in the access model and confirm the bell and item disappear after the next menu refresh (quickstart scenario 5).

### Tests for User Story 4

- [X] T028 [P] [US4] `MOBT/notifications/menuAndScope.test.tsx`: the Teacher, Manager and Director fixtures show the bell and ACCOUNT → Notifications once each; a refreshed access model without `/account/notifications` removes the bell and the item and a stale open screen shows "Not authorized" (SC-007); two users with different notifications on the fake server each see only their own list, counts, reads and deletes, and one user's actions leave the other's unchanged (SC-004, US4 scenario 3); a refreshed access model whose Notifications item loses `DELETE` removes Delete and Clear read from an already-open list while Mark as read stays (SC-007); an Admin or System account has no notification screen in the app (behaviour of spec 018)
- [X] T029 [P] [US4] `MOBT/notifications/headersAndSignOut.test.tsx`: every notification request carries `X-HLS-Client` and either `X-HLS-Location` or `X-HLS-Location-Status`, and the screen behaves identically with location denied (FR-013); signing out and signing in as another user on the same device shows no earlier count, rows or detail (FR-014, US4 scenario 6); a session that ends while the list is open returns to Sign In with nothing left on screen
- [X] T030 [P] [US4] `MOBT/api/notificationsContract.test.ts`: each call of `MOB/api/notificationsApi.ts` sends the method, path, query and body of the 010 contract exactly (`unread=true` only for Unread only, `page`, `size=25`, no body on read, read-all and delete) and parses the real response shapes of data-model.md including `unread` on the page
- [X] T031 [P] [US4] Extend the existing checks to the new files: confirm `MOBT/access/noHardCodedRoles.test.ts` scans `MOB/notifications/` and `MOB/screens/NotificationsScreen.tsx` with no role literal (add no exceptions), and add `MOBT/a11y/notifications.a11y.test.tsx` following `MOBT/a11y/leave.a11y.test.tsx` (the bell, the list with unread and read rows, empty and error states, both dialogs and the row actions: roles, names, 48 dp targets, light and dark)

**Checkpoint**: all four stories pass their own tests.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [ ] T032 [P] Large text and small screens: check the bell, the rows, the header line, the chips and both dialogs at the largest font scale on a 360 dp wide screen in `MOBT/a11y/notifications.a11y.test.tsx` (nothing cut off, nothing overlapping), fixing any layout problem in the files above **Pending device pass:** Jest cannot render font scale or measure layout. By design the rows, action buttons and chips wrap (`flexWrap`, `flexShrink`) and every target is at least 48 dp (checked in `notifications.a11y.test.tsx`); confirm at the largest text size on a 360 dp screen in T035.
- [X] T033 [P] Update `docs/running-mobile.md` with a manual checklist item for notifications (the six scenarios of `quickstart.md`) and note that Devices and Location privacy have no bell
- [X] T034 Run `npm run typecheck`, `npm run lint`, `npm run format -- --check` (or `npx prettier --check .`) and `npm test` from `mobile/` and fix anything they report; confirm no existing test changed except the menu and Home tests named in T007 **Result:** typecheck and lint clean; 75 suites, 792 tests pass (655 before this feature). Prettier is not enforced in this app (172 existing files already differ, mostly line endings), so no formatting-only changes were made.
- [ ] T035 Run the emulator pass of `quickstart.md` (including the bell-to-destination timing and the comparison with the web in scenario 2) against the real backend (scenarios 1-6) including TalkBack, light and dark, and record the results, the build and the date in `specs/021-mobile-notifications/quickstart-results.md`; mark the device pass pending there if no device is available, as for 019 and 020 **Pending:** needs the real backend and an emulator or device; automated results are recorded in `quickstart-results.md`.
- [X] T036 Update the status of row 021 in `docs/spec-roadmap.md` (Implemented, device pass pending or done) and make sure `specs/021-mobile-notifications/spec.md` Status is set to Implemented

---

## Dependencies & Execution Order

**Phase order**: Setup → Foundational → US1 → US2 → US3 → US4 → Polish. US1 is the MVP.

**Story dependencies**:
- **US1** needs Foundational only; its tap opens the placeholder screen of T007.
- **US2** needs Foundational and the provider of US1 (`refreshCount`, `setCount`, `canDelete`) and the header route of T013 to be reachable from the bell; its tests can be written once Phase 2 is done.
- **US3** needs US2's screen and row (T020, T021) because it adds actions to them.
- **US4** tests need the screens they exercise; T030 can start after T004.

**Key task ordering**:
- T004 before every task that calls the server and before T030; T003 before every test that uses the fake server.
- T006 before T021 and T027; T007 before T013, T021 and T022.
- T011 before T012, T013, T019 and T021; T012 before T013.
- T018 before T021; T019 and T020 before T021; T021 before T022 (the shell passes `openRoute` and `canOpen`).
- T026 before T027; T021 before T027.
- Tests in each story are written first and must fail before their implementation tasks.

## Parallel Opportunities

- Setup: T002 and T003 in parallel.
- Foundational: T005 and T006 in parallel after T004; T007 is independent of them but follows T004.
- Within each story, all test tasks (`[P]`) in parallel, then the implementation tasks (US1: T011 then T012 and T013; US2: T018, T019 and T020 in parallel, then T021, then T022; US3: T026 then T027).
- After Phase 2, US2's tests (T014-T017) can be written while US1's implementation is built.
- US4 tests T028-T031 are different files and run in parallel once US3 is done.

## Implementation Strategy

1. **MVP**: Phases 1-3 (the bell shows, refreshes and opens the screen), demonstrable on the emulator.
2. **Increment 2**: US2 (list, open and follow links), then US3 (mark, delete, clear), each testable alone.
3. **Increment 3**: US4 (menu, scope, headers, sign-out, contract, role-name and accessibility checks), then Polish and
   the device pass (T035), which is the only part that needs the real backend and the emulator.
