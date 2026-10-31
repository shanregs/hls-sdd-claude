---

description: "Task list for feature implementation"
---

# Tasks: School Monthly Working Days (School Master Data)

**Input**: Design documents from `/specs/037-school-monthly-working-days/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/school-working-days-api.md,
contracts/school-working-days-read-api.md, quickstart.md, and specs 002, 003, 005 and 008 implemented (they are on
`main`).

**Tests**: included as first-class tasks. Constitution Principle IX requires per-role tests on every endpoint, UI tests
per role and unit tests before calculation logic. The pure `WorkingDaysRules` validator is tested before it is written,
because the figure will drive pay once the consumers adopt it.

**Organization**: grouped by user story (spec.md US1-US5). Order: Foundation, US1 (record and CRUD API), US2 (grid
screen), US3 (published interface), US4 (history and Deleted rows), US5 (copy a year), Polish.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: US1-US5; absent for Setup/Foundational/Polish

## Path Conventions

Backend `backend/src/main/java/com/hls/{attendance/workingdays,attendance/api,identity}/...`, tests under
`backend/src/test/java/com/hls/...`; migrations `backend/src/main/resources/db/migration`; frontend
`frontend/src/features/schoolWorkingDays/...`. Run one Maven test class at a time (`mvn -o test -Dtest=...`); the machine
is slow.

---

## Phase 1: Setup

- [X] T001 Verify the prerequisites before writing code: in `backend/src/main/java/com/hls/` confirm `attendance.api.WorkingCalendar` (`businessToday`, `workingDaysBySchool`), `school.api.SchoolDirectory` (`school`, `schools`, `allSchools`), `school.api.ChangeRecorder` and `audit.api.EntityChanged`, `identity.permissions.PermissionGuard`, `school.api.CallerContext`, and how `attendance.internal.AttendanceScope` limits a Manager to assigned Schools; find the highest migration number on `origin/main` (V50 on 2026-10-13; use the next free number for the migration in T004 and re-check open pull requests that claim the same number). If anything is missing, STOP and report which; do not stub it. Record the actual names found in `specs/037-school-monthly-working-days/research.md` section 12
- [X] T002 Create the package skeletons `backend/src/main/java/com/hls/attendance/workingdays/{internal,web}/` with `package-info.java`, and `backend/src/test/java/com/hls/attendance/workingdays/WorkingDaysModuleRulesTest.java` (ArchUnit: `attendance` still does not depend on `leave` or `payroll`; `attendance.workingdays` uses only the `api` packages of `school`, `organization` and `audit`; nothing outside `attendance.workingdays` uses its `internal` or `web`; no module reads `SchoolWorkingDaysRow` or the `school_working_days*` tables; `attendance.workingdays` has no dependency on `leave`, `payroll` or any attendance-mark, leave-balance or payroll type, which is how FR-010 is enforced)
- [X] T003 [P] Create `frontend/src/features/schoolWorkingDays/schoolWorkingDaysApi.ts` with types and calls for every endpoint in contracts/school-working-days-api.md (list, create, edit, delete with `version`, history, history by key, deleted, copy, schools); a failed call returns the typed error codes `DUPLICATE`, `STALE`, `ROW_DELETED` with their payloads, using the helpers in `frontend/src/features/common/masterDataApi.ts`

---

## Phase 2: Foundational (blocking prerequisites)

- [X] T004 Write `backend/src/main/resources/db/migration/V52__create_school_working_days.sql` (use the number found in T001) per data-model.md. `school_working_days`: id uuid PK, school_id uuid not null, year smallint not null, month smallint not null check between 1 and 12, working_days numeric(3,1) not null check between 0 and 31 and `working_days * 2 = floor(working_days * 2)`, version bigint not null default 0, created_by uuid not null, created_at timestamptz not null, updated_by uuid not null, updated_at timestamptz not null; `UNIQUE (school_id, year, month)`; index on (year, month). `school_working_days_history` (append-only trigger refusing UPDATE and DELETE): id uuid PK, school_id uuid not null, year smallint not null, month smallint not null, action varchar(10) not null check in ('CREATE','EDIT','DELETE','COPY'), old_value numeric(3,1), new_value numeric(3,1), actor_id uuid not null, actor_roles varchar(100) not null, occurred_at timestamptz not null; indexes (school_id, year, month, occurred_at desc) and (school_id, year, action). No foreign keys to other modules' tables
- [X] T005 [P] Add permission module `SCHOOL_WORKING_DAYS(VIEW, CREATE, EDIT, DELETE)` to `identity/permissions/PermissionModule.java`; in `PermissionEligibility` add a new map (the existing ones are full) making it eligible for Admin and Director (all four) and Manager (View only), add the module to `BUSINESS_MODULES` so System is refused and Teacher is refused by the map; in `PermissionMatrixService` seed Admin all four and Director View, Create, Edit (no Delete), nothing for Manager, Teacher or System
- [X] T006 [P] Add navigation to `identity/accessmodel/NavigationCatalog.java`: MASTER DATA -> "School Working Days" `/master-data/school-working-days` (`SCHOOL_WORKING_DAYS` VIEW, sort order after Designations, roles Admin, Director, Manager); add the data scope in `AccessModelService` (org-wide for Admin and Director, Assigned for Manager)
- [X] T007 [P] Extend `PermissionEligibilityTest` and the navigation and access-model tests under `backend/src/test/java/com/hls/identity/` for the module: Admin four actions, Director three by default, Manager View only and off by default, Teacher and System refused; the menu item per role
- [X] T008 [P] `backend/src/test/java/com/hls/attendance/workingdays/WorkingDaysRulesTest.java`, written before the class and failing: the table in research.md section 2 (Feb 2028 accepts 0, 0.5, 28.5, 29 and refuses 29.5 and -0.5; Feb 2027 accepts 28 and refuses 28.5 and 29; Apr 2027 accepts 25.5 and 30 and refuses 30.5 and 25.3; Jul 2027 accepts 31 and refuses 31.5), the year window with today fixed at 2027-03-10 (accepts 2022 and 2032, refuses 2021 and 2033), month 0 and 13 refused, and that each refusal names the allowed range
- [X] T009 `attendance/workingdays/internal/WorkingDaysRules.java` (pure: figure, year, month, today; no repository, no clock) that throws `school.api.InvalidInputException` with a message naming the allowed range, e.g. "Working days for March 2027 must be 0 to 31, in half days." and "Year must be 2022 to 2032."; figure must satisfy `0 <= value <= YearMonth.of(year, month).lengthOfMonth()` in steps of 0.5 (FR-003); year window `[today.year - 5, today.year + 5]` applies to new rows only (FR-029)
- [X] T010 Entities and repositories in `attendance/workingdays/internal`: `SchoolWorkingDaysRow` (`@Version` on `version`, Hibernate only validates the schema; `workingDays` as `BigDecimal` scale 1) with `SchoolWorkingDaysRepository` (`findByYearAndSchoolIdIn`, `findByYear`, `findBySchoolIdAndYear`, `findBySchoolIdInAndYearBetween`, `findBySchoolIdAndYearAndMonth`), and `WorkingDaysHistory` (insert-only) with `WorkingDaysHistoryRepository` (no update or delete method)
- [X] T011 [P] `backend/src/test/java/com/hls/attendance/workingdays/WorkingDaysSchemaTest.java`: a second row for the same School, year and month is refused; month 13, `working_days` 30.7 (in range but not a half step) and 32 are each refused by the check constraints; a direct SQL `UPDATE` and `DELETE` on `school_working_days_history` are refused
- [X] T012 `attendance/workingdays/internal/WorkingDaysScope.java`: for a caller, returns the School ids they may see: Admin and Director all Schools (`SchoolDirectory.allSchools`), a Manager the Schools assigned to them through the same `organization` scope query `AttendanceScope` uses (FR-025); the backend applies it in every list, history and write

**Checkpoint**: migration applies; permissions and navigation seeded; the rules table passes; the schema refuses what it must.

---

## Phase 3: User Story 1 - Keep a School's Monthly Working Days (Priority: P1) 🎯 MVP

**Goal**: an Admin or Director creates, edits and (Admin) deletes the figure for a School and month through the API, with
validation, duplicate refusal, optimistic locking and audit.

**Independent Test**: via the API as Admin: add 26 for School A in March, read it back, change to 25.5, try a second
March (refused naming the existing row), delete it (month returns to "not set"); as Director delete is refused.

### Tests for User Story 1

- [X] T013 [P] [US1] `backend/src/test/java/com/hls/attendance/workingdays/SchoolWorkingDaysCrudTest.java` (service level, Testcontainers): create then read; a duplicate School and month returns the `DUPLICATE` error carrying the existing row; unknown School refused (FR-004); an inactive School accepted; figure 0 and figure equal to the days in the month accepted; out-of-range, 25.3 and a year outside the window refused with the allowed range named; edit replaces the figure and bumps the version; an edit with an old version returns `STALE` with the current row; an edit or delete of a row deleted meanwhile returns `ROW_DELETED` and creates nothing (FR-021, clarification 4); delete removes the row; refused changes write no audit event and no history row; every success writes exactly one `EntityChanged` with old and new value (FR-022); two concurrent creates for the same School and month leave exactly one row and one refusal; an edit that saves the same figure is a no-op (no write, no history row, no audit event, version unchanged, 200 with the current row); deactivating a School (through the school module) leaves its rows readable and editable (FR-005, User Story 1 scenario 7), and there is no way to delete a School that has rows
- [X] T014 [P] [US1] `backend/src/test/java/com/hls/attendance/workingdays/SchoolWorkingDaysApiTest.java` (HTTP, per role per contracts/school-working-days-api.md): Admin may create, edit, delete; Director may create and edit and gets 403 on delete (default grant); Manager, Teacher and System get 403 on every endpoint; a Manager granted View (matrix edited in the test) gets 200 on list limited to assigned Schools and still 403 on create, edit and delete; no token gets 401; a deleted-row edit gets 404 with code `ROW_DELETED`; a stale edit gets 409 with code `STALE` and the current row in the body

### Implementation for User Story 1

- [X] T015 [US1] `attendance/workingdays/internal/WorkingDaysService.java`: `create`, `edit`, `delete` in one transaction each. Validate with `WorkingDaysRules` and `SchoolDirectory.school` (unknown School refused, inactive allowed); on create catch the unique violation and answer `DUPLICATE` with the existing row; on edit and delete load by id (missing -> `ROW_DELETED`), then compare `version` (differs -> `STALE` with the current row; use `school.api.StaleVersion`); append the `WorkingDaysHistory` row (actor, roles, action, old and new value) and publish `EntityChanged` through `school.api.ChangeRecorder` (entity type `SCHOOL_WORKING_DAYS`, field `workingDays`, or `created` / `deleted` with the figure as detail) in the same transaction; an edit whose figure equals the stored one is a no-op: nothing is written, the version is unchanged and the current row is returned; nothing is written when validation or the version check fails. Define the two error types `DuplicateWorkingDaysException` and `RowDeletedException` in `internal` and map them in T017
- [X] T016 [US1] `attendance/workingdays/internal/WorkingDaysRowView.java` and `WorkingDaysViewFactory.java`: build the grid row of the contract (School name and Zone from `SchoolDirectory`, `daysInMonth`, `calendarDerived` from `WorkingCalendar.workingDaysBySchool` bucketed by month for the row's year, `updatedBy` name, `updatedAt`, `version`); the month-bucketing of the calendar dates lives in its own `attendance/workingdays/internal/CalendarCounts.java` (reused by T028); `updatedBy` names are resolved with ONE bulk lookup of the distinct user ids through the same user-name lookup the other master-data screens use, never one lookup per row; one School-directory read, one name lookup and one calendar call per request regardless of row count
- [X] T017 [US1] `attendance/workingdays/web/SchoolWorkingDaysController.java` and `WorkingDaysExceptionAdvice.java`: `POST /api/v1/school-working-days`, `PUT /{id}`, `DELETE /{id}?version=` per the contract; each calls `PermissionGuard.require(CallerContext.roles(jwt), SCHOOL_WORKING_DAYS, CREATE | EDIT | DELETE)` first and fails closed; the actor comes from `CallerContext.userId(jwt)`; the advice returns 201/200/204, 400 with the allowed range, 409 `{code: DUPLICATE, existing}`, 409 `{code: STALE, current}`, 404 `{code: ROW_DELETED}`; a Manager with View never reaches these (403)

**Checkpoint**: User Story 1 works through the API; `SchoolWorkingDaysCrudTest` and `SchoolWorkingDaysApiTest` pass.

---

## Phase 4: User Story 2 - See All Schools' Monthly Working Days in a Data Grid (Priority: P1)

**Goal**: the MASTER DATA -> School Working Days screen lists every School's rows with filters, search, sort and paging,
and supports add, inline edit, delete with confirmation, with loading, empty and error states.

**Independent Test**: with rows for several Schools across two years, filter by year and School, search by name, add a
row, edit another and Cancel, delete a third with the confirmation; check the screen per role and at phone width.

### Tests for User Story 2

- [X] T018 [P] [US2] `backend/src/test/java/com/hls/attendance/workingdays/WorkingDaysGridTest.java`: default year is the current year (via `WorkingCalendar.businessToday`); filters by School, Zone and year; search by School name (case-insensitive contains); sort by School, Zone, month, working days and last changed, both directions; paging returns `total` and the same filters on page 2; a year with no rows returns an empty list; a Manager granted View sees only assigned Schools (a row of another Manager's School is absent from items and total); the number of queries (including the user-name lookup) is the same for 5 and 500 Schools and for 1 and 50 distinct editors (query counter); 500 Schools x 12 months answers a page of 25 in under 2 seconds (SC-008)
- [X] T019 [P] [US2] `frontend/src/features/schoolWorkingDays/SchoolWorkingDaysPage.test.tsx` with a fixture per role (Admin, Director, Manager with View, Teacher, System): the Admin sees Add, Edit and Delete; the Director sees Add and Edit and no Delete; a user without View sees the not-authorized page; loading, empty (the "add the first row" hint hidden when the user cannot add) and error-with-retry states; filters persist on page 2; inline edit shows validation for 25.3 and 32 and Cancel leaves the figure; the finalised-month notice shows beside Save and in the delete confirmation (FR-028); the delete confirmation names the School and month and states the calendar fallback; `DUPLICATE` offers to edit the existing row; `STALE` shows the current figure; `ROW_DELETED` removes the row and says so; a failed save keeps the user's entry; the grid container uses its own scroll or card layout at 375 px width (the real no-horizontal-page-scroll check is in T045); months by name and dates as DD/MM/YYYY

### Implementation for User Story 2

- [X] T020 [US2] `attendance/workingdays/internal/WorkingDaysGridService.java` and `GET /api/v1/school-working-days` plus `GET /api/v1/school-working-days/schools` in the controller (`SCHOOL_WORKING_DAYS` VIEW): apply `WorkingDaysScope`, resolve School, Zone and name filters against the directory in memory, fetch the year's rows with one query, enrich through `WorkingDaysViewFactory`, sort and page in memory, return `{items, total, page, size}` (default `size` 25, max 100; sort keys `school|zone|month|workingDays|updatedAt`); the `schools` endpoint returns id, name, zone and active for the add and copy dialogs, within scope
- [X] T021 [P] [US2] `frontend/src/features/schoolWorkingDays/SchoolWorkingDaysPage.tsx` and `WorkingDaysGrid.tsx` using `@mui/x-data-grid` with server-side pagination and sorting: columns School, Zone, month (by name), working days, days in month, calendar-derived (for reference, with a visible marker when it differs from the stored figure), last changed by and when, and row actions; filters for School, Zone and year (default current year) and a School-name search, kept in the URL query so they persist across pages; loading, empty and error-with-retry states; hide, never disable, actions the user may not perform using the server-provided grants
- [X] T022 [P] [US2] `frontend/src/features/schoolWorkingDays/AddRowDialog.tsx`: choose a School (active and inactive, from `/schools`), a month of the chosen year and a figure; months that already have a row for that School are not selectable; inline validation (0 to days in month, half steps) before sending; on `DUPLICATE` show the existing row and an Edit action; the new row appears in the grid without leaving the screen
- [X] T023 [P] [US2] Inline editing in `WorkingDaysGrid.tsx` (row edit mode with Save and Cancel, validation message under the cell, Cancel restores the stored figure, a failed Save keeps the entry and says what went wrong) and `frontend/src/features/schoolWorkingDays/DeleteDialog.tsx` (names the School and month, states the month will use the calendar-derived count, shows the finalised-month notice "Months already finalised in payroll or attendance keep the figure they used.", nothing removed until confirmed); the same notice beside the inline Save (FR-028)
- [X] T024 [US2] Route `/master-data/school-working-days` in `frontend/src/App.tsx` behind the existing server-driven route guard (View), so a deep link without View shows the not-authorized page; add the page to `frontend/src/a11y/a11y.test.tsx` (axe in light and dark)

**Checkpoint**: User Stories 1 and 2 work together; the screen can maintain the data. This is the MVP.

---

## Phase 5: User Story 3 - Other Modules Read the Figure Through a Published Interface (Priority: P1)

**Goal**: one interface, `attendance.api.SchoolWorkingDays`, answers the stored figure or the calendar-derived count,
labelled, for one or many Schools and months.

**Independent Test**: ask for a School and month with a row, without a row and for a School with a weekly-off override;
ask for 50 Schools and a year and see the query count equal the one for 1 School.

### Tests for User Story 3

- [X] T025 [P] [US3] `backend/src/test/java/com/hls/attendance/workingdays/SchoolWorkingDaysReadApiTest.java` per contracts/school-working-days-read-api.md "Tests that pin the contract": stored 26 vs calendar 27 returns 26 `STORED`; no row returns the calendar count `CALENDAR_DERIVED`, including a School with a weekly-off override and a month with a non-working date; stored 0 returns 0 `STORED`; `forYear` returns twelve entries per School, January first; `forRange` across a year boundary; an unknown School id returns the default calendar's count `CALENDAR_DERIVED`; a changed or deleted row is reflected on the next call; 50 Schools x 12 months issue the same number of queries as 1 School x 12 months; the call works with no authenticated user (module-to-module, FR-013); the answer carries no person data
- [X] T026 [P] [US3] Extend `WorkingDaysModuleRulesTest` (T002): `SchoolWorkingDays` and its records live in `com.hls.attendance.api`; only that interface is visible to other modules

### Implementation for User Story 3

- [X] T027 [US3] `backend/src/main/java/com/hls/attendance/api/SchoolWorkingDays.java` exactly as in contracts/school-working-days-read-api.md (`Source`, `MonthDays`, `forMonth` for one and many Schools, `forYear`, `forRange`), with Javadoc stating the stored/derived rule and that it needs no screen permission
- [X] T028 [US3] `attendance/workingdays/internal/SchoolWorkingDaysImpl.java` (`@Service`, read-only transactions): `forRange` loads the stored rows with one query (`findBySchoolIdInAndYearBetween`, filtered to the month span), calls `WorkingCalendar.workingDaysBySchool(ids, firstOfFrom, lastOfTo)` once, buckets the dates by month, and for each School and month returns the stored figure (`STORED`) or the bucket size as a decimal (`CALENDAR_DERIVED`); `forMonth` (one and many) and `forYear` delegate to it; result lists are ordered oldest first and complete (no School and month without an answer, SC-005); reuse `CalendarCounts` (T016) rather than a second copy

**Checkpoint**: payroll, Manager attendance and the rollups can adopt the figure in their own amendments (not in this
delivery, FR-012); this interface is ready for them.

---

## Phase 6: User Story 4 - Audit Trail and History of Every Change (Priority: P2)

**Goal**: every change is audited; a row's history and the Deleted rows view are available to users with View.

**Independent Test**: create, edit and delete a row and see three audit entries and three history entries with the right
values; open the row history and the Deleted rows view from the grid.

### Tests for User Story 4

- [X] T029 [P] [US4] `backend/src/test/java/com/hls/attendance/workingdays/WorkingDaysHistoryTest.java`: a create, edit and delete leave exactly three history rows (`CREATE`, `EDIT`, `DELETE`) and three `EntityChanged` events with actor, time, entity id, field, old and new value, and the three history rows carry the actor's roles, time, School, month, old and new value; the `DELETE` entry keeps the last figure; a refused change writes none; history by id and by key return newest first and include a `DELETE` for a row that was recreated; the Deleted rows view lists each deleted month with last figure, who and when, for the chosen School and year only; a Manager granted View sees history only for assigned Schools; Director can read history and deleted rows; the audit rules of spec 003 still refuse changing an audit entry
- [X] T030 [P] [US4] Extend `SchoolWorkingDaysApiTest` (T014) with `GET /{id}/history`, `GET /history?schoolId=&year=&month=` and `GET /deleted?schoolId=&year=` per role: 403 without View; scope applied; unknown School -> 404
- [X] T031 [P] [US4] `frontend/src/features/schoolWorkingDays/HistoryDialogs.test.tsx`: the row History dialog shows entries newest first with old and new figures; the Deleted rows view for the selected School and year lists month, last figure, who and when, and shows an empty state; both are visible to a user with View and absent without it

### Implementation for User Story 4

- [X] T032 [US4] `attendance/workingdays/internal/WorkingDaysHistoryService.java`: `forRow(id)` (resolve the row's School, year and month from the existing row; when the row is gone throw `RowDeletedException`, answered as 404 `ROW_DELETED`, because history stores no row id), `forKey(schoolId, year, month)` and `deleted(schoolId, year)`; user names resolved with one bulk lookup as in T016; `WorkingDaysScope` applied
- [X] T033 [US4] Add `GET /{id}/history`, `GET /history` and `GET /deleted` to `SchoolWorkingDaysController` (`SCHOOL_WORKING_DAYS` VIEW), responses per the contract
- [X] T034 [P] [US4] `frontend/src/features/schoolWorkingDays/HistoryDialog.tsx` (row action "History") and `DeletedRowsDialog.tsx` (a "Deleted rows" button in the grid toolbar, uses the selected School and year; asks for a School when none is selected), dates as DD/MM/YYYY with time, months by name

**Checkpoint**: User Stories 1 to 4 complete.

---

## Phase 7: User Story 5 - Fill a Year Quickly (Priority: P3, in scope)

**Goal**: a user with Create copies one year's figures of a School to another year in one step, never overwriting.

**Independent Test**: choose School A, copy 2026 to 2027: months without a row are created, existing months are left and
reported, 29 into February 2027 is skipped and reported.

### Tests for User Story 5

- [X] T035 [P] [US5] `backend/src/test/java/com/hls/attendance/workingdays/YearCopyTest.java`: copies only months that have no target row; existing rows untouched and listed `EXISTS`; 29 working days into February 2027 is not copied and listed `EXCEEDS_DAYS` with `daysInMonth` 28, while 29 into February 2028 is copied; source months with no row are skipped silently; one `COPY` history row and one audit event per created row and none for skipped ones; `fromYear == toYear`, a target year outside the window, an unknown School and a source year with no rows are each refused (400); Create permission required (Director allowed, Manager and Teacher 403); the whole copy is one transaction (a failure rolls back every row)
- [X] T036 [P] [US5] `frontend/src/features/schoolWorkingDays/CopyYearDialog.test.tsx`: the dialog shows only for users with Create; chooses School, from year and to year; after the call shows the created months and the skipped months with their reasons

### Implementation for User Story 5

- [X] T037 [US5] `attendance/workingdays/internal/YearCopyService.java` and `POST /api/v1/school-working-days/copy` (`SCHOOL_WORKING_DAYS` CREATE): per research.md section 10; validate the target year with `WorkingDaysRules`; for each target month apply the figure rules of FR-003; create through `WorkingDaysService` internals so the history action is `COPY` and each row publishes its own `EntityChanged`; return `{created: [{month, workingDays}], skipped: [{month, workingDays, reason, daysInMonth?}]}`
- [X] T038 [P] [US5] `frontend/src/features/schoolWorkingDays/CopyYearDialog.tsx` and a "Copy year" toolbar action in `WorkingDaysGrid.tsx` (hidden without Create); after success refresh the grid and show the result lists

**Checkpoint**: all five stories complete.

---

## Phase 8: Polish and cross-cutting

- [X] T039 [P] `attendance/workingdays/internal/WorkingDaysDevSeeder.java` (dev profile only, like `AttendanceDevSeeder`): a few rows for the current year for several seeded Schools, including one that differs from the calendar-derived count and one of 0; add it to `backend/src/test/java/com/hls/DevSeedTest.java` expectations if that test lists seeders
- [X] T040 [P] Amend the constitution `.specify/memory/constitution.md` as PATCH 2.5.4: Sync Impact Report entry, a School Working Days row in the Default role access matrix (Admin View/Create/Edit/Delete; Director View/Create/Edit; Manager none by default, View-only if granted and Assigned; Teacher and System none), and the MASTER DATA item; bump the version and Last Amended date
- [X] T041 [P] Add the row for spec 037 to `docs/spec-roadmap.md` (module `attendance.workingdays`, depends on 002, 003, 005, 008; consumers 008, 013a/013b, 032 adopt later) and list the three consumer amendments as follow-ups there; no code in those modules changes in this delivery (FR-012)
- [X] T043 Run the full backend test classes of this feature one at a time (`WorkingDaysRulesTest`, `WorkingDaysSchemaTest`, `SchoolWorkingDaysCrudTest`, `SchoolWorkingDaysApiTest`, `WorkingDaysGridTest`, `SchoolWorkingDaysReadApiTest`, `WorkingDaysHistoryTest`, `YearCopyTest`, `WorkingDaysModuleRulesTest`), then `ApplicationModulesTest` and the identity tests touched in T007; fix failures at the cause
- [X] T044 Run the frontend tests of the feature (`vitest run src/features/schoolWorkingDays`), the a11y test for the page and `npm run build`
- [X] T045 Run the walkthrough in quickstart.md end to end (steps 1 to 11, the read interface check and the performance check) and record the results in `specs/037-school-monthly-working-days/quickstart-results.md`, including the time taken to add one figure (SC-001: under 30 seconds), to enter a year by hand and by copy (SC-002: under 3 minutes, under 1 minute), and the check that a 375 px wide window has no horizontal page scroll

---

## Dependencies and execution order

- **Setup (T001-T003)** first; T001 gates everything else.
- **Foundation (T004-T012)** blocks all stories. T008 (rules test) before T009 (rules); T004 before T010 and T011; T005-T007 can run beside them.
- **US1 (T013-T017)** needs Foundation. Tests first (T013, T014), then T015 -> T016 -> T017.
- **US2 (T018-T024)** needs US1 (it shares the controller and the view factory). Backend T020 before the frontend wiring; T021-T023 can run in parallel; T024 last.
- **US3 (T025-T028)** needs Foundation and T016's `CalendarCounts`; independent of US2, so it can run in parallel with US2.
- **US4 (T029-T034)** needs US1 (history is written there); T034 needs T021.
- **US5 (T035-T038)** needs US1 (create path) and T021 for the toolbar.
- **Polish (T039-T045)** last; T040 and T041 can start any time after the plan is accepted.

## Parallel opportunities

- T005, T006, T007 and T008 together after T004 is written.
- T013 and T014 together, then T021, T022 and T023 together once T020 exists.
- US3 (T025-T028) beside US2.
- T029, T030, T031 together; T035 and T036 together.
- T039, T040 and T041 together.

## Implementation strategy

- **MVP**: Setup + Foundation + US1 + US2 (T001-T024): the master record and the grid screen with full CRUD, audited. Stop and validate with the quickstart steps 1 to 6 and 9 to 11.
- **Next**: US3 (the published interface), so the consumer amendments can start; then US4 (history views); then US5 (copy). Each adds value without breaking the earlier ones.
- Consumer adoption (payroll, Manager attendance, rollups) is separate work tracked in the roadmap, not part of these tasks.
