---

description: "Task list for feature implementation"
---

# Tasks: Mobile Attendance

**Input**: Design documents from `/specs/019-mobile-attendance/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/mobile-attendance-screens.md,
quickstart.md, and **specs 008 and 018 implemented** (this feature adds screens to the app shell of 018
and calls the attendance endpoints of 008 unchanged; there is no backend work).

**Tests**: included as first-class tasks. Constitution Principle IX requires per-scope tests (two
Managers), menu-visibility tests per role, and tests that every refusal leaves the day unchanged and
that nothing is shown as saved before the server confirms it (SC-004 to SC-006).

**Organization**: grouped by user story in priority order (spec.md US1-US5). Shared components (month
view, day sheet, picker, API client) come first because three screens use them.

## Format: `[ID] [P?] [Story] Description`

> **Note**: T041 was added after `/speckit-analyze` and sits at the end of Phase 7; its ID is out of numeric order.

- **[P]**: can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: US1-US5; absent for Setup/Foundational/Polish

## Path Conventions

`MOB` = `mobile/src`, `MOBT` = `mobile/__tests__`. All work is under `mobile/`; `backend/` and `frontend/`
are not changed. Always run commands from the `mobile` folder with JDK 17 (see `docs/running-mobile.md`).

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: folders, date helpers and realistic test data.

- [X] T001 Create the folders `MOB/attendance/` and `MOBT/attendance/`, and confirm `npm test`, `npm run lint` and `npm run typecheck` pass from `mobile/` before any change
- [X] T002 [P] Extend `MOB/formats/dates.ts` with month names (January..December), short and long weekday names (Sunday-first), `formatMonth("2026-10")` returning "October 2026", and keep `formatDate` as DD/MM/YYYY; add `MOBT/formats/dates.test.ts`
- [X] T003 [P] Create `MOBT/support/attendanceFixtures.ts` with builders that copy the real response shapes in data-model.md: `monthView({days, locked, rollup})` for `TeacherMonthView` (days with `state`, `editableBy`, `mark` having `code`, `codeName`, `category`, `dayValue`, `schoolName`, `setByKind`, `setByName`, `setAt`, `note`, `version`), `statusCodes()` (P Present, A Absent, L Leave, T Training, S Substitution in sort order, plus H Holiday of category `NON_WORKING`), `calendar()` (default weekly off `["SUN"]`, a School override, Tamil Nadu 2026 non-working dates), `gridRow()` and `gridPage()` for `TeacherGridRow`, and `dayHistory()`
- [X] T004 [P] Create `MOBT/support/attendanceServer.ts` that registers attendance routes on the existing `FakeServer` (`GET /api/v1/attendance/me`, `PUT /api/v1/attendance/me/marks/{date}`, `GET /api/v1/attendance/status-codes`, `GET /api/v1/attendance/calendar`, `GET /api/v1/attendance/teacher-grid`, the `teachers/{id}` view, `PUT` and `DELETE` marks and `.../history`), matching `{date}` and `{id}` path segments, and recording calls; extend `MOBT/support/fakeServer.ts` only if path parameters need support

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: the API client, rules helpers and shared components every screen uses.

**⚠️ CRITICAL**: no user story work can begin until this phase is complete.

- [X] T005 Create `MOB/api/attendanceApi.ts` with the types of data-model.md (`StatusCode` with `category` one of `WORKED`, `LEAVE`, `TRAINING`, `NON_WORKING`; `DayState` one of `MARKED`, `UNMARKED`, `NOT_PLACED`, `WEEKLY_OFF`, `NON_WORKING`, `FUTURE`; `EditableBy` one of `SELF`, `SUPERVISOR`, `NONE`; `TeacherMonthView`, `DayView`, `MarkView`, `RollupView`, `AttendanceCalendar`, `TeacherGridRow`, `DayHistoryEntry`, `MarkRequest {statusCode, dayValue, note, version?}`) and typed calls: `getMyMonth(month)`, `saveMyMark(date, body)`, `listStatusCodes()`, `getCalendar()`, `getTeacherGrid({month, query, page, size})`, `getTeacherMonth(teacherId, month)`, `saveTeacherMark(teacherId, date, body)`, `clearTeacherMark(teacherId, date)`, `getDayHistory(teacherId, date)`; paths exactly as in contracts/mobile-attendance-screens.md; path segments URL-encoded
- [X] T006 [P] Record the server's time: in `MOB/api/httpClient.ts` store the `Date` header of every successful response in `MOB/attendance/serverClock.ts` (`recordServerDate(header)`, `serverNow()` returning the server time projected from the phone clock by the last offset, falling back to the phone clock when no header was seen); add `MOBT/attendance/serverClock.test.ts` (offset applied, missing header falls back, malformed header ignored)
- [X] T007 [P] Create `MOB/attendance/monthRange.ts`: the business time zone is UTC+05:30 with no daylight saving; `currentMonth(now)` returns "YYYY-MM" in that zone; `allowedMonths({kind: "current" | "history"}, now)` returns January..December of the current year, and for `history` also January..December of the previous year; `monthAt(year, month)`, `previousMonth`, `nextMonth` clamped to the allowed range; add `MOBT/attendance/monthRange.test.ts` (31 December 23:00 UTC is already January in India, previous year only for history, no other year ever offered, clamping at both ends)
- [X] T008 [P] Create `MOB/attendance/dayPresentation.ts`: for each `DayState` and mark `category` return a theme-aware colour (light and dark variants from `MOB/theme/tokens.ts`), a one-letter label (`H` for non-working, the weekday letter for weekly off, the status short code for a marked day) and an accessible label such as "Monday 6 October, Present, whole day, set by you"; half days say "half day"; add `MOBT/attendance/dayPresentation.test.ts` for every state, whole and half day, and both themes
- [X] T009 [P] Create `MOB/attendance/refusalMessages.ts`: `refusalText(reason)` maps the server's `reason` text to plain wording for the known refusals (future date, older than 3 days, locked month, no placement that date, already set by a supervisor → "Your Manager set this day. Ask them to correct it.", Teacher exited, changed meanwhile) and otherwise returns the server text as given; add `MOBT/attendance/refusalMessages.test.ts`; re-count the real refusal reasons in `backend/src/main/java/com/hls/attendance/internal/MarkService.java` and `specs/008-attendance/contracts/attendance-api.md` and write the exact count into the test name (spec SC-004 assumed 6 for a Teacher and 4 for a Manager)
- [X] T010 [P] Create `MOB/attendance/useMonthView.ts`: a hook `useMonthView(loader, month)` returning `{status: "loading"|"ready"|"error"|"noConnection"|"notFound", view, reload}`; it never keeps a failed load's old data as current (FR-020), maps a 404 to `notFound`, and `reload()` refetches; add `MOBT/attendance/useMonthView.test.tsx`
- [X] T011 [P] Create `MOB/attendance/MonthPicker.tsx`: previous and next arrows plus a tap-to-open list of the allowed months, a minimum 48 dp target, accessible labels ("Previous month", "Next month", "Choose month, October 2026"); props `{value, kind, onChange}`; add `MOBT/attendance/MonthPicker.test.tsx` (reaches January and December of the current year in two taps, history offers the previous year, no other year)
- [X] T012 [P] Create `MOB/attendance/MonthGrid.tsx`: a seven-column Sunday-first grid of cells from `days[]`, each cell a button using `dayPresentation`, with a list layout when the font scale is 1.3 or more so cells stay readable; props `{days, onSelect}`; add `MOBT/attendance/MonthGrid.test.tsx`
- [X] T013 [P] Create `MOB/attendance/DayLegend.tsx` (the states and colours, light and dark) and `MOB/attendance/RollupStrip.tsx` (working days, days worked, leave, unmarked, and "Locked" when `locked`; shows server figures only, no arithmetic); add `MOBT/attendance/RollupStrip.test.tsx`
- [X] T014 Create `MOB/attendance/DaySheet.tsx`: a bottom sheet with the date, School, state, status, day value, note, who set it and when; a supervisor mark shows "Set by <name>" and, on Teacher screens, "Ask your Manager to correct it"; an edit form (status chooser from the list given by props, whole or half day where the value is 1 or 0.5 and nothing else, an optional note, Save) shown only when props say the day is editable; optional Clear and History actions for Manager use; the draft (`statusCode`, `dayValue`, `note`, `baseVersion`) stays on screen after a failed save and is cleared only on success; props `{day, statuses, editable, onSave, onClear?, onHistory?}`; add `MOBT/attendance/DaySheet.test.tsx` (no Save when not editable with the reason shown, draft kept on failure, only 1 and 0.5 offered, accessible labels)
- [X] T015 Register the four screens: in `MOB/access/screenRegistry.ts` add keys `myAttendance`, `attendanceHistory`, `teacherAttendance`, `holidayCalendar` mapped from `/my-attendance`, `/my-attendance/history`, `/operations/teacher-attendance`, `/master-data/holiday-calendar`; in `MOB/navigation/AppShell.tsx` render them (with simple placeholder components until the story tasks) and extend the Android back handling so a sub-screen or sheet closes before leaving the screen; keep `canOpen` guarding every navigation; update `MOBT/access/menuFromAccessModel.test.tsx` and `MOBT/support/fixtures.ts` so the Teacher, Manager and Director fixtures expect the new entries (and no entry the app has no screen for)

**Checkpoint**: shared pieces and routes work; no screen is complete yet.

---

## Phase 3: User Story 1 - A Teacher Sees and Marks Their Own Month (Priority: P1) 🎯 MVP

**Goal**: a Teacher opens My Attendance, sees the current month, switches month within the year, and marks or corrects a day within the window.

**Independent Test**: sign in as a placed Teacher, mark today Present and yesterday as a half day, switch month and back, and confirm marks and totals (quickstart scenarios 1-6).

### Tests for User Story 1

- [X] T016 [P] [US1] `MOBT/attendance/myAttendance.test.tsx`: opens on the current month in the business time zone using the server's `Date` header even when the phone clock is a day off; shows one cell per day with state, status and half-day marker, and the rollup totals from the server (including a weekly-off or holiday day that the server marks `editableBy: "SELF"`, which offers Save, and one marked `NONE`, which does not); changing month and returning works; `404 "Your profile has not been set up yet."` is shown as given
- [X] T017 [P] [US1] `MOBT/attendance/myAttendanceMark.test.tsx`: Present whole day on today saves with the right body (`{statusCode, dayValue: 1, note}`) and the month is re-fetched so the cell and totals update; changing to half day and another status sends the previous mark's `version`; the status list is the server's active codes in server order with the `NON_WORKING` code (Holiday) not offered and no code hard-coded; Save is offered exactly when the day's `editableBy` is `SELF` and never otherwise, whatever the day's state
- [X] T018 [P] [US1] `MOBT/attendance/myAttendanceRefusals.test.tsx`: for every Teacher refusal in the count found in T009 (future date, older than 3 days, locked month, no placement that date, already set by a supervisor, exited, changed meanwhile) the server's 409 `{reason}` shows the plain wording, the day is unchanged and the draft is kept; "changed meanwhile" re-loads the month and shows the new value; a day set by a supervisor shows who set it and "Ask your Manager to correct it"
- [X] T019 [P] [US1] `MOBT/attendance/myAttendanceOffline.test.tsx`: with the fake server offline, Save shows "No connection", keeps the entered status, day value and note, shows nothing as saved and does not change the cell; going back online and saving again succeeds (SC-006); a failed month load shows an error with Retry and never leaves old data shown as current

### Implementation for User Story 1

- [X] T020 [US1] Create `MOB/screens/MyAttendanceScreen.tsx`: uses `useMonthView(getMyMonth)`, `MonthPicker` with kind `current`, `RollupStrip`, `MonthGrid`, `DayLegend` and `DaySheet`; loads the status codes once on first open of the sheet through `listStatusCodes()` and filters out category `NON_WORKING`; `editable` is true only when the day's `editableBy` is `SELF`; Save calls `saveMyMark(date, body)` with the existing mark's `version`, treats only a 2xx as saved, then re-fetches the month; maps 409 reasons through `refusalText`; keeps the draft on any failure; loading, empty and error states with Retry
- [X] T021 [US1] Wire the screen in `MOB/navigation/AppShell.tsx` for the `myAttendance` key, set the screen title "My Attendance", and make the default month come from `currentMonth(serverNow())` (T006, T007)

**Checkpoint**: US1 is complete and demonstrable on its own (the MVP).

---

## Phase 4: User Story 2 - A Teacher Reviews Earlier Months (Priority: P1)

**Goal**: read-only history of earlier months, current and previous year.

**Independent Test**: open Attendance History, pick two earlier months and one of the previous year, confirm correct marks and totals and no way to edit (quickstart scenario 7).

### Tests for User Story 2

- [X] T022 [P] [US2] `MOBT/attendance/attendanceHistory.test.tsx`: the picker offers the current and the previous year and no other; a month shows the same cells and totals; a locked month says "Locked"; a month with no placement shows days as not placed and zero totals with no error; no day offers Save, Clear or any edit action; opening a day shows read-only details

### Implementation for User Story 2

- [X] T023 [US2] Create `MOB/screens/AttendanceHistoryScreen.tsx` reusing `MonthPicker` with kind `history`, `RollupStrip`, `MonthGrid`, `DayLegend` and a read-only `DaySheet` (`editable` false always), loading `getMyMonth(month)`; wire the `attendanceHistory` key in `MOB/navigation/AppShell.tsx`

**Checkpoint**: US1 and US2 give a Teacher the full self-service picture.

---

## Phase 5: User Story 3 - Everyone Can See the Holiday Calendar (Priority: P2)

**Goal**: a read-only month calendar with holidays, weekly offs and an "All holidays this year" list.

**Independent Test**: as a Teacher and a Manager open the Holiday Calendar, compare with the web, switch month, open the year list (quickstart scenario 8).

### Tests for User Story 3

- [X] T024 [P] [US3] `MOBT/attendance/holidayCalendar.test.tsx`: the current month marks each non-working date and weekly off and lists the month's holidays with descriptions; the month picker reaches any month of the current year and no other year; "All holidays this year" lists every holiday of the current year in date order with descriptions and going back returns to the same month; there is no add, edit or delete control anywhere; a failed load shows Retry
- [X] T025 [P] [US3] `MOBT/attendance/holidayWeeklyOff.test.ts`: weekly offs use the default `["SUN"]` unless the calendar lists a School override for the School the user's data belongs to (taken from the user's own month view or list row), in which case that School's days are shown; a calendar with no override uses the default

### Implementation for User Story 3

- [X] T026 [US3] Create `MOB/attendance/holidayModel.ts` (pure functions: `holidaysOfMonth`, `holidaysOfYear` sorted by date, `weeklyOffFor(schoolId?)`, building the month's cells with states `WEEKLY_OFF` and `NON_WORKING`) and `MOB/screens/HolidayCalendarScreen.tsx` using `getCalendar()` once for the month view, the legend and the "All holidays this year" list; read-only; wire the `holidayCalendar` key in `MOB/navigation/AppShell.tsx`

**Checkpoint**: every role can see the Holiday Calendar.

---

## Phase 6: User Story 4 - A Manager Reviews and Corrects Their Teachers' Attendance (Priority: P2)

**Goal**: a Manager lists assigned Teachers for a month, searches, opens a Teacher's month, marks, corrects, clears, and sees a day's history.

**Independent Test**: as a Manager with two Teachers and another Manager's Teacher, confirm only the two appear, mark, correct and clear a day (quickstart scenarios 9-11).

### Tests for User Story 4

- [X] T027 [P] [US4] `MOBT/attendance/teacherAttendanceList.test.tsx`: the list shows only the Teachers the server returned (a fixture with two Managers' Teachers where the server answers for the first Manager) with School and the rollup figures; the search box is always visible, debounced by 300 ms, sends `query` and narrows the list; "load more" requests the next page of 25; month change reloads; empty state "No Teachers found"; failed load shows Retry
- [X] T028 [P] [US4] `MOBT/attendance/teacherMonth.test.tsx`: opening a Teacher loads `GET /api/v1/attendance/teachers/{id}?month=` and shows the month view; a day with `editableBy: "SUPERVISOR"` offers Save and Clear, including a weekly-off day, and a day with `NONE` (locked month, not placed, or future) offers neither; marking sends `PUT .../teachers/{id}/marks/{date}` with `{statusCode, dayValue, note, version?}`; Clear sends `DELETE` and the day becomes unmarked after the re-fetch; the day's History lists every earlier value newest first with who and when; a supervisor change makes the Teacher's own screen show "Set by <name>"
- [X] T029 [P] [US4] `MOBT/attendance/teacherScope.test.tsx`: a 404 for a Teacher the server does not return shows "Not found" and no data about the Teacher (US4 scenario 7); the app never requests or shows a Teacher id that was not in the server's list or an earlier successful view; a locked month refuses with the plain message and the day is unchanged; every Manager refusal in the count found in T009 shows plain wording and keeps the draft

### Implementation for User Story 4

- [X] T030 [US4] Create `MOB/screens/TeacherAttendanceScreen.tsx`: month selector (kind `current`), always-visible search box with a 300 ms debounce, `getTeacherGrid({month, query, page, size: 25})`, rows showing name, School and the rollup figures, "load more", loading, empty and error states, and an `onOpen(teacherId, name)` callback; wire the `teacherAttendance` key in `MOB/navigation/AppShell.tsx`
- [X] T031 [US4] Create `MOB/screens/TeacherMonthScreen.tsx`: opened from the list, loads `getTeacherMonth(teacherId, month)`, reuses `MonthPicker`, `RollupStrip`, `MonthGrid`, `DayLegend` and `DaySheet`; editable exactly when the day's `editableBy` is `SUPERVISOR` (the server returns it for any past, placed day in an unlocked month, weekly-off and holiday days included; never derive it from `state`, `locked` or the phone's date), with Save through `saveTeacherMark`, Clear through `clearTeacherMark`, and History through `getDayHistory` shown as a list; 404 shows "Not found"; re-fetches after every change; Android back returns to the list with its search and month kept
- [X] T032 [US4] Create `MOB/attendance/DayHistoryList.tsx` (every earlier value newest first with who changed it and when, and whether it was a create, correction or clear) with `MOBT/attendance/DayHistoryList.test.tsx`

**Checkpoint**: Teachers and Managers both have their attendance screens.

---

## Phase 7: User Story 5 - Menus and Safety Come From the Server (Priority: P2)

**Goal**: entries appear only because the server offered them; no role names; location and audit as in spec 018.

**Independent Test**: sign in as Teacher, Manager and Director and confirm each sees only the attendance entries the server offered; remove a permission on the web and see the entry go (quickstart scenarios 12-13).

### Tests for User Story 5

- [X] T033 [P] [US5] `MOBT/attendance/attendanceMenus.test.tsx`: a Teacher's drawer shows My Attendance, Attendance History and Holiday Calendar and no Manager entry; a Manager's shows Teacher Attendance and Holiday Calendar and no self-service entry; a Director's shows Holiday Calendar only; a user with Teacher and Manager roles gets the union with no duplicates; an item the server did not offer is not shown even if the screen exists
- [X] T034 [P] [US5] `MOBT/attendance/attendanceStale.test.tsx`: after a menu refresh that removes `/my-attendance`, an open My Attendance screen shows "Not authorized" with a way home and no data; the existing `MOBT/access/noHardCodedRoles.test.ts` passes with the new source files
- [X] T035 [P] [US5] `MOBT/api/attendanceContract.test.ts`: every call of `MOB/api/attendanceApi.ts` sends the method, path, query and body of contracts/mobile-attendance-screens.md, carries `X-HLS-Client` and either `X-HLS-Location` or `X-HLS-Location-Status`, and `Authorization`; month parameters are "YYYY-MM" and dates "YYYY-MM-DD"
- [X] T036 [US5] Confirm by test and by code review that no file under `MOB/attendance/` or the new screens chooses content by role name, hard-codes a status code, computes a total, or decides whether a day may be changed from the phone's date (only from `editableBy`, `state` and `locked`); fix any finding

- [X] T041 [US5] Update `mobile/src/screens/HomeScreen.tsx` so a business section of the menu that now has a screen in the app (MY ATTENDANCE, OPERATIONS, MASTER DATA) is not shown as a "Coming to the app soon" skeleton card; sections without a screen keep their card; add a test in `mobile/__tests__/screens/notAuthorized.test.tsx` (the existing Home tests) for a Teacher, a Manager and a Director that no card says "coming soon" for a section with a screen

**Checkpoint**: all five stories work together.

---

## Phase 8: Polish & Cross-Cutting Concerns

- [X] T037 [P] Add the new screens to the accessibility checks in `MOBT/a11y/screens.a11y.test.tsx`: every button and input has an accessible name, calendar cells announce weekday, date, state and status, and `minTouchTarget` (48 dp) is used for cells, arrows and sheet actions; confirm light and dark rendering in `MOBT/theme/theme.test.tsx`
- [ ] T038 [P] Verify against the real server data that Present is listed first by the status-code order: read the seed in `backend/src/main/java/com/hls/attendance/internal/` (default codes and `sortOrder`) and, with the backend running, the real `GET /api/v1/attendance/status-codes` response; if Present is not first, order the chooser by the server's position only and record the finding in `specs/019-mobile-attendance/quickstart-results.md`
- [X] T039 [P] Update `docs/running-mobile.md` section 5 (manual checklist) with the attendance scenarios from quickstart.md, and `docs/spec-roadmap.md` row 019 to the current status
- [ ] T040 Run the full mobile suite, lint, typecheck and `npm run check:manifest`; run the quickstart scenarios 1-15 on the emulator against the real backend, including TalkBack and the wrong-phone-clock scenario, and record results and any open items in `specs/019-mobile-attendance/quickstart-results.md`; SC-001 (mark in under 30 seconds) and SC-009 (every change audited as from the Android app, checked in AUDIT → Change History and API Access) are verified manually here, because the audit itself is tested on the server in specs 003 and 008

---

## Dependencies & Execution Order

**Phase order**: Setup → Foundational → US1 → US2 → US3 → US4 → US5 → Polish. US1 is the MVP.

**Story dependencies**:
- **US1** needs Foundational only.
- **US2** needs Foundational; it reuses US1's pieces but not US1's screen, so it can run in parallel with US1 after Phase 2.
- **US3** needs Foundational only (T026 also uses `dayPresentation` and `MonthGrid`).
- **US4** needs Foundational and reuses `DaySheet`; it does not need US1's screen.
- **US5** tests need the screens they exercise (US1 and US4 for stale and menu tests); T035 can start after T005.

**Key task ordering**:
- T005 before every screen task and before T035.
- T006 and T007 before T021.
- T014 before T020, T023, T031.
- T015 before every "wire the key" task (T021, T023, T026, T030).
- T009 (count of real refusals) before T018 and T029.

## Parallel Opportunities

- Setup: T002, T003 and T004 in parallel.
- Foundational: T006 to T013 are all different files and can run in parallel after T005; T014 follows T008 to T013; T015 is independent.
- Within each story, all test tasks (`[P]`) in parallel, then the implementation task.
- After Phase 2, US1, US2, US3 and US4 can be built by different people in parallel.

### Parallel example: Phase 2

```text
T005 → then in parallel: T006, T007, T008, T009, T010, T011, T012, T013 → T014 → (T015 any time)
```

## Implementation Strategy

1. **MVP**: Setup, Foundational and US1. A Teacher can see and mark their month on the phone.
2. **Increment 2**: US2 (History) and US3 (Holiday Calendar), both small once the shared pieces exist.
3. **Increment 3**: US4 (Manager) and US5 (menus and safety tests).
4. **Release**: Polish, the manual emulator pass, and the results file. Offline capture, leave and
   notifications are separate specs.

## Notes

- Do not add any backend, web or migration change; if a gap in the server contract is found, record it
  in `quickstart-results.md` and raise it as a spec 008 follow-up instead.
- Never keep attendance on the phone's disk, and never show a mark as saved before a 2xx.
- The clarified decisions to keep visible in code and tests: server statuses with no hard-coding (Q1), an
  always-visible Manager search (Q2), the "All holidays this year" list (Q3), explanation only with no
  contact shortcut (Q4), and History reaching the previous year only (Q5).
