# Feature Specification: Mobile Leave

**Feature Branch**: `020-mobile-leave`

**Created**: 2026-10-05

**Status**: Draft

**Input**: User description: "020-mobile-leave: Leave screens for the HLS Android app, built on the app shell, sign-in and server-driven menus of spec 018, the shared attendance screens of spec 019 and the leave rules and APIs of spec 009, which stay unchanged. Teacher: Apply Leave (type, first and last date, optional half day at start or end, reason, the server's preview before submitting) and My Leave History (own requests, filter by status, cancel when the server offers it). Manager and Director: Leave Management (requests in scope, Pending first with a count, filter by status, detail with the days the request would mark, approve, reject, revoke exactly when the server offers it). The Pending count appears on the Manager and Director Home. Menu entries come only from the server's access model. Every call carries the device location or its reason. Out of scope: attachments, balances, push notifications, offline capture, Admin and System use, editing a request, export, iOS."

## Clarifications

### Session 2026-10-05

- Q: Must a Teacher write a reason when applying for leave? → A: Yes. Corrected 2026-10-05 after reading the server (spec 009): it refuses an empty reason ("Give a reason.") on both preview and submit, so the reason is required and the app keeps Submit disabled until one is entered (up to 500 characters). The app still enforces nothing else the server does not.
- Q: Can an approver approve or reject from a row of the Leave Management list? → A: No. A row only opens the request; Approve, Reject and Revoke are offered only on the request's detail screen, where the days it would mark and any conflicts are shown first.
- Q: After the server accepts a leave request, what does the app show next? → A: My Leave History, with the new Pending request at the top and a short "Request submitted" message. The Apply Leave form is cleared, so the same request cannot be sent twice by tapping again.
- Q: Should the app check the draft itself before asking the server? → A: Only for what needs no rules or clock: a missing leave type, date or reason, and a last date before the first date (the preview and Submit stay disabled until fixed). The 30-day and 90-day limits, overlap, locked months, placement and half-day rules are never checked in the app; the server's preview and refusals decide them.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - A Teacher Applies for Leave (Priority: P1) 🎯 MVP

A Teacher opens LEAVE → Apply Leave in the app. They choose a leave type from the list the server
offers, a first and a last date, optionally mark the first or the last day as a half day, and write a
reason. Before anything is sent, the app shows the server's preview: how many working days the request
covers and which days count, with the other dates of the range marked weekly off, holiday or not
counted, and any problem the server finds. Submitting creates a Pending request, and the Teacher sees it at the top of their
history.

**Why this priority**: applying for leave is the reason a Teacher opens the leave screens, and it is the
step that starts the whole flow. Without it nothing else in leave can happen on the phone.

**Independent Test**: sign in as a placed Teacher, apply for three days with a half day at the end,
read the preview, submit, and confirm a Pending request with the right working days appears.

**Acceptance Scenarios**:

1. **Given** a placed Teacher, **When** they open Apply Leave, **Then** they can choose a leave type, a
   first date, a last date, a half day at the start and/or at the end, and enter a reason.
2. **Given** a complete draft, **When** the Teacher asks for the preview, **Then** the app shows the
   server's working-day total and counted days, labels the other dates of the range "Weekly off",
   "Holiday" or "Not counted", and shows any problem the server reports, without creating anything.
3. **Given** a valid draft, **When** the Teacher submits, **Then** a Pending request is created, the
   app opens My Leave History with a short "Request submitted" message and the new request, with its
   dates and working days, at the top; the Apply Leave form is cleared.
4. **Given** a draft the server refuses (overlap with another request, a start more than 30 days ago, a
   locked month, a range longer than 90 days, last date before first, no working day, both half-day
   flags on a one-day range, or the Teacher not placed), **When** the Teacher previews or submits,
   **Then** the app shows the reason in plain language, creates nothing, and keeps everything they
   entered.
5. **Given** the server cannot be reached while submitting, **When** the Teacher taps Submit, **Then**
   the app says there is no connection, keeps the draft, and lets them try again; nothing is shown as
   submitted until the server confirms it.
6. **Given** a Teacher whose profile is not yet linked to a Teacher record, **When** they open Apply
   Leave, **Then** the app shows the server's message instead of the form.

---

### User Story 2 - A Teacher Follows and Cancels Their Requests (Priority: P1)

