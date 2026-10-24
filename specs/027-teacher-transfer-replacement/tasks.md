---

description: "Task list for feature implementation"
---

# Tasks: Teacher Transfer and Replacement

**Input**: Design documents from `/specs/027-teacher-transfer-replacement/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/transfer-api.md, contracts/module-interfaces.md,
quickstart.md, and specs 005, 008, 009, 010, 012, 013a and 017a implemented and merged (they are on `main`).

**Defaults used for the plan's open items** (no user answer was given; the plan's stated defaults apply, revisit before the
named task if the answer differs):

1. Outgoing Teacher of a replacement with no destination, with approved leave after the date: those leave days stay with the
   old School (not re-tagged, not removed). Affects T041.
2. A direct placement made after approval stays unrestricted. If it cancels or replaces a scheduled move created by an Approved
   request, the request is closed as Cancelled with "Teacher no longer placed there". The existing placement action is NOT
   changed (SC-007). Affects T062.
3. No call to spec 026 `PerformanceReferrals.reportDecision` from approve. A later task can add it once 026 is built.
4. Migration number V80 was used (main was at V60; 035 took V70).
   T005 re-checks the next free number against merged branches and renumbers every reference to it in this file.

**Tests**: included as first-class tasks. Constitution Principle IX requires per-role API tests on every endpoint, unit tests for
rules and status derivation, integration tests for the day-by-day invariants (SC-001, SC-002) and UI tests per role. Tests are
written before the code they cover and must fail first.

**Organization**: grouped by user story (spec.md US1-US5). Order follows dependencies: Setup, Foundation (migration,
permission, scope, `PlacementMoves` check and apply core), US1 (transfer), US2 (replacement), US3 (attendance, leave and
substitution across the date), US4 (history), US5 (list and notifications), Polish.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: US1-US5; absent for Setup, Foundational and Polish

## Path Conventions

Backend `backend/src/main/java/com/hls/transfer/{api,internal,web}/...`, tests under `backend/src/test/java/com/hls/transfer/...`;
interface additions under `backend/src/main/java/com/hls/{schoolbilling,teacher,attendance,substitution}/api` with their
implementations in each module's `internal`; migration `backend/src/main/resources/db/migration`; frontend
`frontend/src/features/transfers/...` and `frontend/src/features/teachers/...`. Run one Maven test class at a time
(`mvn -o test -Dtest=...`); Docker must be running for integration tests.

---

## Phase 1: Setup

- [X] T001 Verify the prerequisites before writing code, and STOP and report if any is missing (do not stub): in `backend/src/main/java/com/hls/` confirm `teacher.api.TeacherPlacementSource` (with `historyOf`), `schoolbilling.internal.AssignmentService` (`assign`, `resolvePosition`, `cancelPending`, `endForExit`) and `SchoolContracts.occupancyOf`, `attendance.api.AttendanceReadApi` (`marksOf`, `isLocked`), `attendance_mark_history`, `substitution` events `SubstitutionNeeded` and `SubstitutionEnded`, `organization.api.ScopeQueries` and `ManagerQueries`, `identity` `ChangeRecorder` and `BusinessDate`, and the current highest migration number. Record the exact names found in `specs/027-teacher-transfer-replacement/research.md` section 1
- [X] T002 Create the package skeletons `backend/src/main/java/com/hls/transfer/{api,internal,web}/` with `package-info.java`, and `backend/src/test/java/com/hls/transfer/TransferModuleRulesTest.java` (ArchUnit: `transfer` uses only the `api` packages of `teacher`, `schoolbilling`, `school`, `organization`, `attendance`, `substitution`, `identity`, `audit`; nothing outside `transfer` uses `transfer.internal` or `transfer.web`; only `notification` listens to `transfer.api` events; no module reads the `movement_*` tables; `teacher` reaches movement data only through `teacher.api.TeacherMovementSource`)
- [X] T003 [P] Create `frontend/src/features/transfers/transfersApi.ts` with types and calls for every endpoint in contracts/transfer-api.md (dates as ISO strings on the wire, `version` on every write, request object with `actions` and `checks`, movement line)

---

## Phase 2: Foundational (blocking prerequisites)

- [X] T004 [P] Add the permission module `TEACHER_TRANSFERS(VIEW, CREATE, EDIT, APPROVE)` to `identity/permissions/PermissionModule.java`; in `PermissionEligibility` make APPROVE ineligible for Admin and make every action ineligible for Teacher and System; seed in the permission matrix service: Admin gets View, Create, Edit; Director and Manager get View, Create, Edit, Approve
- [X] T005 Write `backend/src/main/resources/db/migration/V56__create_teacher_movement.sql` per data-model.md (first re-check the next free number against merged branches and open pull requests, and renumber this file and every reference). No foreign key crosses a module; every table has `id uuid` primary key. `movement_request`: kind text check in ('TRANSFER','REPLACEMENT'); teacher_id, from_school_id, to_school_id uuid not null; position_id uuid null; replacement_teacher_id uuid null; outgoing_destination_school_id uuid null; effective_on date; reason_category text check in ('SCHOOL_REQUEST','TEACHER_REQUEST','MANAGER_REBALANCING','AFTER_REVIEW','OTHER'); note text (up to 1000 characters; required for OTHER and for SCHOOL_REQUEST); status text check in ('PENDING','APPROVED','REJECTED','CANCELLED'); requested_by, requested_at; decided_by, decided_at, decision_reason (reason required for reject and cancel); applied_notified_at timestamptz; version bigint; created_at, updated_at; checks: `kind = 'REPLACEMENT'` implies `replacement_teacher_id` is not null and `to_school_id = from_school_id`; `kind = 'TRANSFER'` implies `replacement_teacher_id` and `outgoing_destination_school_id` are null and `to_school_id <> from_school_id`; `decided_by <> requested_by` when status is APPROVED or REJECTED; indexes (status, effective_on), (from_school_id), (to_school_id), (teacher_id). `movement_participant`: request_id, teacher_id, role check in ('MOVING','OUTGOING','REPLACEMENT'), open boolean; partial unique index on (teacher_id) where open. `movement_assignment`: request_id, assignment_id (plain uuid of `contract_assignment.id`), teacher_id, effect check in ('ENDED','CREATED'); unique on (request_id, assignment_id); index on assignment_id. `movement_request_history`: request_id, action check in ('RAISED','CHANGED','APPROVED','REJECTED','CANCELLED','APPLIED','CLOSED_STALE'), from_status, to_status, actor_user_id (null for the daily job), note, at; trigger refusing UPDATE and DELETE (as `performance_history` in 026). `contract_assignment` is not changed
- [X] T006 [P] Add navigation to `identity/accessmodel/NavigationCatalog.java`: OPERATIONS "Transfers & Replacements" `/operations/transfers` (`TEACHER_TRANSFERS` VIEW) for Admin, Director and Manager; add the data scope rules (org-wide for Admin and Director, assigned for Manager) in `AccessModelService`
- [X] T007 [P] Extend `PermissionEligibilityTest` and the navigation and access-model tests under `backend/src/test/java/com/hls/identity/` for `TEACHER_TRANSFERS` (APPROVE refused for Admin by the matrix; Teacher and System refused for all four actions; the menu item per role)
- [X] T008 [P] Add the audited entity type `MOVEMENT_REQUEST` to `audit` `AuditVisibility` mapped to `TEACHER_TRANSFERS.VIEW`, plus `backend/src/test/java/com/hls/transfer/TransferAuditMappingTest.java` asserting every entity type this spec writes has a mapping
- [X] T009 [P] `backend/src/test/java/com/hls/transfer/MovementSchemaTest.java`: a REPLACEMENT without `replacement_teacher_id` is refused; a REPLACEMENT with `to_school_id <> from_school_id` is refused; a TRANSFER with `to_school_id = from_school_id`, with a `replacement_teacher_id` or with an `outgoing_destination_school_id` is refused; APPROVED or REJECTED with `decided_by = requested_by` is refused; a second open `movement_participant` row for the same Teacher is refused and a closed one is not; a duplicate `(request_id, assignment_id)` is refused; direct SQL UPDATE and DELETE on `movement_request_history` are refused
- [X] T010 [P] Create the event records in `transfer/api/`: `TransferRequested`, `TransferDecided`, `TransferApplied` with exactly the fields in contracts/module-interfaces.md
- [X] T011 [P] Entities and repositories in `transfer/internal`: `MovementRequest` (with `@Version`), `MovementParticipant`, `MovementAssignment`, `MovementRequestHistory` (insert only); Hibernate only validates the schema
- [X] T012 [P] `transfer/internal/MovementInput.java`: reason and note are trimmed plain text at most 1000 characters; note required for OTHER and SCHOOL_REQUEST; reason required for reject and cancel; one place for the plain-words messages, dates shown DD/MM/YYYY
- [X] T013 [P] `backend/src/test/java/com/hls/transfer/MovementScopeTest.java`, written before the class and failing: Director and Admin see everything; a Manager reads a request when the leaving or the receiving School is in scope, and raises, cancels or decides only for the leaving School; out of scope answers not-found; Teacher and System see nothing
- [X] T014 `transfer/internal/MovementScope.java` using `organization.api.ScopeQueries`: one read filter and one write check shared by lists, details, history panels and the Teacher filter port
- [X] T015 [P] `backend/src/test/java/com/hls/schoolbilling/PlacementMovesCheckTest.java`, written before the class and failing, covering `check(MoveSpec)` for a transfer: Teacher not placed at the School on the date; Teacher in training or exited; effective date not after the outgoing row's `starts_on`; a future ACTIVE row already present; receiving School inactive; no vacant position; a named position occupied; a PER_TEACHER contract without a named position; "MoU pending" with no position is accepted; a valid case returns `ok` with no problems and writes nothing
- [X] T016 Add `schoolbilling/api/PlacementMoves.java` with `MoveSpec`, `MoveCheck`, `AppliedMove` and `VacantPosition` exactly as in contracts/module-interfaces.md, and `schoolbilling/internal/PlacementMovesImpl.java` implementing `check` and `vacantPositions` by reusing the contract lookup and `resolvePosition` rules of `AssignmentService` (same package, no copy of the rules); `check` and `apply` share one code path
- [X] T017 [P] `backend/src/test/java/com/hls/schoolbilling/PlacementMovesApplyTest.java`, written before `apply` and failing: transfer `apply` ends the outgoing row the day before and creates the new row on `effectiveOn`, returns both ids, no day has two Schools or none; any failure (constraint, position taken) is a `ConflictException` and nothing is written; `apply` runs in the caller's transaction (a rollback by the caller undoes it); `reverse` cancels created rows still in the future and restores the open end of the ended row; `reverse` refuses a created row that has started
- [X] T018 Implement `PlacementMovesImpl.apply` (transfer only here; the replacement branch is added in US2) and `reverse` in `schoolbilling/internal/PlacementMovesImpl.java`, relying on the existing exclusion constraints and the existing "try again" translation of lock conflicts
- [X] T019 `transfer/internal/MovementChecks.java` and `backend/src/test/java/com/hls/transfer/MovementChecksTest.java` (test first): the shared rule set for raise, change and approve and the dry run: Teacher ACTIVE or ON_LEAVE with an ACTIVE row at `from_school_id` covering the request date and the effective date; effective date today or later (`BusinessDate`); no future ACTIVE row; receiving School active; delegates the placement rules to `PlacementMoves.check`; one open request per Teacher via `movement_participant`; note rules; decider is not the requester; returns plain-words problems and the `checks` list for the detail response
- [X] T020 `transfer/web/TransferExceptionAdvice.java` mapping to the shared problem format: 400 invalid input, 403 missing grant or forbidden decision, 404 not found or out of scope, 409 rule refused (with `problems: [string]`) or version changed

**Checkpoint**: migration applies; permission and navigation seeded; scope rules and `PlacementMoves.check`, `apply` (transfer) and `reverse` pass their tests; the schema and triggers refuse what they must.

---

## Phase 3: User Story 1 - Request a Transfer and Have It Decided (Priority: P1) MVP

**Goal**: A Manager, Admin or Director raises a transfer; the Director or the leaving School's Manager approves or rejects with a reason; approval records the move for the effective date; a Pending or still-future Approved request can be cancelled.

**Independent Test**: With one placed Teacher and a second School with a vacant position, raise a transfer for next week, approve as the Director, read the Teacher's placements: old School ending the day before, new School starting on the date.

### Tests for User Story 1

- [X] T021 [P] [US1] `backend/src/test/java/com/hls/transfer/MovementServiceTransferTest.java`: raise creates one PENDING request, one participant row (open), one RAISED history row, an audit record, publishes `TransferRequested`, and changes no placement; a second request for the same Teacher is refused; Teacher in training, no vacancy, inactive receiving School are each refused with the reason; reject without a reason is refused and with a reason becomes REJECTED, closes the participant, changes nothing; approve re-runs `MovementChecks`, calls `PlacementMoves.apply`, stores the ended and created assignment ids in `movement_assignment`, sets APPROVED, publishes `TransferDecided`; approve with a stale version is a 409; cancel of PENDING and of a future APPROVED (calls `reverse`) needs a reason; cancel of a started or applied request is refused; change of date, position or note on PENDING is version-checked and writes a CHANGED history row; a Pending request whose date has passed cannot be approved until its date is changed to today or later
- [X] T022 [P] [US1] `backend/src/test/java/com/hls/transfer/MovementControllerTransferTest.java`: per role and per endpoint for `POST /api/transfers/transfers`, `POST /api/transfers/check`, `GET /api/transfers/vacancies`, `PATCH /api/transfers/{id}`, `POST /api/transfers/{id}/approve`, `/reject`, `/cancel`: Admin can raise and cancel but approve returns 403; the requester cannot approve (403); Director and the leaving School's Manager can approve; a Manager outside the leaving School's scope gets 404; a Manager of the receiving School only can read but not decide; Teacher and System get 403; `actions` never offers APPROVE to the requester (SC-004); `check` writes nothing
- [X] T023 [P] [US1] `backend/src/test/java/com/hls/transfer/TransferDayInvariantTest.java` (integration, Testcontainers): over a transfer effective today-plus-one, a month-end date and a year-boundary date, every day around the date has exactly one School for the Teacher, and the receiving School's occupied count rises by one from the date while the leaving School's falls by one (SC-001)

### Implementation for User Story 1

- [X] T024 [US1] `transfer/internal/MovementService.java`: `raiseTransfer`, `changePending`, `approve`, `reject`, `cancel` for kind TRANSFER, all in one transaction each; writes `movement_request`, participant, assignment link and history rows; records before and after values through `ChangeRecorder`; publishes `TransferRequested` and `TransferDecided` from the publisher's transaction; approval re-checks everything, then calls `PlacementMoves.apply`; cancel of an Approved future request calls `PlacementMoves.reverse`
- [X] T025 [US1] `transfer/internal/MovementQueries.java`: request detail (with live `checks` via `MovementChecks`, empty on a final request, the history entries and the server-computed `actions` for the caller), vacancies for the receiving School via `PlacementMoves.vacantPositions` (empty for a "MoU pending" School), and the derived status rule (APPROVED with `effective_on <= today` and every linked row ACTIVE shows APPLIED; a linked row no longer ACTIVE shows CANCELLED with "Teacher no longer placed there")
- [X] T026 [US1] `transfer/web/MovementController.java` endpoints `POST /api/transfers/transfers`, `POST /api/transfers/check` (kind TRANSFER), `GET /api/transfers/vacancies`, `GET /api/transfers/{id}`, `PATCH /api/transfers/{id}`, `POST /api/transfers/{id}/approve`, `/reject`, `/cancel`, each re-checking grant and scope on the server
- [X] T027 [P] [US1] `frontend/src/features/transfers/TransferForm.tsx`: Teacher picker (Teachers placed in the caller's scope), receiving School, position select from `vacancies` (hidden for "MoU pending"), effective date (not before today), reason category, note (required for Other and School-initiated), live problems from `check`, per the design system, light and dark themes, keyboard use, loading, empty and error states, dates DD/MM/YYYY
- [X] T028 [P] [US1] `frontend/src/features/transfers/RequestDetail.tsx`: the request, `checks`, history entries, and buttons only for the `actions` the server sent (approve, reject with required reason, cancel with required reason, change date); stale-version 409 shows "this request changed, reload"
- [X] T029 [P] [US1] `frontend/src/features/transfers/__tests__/TransferForm.test.tsx` and `RequestDetail.test.tsx`: buttons follow `actions` per role (requester never sees Approve; Admin never sees Approve), reason required on reject and cancel, problems shown, form accessible by keyboard

**Checkpoint**: a transfer can be raised, checked, approved, rejected, changed and cancelled end to end; SC-001 holds for transfers.

---

## Phase 4: User Story 2 - Replace a Teacher at a School (Priority: P1)

**Goal**: Replace a placed Teacher with an unplaced one in the same position from the effective date; the outgoing Teacher becomes available or moves to a named School; all or nothing.

**Independent Test**: At an MoU School replace its Teacher with an unplaced Teacher for a future date, approve it, and check the School's filled count on the day before, on the date and the day after: filled each day, never double.

### Tests for User Story 2

- [X] T030 [P] [US2] `backend/src/test/java/com/hls/schoolbilling/PlacementMovesReplacementTest.java`, written before the code and failing: replacement `apply` ends the outgoing row the day before, creates the incoming row on the date with the same `position_id` (null when the School has no positions), and with a destination creates a further row for the outgoing Teacher at that School from the date (subject to a vacant position there); without a destination the outgoing Teacher has no row from the date; a replacement placed elsewhere on the date, with a future row, in training or exited is refused by `check`; any failing step leaves both Teachers unchanged (all or nothing); the School's occupied count is the same on every day around the date; a "MoU pending" School places the replacement with no position
- [X] T031 [P] [US2] `backend/src/test/java/com/hls/transfer/MovementServiceReplacementTest.java`: raise needs a replacement Teacher who is ACTIVE, unplaced on the date and holding no open request (three participant rows when a destination is named only once for the outgoing Teacher); approve applies and links ids; a conflicting MoU re-mapping on the same dates refuses approval with nothing partly applied; reject and cancel as for transfers
- [X] T032 [P] [US2] `backend/src/test/java/com/hls/transfer/MovementControllerReplacementTest.java`: per role and per endpoint for `POST /api/transfers/replacements`, `POST /api/transfers/check` (kind REPLACEMENT) and `GET /api/transfers/candidates`; a placed, in-training or exited Teacher named directly through the API is refused; candidates are ACTIVE, unplaced on the date and have no open request, each with `{id, name, status}`; scope as in US1
- [X] T033 [P] [US2] `backend/src/test/java/com/hls/transfer/ReplacementDayInvariantTest.java` (integration): for a future, month-end and year-boundary date, the School's filled-position count is unchanged on every day around the date and the outgoing Teacher with no destination appears in the available list from the date (SC-001)

### Implementation for User Story 2

- [X] T034 [US2] Extend `schoolbilling/internal/PlacementMovesImpl.java` `check` and `apply` for `Kind.REPLACEMENT` per contracts/module-interfaces.md rules (outgoing row ends the day before; incoming row starts on the date and reuses the outgoing row's `position_id`; optional destination row for the outgoing Teacher; no new status for "available")
- [X] T035 [US2] Extend `transfer/internal/MovementChecks.java` and `MovementService.java` with `raiseReplacement`, replacement participants (MOVING is not used; roles OUTGOING and REPLACEMENT) and the replacement rules (replacement Teacher ACTIVE, no ACTIVE row covering the effective date, no future row, no open participation)
- [X] T036 [US2] `MovementController` endpoints `POST /api/transfers/replacements`, `GET /api/transfers/candidates?schoolId=&on=` and the REPLACEMENT branch of `POST /api/transfers/check`, in `transfer/web/MovementController.java`; candidates query in `transfer/internal/MovementQueries.java`
- [X] T037 [P] [US2] `frontend/src/features/transfers/ReplacementForm.tsx`: School, Teacher to replace, shows the position being filled, candidate picker from `candidates`, effective date, optional destination School for the outgoing Teacher ("available" when none), reason category, note, live problems from `check`; design system, themes, keyboard use, loading, empty and error states
- [X] T038 [P] [US2] `frontend/src/features/transfers/__tests__/ReplacementForm.test.tsx`: candidates list shown, position shown, optional destination, problems shown, controls follow grants

**Checkpoint**: transfers and replacements both work; SC-001 holds for both.

---

## Phase 5: User Story 3 - Placement Rules Hold Across the Move Date (Priority: P1)

**Goal**: Attendance, leave, substitution and payroll see one consistent picture on either side of the date; days before the date are untouched.

**Independent Test**: Approve a transfer for a date; mark the Teacher present the day before (old School) and on the date (new School); approve leave across the date; read the substitution and payroll placement reads and compare.

### Tests for User Story 3

- [X] T039 [P] [US3] `backend/src/test/java/com/hls/attendance/PlacementAttendanceTest.java`, written before the code and failing: `hasNonLeaveMarkOnOrAfter` true for a present or absent mark and false for a leave-only Teacher; `anyMonthLocked` true for the effective month and for later months that hold marks, false otherwise; `retagLeaveMarks` changes only leave-category marks dated on or after `from`, writes one `attendance_mark_history` row (CORRECTED) per mark, never touches an earlier date, throws when any is in a locked month, returns the count
- [X] T040 [P] [US3] `backend/src/test/java/com/hls/substitution/PlacementSubstitutionTest.java`, written before the code and failing: `relocate` moves non-cancelled requirements with `duty_date >= from` to the new School; an ASSIGNED requirement returns to OPEN, its substitute is told through `SubstitutionEnded`, and `SubstitutionNeeded` is published for the new School; with a null School the requirements are cancelled with the reason; earlier dates are untouched; history and audit are written; returns the number changed
- [X] T041 [P] [US3] `backend/src/test/java/com/hls/transfer/MovementServiceAcrossDateTest.java`: raise and approve are refused when the Teacher has a non-leave mark on or after the date or the month is locked (FR-004), with the plain reason and no partial change; on approval leave days from the date carry the new School and earlier leave days stay; for a replacement with no destination the outgoing Teacher's leave days after the date stay with the old School (open item default 1); open substitution requirements are relocated, or cancelled with the reason "Teacher moved" for an outgoing Teacher with no destination; a failure in any step rolls back the placement change too
- [X] T042 [P] [US3] `backend/src/test/java/com/hls/transfer/AcrossDateInvariantTest.java` (integration, Testcontainers): attendance marked the day before carries the old School and on the date the new School; a payroll-style read of the month shows two consecutive spans meeting on the effective date with no gap and no overlap; no mark, leave day or payroll month dated before the effective date changes (SC-002); an approved payroll run and a locked month are never changed (US3 scenarios 6 and 7)

### Implementation for User Story 3

- [X] T043 [P] [US3] Add `attendance/api/PlacementAttendance.java` (`hasNonLeaveMarkOnOrAfter`, `anyMonthLocked`, `retagLeaveMarks` exactly as in contracts/module-interfaces.md) and `attendance/internal/PlacementAttendanceImpl.java` using the attendance module's own repositories and its mark-history writer
- [X] T044 [P] [US3] Add `substitution/api/PlacementSubstitution.java` (`relocate`) and `substitution/internal/PlacementSubstitutionImpl.java` reusing the module's history, audit and `SubstitutionNeeded` and `SubstitutionEnded` publishing
- [X] T045 [US3] Wire both interfaces into `MovementChecks` (non-leave mark and locked month refuse raise, change and approve) and into `MovementService.approve` (same transaction: `PlacementMoves.apply`, then `retagLeaveMarks` to the receiving School for the moving or incoming Teacher, then `PlacementSubstitution.relocate`; for a replacement the outgoing Teacher with no destination gets `relocate(..., null, "Teacher moved")`), and `reverse` handling on cancel of a future Approved request (leave marks and substitution requirements are re-tagged back to the leaving School in the same transaction), in `transfer/internal/MovementChecks.java` and `MovementService.java`
- [X] T046 [US3] Confirm existing behaviour is unchanged (SC-007): run the existing attendance, leave, substitution, `schoolbilling` assignment and payroll test classes one at a time and record in the pull request description that they pass without edits

**Checkpoint**: the day-by-day invariants hold; nothing dated before the effective date changes.

---

## Phase 6: User Story 4 - Movement History for a Teacher and for a School (Priority: P2)

**Goal**: A Teacher's and a School's full movement history in one place, and the Transferred and Replaced filters on the Teacher list.

**Independent Test**: After a transfer and a replacement, open the moved Teacher and the School and read each list; filter the Teacher list by Transferred and by Replaced.

### Tests for User Story 4

- [X] T047 [P] [US4] `backend/src/test/java/com/hls/transfer/MovementHistoryTest.java`: a Teacher with two placements shows both with dates; the first reads "Initial assignment" (kind INITIAL), a row with no request reads "Direct move" (DIRECT), a request-created row shows kind TRANSFER or REPLACEMENT with reason, requester and approver and the other Teacher; the School list shows arrivals and departures with the other Teacher or School named; rejected and cancelled requests are not placement lines; a Manager sees only Teachers and Schools in scope and others are 404
- [X] T048 [P] [US4] `backend/src/test/java/com/hls/transfer/TeacherMovementSourceTest.java` and an addition to the Teacher list API test in `backend/src/test/java/com/hls/teacher/`: `transferredTeacherIds` holds a Teacher with an Approved or Applied transfer or two ACTIVE rows at different Schools; `replacedTeacherIds` holds the outgoing Teacher of an Approved or Applied replacement; `GET /api/teachers?movement=TRANSFERRED|REPLACED` intersects with the caller's scope

### Implementation for User Story 4

- [X] T049 [P] [US4] Add `teacher/api/TeacherMovementSource.java` (`transferredTeacherIds`, `replacedTeacherIds`), the `movement` list filter in `teacher/internal/TeacherService` and `teacher/web/TeacherController` (parameter `movement=TRANSFERRED|REPLACED`, applied inside the caller's scope; absent port means no filter results, so `teacher` still starts without `transfer`)
- [X] T050 [US4] `transfer/internal/TeacherMovementSourceImpl.java` implementing the port from `movement_request` and `TeacherPlacementSource` rows, and the Teacher and School history in `transfer/internal/MovementQueries.java` (placement rows from `TeacherPlacementSource.historyOf` joined to `movement_assignment` and `movement_request`; no placement data copied)
- [X] T051 [US4] `transfer/web/MovementHistoryController.java`: `GET /api/transfers/teachers/{teacherId}/movements` and `GET /api/transfers/schools/{schoolId}/movements`, VIEW grant plus `MovementScope`
- [X] T052 [P] [US4] `frontend/src/features/transfers/MovementHistory.tsx` (a panel taking a Teacher or School id) and mount it on the Teacher and School detail pages and add the Transferred and Replaced filters to the Teacher list in `frontend/src/features/teachers/` (the panel and filters only for roles with the grant)
- [X] T053 [P] [US4] `frontend/src/features/transfers/__tests__/MovementHistory.test.tsx` and a filter test in `frontend/src/features/teachers/__tests__/`: lines render with dates DD/MM/YYYY, hidden without the grant, filters call the API with `movement=`

**Checkpoint**: histories and filters work for every in-scope role.

---

## Phase 7: User Story 5 - Watch the Requests and Know About Them (Priority: P2)

**Goal**: A request list with filters, and notifications when a request is raised, decided and takes effect.

**Independent Test**: Raise two requests, check the deciders' notifications and list, approve one, reject the other, read the requester's notification and the list filters.

### Tests for User Story 5

- [X] T054 [P] [US5] `backend/src/test/java/com/hls/transfer/MovementListTest.java`: `GET /api/transfers` defaults to PENDING and APPROVED; filters by status (including the derived APPLIED), schoolId and from and to dates; paged; only requests in the caller's scope; Teacher and System get 403; Applied shows once the date arrives with no job run
- [X] T055 [P] [US5] `backend/src/test/java/com/hls/notification/TransferEventListenerTest.java`: `TransferRequested` notifies the Director users and the Managers whose scope covers the leaving School, once, excluding the requester; `TransferDecided` notifies the requester and, on approval, the Teachers' own users and the Managers of both Schools; `TransferApplied` notifies the same set; types are `TRANSFER_REQUESTED`, `TRANSFER_DECIDED`, `TRANSFER_APPLIED`
- [X] T056 [P] [US5] `backend/src/test/java/com/hls/transfer/MovementEffectJobTest.java`: for an Approved request whose date has arrived the job writes one APPLIED history row, sets `applied_notified_at` and publishes `TransferApplied` once (a second run does nothing); a late or missed run changes no status; a stale request (a linked row no longer ACTIVE) is closed as CANCELLED with "Teacher no longer placed there" and a CLOSED_STALE history row; the job is sequential and survives one request failing

### Implementation for User Story 5

- [X] T057 [US5] List query in `transfer/internal/MovementQueries.java` and `GET /api/transfers?status=&schoolId=&from=&to=&page=&size=` in `transfer/web/MovementController.java`, using `MovementScope` as the one filter
- [X] T058 [P] [US5] `notification/internal/TransferEventListener.java` and the three message texts in `MessageFactory` for `TRANSFER_REQUESTED`, `TRANSFER_DECIDED`, `TRANSFER_APPLIED`; recipients resolved with `ManagerQueries` and the `teacher` and `identity` APIs; no table change (V50 already widened the type column)
- [X] T059 [US5] `transfer/internal/MovementEffectJob.java`: one small daily job (the existing scheduling pattern) that sends the "took effect" notice and closes stale requests; correctness never depends on it
- [X] T060 [P] [US5] `frontend/src/features/transfers/TransfersPage.tsx` (route `/operations/transfers`): request list with status, School and date-range filters (default Pending and Approved), "Request a transfer" and "Replace a Teacher" buttons only with CREATE, rows open `RequestDetail`; register the route and OPERATIONS menu item to match the navigation catalog
- [X] T061 [P] [US5] `frontend/src/features/transfers/__tests__/TransfersPage.test.tsx`: default filter, status filter, empty and error states, create buttons hidden without CREATE, hidden for Teacher and System

**Checkpoint**: all five stories work independently; notifications and the list are in place.

---

## Phase 8: Polish and Cross-Cutting Concerns

- [X] T062 Stale-request handling for open item default 2: `backend/src/test/java/com/hls/transfer/StaleRequestTest.java` and the matching read in `MovementQueries` prove that a direct placement change after approval which ends or cancels a created row makes the request read as Cancelled "Teacher no longer placed there", and that the existing direct placement action itself is unchanged (SC-007)
- [X] T063 [P] Concurrency test in `backend/src/test/java/com/hls/transfer/MovementConcurrencyTest.java`: two deciders approving at once, one succeeds and the other gets 409; two requests for the same Teacher at once, one succeeds (partial unique index)
- [X] T064 [P] Security tests in `backend/src/test/java/com/hls/transfer/MovementSecurityTest.java`: notes and reasons longer than 1000 characters refused and HTML is escaped on output; no School, Teacher or request identifier leaks across scope (404 not 403); self-decision and Admin decision refused on every decide endpoint
- [X] T065 [P] `transfer/internal/TransferDevSeeder.java`: demo data for the quickstart (Manager A with Schools A1 and A2, Manager B with B1, placed Teachers T1 at A1 and T2 at A2, unplaced active T3, T4 in training, a School with a vacant position), guarded by the same dev-profile switch as the other seeders
- [X] T066 [P] Postman: add a Transfers folder to the collection under `postman/` covering every endpoint of contracts/transfer-api.md with a test per role
- [X] T067 [P] Constitution and roadmap: add the Transfers & Replacements row to the default access matrix and the OPERATIONS navigation in `.specify/memory/constitution.md` with a PATCH version bump (the next patch after whichever of 026 and 027 merges first), and mark the roadmap row for 027 in the roadmap document
- [X] T068 Run `ModulithVerificationTest` and the module-rule ArchUnit tests (`TransferModuleRulesTest` and the existing ones) and fix any boundary violation
- [X] T069 Run the full backend suite for the touched modules one class at a time and the frontend with `npm test -- transfers` and `npm test -- teachers` in `frontend/`; fix failures
- [ ] T070 Walk through `specs/027-teacher-transfer-replacement/quickstart.md` sections 1 to 5 by hand against the dev seeder and record the result in the pull request description

---

## Dependencies and Execution Order

- **Phase 1 (Setup)** has no dependencies; T003 can run any time.
- **Phase 2 (Foundational)** blocks all stories. Inside it: T005 before T009 and T011; T004 before T007; T013 before T014; T015 before T016; T016 before T017 and T018; T011, T012 and T014 before T019; T018 before T024.
- **US1** needs Phase 2. **US2** needs Phase 2 and extends US1's `MovementService` (T035 after T024) and `PlacementMovesImpl` (T034 after T018). **US3** needs US1 (T045 edits the approve path) and can run beside US2 apart from T045, which must follow T035. **US4** needs Phase 2 and one approved request from US1 for its integration data; its backend can start after T024. **US5** needs US1 for events and details; T058 and T059 need T010 and T024.
- **Polish** follows the stories it covers; T062 follows US1 and US3.
- Within a story: tests first (and failing), then interfaces and services, then endpoints, then the frontend.

### Story dependency graph

```
Setup -> Foundation -> US1 -> US2 -> US3 -> Polish
                         \-> US4 -----------^
                         \-> US5 -----------^
