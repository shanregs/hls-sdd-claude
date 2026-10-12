# Feature Specification: Mobile Notifications

**Feature Branch**: `021-mobile-notifications`

**Created**: 2026-10-08

**Status**: Implemented (device pass pending)

**Input**: User description: "021-mobile-notifications: Notifications for the HLS Android app, built on the app shell, sign-in and server-driven menus of spec 018 and the notifications API of spec 010, which stays unchanged. A bell in the app header with the unread count (hidden at zero, 99+ above 99) and a Notifications list (ACCOUNT, Notifications): the user's own notifications newest first, unread highlighted, in pages, filter to unread; open one (marks it read and follows its link to a screen the app has, otherwise just shows it), mark one or all as read, delete one, clear all read. The count is refreshed on app open, on returning to the foreground, on pull-to-refresh and every 30 seconds while the app is open. The web links (leave history, leave management, my attendance for a month) are mapped to the screens of specs 019 and 020. No new business rules; same scope and permission rules as the web. Every call carries the device location or its reason. Out of scope: push notifications, SMS, email, offline capture, notification settings, new event types, iOS, Admin and System use. Per docs/spec-roadmap.md row 021 and docs/spec-inputs/021-mobile-notifications.md."

## Clarifications

### Session 2026-10-08

- Q: Is polling every 30 seconds while the app is in the foreground enough for the bell? → A: Yes. The count is loaded when the app opens, when it returns to the foreground, on pull-to-refresh and every 30 seconds while the app is in the foreground and a signed-in screen is showing, matching the web (spec 010 FR-008). Nothing runs in the background and there is no push.
- Q: When the user opens a notification whose link the app cannot follow, is it marked read? → A: Yes. Opening a notification is the user acknowledging it, so it is marked read in every case; the link is simply not followed and the full text stays on screen.
- Q: Which screens show the bell? → A: The app header, which every signed-in menu screen shares (Home, the menu screens and the lists), as on the web. It is not shown on Sign In, and not on the full-screen Devices and Location privacy sub-screens, which have their own back header (plan research §3).
- Q: Do the delete actions ask for confirmation? → A: "Clear read" asks for confirmation because it removes many at once; deleting one notification does not, because it only removes a message and never the leave or attendance record behind it (spec 010).
- Q: What happens when a link leads to a request or month the user can no longer see? → A: The destination screen shows its own normal state ("not found" or "not authorized", specs 019 and 020). The notification itself stays in the list.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - The Bell Shows What Is New (Priority: P1) 🎯 MVP

A signed-in Teacher, Manager or Director sees a bell in the header of the app with the number of unread
notifications on it. The number is hidden when there are none and shows "99+" above 99. It is up to date
when the app opens, when the user comes back to the app, when they pull to refresh and about every 30
seconds while they use it. Tapping the bell opens the Notifications list.

**Why this priority**: the bell is what makes a leave decision or a new request visible without opening the
leave screens, which is the reason for the feature.

**Independent Test**: as a Manager, have a Teacher submit leave; within 30 seconds the bell on the Manager's
open app shows 1; open the list and the bell drops after reading.

**Acceptance Scenarios**:

1. **Given** a user with unread notifications, **When** any screen with the app header is shown (Home, the menu screens, the lists and "Not authorized"; not the
   full-screen Devices and Location privacy sub-screens), **Then** the header
   bell shows the unread count, "99+" above 99, and no number at zero.
2. **Given** the app is open, **When** a new notification is created for the user, **Then** the bell shows
   the new count within 30 seconds, without the user doing anything.
3. **Given** the app was in the background, **When** the user returns to it, **Then** the count is
   refreshed at once.
4. **Given** the count cannot be loaded, **When** the screen shows, **Then** the bell shows no number and
   no error interrupts the screen; it tries again on the next refresh.
5. **Given** a user whose menu does not offer Notifications (System, or a role whose View was removed on
   the web), **When** the app loads, **Then** there is no bell and no Notifications item.
6. **Given** a screen reader, **When** the bell is focused, **Then** it announces the unread count.

---

### User Story 2 - Read and Open Notifications (Priority: P1)

From the bell, or from ACCOUNT, Notifications, the user opens the list of their own notifications, newest
first, with unread ones highlighted. Each shows its title, message and time. They can show only unread
ones. Opening a notification marks it read and, when its link leads to a screen the app has and the user's
menu offers, opens that screen: a leave decision opens My Leave History, a new request opens Leave
Management, an attendance change or month lock opens My Attendance for that month.

