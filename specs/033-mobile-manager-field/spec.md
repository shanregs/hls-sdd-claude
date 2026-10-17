# Feature Specification: Mobile Manager Field Day

**Feature Branch**: `033-mobile-manager-field`

**Created**: 2026-10-17

**Status**: Draft

**Input**: User description: "033-mobile-manager-field: Field-day screens for the Manager in the HLS Android app, built on the app shell, sign-in and server-driven menus of spec 018 and the Manager attendance rules and APIs of spec 032, which stay unchanged. Full input: docs/spec-inputs/033-mobile-manager-field.md (check-in with required device location and optional photo and day type; field-day activity selection from the server's planned-activity list or purpose text where 032 accepts it; check-out with location and the complete-with-a-reason action for a missed check-out; Home plan showing today's status and planned activities; My month reusing the 019 month component; Manager leave request and the Director's decision as in spec 020; menu entries and allowed actions come only from the server access model; refusals shown in plain language with entered data kept; every call carries device location or its reason and every change is audited server-side). Out of scope: Admin/Director grid, corrections and month lock (web), geofencing, offline check-in, background tracking, push notifications, iOS."

## Clarifications

### Session 2026-10-17

- Q: May a Manager attach a photo from the phone's gallery as well as the camera, and are the image's own details recorded beyond what spec 032 keeps? → A: Camera or gallery. The photo stays optional. Only what spec 032 already holds is recorded, and what the app can show is what the server returns with the photo (file name, type and size; corrected 2026-10-17 after reading the server, which exposes neither who attached it nor when); nothing is added to spec 032, so the time or place a picture was taken is not read from the file.
- Q: Should the app refuse a check-in or check-out when the location fix is poor? → A: No. The app sends whatever fix the phone gives together with its accuracy figure and never refuses for accuracy; only a missing location (denied, switched off or not found in the short wait) stops the request.
- Q: May the Manager change a day's type or add activities after checking in? → A: No. The day type and links stay as checked in; a wrong one is corrected by the Admin or Director on the web, with a reason (spec 032), and the app offers no edit of a recorded check-in.
- Q: Does Home show only today's planned activities or the coming days too? → A: Today only. A Field day can link only to an activity dated that day, and planning ahead belongs to the activity calendar (spec 035).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - A Manager Checks In From the Phone (Priority: P1) 🎯 MVP

On a working day a Manager opens MY ATTENDANCE → Check-in in the app. They choose the kind of day the
server offers, Office or Field. For a Field day they pick one or more of today's planned activities
(school visits, college drives, tasks), or, where the server still accepts it, write a short purpose.
They tap Check in. The app reads the phone's location at that moment, sends it with the request, and shows
the time the server recorded. They may add a photo of the place; a check-in without a photo is just as valid.

**Why this priority**: the Manager's day starts with the check-in, and the deck's Manager is in the field,
not at a desk. Without it, none of the other Manager screens has anything to show.

**Independent Test**: sign in as a Manager with a joining date, allow location, check in as Office and
confirm today's status shows the server's time; repeat on another day as Field with a planned visit
selected and a photo added, and confirm the day shows the visit and the photo.

**Acceptance Scenarios**:

1. **Given** a Manager on a working day who has not checked in, **When** they open Check-in, **Then** the
   app shows the day types the server offers (Office, Field) and, for Field, the planned activities the
   server returns for today, exactly as returned.
2. **Given** an Office day, **When** the Manager taps Check in with location available, **Then** the
   server records the check-in, the app shows "Checked in at <time>" from the server's response, and the
   Check-in action is no longer offered.
3. **Given** a Field day, **When** the Manager selects one or more planned activities (or writes a purpose
   where the server accepts one) and checks in, **Then** the day is recorded as Field with those links or
   that purpose, and the app shows them on today's status.
4. **Given** a Field day with neither an activity nor a purpose, **When** the Manager taps Check in,
   **Then** the app does not send it and says which of the two is needed; a Field day the server refuses
   anyway is shown with the server's reason and everything entered is kept.
