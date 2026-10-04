# Feature Specification: Notifications

**Feature Branch**: `010-notifications`

**Created**: 2026-10-05

**Status**: Draft

**Input**: User description: "010-notifications: In-app notifications for the business roles (Admin, Director, Manager, Teacher; System gets none). A bell in the app header with an unread count and a NOTIFICATIONS item opening a list (newest first, unread highlighted, mark one or all as read, delete one or clear read). Everyone sees only their own. First events (specs 008 and 009): to the Teacher when their leave is approved, rejected (with the reason), revoked or cancelled by a supervisor; to the supervisors in scope (the Teacher's Manager, or Admin and Director when there is no Manager) when a Teacher submits a leave request or cancels a pending or approved one; to the Teacher when a supervisor sets or corrects one of their attendance marks (not their own marks, not leave-made marks); to the Teacher when a month of their attendance is locked or reopened. Created in the same transaction as the event, linked to the related screen, kept 90 days, paged. In-app only now, with room for SMS or push later. Follows docs/spec-roadmap.md row 010; depends on 002 and 009."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - A Teacher Hears About Their Leave Decision (Priority: P1) 🎯 MVP

When a Manager, Admin or Director approves, rejects, or revokes a Teacher's leave request, the Teacher
gets a notification without having to open My Leave History: the bell in the header shows an unread count,
and the list shows a short title ("Your leave was approved"), a message with the dates (and the reason when
it was rejected or revoked), and a link that opens My Leave History.

**Why this priority**: This is the gap the leave spec left open: a Teacher currently has to look to learn the
outcome.

**Independent Test**: As a Manager, reject one request with a reason and approve another; sign in as the
Teacher and see two unread notifications with the right text and links.

**Acceptance Scenarios**:

1. **Given** a Pending request, **When** a supervisor approves it, **Then** the Teacher has one unread
   "approved" notification naming the dates.
2. **Given** a Pending request, **When** a supervisor rejects it with a reason, **Then** the Teacher's
   notification includes that reason.
3. **Given** an Approved request, **When** a supervisor revokes it, **Then** the Teacher is notified with the
   revoke reason.
4. **Given** a decision that fails (locked month, supervisor-set day), **When** the approval is refused,
   **Then** no notification is created.
5. **Given** the Teacher cancels their own request, **When** it is cancelled, **Then** the Teacher gets no
   notification for their own action.

---

### User Story 2 - Supervisors Hear About New and Withdrawn Requests (Priority: P1)

When a Teacher submits a leave request, the Teacher's Manager gets a notification ("Tara Teacher asked for
leave, 12/10 to 13/10") linking to Leave Management. If the Teacher has no Manager, Admin and Director get it
instead. When a Teacher cancels a Pending or Approved request, the same people are told.

**Why this priority**: Requests wait for a supervisor; this tells them without polling the screen.

**Independent Test**: As Tara submit a request; as Manoj see an unread notification linking to Leave
Management; as Lakshmi (no Manager) submit one and see Admin and Director notified, not Manoj.

**Acceptance Scenarios**:

1. **Given** a Teacher with a Manager, **When** they submit a request, **Then** that Manager (and no other
   Manager) is notified.
2. **Given** a Teacher with no Manager, **When** they submit a request, **Then** every active Admin and
   Director is notified.
3. **Given** a Teacher cancels a Pending or an Approved-not-started request, **When** it is cancelled,
   **Then** the same recipients are told.
4. **Given** a Manager's assignment changes after the request was submitted, **When** the notification list
   is read, **Then** it still shows what was sent at the time.

---

### User Story 3 - The Bell and the Notifications List (Priority: P1)

A bell icon in the app header shows the number of unread notifications (capped at "99+", hidden when zero).
Clicking it, or choosing ACCOUNT → Notifications, opens the list: newest first, unread ones highlighted,
paged. A user can open a notification (which marks it read and follows its link), mark one or all as read,
delete one, or clear all read ones. The count updates without a full page reload within 30 seconds of a new
notification arriving.

**Why this priority**: The surface that makes every event visible.

**Independent Test**: With three notifications (two unread), the bell shows 2; mark all read and it hides;
delete one; clear read; the list empties.

**Acceptance Scenarios**:

