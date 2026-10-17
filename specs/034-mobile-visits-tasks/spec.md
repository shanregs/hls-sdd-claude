# Feature Specification: Mobile Visits and Tasks

**Feature Branch**: `034-mobile-visits-tasks`

**Created**: 2026-10-17

**Status**: Draft

**Input**: User description: "034-mobile-visits-tasks: Visit and task screens for the Manager in the HLS Android app, built on the app shell, sign-in and server-driven menus of spec 018, the School visit rules and APIs of spec 023 and the task rules and APIs of spec 025, which stay unchanged. Full input: docs/spec-inputs/034-mobile-visits-tasks.md (My visits: planned visits and meetings today, upcoming and past with School or prospect, purpose and status, filtered by status; Log a visit: complete it with a mandatory outcome and any follow-up, with photo attachments through the shared file store; Plan a visit: plan a new visit to a School or prospect, reschedule or cancel one with a reason as 023 allows; Follow-ups appear as tasks (025 contract C2); My tasks: today, upcoming and overdue, acknowledge, mark in progress, complete with evidence (note and photos), an overdue task asks for a reason as 025 requires, escalation and verification stay server-side; Task and visit detail with history, who assigned it, due date, linked record, evidence; Home counts for today's visits and overdue tasks; menu entries and allowed actions come only from the server access model; refusals in plain language with entered data kept; every call carries the device location or its reason). Out of scope: creating tasks for other users and verifying completed tasks (web), pipeline stage management and Final Stage review (web), proposals and MoU recording (web), offline capture, video attachments, push notifications, iOS."

## Clarifications

### Session 2026-10-18

- Q: Should a Director see the two screens and read every Manager's tasks and visits on the phone, or are they for Managers only? → A: The Director sees both screens as the server's menu offers them, with the same lists across all Managers. Tasks are read-only because the server's action flags are all false for a task that is not the Director's own, and visits follow what the server permits. The app does not hide the screens by role, and the server's menu is not changed.
- Q: When a Manager completes a visit or a task, is a fresh location reading taken or required? → A: No. Only the ordinary best-effort location every request already carries (spec 018) is sent; completing a visit or a task is never blocked by location, and neither spec 023 nor 025 asks for a position.
- Q: Besides photos, may a Manager attach documents to a visit or task, and are photos required? → A: Photos only (taken in the app or chosen from the gallery); documents are added on the web. A photo is never required: a visit or a task is completed with no photo at all, and the app never asks for one.
- Q: Is adding a task for oneself in this spec? → A: Yes, as the last and lowest-priority story (User Story 7): a Manager can add a task for themselves, and only for themselves; it can be dropped without affecting any other story.
- Q: How far does the visit list reach? → A: One month at a time, opening on the current month, with month arrows and a month chooser that reach the previous, current and next year. Within the month shown, today's visits come first, then later ones, then earlier ones. Home's count of today's visits is separate and does not depend on the month shown.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - A Manager Sees Their Visits (Priority: P1) 🎯 MVP

A Manager opens MARKETING → Marketing Calendar in the app and sees their planned visits, calls and meetings with Schools
and prospects: what is planned for today first, then what is coming, then what is past. Each shows the School or prospect,
the date, the kind of activity, and its status as the server describes it (planned, completed, cancelled, or missed when the
date has passed and it was neither completed nor cancelled). The Manager can filter by status and open one to read its
details: who it is with, who from HLS attends, the notes, the outcome and follow-up date if completed, the reason if
cancelled, the history of date changes and any attached photos.

**Why this priority**: knowing today's visits is the field Manager's first question of the day, and every other visit action
starts from a visit in this list.

**Independent Test**: sign in as a Manager with one visit today, one next week and one last week that was never completed,
and confirm the list groups and labels them as the server does (the old one as missed) and the detail shows the server's
fields.

**Acceptance Scenarios**:

1. **Given** a Manager with visits on several dates, **When** they open the Marketing Calendar, **Then** the current month is
   shown, with today's visits first, then later visits in date order, then earlier visits newest first, each with the target's
   name, the date, the kind of activity and the status.
2. **Given** a planned visit whose date has passed, **When** the list loads, **Then** it is shown as Missed, because the
   server says so; the app does not work out lateness itself.
3. **Given** the status filter, **When** the Manager chooses Planned, Completed, Cancelled or Missed, **Then** only those
   visits are shown; "All" shows every visit; the filter is kept while the Manager moves between the list and a detail.