**Why this priority**: the list and its links are the working part; the bell is only the signal.

**Independent Test**: with a Teacher who has an unread "leave was rejected" and an unread "attendance
updated" notification, open the list, tap each and confirm it becomes read and the matching screen opens.

**Acceptance Scenarios**:

1. **Given** notifications, **When** the user opens the list, **Then** only their own are shown, newest
   first, unread ones highlighted, with the unread count.
2. **Given** the list, **When** the user chooses "Unread only", **Then** only unread ones are shown, and
   choosing "All" shows everything again.
3. **Given** a notification with a link to a screen the app has, **When** the user opens it, **Then** it is
   marked read, the bell count drops by one and that screen opens (for attendance links, on the month in the
   link).
4. **Given** a notification whose link leads to a screen the app does not have or the user's menu does not
   offer, **When** the user opens it, **Then** it is marked read, its full text is shown, and nothing else
   opens and no error appears.
5. **Given** a notification with no link, **When** the user opens it, **Then** it is marked read and shown
   in full.
6. **Given** a long list, **When** the user reaches the end, **Then** older notifications load in pages.
7. **Given** the server cannot be reached while marking a notification read, **When** the user opens it,
   **Then** it is not shown as read, the link is still followed, and the next refresh shows the true state.
8. **Given** a link to a request or month the user can no longer see, **When** the destination opens,
   **Then** it shows its own "not found" or "not authorized" state and the notification stays in the list.

---

### User Story 3 - Mark, Delete and Clear (Priority: P2)

The user can mark one notification read without opening its link, mark all as read, delete one, and clear
all read ones. Deleting only removes the notification, never the leave request or attendance record behind
it.

**Why this priority**: keeps the list tidy, but the list is useful without it.

**Independent Test**: with three notifications (two unread), mark all read and confirm the bell hides;
delete one; clear read and confirm the list is empty.

**Acceptance Scenarios**:

1. **Given** unread notifications, **When** the user marks one as read, **Then** it is no longer
   highlighted and the count drops by one.
2. **Given** unread notifications, **When** the user chooses "Mark all as read", **Then** the count is zero
   and the bell hides.
3. **Given** a notification, **When** the user deletes it, **Then** it disappears from the list and the
   count is corrected if it was unread.
4. **Given** read notifications, **When** the user chooses "Clear read" and confirms, **Then** every read
   one is removed and unread ones stay; without confirming, nothing is removed.
5. **Given** a user whose role has View but not Delete on notifications, **When** the list opens, **Then**
   no delete or clear action is shown, while reading and marking read still work.
6. **Given** another user's notification id (for example from an old screen), **When** the app asks for it,
   **Then** it is shown as not found and no data about it appears.
7. **Given** the server refuses or cannot be reached for any of these actions, **When** the user acts,
   **Then** the app says so in plain language, changes nothing on screen, and lets the user try the same action again.

---

### User Story 4 - Menus, Scope and Safety Come From the Server (Priority: P2)

The bell and the Notifications item appear only for users whose server-provided navigation includes them,
nothing is chosen by role name, and every call carries the device location or its reason, as in spec 018.
Each user sees only their own notifications, exactly as on the web.

**Why this priority**: keeps the app consistent with the server's permissions; it needs no screens of its
own, so it is checked alongside the others.

**Independent Test**: sign in as a Teacher, a Manager and a Director and confirm each has the bell and sees
only their own notifications; remove the View permission on the web and confirm the bell and item disappear
after the next menu refresh.

**Acceptance Scenarios**:

1. **Given** a Teacher, Manager or Director, **When** the menu loads, **Then** ACCOUNT contains
   Notifications (with Profile and Logout) and the header shows the bell.
2. **Given** a user with several roles, **When** the menu loads, **Then** the bell and item appear once,
   from the union of what the server offered.
3. **Given** two users, **When** one reads or deletes a notification, **Then** the other's are unchanged.
4. **Given** an Admin or System account, **When** it tries to sign in to the app, **Then** it is refused as
   in spec 018, so no Admin notification screens exist in the app (behaviour of spec 018, checked by that spec's tests
   and by the absence of any Admin notification screen here).
