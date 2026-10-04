# Feature Specification: Mobile Attendance

**Feature Branch**: `019-mobile-attendance`

**Created**: 2026-10-04

**Status**: Draft

**Input**: User description: "019-mobile-attendance: Attendance screens for the HLS Android app, built on the app shell, sign-in and server-driven menus of spec 018 and the attendance rules and APIs of spec 008, which stay unchanged. Teacher: My Attendance (monthly calendar, current month by default, any month of the current year, mark Present/Absent or another allowed status with a whole or half day and an optional note on today or the previous 3 days, refusals in plain language) and Attendance History (read-only earlier months). Holiday Calendar for every role (read-only, current month by default, any month of the current year). Manager: Teacher Attendance (assigned Teachers for a chosen month with a rollup each, and a month view per Teacher in which the Manager can mark, correct or clear a day). Menu entries appear only because the server offers them; nothing is chosen by role name in the app. Every call carries the device location or its reason, every mark is audited by the server. Out of scope: offline capture, photo or geo-tag evidence, push notifications, leave, Admin/Director grids and month lock or reopen, CSV or PDF export, iOS. Depends on 008-attendance and 018-android-app-foundation."

## Clarifications

### Session 2026-10-04

- Q: Which attendance statuses can a Teacher choose when marking their own day on the phone? → A: Whatever the server allows for self-marking, shown with Present first and the rest in the server's order. The app hard-codes no status list, so a status added on the web appears without an app update.
- Q: Should the Manager's list of Teachers have a search box? → A: Yes, always shown above the list, so a Manager can find one Teacher by name among many.
- Q: Besides the month view, should the Holiday Calendar offer an "all holidays this year" list? → A: Yes. The monthly view stays the default, and a button on the same screen opens a read-only list of every holiday of the current year.
- Q: When a Teacher's mark is refused because a supervisor set the day or the month is locked, should the app offer a way to contact the Manager? → A: No. The app only explains in plain words (for example "Your Manager set this day. Ask them to correct it."). There is no contact shortcut or correction request in this release; messaging and notifications are separate specs.
- Q: Should Attendance History reach into the previous year? → A: Yes, on Attendance History only: it offers the current and the previous year. My Attendance and the Holiday Calendar offer the current year only.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - A Teacher Sees and Marks Their Own Month (Priority: P1) 🎯 MVP

A Teacher opens MY ATTENDANCE → My Attendance in the app and sees a calendar of the current month.
Each day shows what it is: marked (with the status and whether it is a whole or half day), weekly off,
holiday, not placed in a School, in the future, or still unmarked. The Teacher can open another month
of the current year with a month picker. On today or one of the previous 3 days they can choose a
status such as Present or Absent, a whole or half day, add a note, and save. The calendar updates at
once and shows who set the day and when.

**Why this priority**: it is the daily, highest-frequency action, and it is the reason Teachers need
the app. Everything else on the phone builds on seeing one month of attendance correctly.

**Independent Test**: sign in as a placed Teacher, mark today as Present and yesterday as a half day,
switch to last month and back, and confirm both marks and the month totals are shown correctly.

**Acceptance Scenarios**:

1. **Given** a placed Teacher, **When** they open My Attendance, **Then** the current month is shown with
   one cell per day, each showing its state, and the month's totals (working days, days worked, leave,
   unmarked) from the server.
2. **Given** the Teacher opened My Attendance, **When** they pick another month of the current year,
   **Then** that month is shown with the same layout, and picking the current month again returns to it.
3. **Given** today is unmarked, **When** the Teacher chooses Present for a whole day and saves, **Then**
   the day shows Present, attributed to them with the time, and the month totals update.
4. **Given** a marked day, **When** the Teacher chooses a half day or another status and saves, **Then**
   the day shows the new value, and the earlier value is kept in the day's history by the server.
5. **Given** the Teacher taps a day they may not change (a future day, a day older than 3 days, a day in
   a locked month, a day not inside a placement, a day a supervisor already set, or a day after they
   exited), **When** they try to save, **Then** the app tells them in plain language why, and the day is
   unchanged. Days they cannot change do not offer a save action at all.
