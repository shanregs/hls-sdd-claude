---

description: "Task list for feature implementation"
---

# Tasks: Manager Attendance and Field Days

**Input**: Design documents from `/specs/032-manager-attendance/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/manager-attendance-api.md,
contracts/manager-attendance-read-api.md, quickstart.md, and **specs 001-005, 005a, 008, 009, 010, 013a and 023 (the
`files` module) implemented and merged**. The planned-activity interface (contract C1) is **not built**; a Field day
carries a purpose text until it is (research.md section 4).

**Tests**: included as first-class tasks. Constitution Principle IX requires per-role tests on every endpoint, UI tests
per role and unit tests before calculation logic. The pure `ManagerMonthCalculator` is tested before it is written; the
spec's rules (a missing check-out is never a deduction, a month with no information is never "no unpaid days", a locked
month never changes) are only trustworthy with tests.

**Organization**: grouped by user story (spec.md US1-US7). The order below follows the dependencies, not the numbers:
Foundation, then US1 (check-in), US2 (Field days), US5 (month, grid, corrections), US3 (leave), US7 (payroll read), US6
(lock), US4 (alert), Polish.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: US1-US7; absent for Setup/Foundational/Polish

## Path Conventions

Backend `backend/src/main/java/com/hls/{attendance/manager,leave/manager,payroll,notification,identity}/...`, tests under
`backend/src/test/java/com/hls/...`; migrations `backend/src/main/resources/db/migration`; frontend
`frontend/src/features/managerAttendance/...`. Run one Maven test class at a time (`mvn -o test -Dtest=...`); the machine
is slow.

---

## Phase 1: Setup

- [ ] T001 Verify the prerequisites before writing code: in `backend/src/main/java/com/hls/` confirm `organization.api.ManagerQueries` has `managerByUserId`, `employment` (joining and exit dates) and `allManagers`; `files.api.FileStore`; `attendance.api.WorkingCalendar`; `leave.api.LeaveTypes`; `notification.internal.RecipientResolver.activeAdminsAndDirectors`; the highest migration number (V27 at planning time, so V28 and V29). If any is missing, STOP and report which; do not stub it. Record the actual names found in `specs/032-manager-attendance/research.md` section 1
- [ ] T002 Create the package skeletons `backend/src/main/java/com/hls/attendance/manager/{internal,web}/` and `backend/src/main/java/com/hls/leave/manager/{internal,web}/` with `package-info.java`, and `backend/src/test/java/com/hls/attendance/manager/ManagerAttendanceModuleRulesTest.java` (ArchUnit: `attendance` still does not depend on `leave`; `leave.manager` uses only `attendance.api`; nothing outside `attendance.manager` uses its `internal` or `web`; no module reads the `manager_*` tables or `ManagerDay`; `attendance.manager` uses only the `api` packages of `organization`, `files` and `school`)
- [ ] T003 [P] Create `frontend/src/features/managerAttendance/managerAttendanceApi.ts` with types and calls for every endpoint in contracts/manager-attendance-api.md (times as ISO strings, fractions as numbers)

---

## Phase 2: Foundational (blocking prerequisites)

- [ ] T004 Write `backend/src/main/resources/db/migration/V28__create_manager_attendance.sql` per data-model.md. `manager_day`: id uuid PK, manager_id uuid not null, day_date date not null, day_type varchar(10) check in ('OFFICE','FIELD'), purpose varchar(500), unlinked boolean not null default false, check_in_at timestamptz, check_in_lat and check_in_lng numeric(9,6), check_in_accuracy_m numeric(8,2), check_out_at timestamptz, check_out_lat and check_out_lng numeric(9,6), check_out_accuracy_m numeric(8,2), photo_file_id uuid, late boolean not null default false, added_later boolean not null default false, corrected boolean not null default false, leave_request_id uuid, leave_fraction numeric(2,1) check in (0.5, 1.0), leave_unpaid boolean not null default false, version bigint not null; unique (manager_id, day_date); index (day_date); latitude between -90 and 90 and longitude between -180 and 180 checks. `manager_day_history` (append-only trigger refusing UPDATE and DELETE; action in 'CHECK_IN','CHECK_OUT','COMPLETE_CHECK_OUT','CORRECT','LEAVE_APPLIED','LEAVE_REMOVED','ADDED_LATER'; reason varchar(500); before_json and after_json text). `manager_regularization` (day_date date, proposed_time time, day_type check in ('OFFICE','FIELD'), purpose varchar(500), reason varchar(500) not null, status check in ('PENDING','APPROVED','REJECTED','CANCELLED'), decision_reason varchar(500); a partial unique index on (manager_id, day_date) where status = 'PENDING'). `manager_month` (unique manager_id + year_month varchar(7), state check in ('OPEN','LOCKED'), the six `frozen_*` numeric(4,1) columns, version) and `manager_month_event` (append-only; event in 'LOCKED','REOPENED'; reason varchar(500)). `manager_attendance_setting` (single row seeded with missed_cutoff 10:30 and late_after 09:30; check late_after < missed_cutoff; version). `manager_missed_alert` (primary key (manager_id, alert_date))
- [ ] T005 [P] Add permission modules `MANAGER_ATTENDANCE(VIEW, CREATE, EDIT, PROCESS)`, `MANAGER_LEAVE(VIEW, APPROVE)` and `MY_MANAGER_ATTENDANCE(VIEW, CREATE, DELETE)` to `identity/permissions/PermissionModule.java`; in `PermissionEligibility` make the first two Admin and Director only and the third Manager only, and add all three to the business-module set so System and Teacher cannot be granted them; seed in `PermissionMatrixService`: Admin gets every action of the first two, Director gets View, Create, Edit on `MANAGER_ATTENDANCE` (no Process by default) and View, Approve on `MANAGER_LEAVE`, Manager gets every action of the third
- [ ] T006 [P] Add navigation to `identity/accessmodel/NavigationCatalog.java`: MY ATTENDANCE → "Check-in" `/manager-attendance/me`, "My Month" `/manager-attendance/me/month`, "My Leave" `/manager-attendance/me/leave` (Manager, `MY_MANAGER_ATTENDANCE` VIEW); OPERATIONS → "Manager Attendance" `/operations/manager-attendance` (`MANAGER_ATTENDANCE` VIEW) and "Manager Leave" `/operations/manager-leave` (`MANAGER_LEAVE` VIEW) for Admin and Director; add the data scopes in `AccessModelService` (Own for Manager, org-wide for Admin and Director)
- [ ] T007 [P] Extend `PermissionEligibilityTest` and the navigation and access-model tests under `backend/src/test/java/com/hls/identity/` for the three modules (Teacher, System and the wrong role refused by the matrix; each menu item per role)
- [ ] T008 Entities and repositories in `attendance/manager/internal`: `ManagerDay`, `ManagerDayHistory` (insert-only), `ManagerRegularization`, `ManagerMonthState`, `ManagerMonthEvent`, `ManagerAttendanceSettings`, `MissedAlert`; Hibernate only validates the schema; `ManagerDayRepository` with `findByManagerIdAndDayDate`, `findByManagerIdAndDayDateBetween` and a bulk `findByManagerIdInAndDayDateBetween`
- [ ] T009 [P] `backend/src/test/java/com/hls/attendance/manager/ManagerAttendanceSchemaTest.java`: a direct SQL `UPDATE` and `DELETE` on `manager_day_history` and `manager_month_event` are refused; a second `manager_day` for the same Manager and date is refused; latitude 91 or -91, longitude 181, `leave_fraction` 0.7 and `late_after` not before `missed_cutoff` are each refused by the check constraints
- [ ] T010 [P] `backend/src/test/java/com/hls/attendance/manager/ManagerMonthCalculatorTest.java`, written before the class and failing: at least 14 hand-computed cases from research.md section 3 on a table of records: a full month; a check-in with no check-out (worked); a day with no row (unpaid); full-day paid leave; full-day Loss-of-Pay leave; half-day leave with a check-in (half leave plus half worked); half-day Loss-of-Pay leave with a check-in; half-day leave with no check-in; a check-in on a weekly off, a holiday and outside the employment span (changes nothing); a mid-month joiner and an exit; today and later dates are `pending` and never unpaid; a month with a locked state returns the frozen figures; the same inputs give the same answer on repeat
- [ ] T011 `attendance/manager/internal/ManagerMonthCalculator.java` (pure: records, working dates, employment span, today; no clock, no repository) returning the figures and the unpaid dates with their fractions, per research.md section 3 and `ManagerMonth.Found` of contracts/manager-attendance-read-api.md
- [ ] T012 `attendance/manager/internal/ManagerContext.java`: resolves the acting Manager from the JWT user through `ManagerQueries.managerByUserId` (a user with no Manager record gets 403 on the `/me` endpoints), and the Manager's joining and exit dates through `ManagerQueries.employment`; a Manager with no joining date is refused for check-in and leave with the reason that the joining date must be recorded first (FR-024)
- [ ] T013 `attendance/manager/internal/ManagerMonthGuard.java`: `requireOpen(managerId, month)` shared by every writer (check-in, check-out, completion, correction, regularization approval, leave apply and remove), refusing with a conflict that names the locked month
- [ ] T014 [P] `attendance/manager/internal/ManagerAttendanceSettingsService.java` and `web/ManagerAttendanceSettingsController.java` for `GET` and `PUT /api/v1/manager-attendance/settings` (`MANAGER_ATTENDANCE` VIEW and EDIT), `late_after` before `missed_cutoff` enforced, stale version refused, each change audited as `MANAGER_ATTENDANCE_SETTING` with prior and new values; test `ManagerAttendanceSettingsTest.java`

**Checkpoint**: migration applies; permissions and navigation seeded; the calculator passes its table; the schema refuses what it must.

---

## Phase 3: User Story 1 - Check In and Check Out for the Day (P1) 🎯 MVP

**Goal**: a Manager checks in with a location, optionally adds a photo, checks out, and a missing check-out is flagged
and completable.

**Independent Test**: check in as Office, check out, see both times in the day; a second check-in and a check-in with no
location are refused.

### Tests for User Story 1 (write first, they must fail)

- [ ] T015 [P] [US1] `backend/src/test/java/com/hls/attendance/manager/CheckInApiTest.java`: check-in records the server time, location and day type; a second check-in is 409; no location, latitude 91 and longitude 181 are 400 with the reason; a day type other than OFFICE or FIELD is 400; a check-out with no check-in is 400 and a second check-out is 409; the check-out is after the check-in; a check-in after the late time is marked late; a check-in on a weekly off or a holiday is accepted and changes no figure; a Manager with no joining date, a day before the joining date and a day after the exit date are refused; a check-in on approved full-day leave is refused and on half-day leave is accepted; a locked month is 409
- [ ] T016 [P] [US1] `backend/src/test/java/com/hls/attendance/manager/CheckInPhotoTest.java`: a jpg is attached and opened by the Manager, an Admin and a Director only; a txt file, an oversize file and a second photo are refused; the check-in itself is valid without a photo
- [ ] T017 [P] [US1] `backend/src/test/java/com/hls/attendance/manager/MyManagerAttendanceAuthorizationTest.java`: Manager succeeds on every `/me` endpoint and only on their own data; Teacher and System get 403 on each (one assertion per endpoint); a user who is a Manager only through the role with no Manager record gets 403
- [ ] T018 [P] [US1] `backend/src/test/java/com/hls/attendance/manager/CheckInAuditTest.java`: each check-in, check-out and completion writes a `MANAGER_DAY` entry and a history row with the actor in the same transaction; a refused request writes none
- [ ] T019 [P] [US1] `backend/src/test/java/com/hls/attendance/manager/MissingCheckOutTest.java`: a day with a check-in and no check-out is flagged "no check-out" after the day ends, still counts as worked and paid, never blocks a lock; the Manager completes it with a time and a reason (`COMPLETE_CHECK_OUT` history row); an Admin or Director completes it too

### Implementation for User Story 1

- [ ] T020 [US1] `attendance/manager/internal/CheckInService.java`: `checkIn` (day type OFFICE or FIELD, a location with latitude -90 to 90 and longitude -180 to 180, an optional accuracy; the time from `BusinessCalendar`, never the device; late when after `late_after`; refuses per T015 including `ManagerContext` and `ManagerMonthGuard`), `checkOut`, `completeCheckOut` (time on the day, not future, a reason of at most 500 characters), each writing the `ManagerDay`, a history row and the audit entry in one transaction
- [ ] T021 [US1] `attendance/manager/internal/CheckInPhotoService.java`: stores through `FileStore.store(actor, "MANAGER_DAY", dayId, ...)` for the Manager's own check-in only, one photo per day, opened by the Manager, Admin and Director through the day detail
- [ ] T022 [US1] `attendance/manager/web/MyManagerAttendanceController.java`: `GET /me/today`, `POST /me/check-in`, `POST /me/check-out`, `POST /me/days/{date}/photo`, and `POST /me/days/{date}/complete-check-out` (add this last one to contracts/manager-attendance-api.md with the other `/me` endpoints), each guarded by `MY_MANAGER_ATTENDANCE` and the actor from the JWT; errors in the house format
- [ ] T023 [P] [US1] `frontend/src/features/managerAttendance/ManagerCheckInPage.tsx`: today's status, the day type choice, the browser location request with a clear message when it is blocked, check-in and check-out buttons, an optional photo, loading, empty and error states, a phone-width layout; the guarded route `/manager-attendance/me` in `frontend/src/App.tsx`
- [ ] T024 [P] [US1] Frontend tests `frontend/src/features/managerAttendance/__tests__/ManagerCheckIn.test.tsx` with a fixture per role (the Manager sees the page and the buttons; Teacher, System, Admin and Director get "not authorized" on the deep link), the location-blocked message, the refusal messages, and an axe check in light and dark themes

**Checkpoint**: US1 works end to end; quickstart section 1 passes.

---

## Phase 4: User Story 2 - Field Days Linked to Planned Activities (P1)

**Goal**: a Field day carries its purpose now and is ready to take planned-activity links later.

**Independent Test**: check in as Field with a purpose and see it on the day; Field with no purpose is refused.

### Tests for User Story 2 (write first, they must fail)

- [ ] T025 [P] [US2] `backend/src/test/java/com/hls/attendance/manager/FieldDayTest.java`: a Field check-in with no purpose is 400 with the reason and a purpose of 501 characters is 400; a Field check-in with a purpose is recorded and counted as a field day; an Office check-in ignores a purpose; `unlinked` stays false while no activity link exists; the correction path cannot turn a Field day into one with no purpose

### Implementation for User Story 2

- [ ] T026 [US2] In `CheckInService`, require a non-empty trimmed purpose of at most 500 characters for FIELD (stored as plain text) and keep `unlinked` false until the planned-activity interface exists; add a comment pointing at research.md section 4 for the link table that a later spec adds
- [ ] T027 [P] [US2] In `ManagerCheckInPage.tsx` show the purpose field only for the Field choice with inline validation and a character count; extend `ManagerCheckIn.test.tsx` for it

**Checkpoint**: US2 works; quickstart section 2 passes.

---

## Phase 5: User Story 5 - See and Correct the Month (P1)

**Goal**: a Manager sees their own month and can ask for a forgotten check-in; Admin and Director see all Managers by
month and can correct a day with a reason.

**Independent Test**: as a Manager see each day's status; as an Admin open the grid, filter to one Zone and correct a
day with a reason, with the original kept.

### Tests for User Story 5 (write first, they must fail)

- [ ] T028 [P] [US5] `backend/src/test/java/com/hls/attendance/manager/MyManagerMonthTest.java`: each day carries its status (worked, field, leave, absent, weekly off, holiday, missed, no check-out, pending), times, purpose and flags, and the month figures; the location appears for the Manager's own days only
- [ ] T029 [P] [US5] `backend/src/test/java/com/hls/attendance/manager/ManagerGridTest.java`: the grid lists every Manager with a status per day and the month figures; the Zone filter keeps the Managers who hold that Zone; name search; the grid for 40 Managers is built in a fixed number of queries (a query-count assertion); no location or photo appears in the grid; a Manager gets 403
- [ ] T030 [P] [US5] `backend/src/test/java/com/hls/attendance/manager/CorrectionApiTest.java`: an Admin and a Director correct a day (type, purpose, check-in or check-out time, or clear it) with a reason; the reason is required; the original and each correction stay in the history with who and when; `corrected` is set; the Manager is notified; a correction in a locked month is 409; a Manager and a Teacher get 403; the first history row holds the original
- [ ] T031 [P] [US5] `backend/src/test/java/com/hls/attendance/manager/RegularizationApiTest.java`: a Manager asks for a forgotten check-in on any past day of an unlocked month with a reason, a proposed time and a day type; refused for a future day or time, a locked month (409), a day that already has a check-in, a second pending request for the same date and a missing reason; an Admin or Director approves (the day becomes worked and `added_later`) or rejects with a reason; a Manager cannot decide; cancelling a pending request works; approval into a locked month is 409 and changes nothing
- [ ] T032 [P] [US5] `backend/src/test/java/com/hls/attendance/manager/ManagerAttendanceAuthorizationTest.java`: Admin and Director succeed on every `MANAGER_ATTENDANCE` endpoint; Manager, Teacher and System get 403 on each (one assertion per endpoint)

### Implementation for User Story 5

- [ ] T033 [US5] `attendance/manager/internal/ManagerGridService.java`: the Manager's month (days with statuses via `ManagerMonthCalculator` and the default calendar from `WorkingCalendar`) and the grid for all Managers in one query for the month's records plus one for the Managers and their employment dates (`ManagerQueries.allManagers`, `employment`, `currentSchoolIds` for the Zone filter) — a fixed number of queries
- [ ] T034 [US5] `attendance/manager/internal/CorrectionService.java`: correct a day with a required reason (at most 500 characters), keep the original in the first history row and every change after it, set `corrected`, refuse in a locked month through `ManagerMonthGuard`, publish `ManagerDayCorrected`, audit as `MANAGER_DAY`
- [ ] T035 [US5] `attendance/manager/internal/RegularizationService.java`: request, cancel, approve and reject per data-model.md `manager_regularization` (status `PENDING`, `APPROVED`, `REJECTED`, `CANCELLED`; a reason on rejection; the decider is not the requester), publish `ManagerRegularizationRequested` and `ManagerRegularizationDecided`, audit as `MANAGER_REGULARIZATION`
- [ ] T036 [US5] Add the events `ManagerDayCorrected`, `ManagerRegularizationRequested` and `ManagerRegularizationDecided` to `attendance/api/`, and `notification/internal/ManagerAttendanceEventListener.java` with the new `NotificationType` values `MANAGER_DAY_CORRECTED`, `MANAGER_REGULARIZATION_REQUESTED`, `MANAGER_REGULARIZATION_DECIDED` and their texts in `MessageFactory` (contracts/manager-attendance-read-api.md, "Events")
- [ ] T037 [US5] `attendance/manager/web/MyManagerAttendanceController.java` (add `GET /me/months/{yyyy-MM}`, `GET /me/regularizations`, `POST /me/regularizations`, `DELETE /me/regularizations/{id}`) and `web/ManagerAttendanceController.java` (`GET /grid`, `GET /managers/{managerId}/months/{yyyy-MM}` with locations and history, `PUT /managers/{managerId}/days/{date}`, `POST /managers/{managerId}/days/{date}/complete-check-out`, `GET /regularizations`, `POST /regularizations/{id}/approve` and `/reject`), guarded by `MANAGER_ATTENDANCE` VIEW, EDIT or `MANAGER_LEAVE` APPROVE as in the contract
- [ ] T038 [P] [US5] `frontend/src/features/managerAttendance/MyManagerMonthPage.tsx` (route `/manager-attendance/me/month`): the month with a status per day, times, flags and the forgotten check-in request form with inline validation
- [ ] T039 [P] [US5] `frontend/src/features/managerAttendance/ManagerAttendanceGridPage.tsx` (route `/operations/manager-attendance`) and `ManagerDayDialog.tsx`: the grid by month with the Zone filter and search, the day detail with locations, photo and history, and the correction form with a required reason; hidden actions without EDIT
- [ ] T040 [P] [US5] Frontend tests `__tests__/ManagerMonth.test.tsx` and `__tests__/ManagerGrid.test.tsx` with a fixture per role, the refusals, the correction reason, and an axe check in light and dark themes

**Checkpoint**: US5 works; quickstart section 5 passes.

---

## Phase 6: User Story 3 - Manager Leave Approved by the Director (P1)

**Goal**: a Manager requests leave of a spec 009 type; the Director or an Admin decides it; approved days become Leave.

**Independent Test**: request two days of casual leave, approve them, see them as paid Leave; a Loss-of-Pay day counts as
unpaid; a Manager cannot decide.

### Tests for User Story 3 (write first, they must fail)

- [ ] T041 [P] [US3] `backend/src/test/java/com/hls/leave/manager/ManagerLeaveRequestApiTest.java`: a Manager requests leave with a type, dates and a reason; refused for reversed dates, a range with no working day, a date before the joining date, no joining date, a missing reason, a half day on a middle date, and an overlap with another pending or approved request (409); weekly offs and holidays inside the range are not leave days; a Manager cancels a pending request but not an approved one
- [ ] T042 [P] [US3] `backend/src/test/java/com/hls/leave/manager/ManagerLeaveDecisionTest.java`: an Admin or Director approves (the days are written onto the Manager's records), rejects with a required reason, or revokes with a required reason (the days are removed); a Manager never decides, nobody decides their own request, an Admin decides a Director's and a Director an Admin's request; approval into a locked month is 409 and changes nothing; the Manager is told of every decision; the days of a Loss-of-Pay type are unpaid and any other approved type is paid
- [ ] T043 [P] [US3] `backend/src/test/java/com/hls/attendance/manager/ManagerLeaveAttendanceTest.java`: `apply` writes `leave_request_id`, `leave_fraction` and `leave_unpaid` on each day and a `LEAVE_APPLIED` history row, creating the row when none exists; `remove` clears them with `LEAVE_REMOVED`; a day with full-day leave refuses a check-in and a half-day leave accepts one; both fail with a conflict in a locked month and change nothing
- [ ] T044 [P] [US3] `backend/src/test/java/com/hls/leave/manager/ManagerLeaveAuthorizationTest.java`: Manager succeeds on `/me/leave` endpoints and only for their own requests; Admin and Director succeed on the decision endpoints; Teacher and System get 403 on each; a Manager gets 403 on the decision endpoints

### Implementation for User Story 3

- [ ] T045 [US3] Write `backend/src/main/resources/db/migration/V29__create_manager_leave.sql`: `manager_leave_request` per data-model.md (id uuid PK, manager_id uuid not null, leave_type_id uuid not null, first_date and last_date date not null with first_date <= last_date, first_half and last_half boolean not null default false, reason varchar(500) not null, status check in ('PENDING','APPROVED','REJECTED','CANCELLED','REVOKED'), requested_at timestamptz not null, decided_by uuid, decided_at timestamptz, decision_reason varchar(500), version bigint not null)
- [ ] T046 [US3] `attendance/api/ManagerLeaveAttendance.java` (with its `LeaveDay` record) and `attendance/manager/internal/ManagerLeaveAttendanceImpl.java` per contracts/manager-attendance-read-api.md: `workingDays`, `apply` and `remove`, using `ManagerMonthGuard`, writing history, and rejecting a full-day leave over an existing full-day leave
- [ ] T047 [US3] `leave/manager/internal/ManagerLeaveRequest.java` and its repository, and `ManagerLeaveService.java`: request, cancel, approve, reject, revoke with the decider rules of T042, the Loss-of-Pay flag from `LeaveTypes`/the type catalog, the audit as `MANAGER_LEAVE`; and the events `ManagerLeaveRequested`, `ManagerLeaveDecided` and `ManagerLeaveCancelled` in `leave/api/`
- [ ] T048 [US3] `leave/manager/web/MyManagerLeaveController.java` (`GET /me/leave/types`, `GET /me/leave`, `POST /me/leave`, `DELETE /me/leave/{id}`) and `ManagerLeaveDecisionController.java` (`GET /leave`, `POST /leave/{id}/approve`, `/reject`, `/revoke`) under `/api/v1/manager-attendance`, guarded as in the contract; extend `ManagerAttendanceEventListener` and `NotificationType` with `MANAGER_LEAVE_REQUESTED` and `MANAGER_LEAVE_DECIDED` and their texts
- [ ] T049 [P] [US3] `frontend/src/features/managerAttendance/MyManagerLeavePage.tsx` (route `/manager-attendance/me/leave`): the leave types, the request form with inline validation, and the Manager's requests with cancel; and `ManagerLeaveDecisionsPage.tsx` (route `/operations/manager-leave`): pending leave and forgotten check-ins with approve, reject and revoke and the required reasons
- [ ] T050 [P] [US3] Frontend tests `__tests__/ManagerLeave.test.tsx` with a fixture per role (the Manager sees the request page, Admin and Director see the decisions page, others "not authorized"), the refusals, and an axe check in light and dark themes

**Checkpoint**: US3 works; quickstart section 3 passes.

---

## Phase 7: User Story 7 - Payroll Reads Manager Attendance (P1)

**Goal**: payroll gets a Manager's working, worked, leave, unpaid and field days and the lock state, and spec 013a's
`PayRules` answers a Manager's unpaid days and total loss of pay.

**Independent Test**: for a Manager with a known month the figures equal the hand-computed ones, and `PayRules` returns
the unpaid days instead of "not available".

### Tests for User Story 7 (write first, they must fail)

- [ ] T051 [P] [US7] `backend/src/test/java/com/hls/attendance/manager/ManagerAttendanceReadApiTest.java` (integration): the hand-computed figures of quickstart section 7 through the public interface; `NoJoiningDate` and `Unknown` instead of zero; a locked month returns the frozen figures after the records change; the bulk form equals the single form for 40 Managers and runs in a fixed number of queries; nothing returned carries a location or a photo
- [ ] T052 [P] [US7] Extend `backend/src/test/java/com/hls/payroll/PayRulesTest.java` and `PayRulesCalendarTest.java`: a Manager's `unpaidDays` returns each unpaid date with its fraction; `totalLossOfPay` for a Manager sums each unpaid day at that day's salary and rounds once; `NoJoiningDate` and `Unknown` give `Unavailable` with the reason; a Manager with an unpaid day and no salary gives `Cannot`; an Admin's or Director's `unpaidDays` is `Found` with an empty list and `totalLossOfPay` is `Found` with zero; the old "Manager attendance is not recorded yet" answer is gone

### Implementation for User Story 7

- [ ] T053 [US7] `attendance/api/ManagerAttendanceReadApi.java`, `ManagerMonth.java` and `UnpaidDay.java` per contracts/manager-attendance-read-api.md, and `attendance/manager/internal/ManagerAttendanceReadApiImpl.java` over `ManagerMonthCalculator`, the records, `WorkingCalendar`, `ManagerQueries.employment` and the frozen figures, in a fixed number of queries for the bulk form
- [ ] T054 [US7] Change `payroll/internal/PayRulesImpl.java`: a Manager's `unpaidDays` and `totalLossOfPay` use `ManagerAttendanceReadApi`; a `Kind.STAFF` person's `unpaidDays` is `UnpaidDays.Found(List.of())` and `totalLossOfPay` is zero by rule; remove `MANAGER_ATTENDANCE_NOT_RECORDED` and `STAFF_ATTENDANCE_NOT_RECORDED`; update `PayrollModuleRulesTest` so `payroll` may use `attendance.api.ManagerAttendanceReadApi`
- [ ] T055 [P] [US7] Update spec 013a's documents for the change: `specs/013a-salary-structures/contracts/pay-rules-interface.md` (unpaid days of a Manager and of staff), `research.md` section 5 and `spec.md` FR-012 and User Story 3 scenario 10, each saying what replaced "not available"

**Checkpoint**: US7 works; quickstart section 7 passes; `PayRulesTest` returns the Manager unpaid days.

---

## Phase 8: User Story 6 - Lock the Month for Payroll (P2)

**Goal**: an Admin locks a Manager's month, or every Manager's, once it has ended and nothing is pending; it freezes the
figures payroll reads; a reopen needs a reason.

**Independent Test**: lock a month for all Managers, see check-ins, corrections and leave for it refused, reopen with a
reason and see them allowed again.

### Tests for User Story 6 (write first, they must fail)

- [ ] T056 [P] [US6] `backend/src/test/java/com/hls/attendance/manager/ManagerMonthLockTest.java`: lock one Manager or all after the month has ended; a lock of a month that has not ended is 409; a lock is refused while any leave or forgotten-check-in request touching the month is pending and the response lists them; locking all locks each lockable Manager and returns the refused ones with the reason; the figures are frozen at lock and unchanged after a record changes; every writer (check-in, completion, correction, regularization approval, leave apply and remove) is refused with 409 in a locked month; a reopen needs a reason, clears the frozen figures and allows changes; a lock again freezes the new figures; each action is audited as `MANAGER_MONTH`; a Director without Process gets 403 on lock and reopen
- [ ] T057 [P] [US6] `backend/src/test/java/com/hls/leave/manager/ManagerLeavePendingRequestsTest.java`: the leave module's `PendingManagerRequests` implementation returns the pending leave requests that touch a month, and a request decided or cancelled stops appearing

### Implementation for User Story 6

- [ ] T058 [US6] `attendance/api/PendingManagerRequests.java` (the SPI of contracts/manager-attendance-read-api.md) and `attendance/manager/internal/ManagerMonthLockService.java`: lock (one or all), reopen, event log, frozen figures from `ManagerMonthCalculator`, collecting every `PendingManagerRequests` bean (`ObjectProvider`) plus the forgotten-check-in requests, so `attendance` never depends on `leave`
- [ ] T059 [US6] `leave/manager/internal/ManagerLeavePendingRequests.java` implementing the SPI; `ManagerAttendanceController`: `POST /months/{yyyy-MM}/lock` and `/reopen` guarded by `MANAGER_ATTENDANCE` PROCESS
- [ ] T060 [P] [US6] In `ManagerAttendanceGridPage.tsx` add the lock and reopen actions (hidden without PROCESS) with the pending-request list shown when a lock is refused and a required reason for a reopen; extend `__tests__/ManagerGrid.test.tsx`

**Checkpoint**: US6 works; quickstart section 6 passes.

---

## Phase 9: User Story 4 - Missed Check-in Alert (P2)

**Goal**: a Manager with no check-in and no leave past the cut-off on a working day is told, with the Director and the
Admin, once; the day shows on a missed list.

**Independent Test**: leave a Manager without a check-in past the cut-off and see one notification to each of the
three, and the day on the missed list; check in later and see the day leave the list.

### Tests for User Story 4 (write first, they must fail)

- [ ] T061 [P] [US4] `backend/src/test/java/com/hls/attendance/manager/MissedCheckInJobTest.java`: with the clock and settings fixed, a working day past the cut-off notifies the Manager and every active Admin and Director once; running the job twice or from two instances does not notify twice (the unique key); a Manager with approved leave, a weekly off, a holiday, a day outside the employment span, a Manager with no joining date and a Manager who already checked in are not notified; a check-in after the alert marks the day late and removes it from the missed list; changing the cut-off applies from the next run
- [ ] T062 [P] [US4] `backend/src/test/java/com/hls/attendance/manager/MissedListApiTest.java`: `GET /missed` and `GET /on-leave` list the right Managers for a date (default today); a Manager, a Teacher and System get 403

### Implementation for User Story 4

- [ ] T063 [US4] `attendance/manager/internal/MissedCheckInJob.java` on the existing scheduler every 2 minutes (switchable by a property like `OfferExpiryJob`, with a `runNow()` for tests): after `missed_cutoff` on a business-calendar working day, insert the `manager_missed_alert` row and publish `ManagerCheckInMissed` for each qualifying Manager; add the event to `attendance/api/`, the `MANAGER_CHECK_IN_MISSED` type and text, and the listener case in `ManagerAttendanceEventListener`
- [ ] T064 [US4] `ManagerGridService`/`ManagerAttendanceController`: `GET /missed` and `GET /on-leave`
- [ ] T065 [P] [US4] In `ManagerAttendanceGridPage.tsx` add the missed check-ins and Managers-on-leave-today panels and the settings form (cut-off and late time, hidden without EDIT); extend `__tests__/ManagerGrid.test.tsx`

**Checkpoint**: US4 works; quickstart section 4 passes.

---

## Phase 10: Polish & Cross-Cutting Concerns

- [ ] T066 [P] `attendance/manager/internal/ManagerAttendanceDevSeeder.java` (dev profile only): for the demo Manager a few past days (a worked day, a field day with a purpose, a leave day, an absence) so the month and the grid show values; do not seed any other module's data
- [ ] T067 [P] Add a "032 Manager Attendance" folder under `postman/HLS API/` with every endpoint of contracts/manager-attendance-api.md and the refusals described, and list it in `postman/README.md`
- [ ] T068 [P] Update `docs/spec-roadmap.md` (the 032 row status and the 013a note that Manager unpaid days are now answered) and `.specify/memory/constitution.md` (Manager Attendance rows in the Default role access matrix with a patch entry in the Sync Impact Report)
- [ ] T069 Run `ManagerAttendanceModuleRulesTest`, `LeaveModuleRulesTest`, `AttendanceModuleRulesTest`, `PayrollModuleRulesTest` and `ApplicationModulesTest` (Spring Modulith verification) and fix any boundary violation; confirm the existing attendance, leave and payroll tests still pass unchanged
- [ ] T070 [P] Accessibility pass on the five screens: keyboard order, a label on every form field, error text announced, WCAG 2.2 AA contrast in light and dark themes, no horizontal scroll at phone width, the location-blocked message readable
- [ ] T071 Run the full quickstart.md walkthrough and record each step's outcome in `specs/032-manager-attendance/quickstart-results.md`; also time a check-in (under 30 seconds) and a check-out (under 20 seconds), SC-001
- [ ] T072 Financial review of the unpaid-day rules in research.md section 3 against decisions D7, D12 and D15 of `docs/spec-roadmap.md` (Constitution Principle IX, Development Workflow item 5); note the outcome in `quickstart-results.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: T001 first and blocking; if a prerequisite is missing, nothing else starts
- **Foundational (Phase 2)**: after Setup; blocks every user story
- **US1 (Phase 3)**: after Foundational; the MVP
- **US2 (Phase 4)**: after US1 (it extends the check-in)
- **US5 (Phase 5)**: after US1 (it reads and corrects the days)
- **US3 (Phase 6)**: after US1; independent of US5 except that both use `ManagerMonthGuard`
- **US7 (Phase 7)**: after US1 and US3 (leave days are part of the figures); `PayRules` change after `ManagerAttendanceReadApi`
- **US6 (Phase 8)**: after US5 and US3 (it consults leave through the SPI)
- **US4 (Phase 9)**: after US1 and US5 (it reads the month and shares the listener)
- **Polish (Phase 10)**: after all stories

