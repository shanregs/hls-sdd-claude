---

description: "Task list for feature implementation"
---

# Tasks: Same-Day Flexibility Without Prior Approval

**Input**: Design documents from `/specs/040-same-day-flexibility/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/same-day-api.md, quickstart.md. Specs 008, 009,
013a/013b, 017a, 023, 025, 026, 032, 034 and 036 are implemented on `main`.

**Tests**: included as first-class tasks (Constitution Principle IX: per-role and per-scope tests on every endpoint, UI
tests per role). The pay-rule tests are mandatory and written first: this feature changes what is deducted from salary.

**Organization**: grouped by user story (spec.md US1-US4). Order: Setup, Foundation, US1 (same-day leave and the pay rule),
US2 (Manager cover), US3 (same-day tasks, visits, mobile), US4 (visibility and reports), Polish.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: US1-US4; absent for Setup/Foundational/Polish

## Path Conventions

Backend `backend/src/main/java/com/hls/{leave,attendance,payroll,substitution,tasks,recruitment/marketing,activitycalendar,reporting}/...`,
tests under `backend/src/test/java/com/hls/...`; frontend `frontend/src/features/{leave,substitution,tasks,marketing,activityCalendar,dashboard}/...`;
mobile `mobile/src/{tasks,visits,outbox}/...`. **One migration (V150), no new permission key, no new menu item** (plan.md).
Run one Maven test class at a time with `JAVA_HOME` on JDK 25 (`mvn -o test -Dtest=...`); Docker Desktop must be running
for the Testcontainers tests (`docker info` first).

---

## Phase 1: Setup

- [X] T001 Verify the prerequisites before writing code; if anything is missing or differs, STOP and report which, do not stub it. Confirm in `backend/src/main/java/com/hls/`: `leave/internal/LeaveRequestService.java` (`submit`, `evaluate`, `cancelOwn`), `leave/internal/LeaveDecisionService.java` (`approve`, `revoke`), `leave/internal/LeaveScope.java`, `attendance/internal/LeaveAttendanceImpl.java` (`problems`, `apply`, `remove`), `substitution/internal/SubstitutionService.java` (`cover`, `assign`, `release`), `substitution/internal/AbsenceListener.java`, `tasks/internal/TaskService.java` (`complete`, `verify`, `completeLinked`), `tasks/internal/TaskRecorder.java`, `recruitment/marketing/internal/ActivityService.java` (`plan`, `complete`), `payroll/internal/UnpaidDayClassifier.java` and `PayRulesImpl.teacherUnpaid`, and the business-date helper used as `BusinessDate` (`today()`). Record the exact names found in `specs/040-same-day-flexibility/research.md` as section D11. Also confirm the seeded grants in `identity/permissions/PermissionMatrixService.java`: Approve on `LEAVE_MANAGEMENT` for Admin, Director and Manager; Create on `SUBSTITUTION`, `TASKS` and the marketing module for the roles named in the spec role table. If a grant is missing, stop and report; do not add a permission key
- [X] T002 Settle research D8 point 3 (does a paid LEAVE-category status exist?). Read the seeded status codes in `backend/src/main/resources/db/migration/` (the attendance status-code seed) and `attendance/api` `StatusCategory`. List every code whose category is `LEAVE`. If the only codes are Leave (L) and Absent (A), note "no paid LEAVE-category code" in research.md D1 and continue. If any other LEAVE-category code exists, STOP and ask which codes are to be deducted before T014
- [X] T003 Settle research D8 points 1 and 2 by recording the decisions already assumed by the plan in research.md D4 and D7 under a heading "Decided": (1) a same-day leave replaces a supervisor-set **Absent** mark and is refused for any other supervisor-set non-L mark; (2) a same-day observation is a task record that satisfies the open due, without the spec 026 ratings form. If the user has said otherwise, follow the user and update plan.md and contracts first

---

## Phase 2: Foundational (blocks every story)

- [X] T004 Create `backend/src/main/resources/db/migration/V150__same_day_flags.sql` exactly per data-model.md: `ALTER TABLE leave_request ADD COLUMN same_day boolean NOT NULL DEFAULT false, ADD COLUMN linked_request_id uuid NULL REFERENCES leave_request(id)`; `ALTER TABLE substitution_requirement ADD COLUMN same_day boolean NOT NULL DEFAULT false`; the same `same_day` column on `task` and `marketing_activity`; and `ALTER TABLE task ADD COLUMN client_key uuid NULL` and the same on `marketing_activity`, each with a unique index `WHERE client_key IS NOT NULL`; and a partial index `WHERE same_day` on each (`leave_request(first_date)`, `substitution_requirement(duty_date)`, `task(due_on)`, `marketing_activity(activity_date)`). Confirm V150 is still the next free number (`ls backend/src/main/resources/db/migration | sort -V | tail -2`; V140 was highest on 2026-10-28)
- [X] T005 [P] Add the field `sameDay` (boolean, default false) and `linkedRequestId` (UUID, nullable) with getters to `backend/src/main/java/com/hls/leave/internal/LeaveRequest.java`; add a static factory `approvedSameDay(...)` that builds a request already in `APPROVED` with `decided_by_user_id` = the recorder, `decided_at` = now, `decision_note` = "same-day, auto-accepted", `sameDay` = true (spec 009 data-model: `decision_note` max 500)
- [X] T006 [P] Add `sameDay` (boolean, default false) with getter and a setter used only by the services to `backend/src/main/java/com/hls/substitution/internal/SubstitutionRequirement.java`, `backend/src/main/java/com/hls/tasks/internal/Task.java` and `backend/src/main/java/com/hls/recruitment/marketing/internal/MarketingActivity.java` (find the exact entity file name first; table `marketing_activity`)
- [X] T007 [P] Add a small helper `backend/src/main/java/com/hls/audit/api/SameDayDetail.java` (public) that builds the audit detail text `sameDay=true; recordedBy=<userId>; for=<personId>; auto-accepted` so every module writes the identical string; add a unit test `backend/src/test/java/com/hls/audit/SameDayDetailTest.java`

**Checkpoint**: migration applies on a fresh database and on the dev database; existing tests still pass (`mvn -o test -Dtest=NavigationSectionOrderTest` is a quick smoke).

---

## Phase 3: User Story 1 - Same-day leave takes effect at once, and every leave day is deducted (Priority: P1) MVP

**Goal**: A Teacher, or a Manager, Admin or Director for a Teacher in scope, records leave for today and it is Approved at once; every Teacher leave day is unpaid at the payroll run.

**Independent Test**: As a Teacher apply for leave dated today and see the day marked L with no pending step; run payroll for a month with a full and a half leave day of different types and see 1.5 days deducted.

### Tests first

- [X] T008 [P] [US1] Pure test `backend/src/test/java/com/hls/payroll/UnpaidDayClassifierTest.java` (extend if it exists): a LEAVE-category mark backed by an approved Casual request is unpaid (1 for a full day, the policy half-day fraction for a 0.50 mark); a mark backed by a Loss-of-Pay request is unpaid; an unbacked LEAVE mark is unpaid; a non-LEAVE category is never unpaid; a date outside the payable dates is never unpaid; two marks on the same date count once. Must fail before T014
- [ ] T009 [P] [US1] Integration test `backend/src/test/java/com/hls/payroll/TeacherLeaveDeductionTest.java` (hand-computed, SC-003): a Teacher with one approved full Casual day and one approved half Sick day and one Absent mark replaced by same-day leave in a month; `PayRules.unpaidDays` returns three entries and the total equals the hand-computed figure from the month's pay policy; a Manager's `unpaidDays` is unchanged by this change
- [X] T010 [P] [US1] Integration test `backend/src/test/java/com/hls/leave/SameDayLeaveApiTest.java`, per role per scope: Teacher own today -> `201`, `status APPROVED`, `sameDay true`, day shows L, Manager notified, substitution requirement raised; Teacher for tomorrow stays `PENDING` and `sameDay false`; recorder `POST /api/v1/leave/same-day` as Manager (assigned Teacher -> 201 with `recordedBy`; unassigned Teacher -> 404), Admin and Director (any Teacher -> 201), Teacher and System (-> 403); `firstDate` not today -> 400; locked month, non-working day, overlap, a Present mark set by a supervisor -> 409 with the reason; an Absent mark set by a supervisor -> 201, the old value is in the mark history, and the audit event detail contains `sameDay=true` and `auto-accepted`; any leave type is accepted; half day raises a 0.50 requirement; the leave view has no balance field and nothing is deducted from a balance (FR-006)
- [X] T011 [P] [US1] Integration test `backend/src/test/java/com/hls/leave/SameDayLeaveSplitTest.java` (FR-005): a range starting today and ending in two days -> today's part `APPROVED sameDay`, later part `PENDING` with `linkedRequestId`, each view shows `otherPart`; rejecting the later part leaves today's leave; `halfDayStart` lands on today's part and `halfDayEnd` on the later part; a range starting yesterday is not same-day
- [X] T012 [P] [US1] Integration test `backend/src/test/java/com/hls/leave/SameDayLeaveConcurrencyTest.java` (SC-006): two simultaneous recordings (Teacher and Manager) for the same Teacher and date -> exactly one 201 and one 409 "already exists", over 20 trials; no Pending orphan part is left when the first part fails
- [X] T013 [P] [US1] Integration test `backend/src/test/java/com/hls/leave/SameDayLeaveCancelTest.java` (FR-016): the Teacher, the recorder and an in-scope supervisor can cancel a same-day request on the same business day (marks removed, substitution requirement cancelled by `AbsenceListener`, a Manager cover entry ended, history kept); the day after, the existing "already started" rule applies; a non-same-day approved request keeps the existing rule

### Implementation

- [X] T014 [US1] In `backend/src/main/java/com/hls/payroll/internal/UnpaidDayClassifier.java` remove the `.filter(m -> m.leaveRequestId() == null || lossOfPayRequests.contains(...))` line so every LEAVE-category mark on a payable date is unpaid; keep the method signature working for callers by leaving `lossOfPayRequests` as an ignored parameter (mark it deprecated in the javadoc) and update the class javadoc to cite spec 040 FR-003. In `PayRulesImpl.teacherUnpaid` stop calling `leaveTypes.lossOfPayRequests` and pass `Set.of()`. Do not touch the Manager path. Update the javadoc of spec 013a FR-012 reference accordingly. Makes T008 and T009 pass
- [X] T015 [US1] In `backend/src/main/java/com/hls/attendance/api/LeaveAttendance.java` change `problems(UUID teacherId, List<LeaveDay> days)` to also take `boolean replaceAbsent` (keep the old two-argument method as a default method calling it with `false`); in `attendance/internal/LeaveAttendanceImpl.java` when `replaceAbsent` is true do not report a supervisor-set mark whose status code is the Absent code (`codes.requireByShortCode("A")`); thread the same flag through `apply(...)`. Other supervisor-set non-L marks still produce "days set by a supervisor: ..."
- [X] T016 [US1] In `backend/src/main/java/com/hls/leave/internal/LeaveRequestService.java` add the same-day path to `submit`: after `evaluate` succeeds, if `draft.firstDate()` equals `attendance.businessToday()` call a new private `submitSameDay(teacherId, draft, evaluation, recorderUserId)`; it re-evaluates with `replaceAbsent = true`, builds today's part with `LeaveRequest.approvedSameDay`, inserts it first with `saveAndFlush` (same `DataIntegrityViolationException | PessimisticLockingFailureException` handling as today, message "Same-day leave already exists for this date."), calls `attendance.apply(request.getId(), recorder, teacherId, todayDays, leaveTypes.isLossOfPay(...))`, writes `LeaveAudit` with `SameDayDetail`, publishes `LeaveRequested` and `LeaveDecided` (Approved) events, and when `lastDate` is after today creates the later part as a normal Pending request carrying `linkedRequestId` of today's part (research D2). Reason max `MAX_REASON` = 500, unchanged
- [X] T017 [US1] Create `backend/src/main/java/com/hls/leave/internal/SameDayLeaveService.java`: `record(UUID actor, Set<Role> roles, RecordRequest request)` where `RecordRequest(UUID teacherId, UUID leaveTypeId, LocalDate firstDate, LocalDate lastDate, boolean halfDayStart, boolean halfDayEnd, String reason)`; checks scope with `LeaveScope` (404 outside scope), rejects `firstDate != today` with `InvalidInputException("Same-day leave must start today.")`, then delegates to the same private path as T016 with the recorder as actor. Return `LeaveViewFactory.LeaveView` with `Perspective.SUPERVISOR`
- [X] T018 [US1] In `backend/src/main/java/com/hls/leave/web/LeaveManagementController.java` add `POST /api/v1/leave/same-day` (Approve on `LEAVE_MANAGEMENT` via `PermissionGuard`; the module has only View and Approve, so no new action or seed, body `RecordRequest`, `201`); add `sameDay` (nullable Boolean) query parameter to the list and a matching repository filter in `LeaveRequestRepository`; in `LeaveViewFactory` add `sameDay`, `recordedBy {userId, name}` and `otherPart {id, firstDate, lastDate, status}` to `LeaveView`
- [X] T019 [US1] Relax cancellation for same-day requests (data-model "State and rules"): in `LeaveRequestService.cancelOwn` and `LeaveDecisionService.revoke` allow cancelling an `APPROVED` request with `sameDay = true` when `firstDate` equals today, by the Teacher or the recorder (a supervisor already uses the existing `revoke` path, which only needs its same-day guard relaxed in `LeaveDecisionService`); otherwise keep the existing refusal. Makes T013 pass
- [X] T020 [P] [US1] Extend `POST /api/v1/me/leave/preview` in `backend/src/main/java/com/hls/leave/web/MyLeaveController.java` and `LeaveRequestService.Preview` with `takesEffectAtOnce` and `parts [{from, to, outcome}]` (contract section 1)
- [X] T021 [P] [US1] Frontend: in `frontend/src/features/leave/leaveApi.ts` add `sameDay`, `recordedBy`, `otherPart` to the view type, `takesEffectAtOnce`/`parts` to the preview type, and `recordSameDayLeave(...)`; in `ApplyLeavePage.tsx` show "Takes effect at once, no approval needed" when the preview says so and the two-part notice for a range past today
- [X] T022 [P] [US1] Frontend: create `frontend/src/features/leave/RecordSameDayLeaveDialog.tsx` (Teacher picker limited to the Teachers the list endpoint returns, leave type, today fixed and read-only, half-day, reason max 500) opened from a "Record same-day leave" button in `LeaveManagementPage.tsx`, shown only when Create is granted; add a "Same-day" chip (text, not colour only) in `LeaveStatusChip.tsx` and a Same-day filter in the page
- [X] T023 [P] [US1] Frontend tests: `frontend/src/features/leave/RecordSameDayLeaveDialog.test.tsx`, additions to `ApplyLeavePage.test.tsx` and `LeaveManagementPage.test.tsx` (button hidden without Create, chip shown, filter sends `sameDay=true`), and add the dialog to `frontend/src/a11y/a11y.test.tsx` in both themes

**Checkpoint**: US1 works end to end; quickstart steps 1-4 and 9 pass.

---

## Phase 4: User Story 2 - A Zone Manager's cover is a real substitution entry (Priority: P1)

**Goal**: "I covered" is recorded with no prior step, kept as a Manager cover entry, listed apart from Teacher credits.

**Independent Test**: Take the requirement raised by today's leave, record cover as the Zone Manager, see it in the Manager covers list and not in any Teacher credit.

- [X] T024 [P] [US2] Integration test `backend/src/test/java/com/hls/substitution/ManagerCoverApiTest.java`: cover from `OPEN` after the class (no assignment) -> `MANAGER_COVER`, `sameDay true` when recorded on the duty date and `false` for an earlier date; the entry appears in `GET /api/v1/substitutions/manager-covers` with label "Manager cover", `state ACTIVE`; a Teacher substitute assigned later -> entry `ENDED` with `endedBy TEACHER_ASSIGNED`, `sameDay` cleared, history kept, the Teacher gets the credit; release -> `ENDED/RELEASED`; the absence withdrawn -> `ENDED/ABSENCE_WITHDRAWN`; Teacher credit endpoints never include the cover; Admin and Director cover resolves to the School's Manager; a School with no Manager shows "no Manager recorded"; a Manager outside scope -> 404; half day cover is 0.5; "School informed" still asked for; a Teacher who is already assigned as a substitute elsewhere today and then goes on same-day leave is flagged "substitute now unavailable" (spec 017a, edge case)
- [X] T025 [US2] In `backend/src/main/java/com/hls/substitution/internal/SubstitutionService.java` `cover(...)`: set `r.setSameDay(duty date equals BusinessDate.today())`, put `sameDay=true` through `SameDayDetail` in `audit.status`, and clear `sameDay` in `assign`, `release` and in `AbsenceListener.onWithdrawn`; no change to the status rules (research D6)
- [X] T026 [US2] Add `managerCovers(UUID userId, Set<Role> roles, CoverFilter filter, int page, int size)` to `SubstitutionService` reading requirements ever in `MANAGER_COVER` (join the `substitution_history` `COVERED` rows so ended entries are included), scoped by `SubstitutionScope`; add `GET /api/v1/substitutions/manager-covers` to `backend/src/main/java/com/hls/substitution/web/SubstitutionController.java` (View on `SUBSTITUTION`) with the item shape in contracts section 2; add the `sameDay` filter to `GET /api/v1/substitutions`
- [X] T027 [P] [US2] Frontend: in `frontend/src/features/substitution/` add the "Manager cover" list (new tab or section in the existing page, route and menu item unchanged), the "I covered" action available from an open requirement for today with no assignment step, the "Same-day" chip and filter; hide actions without Create; add tests and the page to `a11y.test.tsx`

**Checkpoint**: quickstart step 5 passes.

---

## Phase 5: User Story 3 - Log a task or visit for today on the spot, including offline (Priority: P1)

**Goal**: A Manager logs own work for today and it is final at once; works from the phone offline.

**Independent Test**: As a Manager log a completed School visit for today with a note and see it in Done and the activity calendar, with nothing awaiting verification.

### Tests first

- [X] T028 [P] [US3] Integration test `backend/src/test/java/com/hls/tasks/SameDayTaskApiTest.java`: `POST /api/v1/tasks/same-day` as Manager `IN_PROGRESS` -> 201 `sameDay true`; `COMPLETED` with a 10-character note -> stored `VERIFIED`, three history rows by the Manager, absent from the verification queue; `COMPLETED` with a 9-character note -> 400 ("completion note 10 to 2000 characters"); Admin and Director -> 403; body with an assignee or date field is ignored/refused; every `TaskType` accepted; OBSERVATION linked to a Teacher clears that Teacher's open observation due (`completeLinked`); `POST /api/v1/tasks/{id}/done-on-the-spot` by the assignee on a task due today -> `VERIFIED`, original assigner in history; not the assignee -> 404; not due today or not open -> 409; another Manager's scope -> 404; a linked School or Teacher outside the Manager's scope -> 404 (edge case); 50 same-day items in one day are all accepted (FR-015, no cap)
- [X] T029 [P] [US3] Integration test `backend/src/test/java/com/hls/recruitment/marketing/SameDayVisitApiTest.java`: `POST /api/v1/marketing/activities/same-day` creates a `COMPLETED` activity dated today with `sameDay true`; a School outside the Manager's scope -> 404; outcome and notes required as in `ActivityService.complete`; appears in `GET /api/v1/activity-calendar` with `sameDay`; Admin and Director may not log for another Manager
- [X] T030 [P] [US3] Integration test `backend/src/test/java/com/hls/tasks/SameDayOfflineReplayTest.java` (contract section 6): with `Idempotency-Key` and `capturedOn` = yesterday within 7 days -> accepted, activity date is `capturedOn`, `sameDay true`; same key twice -> one record, same response; `capturedOn` in the future or more than 7 days old -> 409 `DATE_NOT_ALLOWED`; `capturedOn` without the header -> ignored (today)

### Implementation

- [X] T031 [US3] In `backend/src/main/java/com/hls/tasks/internal/TaskService.java` add `logSameDay(Caller caller, SameDayRequest request)` and `markDoneOnTheSpot(Caller caller, UUID id, String note)` per research D7: assignee = `caller.userId()` (Manager role required, else `ForbiddenFieldException`), `dueOn = businessDate.today()`, source `"SAME_DAY"`, `sameDay = true`; `COMPLETED` writes `CREATED`, `COMPLETED`, `VERIFIED` history through `TaskRecorder.change(...)` with note "same-day, auto-accepted"; completion note validated with the existing `required(note, 10, 2000, "The completion note")`; OBSERVATION with `linkedType`/`linkedId` also calls `completeLinked(TaskType.OBSERVATION, ...)` (FR-012). The existing `create`, `complete` and `verify` paths are unchanged
- [X] T032 [US3] Add to `backend/src/main/java/com/hls/tasks/web/TaskController.java` `POST /api/v1/tasks/same-day` (Create on `TASKS`) and `POST /api/v1/tasks/{id}/done-on-the-spot` (Edit on `TASKS`), the `sameDay` list filter and `sameDay` in the list item; make sure the verification queue query still selects `COMPLETED` only
- [X] T033 [US3] In `backend/src/main/java/com/hls/recruitment/marketing/internal/ActivityService.java` add `logSameDay(UUID actor, Set<Role> roles, SameDayRequest request)` creating the activity directly `COMPLETED` for today with `sameDay = true`, reusing the target-scope check and the `changes.record(...)` audit call used by `plan` and `complete`; add `POST /api/v1/marketing/activities/same-day` and the `sameDay` filter to `ActivityController`; add `sameDay` to `ActivityEntry` (module `recruitment/api`) and to `CalendarEntry` in `activitycalendar/internal/` so `VisitSource` passes it through
- [X] T034 [US3] Offline replay (contract section 6): in `TaskController` and `ActivityController` read the optional `Idempotency-Key` header and body `capturedOn`. Store the key in the new `client_key` column of `task` / `marketing_activity` (T004); a repeated key returns the existing record and creates nothing. Validate `capturedOn`: not in the future and not more than 7 days before the business date, else `409 DATE_NOT_ALLOWED`; without the header ignore `capturedOn`. Each module de-duplicates its own rows, so no shared store and no module boundary is crossed. Makes T030 pass
- [X] T035 [P] [US3] Frontend: `frontend/src/features/tasks/LogSameDayDialog.tsx` (type, title, description, link, place, status In progress or Completed, completion note 10-2000), a "Log for today" button and a "Done on the spot" action on a task due today in the tasks page; a "Log a visit for today" dialog in `frontend/src/features/marketing/`; Same-day chip and filter in the tasks, visits and `frontend/src/features/activityCalendar/` pages; hide controls without Create or Edit; tests and `a11y.test.tsx` entries
- [X] T036 [P] [US3] Mobile: add a "Log for today" mode to `mobile/src/tasks/AddTaskForm.tsx` and `mobile/src/visits/PlanVisitForm.tsx` (task: In progress or Completed; visit: Completed only, per research D7); add same-day task and visit steps to `mobile/src/outbox/syncSteps.ts` that send the idempotency key and the device business date as `capturedOn`; show queued state like the expense outbox
- [X] T037 [P] [US3] Mobile tests `mobile/__tests__/sameDayTask.test.tsx`, `sameDayVisit.test.tsx`, `sameDayOutbox.test.ts`: online log, offline log then sync once, retry sends the same key, `DATE_NOT_ALLOWED` shows the message and keeps the item for the user, controls hidden when the user may not create

**Checkpoint**: quickstart steps 6 and 7 pass.

---

## Phase 6: User Story 4 - Same-day items in audit and reports (Priority: P2)

**Goal**: Admin and Director find every same-day item and see counts per Manager and School.

**Independent Test**: Record one of each; filter each list and open the report; they match.

- [ ] T038 [P] [US4] Integration test `backend/src/test/java/com/hls/reporting/SameDayReportApiTest.java`: `GET /api/v1/reports/same-day?month=` returns per-Manager and per-School counts for leave days, covers (half day 0.5), tasks and visits after seeding one of each plus some normal items; a Manager sees assigned Managers and Schools only; a section the caller cannot View is omitted; Teacher and System -> 403; zone filter applies; fixed number of queries (statement-count assertion, one per module)
- [ ] T039 [US4] Create public interface `SameDayCounts` in each of `backend/src/main/java/com/hls/leave/api/`, `substitution/api/`, `tasks/api/`, `recruitment/api/` (`countsByManager(YearMonth, zoneId, scope)` and `countsBySchool(...)` returning `(id, name, count)`), implemented in each module's `internal` package over its own repository (indexes from T004); no module reads another module's tables
- [ ] T040 [US4] Create `backend/src/main/java/com/hls/reporting/web/SameDayReportController.java` and `reporting/internal/SameDayReportService.java` composing the four interfaces by the caller's View permission and scope (Principle III), shape per contracts section 5
- [ ] T041 [P] [US4] Verify audit (FR-014): add `backend/src/test/java/com/hls/audit/SameDayAuditTest.java` asserting that for one same-day leave, cover, task and visit the Change History search returns an entry whose detail contains `sameDay=true`, the recorder, the person and `auto-accepted`, and that no entry was edited
- [ ] T042 [P] [US4] Frontend: `frontend/src/features/dashboard/SameDayReport.tsx` (month picker, Zone filter, two tables per section; tables scroll inside their container at 375 px), reached from a link on the Dashboard page and from the Same-day filter bar of each list (no new menu item), with tests and an `a11y.test.tsx` entry
- [ ] T043 [P] [US4] Confirm the `sameDay` filter exists and is tested on all five lists (leave T018, substitutions T026, tasks T032, visits and calendar T033); add the missing test cases to the existing controller tests if any is not yet covered

**Checkpoint**: quickstart step 8 passes.

---

## Phase 7: Polish & Cross-Cutting

- [ ] T044 Run the architecture and access tests: `mvn -o test -Dtest='ArchitectureTest,*AccessModel*Test,NavigationSectionOrderTest'`; the access model must be unchanged (no new menu item or key)
- [ ] T045 Regression checks from quickstart step 10 as tests or manual steps: tomorrow's leave still Pending; an Admin-assigned task still needs Acknowledge, Complete, Verify; Manager attendance unpaid days unchanged; run the existing leave, payroll, substitution, tasks and marketing test classes one at a time
- [ ] T046 [P] Update the earlier specs with a dated "Amended by spec 040" note in `specs/009-leave/spec.md`, `specs/013a-*/spec.md`, `specs/013b-*/spec.md`, `specs/017a-substitution/spec.md`, `specs/025-manager-tasks/spec.md`, `specs/026-teacher-observation-performance/spec.md` stating the changed rule (Teacher leave days always unpaid; Absent replaceable; Manager cover list; same-day task and visit), and update the leave-type description shown in settings or help text that says Loss-of-Pay decides pay (search the frontend for "Loss of Pay")
- [ ] T047 [P] Add row 040 to `docs/spec-roadmap.md`
- [ ] T048 Run the full frontend (`npx vitest run` in `frontend/`) and mobile (`npx jest` in `mobile/`) suites and the backend classes added here; fix failures
- [ ] T049 Walk `specs/040-same-day-flexibility/quickstart.md` steps 1-10 on the local stack and record the results in `specs/040-same-day-flexibility/quickstart-results.md`; leave the device and offline step (7) as a manual item if no phone is available

---

## Dependencies & Order

- Phase 1 -> Phase 2 -> stories. (Analyze remediation of 2026-10-28 applied: Approve for recording leave, 7-day offline window, `client_key` for idempotency.) T002 must finish before T014; T003 before T015.
- US1 is the MVP and carries the pay change. US2 needs US1's leave path only for its test data (it can use the substitution test seed instead). US3 is independent of US1 and US2 after Phase 2. US4 needs the flags and filters from US1-US3.
- Within a story: tests first and failing, then entities/services, then controllers, then frontend/mobile.
- Tests T008-T013 can run in parallel with each other; T021-T023 in parallel after T018; T035-T037 in parallel after T032-T034.

## Parallel Example: US1

```text
T008 UnpaidDayClassifierTest + T009 TeacherLeaveDeductionTest + T010 SameDayLeaveApiTest + T011 + T012 + T013   # all [P]
then T014 (classifier), T015 (attendance), T016 -> T017 -> T018 -> T019
then T020, T021, T022, T023 in parallel
```

## Implementation Strategy

1. MVP: Phases 1-3 (same-day leave plus the pay rule). Ship behind no flag: the pay change applies at the next payroll run,
   so announce it with the release and run T009 against the month in progress before merging.
2. Then US2 (small), US3 (largest, includes mobile), US4, Polish.
3. Each story ends with its quickstart steps passing before the next starts.