A Teacher opens LEAVE → My Leave History and sees their requests, newest first: each with its leave type,
dates, working days, status, who decided it and when, and the note or reason. They can filter by status.
Where the server offers Cancel for a request (a Pending one, or an Approved one whose first day is still in
the future), the Teacher can cancel it.

**Why this priority**: Teachers need to know whether leave was approved before they plan around it, and
to withdraw a request they no longer need.

**Independent Test**: with one Pending, one Approved and one Rejected request, confirm each shows the
right status and decision details, filter by status, and cancel the Pending one.

**Acceptance Scenarios**:

1. **Given** a Teacher with requests, **When** they open My Leave History, **Then** only their own
   requests are listed, newest first, each with type, dates, working days, status and decision details.
2. **Given** the list, **When** the Teacher filters by a status, **Then** only requests of that status
   are shown, and choosing "All" shows every request again.
3. **Given** a Rejected request, **When** the Teacher opens it, **Then** the supervisor's reason is shown.
4. **Given** a request for which the server offers Cancel, **When** the Teacher cancels it and confirms,
   **Then** it becomes Cancelled and, if it was Approved, the days it marked disappear from My
   Attendance after the next load.
5. **Given** a request for which the server does not offer Cancel (already started, decided against, or
   cancelled), **When** the Teacher opens it, **Then** no Cancel action is shown.
6. **Given** the Teacher cancels a request that a supervisor decided in the meantime, **When** the server
   refuses, **Then** the app explains this in plain language and shows the request as it now is.
7. **Given** a long history, **When** the Teacher reaches the end of the list, **Then** they can load more.

---

### User Story 3 - A Manager or Director Decides Requests (Priority: P1)

A Manager or Director opens OPERATIONS → Leave Management. They see the requests in their scope, Pending
by default, with the number of Pending requests and a filter by status. A Manager sees only the Teachers in
their assigned Zones and Schools; a Director sees everyone. Opening a request shows who asked, the type,
dates, working days, reason and the days it would mark. Where the server offers the action, they can
approve it (with an optional note), reject it (a reason is required) or, for an Approved request, revoke
it (a reason is required).

**Why this priority**: the decision is the core business step of leave, and Managers and Directors are
mostly away from a desk.

**Independent Test**: as a Manager with two in-scope Pending requests and one from another Manager's
Teacher, confirm only the two appear, approve one and reject the other with a reason, and see both
reflected in the Teacher's history.

**Acceptance Scenarios**:

1. **Given** a Manager or Director, **When** they open Leave Management, **Then** Pending requests in
   their scope are listed by default (the other statuses through the filter, with no "All"), with the
   Pending count, and a Teacher outside a Manager's scope never
   appears.
2. **Given** the list, **When** they filter by another status, **Then** requests of that status are
   shown.
3. **Given** a Pending request, **When** the approver opens it, **Then** its details and the days it
   would mark are shown, and Approve and Reject are offered.
4. **Given** a Pending request, **When** the approver approves it (with or without a note), **Then** it
   becomes Approved, showing who decided and when, and the Pending count goes down.
5. **Given** a Pending request, **When** the approver rejects it without a reason, **Then** the app does
   not send it; with a reason it becomes Rejected and the Teacher will see the reason.
6. **Given** an Approved request, **When** the server offers Revoke and the approver revokes it with a
   reason, **Then** it becomes Cancelled and its marks are removed.
7. **Given** an approval the server refuses (days a supervisor already marked, a locked month, already
   decided, cancelled, or changed since it was opened), **When** the approver submits, **Then** the app
   explains the reason in plain language (naming the days or the month when the server does), reloads the
   request and shows its current state.
8. **Given** a request outside the approver's scope (for example from an old screen), **When** the app
   asks for it, **Then** it shows "not found" and no data about that Teacher.

---

### User Story 4 - Pending Leave Is Visible From Home (Priority: P2)

On the Home screen of a Manager or Director, a widget shows how many leave requests are waiting for a
decision. Tapping it opens Leave Management on the Pending list. It replaces the "coming soon" card for
the section that now has a screen.

**Why this priority**: it tells approvers at a glance that something needs doing, but the decisions can
be made without it.

**Independent Test**: with three Pending requests in scope, sign in as the Manager and confirm Home shows
3 and tapping it opens the Pending list; decide one and confirm the count is 2 after returning to Home.

**Acceptance Scenarios**:

