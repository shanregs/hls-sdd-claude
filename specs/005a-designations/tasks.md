---

description: "Task list for feature implementation"
---

# Tasks: Designations and Employment Details

**Input**: Design documents from `/specs/005a-designations/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/designations-api.md, quickstart.md, and
**specs 001-005, 008, 009 and 012 implemented and merged** (this feature adds a permission module and a navigation
item to 002's matrix and catalog, and adds fields to the Manager and Teacher records of spec 005 without changing any
existing behaviour). Flyway: the latest migration is V23; this feature uses `V24` only.

**Tests**: included as first-class tasks. Constitution Principle IX requires per-role and per-scope tests on every
endpoint and UI tests per role; the spec's invariants (history rows never change, unique employee id even under
concurrency, designation on a date, fixed query count) are only trustworthy with tests. Rule unit tests come before
the services that use them.

**Organization**: grouped by user story in priority order (spec.md US1-US4).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: US1-US4; absent for Setup/Foundational/Polish

## Path Conventions

Backend `backend/src/main/java/com/hls/{designation,organization,teacher,identity}/...`, tests under
`backend/src/test/java/com/hls/...`; migration `backend/src/main/resources/db/migration`; frontend
`frontend/src/features/{designations,managers,teachers}/...`. Run one Maven test class at a time
(`mvn -o test -Dtest=...`); the machine is slow.

---

## Phase 1: Setup

- [ ] T001 Create the `designation` module skeleton: `backend/src/main/java/com/hls/designation/{api,internal,web}/` with `package-info.java` (the `api` package a `@NamedInterface`) and `backend/src/test/java/com/hls/designation/DesignationModuleRulesTest.java` (ArchUnit: nothing outside `designation` uses `designation.internal`; `designation` does not depend on `organization` or `teacher`; no module reads the `designation`, `employee_id_claim` or `manager_designation` tables outside its owner)
- [ ] T002 [P] Create `frontend/src/features/designations/designationsApi.ts` (types and calls for every endpoint in contracts/designations-api.md) and add the employment types and calls to `frontend/src/features/managers/managersApi.ts` and `frontend/src/features/teachers/teachersApi.ts`

---

## Phase 2: Foundational (blocking prerequisites)

- [ ] T003 Write `backend/src/main/resources/db/migration/V24__create_designations.sql` per data-model.md: `designation` (id uuid PK, name varchar(80) not null, kind varchar(8) not null check in ('TEACHER','MANAGER'), retired boolean not null default false, held_ever boolean not null default false, version bigint not null, created_by uuid not null, created_at/updated_at timestamptz not null; unique index on `(kind, lower(regexp_replace(btrim(name), '\s+', ' ', 'g')))`), `employee_id_claim` (employee_key varchar(20) PK, person_kind varchar(8) not null, person_id uuid not null unique, employee_id varchar(20) not null, person_name varchar(200) not null, claimed_at timestamptz not null), `ALTER TABLE manager ADD employee_id varchar(20) null, joining_date date null, exit_date date null` with check `exit_date >= joining_date` when both set, `manager_designation` (id uuid PK, seq bigint generated always as identity, manager_id uuid not null FK manager(id), designation_id uuid not null with no foreign key, effective_on date not null, recorded_by uuid not null, recorded_at timestamptz not null; index `(manager_id, effective_on, seq)`; `BEFORE UPDATE OR DELETE` trigger raising an exception), `ALTER TABLE teacher ADD designation_id uuid null, employee_id varchar(20) null`
- [ ] T004 [P] Add permission module `DESIGNATIONS(VIEW, CREATE, EDIT)` to `identity/permissions/PermissionModule.java`; in `PermissionEligibility` make it a business module (System excluded), restrict it to Admin, Director and Manager, and let Manager hold `VIEW` only (never CREATE or EDIT); seed defaults in `PermissionMatrixService` (Admin and Director: VIEW, CREATE, EDIT; nobody else)
- [ ] T005 [P] Add navigation `MASTER DATA -> Designations` (`/master-data/designations`, DESIGNATIONS VIEW, Admin and Director) to `identity/accessmodel/NavigationCatalog.java` and add `DESIGNATIONS` to the org-wide data scope in `AccessModelService.java`
- [ ] T006 [P] Extend `PermissionEligibilityTest`, `NavigationSectionOrderTest` and the access-model and seed tests under `backend/src/test/java/com/hls/identity/` for the module (Teacher and System excluded; Manager VIEW only) and the MASTER DATA item per role
- [ ] T007 [P] Add `designation/api/{DesignationDirectory,EmployeeIds,HolderCounter,BusinessDate}.java` exactly as in contracts/designations-api.md (`BusinessDate` is a component giving today's date in `hls.business-timezone`, default Asia/Kolkata)
- [ ] T008 Entities and repositories in `designation/internal` (`Designation`, `EmployeeIdClaim`, Spring Data repositories) and in `organization/internal` (`ManagerDesignation`, insert-only, plus new fields on `Manager`) and `teacher/internal` (new fields on `Teacher`); Hibernate only validates the schema

**Checkpoint**: migration applies on a database holding existing Managers and Teachers; permission and navigation seeded; entities validate against the schema.

---

## Phase 3: User Story 1 - Keep the List of Designations (P1) MVP

**Goal**: Admin and Director keep the list of designations.

**Independent Test**: add, rename, retire and reactivate; duplicates, empty and long names refused; kind and deletion refused once held; the menu item is absent for Zone Manager, Teacher and System.

### Tests for User Story 1

- [ ] T009 [P] [US1] `backend/src/test/java/com/hls/designation/DesignationServiceTest.java` (rules first): name 1 to 80 after trimming; duplicate per kind ignoring capitals and extra spaces refused, same name in the other kind allowed; rename audited with old and new; retire and reactivate; kind change refused once `held_ever`; `assign` refuses retired, wrong kind and unknown ids and sets `held_ever`; stale `version` is a 409
- [ ] T010 [P] [US1] `backend/src/test/java/com/hls/designation/DesignationApiTest.java`: per-role matrix for every endpoint in contracts/designations-api.md (Admin and Director succeed; Zone Manager, Teacher and System 403; DELETE always 409 for Admin); the list shows holder counts; `options` returns only active designations of the kind
- [ ] T011 [P] [US1] `backend/src/test/java/com/hls/designation/DesignationAuditTest.java`: each create, rename, retire and reactivate writes an audit entry with actor and old and new values
- [ ] T012 [P] [US1] `frontend/src/features/designations/DesignationsPage.test.tsx`: role fixtures (Admin and Director with add, rename, retire; read-only grant shows no actions), validation messages, empty and error states, axe in both themes

### Implementation for User Story 1

- [ ] T013 [US1] `designation/internal/DesignationService.java` (implements `DesignationDirectory`; create, update, list with counts collected from all `HolderCounter` beans, `summary`, options; audit entity `DESIGNATION` through `ChangeRecorder`; `BusinessDate` not needed here)
- [ ] T014 [US1] `designation/web/DesignationController.java` for the `/api/v1/designations` endpoints with `PermissionGuard` on `DESIGNATIONS`
- [ ] T015 [P] [US1] `frontend/src/features/designations/DesignationsPage.tsx` and its dialog: list with kind, status and people count, add, rename, retire and reactivate (actions only with the grants); route `/master-data/designations` guarded in `frontend/src/App.tsx`

**Checkpoint**: the list works end to end for each role.

---

## Phase 4: User Story 2 - Record a Manager's Designation and Employment Dates (P1)

**Goal**: Admin and Director record designation history, employee id, joining and exit dates; a Zone Manager reads them.

**Independent Test**: set designation, employee id and joining date, deactivate the Manager's account and see the exit date, reactivate and see it cleared, all in the audit log and read-only for a Zone Manager.

### Tests for User Story 2

- [ ] T016 [P] [US2] `designation/EmployeeIdServiceTest.java` (rules first, in the `designation` test package): format `[A-Za-z0-9-]{1,20}`, trimmed; key is lower-cased trim; duplicate across Manager and Teacher refused naming the holder; clearing releases; two simultaneous claims of one id: exactly one succeeds
- [ ] T017 [P] [US2] `backend/src/test/java/com/hls/organization/ManagerDesignationHistoryTest.java`: table of at least 6 cases for the designation on a date (before the first row, on a row's date, between rows, after the last, two rows on one date with the later recorded winning, a change in the middle of a month); effective-date rules (first designation from the joining date or any date up to today when none; later changes not before the first day of the current month; the same designation refused; retired or wrong-kind refused); rows cannot be updated or deleted even by SQL
- [ ] T018 [P] [US2] `backend/src/test/java/com/hls/organization/ManagerEmploymentTest.java`: joining date not after the exit date; exit date only while inactive, not before the joining date, not more than 90 days after today (boundary days); deactivating the account sets the exit date to today and audits it; reactivation clears it and the old value is in the audit log; legacy inactive Manager has no exit date and is flagged; views show `missing` codes
- [ ] T019 [P] [US2] `backend/src/test/java/com/hls/organization/ManagerEmploymentApiTest.java`: per-role and per-scope matrix for `PUT /managers/{id}/employment` and `POST /managers/{id}/designation` (Admin and Director succeed; Zone Manager, Teacher, System 403 even if the Manager edit permission is granted); Zone Manager reads `employment` on `GET /managers/{id}`; audit entries per field (SC-002)
- [ ] T020 [P] [US2] `backend/src/test/java/com/hls/organization/ManagerQueriesEmploymentTest.java`: `designationOn`, `designationsOn`, `employment`, `holderCountsByDesignation` return what the screens show, and the bulk forms use a fixed number of statements for 300 Managers (SC-007)
- [ ] T021 [P] [US2] `frontend/src/features/managers/ManagersPage.test.tsx`: the new fields, missing flags, editor only with `DESIGNATIONS` EDIT, read-only for a Zone Manager fixture, history list

### Implementation for User Story 2

- [ ] T022 [US2] `designation/internal/EmployeeIdService.java` implementing `EmployeeIds` over `employee_id_claim` (insert, translate the primary-key violation into a 409 naming the holder, release)
- [ ] T023 [US2] `organization/internal/ManagerEmploymentService.java`: `updateEmployment` (employee id, joining and exit dates) and `appendDesignation`, using `DesignationDirectory.assign`, `EmployeeIds.claim` and `BusinessDate`; audit entity `MANAGER` with fields `employeeId`, `joiningDate`, `exitDate`, `designation`; extend `ManagerService` (implements the new `ManagerQueries` methods; `ManagerView.employment`) and `ManagerRepository`/`ManagerDesignationRepository` bulk queries
- [ ] T024 [US2] `organization/internal/ManagerAccountSync.java`: set the exit date to the business date when a Manager becomes inactive without one, clear it when active again; audit as the event's actor
- [ ] T025 [US2] `organization/web/ManagerController.java`: the two endpoints, each requiring `DESIGNATIONS` `EDIT`
- [ ] T026 [P] [US2] `frontend/src/features/managers/{ManagersPage,EmploymentDialog}.tsx`: show designation, employee id, joining and exit dates and missing flags; the dialog for editing (designation with effective date, employee id, dates) only with the `DESIGNATIONS` EDIT action; history list

**Checkpoint**: a Manager's details work end to end for each role; 013a can read `ManagerQueries`.

---

## Phase 5: User Story 3 - Record a Teacher's Designation and Employee Id (P1)

**Goal**: Admin and Director record a Teacher's designation and optional employee id; Zone Manager reads them in scope; My Profile shows neither.

**Independent Test**: set both on a Teacher, a Zone Manager reads them for a Teacher in their School and fails to change them, an existing Teacher with neither still works.

### Tests for User Story 3

- [ ] T027 [P] [US3] `backend/src/test/java/com/hls/teacher/TeacherEmploymentTest.java`: only active TEACHER designations accepted (a retired one is accepted only when unchanged); duplicate id across Teachers and Managers refused; clearing the id; the designation may be changed or cleared (audited); an exited Teacher can still be corrected; audit per field with old and new values
- [ ] T028 [P] [US3] `backend/src/test/java/com/hls/teacher/TeacherEmploymentApiTest.java`: per-role and per-scope matrix for `PUT /teachers/{id}/employment` (Zone Manager 403 even with TEACHERS EDIT; Teacher and System 403) and reads (Zone Manager sees `employment` in scope, 404 outside; `/teachers/me` has `employment` null); existing Teachers without the fields are unchanged
- [ ] T029 [P] [US3] `backend/src/test/java/com/hls/teacher/TeacherDirectoryEmploymentTest.java`: `currentDesignation`, `currentDesignations`, `employment`, `holderCountsByDesignation` and a fixed statement count for 300 Teachers (SC-007)
- [ ] T030 [P] [US3] `frontend/src/features/teachers/TeachersPage.test.tsx` (extend) and `MyTeacherProfile.test.tsx`: fields and missing flag, editor only with the grant, My Profile shows none

### Implementation for User Story 3

- [ ] T031 [US3] `teacher/internal/TeacherEmploymentService.java` (update with `DESIGNATIONS` rules, `DesignationDirectory.assign`, `EmployeeIds.claim`, audit entity `TEACHER` fields `designation` and `employeeId`), the new `TeacherDirectory` methods in `TeacherDirectoryImpl`, `TeacherView.employment` filled in `TeacherService.viewsOf` and null in `mine`
- [ ] T032 [US3] `teacher/web/TeacherController.java`: `PUT /api/v1/teachers/{id}/employment` requiring `DESIGNATIONS` `EDIT` plus the Teacher's scope
- [ ] T033 [P] [US3] `frontend/src/features/teachers/{TeachersPage,EmploymentDialog}.tsx`: show and edit the fields; none on My Profile

**Checkpoint**: Teachers' details work end to end; 013a can read `TeacherDirectory`.

---

## Phase 6: User Story 4 - Find What Is Missing Before Payroll (P2)

**Goal**: counts and filters of people with missing details.

**Independent Test**: with some people missing fields, counts and the filtered lists match exactly.

### Tests for User Story 4

- [ ] T034 [P] [US4] `backend/src/test/java/com/hls/designation/MissingDetailsTest.java`: `summary` counts equal the lengths of `GET /managers?missing=true` and `GET /teachers?missingDesignation=true` (SC-006); zero when everyone is complete; Zone Manager list stays in scope
- [ ] T035 [P] [US4] `frontend/src/features/designations/DesignationsPage.test.tsx` (extend): the missing counts and links with filters; no flag when counts are zero

### Implementation for User Story 4

- [ ] T036 [US4] `HolderCounter` implementations `organization/internal/ManagerHolderCounter.java` and `teacher/internal/TeacherHolderCounter.java`; `missing` filters in `ManagerService.list` and `TeacherService.list`/`TeacherRepository`; controller parameters
- [ ] T037 [P] [US4] Frontend: summary cards on `DesignationsPage.tsx`; "Missing details" filter in `ManagersPage.tsx` and "Missing designation" filter in `TeachersPage.tsx`, read from the URL query so the links work

**Checkpoint**: all four stories work.

---

## Phase 7: Polish and Cross-Cutting Concerns

- [ ] T038 [P] `designation/internal/DesignationDevSeeder.java` (a few Teacher and Manager designations) and extend `OrganizationDevSeeder` and `TeacherDevSeeder` to give the demo people a designation and employee id (idempotent, demo flag only; leave one person incomplete so the missing counts show something)
- [ ] T039 [P] Add a "Designations" folder to the Postman collection in `docs/postman/` and refresh the schema doc if one exists
- [ ] T040 [P] Add a Designations row to the Default role access matrix and `designation` to the module list in `.specify/memory/constitution.md` (patch 2.5.1 with a Sync Impact note); update `docs/spec-roadmap.md` and `docs/running-locally.md` (V24, demo data)
- [ ] T041 Run `ApplicationModulesTest`, `DesignationModuleRulesTest`, `MasterDataModuleRulesTest` and fix any fixtures the new menu item or fields legitimately change (menu counts, `TeacherView`/`ManagerView` constructors); existing specs 005, 008, 009 and 012 tests stay unchanged in their assertions (SC-005)
- [ ] T042 Run the full backend suite and the full frontend suite (tests, `npm run build` for typecheck, `npm run lint`) once
- [ ] T043 Walk the scenarios in `quickstart.md` as far as the environment allows and record the outcomes in `specs/005a-designations/quickstart-results.md`

---

## Dependencies and Execution Order

- Phase 1 then Phase 2. T003 blocks all table-using tasks; T004 to T007 are independent; T008 follows T003 and T007.
- US1 needs Phase 2. US2 needs US1 (designations to assign) and T022 before T023. US3 needs US1 and T022. US2 and US3 are independent of each other. US4 needs US1, US2 and US3.
- Frontend tasks marked [P] can start once the typed client of T002 exists.
- Polish after all stories; T041 to T043 last.

### Parallel opportunities

- Phase 2: T004, T005, T006, T007 together, then T008.
- US1: T009 to T012 together. US2: T016 to T021 together; T026 alongside T025. US3: T027 to T030 together; T033 alongside T032. US4: T034, T035 together.
- Polish: T038 to T040 together.

## Implementation Strategy

- **MVP**: Phases 1 to 3 (US1): the list exists and can be edited.
- **Unblocking 013a**: US2 and US3 expose the public interfaces; US4 is a convenience.
- Existing suites stay green at every phase: the new columns are nullable and the new view members are additive.