6. **Given** a day set by a supervisor, **When** the Teacher opens it, **Then** it shows who set it and
   explains that their Manager must correct it.
7. **Given** a Teacher whose profile is not yet linked to a Teacher record, **When** they open My
   Attendance, **Then** the app shows the server's message that their profile has not been set up yet.
8. **Given** the server cannot be reached while saving, **When** the Teacher taps Save, **Then** the app
   says there is no connection, keeps what they entered, and lets them try again; nothing is saved
   until the server confirms it.

---

### User Story 2 - A Teacher Reviews Earlier Months (Priority: P1)

A Teacher opens MY ATTENDANCE → Attendance History and picks any month of the current or the previous year to see it
read-only: each day's status, who set it, and the month's totals. Nothing on this screen can be changed.

**Why this priority**: Teachers check past months when pay or leave questions come up. It reuses the
month view of the first story, so it is cheap and makes the first story complete.

**Independent Test**: open History, pick two earlier months, and confirm each shows the right marks and
totals and offers no way to edit.

**Acceptance Scenarios**:

1. **Given** earlier months with marks, **When** the Teacher opens Attendance History and picks one,
   **Then** the month is shown read-only with the same cell states and totals.
2. **Given** a month that has been locked, **When** it is shown, **Then** it says the month is locked.
3. **Given** a month with no placement, **When** it is shown, **Then** days show as not placed and the
   totals are zero, with no error.

---

### User Story 3 - Everyone Can See the Holiday Calendar (Priority: P2)

Any signed-in user can open the Holiday Calendar and see the current month with its holidays
(non-working dates, with their description) and weekly offs marked. A month picker shows any month of
the current year. A button on the same screen opens a list of every holiday of the current year. It is
read-only for everyone in the app.

**Why this priority**: it explains why a day is not a working day and is needed by every role, but it
is reference information and does not block marking attendance.

**Independent Test**: sign in as a Teacher and as a Manager, open the Holiday Calendar, confirm the
current month's holidays and weekly offs match the web calendar, and switch to another month.

**Acceptance Scenarios**:

1. **Given** a signed-in user of any role, **When** they open the Holiday Calendar, **Then** the current
   month is shown with each holiday marked and its description available, and weekly off days marked.
2. **Given** the user picks another month of the current year, **When** it loads, **Then** that month's
   holidays and weekly offs are shown.
3. **Given** the calendar is shown, **When** the user opens "All holidays this year", **Then** every
   holiday of the current year is listed in date order with its description, and going back returns to
   the month they were on.
4. **Given** the calendar is shown, **When** the user looks for edit actions, **Then** there are none:
   changing the calendar remains a web action for Admin and Director.

---

### User Story 4 - A Manager Reviews and Corrects Their Teachers' Attendance (Priority: P2)

A Manager opens OPERATIONS → Teacher Attendance and chooses a month. They see the Teachers in their
assigned Schools, each with their School and month totals, and always has a search box to find a Teacher by name. Tapping a Teacher
opens that Teacher's month view, in which the Manager can mark, correct or clear any unlocked day
inside the Teacher's placement, with status, whole or half day and a note.

**Why this priority**: it covers Teachers who cannot mark for themselves, and it is the Manager's main
phone task. It comes after the Teacher stories because it reuses their month view.

**Independent Test**: sign in as a Manager with two assigned Teachers and another Manager's Teacher,
confirm only the two appear, mark a day for one, correct it, and clear it.

**Acceptance Scenarios**:

1. **Given** a Manager with assigned Teachers, **When** they open Teacher Attendance for a month,
   **Then** only Teachers placed in their assigned Schools during that month are listed, with School and
   month totals, and a Teacher of another Manager never appears.
2. **Given** the list, **When** the Manager searches by name, **Then** the list narrows to matching
   Teachers.
3. **Given** a Teacher, **When** the Manager opens them, **Then** the Teacher's month view is shown as in
   the first story, with every unlocked day inside a placement open for marking.