1. **Given** a Manager or Director with Pending requests in scope, **When** Home opens, **Then** the
   widget shows the count and tapping it opens Leave Management.
2. **Given** no Pending requests, **When** Home opens, **Then** the widget says so, still opening Leave
   Management when tapped.
3. **Given** a user whose menu does not offer Leave Management (for example a Teacher), **When** Home
   opens, **Then** no leave widget is shown.
4. **Given** the count cannot be loaded, **When** Home opens, **Then** the widget shows it could not be
   loaded, with no number, and the rest of Home still works.

---

### User Story 5 - Menus, Scope and Safety Come From the Server (Priority: P2)

The leave screens appear in the menu only for users whose server-provided navigation includes them, no
screen is chosen by role name, and what each button may do comes from the request's own allowed actions.
Every request still carries the device location or its reason, as in spec 018, and every change is audited
by the server exactly as on the web.

**Why this priority**: it keeps the app consistent with the server's permissions, and needs no new screens
of its own, so it is checked alongside the others.

**Independent Test**: sign in as a Teacher, a Manager and a Director and confirm each sees only the leave
entries the server offered; remove a permission on the web and confirm the entry disappears after the
next menu refresh.

**Acceptance Scenarios**:

1. **Given** a Teacher, **When** the menu loads, **Then** it shows Apply Leave and My Leave History and no
   Leave Management entry.
2. **Given** a Manager or Director, **When** the menu loads, **Then** it shows Leave Management and no
   Teacher self-service leave entries.
3. **Given** a user with several roles, **When** the menu loads, **Then** it is the union of what the
   server offered, with no duplicates.
4. **Given** a request shown in any list or detail, **When** the server offers no action on it, **Then**
   the app shows no action button for it, whatever the request's status or dates look like.
5. **Given** any change made from the app, **When** it is saved, **Then** the server's audit history shows
   it as from the Android app with the device location or the reason it is missing.
6. **Given** an Admin or System account, **When** it tries to sign in to the app, **Then** it is refused as
   in spec 018, so no Admin leave screens exist in the app.

---

### Edge Cases

- A range that crosses a month end where only the later month is locked: the server refuses the whole
  request and names the month; the app shows that and keeps the draft.
- A one-day range: only one half-day flag makes sense; the app leaves that rule to the server and shows the
  server's refusal in plain language if it receives one.
- First or last date is a weekly off or holiday: allowed; the preview shows those days as not counted.
- The Teacher changes a date after previewing: the old preview is cleared and must be asked for again
  before submitting, so a submitted request always matches the preview shown.
- A request approved or rejected while the Teacher has My Leave History open: the Teacher sees the new
  status on the next load or refresh; actions are always taken from the latest server response.
- Two approvers act on the same request: one succeeds; the other is told it was already decided and sees the
  current state.
- The approver opens a request the Teacher cancelled meanwhile: the app says it was cancelled and shows it
  as cancelled.
- The device clock is wrong: "today", the 30-day limit and "first day in the future" come from the server's
  business date and the server's allowed actions, never from the phone's clock.
- Very small screens and large text: forms and lists stay usable and nothing is cut off.
- A very long reason or note: the app limits input to the length the server accepts (500 characters) and
  shows how much is left.
- Pending count while the approver is on the list: it is refreshed when the screen opens, after a decision
  and on pull-to-refresh, not continuously.

## Requirements *(mandatory)*

### Functional Requirements

**Teacher: Apply Leave**

- **FR-001**: A placed Teacher MUST be able to compose a leave request with a leave type chosen from the
  server's active list, a first date, a last date, optional half-day-at-start and half-day-at-end flags,
  and a reason of up to 500 characters that is required, as the server requires it (corrected
  2026-10-05). The app MUST NOT hard-code the leave types.
- **FR-002**: Before submitting, the app MUST be able to show the server's preview of the draft: the number
  of working days, the counted days (the other dates of the range are labelled Weekly off, Holiday or Not
  counted) and any problems, without creating anything. A change to any field MUST clear the shown preview so only a preview of the
  current draft can precede a submit. The app MUST itself block only a missing leave type, date or reason and a
  last date before the first date (clarified 2026-10-05); every other rule is decided by the server.