4. **Given** a visit, **When** the Manager opens it, **Then** the detail shows the School or prospect, the date, the kind, the
   HLS people attending, the notes, and for a completed visit the outcome and the follow-up date, for a cancelled one the
   reason, any earlier dates it was moved from, and the attached photos with their names and sizes.
5. **Given** the Manager moves to another month with the month arrows or the month chooser (reaching the previous, current and
   next year), **When** it loads, **Then** that month's visits are shown the same way; a month with no visits says so and
   explains how to plan one.
6. **Given** the list cannot be loaded, **When** the Manager opens it, **Then** the app shows the server's reason or "no
   connection" with Try again, and no stale list is shown as current.
7. **Given** a visit of a prospect outside the Manager's Zones, **When** it would be opened, **Then** the app shows the
   server's "not found" and nothing about it.

---

### User Story 2 - A Manager Logs a Visit (Priority: P1)

After a visit the Manager completes it: they write the outcome (who was met, what was discussed and the next action, which the
server requires), may add notes and a follow-up date, and may attach photos taken at the visit. Sending it marks the visit
Completed. A follow-up date creates a follow-up task for the Manager, which they then see in My Tasks.

**Why this priority**: recording what happened is the point of a field visit, and the outcome is mandatory; without it the
visit stays open and the pipeline learns nothing.

**Independent Test**: complete a planned visit with an outcome, a follow-up date and a photo, and confirm the visit shows
Completed with its outcome and photo, and a follow-up task appears in My Tasks.

**Acceptance Scenarios**:

1. **Given** a planned visit, **When** the Manager opens Complete visit, **Then** they can write the outcome, notes, choose
   an optional follow-up date, and add photos.
2. **Given** an empty outcome, **When** the Manager tries to send, **Then** the app does not send it and says an outcome is
   needed: who was met, what was discussed and the next action; a whitespace-only outcome counts as empty.
3. **Given** an outcome and optional fields, **When** the Manager sends, **Then** the server completes the visit, the app shows
   it as Completed with the outcome as saved, and the photos chosen are uploaded after the visit is completed.
4. **Given** a follow-up date, **When** the visit is completed, **Then** the server creates a follow-up task for the Manager and
   the app's My Tasks shows it; the app does not create it itself.
5. **Given** a follow-up date before the visit's date, a visit that was changed meanwhile, or a visit that is no longer
   planned, **When** the Manager sends, **Then** the server's reason is shown in plain language, nothing changes, and the
   entries are kept.
6. **Given** a photo the server refuses (type, size) or one that fails to upload, **When** the visit has been completed,
   **Then** the visit stays Completed, the failure is explained, and the photo can be added again from the visit.
7. **Given** the connection is lost while sending, **When** the Manager taps Send, **Then** the app says nothing was sent,
   keeps every entry, and shows the visit as Completed only after the server confirms it.

---

### User Story 3 - A Manager Works Through Their Tasks (Priority: P1)

The Manager opens OPERATIONS → My Tasks and sees what is due today, what is coming, what is overdue, what has been completed
and is waiting for verification, and what is done. They acknowledge a new task, mark it in progress, and complete it with a
note and photos as evidence. If a task is overdue, they give a reason (and may set a follow-up date) before completing it. The
server decides escalation, notifications and verification; the app shows the results.

**Why this priority**: tasks are how the Director assigns work to a field Manager, and the Manager needs to act on them where
the work happens.

**Independent Test**: with a new task, a task in progress and an overdue task, acknowledge the first, start it, complete the
second with a note and a photo, and give a reason on the overdue one; confirm each task's status and the counts change as the
server says.

**Acceptance Scenarios**:

1. **Given** the Manager's tasks, **When** they open My Tasks, **Then** they can switch between Today, Upcoming, Overdue,
   Awaiting verification, Done and All, each showing the task's title, type, due date, status and, for an overdue one, how
   many days overdue and whether it has been escalated, as the server returns them.
2. **Given** a new task, **When** the Manager acknowledges it, **Then** the server records it and the task shows as
   Acknowledged.
3. **Given** an acknowledged task, **When** the Manager marks it in progress, **Then** it shows as In progress.
4. **Given** a task in progress or acknowledged, **When** the Manager completes it with a note of at least 10 characters,
   **Then** the server completes it and the task shows as Completed and awaiting verification; a note shorter than 10
   characters is not sent and the app says how many more are needed.