```

## Parallel Execution Examples

- Foundation: T004, T006, T008, T010, T011, T012 touch different files and run together after T001 and T002.
- US1: T021, T022, T023 (tests) together, then T027, T028 and T029 (frontend) in parallel with backend T024 to T026 once `transfersApi.ts` (T003) exists.
- US2: T030, T031, T032, T033 together; T037 and T038 beside T034 to T036.
- US3: T039, T040, T041, T042 together; T043 and T044 together (different modules).
- US4 and US5 can be built by two people at once after US1: T049 to T053 and T054 to T061 touch different packages.

## Implementation Strategy

1. **MVP**: Phase 1, Phase 2 and US1 (transfer raise, check, decide, cancel). Stop and validate with the US1 independent test and the quickstart section 1. Because the existing direct move stays, nothing is blocked while later stories are built.
2. Add US2 (replacement), then US3 (attendance, leave and substitution across the date). US1 to US3 are all P1 and should ship together: a transfer approved without US3 would leave future leave days on the old School, so do not enable the Transfers menu in production before US3 is done.
3. Add US4 and US5 (P2) in either order or in parallel.
4. Finish with Polish: boundary checks, concurrency, security, seeder, Postman, constitution row, walkthrough.

## Notes

- Tasks changing existing modules (T016, T018, T034, T043, T044, T049, T058) add one interface each and must not change existing behaviour (SC-007).
- Never write placement tables from `transfer`; always go through `PlacementMoves`.
- Commit after each task or small group; run one Maven test class at a time.