5. **Given** any call made by these screens, **When** it is sent, **Then** it carries the device location or
   the reason there is none, and behaves the same without it.
6. **Given** a user signs out, **When** they sign in as someone else on the same device, **Then** no
   notification or count of the previous user is shown.

---

### Edge Cases

- A notification created while the user is on the list: it appears at the top on the next refresh or poll;
  the list does not jump while the user is reading it.
- The user deletes a notification on the web while the app list is open: the next action on it is answered as
  not found, and the app removes it from the list.
- Notifications older than 90 days are removed by the server (spec 010); the app shows only what the server
  returns and keeps no copy.
- A very long message: shown in full on the notification and shortened in the list row.
- The device clock is wrong: times shown come from the server's timestamps; "today" and relative times
  never depend on the phone deciding what is new.
- Count over 99: shown as "99+" on the bell, and the exact number is shown on the list header.
- Attendance link with a month the Teacher has no data for: My Attendance shows its normal empty month.
- Several taps on the same notification: one read request; repeated opens are harmless because marking read
  is repeatable.
- A user's session ends while the list is open: the app returns to Sign In as in spec 018, with no
  notifications left on the device.
- Very small screens and large text: the bell, the rows and the actions stay usable and nothing is cut off.
- No connection: a failed load shows a retry and never shows stale data as current (FR-014).

## Requirements *(mandatory)*

### Functional Requirements

**Bell**

- **FR-001**: The app header, shared by every signed-in menu screen, MUST show a bell with the user's unread notification
  count, hidden at zero and shown as "99+" above 99, whenever the server's navigation offers Notifications.
  The count MUST come from the server and the app MUST NOT compute it.
- **FR-002**: The count MUST be loaded when the app opens, when it returns to the foreground, on
  pull-to-refresh of the list, after any read, delete or clear, and every 30 seconds while the app is in the
  foreground on a signed-in screen. The app MUST NOT poll in the background, and push is out of scope.
- **FR-003**: A failed count MUST show no number and no error on the screen, and MUST be retried on the next
  trigger. Tapping the bell MUST open the Notifications list.

**List and open**

- **FR-004**: The Notifications list MUST be reached from the bell and from ACCOUNT, Notifications, and MUST
  show only the signed-in user's own notifications, newest first, in pages, unread highlighted, with title,
  message, time and the unread count, and a filter between All and Unread only.
- **FR-005**: Opening a notification MUST mark it read on the server and update the bell. When its link maps
  to a screen the app has and the user's menu offers, the app MUST open that screen. When it does not, or
  there is no link, the app MUST mark it read, show its full text and open nothing else, with no error
  (clarified 2026-10-08).
- **FR-006**: The app MUST map the server's links as follows: the leave history link to My Leave History
  (spec 020), the leave management link to Leave Management (spec 020), and the my-attendance link with a
  month to My Attendance for that month (spec 019). Any other link MUST be treated as not followable. The
  mapping MUST NOT grant access: the destination applies the user's own menu and permissions.
- **FR-007**: A failure to mark read MUST NOT block following the link and MUST NOT show the notification as
  read until the server confirms it.

**Mark, delete, clear**

- **FR-008**: The user MUST be able to mark one notification read, mark all read, delete one, and clear all
  read ones. "Clear read" MUST ask for confirmation; deleting one MUST NOT (clarified 2026-10-08).
- **FR-009**: Delete and Clear read MUST be offered only when the user holds the Delete permission on
  notifications as the server's access model reports it; marking read needs View only. The app MUST NOT
  choose this by role name.
- **FR-010**: A notification that belongs to another user, or no longer exists, MUST be shown as "not found"
  with no data. Deleting a notification MUST NOT affect the leave request or attendance record it refers
  to.
- **FR-011**: No action MUST be shown as done before the server confirms it. A refusal or loss of
  connection MUST show a plain-language message, change nothing on screen and allow a retry.

**Menus, scope and shared rules (specs 018, 019, 020)**

- **FR-012**: The bell and the Notifications item MUST be present only when the server's access model
  offers them; nothing MUST be decided by role name in the app. A user without them (System, or a role
  without View) MUST see no bell and no item, and a stale destination MUST show the "not authorized" state
  of spec 018.
- **FR-013**: Every request made by these screens MUST carry the device location or the reason there is
  none, and MUST behave identically without it, exactly as in spec 018. Notification reads, marks and
  deletes need no audit entry, as in spec 010.