5. **Given** a Manager who is checking in, **When** they add a photo (taken in the app or chosen from the phone's gallery) of a type and size the server
   accepts, **Then** the photo is attached to that day's check-in and shown on today's status with the details
   the server returns with it (file name, type and size); a photo the server refuses is
   explained and the check-in itself stays as recorded.
6. **Given** location is denied, switched off or not found within the short wait of spec 018, **When**
   the Manager taps Check in, **Then** the app sends nothing, says that a location is needed for
   check-in and how to turn it on (or to allow it for this app), and offers to try again; nothing is
   shown as checked in.
7. **Given** a Manager who is on approved full-day leave today, who is checking in a second time, whose
   month is locked, or who has no joining date recorded, **When** they try to check in, **Then** the
   server's plain-language reason is shown and nothing is recorded. On approved half-day leave one
   check-in is accepted.
8. **Given** the server cannot be reached, **When** the Manager taps Check in, **Then** the app says there
   is no connection, keeps what was entered, and lets them try again; nothing is shown as checked in until
   the server confirms it.

---

### User Story 2 - A Manager Checks Out, and Completes a Missed Check-out (Priority: P1)

At the end of the day the Manager taps Check out. The app reads the phone's location at that moment and
sends it. If an earlier day was left without a check-out, that day shows a flag, "No check-out", and the
Manager can complete it with a time on that day and a reason.

**Why this priority**: a check-in without a check-out leaves a flagged day for the Director to review.
Completing the day is part of the same daily habit and the only way the Manager can clear the flag
themselves.

**Independent Test**: check in, then check out and confirm the time shows; leave a past day without a
check-out and complete it with a time and a reason, and confirm the flag clears.

**Acceptance Scenarios**:

1. **Given** a Manager who has checked in today, **When** they tap Check out with location available,
   **Then** the server records the time and location, and today's status shows "Checked out at <time>".
2. **Given** a Manager who has not checked in today, or who has already checked out, **When** they open
   today, **Then** Check out is not offered; if the server still refuses it, its reason is shown.
3. **Given** location is unavailable, **When** the Manager taps Check out, **Then** nothing is sent and
   the app explains how to turn location on, as for check-in.
4. **Given** a past day with a check-in and no check-out, **When** the Manager views it (Home or My
   month), **Then** it carries a "No check-out" flag and a "Complete check-out" action.
5. **Given** the Complete check-out form, **When** the Manager enters a time and a reason and sends it,
   **Then** the server completes that day's check-out and the flag clears; a missing reason, a time not
   on that day, a future time, or a locked month is refused with the server's reason and the entry is
   kept.
6. **Given** the server cannot be reached, **When** the Manager checks out or completes a check-out,
   **Then** the app says there is no connection, keeps the entry, and shows nothing as done until the
   server confirms.

---

### User Story 3 - Home Shows Today and Today's Plan (Priority: P2)

The Manager's Home shows where today stands: not checked in, checked in at a time (and Office or Field),
or checked out. It also shows today's planned activities, each with what it is and where it is. It says
when today is a weekly off, a holiday or a leave day, so the Manager is not asked to check in. Tapping an
activity opens its record where the app has a screen for it, and otherwise shows its details.

**Why this priority**: it turns the check-in into a daily start page and tells a Manager what to pick for
a Field day before they open Check-in. It is useful without the month and leave screens.

**Independent Test**: sign in as a Manager with a planned visit and a task today, read the Home card
before checking in, after checking in and after checking out; then repeat on a holiday.

**Acceptance Scenarios**:

1. **Given** a Manager on a working day, **When** they open Home, **Then** they see today's status and
   the planned activities the server returns for today, in the server's order.
2. **Given** the three states (not checked in, checked in, checked out), **When** the Manager acts,
   **Then** Home reflects the new state after the server confirms it, without leaving the screen.
3. **Given** today is a weekly off or a holiday, or the Manager is on approved leave, **When** they open
   Home, **Then** it says so and does not offer Check in.
4. **Given** the cut-off for a missed check-in has passed with no check-in and no leave, **When** the
   Manager opens Home, **Then** the card says they have not checked in and offers Check in (the server
   marks a late check-in; the app does not).
5. **Given** a planned activity, **When** the Manager taps it, **Then** the app opens its record if it
   has a screen for that kind, and otherwise shows the activity's kind, place, date and status.
6. **Given** today has no planned activities, **When** the Manager opens Home, **Then** the card says
   so; a Field day can then be checked in with a purpose where the server accepts one.

---

### User Story 4 - A Manager Sees Their Own Month (Priority: P2)

The Manager opens MY ATTENDANCE → My Month and sees each day of the month with its status (worked,
Office or Field, leave, weekly off, holiday, missed), the check-in and check-out times, the purpose or
linked activities of a Field day, and flags such as "No check-out", "Late" or "Added later". The month
figures from the server (days worked, leave, missed) appear at the top. They can move between months.

**Why this priority**: it lets the Manager check what the Director and payroll will see and find the
days that need completing. It reuses the month component of spec 019, so the cost is low.

**Independent Test**: sign in as a Manager with a mix of days in the month and confirm every day, flag
and figure matches what the web Manager Attendance screen shows for that Manager.

**Acceptance Scenarios**:

1. **Given** a Manager, **When** they open My Month, **Then** they see the current month with a status
   for each day, the times, the purpose or links, the flags, and the month figures, all as the server
   returns them; the app calculates none of them.
2. **Given** a day with a flag that the Manager can act on (a missing check-out), **When** they tap it,
   **Then** the Complete check-out action of story 2 is offered.
3. **Given** a locked month, **When** the Manager views it, **Then** it is shown as locked and no
   action that would change it is offered.
4. **Given** the Manager moves to a previous month, **When** it loads, **Then** its days and figures are
   shown; the months offered are those the server returns data for (see Assumptions).

---

### User Story 5 - A Manager Requests Leave and Sees the Decision (Priority: P2)

The Manager opens MY ATTENDANCE → My Leave. They request leave by choosing a leave type from the list the
server offers, a first and a last date, optionally a half day at the start or end, and a reason. They see
their requests, newest first, with status and the decision (approved, rejected with the Director's
reason, revoked), and can cancel a pending request. This follows the pattern of spec 020 against the
Manager leave rules of spec 032.

**Why this priority**: leave is the other half of a Manager's attendance; an approved request turns
working days into leave days that the check-in screens then respect. It is independent of check-in.

**Independent Test**: request three days of leave, see it Pending, have the Director approve it on the
web, and confirm the app shows Approved and today's Home reflects leave on a covered day.

**Acceptance Scenarios**:

1. **Given** a Manager, **When** they open My Leave, **Then** they see their requests, newest first,
   each with type, dates, working days, status and, when decided, the decision and reason.
2. **Given** a complete request, **When** the Manager submits it, **Then** a Pending request is created
   and appears at the top with a short "Request submitted" message; the form is cleared so the same
   request is not sent twice.
3. **Given** a request the server refuses (dates reversed, no working day in the range, before the
   joining date, no joining date, a missing reason, a half day on a middle day, or an overlap with
   another pending or approved request), **When** the Manager submits, **Then** the server's reason is
   shown in plain language and everything entered is kept.
4. **Given** a Pending request, **When** the Manager cancels it, **Then** the server cancels it and the
   list shows it as cancelled; an approved request has no cancel action (the approver revokes it).
5. **Given** a request that the Director rejected or revoked, **When** the Manager opens it, **Then**
   the reason given is shown.

---

### User Story 6 - A Manager Asks to Add a Forgotten Check-in (Priority: P3)

When the Manager forgot to check in on a past day, they can ask for it to be added: the date, the time
they arrived, Office or Field (with a purpose for Field), and a reason. The request goes to the Director
or Admin, and the Manager sees it Pending and then Approved ("added later") or Rejected with the reason.
A Pending request can be cancelled.

**Why this priority**: it is part of the same 032 rules and the Manager is on the phone, but it is
needed less often than the daily check-in and can follow once the main flow works.

**Independent Test**: ask to add a check-in for yesterday, see it Pending, approve it on the web, and
confirm yesterday shows as worked and "Added later" in My Month.

**Acceptance Scenarios**:

1. **Given** a past day without a check-in in a month that is not locked, **When** the Manager asks to
   add a check-in with a time and a reason, **Then** a Pending request is created and listed.
2. **Given** a future day, a day that already has a check-in, a day with a pending request, a locked
   month, or no reason, **When** the Manager submits, **Then** the server's reason is shown and the
   entry is kept.
3. **Given** a Pending request, **When** the Manager cancels it, **Then** it is cancelled and listed so.
4. **Given** a decided request, **When** the Manager opens it, **Then** the outcome and, for a rejection,
   the reason are shown.

---

### User Story 7 - Menus, Permissions and Safety Come From the Server (Priority: P2)

The Manager sees these screens only because the server's access model offers them, and each button is
shown only when the server says the Manager may use it. Teacher, Admin and System accounts get none of
these screens. Whatever a refusal says is shown in plain language, and what the Manager typed is kept.

**Why this priority**: the app adds no business rules of its own (spec 018). This keeps the phone and the
web agreeing even when an Admin changes permissions.

**Independent Test**: sign in as a Manager, a Teacher and a Director and confirm only the Manager sees
MY ATTENDANCE; then remove a permission in Role & Permission Management and confirm the matching entry
or button disappears after the next load.

**Acceptance Scenarios**:

1. **Given** a Manager signed in to the app, **When** the menu loads, **Then** MY ATTENDANCE appears with
   Check-in, My Month and My Leave exactly as the server's navigation model returns them.
2. **Given** a Teacher or Director, **When** they use the app, **Then** these entries do not appear; a
   deep link to one shows the not-authorized screen.
3. **Given** the server removes a permission, **When** the Manager next opens the screen, **Then** the
   matching button is gone and any attempt is refused by the server with its reason.
4. **Given** any refusal (already checked in, locked month, no planned activity, leave day, conflict,
   permission), **When** the app shows it, **Then** the message is the server's reason in plain
   language, not a code, and the data entered is kept.
5. **Given** a signed-out or expired session, **When** the Manager acts, **Then** they are taken to
   sign-in as in spec 018 and nothing is shown as saved.

---

### Edge Cases

- A Manager checked in as Office and then went on a visit, or picked the wrong activity: the app offers no change
  to a recorded check-in; the Admin or Director corrects the day on the web with a reason.
- Location fix is slow or imprecise: the app waits only the short time of spec 018, then tells the
  Manager location was not found; it does not send a check-in or check-out without a location and does
  not judge accuracy (the server stores the figure as captured).
- The Manager taps Check in twice quickly: only one request is sent while one is in flight, and a second
  check-in the server refuses is shown with its reason; no duplicate is shown.
- The phone's clock is wrong: the app displays the server's recorded times only.
- A day's check-in succeeded but the photo upload failed (large file, wrong type, connection lost): the
  check-in stays, the photo can be added again from today's status, and the failure is explained.
- A Field day is checked in with a planned activity that was removed or re-dated meanwhile: the server
  refuses it with its reason, the list of today's activities is reloaded, and the other entries are kept.
- Several planned activities of different kinds on one day: the Manager can select more than one; the
  server's list order is kept.
- Today is a weekly off or a holiday and the Manager wants to check in: not offered; if the server
  would still accept it, the app shows what the server returns for the day.
- A Manager with no joining date recorded (spec 005a): the server's message is shown on check-in and
  leave; the app does not hide the screens.
- The month is locked while the Manager has the day open: the completing or requesting action is
  refused with the server's reason and the entry is kept.
- A Manager who also holds Admin or Director: the app shows only what the server's navigation offers.
- The app is in the background for a long time: nothing is sent and no location is read until the
  Manager acts again.

## Requirements *(mandatory)*

### Functional Requirements

**Check-in**

- **FR-001**: The app MUST let a Manager check in once for a working day, choosing the day type the
  server offers (Office or Field); a Leave day arises only from approved leave and is never chosen at
  check-in.
- **FR-002**: The app MUST read the phone's location at the moment of the Check in tap and send it with
  the request, together with the accuracy the phone reports, and MUST NOT refuse a fix for being imprecise. If location is denied, switched off or not found within the wait of spec 018, the app MUST
  NOT send the check-in and MUST tell the Manager how to turn location on. This is the one action in the
  app for which location is required rather than best-effort (decision D15 of spec 032).
- **FR-003**: For a Field day the app MUST let the Manager select one or more of today's planned
  activities from the list the server returns for the day, showing it exactly as returned, and MUST
  offer a purpose text where the server accepts one. It MUST NOT send a Field day with neither, and MUST
  say which of the two is needed.
- **FR-004**: The app MUST let the Manager add a photo to the day's check-in, optionally, by taking one in
  the app or choosing one from the phone's gallery, in a type and size the server accepts. A check-in without
  a photo is valid, and a refused photo MUST NOT undo the check-in. The app MUST show the details the server
  returns with an attached photo (file name, type and size) and MUST NOT read or store
  the time or place a picture was taken; spec 032 is not changed to keep them.
- **FR-005**: The app MUST show the time of a check-in only from the server's response, and MUST NOT show
  a check-in as made until the server confirms it.

**Check-out and completion**

- **FR-006**: The app MUST let a Manager who has checked in check out, reading and sending the location
  at the moment of the tap under the same rule as FR-002.
- **FR-007**: The app MUST show a "No check-out" flag on a past day with a check-in and no check-out,
  and MUST let the Manager complete it with a time on that day and a reason. It MUST NOT send it without a
  reason; the server refuses a time not on that day, a future time and a locked month, and its reason is
  shown.

**Home and month**

- **FR-008**: The Home screen MUST show today's status (not checked in, checked in with the time and day
  type, checked out), whether today is a working day, weekly off, holiday or leave day, and today's
  planned activities, all as the server returns them; Check in and Check out are offered only when the
  server's response allows them.