5. **Given** a task that is overdue, **When** the Manager tries to complete it before giving a reason, **Then** the app asks for
   the reason first (at least 5 characters) with an optional follow-up date, sends it, and only then offers completion.
6. **Given** a task that is open or Completed, **When** the Manager adds evidence, **Then** up to 5 photos can be attached in
   total, taken in the app or chosen from the gallery; the number attached out of 5 is shown, and adding a sixth is not
   offered.
7. **Given** any task action, **When** the server refuses it (the task changed, it is not the Manager's, a rule), **Then** the
   server's reason is shown, nothing changes, and the entries are kept.
8. **Given** the connection is lost during any action, **When** the Manager taps the button, **Then** the app says nothing was
   sent, keeps the entries, and shows nothing as done until the server confirms it.

---

### User Story 4 - A Manager Plans, Moves and Cancels a Visit (Priority: P2)

The Manager plans a new visit: they choose a School or a prospect from the list the server gives them, the kind of activity
(visit, call, proposal meeting or follow-up), the date, which HLS colleagues attend, and notes. They can move a planned visit
to another date (the earlier date stays in its history) or cancel it with a reason.

**Why this priority**: planning is what puts visits on the calendar that story 1 shows, and rescheduling is a daily event in
the field; it is less urgent than seeing and logging visits.

**Independent Test**: plan a visit for a prospect next Tuesday, move it to Wednesday, and cancel another with a reason, and
confirm the list and the visit history show each change.

**Acceptance Scenarios**:

1. **Given** the Plan a visit screen, **When** the Manager chooses a target from the Schools and prospects the server lists for
   them, a kind, a date and optionally colleagues and notes, and sends, **Then** the server plans it and it appears in the
   list.
2. **Given** no target or no date, **When** the Manager tries to send, **Then** the app does not send and says which is
   missing; every other rule (a date in the past, a target outside the Manager's Zones) is the server's.
3. **Given** a planned visit, **When** the Manager moves it to a new date, **Then** the visit shows the new date and its history
   lists the old one; moving it to the date it is already on is refused with the server's reason.
4. **Given** a planned visit, **When** the Manager cancels it with a reason, **Then** it shows as Cancelled with the reason; a
   cancel without a reason is not sent.
5. **Given** a visit that is completed or cancelled, **When** the Manager opens it, **Then** Reschedule, Cancel and Complete
   are not offered.
6. **Given** the server refuses a plan, move or cancel, **When** the Manager sends it, **Then** the reason is shown in plain
   language and the entries are kept.

---

### User Story 5 - Home Shows Today's Visits and Overdue Tasks (Priority: P2)

The Manager's Home shows how many visits are planned for today and how many tasks are overdue (with how many are due today),
each opening the matching list. The "coming soon" cards for these areas are replaced.

**Why this priority**: it turns the app's front page into a daily briefing, and it is cheap once the lists exist.

**Independent Test**: with two visits today and three overdue tasks, confirm Home shows "2 visits today" and "3 overdue tasks"
and each opens the right list.

**Acceptance Scenarios**:

1. **Given** a Manager whose menu offers the Marketing Calendar and My Tasks, **When** they open Home, **Then** they see the
   number of visits planned for today and the numbers of tasks overdue and due today, from the server.
2. **Given** a count, **When** the Manager taps it, **Then** the matching list opens already filtered (today's visits, or
   overdue tasks).
3. **Given** a count cannot be loaded, **When** Home opens, **Then** that card says it could not be loaded and the others are
   unaffected.
4. **Given** a user whose menu offers neither screen, **When** they open Home, **Then** these cards do not appear.

---

### User Story 6 - Menus, Permissions and Safety Come From the Server (Priority: P2)

The Manager sees these screens only because the server's access model offers them, and every button appears only when the server
says the Manager may use it (the permissions of the menu item and, for a task, the actions the server returns with it).
Admin and System accounts get none of these screens. Whatever the server refuses is shown in plain language with what the
Manager typed kept, and nothing is ever shown as done before the server confirms it.

**Why this priority**: the app adds no business rules (spec 018); this keeps the phone and the web agreeing even when an Admin
changes permissions or a task changes on the web.

**Independent Test**: sign in as a Manager, a Teacher and a Director and confirm who sees what; take a permission away on the web
and confirm the matching button disappears after the next load; change a task on the web while it is open on the phone and
confirm the phone's action is refused with the server's reason.

**Acceptance Scenarios**:

1. **Given** a Manager signed in, **When** the menu loads, **Then** it shows My Tasks and the Marketing Calendar exactly as the
   server's navigation model returns them.
2. **Given** a Teacher, **When** they use the app, **Then** these entries do not appear.
3. **Given** a task, **When** the app shows its buttons, **Then** each action (acknowledge, start, complete, give a reason, add
   evidence) is shown exactly when the server's flags for that task say it is allowed, never from the task's status or dates.
4. **Given** the server removes Create or Edit from the Manager, **When** they next open a screen, **Then** the matching
   buttons are gone, and any attempt is refused by the server with its reason.
5. **Given** a task changed on the web while open on the phone, **When** the Manager acts on the stale view, **Then** the
   server's reason is shown, the task is reloaded, and the Manager's entries are kept.

---

### User Story 7 - A Manager Adds a Task for Themselves (Priority: P3)

The Manager can add a task for themselves with a type, a title, a description, a due date and optionally a place, when the
server allows it. It then appears in their list like any other task.

**Why this priority**: the server lets a Manager assign a task to themselves, and the field is where a reminder is thought of;
it is the least needed of the stories and can be cut without affecting the others.

**Independent Test**: add a task for tomorrow and confirm it appears under Upcoming with the status the server gives it.

**Acceptance Scenarios**:

1. **Given** the Add a task screen, **When** the Manager enters a type, a title and a due date and sends, **Then** the server
   creates the task assigned to the Manager and it appears in their list.
2. **Given** a missing title or due date, **When** the Manager tries to send, **Then** the app does not send and says which is
   missing; a due date in the past and every other rule are the server's.
3. **Given** the menu does not grant Create, **When** the Manager opens My Tasks, **Then** Add a task is not offered.

---

### Edge Cases

- A visit and a task of the "School visit" type can both exist for the same trip: they are different records (a visit is a
  marketing activity of spec 023, a task is of spec 025) and the app shows each in its own list without merging them.
- A visit's follow-up date creates a task only on the server; if the task does not appear immediately, My Tasks shows it after the
  next load, and the app never creates it.
- A photo larger than the server's limit, or not an image type it accepts: refused with the server's reason after the visit or task
  action itself has succeeded; the action is not undone.
- Two photos chosen at once for a task that has room for only one more: the first is sent and the second is refused with the
  server's reason; the count shown is the server's.
- A task that is escalated: shown as escalated; the Manager cannot clear it; only the server's flags decide the buttons.
- A visit on a date with no time of day: the app shows dates only; there is no time to show.
- The Manager opens a visit or task another user removed or reassigned: the server answers "not found" and the app says so and
  returns to the list.
- A very long outcome, note or reason: the field shows how many characters are left against the server's limit and does not
  accept more.
- The app is in the background for a long time: lists are reloaded when the Manager returns to the screen, never kept as
  current.

## Requirements *(mandatory)*

### Functional Requirements

**Visits (spec 023)**

- **FR-001**: The app MUST list the Manager's visits, calls and meetings (those the server returns for the Manager) one month at
  a time, opening on the current month, with month arrows and a month chooser that reach the previous, current and next year;
  within the month shown, today's visits come first, then later ones, then earlier ones, each showing the School or prospect,
  the date, the kind of activity and the status as the server gives it, including Missed. The app MUST NOT work out lateness or
  status itself.
- **FR-002**: The app MUST let the Manager filter the list by status (All, Planned, Completed, Cancelled, Missed) and keep the
  choice while moving between a list and a detail.
- **FR-003**: The app MUST show a visit's details as the server holds them: target, date, kind, attending colleagues, notes,
  outcome and follow-up date when completed, the reason when cancelled, earlier dates, and attachments with name and size.
- **FR-004**: The app MUST let the Manager complete a planned visit with an outcome (required, at most 1000 characters), notes
  (at most 1000 characters) and an optional follow-up date, and MUST NOT send it with an empty or whitespace-only outcome.
- **FR-005**: The app MAY let the Manager add photos to a visit (taken in the app or chosen from the gallery; photos only, no
  documents), uploaded after the visit action it belongs to has succeeded; a photo is optional and the app MUST NOT require one
  to complete a visit, and MUST NOT undo a completed visit when a photo fails.
- **FR-006**: The app MUST let the Manager plan a visit by choosing a School or prospect from the lists the server provides, a
  kind (visit, call, proposal meeting, follow-up), a date, optional attending colleagues and notes; it MUST send nothing without
  a target and a date.
- **FR-007**: The app MUST let the Manager move a planned visit to another date and cancel it with a reason (at most 300
  characters), and MUST offer none of Complete, Reschedule or Cancel on a visit that is not planned.

**Tasks (spec 025)**

- **FR-008**: The app MUST list the Manager's tasks in the views the server offers (Today, Upcoming, Overdue, Awaiting
  verification, Done, All) with title, type, due date, status, and, from the server, overdue days and the escalated mark.
- **FR-009**: The app MUST let the Manager acknowledge a task, mark it in progress, give an overdue reason (at least 5
  characters, with an optional follow-up date), and complete it with a note (at least 10 characters), each only when the
  server's flags for that task allow it.
- **FR-010**: The app MUST let the Manager add evidence to a task, as photos taken in the app or chosen from the gallery (no
  documents), up to 5 in total, showing how many are attached; the count and the limit come from the server's task. A photo is
  optional: the app MUST NOT require one to complete a task.
- **FR-011**: The app MUST show a task's details as the server holds them: who assigned it, due date, linked record and place,
  description, history of changes, the overdue reason, the completion note, verification, and the evidence, and MUST NOT offer
  verification, send-back, cancel or editing, which stay on the web.
- **FR-012**: The app MUST let the Manager add a task for themselves (type, title, description, due date, optional place) when
  the server grants Create, and MUST NOT offer assigning a task to anyone else.

**Home and shared behaviour**

- **FR-013**: The Home screen MUST show, from the server, the number of visits planned for today and the numbers of tasks overdue
  and due today, each opening the matching list, and MUST show these cards only when the menu offers the matching screen.
- **FR-014**: Menu entries, screens and every button MUST appear only because the server's access model and responses offer
  them; the app MUST NOT choose by role name. A Teacher, Admin or System account MUST NOT see these screens.
- **FR-015**: Every refusal MUST be shown as the server's reason in plain language, and everything the Manager entered MUST be
  kept so they can correct it and try again.
- **FR-016**: Nothing MUST be shown as done (a completed visit, a task step, a plan, a move, a cancel, a photo) before the server
  has confirmed it; when the phone is offline the app MUST say nothing was sent and keep the entries. Queued or offline capture
  is not provided.
- **FR-017**: Every request MUST carry the device location or the reason it is unavailable, as in spec 018. Location is
  best-effort for these actions and never required: the app MUST NOT take a separate reading at the tap, and MUST NOT refuse
  to complete a visit or a task because location is unavailable.
- **FR-018**: Lists and details MUST be loaded from the server each time a screen opens or is refreshed; a failed load MUST drop
  the data and show an error with a way to try again, never stale data as current.

### Key Entities *(include if feature involves data)*

- **Visit (marketing activity)**: a planned visit, call, proposal meeting or follow-up with a School or prospect on a date, with
  attending colleagues, notes, a status (planned, completed, cancelled, or missed when planned and past), an outcome and
  follow-up date when completed, a reason when cancelled, earlier dates when moved, and photo attachments.
- **Task**: a unit of work assigned to a Manager with a type, title, description, due date, status (assigned, acknowledged, in
  progress, completed, verified, cancelled), the person who assigned it, a linked record and place, an overdue reason, a
  completion note, history, evidence photos, and the actions the server currently allows.
- **Follow-up task**: a task the server creates when a visit is completed with a follow-up date; it links back to the visit.
- **Evidence and attachment**: a photo attached to a task or a visit, with a name, type and size.
- **Count summary**: the server's numbers of tasks by view (today, upcoming, overdue, awaiting, escalated, done) and the number of
  visits planned for today.

## Role & Permission Impact *(mandatory — Constitution Principles II–IV)*

| Role     | Menu (section → item) | Default actions | Data scope |
| -------- | --------------------- | --------------- | ---------- |
| Admin    | None in the app: Admin is web-only, and an account whose only app-eligible roles are Admin or System cannot sign in to the app (spec 018) | None in the app | None |
| Director | OPERATIONS → Manager Tasks and MARKETING → Marketing Calendar as the server's menu offers them; read-only on tasks because every action flag the server returns is false for a task that is not the Director's own (confirmed 2026-10-18) | As the server grants | Org-wide as the server returns |
| Manager  | OPERATIONS → My Tasks; MARKETING → Marketing Calendar | Tasks: View, Create (a task for self), Edit (acknowledge, start, give a reason, complete, add evidence). Visits: View, Create (plan), Edit (complete, move, cancel) | Own tasks; visits in the Manager's own Zones |
| Teacher  | None | None | None |
| System   | None (System sees no business data; System-only accounts cannot sign in to the app) | None | None |

**New permission keys**: none. This feature uses the `TASKS` and `MARKETING` permissions of specs 025 and 023. Only Admin, Director
and System may edit the role→permission matrix (Constitution Principle II); this feature does not change that. A user who holds
another app role as well is shown only what that role's navigation offers.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A Manager can see today's visits and overdue tasks within 5 seconds of opening the app, from Home, on a normal mobile
  connection.
- **SC-002**: A Manager can complete a visit with an outcome and one photo in under 90 seconds, and complete a task with a note and
  one photo in under 60 seconds.
- **SC-003**: For any Manager, the visits, tasks, statuses, counts and overdue days shown in the app match the web screens for that
  Manager in 100% of cases checked, including a visit shown as Missed.
- **SC-004**: A completed visit or task step is never shown as done unless the server confirmed it: in tests with the connection cut
  or the server refusing, 0 of 20 attempts show a false success and every entered value is kept.
- **SC-005**: Every refusal covered by specs 023 and 025 (no outcome, a stale version, a note too short, a missing reason, a limit of
  5 evidence files, a visit no longer planned) shows the server's plain-language reason, never a code or a generic error.
- **SC-006**: A Teacher, Admin or System account sees none of these screens, and removing a permission removes the matching button
  on the next load in 100% of checked cases.
- **SC-007**: In acceptance testing, 9 of 10 Managers complete a visit with an outcome and acknowledge, start and complete a task on
  their first attempt without help.
- **SC-008**: A photo that fails to upload never loses the visit or task action it belongs to: in tests, 0 of 20 failed uploads undo
  a completed visit or task.

## Assumptions

- The server rules and APIs of specs 023 and 025 are unchanged; this spec adds screens to the app shell of spec 018 and reuses its
  sign-in, session, menu, location and error handling, and the photo picking and upload pieces added by spec 033.
- The input said "move a visit through the server's status flow", "people met and discussion as separate fields", "time" and "mark
  a visit missed with a reason". Spec 023's actual rules differ and are followed: a visit is planned, then completed or cancelled;
  "missed" is worked out by the server from a planned visit whose date has passed, and cannot be set; a visit has a date only, no
  time; the people met, what was discussed and the next action are written together in the one required outcome; the reason is
  asked on cancel.
- A "visit" in this spec is a marketing activity of spec 023 (visit, call, proposal meeting, follow-up); a task of the type "School
  visit" is a task of spec 025. They stay separate records and separate lists.
- The Marketing Calendar menu entry is the mobile entry for visits. The web's Prospects, Pipeline and Marketing Dashboard entries
  have no screen in the app, so the server's menu items for them are simply not shown (spec 018).
- Prospects are visible on the phone only as the target of a visit (name, and the contact person the server returns), never as
  prospect screens.
- The Director's menu offers the same two routes under the web's names; the screens show whatever the server returns and offer
  only the actions its flags allow, so a Director sees every Manager's tasks read-only and visits as the server permits
  (confirmed 2026-10-18).
- Defaults to confirm in `/speckit-clarify`: offline capture is not provided (consistent with spec 033); photos only, optional, taken or
  chosen from the gallery and compressed on the phone before upload, with the server's limits (10 files of 10 MB per visit, 5
  evidence files per task) enforced by the server (confirmed 2026-10-18); starting or completing a visit takes no special location reading beyond the best-effort
  location every call carries (confirmed 2026-10-18); the visit list opens on the current month and reaches the previous, current and next year (confirmed 2026-10-18); a Manager can add a task only for themselves (story 7, lowest priority; confirmed 2026-10-18).
- Out of scope, tracked for later specs: creating tasks for other users and verifying, sending back, cancelling and editing tasks
  (web); pipeline stage changes, Final Stage review, proposals, MoU recording and the marketing dashboard (web); offline or queued
  capture; video attachments; push notifications; iOS; expenses (spec 036).