- **FR-014**: The screens MUST show loading, empty and error states; a failed load MUST offer a retry and
  MUST NOT show stale data as current. The app MUST NOT keep any authoritative copy of notifications, and on
  sign-out MUST clear anything it held.
- **FR-015**: The screens MUST work in light and dark themes, be usable with TalkBack and large text, announce
  the unread count on the bell, and show dates as DD/MM/YYYY (and months by name where a month is shown),
  following the date, month and refusal helpers shared with spec 019.

### Key Entities *(include if feature involves data)*

- **Notification**: as defined in spec 010: type, short title, message, optional link, time created, and
  whether it has been read. Belongs to one user.
- **Unread Count**: the number of the signed-in user's unread notifications, from the server.
- **Link Target**: the app screen a notification link maps to (My Leave History, Leave Management, My
  Attendance for a month), or none.

No new data is stored by this feature. It reads and changes the user's own notifications of spec 010
through the server.

## Role & Permission Impact *(mandatory — Constitution Principles II–IV)*

| Role     | Menu (section → item) | Default actions | Data scope |
| -------- | --------------------- | --------------- | ---------- |
| Admin    | None in the app: Admin is web-only, and an account whose only app-eligible roles are Admin or System cannot sign in to the app (spec 018) | None in the app | None |
| Director | Header bell; ACCOUNT → Notifications | View, Delete | Own |
| Manager  | Header bell; ACCOUNT → Notifications | View, Delete | Own |
| Teacher  | Header bell; ACCOUNT → Notifications | View, Delete | Own |
| System   | None (System sees no business data and cannot sign in to the app) | None | None |

**New permission keys**: none. This feature uses the existing `NOTIFICATIONS` module (View, Delete) from
spec 010; marking read counts as View. The server-provided navigation already offers the matching menu item.
Only Admin, Director and System may edit the role→permission matrix (Constitution Principle II); this feature
does not change that. A user who holds Admin together with another app role is shown only what that other
role's navigation offers. A notification link never widens access: the screen it opens applies the
permissions and scope of specs 019 and 020.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A user with the app open sees a new notification on the bell within 30 seconds of it being
  created, and immediately on returning to the app, in 100% of tests.
- **SC-002**: A user can go from the bell to the screen a notification refers to in under 10 seconds.
- **SC-003**: For test data, 100% of unread counts, titles, messages and the instant of each time shown in the app
  equal what the web shows for the same user (displayed in the viewer's own time zone).
- **SC-004**: With several users in the data, 100% of lists, counts, reads and deletes in the app affect
  only the signed-in user's own notifications; opening anyone else's shows no data.
- **SC-005**: 100% of notification links produced by spec 010 either open the matching app screen or are
  safely not followed, with no error and no crash.
- **SC-006**: No read, delete or clear is ever shown as done before the server confirms it, in 100% of
  tests, including with no connection.
- **SC-007**: A change to a user's notification permission on the web appears in their app (bell, item,
  delete actions) on the next menu refresh, with no app update, in 100% of tests.
- **SC-008**: All notification screens pass the accessibility checks in light and dark themes and are fully
  usable with TalkBack, with the unread count announced.
- **SC-009**: The app sends no notification request while it is in the background, in 100% of tests.

## Assumptions

- The server rules and API of spec 010 are unchanged: own-only access, View and Delete permissions, 90-day
  retention, paging, the unread count and the existing events and texts. No new event types are added.
- The links in notifications are the web routes of spec 010; the app maps them to its own screens and treats
  unknown links as not followable.
- Polling in the foreground every 30 seconds is enough; real-time push, SMS and email are out of scope and
  left for a later spec. The channel field of spec 010 keeps the door open.
- Delete and Clear read are available to the roles that hold Delete; deleting one does not ask for
  confirmation, clearing read does.
- A paged list uses the server's page size; the unread count in the list response is used where present, so
  opening the list needs one request.
- Admin is web-only, and System receives no notifications.
- Notification settings or preferences, offline capture, and iOS are out of scope and tracked in later specs.
- The app and server keep the location rules of spec 018: location is audit-only, best-effort, and never
  required for any action.
- This spec adds screens to the app shell of spec 018 and reuses its sign-in, session, menu and error
  handling, and the date, month and refusal helpers of spec 019, without change.