4. **Given** an unlocked day, **When** the Manager marks or corrects it and saves, **Then** the day shows
   the new value attributed to the Manager, and the Teacher can no longer change that day.
5. **Given** a marked unlocked day, **When** the Manager clears it, **Then** it becomes unmarked and the
   clearing is kept in the day's history by the server.
6. **Given** a locked month, **When** the Manager tries to change a day, **Then** the app explains the
   month is locked and nothing changes.
7. **Given** a Teacher outside the Manager's scope, **When** the Manager's app is asked to open them
   (for example from an old screen), **Then** the app shows "not found" and no data about them.
8. **Given** a day's history, **When** the Manager opens it, **Then** every earlier value of that day is
   listed, newest first, with who changed it and when.

---

### User Story 5 - Menus and Safety Come From the Server (Priority: P2)

The new screens appear in the menu only for users whose server-provided navigation includes them, and
no screen is chosen by role name. Every request still carries the device location or its reason, as in
spec 018, and every mark is audited by the server exactly as on the web.

**Why this priority**: it keeps the app consistent with spec 018's rules and with the server's
permissions, but it needs no new screens of its own, so it is checked alongside the others.

**Independent Test**: sign in as a Teacher, a Manager and a Director and confirm each sees only the
attendance entries the server offered; remove a permission on the web and confirm the entry
disappears after the next menu refresh.

**Acceptance Scenarios**:

1. **Given** a Teacher, **When** the menu loads, **Then** it shows My Attendance, Attendance History and
   Holiday Calendar, and no Manager or Admin entries.
2. **Given** a Manager, **When** the menu loads, **Then** it shows Teacher Attendance and Holiday
   Calendar and no Teacher self-service entries.
3. **Given** a user with several roles, **When** the menu loads, **Then** it is the union of what the
   server offered, with no duplicates.
4. **Given** a Director or Admin, **When** the menu loads, **Then** Holiday Calendar is shown and the
   grids and month lock stay web-only, with no entry for them in the app.
5. **Given** an attendance change, **When** it is saved from the app, **Then** the server's audit
   history shows it as from the Android app with the device location or the reason it is missing.

---

### Edge Cases

- A month in which the Teacher moved Schools: each day shows the School they were placed in that day.
- A half-day mark: it shows as a half day on the calendar and counts as half in the totals.
- A mixed day (part school, part leave or training): one mark per day, as in spec 008; the note carries
  the rest.
- The user changes the month while a save is in progress: the save finishes first, and the calendar then
  shows the month they chose.
- The same day is changed by a supervisor while the Teacher has it open: the Teacher's save is refused
  as out of date and the app shows the new value.
- The device clock or time zone is wrong: "today" and the 3-day window come from the server's business
  date, not the phone's, so a wrong clock cannot open dates the server would refuse.
- Very small screens and large text: the calendar stays usable, with a list fallback if the grid does not
  fit.
- The month picker on My Attendance and the Holiday Calendar never offers months outside the current
  year. Attendance History also offers the previous year (clarified 2026-10-04), so a check in January
  can still reach December.
- Leave days marked by a supervisor appear coloured and labelled as leave; holiday days are labelled as
  holidays and cannot be marked.

## Requirements *(mandatory)*

### Functional Requirements

**Teacher: My Attendance and Attendance History**

- **FR-001**: The app MUST show the signed-in Teacher's own attendance for one month as a calendar with
  one cell per day, each showing its state (marked with its status and day value, weekly off, holiday,
  not placed, future, unmarked), the School for placed days, and the month's totals as returned by the
  server.
- **FR-002**: The default month MUST be the current month. A month picker MUST let the user choose any
  month of the current year, and MUST NOT offer months of other years, except that Attendance History
  also offers the previous year (FR-006).