- **FR-009**: A planned activity on Home MUST open its record where the app has a screen for that kind,
  and otherwise show its kind, place, date and status.
- **FR-010**: My Month MUST show, for each day of the chosen month, the status, times, purpose or linked
  activities, flags (such as No check-out, Late, Added later) and leave, and the month figures, using the
  server's values and the month component of spec 019. The app MUST NOT compute totals, lateness or day
  states.
- **FR-011**: My Month MUST show a locked month as locked and MUST NOT offer an action that would change
  it.

**Leave and forgotten check-in**

- **FR-012**: The app MUST let a Manager request leave with a leave type from the server's list, a first
  and a last date, an optional half day at the start or the end, and a reason; list their requests,
  newest first, with status, decision and reason; and cancel a Pending request. The app checks only what
  needs no rules (a missing type, date or reason; last date before first) and leaves every other rule to
  the server.
- **FR-013**: The app MUST let a Manager ask for a forgotten check-in on a past day with a time, a day
  type (and a purpose for Field) and a reason; list their requests with status and, for a rejection, the
  reason; and cancel a Pending request.

**Server authority and safety**

- **FR-014**: Menu entries, screens and every button MUST appear only because the server's access model
  and responses offer them; the app MUST NOT choose by role name. A Teacher, Admin or System account MUST
  NOT see these screens.
