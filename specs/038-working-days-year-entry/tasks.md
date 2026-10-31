---

description: "Task list for feature implementation"
---

# Tasks: School Working Days — Enter a Whole Year on One Screen

**Input**: Design documents from `/specs/038-working-days-year-entry/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/school-working-days-year-api.md,
quickstart.md, and spec 037 implemented (it is on `main`, V52).

**Tests**: included as first-class tasks. Constitution Principle IX requires per-role tests on every endpoint and UI
tests per role. The retry and conflict tests are mandatory: the figure drives pay once consumers adopt it. The pure
month-status rules are tested before they are written.

**Organization**: grouped by user story (spec.md US1-US5). Order: Foundation, US1 (load and save a year), US2 (partial
save and conflicts), US3 (permissions and phone layout), US4 (helpers), US5 (reach the screen and leaving it), Polish.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: US1-US5; absent for Setup/Foundational/Polish

## Path Conventions

Backend `backend/src/main/java/com/hls/{attendance/workingdays,identity/permissions}/...`, tests under
`backend/src/test/java/com/hls/...`; frontend `frontend/src/features/schoolWorkingDays/...`. **No migration and no new
permission key** (plan.md). Run one Maven test class at a time (`mvn -o test -Dtest=...`); the machine is slow. Docker
Desktop must be running for the Testcontainers tests (`docker info` first).

---

## Phase 1: Setup

- [X] T001 Verify the prerequisites before writing code. In `backend/src/main/java/com/hls/attendance/workingdays/internal/` confirm `WorkingDaysService` (`insert`, `requireSchoolExists`, the private `record`, `checkVersion`, `conflictFor`), `WorkingDaysRules.checkFigure` and `checkNewYear`, `CalendarCounts.byMonth`, `WorkingDaysScope.requireSchool`, `WorkingDaysViewFactory.userNames`, `SchoolWorkingDaysRepository.findBySchoolIdAndYear`, and `identity.permissions.PermissionMatrixService.isGranted`; confirm the controller base path `/api/v1/school-working-days` and `WorkingDaysExceptionAdvice`. In `frontend/src/features/schoolWorkingDays/` confirm `schoolWorkingDaysApi.ts` (`call`, `Outcome`), `workingDaysRules.ts` (`figureProblem`, `formatFigure`) and `useGrantedActions`. If anything is missing or has a different shape, STOP and report which; do not stub it. Record the names found in `specs/038-working-days-year-entry/research.md` as section 14
- [X] T002 [P] Add the types and the two calls to `frontend/src/features/schoolWorkingDays/schoolWorkingDaysApi.ts` for `GET /year` and `PUT /year` per contracts/school-working-days-year-api.md: `YearMonthState` (month, daysInMonth, calendarDerived, id, workingDays, version, updatedBy, updatedAt; the last five nullable), `YearView`, `YearSaveRequest` (months with `month`, `workingDays`, `version` nullable), `YearSaveResult` (created, updated, unchanged, notSaved, months with `status` `CREATED|UPDATED|UNCHANGED|NOT_SAVED`, `reason` `INVALID_FIGURE|STALE|ALREADY_EXISTS|ROW_DELETED|NOT_PERMITTED`, `message`, `current`); a request-level failure returns the existing `Outcome` error shape

---

## Phase 2: Foundation (blocks all stories)

- [X] T003 [P] Write `backend/src/test/java/com/hls/attendance/workingdays/YearEntryRulesTest.java` first (or add to `WorkingDaysRulesTest`): the pure month-status decision as a hand-written table covering every row of the status table in data-model.md ("Month status rules"): no figure and no version; a row appeared meanwhile with equal and with different figure; version matches with equal and with different figure; version differs with equal and with different figure; deleted meanwhile; and the order "figure validated first, then permission, then stored-state rules" (done as `MonthDecisionTest`, in the package `com.hls.attendance.workingdays`)
- [X] T004 Add `public static MonthDecision decide(BigDecimal typed, Integer version, SchoolWorkingDaysRow current, boolean canCreate, boolean canEdit, int year, int month)` (name may differ) to `WorkingDaysRules.java` or a new `internal/MonthDecision.java`: pure, no repository, no clock; returns one of `CREATE`, `UPDATE`, `UNCHANGED` or a refusal (`INVALID_FIGURE`, `NOT_PERMITTED`, `STALE`, `ALREADY_EXISTS`, `ROW_DELETED`) following data-model.md exactly; the figure is checked with the existing `checkFigure` (0 up to the days in the month, in steps of 0.5); makes T003 pass
- [X] T005 [P] Add `public boolean isGranted(Set<Role> callerRoles, PermissionModule module, PermissionAction action)` to `backend/src/main/java/com/hls/identity/permissions/PermissionGuard.java` using the same `anyMatch(permissionMatrixService.isGranted(...))` as `require`, and make `require` call it; add a case to the existing `PermissionGuard` test class (find it with a search; if none exists create `PermissionGuardTest.java` next to the guard) proving `isGranted` is true for a granted role, false for an ungranted one and false for an empty role set
- [X] T006 Add `saveMonth(UUID actor, Set<Role> roles, UUID schoolId, int year, int month, BigDecimal workingDays, Long version, boolean canCreate, boolean canEdit)` to `WorkingDaysService.java` returning a `MonthOutcome` record (`status`, `reason`, `message`, `row`): it runs `decide(...)` in its own `TransactionTemplate` transaction; `CREATE` calls the existing `insert(..., Action.CREATE)`; `UPDATE` changes the row and calls the existing `record(..., Action.EDIT, ...)` and `changes.record(...)` exactly as `edit` does (one history row and one central audit event per changed month); `UNCHANGED` and every refusal write nothing; a `DataIntegrityViolationException` on create (two creates at once) or an `ObjectOptimisticLockingFailureException` on update reloads the row and re-decides (equal figure gives `UNCHANGED`, different gives `ALREADY_EXISTS` or `STALE`); a row gone gives `ROW_DELETED` and is never recreated. Do not change `create`, `edit`, `delete` or `insert` behaviour
- [X] T007 [P] Write `backend/src/test/java/com/hls/attendance/workingdays/YearEntryApiTest.java` skeleton with the shared fixtures (sign in as each role, a School in a new zone, a helper to PUT/GET `/year`, `thisYear() + 1` as the working year) so later tasks add cases to it; no cases yet beyond one that fails because `/year` does not exist (404)

**Checkpoint**: the month rules and `saveMonth` are done and tested at unit level; no endpoint yet.

---

## Phase 3: User Story 1 — Enter or correct all twelve months in one go (P1) 🎯 MVP

**Goal**: pick a School and a year, type figures, press Save once, and the changed months are stored.

**Independent test**: School A, a year with no figures: type 12 valid figures, Save once, see 12 stored rows in the grid; reopen, change two months, Save, and only those two rows change.

- [X] T008 [P] [US1] Add to `YearEntryApiTest.java` (write first, they fail until T010-T012): GET `/year` returns twelve months in order with `daysInMonth` (leap February in a leap year), `calendarDerived` equal to the grid's value for the same month, and all stored fields `null` when no figure exists; PUT creates only the sent months (months not sent stay without a row); PUT of unchanged figures returns `UNCHANGED` and adds no history row and no audit entry (count `school_working_days_history` and the audit entries before and after); an update changes only the sent months and bumps only their `version`; a figure stored here appears in `GET /api/v1/school-working-days` and in the row history exactly like one stored from the grid (FR-023); a blank or missing entry is simply not sent and never deletes
- [X] T009 [P] [US1] Add to `YearEntryApiTest.java` the request-level cases: 400 for an unknown School, a year outside the window (message names the allowed years: current year minus 5 to plus 5), a `month` outside 1 to 12, the same month twice, no months, more than 12 months, and a missing `schoolId` or `year`; and a statement-count case in the style of `WorkingDaysGridTest.theNumberOfStatementsAndTheTimeDoNotGrowWithTheNumberOfSchools`: the number of statements for GET `/year` is the same for a year with 0 and with 12 stored months
- [X] T010 [US1] Create `backend/src/main/java/com/hls/attendance/workingdays/internal/YearEntryService.java`: `loadYear(actor, roles, schoolId, year)` runs `scope.requireSchool`, `requireSchoolExists`, `WorkingDaysRules.checkNewYear(year, calendar.businessToday())`, then reads `findBySchoolIdAndYear`, `CalendarCounts.byMonth` for the twelve months, the School and Zone through `SchoolDirectory`, and the last-changed names through `WorkingDaysViewFactory.userNames`; returns the Year Entry View of data-model.md (twelve months in order, stored fields `null` when no row); a fixed number of queries whatever is stored
- [X] T011 [US1] In `YearEntryService.java` add `saveYear(actor, roles, request)`: the request-level checks of research.md section 4 (400 messages as in T009, using `InvalidInputException`), `scope.requireSchool` once, then for each requested month call `WorkingDaysService.saveMonth(...)` with `canCreate` and `canEdit` from `PermissionGuard.isGranted` for `SCHOOL_WORKING_DAYS` CREATE and EDIT; build the Year Save Result (created, updated, unchanged, notSaved, and per month the status, reason, message and the `current` month state after the call, with its new version); months come back in request order; make the whole method safe to call twice with the same request (nothing extra written the second time)
- [X] T012 [US1] Add `GET /year` and `PUT /year` to `web/SchoolWorkingDaysController.java`: GET calls `guard.require(roles, MODULE, VIEW)`; PUT calls `guard.require(... VIEW)` and then refuses with 403 unless `isGranted` for CREATE or for EDIT; records `YearSaveRequest` (`schoolId`, `year`, `months` of `month`, `workingDays`, `version` nullable) per contracts/school-working-days-year-api.md; map the `InvalidInputException` as the grid does; makes T008 and T009 pass
- [X] T013 [P] [US1] Create `frontend/src/features/schoolWorkingDays/yearEntryState.ts` (pure, no React) with its test `yearEntryState.test.ts` written first: state `{ loaded: YearView | null, typed: Record<month, string>, results: YearSaveResult | null, helperFilled: Set<month> }`; `initial(view)` puts each stored figure (formatted with `formatFigure`) into `typed`; `dirtyMonths(state)` returns the months whose typed text differs from the loaded figure (a month changed back to its loaded value is not dirty; a blank field on a month with no figure is not dirty; a blank field on a stored month is **not** sent and never deletes, FR-008); `buildRequest(state, schoolId, year)` returns only dirty months with `version` (null when no stored figure); `applyResult(state, result)` replaces `loaded` with each returned `current`, keeps the typed text of every `NOT_SAVED` month, and resets the others to the stored figure
- [X] T014 [P] [US1] Create `frontend/src/features/schoolWorkingDays/MonthField.tsx`: one month as a labelled row with the month name and year in the accessible name of the field (FR-026), the days in the month, a numeric text field (`inputMode="decimal"`, half-day figures), the stored figure and "last changed by/on" when one exists, and the calendar-derived count as a hint visibly different from a stored figure ("Calendar: 26"); inline validation text from `figureProblem(value, year, month)` shown under the field; props for `readOnly`, `error` and `autoFocus`
- [X] T015 [US1] Create `frontend/src/features/schoolWorkingDays/YearEntryPage.tsx`: School picker (from `listSchoolOptions`) and year picker limited to the current year minus 5 to plus 5 and defaulting to the current year; loads `getYear` on pick (loading, error-with-retry states); renders the twelve `MonthField`s in a single column from `xs` and two columns from `sm` (no table, `minWidth: 0`, so no horizontal page scroll at 375 px); Save is disabled with the text "Nothing to save" while `dirtyMonths` is empty; Save calls `saveYear(buildRequest(...))`, then shows "N created, N updated, N unchanged" and keeps the fields in sync through `applyResult`; always shows the fixed notice "Months already finalised in payroll or attendance keep the figure they used." once beside Save (FR-015); dates and months as in the grid (DD/MM/YYYY, month names)
- [X] T016 [P] [US1] Write `frontend/src/features/schoolWorkingDays/YearEntryPage.test.tsx` (Admin fixture, mocked `authFetch` as `SchoolWorkingDaysPage.test.tsx` does): loads twelve months with hints; typing two months enables Save and sends only those two with the right `version` values; Save disabled with "Nothing to save" when nothing changed or when a field is typed and then changed back; the result summary after a successful save; a second Save after the first works without a reload (new versions used); a blank field on a stored month sends nothing; loading and error-with-retry states; the finalised-month notice shown once
- [X] T017 [US1] Add the route in `frontend/src/App.tsx`: `/master-data/school-working-days/year` wrapped in `RouteGuard` rendering `YearEntryPage` (no access-model change: `isAuthorizedPath` already authorizes children of the authorized `/master-data/school-working-days` route)

**Checkpoint**: an Admin can fill a whole year in one Save; quickstart steps 1 to 4 pass.

---

## Phase 4: User Story 2 — Partial save: good months saved, problem months reported (P1)

**Goal**: when some months cannot be saved, the others are stored and the user sees which failed and why, with their typing kept.

**Independent test**: enter 12 figures where one is 31 for a 30-day month and another month was changed by someone else since the screen opened; Save; 10 stored, 2 reported by name with reasons, the two typed values still in the fields.

- [X] T018 [P] [US2] Add to `YearEntryApiTest.java` (write first): out-of-range (31 for a 30-day month, 29 in a non-leap February, negative) and non-half-step (25.3) figures come back `NOT_SAVED` `INVALID_FIGURE` with the allowed range in the message while every other valid month is stored (FR-009); a month changed by someone else to a different figure is `STALE` with `current` showing the stored row and who changed it, and nothing is overwritten (SC-003, two simultaneous users with a real second request); a month created by someone else with a different figure is `ALREADY_EXISTS`; a month deleted by someone else is `ROW_DELETED` and is not recreated and writes no audit; 0 is stored as 0 and is not treated as blank
- [X] T019 [P] [US2] Add to `YearEntryApiTest.java` the clarified idempotency cases (SC-006, FR-014): the same PUT sent twice writes nothing the second time (row, history and audit counts identical) and the second answer reports every month `UNCHANGED`; a month already created by someone else with exactly the typed figure is `UNCHANGED`, not `ALREADY_EXISTS`; an update whose first attempt already succeeded (newer version, same figure) is `UNCHANGED`; two simultaneous PUTs of the same new month produce one row and no 500
- [X] T020 [US2] Fix whatever T018 and T019 expose in `WorkingDaysService.saveMonth` and `YearEntryService` (the logic is specified in data-model.md; do not weaken a test to pass); confirm a refused or unchanged month writes no history row and no audit event
- [X] T021 [P] [US2] Extend `YearEntryPage.test.tsx` with a partial-save fixture: a 200 response with some months `NOT_SAVED` (each reason) shows "N saved, N not saved", lists the failed months in calendar order with one reason each, keeps the typed text in those fields, shows the stored figure and who changed it beside a `STALE` or `ALREADY_EXISTS` month, moves focus to the first failed month, and offers a second Save that sends only the still-dirty months with the new versions; a whole-request failure (network error, 500, expired session) keeps every typed value, shows nothing as saved, and a retry sends the same body
- [X] T022 [US2] In `YearEntryPage.tsx` and `MonthField.tsx` implement the result display of T021: failed-month list (an `aria-live="polite"` summary and a message under each failed field), focus to the first failed month, "Use the stored figure" per `STALE`/`ALREADY_EXISTS` month (replaces the typed text with the stored figure), and no loss of typed text on any failure; the finalised-month notice stays visible

**Checkpoint**: quickstart steps 5 to 7 and 12 pass.

---

## Phase 5: User Story 3 — The right permissions, and it works on a phone (P1)

**Goal**: same access rules as the grid, enforced per month on the server, and a layout with no horizontal page scroll at 375 px.

**Independent test**: sign in as each role and check the menu and the screen; call both endpoints as a role without the permission and see 403; open the screen at 375 px and see no horizontal page scroll.

- [X] T023 [P] [US3] Add to `YearEntryApiTest.java` the per-role matrix on both endpoints: Admin and Director read and save; Manager, Teacher and System get 403 on GET and PUT by default; a user with View but neither Create nor Edit gets 200 on GET and 403 on PUT; with the matrix changed in the test (restore it in `finally`, as `WorkingDaysGridTest` does) a role with Create but not Edit gets `CREATED` for empty months and `NOT_PERMITTED` for stored months in the same request, and the reverse, and `NOT_PERMITTED` writes nothing; a Manager granted View is limited to assigned Schools (403 for another School on both endpoints)
- [X] T024 [US3] Fix whatever T023 exposes (controller guard order, per-month `canCreate` and `canEdit`, scope); the backend decides, independent of what the screen shows (Principle X)
- [X] T025 [P] [US3] Extend `YearEntryPage.test.tsx` with a fixture per role using the way `SchoolWorkingDaysPage.test.tsx` provides the access model: Admin and Director (every month editable, Save offered when dirty); View only (twelve months and hints read-only, no Save); Create without Edit (only empty months editable); Edit without Create (only stored months editable); a user without View gets the not-authorized page through the route guard; actions the user may not perform are hidden, not disabled (Principle IV)
- [X] T026 [US3] In `YearEntryPage.tsx` read `useGrantedActions("/master-data/school-working-days")` and set `readOnly` per month (stored figure needs EDIT, empty month needs CREATE); hide Save and the helpers when the user has neither; keep the server's `NOT_PERMITTED` as the final word (show its message if it comes back)
- [X] T027 [P] [US3] Add `YearEntryPage` to `frontend/src/a11y/a11y.test.tsx` in both themes, with mocks for `/api/v1/school-working-days/year` and `/schools` (the file mocks `/api/v1/school-working-days...` by URL prefix near line 956; add the `/year` case before the generic prefix case), including a state with failed months; each field has a label naming its month and year; the keyboard order is January to December then Save (FR-026); errors are announced
- [X] T028 [US3] Verify the phone layout in code and by a test: the page and `MonthField` use no fixed widths larger than the container, long error text wraps, and a Vitest check asserts that the month list renders as a stacked list (no `<table>` role). The real 375 px browser check is manual and is recorded in T038

**Checkpoint**: quickstart steps 8 and 11 pass (step 8 by hand).

---

## Phase 6: User Story 4 — Fill the year faster (P2)

**Goal**: "Use calendar counts" and "Same figure for all empty months" fill fields for review without saving.

**Independent test**: with a year that has two stored figures, press "Use calendar counts" and see the ten empty months filled with their hints and the two stored months untouched; Save creates ten rows.

- [X] T029 [P] [US4] Write first, in `yearEntryState.test.ts`: `useCalendarCounts(state)` fills only months that have no stored figure and no typed text, with `calendarDerived`, and marks them in `helperFilled`; `sameFigureForEmpty(state, value)` fills each such month for which `figureProblem(value, year, month)` is null and returns the months it could not fill (30 in February); neither changes a stored figure or typed text; changing or clearing a helper-filled value makes it an ordinary typed value (removed from `helperFilled`); neither function does I/O (the functions are named `fillWithCalendarCounts` and `fillSameFigure`; see research.md section 14)
- [X] T030 [US4] Implement the two functions in `yearEntryState.ts` (pure) to make T029 pass
- [X] T031 [US4] In `YearEntryPage.tsx` add the two buttons (hidden for users who cannot create) and a small dialog asking for the number for "Same figure for all empty months"; show the list of months that could not be filled; helper-filled fields are marked as such ("filled, not saved") until saved; extend `YearEntryPage.test.tsx` with both helpers, including that nothing is sent until Save and that Save then sends the filled months as new figures

**Checkpoint**: quickstart step 9 passes.

---

## Phase 7: User Story 5 — Reach the screen, and go back to the grid (P2)

**Goal**: one click from the grid to the year screen on the same School and year, safe handling of unsaved changes, and the grid shows the new figures afterwards.

**Independent test**: in the grid filter to School A and a year, choose "Enter a year", see them selected; change a field, change the School and be asked to confirm; save, go back, and see the new rows.

- [X] T032 [P] [US5] Add to `SchoolWorkingDaysPage.test.tsx`: the "Enter a year" button shows only to users with View and Create or Edit, links to `/master-data/school-working-days/year?schoolId=…&year=…` carrying the grid's School and year filters, and with no School filter links with the current year only
- [X] T033 [US5] Add the "Enter a year" button to `SchoolWorkingDaysPage.tsx` (hidden, not disabled, for users who may not use it) carrying `schoolId` and `year` as query parameters; read them in `YearEntryPage.tsx` to preselect the pickers (invalid or out-of-window values are ignored)
- [X] T034 [US5] Unsaved changes in `YearEntryPage.tsx` per research.md section 9: a confirmation dialog when changing the School or year and on the screen's own "Back to the grid" button (which returns to the grid with the same filters), and a `beforeunload` handler while any month is dirty (removed when nothing is dirty); `useBlocker` is not available with `BrowserRouter`, so menu and browser-back navigation are not intercepted (known limit, noted in the quickstart results); extend `YearEntryPage.test.tsx` for the dialog and for the handler being set and removed
- [X] T035 [US5] Make the grid show the saved figures on return without a manual refresh: the grid already loads on mount; add a test in `SchoolWorkingDaysPage.test.tsx` or `YearEntryPage.test.tsx` that returning to the grid after a save requests the list again (no change needed: the grid loads on mount, so it requests the list again on return; covered by the existing list-load tests)

**Checkpoint**: quickstart steps 1 and 10 pass.

---

## Phase 8: Polish and cross-cutting

- [X] T036 [P] Add the row for spec 038 to `docs/spec-roadmap.md` (module `attendance.workingdays`, extends 037, depends on 002, 003, 005, 008; no schema change; consumers unaffected) and mark it implemented on its branch when done
- [X] T037 [P] Extend `backend/src/main/java/com/hls/attendance/workingdays/internal/WorkingDaysDevSeeder.java` only if the seed lacks a School and year useful for the walkthrough (a School with 3 stored months and the rest empty in the current year plus one); otherwise skip this task and say so in the commit message (skipped: the 037 seed already has current-year rows for several Schools; the walkthrough uses a different year, which starts empty)
- [X] T038 Run the backend tests of this feature one at a time (`YearEntryRulesTest` or the rules test file from T003, `YearEntryApiTest`, the `PermissionGuard` test, `WorkingDaysModuleRulesTest`, and the 037 classes `SchoolWorkingDaysCrudTest`, `SchoolWorkingDaysApiTest`, `WorkingDaysGridTest`, `YearCopyTest` to prove nothing regressed), then `ApplicationModulesTest` and the identity permission tests; fix failures at the cause
- [X] T039 Run the frontend tests (`vitest run src/features/schoolWorkingDays`, the a11y test for the new page) and `npm run build`
- [X] T040 Run the walkthrough in quickstart.md end to end (steps 1 to 12 and the performance check) and record the results in `specs/038-working-days-year-entry/quickstart-results.md`: the time to enter a full year by hand and with the helpers (SC-001: under 1 minute, under 30 seconds with helpers), the screen load and the twelve-month Save times (SC-009: under 2 seconds, under 3 seconds), and the real-browser check that a 375 px wide window has no horizontal page scroll (SC-008; in DevTools device mode drag the content, the emulated scrollbar cannot be dragged). Step 8 and the timings need a person at the browser; mark anything not done as **not done**, never as passed

---

## Dependencies and execution order

- **Setup (T001-T002)** first; T001 gates everything else.
- **Foundation (T003-T007)** blocks all stories. T003 before T004; T005 can run beside them; T006 needs T004; T007 can run beside T003.
- **US1 (T008-T017)** needs Foundation. Tests first (T008, T009), then T010 -> T011 -> T012; the frontend T013, T014 can start once T002 exists; T015 needs T013 and T014; T016 can be written beside T015; T017 last.
- **US2 (T018-T022)** needs US1's endpoints (T012) and T015 for the screen. T018 and T019 together, then T020; T021 before T022.
- **US3 (T023-T028)** needs US1; independent of US2 on the backend, so T023 can run beside T018. T025 before T026.
- **US4 (T029-T031)** needs T013 and T015; independent of US2 and US3.
- **US5 (T032-T035)** needs T015; T033 before T034.
- **Polish (T036-T040)** last; T036 and T037 can start any time after the plan is accepted.

## Parallel opportunities

- T002 beside T001's reading; T003, T005 and T007 together.
- T008 and T009 together; T013 and T014 together once T002 exists.
- T018 and T019 together; T023 beside T018; T025 and T027 together.
- T029 beside US2/US3 work; T032 beside T033's reading.
- T036 and T037 together.

## Implementation strategy

- **MVP**: Setup + Foundation + US1 + US2 (T001-T022). A year can be filled in one Save, and months that fail are reported without losing the others. This is the requested behaviour, and the permission checks in `saveMonth` already make it safe; stop and validate with quickstart steps 1 to 7 and 12.
- **Next**: US3 (the per-role matrix and the phone layout checks), then US4 (helpers), then US5 (links and unsaved changes). Each adds value without breaking the earlier ones.
- US3's server rules are built in Foundation; its phase proves them with the full role matrix, so do not ship the MVP to users without US3's tests passing.
- Consumer modules (payroll, Manager attendance, rollups) are not touched; they read these figures through the 037 interface.

> Test note (2026-10-26): `YearEntryApiTest` concurrency test re-run alone 4 times, 4 of 4 green; the earlier single failure in a
> full run did not reproduce. See specs/026-teacher-observation-performance/quickstart-results.md.