- **FR-003**: A Teacher MUST be able to mark a day with a status from the list the server allows for
  self-marking (Present first, the rest in the server's order, none hard-coded in the app), a whole or half day, and an
  optional note, on today or any of the previous 3 days, and to correct an existing mark of their own on
  those days. The app MUST offer saving only on days the server says the Teacher may change.
- **FR-004**: When the server refuses a mark (future date, older than 3 days, locked month, not inside a
  placement, already set by a supervisor, Teacher exited, or the day changed meanwhile), the app MUST show
  the reason in plain language and MUST leave the day unchanged.
- **FR-005**: A mark MUST only be treated as saved once the server confirms it. There is no offline
  queue in this release; with no connection the app MUST keep the Teacher's entry on screen and let them
  retry.
- **FR-006**: Attendance History MUST show earlier months read-only, from the current year and the
  previous year, with the same
  cells and totals, and MUST offer no way to change anything. A locked month MUST say it is locked.
- **FR-007**: A day set by a supervisor MUST show who set it and when, and MUST explain that only a
  Manager (or Admin or Director) can correct it. When a mark is refused because a supervisor set the
  day or the month is locked, the app MUST only explain in plain words; it offers no contact shortcut
  or correction request in this release.
- **FR-008**: The days and windows that decide what may be changed MUST follow the server's business date
  and rules (spec 008), never the phone's clock.

**Holiday Calendar**

- **FR-009**: Every signed-in user MUST be able to open a read-only Holiday Calendar that shows, for the
  chosen month, each non-working date with its description and each weekly off day. The default month is
  the current month and the month picker follows FR-002. A button on the same screen MUST open a
  read-only list of every non-working date of the current year, in date order, with its description.
- **FR-010**: The app MUST NOT offer any way to add, change or delete holidays or weekly offs; those stay
  web actions for Admin and Director.

**Manager: Teacher Attendance**

- **FR-011**: A Manager MUST be able to list, for a chosen month, the Teachers placed in their assigned
  Schools, with each Teacher's School and month totals, and to search the list by name using a search box that is always shown. The server decides
  who is in scope; the app MUST NOT show any other Teacher.
- **FR-012**: A Manager MUST be able to open a Teacher's month view, and to mark, correct or clear any
  unlocked day inside that Teacher's placement, with status, whole or half day and a note. A Manager's
  mark or correction takes the day away from the Teacher until a supervisor changes it.
- **FR-013**: A Manager MUST be able to see the history of a day: every earlier value, newest first, with
  who changed it and when.
- **FR-014**: When a Manager's change is refused (locked month, outside placement, out of date, outside
  scope), the app MUST explain why in plain language and leave the day unchanged.

**Menus and shared rules (spec 018)**

- **FR-015**: The attendance screens MUST be reached only through menu entries that the server's access
  model offers; an entry the server does not offer MUST NOT be shown, and nothing MUST be decided by role
  name in the app.
- **FR-016**: The app MUST show an unavailable or "not authorized" state, never data, for a destination
  the user no longer has.
- **FR-017**: Every request made by these screens MUST carry the device location or the reason there is
  none, and MUST behave identically without it, exactly as in spec 018.
- **FR-018**: Every mark, correction and clearing MUST be recorded by the server in its audit history, as
  in spec 008. The app MUST NOT keep any authoritative copy of attendance.
- **FR-019**: The screens MUST work in light and dark themes, be usable with TalkBack and large text,
  and show dates as DD/MM/YYYY and months by name, following the product's conventions.
- **FR-020**: Loading, empty and error states MUST be shown on every screen; a failed load MUST offer a
  retry and MUST NOT show stale data as current.

### Key Entities *(include if feature involves data)*

- **Month View**: one Teacher's attendance for one month: totals, whether the month is locked, and one
  entry per day. Provided by the server; the app does not compute totals.
- **Day Entry**: a date with its state (marked, weekly off, holiday, not placed, future, unmarked), the
  status and day value, the School, who set it and when, a note, and who may change it (self,
  supervisor, nobody).
- **Status Code**: a named attendance status (for example Present, Absent, Leave, Training) the server
  allows for marking.
- **Holiday Calendar**: the organization's non-working dates with descriptions and its weekly off days.
- **Teacher Summary**: a Teacher in a Manager's list with School and month totals.