1. **Given** unread notifications, **When** the user opens one, **Then** it becomes read and its link opens.
2. **Given** a list, **When** the user chooses "Mark all as read", **Then** the count drops to zero.
3. **Given** two users, **When** one reads or deletes a notification, **Then** the other's are unchanged,
   and a direct request for another user's notification is answered as not found.
4. **Given** a user without the NOTIFICATIONS permission (System), **When** they sign in, **Then** there is
   no bell and no menu item, and the API refuses them.
5. **Given** more notifications than fit a page, **When** the user scrolls to the end, **Then** older ones
   load in pages.

---

### User Story 4 - A Teacher Hears About Attendance Changes (Priority: P2)

When a Manager, Admin or Director sets or corrects one of a Teacher's attendance marks, the Teacher is told
("Manoj Manager updated your attendance for 05/10"), linking to My Attendance for that month. Several
changes by the same person to the same Teacher in the same month within 10 minutes become one notification
("Manoj Manager updated 4 days of your attendance in October"). When a month of the Teacher's attendance is
locked or reopened, the Teacher is told too.

**Why this priority**: Attendance feeds pay, so a Teacher should know when someone changes their record;
less urgent than leave.

**Independent Test**: As Manoj mark three days for Tara in a row and see one notification with a count; lock
the month as Asha and see a "month locked" notification.

**Acceptance Scenarios**:

1. **Given** a supervisor marks a day for a Teacher, **When** it is saved, **Then** the Teacher has a
   notification for it.
2. **Given** the Teacher marks their own day, **When** it is saved, **Then** no notification is created.
3. **Given** a day marked as Leave by approved leave, **When** the leave is approved, **Then** only the
   leave notification is created, not one per day.
4. **Given** the same supervisor changing several days for the same Teacher within 10 minutes, **When** the
   Teacher looks, **Then** there is one unread notification with the number of days.
5. **Given** a month is locked or reopened, **When** it completes, **Then** each affected Teacher is notified
   once.

---

### User Story 5 - Housekeeping and Future Channels (Priority: P3)

Notifications older than 90 days are removed automatically. Every notification records a delivery channel
(only "in-app" exists now), so SMS or push can be added later without changing the events.

**Why this priority**: Keeps the table small and the design open; invisible to users.

**Independent Test**: A notification dated 91 days ago disappears after the clean-up runs; a new one carries
the in-app channel.

**Acceptance Scenarios**:

1. **Given** notifications older than 90 days, **When** the daily clean-up runs, **Then** they are deleted and
   newer ones are untouched.

### Edge Cases

- An event whose transaction rolls back creates no notification (created in the same transaction).
- A recipient who is deactivated: notifications are still stored, never delivered anywhere else.
- A Teacher with no linked user account: nothing to notify; the event proceeds normally.
- Several Managers cover the same Teacher's School: each assigned Manager is notified.
- A very long reason is shortened to 200 characters in the message with the full text on the leave screen.
- A user with thousands of notifications: the list is paged and the count query stays fast.
- Deleting a notification does not affect the underlying leave request or attendance record.
- Two events at once for the same recipient: both are stored, newest first.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST create an in-app notification for the Teacher's linked user when their leave
  request is approved, rejected, revoked, or cancelled by a supervisor, with the dates and, for rejection and
  revoke, the reason (shortened to 200 characters), linking to My Leave History.
- **FR-002**: The system MUST notify the supervisors in scope when a Teacher submits a leave request, or
  cancels a Pending or Approved one: the Teacher's assigned Manager(s), or every active Admin and Director
  when the Teacher has no Manager, linking to Leave Management.
- **FR-003**: The system MUST notify a Teacher when a Manager, Admin or Director sets or corrects one of
  their attendance marks, linking to My Attendance for the month, but not for the Teacher's own marks and
  not for marks made by approved leave. Changes by the same person to the same Teacher in the same month
  within 10 minutes MUST be merged into one unread notification with a day count.
- **FR-004**: The system MUST notify a Teacher when a month of their attendance is locked or reopened.
- **FR-005**: A notification MUST be created in the same transaction as the event that causes it, so a
  rolled-back event leaves none and a committed event always has it. A failed or refused action creates none.