- **FR-003**: Submitting MUST create a Pending request only after the server confirms it, and then MUST
  open My Leave History showing the new request at the top and clear the form (clarified 2026-10-05). When the server
  refuses (overlap with another request, start more than 30 days before today, a locked month, a range
  longer than 90 days, last date before first, no working day, both half-day flags on a one-day range, or
  the Teacher not placed), the app MUST show the reason in plain language, create nothing, and keep the
  Teacher's entries.
- **FR-004**: With no connection the app MUST keep the draft on screen and let the Teacher retry. There is
  no offline queue in this release.

**Teacher: My Leave History**

- **FR-005**: The app MUST list only the signed-in Teacher's own requests, newest first, in pages, each
  showing leave type, first and last date, half-day flags, working days, status, who decided it and when,
  and the note or reason. The list MUST be filterable by status.
- **FR-006**: A Cancel action MUST be offered on a request only when the server lists it among that
  request's allowed actions, and MUST ask for confirmation before sending. After a cancel the request and
  the list MUST be reloaded from the server.

**Manager and Director: Leave Management**

- **FR-007**: The app MUST list the requests the server returns for the signed-in Manager or Director,
  Pending by default, in pages, with the count of Pending requests in scope, and MUST let the user
  filter by status (there is no "All" on Leave Management). The server decides who is in scope; the app MUST NOT show any other Teacher's request.
- **FR-008**: The app MUST show a request's detail: Teacher, School, leave type, dates, half-day flags,
  working days, reason, status and decision details, and the days that approving it would mark, as given by
  the server.
- **FR-009**: Approve, Reject and Revoke MUST each be offered only on the request's detail screen (never on
  a list row; clarified 2026-10-05) and only when the server lists that action for the request. Approve MAY carry a note. Reject and Revoke MUST require a reason (up to 500 characters) before
  anything is sent, and each of the three MUST ask for confirmation. Every action MUST send the request's
  version so a change made meanwhile is detected.
- **FR-010**: When the server refuses an action (days set by a supervisor, a locked month, already decided,
  cancelled, changed since opened, or out of scope), the app MUST show the reason in plain language,
  naming the days or month when the server does, reload the request, and show its current state.
- **FR-011**: A request or list outside the user's scope MUST be shown as "not found" with no data about the
  Teacher.

**Home**

- **FR-012**: The Home screen of a user whose menu offers Leave Management MUST show the number of Pending
  requests in scope as a widget that opens Leave Management on the Pending list, replacing the "coming
  soon" card for that section. A Home without that menu entry MUST show no such widget, and a failed
  count MUST not hide the rest of Home.

**Menus and shared rules (specs 018 and 019)**

- **FR-013**: The leave screens MUST be reached only through menu entries that the server's access model
  offers; an entry the server does not offer MUST NOT be shown, and nothing MUST be decided by role name
  in the app. What a button may do MUST come from the request's allowed actions, never from its status,
  dates or the phone's clock.
- **FR-014**: The app MUST show a "not authorized" state, never data, for a destination the user no longer
  has.
- **FR-015**: Every request made by these screens MUST carry the device location or the reason there is
  none, and MUST behave identically without it, exactly as in spec 018.
- **FR-016**: Every create, cancel, approve, reject and revoke MUST be recorded by the server in its audit
  history as in spec 009. The app MUST NOT keep any authoritative copy of leave data.
- **FR-017**: Leave approved on the web or in the app MUST appear in My Attendance (spec 019) on its next
  load, and cancelled or revoked leave MUST disappear from it, with no extra work in the app.
- **FR-018**: The screens MUST work in light and dark themes, be usable with TalkBack and large text, and
  show dates as DD/MM/YYYY (and months by name where a month is shown), following the product's
  conventions and the month, date and refusal helpers shared with spec 019.
- **FR-019**: Loading, empty and error states MUST be shown on every screen; a failed load MUST offer a
  retry and MUST NOT show stale data as current.

### Key Entities *(include if feature involves data)*

- **Leave Type**: a named kind of leave the server offers (for example Casual, Sick, Personal, Other). It is
  informational: every approved type is recorded in attendance as Leave.
- **Leave Request**: a Teacher's request with type, first and last date, half-day flags, working days,
  reason, status (Pending, Approved, Rejected, Cancelled), who decided it and when, the note or reason, a
  version, and the list of actions the server allows the viewer to take on it.
- **Leave Preview**: the server's answer for a draft: working days, one entry per day with its kind, and the
  problems found. It is never stored.