No new data is stored by this feature. It reads and writes the attendance records of spec 008 through
the server.

## Role & Permission Impact *(mandatory — Constitution Principles II–IV)*

| Role     | Menu (section → item) | Default actions | Data scope |
| -------- | --------------------- | --------------- | ---------- |
| Admin    | Holiday Calendar (view only) in the app. Grids, month lock and setup stay on the web | View Holiday Calendar | Org-wide (calendar) |
| Director | Holiday Calendar (view only) in the app. Grids, month lock and setup stay on the web | View Holiday Calendar | Org-wide (calendar) |
| Manager  | OPERATIONS → Teacher Attendance; Holiday Calendar | View, Create, Edit (mark, correct, clear) on assigned Teachers; View Holiday Calendar | Assigned |
| Teacher  | MY ATTENDANCE → My Attendance, Attendance History; Holiday Calendar | View own month; Create and Edit own marks within the window; View Holiday Calendar | Own |
| System   | None (System sees no business data; Admin-only and System-only accounts cannot sign in to the app, spec 018) | None | None |

Admin and Director only appear as app users when they also hold Teacher, Manager or Director; Admin is
refused on the app when it is their only app-eligible role (spec 018 FR-003).

**New permission keys**: none. This feature uses the existing attendance permissions from spec 008:
`MY_ATTENDANCE`, `TEACHER_ATTENDANCE` and `HOLIDAY_CALENDAR` (view). The server-provided navigation
already offers the matching menu items. Only Admin, Director and System may edit the role→permission
matrix (Constitution Principle II); this feature does not change that.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A Teacher can mark today's attendance in under 30 seconds from opening the app, on a
  typical mobile connection.
- **SC-002**: A Teacher or Manager can reach any month of the current year from the current month in
  two taps or fewer.
- **SC-003**: For test Teachers, 100% of the days, statuses and totals shown in the app equal what the
  web shows for the same Teacher and month.
- **SC-004**: For every refusal the server can give (6 reasons for a Teacher, 4 for a Manager), the app
  shows a plain-language message and leaves the day unchanged, in 100% of tests.
- **SC-005**: With two Managers' Teachers in the data, 100% of Teacher lists and month views opened by a
  Manager contain only that Manager's own Teachers; opening any other Teacher shows no data.
- **SC-006**: No attendance change is ever shown as saved before the server confirms it, in 100% of tests,
  including with no connection.
- **SC-007**: A change to a user's permissions on the web appears in their app menu on its next refresh,
  with no app update, in 100% of tests.
- **SC-008**: All attendance screens pass the accessibility checks in light and dark themes and are fully
  usable with TalkBack.
- **SC-009**: Every attendance change made from the app appears in the server's audit history, marked as
  from the Android app, in 100% of tests.

## Assumptions

- The server rules of spec 008 are unchanged: one mark per Teacher per date, a 3-day window for Teacher
  self-marking, month locks, supervisor marks taking precedence, status codes and weights, and audit.
- The app uses the server's own results (totals, day states, who may change a day) and computes none of
  them.
- Teachers choose from the statuses the server allows for self-marking (clarified 2026-10-04): the app
  shows that list, Present first and the rest in the server's order, and does not hard-code it.
- The Holiday Calendar in the app is monthly (with weekly offs and holidays) plus an "all holidays this
  year" list (clarified 2026-10-04), not the yearly grid overview or PDF of the web. School-specific weekly offs are shown for the School the user's data belongs to.
- My Attendance, the Teacher picker months and the Holiday Calendar reach the current year only, as
  requested. Attendance History also reaches the previous year (clarified 2026-10-04). Older years may be
  added later.
- Offline capture, push notifications, leave, photos or geo-tags, grids and month lock for Admin and
  Director, CSV or PDF export, and iOS are out of scope and tracked in later specs.
- The app and server keep the location rules of spec 018: location is audit-only, best-effort, and never
  required for any action.
- This spec adds screens to the app shell of spec 018 and reuses its sign-in, session, menu and error
  handling without change.
