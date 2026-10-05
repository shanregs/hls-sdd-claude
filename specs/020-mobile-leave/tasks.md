---

description: "Task list for feature implementation"
---

# Tasks: Mobile Leave

**Input**: Design documents from `/specs/020-mobile-leave/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/mobile-leave-screens.md,
quickstart.md, and **specs 009, 018 and 019 implemented** (this feature adds screens to the app shell of
018, reuses the month, date and refusal pieces of 019, and calls the leave endpoints of 009 unchanged;
there is no backend work).

**Tests**: included as first-class tasks. Constitution Principle IX requires per-scope tests (two
Managers), menu-visibility tests per role, and tests that every refusal leaves things unchanged and that
nothing is shown as done before the server confirms it (SC-004 to SC-007).

**Organization**: grouped by user story in priority order (spec.md US1-US5). Shared pieces (API client,
messages, dialog, cards, list hook) come first because three screens use them.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: US1-US5; absent for Setup/Foundational/Polish

## Path Conventions

`MOB` = `mobile/src`, `MOBT` = `mobile/__tests__`. All work is under `mobile/`; `backend/` and `frontend/`
are not changed. Always run commands from the `mobile` folder with JDK 17 (see `docs/running-mobile.md`).
The real server shapes are in `data-model.md` (they follow the code, not the 009 contract document).

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: folders and realistic test data.

- [X] T001 Create the folders `MOB/leave/` and `MOBT/leave/`, and confirm `npm test`, `npm run lint` and `npm run typecheck` pass from `mobile/` before any change
- [X] T002 [P] Create `MOBT/support/leaveFixtures.ts` with builders that copy the real shapes of data-model.md: `leaveRequest({status, allowedActions, ...})` for the server's `LeaveView` (`id, teacherId, teacherName, schoolId, schoolName, leaveType, firstDate, lastDate, halfDayStart, halfDayEnd, workingDays, reason, status` one of `PENDING`, `APPROVED`, `REJECTED`, `CANCELLED`, `decidedByName, decidedAt, decisionNote, cancelledBy, createdAt, version, allowedActions`), `leaveTypes()` (Casual, Sick, Personal, Other with `id, code, name`), `preview({workingDays, days, problems})` with days as `{date, value}` for counted days only, `teacherPage(content)`, `supervisorPage(content, pendingCount)` and `detail(request, days, problems)`
- [X] T003 [P] Create `MOBT/support/leaveServer.ts` that registers the leave routes on the existing `FakeServer` (`GET /api/v1/me/leave/types`, `POST /api/v1/me/leave/preview`, `POST /api/v1/me/leave`, `GET /api/v1/me/leave`, `POST /api/v1/me/leave/{id}/cancel`, `GET /api/v1/leave`, `GET /api/v1/leave/{id}`, `POST /api/v1/leave/{id}/approve`, `.../reject`, `.../revoke`), applies submits, cancels and decisions to its state so a reload shows the result, honours `status` (no status means all for the Teacher list and Pending only for the supervisor list, as the server does), `page` and `size`, records calls, and lets a test make any route answer a refusal such as `{status: 409, body: {reason}}`; add an `openLeaveScreen(menuLabel, setup)` helper to `MOBT/support/attendanceApp.tsx` (or a sibling file) that signs in, installs the leave and attendance routes and opens a screen from the drawer

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: the API client, helpers, shared components and routes every story uses.

**⚠️ CRITICAL**: no user story work can begin until this phase is complete.

- [X] T004 Create `MOB/api/leaveApi.ts` with the types of data-model.md (`LeaveStatus` one of `PENDING`, `APPROVED`, `REJECTED`, `CANCELLED`; `LeaveAction` one of `CANCEL`, `APPROVE`, `REJECT`, `REVOKE`; `LeaveType`, `LeaveRequest`, `PreviewDay {date, value}`, `LeavePreview {workingDays, days, problems}`, `LeaveDraftBody {leaveTypeId, firstDate, lastDate, halfDayStart, halfDayEnd, reason}`, `TeacherLeavePage {content, page, size, totalElements}`, `SupervisorLeavePage` adding `pendingCount`, `LeaveDetail {request, days, problems}`) and typed calls: `listLeaveTypes()`, `previewLeave(body)`, `submitLeave(body)`, `listMyLeave({status, page, size=25})`, `cancelMyLeave(id)`, `listLeave({status, page, size=25})`, `getLeave(id)`, `approveLeave(id, {note, version})`, `rejectLeave(id, {reason, version})`, `revokeLeave(id, {reason, version})`; statuses are sent upper case and `status` is left out when not filtering
- [X] T005 [P] Add a `leave` range to `MOB/attendance/monthRange.ts` (`allowedMonths("leave", now)` returns January..December of the previous, current and next year, using the business time zone UTC+05:30) and extend `MOBT/attendance/monthRange.test.ts` (31 December 23:00 UTC is already January in India, three years offered and no others, clamping at both ends)
- [X] T006 [P] Create `MOB/leave/leaveMessages.ts`: `leaveRefusalText(reason)` rewords `days set by a supervisor: <dates>` to "Some of these days were marked by a supervisor (<dates>). Correct them first, then approve.", `month locked: <YYYY-MM>` to "Attendance for <month name year> is locked.", and the stale-version text "This record was changed by someone else. Reload and try again." to "This request was changed by someone else. It now shows the latest state.", and returns every other server text as given; `isStaleLeaveRefusal(reason)`; add `MOBT/leave/leaveMessages.test.ts`; re-count the real reasons in `backend/src/main/java/com/hls/leave/internal/LeaveRequestService.java`, `LeaveDecisionService.java`, `LeaveCounter.java` and `attendance/internal/LeaveAttendanceImpl.java` and record the counts as exported constants (apply, cancel, decide) for the refusal tests
- [X] T007 [P] Create `MOB/leave/statusPresentation.ts`: label, colour (light and dark variants from `MOB/theme/tokens.ts`) and accessible text for `PENDING`, `APPROVED`, `REJECTED`, `CANCELLED` ("Cancelled by the Teacher" when `cancelledBy` is "TEACHER"); add `MOBT/leave/statusPresentation.test.ts`
- [X] T008 [P] Create `MOB/leave/previewDays.ts`: `previewDayList(firstDate, lastDate, preview, calendar)` returns one entry per date of the range (string date arithmetic, no time zone); a date present in `preview.days` is "Counted" with its `value` (1 or 0.5, shown as "whole day" or "half day"); any other date is "Holiday" when the Holiday Calendar (`MOB/attendance/holidayModel.ts`) lists it, "Weekly off" when `weeklyOffFor(calendar)` has its weekday, otherwise "Not counted"; it never computes the working-day total, which is the server's; add `MOBT/leave/previewDays.test.ts` (counted, half day, holiday over weekly off, unknown reason reads "Not counted", a range of one day, a range crossing a month)
- [X] T009 [P] Create `MOB/leave/ReasonDialog.tsx`: a Paper dialog `{title, label, required, maxLength=500, confirmLabel, onConfirm(text): Promise<string | null>, onCancel}` with a remaining-characters counter, a required text (confirm disabled when empty after trimming) or an optional one, disabled while sending, and an error line (role alert) with the text kept when `onConfirm` resolves with a refusal message; used for Cancel (no text field), Approve (optional note), Reject and Revoke (required reason); add `MOBT/leave/ReasonDialog.test.tsx` (required blocks empty and whitespace, 500 limit, text kept after a refusal, closes only on success, accessible names)
- [X] T010 [P] Create `MOB/leave/LeaveRequestCard.tsx` (type, `DD/MM/YYYY – DD/MM/YYYY` with half-day marks, working days as sent, status via T007, who decided and when, the decision note or reason; Teacher name and School when `showTeacher`; an `onPress` for Leave Management rows and an expand for history) and `MOB/leave/StatusFilter.tsx` (chips for the options it is given, with an accessible selected state and a 48 dp target); add `MOBT/leave/LeaveRequestCard.test.tsx`
- [X] T011 [P] Create `MOB/leave/useLeaveList.ts`: `useLeaveList(loader, status)` returns `{state: "loading"|"ready"|"error"|"noConnection", items, total, pendingCount, loadMore, reload, loadingMore}`; a result belongs to the status it was loaded for (another status shows loading, never old rows), a failed load drops the rows (FR-019), pages of 25 are appended, and a page failure keeps the earlier pages and shows the error; add `MOBT/leave/useLeaveList.test.tsx`
- [X] T012 Register the three screens and shell navigation: in `MOB/access/screenRegistry.ts` add keys `applyLeave`, `myLeaveHistory`, `leaveManagement` mapped from `/leave/apply`, `/leave/history`, `/operations/leave`; in `MOB/navigation/AppShell.tsx` render them (placeholder components until the story tasks), titles "Apply Leave", "My Leave History", "Leave Management", and give screens an `openRoute(route, state?)` callback that goes through `canOpen` (used to open My Leave History with a notice after a submit and Leave Management from Home); update `MOBT/support/fixtures.ts` (Teacher gets LEAVE → Apply Leave `/leave/apply` and My Leave History `/leave/history`; Manager and Director get OPERATIONS → Leave Management `/operations/leave`) and `MOBT/access/menuFromAccessModel.test.tsx`, `MOBT/screens/notAuthorized.test.tsx` for the new entries, and update the T041 Home test in `MOBT/screens/notAuthorized.test.tsx` ("does not preview a section that already has a screen") so the Director's OPERATIONS card is expected to be absent, because Leave Management now gives that section a screen

**Checkpoint**: shared pieces and routes work; no screen is complete yet.

---

## Phase 3: User Story 1 - A Teacher Applies for Leave (Priority: P1) 🎯 MVP

**Goal**: a Teacher chooses a type, dates, half days and a reason, checks the server's preview, submits, and lands on their history with the new request.

**Independent Test**: sign in as a placed Teacher, apply for three days with a half day at the end, read the preview, submit, and confirm a Pending request with the right working days appears (quickstart scenarios 2-4).

### Tests for User Story 1

- [X] T013 [P] [US1] `MOBT/leave/applyLeave.test.tsx`: the type chooser lists exactly the server's types in server order, none built in; first and last date, "Half day on the first day", "Half day on the last day" and a Reason field with a counter are shown; "Check" posts `{leaveTypeId, firstDate, lastDate, halfDayStart, halfDayEnd, reason}` and shows the server's working days, the counted days with whole or half day, and the other dates of the range as "Weekly off", "Holiday" or "Not counted" only (a date before the placement reads "Not counted"; calendar fetched once); changing any field clears the preview; Submit is disabled until a preview of the current draft listed no problems; the app blocks only a missing leave type, date or reason and a last date before the first date ("The last date cannot be before the first date."), and sends nothing else for checking itself; on 201 the form is cleared and My Leave History opens with "Request submitted" and the new request at the top
- [X] T014 [P] [US1] `MOBT/leave/applyLeaveRefusals.test.tsx`: for every apply refusal in the count found in T006 (type, dates, last before first, "Give a reason.", more than 90 days, started more than 30 days ago, not placed, no working day, both half days on one working day, overlap with a pending or approved request, locked month) the server's text appears in plain wording in the preview or on submit (400 and 409 `{reason}`), nothing is created and the draft is kept; the wrong-phone-clock case: a start 31 days back is refused only because the server says so
- [X] T015 [P] [US1] `MOBT/leave/applyLeaveOffline.test.tsx`: with the fake server offline, Check and Submit show "No connection. Your request was not submitted.", keep every entry, show nothing as submitted and create nothing; going back online and submitting succeeds (SC-006); a `404 "Your profile has not been set up yet."` on opening is shown instead of the form; a failed type list shows an error with Retry

### Implementation for User Story 1

- [X] T016 [P] [US1] Create `MOB/leave/draft.ts`: the draft state `{leaveTypeId, firstDate, lastDate, halfDayStart, halfDayEnd, reason, preview}` with `appProblems(draft)` returning only "Choose a leave type.", "Choose the first and last date.", "The last date cannot be before the first date." and "Give a reason." (reason trimmed, at most 500 characters), `toBody(draft)` (reason trimmed, half-day flags false when unset) and `edit(draft, change)` that clears `preview` on every change; add `MOBT/leave/draft.test.ts` (each check, trimming, the 500-character limit, nothing else checked such as 30 or 90 days)
- [X] T017 [P] [US1] Create `MOB/leave/DateChooser.tsx`: a sheet with the `MonthPicker` of spec 019 (range `leave`, T005) and the month's days as a list or grid; props `{value, onChange, label}` (no date limits: the server decides them); returns a `YYYY-MM-DD` string, shown elsewhere as DD/MM/YYYY; add `MOBT/leave/DateChooser.test.tsx` (reaches the previous, current and next year, no other, accessible labels with the full date)
- [X] T018 [P] [US1] Create `MOB/leave/PreviewPanel.tsx`: the server's working days as the headline, the list from `previewDayList` (T008) with accessible labels such as "Tuesday 6 October 2026, counted, whole day", and each server problem in plain wording (T006) as an alert; add `MOBT/leave/PreviewPanel.test.tsx`
- [X] T019 [US1] Create `MOB/screens/ApplyLeaveScreen.tsx`: loads `listLeaveTypes()` once, the form of the Contract section "Apply Leave" using `DateChooser`, a half-day switch for each end, a Reason field (maximum 500 characters, counter), "Check" (disabled until a leave type and both dates are valid) calling `previewLeave`, `PreviewPanel`, Submit enabled only after a preview of the current draft with no problems and no `appProblems`; submit through `submitLeave`, treat only a 2xx as submitted, then clear the draft and call `openRoute("/leave/history", {notice: "Request submitted"})`; map 400 and 409 reasons through `leaveRefusalText`; keep the draft on any failure; loading, error (Retry) and the 404 message states
- [X] T020 [US1] Wire the screen in `MOB/navigation/AppShell.tsx` for the `applyLeave` key and pass `openRoute`

**Checkpoint**: US1 is complete and demonstrable on its own (the MVP), with My Leave History still a placeholder until US2.

---

## Phase 4: User Story 2 - A Teacher Follows and Cancels Their Requests (Priority: P1)

**Goal**: own requests newest first, filtered by status, with Cancel only where the server offers it.

**Independent Test**: with one Pending, one Approved and one Rejected request, confirm status and decision details, filter, and cancel the Pending one (quickstart scenario 5).

### Tests for User Story 2

- [X] T021 [P] [US2] `MOBT/leave/myLeaveHistory.test.tsx`: lists only the server's requests newest first with type, dates (DD/MM/YYYY), half-day marks, working days, status, who decided and when, and the note or reason; chips All, Pending, Approved, Rejected, Cancelled send `status` upper case (All sends none); a Rejected request shows its reason; "Load more" requests the next page of 25; opening with the notice "Request submitted" shows it and still reloads from the server; empty state "No leave requests"; failed load shows Retry and never old rows; a pull-to-refresh sends a new request
- [X] T022 [P] [US2] `MOBT/leave/myLeaveCancel.test.tsx`: "Cancel request" is shown exactly when `allowedActions` contains `CANCEL`, whatever the dates or the phone clock (a Pending request, an Approved one the server still allows, and none for Rejected, Cancelled or an Approved one that has started); it confirms first, posts `POST /api/v1/me/leave/{id}/cancel` with no body, then reloads the list; for every cancel refusal in the count found in T006 ("This leave has already started. Ask your Manager to revoke it.", "This request was already cancelled.", "A rejected request cannot be cancelled.") the plain text is shown and the request is reloaded in its current state; offline cancel shows "No connection" and nothing changes

### Implementation for User Story 2

- [X] T023 [US2] Create `MOB/screens/MyLeaveHistoryScreen.tsx` using `useLeaveList(listMyLeave, status)`, `StatusFilter`, `LeaveRequestCard` (expandable), "Load more", the optional `notice` from the shell, "Cancel request" through `ReasonDialog` without a text field and `cancelMyLeave(id)`, and reload after a cancel; pull-to-refresh reloads the list from the server; wire the `myLeaveHistory` key in `MOB/navigation/AppShell.tsx`

**Checkpoint**: US1 and US2 give a Teacher the full leave picture.

---

## Phase 5: User Story 3 - A Manager or Director Decides Requests (Priority: P1)

**Goal**: scope lists, a detail with the days it would mark, and approve, reject and revoke where the server offers them.

**Independent Test**: as a Manager with two Teachers and another Manager's Teacher, confirm only the two requests appear, approve one with a note, reject the other with a reason (quickstart scenarios 6-8).

### Tests for User Story 3

- [X] T024 [P] [US3] `MOBT/leave/leaveManagement.test.tsx`: opens on Pending with "Pending: N" from `pendingCount`; chips Pending, Approved, Rejected, Cancelled (no All) send `status`; a row shows Teacher, School, type, dates, working days and status and opens the detail, with no Approve, Reject or Revoke on any row; paging by 25; with two Managers' Teachers where the server answers for the first Manager only, no other Teacher's request is ever shown; a Director fixture shows both Managers' requests; a 404 detail shows "Not found" with no data about the Teacher; empty state "No leave requests"; failed load shows Retry; Android back from the detail returns to the list with its filter kept; a pull-to-refresh reloads the list and the count
- [X] T025 [P] [US3] `MOBT/leave/leaveDetail.test.tsx`: the detail shows the request and the days approving would mark (`days`) and any `problems` in plain wording; Approve, Reject and Revoke are shown exactly for the actions in `allowedActions` (Pending: Approve and Reject; Approved: Revoke; none otherwise), never derived from status, dates or the phone's clock (a wrong phone clock and a request whose server `allowedActions` is empty show none)
- [X] T026 [P] [US3] `MOBT/leave/leaveDecisions.test.tsx`: Approve confirms with an optional note and posts `{note, version}`; Reject requires a reason, up to 500 characters, and posts `{reason, version}`; Revoke the same; each reloads the list and the count afterwards; for every decision refusal in the count found in T006 (days set by a supervisor naming the dates, month locked, already approved, already rejected, cancelled by the Teacher, only a pending request can be decided, only an approved request can be revoked, reason required, note too long, now overlaps another request, changed by someone else) the dialog stays open with the plain wording and its text kept, and for a 409 the detail reloads and shows the current state; offline shows "No connection" and nothing is shown as decided (SC-006); a decision is sent once even if Confirm is tapped twice

### Implementation for User Story 3

- [X] T027 [US3] Create `MOB/screens/LeaveRequestDetailScreen.tsx`: loads `getLeave(id)` (404 shows "Not found" only), shows the request via `LeaveRequestCard`, the days it would mark, any `problems`, and Approve, Reject and Revoke exactly for `allowedActions`, each through `ReasonDialog` (Approve optional note, Reject and Revoke required reason) calling `approveLeave`, `rejectLeave` or `revokeLeave` with `{note|reason, version}`; maps 409 and 400 reasons through `leaveRefusalText`, reloads the request after any 409 and after success, and reports success to its parent so the list and count reload
- [X] T028 [US3] Create `MOB/screens/LeaveManagementScreen.tsx`: `useLeaveList(listLeave, status)` with `StatusFilter` (Pending default), "Pending: N" from `pendingCount`, `LeaveRequestCard` rows that only open the detail, "Load more", pull-to-refresh reloads the list and the count, and the detail rendered in place using `useInnerBack` of spec 019 so Android back returns to the list with its filter kept; accept an initial status from the shell (the Home widget opens Pending); wire the `leaveManagement` key in `MOB/navigation/AppShell.tsx`

**Checkpoint**: Teachers and approvers both have their leave screens.

---

## Phase 6: User Story 4 - Pending Leave Is Visible From Home (Priority: P2)

**Goal**: a Pending count widget on the Home of users whose menu offers Leave Management.

**Independent Test**: with three Pending requests in scope, Home shows 3 and tapping it opens the Pending list; decide one and the count is 2 on return (quickstart scenario 9).

### Tests for User Story 4

- [X] T029 [P] [US4] `MOBT/leave/homeLeaveWidget.test.tsx`: for a Manager and a Director whose menu offers `/operations/leave` Home calls `GET /api/v1/leave?status=PENDING&size=1` and shows "Pending leave requests: N" from `pendingCount`; "No pending leave requests" for 0, still opening Leave Management; tapping opens Leave Management on Pending; after deciding a request and going Home the count is reloaded; a Teacher (no `/operations/leave` entry) sees no widget and no request is made; a failed count shows "Could not load" in the widget alone while the rest of Home works; a pull-to-refresh reloads the count; no "coming soon" card remains for the section that has a screen

### Implementation for User Story 4

- [X] T030 [US4] Create `MOB/leave/usePendingCount.ts` (`{state, count, reload}`; loads only when enabled) and change `MOB/screens/HomeScreen.tsx` to show the widget when the access model's navigation contains `/operations/leave` (decided by the route, never by role), opening it through `openRoute("/operations/leave", {status: "PENDING"})`; reload the count when Home becomes the current screen again and on pull-to-refresh; keep the existing "coming soon" cards for sections without a screen

**Checkpoint**: approvers see waiting work at a glance.

---

## Phase 7: User Story 5 - Menus, Scope and Safety Come From the Server (Priority: P2)

**Goal**: entries appear only because the server offered them; no role names; location and audit as in spec 018.

**Independent Test**: sign in as Teacher, Manager and Director and confirm each sees only the leave entries the server offered; remove a permission and see the entry go (quickstart scenarios 1, 10).

### Tests for User Story 5

- [X] T031 [P] [US5] `MOBT/leave/leaveMenus.test.tsx`: a Teacher's drawer shows Apply Leave and My Leave History and no Leave Management; a Manager's and a Director's show Leave Management and no LEAVE section; a user with Teacher and Manager roles gets the union with no duplicates; an item the server did not offer is not shown even if the screen exists
- [X] T032 [P] [US5] `MOBT/leave/leaveStale.test.tsx`: after a menu refresh that removes `/leave/apply` (or `/operations/leave` while a detail is open) the open screen shows "Not authorized" with a way home and no data; the existing `MOBT/access/noHardCodedRoles.test.ts` passes with the new source files
- [X] T033 [P] [US5] `MOBT/api/leaveContract.test.ts`: every call of `MOB/api/leaveApi.ts` sends the method, path, query and body of contracts/mobile-leave-screens.md (statuses upper case, `status` omitted for All, `size=25`, bodies `{note, version}` and `{reason, version}`, the draft body with trimmed reason), carries `X-HLS-Client` and either `X-HLS-Location` or `X-HLS-Location-Status`, and `Authorization`; dates are `YYYY-MM-DD`
- [X] T034 [US5] Confirm by test and by code review that no file under `MOB/leave/` or the new screens chooses content by role name, hard-codes a leave type, computes a working-day count, a limit or a total, or decides whether an action is offered from a status, a date or the phone's clock (only from `allowedActions`); fix any finding

---

## Phase 8: Polish & Cross-Cutting Concerns

- [X] T035 [P] Add the new screens to the accessibility checks in `MOBT/a11y/leave.a11y.test.tsx`: every button, chip, switch and input has an accessible name, dialogs announce their title and error, list cards announce type, dates, status and decision, and `minTouchTarget` (48 dp) is used for chips, buttons and rows; confirm light and dark rendering for the new status colours
- [ ] T036 [P] Verify against the real server: with the backend running, `GET /api/v1/me/leave/types` order and names, a real `POST /api/v1/me/leave/preview` (check that `days` lists counted days only and that an empty reason answers "Give a reason."), and the real refusal texts of T006; record any difference in `specs/020-mobile-leave/quickstart-results.md` and fix `leaveMessages.ts` if a text differs
- [X] T037 [P] Update `docs/running-mobile.md` section 5 (manual checklist) with the leave scenarios from quickstart.md, and `docs/spec-roadmap.md` row 020 to the current status
- [ ] T038 Run the full mobile suite, lint, typecheck and `npm run check:manifest`; run the quickstart scenarios 1-12 on the emulator against the real backend, including TalkBack and the wrong-phone-clock scenario, and record results and any open items in `specs/020-mobile-leave/quickstart-results.md`; SC-001 (apply in under 90 seconds), SC-002 (decide in under 30 seconds) and SC-010 (every change audited as from the Android app, checked in AUDIT → Change History and API Access) are verified manually here, because the audit itself is tested on the server in specs 003 and 009; also check by hand that approved leave appears in, and cancelled or revoked leave leaves, My Attendance (FR-017)

---

## Dependencies & Execution Order

**Phase order**: Setup → Foundational → US1 → US2 → US3 → US4 → US5 → Polish. US1 is the MVP.

**Story dependencies**:
- **US1** needs Foundational only; its submit opens My Leave History, which is a placeholder until US2 (the route and the notice exist from T012).
- **US2** needs Foundational only; it can run in parallel with US1 after Phase 2.
- **US3** needs Foundational and reuses `ReasonDialog`, `LeaveRequestCard` and `useLeaveList`; it does not need US1 or US2 screens.
- **US4** needs US3 (the Leave Management route and the Pending count call).
- **US5** tests need the screens they exercise; T033 can start after T004.

**Key task ordering**:
- T004 before every screen task and before T033; T006 before T014, T022, T026.
- T005 before T017; T008 before T018; T009 before T019, T023, T027; T010 and T011 before T023, T028.
- T012 before every "wire the key" task (T020, T023, T028) and before T030.
- T016, T017, T018 before T019.

## Parallel Opportunities

- Setup: T002 and T003 in parallel.
- Foundational: T005 to T011 are all different files and can run in parallel after T004; T012 is independent of them but follows T004.
- Within each story, all test tasks (`[P]`) in parallel, then the implementation tasks (T016-T018 in parallel, then T019).
- After Phase 2, US1, US2 and US3 can be built by different people in parallel.

## Implementation Strategy

1. **MVP**: Phases 1-3 (a Teacher can apply and see the preview and submit), demonstrable on the emulator.
2. **Increment 2**: US2 (history and cancel), then US3 (decide), each testable alone.
3. **Increment 3**: US4 (Home widget) and US5 (menu, stale, contract and role-name checks), then Polish and the
   device pass (T036, T038), which is the only part that needs the real backend and the emulator.
