---

description: "Task list for feature implementation"
---

# Tasks: School Contracts (MoU)

**Input**: Design documents from `/specs/012-school-contracts/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/school-contracts-api.md, quickstart.md, and
**specs 001-005, 008, 009 and 010 implemented and merged** (this feature adds a permission module and navigation to
002's matrix and catalog, takes over the Teacher placement of 005, and must leave attendance (008) and leave (009)
behaving exactly as before).

**Tests**: included as first-class tasks. Constitution Principle IX requires per-role and per-scope tests on every
endpoint and UI tests per role; the spec's invariants (signing details complete and unchangeable, a position holds one
Teacher, no gap on re-mapping, the placement migration, attendance unchanged) are only trustworthy with tests. Rule
unit tests come before the services that use them.

**Organization**: grouped by user story in priority order (spec.md US1-US4). US2 (moving the placement) comes before
US1 because it is the riskiest change and the assignment rows and the `TeacherPlacementSource` interface are what the
contract rules sit on; it is proven before anything is built on it.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: US1-US4; absent for Setup/Foundational/Polish

## Path Conventions

Backend `backend/src/main/java/com/hls/{schoolbilling,teacher,identity}/...`, tests under
`backend/src/test/java/com/hls/...`; migrations `backend/src/main/resources/db/migration`; frontend
`frontend/src/features/{schoolbilling,teachers}/...`. Run one Maven test class at a time
(`mvn -o test -Dtest=...`); the machine is slow.

---

## Phase 1: Setup

- [x] T001 Create the `schoolbilling` module skeleton: `backend/src/main/java/com/hls/schoolbilling/{api,internal,web}/` with `package-info.java` (the `api` package a `@NamedInterface`) and `backend/src/test/java/com/hls/schoolbilling/SchoolBillingModuleRulesTest.java` (ArchUnit: nothing outside `schoolbilling` uses `schoolbilling.internal`; `teacher` does not depend on `schoolbilling`; no module reads contract tables)
- [x] T002 Create `frontend/src/features/schoolbilling/` with `schoolContractsApi.ts` (types and calls for every endpoint in contracts/school-contracts-api.md; money as strings)

---

## Phase 2: Foundational (blocking prerequisites)

- [x] T003 Write `backend/src/main/resources/db/migration/V20__create_school_contract_tables.sql` per data-model.md: `contract` (id uuid PK, school_id uuid not null, state varchar(12) check in ('RATE_PENDING','ACTIVE','CANCELLED'), salary_mode varchar(12) null check in ('SAME_FOR_ALL','PER_TEACHER'), teacher_count int null check between 1 and 500, rate numeric(12,2) null, signed_on date null, cycle varchar(10) not null default 'MONTHLY' check = 'MONTHLY', starts_on date not null, ends_on date null, version bigint not null, created_by, created_at; checks: SAME_FOR_ALL requires rate > 0, PER_TEACHER requires rate null, RATE_PENDING has salary_mode, teacher_count, rate and signed_on all null and any other state has salary_mode, teacher_count and signed_on set; ends_on null or >= starts_on; gist exclusion: no two non-CANCELLED contracts of one school overlap in dates), `contract_position` (id uuid PK, contract_id FK, number int unique per contract, title varchar(80) null, salary numeric(12,2) check > 0), `contract_signatory` (id uuid PK, contract_id FK, party varchar(6) in SCHOOL/HLS, name varchar(120) not blank, designation varchar(120) not blank, user_id uuid null), `contract_assignment` (id uuid PK, teacher_id, school_id, position_id uuid null FK to contract_position, starts_on not null, ends_on null, status varchar(12) in ACTIVE/CANCELLED/CORRECTED, created_by, created_at; `ends_on >= starts_on`; the same gist exclusion as `teacher_placement` so one Teacher has no overlapping ACTIVE rows; a second gist exclusion on (position_id with =, date range with &&) where status = 'ACTIVE' and position_id is not null; indexes on school_id, teacher_id, position_id; no foreign keys to other modules). The same migration inserts one RATE_PENDING contract per School that has placements (starts_on = the School's earliest placement start, ends_on null) and copies every `teacher_placement` row (all statuses, same id, dates and status) into `contract_assignment` with position_id null
- [x] T004 [P] Add permission module `SCHOOL_CONTRACTS(VIEW, CREATE, EDIT)` to `identity/permissions/PermissionModule.java`; add it to `PermissionEligibility` so Teacher and System are excluded; seed defaults in `PermissionMatrixService`/`PermissionMatrixSeeder` per the spec's table (Admin: VIEW, CREATE, EDIT; Director: VIEW, CREATE, EDIT; Manager: VIEW; Teacher and System none)
- [x] T005 [P] Add navigation `OPERATIONS -> School Contracts` (`/operations/school-contracts`, SCHOOL_CONTRACTS VIEW) to `identity/accessmodel/NavigationCatalog.java` and the data-scope entry `ASSIGNED` (Manager) / org-wide (Admin, Director) in `AccessModelService.java`
- [x] T006 [P] Extend `PermissionEligibilityTest`, `NavigationSectionOrderTest` and the access-model tests under `backend/src/test/java/com/hls/identity/` for the module (Teacher and System excluded) and the OPERATIONS item per role
- [x] T007 [P] Add `backend/src/main/java/com/hls/teacher/api/TeacherPlacementSource.java` per contracts/school-contracts-api.md (`spansOverlapping`, `teachersAssignedDuring`, `hasCurrentOrFutureAssignment`, `teacherCountsBySchool`, `assign` with an optional `positionId`, `cancelPending`, `endForExit`), reusing `TeacherDirectory.PlacementSpan`
- [x] T008 Entities and repositories in `schoolbilling/internal`: `Contract`, `ContractPosition`, `ContractSignatory` (no setters, insert-only), `ContractAssignment` (same shape and `isInEffectOn` rule as `TeacherPlacement`, plus `positionId`), `AssignmentStatus`, each with its Spring Data repository; Hibernate only validates the schema

**Checkpoint**: migration applies on a database holding old-shaped placements; permission and navigation seeded; entities validate against the schema.

---

## Phase 3: User Story 2 - Replace the Interim Placement (P1) 🎯 MVP

**Goal**: "which School is this Teacher in" comes from contract assignments, with the same current School, dates and history as before, every "interim" label gone, and attendance and leave unchanged.

**Independent Test**: load old-shaped placements (current, scheduled, ended, cancelled, corrected), run `V20`, and compare Teacher and School screens, `TeacherDirectory` answers and a month of attendance and leave figures before and after: identical; the old table is gone after `V21`.

### Tests for User Story 2

- [x] T009 [US2] `backend/src/test/java/com/hls/schoolbilling/PlacementMigrationTest.java`: build a database at version 19, insert placements of every status for several Teachers and Schools (one School with none), migrate to 20, and assert row count, ids, dates and status equal, one RATE_PENDING contract per School that had placements starting on its earliest placement date and none for the other, and `position_id` null on every copied row
- [x] T010 [P] [US2] Move the placement tests under `backend/src/test/java/com/hls/teacher/` (those exercising `TeacherPlacementService`, the placement endpoints and `TeacherDirectory`) so they run against the new implementation without changing their assertions
- [x] T011 [P] [US2] `backend/src/test/java/com/hls/schoolbilling/AttendanceUnchangedTest.java`: seed a demo month of attendance and approved leave through the placements, snapshot the grid, rollup and leave-preview results, migrate, and assert the same results (SC-001)

### Implementation for User Story 2

- [x] T012 [US2] `schoolbilling/internal/AssignmentService.java`: move the rules of `TeacherPlacementService` unchanged (`place` as `assign`, `cancelPending`, `endForExit`; dated rows, a future date schedules, "current" evaluated against today, rows never overwritten, `ACTIVE`/`CANCELLED`/`CORRECTED`, overlap refused with the same messages), plus the new rule: when the School has no contract covering the start date, create a RATE_PENDING ("MoU pending") contract from that date and assign with no position, so existing seeders, fixtures and flows keep working (the position rules come in US3); with a test that placing a Teacher in a School that has no contract succeeds and creates the pending contract; audit through `ChangeRecorder` with entity `TEACHER_PLACEMENT` and the Teacher as entity id, as before
- [x] T013 [US2] `schoolbilling/internal/TeacherPlacementSourceImpl.java` implementing `TeacherPlacementSource` over `ContractAssignmentRepository` (spans, teachers assigned during, current-or-future check, counts per School, and the three operations through `AssignmentService`)
- [x] T014 [US2] Switch `teacher/internal/{TeacherDirectoryImpl,TeacherSchoolHooks,TeacherService,ManagerTeacherCountEnricher,TeacherScopeService,TeacherDevSeeder}.java` and `teacher/web/TeacherController.java` from `TeacherPlacementRepository`/`TeacherPlacementService` to `TeacherPlacementSource`; the endpoints `POST /api/v1/teachers/{id}/placements` and `DELETE /api/v1/teachers/{id}/placements/pending` keep their paths, bodies and permission (`TEACHERS` `EDIT` with scope)
- [x] T015 [US2] Write `V21__drop_teacher_placement.sql` and delete `teacher/internal/{TeacherPlacement,TeacherPlacementRepository,TeacherPlacementService,PlacementStatus}.java`; update `AttendanceDevSeeder` and any attendance or leave test fixtures that created placements through the old classes
- [x] T016 [P] [US2] Replace the "interim" wording by "assignment" in `teacher/api/TeacherView.java` (field comments and label text) and in `frontend/src/features/teachers/{PlacementDialog,TeachersPage,MyTeacherProfile,teachersApi}.ts(x)` and their tests (`TeachersPage.test.tsx`, `MyTeacherProfile.test.tsx`, `frontend/src/account/ProfilePage.teacher.test.tsx`); add a test that no response or screen text for a Teacher's School contains "interim" (SC-002)
- [x] T017 [US2] Run `ApplicationModulesTest`, `SchoolBillingModuleRulesTest`, `AttendanceUnchangedTest` and the existing teacher, attendance and leave suites one class at a time; fix any fixture that still names the old table

**Checkpoint**: the app behaves as before with the new owner; Zone Manager scope on Teachers is unchanged (spec 005 tests pass).

---

## Phase 4: User Story 1 - Record a School's MoU Contract (P1)

**Goal**: Admin and Director record and replace contracts with positions and signing details.

**Independent Test**: create a 3-position contract at different salaries with signing details, record a new MoU from a later date and see both contracts with their signatories in the history; a Zone Manager can view but not create.

### Tests for User Story 1

- [x] T018 [P] [US1] `backend/src/test/java/com/hls/schoolbilling/ContractServiceTest.java` (rules first): same-for-all creates N positions at the rate; per-Teacher needs exactly N amounts, each positive; count at least 1; signed date required and not in the future; at least one School signatory with name and designation; at least one HLS signatory, each the School's Zone Manager or an active Director (Zone Manager only, Director only, both, and two Directors all save; none is refused); a new contract ends the old one the day before; overlap refused; a School with no Zone Manager refused; a RATE_PENDING contract takes its MoU in place once and then cannot be changed; positions and signatories are never updated; the responsible Manager is read from `ManagerQueries` and never stored
- [x] T019 [P] [US1] `backend/src/test/java/com/hls/schoolbilling/ContractApiTest.java`: per-role and per-scope matrix from contracts/school-contracts-api.md (Admin and Director create; Zone Manager 403 on create, record MoU, end and cancel but views own Schools; Zone Manager B gets 404 for Zone Manager A's School; Teacher and System 403); `signatory-candidates` returns only the School's Zone Manager and active Directors; a `userId` outside that set is a 400
- [x] T020 [P] [US1] `backend/src/test/java/com/hls/schoolbilling/ContractAuditTest.java`: each create, record-MoU, end, cancel writes one audit entry with actor, roles and prior and new values
- [x] T021 [P] [US1] `frontend/src/features/schoolbilling/SchoolContractPage.test.tsx`: the MoU form (count, salary mode, per-position amounts, dates, signed date, signatories), validation messages naming the missing item, history with signatories, read-only for a Zone Manager, axe in both themes

### Implementation for User Story 1

- [x] T022 [US1] `schoolbilling/internal/ContractService.java`: create (ends the current contract in the same transaction, optimistic `version`), `recordMou` for RATE_PENDING only, the validations of T018; reads the School through `school.api.SchoolDirectory` and the Zone Manager through `organization.api.ManagerQueries.managerOfSchool`; audit entity `CONTRACT`; decimal amounts as `BigDecimal` scale 2
- [x] T023 [US1] `schoolbilling/internal/SignatoryCandidates.java` (the School's Zone Manager and the active Directors, through the public `identity` lookup used by spec 010's `RecipientResolver`; if that lookup is not in a public package, add a small public interface in `identity` and cover it in `SchoolBillingModuleRulesTest`)
- [x] T024 [US1] `schoolbilling/web/ContractController.java` for `/api/v1/school-contracts/schools/{schoolId}`, `/schools/{schoolId}/contracts`, `/contracts/{id}/mou`, `/signatory-candidates` with `PermissionGuard` on `SCHOOL_CONTRACTS`; scope through `ScopeQueries`; 404 for out-of-scope Schools
- [x] T025 [P] [US1] `frontend/src/features/schoolbilling/SchoolContractPage.tsx`: School contract page with history (positions, signatories), the MoU form (shown only with `SCHOOL_CONTRACTS` CREATE/EDIT), "no MoU yet" state; route `/operations/school-contracts/schools/:schoolId` guarded in `frontend/src/App.tsx`

**Checkpoint**: contracts with signing details work end to end for each role; no Teacher is mapped to a position yet.

---

## Phase 5: User Story 3 - Map Recruited Teachers to the Contract (P1)

**Goal**: Teachers are mapped to vacant positions; a new MoU re-maps the current Teachers without a gap.

**Independent Test**: map two Teachers to a 3-position contract, see one vacant, the fourth refused; record a new MoU, re-map the three and see continuous assignments.

### Tests for User Story 3

- [x] T026 [P] [US3] `backend/src/test/java/com/hls/schoolbilling/PositionAssignmentTest.java`: map to a vacant position; per-Teacher contract requires a position, same-for-all takes the next vacant one; all positions filled refuses with the message in the spec; a position holds one Teacher (two simultaneous requests: the second is refused by the database constraint); a Teacher cannot overlap another assignment; no contract refuses; an exited Teacher refused; a scheduled move leaves the current assignment untouched until it takes effect; a Teacher exit makes the position vacant from the next day; every map, move and exit writes an audit entry (actor, roles, prior and new values)
- [x] T027 [P] [US3] `backend/src/test/java/com/hls/schoolbilling/RemapTest.java`: re-map three Teachers to a new contract: old assignments end the day before the new start, new ones begin under the chosen positions, `TeacherDirectory.placementsOverlapping` shows a continuous span, a failure on the third Teacher rolls back all three, audit entry `CONTRACT_REMAP` per Teacher; Teachers left on an ended or replaced contract are reported as unmapped (FR-007a) and stay placed
- [x] T028 [P] [US3] `backend/src/test/java/com/hls/schoolbilling/MappingApiTest.java`: per-role and per-scope matrix (Admin, Director and the Zone Manager of that School may map; another Zone Manager gets 404; Teacher and System 403) on `POST /api/v1/teachers/{id}/placements` and `map-teachers`, including `positionId` validation
- [x] T029 [P] [US3] `frontend/src/features/schoolbilling/MapTeachersDialog.test.tsx`: vacant positions with their salaries, refusal text, the re-map step lists current Teachers and new positions, axe in both themes

### Implementation for User Story 3

- [x] T030 [US3] Extend `schoolbilling/internal/AssignmentService.java` with positions: choose or default the position, check it belongs to the School's contract on the dates and is vacant, set `position_id`, translate the position-exclusion violation into "That position is already filled on those dates."
- [x] T031 [US3] `schoolbilling/internal/RemapService.java`: one transaction per call, all or nothing, per-Teacher end and start as described in research.md section 3; audit entity `CONTRACT_REMAP`
- [x] T032 [US3] `teacher/web/TeacherController.java`: accept an optional `positionId` on `POST /api/v1/teachers/{id}/placements`; `schoolbilling/web/MapTeachersController.java` for `POST /api/v1/school-contracts/contracts/{id}/map-teachers`, both guarded by `TEACHERS` `EDIT` and scope through `ScopeQueries`
- [x] T033 [P] [US3] `frontend/src/features/schoolbilling/MapTeachersDialog.tsx` and the position field in `frontend/src/features/teachers/PlacementDialog.tsx`: choose a vacant position (showing its salary) when mapping; a "Map Teachers to the new contract" step on `SchoolContractPage.tsx` after recording a new MoU

**Checkpoint**: recruited Teachers are mapped to positions and the School shows filled and vacant positions; attendance still correct.

---

## Phase 6: User Story 4 - See and Maintain the Contracts (P2)

**Goal**: a list of Schools with contract status and vacancies, plus end and cancel.

**Independent Test**: three Schools (full contract, vacancies, MoU pending): each state shows, filters work, a Zone Manager's list holds only their Schools.

### Tests for User Story 4

- [x] T034 [P] [US4] `backend/src/test/java/com/hls/schoolbilling/ContractListApiTest.java`: statuses (`ACTIVE`, `ENDS_SOON` within 30 days, `MOU_PENDING`, `NONE`, `ENDED`), filled and vacant counts, filter by status and by Zone Manager, two-Zone-Manager scope boundary in list, search and detail, empty list, Teacher and System 403
- [x] T035 [P] [US4] `backend/src/test/java/com/hls/schoolbilling/EndAndCancelTest.java`: end sets `ends_on` (not before `starts_on`), leaves Teachers' assignments alone, cancel only when no Teacher is mapped, Zone Manager 403, audit entries
- [x] T036 [P] [US4] `backend/src/test/java/com/hls/schoolbilling/SchoolContractsPublicApiTest.java`: `contractOf`, `positionOf`, `contractsOverlapping` and `unmappedTeachers` return what the screens show, a Teacher unmapped (carried over) is listed, and no other module reads contract tables (SC-008)
- [x] T037 [P] [US4] `frontend/src/features/schoolbilling/ContractsListPage.test.tsx`: role fixtures (Admin and Director with create, Zone Manager read-only and scoped), status chips, filters, empty and error states, axe in both themes

### Implementation for User Story 4

- [x] T038 [US4] `schoolbilling/internal/ContractListService.java` (one paged query per page over contracts, positions and today's assignments; scope through `ScopeQueries`) and `end`/`cancel` in `ContractService.java`; `GET /api/v1/school-contracts`, `/contracts/{id}/end`, `/contracts/{id}/cancel` in `ContractController.java`
- [x] T039 [US4] `schoolbilling/api/{SchoolContracts,ContractView,PositionView,TeacherPosition}.java` per contracts/school-contracts-api.md and `schoolbilling/internal/SchoolContractsImpl.java`
- [x] T040 [P] [US4] `frontend/src/features/schoolbilling/ContractsListPage.tsx`: the School Contracts list with status chips, filled and vacant counts, filters, "Record MoU" for Schools with none or pending (with `SCHOOL_CONTRACTS` CREATE); route `/operations/school-contracts` in `frontend/src/App.tsx`

**Checkpoint**: the full spec is functional; spec 022 can read contracts through `SchoolContracts`.

---

## Phase 7: Polish and Cross-Cutting Concerns

- [x] T041 [P] `schoolbilling/internal/ContractDevSeeder.java`: demo MoUs for Demo School One (4 Teachers, one salary for all) and Demo School Two (3 Teachers, different salaries) with signing details, existing demo Teachers mapped to positions, idempotent, only with the demo flag
- [x] T042 [P] Role-by-role UI test of the OPERATIONS menu and every action visibility (Admin, Director, Zone Manager, Teacher, System) in `frontend/src/features/schoolbilling/` and the navigation tests
- [ ] T043 [P] Accessibility pass on the screens and dialogs (keyboard only, screen reader names for amounts and status chips, focus after dialogs) and phone-width layout; record results in `specs/012-school-contracts/quickstart-results.md`
- [x] T044 [P] Add a "School Contracts" folder to the Postman collection in `docs/postman/HLS API/` and refresh `docs/db/schema-v21.sql`
- [x] T045 [P] Update `docs/spec-roadmap.md`: row 012 becomes "012-school-contracts" with this scope; add row 022 `022-school-billing` (month-end billing, payments, balance, overdue; depends on 008, 012; input `docs/spec-inputs/022-school-billing.md`); correct the 008, 009 and 010 rows (implemented and merged); make 013 payroll, 014 reports and 017 substitution depend on 012 and 022 instead of the old 012 billing; mention that placement now lives in `schoolbilling`
- [x] T046 [P] Add a "School Contracts" row to the Default role access matrix in `.specify/memory/constitution.md` (Admin and Director: View, Create, Edit; Zone Manager: View, Assigned) with a Sync Impact Report patch note (Principle II wording unchanged)
- [x] T047 [P] Update `docs/running-locally.md` with the contract demo data and the process order (MoU, mapping, attendance, then billing)
- [x] T048 Review before the PR: run the `java-reviewer` agent on `schoolbilling` and the `teacher` changes, and the `database-reviewer` agent on `V20` and `V21` (exclusion constraints, the copy, the drop); address findings
- [ ] T049 Walk through every scenario in `quickstart.md` on the local app (database on Docker port 5433), including the placement and attendance comparison before and after `V20`, and write the outcomes to `specs/012-school-contracts/quickstart-results.md`
- [ ] T050 Run the full backend suite and the full frontend suite once (one Maven class at a time if the machine struggles), then open the PR

---

## Dependencies and Execution Order

- Phase 1 then Phase 2 first. T003 blocks every table-using task; T004 to T007 are independent of each other; T008 follows T003.
- **US2 (Phase 3) before US1 (Phase 4)**: the moved assignment service and `TeacherPlacementSource` are what the contract and mapping rules sit on. Within US2, T012 to T015 are in order; T009 to T011 and T016 can be written alongside.
- US1 needs US2 (assignments) and T022 needs T023. US3 needs US1 (contracts to map to) and extends T012's service (T030). US4 needs US1 and US3 (filled and vacant counts).
- Frontend tasks marked [P] in a phase can start once that phase's API contract is fixed; they need only the typed client from T002.
- Polish after all stories; T048 to T050 last.

### Parallel opportunities

- Phase 2: T004, T005, T006, T007 together, then T008.
- US2: T009, T010, T011 together; T016 alongside T014.
- US1: T018 to T021 together; T025 alongside T024.
- US3: T026 to T029 together; T033 alongside T032.
- US4: T034 to T037 together; T040 alongside T038 and T039.
- Polish: T041 to T047 together.

## Implementation Strategy

- **MVP**: Phases 1 to 4 (US2 and US1): the placement moves safely under contract assignments, and Admin and Director
  can record MoUs with their signing details. It changes no figure users see and unblocks everything else.
- **Complete slice**: add US3 (map Teachers to positions, re-map on a new MoU) and US4 (maintenance list), which is the
  whole of the business process's first two steps. Then spec 022 builds the month-end bill on it.
- Land the placement move first and keep the old suites green before any contract logic: if anything is going to
  break, it breaks there.

---

## Implementation notes (2026-10-05)

Done and verified: Phases 1 to 6 and most of Phase 7. Differences from the plan:

- **T010**: the placement tests were not moved. They test the teacher endpoints, so they stay in `teacher/` and run
  against the new owner with unchanged assertions (`TeacherPlacementServiceTest`, `TeacherDirectoryTest`,
  `SchoolDeactivationGuardTest`). Four tests that wrote `teacher_placement` by SQL now write `contract_assignment`.
- **T011**: instead of a separate before/after test, the whole existing attendance, leave and notification suites
  ran unchanged against the migrated code (601 tests, 0 failures).
- **T012**: a School with no contract creates its "MoU pending" contract automatically (analysis finding I1), so seeders
  and old flows keep working; a position is required only once an active MoU covers the date.
- **T023**: the signatory candidates live in `ContractService` (the identity lookups were already public).
- **T032**: mapping by a Zone Manager needed a change to the Teachers placement endpoint, which was Admin and
  Director only. `TeacherService.assign` now allows a Zone Manager into their own Schools (a Teacher who is not
  placed yet or is placed at one of their Schools); `TeacherControllerTest` was updated for it.
- **Added beyond the plan**: School scope is enforced in `ContractService` for create, record, end and cancel (not only
  in the controller guard), because the permission matrix is runtime-editable; `SchoolDirectory.allSchools()` for the list.
- **T045**: `docs/spec-roadmap.md` was updated by someone else during the session (012 renamed, 016, 023, 024 and the
  delivery order added); nothing further was needed here.
- **T048**: the `java-reviewer` and `database-reviewer` agents ran. The database review found nothing blocking. The Java review found one high and several medium issues, all fixed with regression tests: a re-map no longer drops an assignment's end date or picks the wrong row, a rescheduled move can reuse its own position, re-submitting the same School keeps the Teacher's position, a Zone Manager cannot overwrite another zone's scheduled move or backdate, and a first-assignment race or an exclusion-constraint deadlock now answers 409 instead of 500. Left as notes: the contract list reads all Schools in memory (fine at this scale) and the signatory lookup does one query per Director.
- **Not done**: T043 (keyboard-only and screen-reader pass, phone-width check), the
  browser half of T049, and T050 (the PR). See `quickstart-results.md`.