- **Pending Count**: the number of Pending requests in the viewer's scope, from the server.

No new data is stored by this feature. It reads and writes the leave requests of spec 009 through the
server.

## Role & Permission Impact *(mandatory — Constitution Principles II–IV)*

| Role     | Menu (section → item) | Default actions | Data scope |
| -------- | --------------------- | --------------- | ---------- |
| Admin    | None in the app: Admin is web-only, and an account whose only app-eligible roles are Admin or System cannot sign in to the app (spec 018) | None in the app | None |
| Director | OPERATIONS → Leave Management | View, Approve (approve, reject, revoke) | Org-wide |
| Manager  | OPERATIONS → Leave Management | View, Approve (approve, reject, revoke) for Teachers in scope | Assigned (Zones → Schools → Teachers) |
| Teacher  | LEAVE → Apply Leave, My Leave History | View, Create, Delete (cancel own Pending, or Approved not yet started) | Own |
| System   | None (System sees no business data; System-only accounts cannot sign in to the app) | None | None |

**New permission keys**: none. This feature uses the existing leave permissions from spec 009:
`MY_LEAVE` (View, Create, Delete) and `LEAVE_MANAGEMENT` (View, Approve). The server-provided navigation
already offers the matching menu items. Only Admin, Director and System may edit the role→permission matrix
(Constitution Principle II); this feature does not change that. A user who holds Admin together with
another app role is shown only what that other role's navigation offers.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A Teacher can apply for leave, including reading the preview, in under 90 seconds from opening
  Apply Leave, on a typical mobile connection.
- **SC-002**: A Manager or Director can decide (approve or reject) a Pending request in under 30 seconds
  from opening Leave Management.
- **SC-003**: For test data, 100% of the working-day counts, statuses and decision details shown in the app
  equal what the web shows for the same request.
- **SC-004**: For every refusal the server can give when applying, cancelling, approving, rejecting or
  revoking, the app shows a plain-language message, changes nothing, and keeps what the user entered, in
  100% of tests.
- **SC-005**: With two Managers' Teachers in the data, 100% of lists and details opened by a Manager contain
  only that Manager's Teachers; opening any other request shows no data.
- **SC-006**: No request or decision is ever shown as saved before the server confirms it, in 100% of tests,
  including with no connection.
- **SC-007**: A button for an action (cancel, approve, reject, revoke) is shown exactly when the server
  lists that action for the request, in 100% of tests, regardless of the request's dates.
- **SC-008**: A change to a user's permissions on the web appears in their app menu on its next refresh,
  with no app update, in 100% of tests.
- **SC-009**: All leave screens pass the accessibility checks in light and dark themes and are fully usable
  with TalkBack.
- **SC-010**: Every leave change made from the app appears in the server's audit history, marked as from the
  Android app, in 100% of tests.

## Assumptions

- The server rules of spec 009 are unchanged: leave types, the working-day count from weekly offs and
  holidays, the overlap rule, the 30-day look-back, the 90-day limit, atomic approval, revoke and cancel
  rules, scope from the shared scope queries, and audit.
- The app uses the server's own results (working days, previews, allowed actions, counts) and computes none
  of them.
- Dates are chosen with a simple in-app date chooser for a first and last date, followed by the server's
  preview; a calendar showing holidays while choosing dates is not needed, because the preview names them.
- Approve is sent after the optional note is entered and a confirmation; Reject and Revoke require a reason
  and a confirmation. No action is sent by a single accidental tap, and none is available from a list row.
- Leave Management is filtered by status only on the phone (Pending by default); filtering by Teacher,
  School or month stays on the web.
- The Pending count is loaded when a screen opens, after a decision and on pull-to-refresh, not on a timer.
- The Home widget shows the count only, not a list of requests.
- The Director on the phone has the same actions as the Manager, over the whole organization, as the server
  allows. Admin is web-only.
- Attachments such as medical certificates, leave balances or entitlements, push notifications, offline
  capture, editing a submitted request (cancel and apply again), CSV or PDF export, and iOS are out of scope
  and tracked in later specs.
- The app and server keep the location rules of spec 018: location is audit-only, best-effort, and never
  required for any action.
- This spec adds screens to the app shell of spec 018 and reuses its sign-in, session, menu and error
  handling, and the date, month and refusal helpers of spec 019, without change.