- **FR-015**: Every refusal MUST be shown as the server's reason in plain language, and everything the
  Manager entered MUST be kept so they can correct it and try again.
- **FR-016**: Every request the app makes MUST carry the device location or the reason it is unavailable,
  as in spec 018. Every change made through these screens is audited by the server as in spec 032; the app
  adds no audit store.
- **FR-017**: The app MUST NOT send a check-in, check-out, completion or request while the phone is
  offline; it MUST say there is no connection, keep the entry, and let the Manager retry. Queued or
  offline capture is not provided.
- **FR-018**: The app MUST read the location only when the Manager acts or a screen loads data, never in
  the background and never continuously.

### Key Entities *(include if feature involves data)*

- **Manager's day**: one record per Manager per date, held by spec 032: day type (Office or Field),
  purpose or linked activities, check-in and check-out with their times and locations, an optional
  photo, flags, and any leave covering the date. The app shows and sends to it; it keeps no copy.
- **Planned activity**: something the Manager is scheduled to do on a date, from the shared list of
  visits, college drives, training sessions and tasks, with a kind, a date, a place and a status (the server's record has no title). The
  server decides which appear for today.
- **Manager leave request**: a request for a range of dates with a type, optional half days and a
  reason, with a status (Pending, Approved, Rejected, Revoked, Cancelled) and the decider's reason.
- **Forgotten check-in request**: a request to add a check-in to a past day with a time, day type, and
  reason, with a status and the decider's reason.
- **Month figures**: the server's count of days worked, on leave, missed and flagged for the month and
  whether the month is locked.

## Role & Permission Impact *(mandatory — Constitution Principles II–IV)*

| Role     | Menu (section → item) | Default actions | Data scope |
| -------- | --------------------- | --------------- | ---------- |
| Admin    | None in the app: Admin is web-only, and an account whose only app-eligible roles are Admin or System cannot sign in to the app (spec 018) | None in the app | None |
| Director | None for these screens; the Director's decisions on Manager leave and forgotten check-ins stay on the web (OPERATIONS → Manager Leave, spec 032) | None in the app | None |
| Manager  | MY ATTENDANCE → Check-in, My Month, My Leave | View, Create (check-in, check-out, completion, leave and forgotten check-in requests), Delete (cancel own Pending request) | Own |
| Teacher  | None | None | None |
| System   | None (System sees no business data; System-only accounts cannot sign in to the app) | None | None |

**New permission keys**: none. This feature uses the permissions spec 032 added:
`MY_MANAGER_ATTENDANCE` (View, Create, Delete). The server-provided navigation already offers the
matching menu items. Only Admin, Director and System may edit the role→permission matrix (Constitution
Principle II); this feature does not change that. A user who holds another app role as well is shown only
what that role's navigation offers.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A Manager can check in as Office from opening the app in under 30 seconds, and as Field
  with a planned activity selected in under 60 seconds, on a normal mobile connection.
- **SC-002**: Every check-in and check-out made through the app carries a location; none is recorded
  without one. A refused attempt (no location) is explained in a single message that says how to fix it.
- **SC-003**: For any Manager and month, the days, flags and figures shown in My Month match the web
  Manager Attendance screen for that Manager in 100% of days checked.
- **SC-004**: In acceptance testing, 9 of 10 Managers complete a check-in, a check-out and a leave
  request on their first attempt without help.
- **SC-005**: A check-in, check-out, completion or request is never shown as done unless the server
  confirmed it: in tests with the connection cut or the server refusing, 0 of 20 attempts show a false
  success, and every entered value is kept.
- **SC-006**: Every refusal covered by spec 032 (second check-in, locked month, full-day leave, no
  activity or purpose, no joining date) shows the server's plain-language reason, never a code or a
  generic error.
- **SC-007**: A Teacher, Director, Admin or System account sees none of these screens, and removing a
  permission removes the matching button on the next load in 100% of checked cases.
- **SC-008**: A day left without a check-out can be completed from the app in under 45 seconds, and the
  flag clears for the Manager, the Director and payroll.

## Assumptions

- The server rules and APIs of spec 032 are unchanged; this spec adds screens to the app shell of
  spec 018 and reuses its sign-in, session, menu, location and error handling.
- Location is **required** for check-in and check-out (decision D15 of spec 032). This is a deliberate
  exception to the rule in specs 018 and 019 that location is audit-only and never required: those
  specs describe location attached to a request, while 032 refuses the check-in itself without one. For
  every other action the app keeps the best-effort rule of spec 018.
- The check-in day type offered is Office or Field. The input mentioned Leave as a third choice, but
  spec 032 (FR-006) creates a Leave day only from approved leave, so the app offers leave through My
  Leave and shows leave days on Home and My Month.
- The open questions of the input's clarify list are settled (2026-10-17). Confirmed in `/speckit-clarify`: a photo may
  be taken or chosen from the gallery, with only the details spec 032 already keeps; the app takes a fresh
  location reading at the tap, sends the accuracy the phone reports and applies no accuracy threshold of its
  own; a Manager cannot change a day's type or links after checking in (a correction is an Admin or Director
  action on the web); Home shows today's plan only. Settled by the input's own scope: offline check-in is
  refused with a message (no queue, a later spec) and there is no reminder before the missed check-in
  cut-off (it needs push notifications, out of scope).
- Forgotten check-in requests (story 6) are included because they are part of the Manager's own API in
  spec 032 and the same screens serve them. They are the lowest priority and can be cut without
  affecting the other stories.
- Month navigation reaches the current month and the earlier months the server returns data for.
  Newer data is never invented.
- Out of scope, tracked for later specs: the Admin and Director grid, corrections and month lock (web,
  spec 032); geofencing or distance checks; offline or queued check-in; background location tracking;
  push notifications and reminders; visit logging and tasks (spec 034); expenses (spec 036); iOS.
- A Manager's planned activities come from the shared list of drives (016), marketing visits (023) and
  tasks (025) as the server returns it today; if some of those modules are not live in an environment,
  their activities are simply absent, and a Field day is then checked in with a purpose where the
  server accepts one.