### Within Each Story

- Tests are written first and must fail
- The pure calculator before the services; services before controllers; controllers before screens

### Parallel Opportunities

- T003 with T002; T005, T006, T007, T009, T010, T014 after T004 as noted
- In US1: T015 to T019 together; T023 and T024 with T020 to T022
- In US5: T028 to T032 together; T038 to T040 after T037
- In US3: T041 to T044 together; T049 and T050 after T048
- US3 and US5 can run in parallel after US1

## Parallel Example: User Story 3

```text
# Tests first, together:
Task: "ManagerLeaveRequestApiTest in backend/src/test/java/com/hls/leave/manager/ManagerLeaveRequestApiTest.java"
Task: "ManagerLeaveDecisionTest in backend/src/test/java/com/hls/leave/manager/ManagerLeaveDecisionTest.java"
Task: "ManagerLeaveAttendanceTest in backend/src/test/java/com/hls/attendance/manager/ManagerLeaveAttendanceTest.java"

# Then the screens, together, once the endpoints exist:
Task: "MyManagerLeavePage and ManagerLeaveDecisionsPage in frontend/src/features/managerAttendance/"
Task: "ManagerLeave.test.tsx in frontend/src/features/managerAttendance/__tests__/ManagerLeave.test.tsx"
```

## Implementation Strategy

### MVP First (US1 only)

1. T001 to T003, then Phase 2
2. Phase 3 (US1): a Manager checks in and out with a location
3. Stop and validate with quickstart section 1
4. Then US5 and US3, because payroll (US7) cannot read a month without leave and corrections

### Incremental Delivery

1. Setup + Foundational: tables, permissions, the calculator
2. US1 and US2: the record; US5: the month and the grid; US3: leave
3. US7: payroll reads Manager attendance (013b can start for Managers); US6: the lock; US4: the alert
4. Polish and the financial review

## Notes

- [P] tasks = different files, no dependencies
- A check-in's time and a correction's time come from the server in the business time zone, never from the device
- A missing check-out is a flag, never a deduction; a month with no information is never "no unpaid days" (SC-008)
- Admin and Director have no attendance; System accounts are not payroll people (spec Clarifications, FR-023)
- A location and a photo are shown only to the Manager, Admin and Director, never in a grid or an export
- Commit after each task or logical group
