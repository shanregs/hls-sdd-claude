---

description: "Task list for feature implementation"
---

# Tasks: Attendance

**Input**: Design documents from `/specs/008-attendance/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/attendance-api.md,
quickstart.md, and **specs 001-005 implemented** (this feature adds permission modules and
navigation to 002's matrix/catalog, publishes into 003's Change History through
`school.api.ChangeRecorder`, and reads Teachers, placements and Schools through spec 005's public
APIs).

**Tests**: included as first-class tasks. Constitution Principle IX requires per-role authorization
tests on every endpoint and per-scope-boundary tests (Manager A vs Manager B) on every grid,
detail and export; the rollup rules, lock invariants and supervisor-protection rule are only
trustworthy with tests.

**Organization**: grouped by user story in priority order (spec.md US1-US8). Foundational work
(permissions, navigation, audit visibility, `TeacherDirectory`, migration, business calendar) comes
first. Migration V14 is written once in Foundational because every story reads its tables.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: US1-US8; absent for Setup/Foundational/Polish

## Path Conventions

Web application per plan.md. `BE` = `backend/src/main/java/com/hls`, `BT` =
`backend/src/test/java/com/hls`, `FE` = `frontend/src`, `MIG` =
`backend/src/main/resources/db/migration`. New backend module `BE/attendance/{api,internal,web}`;
new frontend folder `FE/features/attendance/`.

---

## Phase 1: Setup

- [X] T001 Create the `attendance` module skeleton: `BE/attendance/api/package-info.java` (Spring Modulith `@NamedInterface("api")`, mirroring `BE/teacher/api/package-info.java`), plus empty `internal/` and `web/` packages with their `package-info.java`
- [X] T002 [P] Create the frontend folder `FE/features/attendance/` with `monthUtils.ts` (month string `YYYY-MM` helpers: previous/next month, days in month, weekday of a date, DD/MM/YYYY formatting, `isFutureDate`) and `monthUtils.test.ts` covering 28/29/30/31-day months and leap years
- [X] T003 [P] Add `hls.business-timezone: Asia/Kolkata` to `backend/src/main/resources/application.yml` (and test profile) as the configurable business timezone (research.md section 9)

---

## Phase 2: Foundational (blocks all user stories)

**Purpose**: permissions, navigation, audit visibility, the `teacher.api` read contract, the single
migration, and the shared domain primitives.

- [X] T004 Add `ATTENDANCE`, `TEACHER_ATTENDANCE`, `MY_ATTENDANCE`, `ATTENDANCE_SETUP` to `BE/identity/permissions/PermissionModule.java`
- [X] T005 Seed default grants in `BE/identity/permissions/PermissionMatrixService.java` exactly per spec.md Role & Permission Impact: `ATTENDANCE` Admin (VIEW, CREATE, EDIT, DELETE, PROCESS, EXPORT) and Director (VIEW, CREATE, EDIT, PROCESS, EXPORT); `TEACHER_ATTENDANCE` Manager (VIEW, CREATE, EDIT); `MY_ATTENDANCE` Teacher (VIEW, CREATE, EDIT); `ATTENDANCE_SETUP` Admin and Director (VIEW, EDIT); nothing for System. Must be idempotent for existing databases (add missing rows only; never overwrite edited grants), by extending the same `PermissionMatrixService` seeding method spec 005 extended (adding rows only where missing)
- [X] T006 Add navigation to `BE/identity/accessmodel/NavigationCatalog.java`: OPERATIONS -> "Attendance" (`/operations/attendance`, module `ATTENDANCE`), "Attendance Setup" (`/operations/attendance-setup`, `ATTENDANCE_SETUP`), "Teacher Attendance" (`/operations/teacher-attendance`, `TEACHER_ATTENDANCE`); new section MY ATTENDANCE -> "My Attendance" (`/my-attendance`), "Attendance History" (`/my-attendance/history`) both `MY_ATTENDANCE`; and the data scopes in `AccessModelService.java`: `ATTENDANCE` org-wide, `TEACHER_ATTENDANCE` assigned, `MY_ATTENDANCE` own
- [X] T007 [P] Extend `BE/audit/support/AuditVisibility.java`: `ATTENDANCE_MARK`, `ATTENDANCE_MONTH`, `ATTENDANCE_EXPORT` require `ATTENDANCE.VIEW`; `ATTENDANCE_CODE`, `ATTENDANCE_CALENDAR` require `ATTENDANCE_SETUP.VIEW` (research.md section 14), so System sees none
- [X] T008 [P] Update `BT/identity` tests for the matrix and access model: the new modules appear with the seeded grants per role, the navigation items appear only for the right roles, and System receives no attendance navigation or grants (extend the existing access-model and matrix test classes rather than creating parallel ones); add two tests: seeding twice changes nothing, and seeding over a matrix an Admin has edited keeps the edits
- [X] T009 Add `BE/teacher/api/TeacherDirectory.java` (read-only): `teacherInfo(Collection<UUID> ids)`, `teacherOfUser(UUID userId)`, `placementsOverlapping(Collection<UUID> teacherIds, LocalDate from, LocalDate to)` (school id and date range per placement, ACTIVE placements only), `teachersPlacedDuring(LocalDate from, LocalDate to)`; with view records in the same `api` package, and implement in `BE/teacher/internal/TeacherDirectoryImpl.java` using the existing `TeacherRepository` and `TeacherPlacementRepository` (bulk queries only, no per-Teacher loops)
- [X] T010 [P] Test `TeacherDirectory` in `BT/teacher/TeacherDirectoryTest.java` (extends `MasterDataTestBase`): placements overlapping a range, exit day handled as spec 005 does (`endsOn = exitDate - 1`), `teachersPlacedDuring` includes a Teacher placed for only part of the month, and an unplaced Teacher is excluded
- [X] T011 Create `MIG/V14__create_attendance_tables.sql` exactly per data-model.md: `attendance_status_code` (short_code VARCHAR(8), unique index on lower(short_code), name VARCHAR(60), category VARCHAR(12) CHECK in WORKED/LEAVE/TRAINING/NON_WORKING, weight NUMERIC(4,2) CHECK 0..1, active, system, sort_order, version), `attendance_mark` (unique (teacher_id, mark_date), day_value NUMERIC(3,2) CHECK IN (0.50, 1.00), note VARCHAR(500), set_by_kind VARCHAR(10), indexes on (mark_date) and (school_id, mark_date)), `attendance_mark_history` (action CREATED/CORRECTED/CLEARED, index (teacher_id, mark_date, set_at)), `attendance_calendar_setting` (school_id nullable, unique where not null plus single-null-row unique partial index, weekly_off_days VARCHAR(40), version), `attendance_non_working_date` (on_date unique, description VARCHAR(200)), `attendance_teacher_month` (year_month CHAR(7) CHECK `^[0-9]{4}-[0-9]{2}$`, unique (teacher_id, year_month), state LOCKED/OPEN, frozen NUMERIC(6,2) rollup columns, weighted_total NUMERIC(7,2)), `attendance_teacher_month_event` (event LOCKED/REOPENED/RELOCKED, reason VARCHAR(500)). Seed the four system status codes (`P` Present WORKED 1.00, `L` Leave LEAVE 0.00, `T` Training day TRAINING 1.00, `N` Non-working NON_WORKING 0.00; all `system = true`) and the default calendar row with `SUN`. No foreign key leaves the module. Use `@JdbcTypeCode(SqlTypes.CHAR)` on the `year_month` entity field (the earlier CHAR(n) validation lesson)
- [X] T012 [P] Add `BE/attendance/internal/BusinessCalendar.java`: wraps the application `Clock` with `ZoneId` from `hls.business-timezone` (default Asia/Kolkata); `today()`, `monthHasEnded(YearMonth)`, `windowStart()` (today - 3); it takes the `Clock` bean, which tests override with a fixed clock in `AttendanceTestBase`; and `BT/attendance/BusinessCalendarTest.java` with a fixed `Clock` at 2026-10-03T19:00Z (already 04/10 in IST) proving the IST date is used rather than the UTC date
- [X] T013 [P] Add the JPA entities and repositories in `BE/attendance/internal/`: `StatusCode`, `AttendanceMark`, `MarkHistoryEntry`, `CalendarSetting`, `NonWorkingDate`, `TeacherMonth`, `TeacherMonthEvent` (enums `StatusCategory`, `MarkAction`, `SetByKind`, `MonthState`, `MonthEventType`; `@Version` on the mutable ones; history and event entities have no setters/update/delete paths) and their Spring Data repositories
- [X] T014 Add the shared recorder wiring `BE/attendance/internal/AttendanceAudit.java`: thin helper over `school.api.ChangeRecorder` publishing `EntityChanged` for entity types `ATTENDANCE_MARK`, `ATTENDANCE_MONTH`, `ATTENDANCE_CODE`, `ATTENDANCE_CALENDAR`, `ATTENDANCE_EXPORT` with actor, previous and new values
- [X] T015 Extend the architecture tests: confirm `ApplicationModulesTest` still verifies (`attendance` depends only on `teacher.api`, `organization.api`, `school.api`, `identity` public APIs) and add ArchUnit rules in the existing rules class under `BT/` that `attendance.internal` and `attendance.web` are not referenced from outside the module and that the other modules do not depend on `attendance`
- [X] T016 [P] Add `BT/support/AttendanceTestBase.java` extending `MasterDataTestBase`: helpers to create two Managers with disjoint Schools, Teachers placed in each (with a fixed fake `Clock` and `BusinessCalendar` date), unplaced and exited Teachers, and to obtain sessions for Asha (Admin), a Director, Manager A/B, a Teacher, and System

**Checkpoint**: modules, grants, navigation, migration and primitives in place; the app starts and
`ApplicationModulesTest` is green.

---

## Phase 3: User Story 5 - Status codes and the non-working calendar (P2 by spec, built first because every rollup reads it)

**Goal**: Admin/Director maintain status codes, default weekly off days, per-School overrides and
non-working dates.

**Independent Test**: add a holiday and a custom code through the API; Manager/Teacher attempts are
refused.

- [X] T017 [US5] Add `BT/attendance/SetupControllerTest.java` (written first): per-role authorization on every setup endpoint (Admin and Director succeed; Manager, Teacher, System get 403; no session 401); duplicate short code (case-insensitive) -> 409; a system code cannot be deactivated; a used code cannot be deleted/only deactivated; non-working date duplicate -> 409; school override create/replace/delete; stale `version` -> 409; every change appears in Change History visible to Admin/Director and not to System, queryable immediately after the call (SC-005)
- [X] T018 [US5] Implement `BE/attendance/internal/StatusCodeService.java`: list (`activeOnly` default true), create (`shortCode`, `name`, `category`, `weight`), update (`name`, `weight`, `active`, `version`); validate short code <= 8 chars, name <= 60 chars, weight within 0..1, case-insensitive uniqueness; reject deactivating a `system` code; publish `ATTENDANCE_CODE` changes
- [X] T019 [US5] Implement `BE/attendance/internal/CalendarService.java`: read the whole calendar (default weekly off, school overrides with names via `school.api.SchoolDirectory`, non-working dates); update the default (`weeklyOff`, `version`); upsert/delete a School override (School must exist); add/delete a non-working date (`description` <= 200 chars); an override replaces, never merges, the default; publish `ATTENDANCE_CALENDAR` changes
- [X] T020 [US5] Implement `BE/attendance/web/SetupController.java` per contracts/attendance-api.md "Status codes and setup": the listed GET/POST/PUT/DELETE routes with `PermissionGuard` (`ATTENDANCE_SETUP.VIEW/EDIT`; the status-code list also accepts `ATTENDANCE.VIEW`, `TEACHER_ATTENDANCE.VIEW`, `MY_ATTENDANCE.VIEW`); failures return `{"reason": ...}`
- [X] T021 [P] [US5] Add `FE/features/attendance/attendanceApi.ts` setup functions (`listStatusCodes`, `createStatusCode`, `updateStatusCode`, `getCalendar`, `saveDefaultWeeklyOff`, `saveSchoolOverride`, `removeSchoolOverride`, `addNonWorkingDate`, `removeNonWorkingDate`) with the shared `authFetch` result shape used in `teachersApi.ts`
- [X] T022 [US5] Build `FE/features/attendance/AttendanceSetupPage.tsx` with `StatusCodesPanel.tsx` (table, add/edit dialog with inline validation, active toggle, system codes marked) and `CalendarPanel.tsx` (default weekly off day checkboxes, per-School overrides with School picker and remove, non-working dates list with add/remove, DD/MM/YYYY dates); loading, empty and error states; Edit controls only when `useGrantedActions` includes EDIT
- [X] T023 [US5] Add `FE/features/attendance/AttendanceSetupPage.test.tsx` (role fixtures: Admin sees edit controls, a view-only grant sees none) and register the route `/operations/attendance-setup` guarded by `ATTENDANCE_SETUP` in `FE/App.tsx`; add the page to the axe suite in `FE/a11y/a11y.test.tsx` in both themes

**Checkpoint**: setup works end to end (quickstart Scenario 5, steps 1-3).

---

## Phase 4: User Story 3 - Monthly rollup (P1)

**Goal**: a pure, table-tested rollup computed from placements, calendar, codes and marks.

**Independent Test**: the calculator's table suite matches hand-computed values (SC-003).

- [X] T024 [US3] Write `BT/attendance/RollupCalculatorTest.java` first, table-driven per research.md section 4: whole/half days, Leave, Training, explicit mark on a Sunday/holiday (counts as worked and becomes a working day), explicit NON_WORKING mark removes a working day, org-wide non-working date, School override of weekly off days, mid-month placement change between a Mon-Sat and a Mon-Fri School (per-date School), dates outside any placement not counted, exit mid-month, future days never "unmarked", `unmarked` counts only working days up to today, fractional weighted total (e.g. 10 whole + 2 half + 1 training = 12), 28/29/30/31-day months, empty month, Teacher exits mid-month (the placement ends the day before the exit date, so later days are not in play and not unmarked)
- [X] T025 [US3] Implement `BE/attendance/internal/RollupCalculator.java` as a pure function `compute(month, placementsByDate, marksByDate, codes, settings, nonWorkingDates, today)` returning a `Rollup` record `{workingDays, daysWorked, daysLeave, trainingAvailable, trainingAttended, unmarked, weightedTotal}` with `BigDecimal` arithmetic (scale 2) and no Spring or database dependency; exit is represented by the placement end date (exit - 1), so there is no separate exit input
- [X] T026 [P] [US3] Add the public view records in `BE/attendance/api/`: `RollupView` (figures plus `locked` and `frozen` flags) and `MarkView` (date, code short code and name, dayValue, school id and name, setByKind, setByName, setAt, note)

**Checkpoint**: calculator green; no endpoint yet.

---

## Phase 5: User Story 1 - A Teacher marks their own attendance (P1) MVP

**Goal**: self-marking in the 3-day window with supervisor protection, history kept, month view.

**Independent Test**: quickstart Scenario 1.

- [X] T027 [US1] Write `BT/attendance/MyAttendanceControllerTest.java` first: Teacher marks today (201/200) and a half day yesterday; correction of own mark keeps the earlier value in history; refusal 409 with a reason for: future date, date older than 3 days, date with no placement, date after exit, locked month, inactive status code, a day already set by a supervisor; invalid day value (not 0.5/1) -> 400; note > 500 chars -> 400; user without a linked Teacher record -> 404 "Your profile has not been set up yet."; Manager, Admin, System calling the `/me` routes -> 403; per-role matrix for every `/me` endpoint; concurrency: stale `version` -> 409 with "the cell changed" message
- [X] T028 [US1] Implement `BE/attendance/internal/TeacherMonthLock.java` helper: acquire `pg_advisory_xact_lock(hashtextextended(teacherId || ':' || yearMonth, 0))` via `JdbcTemplate`/`EntityManager` native query and expose `isLocked(teacherId, yearMonth)` reading `attendance_teacher_month` (state LOCKED); used by every write path (research.md section 7)
- [X] T029 [US1] Implement `BE/attendance/internal/MarkService.java`: `setMark(actor, teacherId, date, statusCodeShort, dayValue, note, version, kind)` and `clearMark(...)`; resolve the placement School for the date via `TeacherDirectory.placementsOverlapping`; take the advisory lock; enforce: placement exists, not after exit, not after `BusinessCalendar.today()`, unlocked month, active status code, day value 0.5/1.00, and for `SELF` kind: date within [today - 3, today] and current mark (if any) has `set_by_kind = SELF`; write the mark (create or correct), append a history row (`CREATED`/`CORRECTED`/`CLEARED`), publish `ATTENDANCE_MARK` with previous/new values (tests assert the Change History entry is queryable right after the write); `@Version` conflict -> a dedicated exception mapped to 409; refusal reasons are the exact user-facing strings from spec.md acceptance scenarios
- [X] T030 [US1] Implement `BE/attendance/internal/TeacherMonthViewService.java`: builds the `TeacherMonthView` (contracts/attendance-api.md) for one Teacher and month: one `days` entry per calendar day with `state` (`MARKED`, `UNMARKED`, `NOT_PLACED`, `WEEKLY_OFF`, `NON_WORKING`, `FUTURE`), mark fields, `editableBy` (`SELF` inside the window and unlocked and not supervisor-set, `SUPERVISOR`, or `NONE`), the live rollup via `RollupCalculator`, loading placements, marks, codes and calendar settings in bulk for the month
- [X] T031 [US1] Implement `BE/attendance/web/MyAttendanceController.java`: `GET /api/v1/attendance/me?month=` (`MY_ATTENDANCE.VIEW`) resolving the caller's Teacher via `TeacherDirectory.teacherOfUser`; `PUT /api/v1/attendance/me/marks/{date}` (`MY_ATTENDANCE.CREATE` for a new mark, `.EDIT` for a correction) with body `{statusCode, dayValue, note, version?}`; own record only, never an id from the client
- [X] T032 [P] [US1] Extend `FE/features/attendance/attendanceApi.ts` with `getMyMonth(month)` and `saveMyMark(date, body)` and the `TeacherMonthView`/`DayView`/`Rollup` TypeScript types
- [X] T033 [P] [US1] Build `FE/features/attendance/MonthCalendar.tsx`: a keyboard-navigable month calendar (weeks Mon-Sun, DD/MM/YYYY labels) where each day shows the code's short letter, half-day marker, "set by <name>" text for supervisor-set days, and non-colour state text/aria-labels for weekly off, non-working, not placed, future, locked, unmarked
- [X] T034 [P] [US1] Build `FE/features/attendance/MarkDialog.tsx` (react-hook-form): status code select from `listStatusCodes`, whole/half toggle, note (max 500 with counter), inline validation, a hint "For a split day, record the larger share and put the other in the note; for equal halves choose either", shows the server refusal reason; and `FE/features/attendance/RollupSummary.tsx` showing the seven rollup figures
- [X] T035 [US1] Build `FE/features/attendance/MyAttendancePage.tsx`: current month, month picker limited to the current month, click an editable day to open `MarkDialog`, "Mark today" quick action, the "profile not set up" message on 404, loading/empty/error states
- [X] T036 [US1] Add `FE/features/attendance/MyAttendancePage.test.tsx` (Teacher fixture marks today; supervisor-set day is not editable and shows who set it; 404 shows the setup message) and register route `/my-attendance` guarded by `MY_ATTENDANCE`; add to the axe suite in both themes

**Checkpoint**: Teacher marking works (quickstart Scenario 1) and is demoable as the MVP.

---

## Phase 6: User Story 2 - A Manager marks and corrects for assigned Teachers (P1)

**Goal**: scoped Manager grid and supervisor marking.

**Independent Test**: quickstart Scenario 2; two-Manager scope tests.

- [X] T037 [US2] Write `BT/attendance/TeacherAttendanceControllerTest.java` first: Manager A sees only their placed Teachers in `teacher-grid` and an unplaced Teacher is absent; day columns equal the month's real length; Manager A marking Tara-equivalent sets `SUPERVISOR`, after which the Teacher's self-mark is refused 409; Manager A can mark any unlocked date inside a placement (older than 3 days allowed); Manager A reading/marking/clearing/history for Manager B's Teacher -> 404 identical to a nonexistent id; clearing writes a `CLEARED` history row and leaves the date unmarked; locked month -> 409; per-role matrix (Teacher and System 403; Admin/Director use the other grid); Admin can clear a mark but Director gets 403 on clear while still able to mark over; history endpoint lists every value newest first
- [X] T038 [US2] Implement `BE/attendance/internal/AttendanceScope.java`: resolves the allowed Teacher id set for the caller using only `organization.api.ScopeQueries` and `teacher.api.TeacherScopeQueries` (Manager: assigned; Admin/Director: all), intersected with `TeacherDirectory.teachersPlacedDuring(month)`; and `requireTeacherInScope(caller, teacherId)` that throws the same not-found exception as a missing Teacher
- [X] T039 [US2] Implement `BE/attendance/internal/AttendanceGridService.java` per research.md section 10: resolve allowed Teachers, apply filters (name query; Zone/School/Manager/status for Admin/Director via the placement data and `organization.api.ManagerQueries`), page (size capped at 100), bulk-load marks, placements and settings for the page only, compute per-Teacher rollups with `RollupCalculator`, return `GridResponse` rows with `cells[{date, code, dayValue, setByKind, state}]`, `locked` and `rollup`
- [X] T040 [US2] Implement `BE/attendance/web/SupervisorMarkController.java`: `GET /api/v1/attendance/teachers/{teacherId}?month=`, `PUT .../marks/{date}`, `DELETE .../marks/{date}`, `GET .../marks/{date}/history` and `GET /api/v1/attendance/teacher-grid` with permission `TEACHER_ATTENDANCE.*` or `ATTENDANCE.*` (either module grants access; the grid routes are per module as in the contract) and scope enforced by `AttendanceScope`. Check order on every supervisor route: permission first (403), then scope through `AttendanceScope` (404), then business rules (409). Clearing (`DELETE .../marks/{date}`) requires `TEACHER_ATTENDANCE.EDIT` for a Manager and `ATTENDANCE.DELETE` for Admin; Director has no clear and corrects by marking over the day
- [X] T041 [P] [US2] Extend `attendanceApi.ts` with `getTeacherMonth`, `setTeacherMark`, `clearTeacherMark`, `getMarkHistory`, `getTeacherGrid` and the `GridResponse` types
- [X] T042 [US2] Build `FE/features/attendance/AttendanceGrid.tsx`: custom MUI table with a sticky Teacher column, one column per actual day (28-31), each cell showing the code's short letter with a half-day marker and an accessible name (state words, not colour alone) for locked, not placed, weekly off, non-working and unmarked variants; keyboard navigation between cells (arrow keys) and Enter to open `MarkDialog`; rollup totals column; pagination
- [X] T043 [US2] Build `FE/features/attendance/TeacherMonthPanel.tsx` (side panel: rollup via `RollupSummary`, per-day mark history, lock status) and `FE/features/attendance/TeacherAttendancePage.tsx` (Manager: month picker, name search, grid, mark/clear via `MarkDialog`, Clear action only when the grant allows)
- [X] T044 [US2] Add `FE/features/attendance/TeacherAttendancePage.test.tsx` (Manager fixture sees grid rows and can mark; locked cell not editable; empty state), register route `/operations/teacher-attendance` guarded by `TEACHER_ATTENDANCE`, add `AttendanceGrid` page to the axe suite in both themes

**Checkpoint**: Manager marking and supervisor protection work (quickstart Scenario 2).

---

## Phase 7: User Story 4 - Admin and Director oversight grid (P1)

**Goal**: org-wide grid with filters, plus rollups and corrections.

**Independent Test**: quickstart Scenario 4.

- [X] T045 [US4] Write `BT/attendance/AttendanceGridControllerTest.java` first: Admin and Director see all placed Teachers; filters by Zone, School, Manager, Teacher status and name combine with AND and totals follow the filter; a mark made by Admin is attributed to Admin and appears in the Teacher's `/me` month; Manager, Teacher and System -> 403 on `/attendance/grid`; Admin clears a mark (`ATTENDANCE.DELETE`) and Director's clear is 403; page size capped at 100; a month with no placed Teachers returns an empty page, not an error; performance (SC-007): a 500-Teacher fixture returns the first page in under 3 seconds without per-Teacher queries (assert statement count and elapsed time)
- [X] T046 [US4] Implement `BE/attendance/web/AttendanceGridController.java`: `GET /api/v1/attendance/grid?month=&query=&zoneId=&schoolId=&managerId=&status=&page=&size=` (`ATTENDANCE.VIEW`) delegating to `AttendanceGridService` with the extra filters (reuse T039; add only filter plumbing)
- [X] T047 [P] [US4] Extend `attendanceApi.ts` with `getGrid(params)`; build `FE/features/attendance/GridFilters.tsx` (Zone, School, Manager, status selects loaded from the existing zones/schools/managers APIs, name search with debounce)
- [X] T048 [US4] Build `FE/features/attendance/AttendanceGridPage.tsx` (Admin/Director): month picker, `GridFilters`, shared `AttendanceGrid`, `TeacherMonthPanel` with rollup and mark history, mark/correct via `MarkDialog`; hides Edit controls without `ATTENDANCE.EDIT`
- [X] T049 [US4] Add `FE/features/attendance/AttendanceGridPage.test.tsx` (filters change the request, correction refreshes the cell, Teacher/System fixture sees "not authorized"), register route `/operations/attendance` guarded by `ATTENDANCE`, add to the axe suite in both themes

**Checkpoint**: org-wide oversight works (quickstart Scenarios 3-4).

---

## Phase 8: User Story 6 - Lock a month and reopen it (P2)

**Goal**: month lock with the complete-and-ended rule, per-Teacher reopen/relock, frozen rollups.

**Independent Test**: quickstart Scenario 6.

- [X] T050 [US6] Write `BT/attendance/MonthLockTest.java` first: locking the current/future month -> 409 "month has not ended"; locking a past month with an unmarked working day of any placed Teacher -> 409 listing `{teacherId, name, dates}` and nothing locked; after filling the gaps lock succeeds, writes frozen rollup rows and `LOCKED` events; every mark add/change/clear in a locked Teacher-month is refused for Teacher, Manager, Admin and Director (SC-006: 100%); reopen requires a non-blank reason, flips only that Teacher to `OPEN`, writes `REOPENED`; edit then relock with a gap -> 409, relock without gap re-freezes the rollup and writes `RELOCKED`; events listed in order; Manager/Teacher/System lock/reopen/relock -> 403; month with no placed Teachers locks trivially; Teacher exited mid-month only needs days up to exit; concurrency test: a mark write racing a lock never leaves a locked month with an unmarked or changed-after-freeze day (advisory lock); changing weekly off days after locking does not change a locked rollup
- [X] T051 [US6] Implement `BE/attendance/internal/MonthLockService.java`: `lockMonth(actor, yearMonth)` (month must have ended per `BusinessCalendar`; find placed Teachers; compute unmarked via `RollupCalculator`; if any, throw a 409 exception carrying the `unmarked` list; else in one transaction take each Teacher's advisory lock, insert `LOCKED` rows with frozen rollup values and events), `reopen(actor, teacherId, yearMonth, reason)` (a Teacher-month with no row and one in state `OPEN` are both editable; only `LOCKED` blocks writes), `relock(actor, teacherId, yearMonth)` (revalidate unmarked, recompute, refreeze), `events(teacherId, yearMonth)`; publish `ATTENDANCE_MONTH` changes; relock keeps `year_month` CHAR(7)
- [X] T052 [US6] Update `TeacherMonthViewService` and `AttendanceGridService` to read frozen figures (`frozen: true`) from `attendance_teacher_month` for locked months and live figures otherwise; add the `locked` flag to grid rows and day states
- [X] T053 [US6] Implement `BE/attendance/web/LockController.java` per the contract: `POST /api/v1/attendance/months/{yearMonth}/lock`, `POST /api/v1/attendance/teachers/{teacherId}/months/{yearMonth}/reopen` (`{reason}` required), `.../relock`, `GET .../events`, all `ATTENDANCE.PROCESS` (events: `ATTENDANCE.VIEW`); 409 body includes `{reason, unmarked:[...]}`
- [X] T054 [P] [US6] Extend `attendanceApi.ts` with `lockMonth`, `reopenMonth`, `relockMonth`, `getMonthEvents`
- [X] T055 [US6] Build `FE/features/attendance/LockMonthDialog.tsx` (confirm; on 409 lists unmarked Teachers and dates with a link to open each) and `ReopenDialog.tsx` (reason required, max 500), wire into `AttendanceGridPage.tsx` toolbar ("Lock month") and `TeacherMonthPanel.tsx` (Reopen/Relock and the event history), shown only with `PROCESS`
- [X] T056 [US6] Add `FE/features/attendance/LockMonthDialog.test.tsx` (409 list rendered, reason required on reopen, no controls without PROCESS) and include both dialogs in the axe suite in both themes

**Checkpoint**: locking works end to end (quickstart Scenario 6).

---

## Phase 9: User Story 7 - Teacher attendance history (P2)

**Goal**: earlier months, read-only when locked.

**Independent Test**: quickstart Scenario 7, step 1.

- [X] T057 [US7] Add tests to `BT/attendance/MyAttendanceControllerTest.java`: earlier-month `GET /me?month=` returns the same figures a Manager sees for that Teacher; a locked month returns frozen rollup and `editableBy: NONE` for every day; a Teacher can never read another Teacher's month by any route (Teacher role on `/teachers/{id}` -> 403; Manager B on Manager A's Teacher -> 404)
- [X] T058 [US7] Build `FE/features/attendance/AttendanceHistoryPage.tsx`: month picker with previous/next, read-only `MonthCalendar` plus `RollupSummary`, a "Locked" banner for frozen months, loading/empty/error states; register route `/my-attendance/history` guarded by `MY_ATTENDANCE`
- [X] T059 [US7] Add `FE/features/attendance/AttendanceHistoryPage.test.tsx` (locked month shows banner and no edit affordance) and add to the axe suite in both themes

---

## Phase 10: User Story 8 - CSV export (P3)

**Goal**: export a filtered month, audited.

**Independent Test**: quickstart Scenario 7, step 2.

- [ ] T060 [US8] Write `BT/attendance/AttendanceExportTest.java` first: Admin/Director download `text/csv` with a header row and exactly the filtered Teachers (name, status, School, Manager, rollup figures, one column per actual day with the short code and `0.5` suffix for half days); Manager, Teacher and System -> 403; the export produces exactly one `ATTENDANCE_EXPORT` Change History event with actor, month and filter, visible to Admin/Director but not System; CSV values beginning with `=`, `+`, `-`, `@` in names are neutralized against formula injection; streaming works for 500 Teachers within the SC-007 budget
- [ ] T061 [US8] Implement `BE/attendance/internal/CsvExporter.java` (streams via page-sized batches from `AttendanceGridService`, RFC 4180 escaping, formula-injection guard) and `BE/attendance/web/ExportController.java` (`GET /api/v1/attendance/export?month=&zoneId=&schoolId=&managerId=&status=&query=`, `ATTENDANCE.EXPORT`, `Content-Disposition` filename `attendance-YYYY-MM.csv`, publishes one `ATTENDANCE_EXPORT` event)
- [ ] T062 [US8] Add the "Export CSV" button to `AttendanceGridPage.tsx` (only with `EXPORT`, downloads through `authFetch` as a blob using the current filters) and extend `AttendanceGridPage.test.tsx` for it

---

## Phase 11: Read contract for later modules

- [ ] T063 Add `BE/attendance/api/AttendanceReadApi.java` (`rollupOf(teacherId, yearMonth)` returning `RollupView` with `locked` flag, frozen if locked; `marksOf(teacherId, yearMonth)` returning `List<MarkView>`; `isLocked(teacherId, yearMonth)`) and implement in `BE/attendance/internal/AttendanceReadApiImpl.java` reusing `TeacherMonthViewService`/`MonthLockService`; test in `BT/attendance/AttendanceReadApiTest.java` (open month live, locked month frozen, unknown Teacher -> empty/zero without error); ArchUnit check that no other module computes attendance itself

---

## Phase 12: Polish and cross-cutting

- [ ] T064 [P] Blocked on the user for the Postman part: add the attendance requests to the Postman collection under `postman/` ONLY if the user has resolved the pending re-export (ask first; do not touch the uncommitted `postman/` changes otherwise) and document attendance setup in `docs/running-locally.md` (demo: Tara, Manoj, Asha, the 3-day window, how to lock a past month)
- [X] T065 [P] Extend the demo seeder `BE/teacher/internal/TeacherDevSeeder.java` (or a new `BE/attendance/internal/AttendanceDevSeeder.java` behind `hls.seed.demo-data=true`) with a few past-month marks for Tara so History, grid and lock are demoable; idempotent
- [ ] T066 [P] Full cross-role authorization sweep in `BT/attendance/AttendanceAuthorizationMatrixTest.java`: one parameterized test enumerating every endpoint in contracts/attendance-api.md against Admin, Director, Manager, Teacher, System and unauthenticated, asserting the contract's permission rules (Principle IX)
- [ ] T067 Run the full backend suite (`mvn test` in `backend/`) and frontend checks (`npx prettier --check`, `npx tsc --noEmit`, `npx eslint src`, `npx vitest run`); fix findings; confirm `ApplicationModulesTest` and the ArchUnit rules are green
- [ ] T068 Walk quickstart.md Scenarios 1-7 manually against the local stack and record the outcome in the final commit message; verify keyboard-only use of the grid and calendar (SC-008) and the SC-001/SC-002 timings informally
> Reminder (not a task): open a separate change, outside this task list, to move spec 005's UTC-based "today" to `BusinessCalendar` (research.md section 9).

---

## Dependencies & Execution Order

- **Phase 1 -> Phase 2** strictly; Phase 2 blocks everything.
- **Phase 3 (US5 setup)** and **Phase 4 (US3 calculator)** can proceed in parallel after Phase 2
  (different files); both are required before Phase 5.
- **Phase 5 (US1)** needs Phases 3-4. **Phase 6 (US2)** needs Phase 5 (`MarkService`,
  `TeacherMonthViewService`). **Phase 7 (US4)** needs Phase 6 (`AttendanceGridService`,
  `AttendanceGrid.tsx`). **Phase 8 (US6)** needs Phases 5-7. **Phase 9 (US7)** needs Phase 5 and
  benefits from Phase 8 (locked display). **Phase 10 (US8)** needs Phase 7. **Phase 11** needs
  Phases 5 and 8. **Phase 12** last.
- Within a story: tests first (they fail), then services, controllers, then frontend.
- Tasks touching the same file are sequential: `attendanceApi.ts` (T021, T032, T041, T047, T054),
  `AttendanceGridPage.tsx` (T048, T055, T062), `a11y.test.tsx` and `App.tsx` routes
  (T023, T036, T044, T049, T056, T059), `MyAttendanceControllerTest.java` (T027, T057).

## Parallel Opportunities

- Phase 2: T007, T008, T010, T012, T013, T016 in parallel once T004-T006, T009 and T011 exist.
- Phase 3 frontend (T021) in parallel with backend T018-T020 after T017.
- Phase 4 (T024-T026) in parallel with Phase 3.
- Phase 5 frontend T032-T034 in parallel with backend T028-T031.
- Phase 12: T064-T066 in parallel.

## Implementation Strategy

1. **MVP**: Phases 1-5 (foundation, setup, calculator, Teacher self-marking) - a Teacher can mark
   and see their month with a correct rollup.
2. Add Phase 6 (Manager) then Phase 7 (Admin/Director) - the operational core.
3. Add Phase 8 (lock/reopen), Phase 9 (history), Phase 10 (export), Phase 11 (read contract).
4. Polish and walk the quickstart; commit per phase.

## Story-to-task map

| Story | Priority | Tasks |
| ----- | -------- | ----- |
| US1 Teacher marks | P1 | T027-T036 |
| US2 Manager | P1 | T037-T044 |
| US3 Rollup | P1 | T024-T026 (plus T030, T039, T052 use it) |
| US4 Admin/Director grid | P1 | T045-T049 |
| US5 Setup | P2 | T017-T023 |
| US6 Lock/reopen | P2 | T050-T056 |
| US7 History | P2 | T057-T059 |
| US8 Export | P3 | T060-T062 |
| Foundation / contract / polish | - | T001-T016, T063-T068 |
