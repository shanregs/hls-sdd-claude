---

description: "Task list for feature implementation"
---

# Tasks: Leave Management

**Input**: Design documents from `/specs/009-leave/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/leave-api.md,
quickstart.md, and **specs 001-005 and 008 implemented** (this feature adds permission modules and
navigation to 002's matrix/catalog, publishes into 003's Change History, reads Teachers through
`teacher.api` and writes leave marks through a new `attendance.api` contract).

**Tests**: included as first-class tasks - Constitution Principle IX requires per-role authorization
tests on every endpoint and per-scope-boundary tests (Manager A vs Manager B) on every list and
decision, and the spec's invariants (working-day counting, overlap, all-or-nothing approval, locked
months, supervisor-set days) are only trustworthy with tests.

**Organization**: grouped by user story in priority order (spec.md US1-US5). Foundational work that
several stories share (permissions, migration, the attendance-side contract) comes first.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: US1-US5; absent for Setup/Foundational/Polish

## Path Conventions

Backend `backend/src/main/java/com/hls/{leave,attendance,identity,audit}/...`, tests under
`backend/src/test/java/com/hls/...`; migration `backend/src/main/resources/db/migration`; frontend
`frontend/src/features/leave/...`.

---

## Phase 1: Setup

- [ ] T001 Create the `leave` module skeleton: `backend/src/main/java/com/hls/leave/{api,internal,web}/` with `package-info.java` files declaring the module (copy the attendance pattern) and extend the ArchUnit/Modulith boundary test (`backend/src/test/java/com/hls/ModuleBoundaryTest.java` or the existing equivalent) so `leave` may depend only on `attendance.api` and `teacher.api`, and nothing may depend on `leave.internal`
- [ ] T002 Create `frontend/src/features/leave/` with empty `leaveApi.ts`, `leaveDates.ts` (DD/MM/YYYY helpers reusing the attendance `monthUtils`), `LeaveStatusChip.tsx` placeholders so later tasks only fill them

---

## Phase 2: Foundational (blocking prerequisites)

- [ ] T003 Write `backend/src/main/resources/db/migration/V17__create_leave_tables.sql`: `leave_type` (id uuid PK, code varchar(20) unique, name varchar(60), sort_order int, active boolean default true) seeded with CASUAL/Casual, SICK/Sick, PERSONAL/Personal, OTHER/Other; `leave_request` per data-model.md (teacher_id uuid not null, leave_type_id FK, first_date/last_date date not null with `CHECK (last_date >= first_date AND last_date - first_date <= 89)`, half_day_start/half_day_end boolean not null default false, working_days numeric(5,2) not null, reason varchar(500) not null, status varchar(12) not null in PENDING/APPROVED/REJECTED/CANCELLED, decided_by_user_id uuid null, decided_at timestamptz null, decision_note varchar(500) null, cancelled_by_kind varchar(12) null, created_by_user_id uuid not null, created_at timestamptz not null, version bigint not null), `CHECK (status <> 'REJECTED' OR decision_note IS NOT NULL)`, the `EXCLUDE USING gist (teacher_id WITH =, daterange(first_date, last_date, '[]') WITH &&) WHERE (status IN ('PENDING','APPROVED'))` constraint, indexes `(status, first_date)` and `(teacher_id, first_date desc)`; and `ALTER TABLE attendance_mark ADD COLUMN leave_request_id uuid` (+ index) and `ALTER TABLE attendance_mark_history ADD COLUMN leave_request_id uuid`
- [ ] T004 [P] Add permission modules `LEAVE_MANAGEMENT(VIEW, APPROVE)` and `MY_LEAVE(VIEW, CREATE, DELETE)` to `identity/permissions/PermissionModule.java`; in `PermissionEligibility.java` make `LEAVE_MANAGEMENT` eligible for Admin, Director and Manager only, `MY_LEAVE` for Teacher only (System excluded from both); seed defaults in `PermissionMatrixService.seedDefaults` per the spec table (Admin, Director: LEAVE_MANAGEMENT VIEW+APPROVE; Manager: VIEW+APPROVE; Teacher: MY_LEAVE VIEW+CREATE+DELETE), idempotent
- [ ] T005 [P] Add navigation to `identity/accessmodel/NavigationCatalog.java`: section **LEAVE** (Teacher) with items Apply Leave (`/leave/apply`, MY_LEAVE CREATE) and My Leave History (`/leave/history`, MY_LEAVE VIEW) placed directly after MY ATTENDANCE, and **OPERATIONS → Leave Management** (`/operations/leave`, LEAVE_MANAGEMENT VIEW) for Admin, Director, Manager; add data-scope entries in `AccessModelService.java` (Manager: Assigned, Teacher: Own, Admin/Director: Org-wide)
- [ ] T006 [P] Map entity type `LEAVE_REQUEST` to `PermissionModule.LEAVE_MANAGEMENT` VIEW in `audit/support/AuditVisibility.java`
- [ ] T007 Add `leaveRequestId` (nullable UUID) to `attendance/internal/AttendanceMark.java` and `MarkHistoryEntry.java`; make `MarkService.setMark` set it to null on every non-leave write (spec research.md section 2); update `MarkViewFactory`/`MarkView` to expose `leaveRequestId` so grids and history can link to the request
- [ ] T008 Define `attendance/api/LeaveAttendance.java` (named interface, shape in contracts/leave-api.md: `businessToday()`, `workingDays(teacherId, from, to)`, `problems(teacherId, days)`, `apply(requestId, approverUserId, teacherId, days)`, `remove(requestId, actorUserId)` with records `LeaveDay(date, schoolId, value)` and `RemoveResult(removed, keptBecauseChanged)`) and implement it in `attendance/internal/LeaveAttendanceImpl.java`: working days from `RollupCalculator.plan` (placement + per-School weekly off + non-working dates; days without a placement are not working days), `problems` listing locked months and days set by a supervisor to a status other than L, `apply` taking the Teacher-month advisory locks in ascending month order and writing L marks through a new `MarkService.setLeaveMark` that skips the future-date and 3-day-window checks but keeps lock, placement and active-code checks, kind SUPERVISOR attributed to the approver, plus history entries and `ATTENDANCE_MARK` audit naming the request; `remove` deleting only marks whose `leave_request_id` still equals the request, writing CLEARED history and refusing the whole call when any such mark is in a locked month
- [ ] T009 [P] Unit and integration tests for T008 in `backend/src/test/java/com/hls/attendance/LeaveAttendanceTest.java` (extends `AttendanceTestBase`): working days skip Sunday, holiday, unplaced days; half values; `problems` reports a locked month and a supervisor-set Present but not a Teacher-set one or an existing L; `apply` marks future days, replaces a Teacher-set mark (history keeps it), refuses atomically; `remove` leaves a hand-changed day and refuses on a locked month; a later `setMark` clears `leave_request_id`
- [ ] T010 [P] Extend `PermissionEligibilityTest`, `NavigationSectionOrderTest` and the access-model resolution tests under `backend/src/test/java/com/hls/identity/` for the two modules, the LEAVE section order (after MY ATTENDANCE, before the System/Audit/Account sections per the existing catalog) and the OPERATIONS item per role

**Checkpoint**: migration applies, matrix and navigation seeded, attendance contract proven.

---

## Phase 3: User Story 1 - A Teacher Applies for Leave (P1) 🎯 MVP

**Goal**: submit a valid request, see its working-day count, get clear refusals.

**Independent Test**: as a placed Teacher apply for a range including a Sunday and a holiday; see a Pending request with only working days counted; each refusal case returns its reason.

- [ ] T011 [P] [US1] JPA entities `LeaveType`, `LeaveRequest` (fields and constraints verbatim from data-model.md; `@Version`), `LeaveStatus` enum, repositories `LeaveTypeRepository`, `LeaveRequestRepository` in `backend/src/main/java/com/hls/leave/internal/`
- [ ] T012 [US1] `LeaveRequestService` in `leave/internal/`: `preview(teacherId, draft)` and `submit(userId, draft)` using `LeaveAttendance.workingDays`; counting (1 per working day, 0.5 for a half-day first or last **working** day; on a one-working-day range both flags set is refused); refusals with the spec wording: no working day, overlap naming the clashing request (also map the exclusion-constraint violation to the same 409), first date more than 30 days before `businessToday()`, locked month (`LeaveAttendance.problems` locked part), range longer than 90 calendar days, last before first, reason empty or over 500 characters, Teacher not placed; Teacher resolved via `TeacherDirectory.teacherOfUser`; status PENDING, `working_days` stored
- [ ] T013 [P] [US1] `LeaveAudit` in `leave/internal/` publishing `LEAVE_REQUEST` changes through `ChangeRecorder` (created, status before/after, reason or note, cancelledBy), and call it from submit
- [ ] T014 [US1] `MyLeaveController` (`/api/v1/me/leave`) with `GET /types`, `POST /preview`, `POST` guarded by `MY_LEAVE` CREATE/VIEW via `PermissionGuard`; `LeaveExceptionAdvice` mapping the shared `InvalidInputException`/`ConflictException`/`NotFoundException` to 400/409/404 in the house `{reason}` format, in `leave/web/`
- [ ] T015 [P] [US1] Tests `backend/src/test/java/com/hls/leave/LeaveCountingTest.java` (table-driven: Sunday/holiday/unplaced exclusion, half start, half end, both on one-day range refused, single working day, 90-day cap, month boundaries) and `LeaveApplyTest.java` (valid submit; every refusal; overlap with Pending and with Approved; Cancelled/Rejected do not block; two simultaneous overlapping submits yield exactly one success; per-role authorization: only Teacher allowed, Admin/Director/Manager/System get 403)
- [ ] T016 [P] [US1] Frontend `leaveApi.ts` (types for request view, preview; calls for types, preview, submit) and `ApplyLeavePage.tsx` (react-hook-form: type select, first/last date, half-day-start and half-day-end checkboxes, reason; debounced preview showing the working-day count and per-day list; submit; refusal messages shown; "you cannot apply until you are placed" state); route `/leave/apply` guarded in `frontend/src/App.tsx`
- [ ] T017 [P] [US1] `ApplyLeavePage.test.tsx`: count shown, refusal shown, success message, unplaced state; add `ApplyLeavePage` to `frontend/src/a11y/a11y.test.tsx` in both themes

**Checkpoint**: a Teacher can apply; MVP of the input side.

---

## Phase 4: User Story 2 - A Teacher Follows and Cancels Their Requests (P1)

**Goal**: own history with status and reasons; cancel Pending.

**Independent Test**: apply, see Pending, cancel, see Cancelled; another Teacher cannot see it.

- [ ] T018 [US2] In `LeaveRequestService`: `listOwn(userId, status, page)` (newest first, own Teacher only) and `cancelOwn(userId, id)` for PENDING (row lock `FOR UPDATE`, status check, set CANCELLED with `cancelled_by_kind = TEACHER`, audit); the APPROVED-before-first-day branch is added in T031; compute `allowedActions` for the view
- [ ] T019 [US2] Add `GET /api/v1/me/leave` and `POST /api/v1/me/leave/{id}/cancel` to `MyLeaveController` (`MY_LEAVE` VIEW and DELETE); another Teacher's id answers 404
- [ ] T020 [P] [US2] Tests `LeaveHistoryTest.java`: own only (two Teachers), newest first, paging, status filter, cancel Pending, cancel Rejected/Cancelled refused, direct id of another Teacher's request is 404, per-role authorization
- [ ] T021 [P] [US2] Frontend `MyLeaveHistoryPage.tsx` (grid with number, type, dates, working days, `LeaveStatusChip`, decided by/at, reason on rejection, cancel icon via `RowActionButton` + `ConfirmDialog` shown only when `allowedActions` has CANCEL), route `/leave/history`; `MyLeaveHistoryPage.test.tsx` and the a11y cases in both themes

**Checkpoint**: Teacher side complete.

---

## Phase 5: User Story 3 - A Manager, Admin or Director Decides Requests (P1)

**Goal**: scoped list; approve (feeding attendance) and reject with reason; one winner under concurrency.

**Independent Test**: as Manager approve one in-scope request and reject another with a reason; the Teacher sees both; out-of-scope requests are invisible; two simultaneous decisions yield one success.

- [ ] T022 [US3] `LeaveScope` in `leave/internal/` wrapping `TeacherScopeQueries.teacherScope(userId, roles)` (no own scoping logic); `LeaveRequestService.listInScope(...)` with filters status (default PENDING), teacherId, schoolId, month and paging, plus `pendingCount`; Teacher names and Schools resolved in bulk through `TeacherDirectory`/`SchoolDirectory` (no per-row queries); `get(id)` answering 404 outside scope
- [ ] T023 [US3] `LeaveDecisionService` in `leave/internal/`: `approve(actor, id, note, version)`: lock the row, require PENDING (409 "already decided"/"cancelled"), recompute `workingDays` (do not trust the stored count), re-check overlap, call `LeaveAttendance.problems` and refuse as a whole with the list (`days set by a supervisor: ...`, `month locked: ...`), then `LeaveAttendance.apply`, set APPROVED, decided by/at/note, audit; `reject(actor, id, reason, version)`: reason required (max 500), set REJECTED; stale `version` is 409; all in one transaction
- [ ] T024 [US3] `LeaveManagementController` (`/api/v1/leave`): `GET` list (with `pendingCount`), `GET /{id}` (with the days the request would mark), `POST /{id}/approve`, `POST /{id}/reject`, guarded by `LEAVE_MANAGEMENT` VIEW/APPROVE and `LeaveScope`; add `ArchUnit` check that the controller uses only `LeaveScope` for scoping
- [ ] T025 [P] [US3] Tests `LeaveDecisionTest.java` (extends `AttendanceTestBase`): approve writes the marks (US4 assertions live in T031); reject needs a reason; deciding twice is 409; cancelled-while-open is 409; stale version 409; concurrency: two approvers exactly one success, approver vs Teacher cancel exactly one outcome; approval refused as a whole for a supervisor-set day and for a locked month with nothing changed; `LeaveScopeBoundaryTest.java`: two Managers with different assignments see and decide only their own Teachers' requests (list, detail, approve, reject; foreign id is 404), Admin/Director see all; per-role authorization matrix (Teacher/System 403 on every `/leave` endpoint)
- [ ] T026 [P] [US3] Frontend `LeaveManagementPage.tsx` (status filter default Pending, Teacher/School/month filters, Pending count in the header, grid with Teacher, School, type, dates, working days, status chip, reason; row icons Approve/Reject via `RowActionButton`), `LeaveDecisionDialog.tsx` (approve with optional note; reject with required reason; shows conflicts returned by the server), route `/operations/leave`; `LeaveManagementPage.test.tsx` per role (Manager scope, Admin all) and a11y cases in both themes
- [ ] T027 [P] [US3] Add the guarded routes for all three screens to `frontend/src/App.tsx` if not yet added and confirm the server-driven navigation renders LEAVE for a Teacher and Leave Management for the three supervisor roles (`frontend/src/layout/` fixture tests)

**Checkpoint**: decisions work end to end; attendance already receives the marks.

---

## Phase 6: User Story 4 - Approved Leave Appears in Attendance (P1)

**Goal**: marks visible everywhere L marks show; revoke and Teacher cancel remove exactly what leave made.

**Independent Test**: approve a five-day request including a holiday: four L days in the grid and rollup; revoke: they disappear (a hand-changed day stays); a locked month blocks revoke.

- [ ] T028 [US4] `LeaveDecisionService.revoke(actor, id, reason, version)`: APPROVED only, reason required, `LeaveAttendance.remove`, set CANCELLED with `cancelled_by_kind = SUPERVISOR` and the reason as `decision_note`, audit; refused (409) when a mark to be removed lies in a locked month; endpoint `POST /api/v1/leave/{id}/revoke` in `LeaveManagementController`
- [ ] T029 [US4] `LeaveRequestService.cancelOwn`: extend T018 so an APPROVED request whose `first_date` is after `businessToday()` can be cancelled by its Teacher (no reason), removing its marks through `LeaveAttendance.remove`, audited as a Teacher cancellation; once the first day has arrived the call is 409 and the view's `allowedActions` omits CANCEL
- [ ] T030 [P] [US4] Surface the source of a leave mark: in attendance `TeacherMonthPanel.tsx`/mark history (`frontend/src/features/attendance/`) show "From leave request" with the dates when `leaveRequestId` is present; keep the `dayStyle.ts` L colour and label unchanged
- [ ] T031 [P] [US4] Tests `LeaveFeedTest.java` (extends `AttendanceTestBase`): approve a five-day request including a holiday and a Sunday: marks only on working days, half values, attributed to the approver, `SUPERVISOR` kind (the Teacher cannot change them), visible in the Teacher calendar API, the Manager grid, the rollup leave total and the CSV export; replace a Teacher-set Present (history keeps it); refuse a supervisor-set Present; future days marked; placement change mid-range follows the School per date; revoke removes marks but keeps a hand-changed day; revoke refused for a locked month; Teacher cancel of a future approved request removes marks, after the first day it is refused; re-apply after revoke works (the exclusion constraint no longer blocks)
- [ ] T032 [P] [US4] Frontend: revoke action with reason dialog in `LeaveManagementPage.tsx` (icon shown when `allowedActions` has REVOKE); `MyLeaveHistoryPage.tsx` shows the cancel icon for an Approved not-yet-started request; tests for both

**Checkpoint**: leave and attendance agree in every view.

---

## Phase 7: User Story 5 - Everything Is Audited and Scoped (P2)

**Goal**: complete audit trail and role enforcement proven.

**Independent Test**: perform each action and find its Change History entry; run the per-role matrix.

- [ ] T033 [US5] Audit completeness: ensure every transition (submit, cancel by Teacher, cancel by supervisor, approve, reject, revoke) records actor, time, Teacher, request, old and new status and reason/note via `LeaveAudit`; leave-made mark changes carry the request id in the `ATTENDANCE_MARK` detail; Manager sees `LEAVE_REQUEST` entries only for Teachers in scope in Change History (extend the audit scope filter if it needs the Teacher id)
- [ ] T034 [P] [US5] Tests `LeaveAuditTest.java`: each transition appears in `GET /api/v1/audit/change-history` within 5 seconds for Admin/Director with the right fields, a Manager sees only in-scope entries, Teacher and System see none; and a full per-role/per-endpoint authorization table test `LeaveAuthorizationTest.java` covering every endpoint in contracts/leave-api.md
- [ ] T035 [P] [US5] `LeaveDevSeeder` in `leave/internal/` (demo flag only, idempotent by a fixed reason-prefix marker, runs after `AttendanceDevSeeder`): for Tara and the three attendance demo Teachers create one Pending future request (Pongal travel), one Approved past request of two working days with its L marks written through `LeaveAttendance.apply` (family function), and one Rejected request with a reason (medical appointment during exams); test `LeaveDevSeederTest.java` for idempotency and expected counts

---

## Phase 8: Polish & Cross-Cutting

- [ ] T036 [P] Update `docs/running-locally.md` (a Leave section: Teacher apply and history, supervisor decisions, demo data) and add Postman requests under `postman/HLS API/` for the `/me/leave` and `/leave` endpoints (file-based collection, same style as spec 008)
- [ ] T037 [P] Update `docs/spec-roadmap.md` row 009 to Implemented with the test counts, and `specs/002-access-model-app-shell/{spec,data-model,tasks}.md` with the two new permission modules and navigation items (same way spec 008 did)
- [ ] T038 Run the full backend test suite and the frontend Vitest suite including `src/a11y`, then execute `quickstart.md` scenarios 1-8 against the running local app (Playwright-core script allowed for the UI checks) and record the results in `specs/009-leave/quickstart-results.md`
- [ ] T039 Re-read spec.md FR-001..FR-014 and SC-001..SC-008 against what was built and note any gap in `quickstart-results.md`; open the PR with `gh pr create`

---

## Dependencies & Execution Order

- Phase 1 then Phase 2 (blocking). Within Phase 2: T003 first; T004-T006 parallel; T007 before T008; T009 after T008; T010 after T004/T005.
- US1 (Phase 3) needs Phase 2. US2 needs US1's service and entities. US3 needs US1 (entities) and T008 (apply). US4 needs US3 (decisions exist) and T008. US5 needs everything before it.
- Parallel examples: T004/T005/T006 together; in US1 T011/T013/T015/T016/T017 together once T003 is done; in US3 T025/T026/T027 together once T024 exists.

## Implementation Strategy

- **MVP**: Phase 1, Phase 2, US1 (apply and preview), US2 (history, cancel), US3 (decide) - a working request/approval loop with attendance feed already active; then US4's revoke and Teacher cancel.
- Land each phase as its own commit with tests green; run the frontend a11y cases for each new screen as it is added.
- Specs, docs and Postman are updated in Phase 8 but each task that changes a contract must update `contracts/leave-api.md` in the same commit.