- **FR-006**: Each notification MUST have a recipient user, a short title (up to 80 characters), a message
  (up to 300 characters), a type, an optional link to an app screen, a created time, a read flag and time,
  and a delivery channel (only "in-app" in this spec).
- **FR-007**: A user MUST be able to list their own notifications newest first in pages, filter to unread,
  see the unread count, open one (marking it read), mark one or all as read, delete one, and clear all read
  ones. A user MUST NOT see or change another user's notifications; another user's id is answered as not
  found.
- **FR-008**: The app header MUST show a bell with the unread count (hidden at zero, "99+" above 99) that
  opens the list, and ACCOUNT MUST contain a Notifications item; the count MUST refresh at least every 30
  seconds while the app is open.
- **FR-009**: The system MUST add the permission module `NOTIFICATIONS` (View, Delete) to the seeded matrix
  for Admin, Director, Manager and Teacher, never for System, and MUST enforce it on every endpoint and
  screen; reading, marking and deleting a user's own notifications need no audit entry.
- **FR-010**: Notifications older than 90 days MUST be deleted automatically each day.
- **FR-011**: The notification text MUST contain no data the recipient could not already see on the linked
  screen (a Manager's notification names only Teachers in their scope), and System MUST receive none.
- **FR-012**: Demo data (demo flag only, idempotent) MUST give Tara, Manoj and Asha a few notifications of
  different kinds, some read and some unread.

### Key Entities

- **Notification**: recipient user, type, title, message, link, channel, created time, read time (null while
  unread), and a grouping key used to merge attendance-change notifications.
- **Notification Type**: LEAVE_DECIDED, LEAVE_REQUESTED, LEAVE_CANCELLED, ATTENDANCE_CHANGED,
  ATTENDANCE_MONTH_LOCKED, ATTENDANCE_MONTH_REOPENED.

## Role & Permission Impact *(mandatory — Constitution Principles II–IV)*

| Role     | Menu (section → item) | Default actions | Data scope |
| -------- | --------------------- | --------------- | ---------- |
| Admin    | Header bell; ACCOUNT → Notifications | View, Delete | Own |
| Director | Header bell; ACCOUNT → Notifications | View, Delete | Own |
| Manager  | Header bell; ACCOUNT → Notifications | View, Delete | Own |
| Teacher  | Header bell; ACCOUNT → Notifications | View, Delete | Own |
| System   | none | none (System MUST NOT see business data, and the events carry it) | None |

**New permission keys**: module `NOTIFICATIONS` (actions `VIEW`, `DELETE`; marking read counts as View).
Granted by default to Admin, Director, Manager and Teacher; not eligible for System. Runtime-editable in
Role & Permissions. Only Admin, Director and System may edit the role→permission matrix; this spec does not
change that.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A Teacher sees a leave decision within 30 seconds of it being made, without opening the leave
  screens.
- **SC-002**: A Manager is told of a new request from their Teacher within 30 seconds, and no other Manager
  is told, in 100% of tested cases.
- **SC-003**: 100% of events that roll back leave no notification, and 100% of committed events have exactly
  the expected notifications.
- **SC-004**: In testing with several users, 100% of lists, counts, reads and deletes affect only the
  caller's own notifications.
- **SC-005**: The unread count query returns in under 200 ms for a user with 5,000 notifications, and the
  list's first page in under 1 second.
- **SC-006**: Every notification screen is usable by keyboard alone, the unread count is announced to screen
  readers, and the screens pass the same accessibility checks as earlier ones in both themes.
- **SC-007**: Notifications older than 90 days are gone after the daily clean-up in 100% of tested cases.

## Assumptions

- In-app only: no SMS, email or mobile push in this spec; the channel field only keeps the door open.
- Polling every 30 seconds is enough for the count; real-time push to the browser is out of scope.
- The mobile app will read the same API later; no mobile screen is built here.
- Events come from specs 008 (attendance) and 009 (leave); other modules add their own events in their own
  specs by calling the same notification contract.
- A Teacher's Manager(s) are those assigned to the Teacher's School at the time of the event, from the
  shared scope APIs of spec 005.
- A user can have several roles; they get the notifications addressed to their user, whichever role asked.
