---

description: "Task list for Zone-Based Manager Scoping (reworked)"
---

# Tasks: Zone-Based Manager Scoping (reworked)

**Input**: Design documents from `/specs/006-zone-scoping/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/zone-api.yaml, quickstart.md

**Tests**: Included — this codebase's established practice writes unit + integration tests alongside every service, and this feature additionally requires updating two already-shipped test files (plan.md Summary/Complexity Tracking).

**Organization**: Tasks are grouped by user story (spec.md priorities: US1 = P1, US2 = P1, US3 = P2). This is an extension of the already-shipped `organization` module — no new bounded-context package, no new frontend page.

**Rework note**: This tasks.md replaces the original, never-executed one entirely — it does not define Zone or School↔Zone assignment (both are `school`'s, already implemented) and adds organization's first real cross-module dependency on `school.api.ZoneQueries`.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2, US3)

## Path Conventions

`backend/src/main/java/com/hls/organization/`, `backend/src/test/java/com/hls/organization/`, `frontend/src/pages/AssignmentsPage/`.

---

## Phase 1: Foundational (Blocking Prerequisites)

**Purpose**: The `ZoneManagerAssignment` table/entity/repository, DTOs, and new exception every user story builds on.

**⚠️ CRITICAL**: No user story task can begin until this phase is complete.

- [X] T001 Create migration `backend/src/main/resources/db/migration/V8__create_zone_manager_assignment_table.sql`: table `organization_zone_manager_assignment` with columns `id UUID PRIMARY KEY`, `zone_id UUID NOT NULL`, `manager_id UUID NOT NULL`, `effective_from TIMESTAMPTZ NOT NULL`, `effective_to TIMESTAMPTZ`, `assigned_by UUID NOT NULL`, `assigned_at TIMESTAMPTZ NOT NULL`; partial unique index `ux_zone_manager_assignment_current ON organization_zone_manager_assignment (zone_id, manager_id) WHERE effective_to IS NULL` (research.md §2 — NOT `(zone_id)` alone); index `ix_zone_manager_assignment_zone_id ON organization_zone_manager_assignment (zone_id) WHERE effective_to IS NULL`
- [X] T002 [P] Create `backend/src/main/java/com/hls/organization/internal/ZoneManagerAssignment.java`: JPA entity mirroring `SchoolAssignment`'s exact shape (`id, zoneId, managerId, effectiveFrom, effectiveTo, assignedBy, assignedAt`, `isCurrent()`, `end(Instant)` settable exactly once)
- [X] T003 [P] Create `backend/src/main/java/com/hls/organization/internal/ZoneManagerAssignmentRepository.java`: `extends JpaRepository<ZoneManagerAssignment, UUID>`, plus `findByZoneIdAndManagerIdAndEffectiveToIsNull`, `findByZoneIdAndEffectiveToIsNull`, `endIfStillCurrent` (`@Modifying @Query`, mirroring `SchoolAssignmentRepository`'s)
- [X] T004 [P] Create `backend/src/main/java/com/hls/organization/api/SchoolManagerNotInZoneException.java`: FR-003/FR-004's rejection, mapped to 422 by the controller (research.md §4)
- [X] T005 [P] Create `backend/src/main/java/com/hls/organization/api/ZoneNotFoundException.java`: FR-002's rejection (an unknown `zoneId`), mapped to 404 by the controller
- [X] T006 [P] Create `backend/src/main/java/com/hls/organization/api/dto/ZoneManagerAssignmentView.java`: `record ZoneManagerAssignmentView(UUID id, UUID zoneId, UUID managerId, Instant effectiveFrom)`
- [X] T007 [P] Create `backend/src/main/java/com/hls/organization/api/dto/ZoneCoverage.java`: `record ZoneCoverage(UUID zoneId, List<UUID> managerIds, List<UUID> schoolIds)` — composed at read time (data-model.md), never persisted
- [X] T008 Extend `backend/src/main/java/com/hls/organization/api/AccountabilityCommands.java`: add `ZoneManagerAssignmentView assignManagerToZone(UUID zoneId, UUID managerId, UUID actingUserId)` (FR-001/002) and `void removeManagerFromZone(UUID assignmentId, UUID actingUserId)` (FR-001)
- [X] T009 Extend `backend/src/main/java/com/hls/organization/api/AccountabilityQueries.java`: add `ZoneCoverage zoneCoverage(UUID zoneId)` (FR-005)

**Checkpoint**: `ZoneManagerAssignment` persists and is queryable; the public interfaces declare the new contract. User story implementation can now begin.

---

## Phase 2: User Story 1 - Director/Admin Assigns Manager(s) to Cover an Existing Zone (Priority: P1) 🎯 MVP

**Goal**: FR-001/FR-002 — assign one or more Managers to cover an existing Zone; reject an unknown Zone id; remove a Manager's coverage.

**Independent Test**: Assign a Manager to an existing Zone (created via `school`'s real endpoint) and confirm that Manager is retrievable as one of the Zone's currently covering Managers.

### Implementation for User Story 1

- [X] T010 [US1] Implement `AccountabilityService.assignManagerToZone(...)` in `backend/src/main/java/com/hls/organization/internal/AccountabilityService.java`: validate `zoneId` exists via the new `school.api.ZoneQueries.findById(zoneId)` constructor dependency (throw `ZoneNotFoundException` if absent — FR-002); if a currently-open row for `(zoneId, managerId)` already exists, return it as a no-op (consistent with every other assignment command in this codebase); otherwise create and save a new `ZoneManagerAssignment`
- [X] T011 [US1] Implement `AccountabilityService.removeManagerFromZone(...)`: `endIfStillCurrent` on the named assignment id, throwing `AssignmentConflictException` (existing type, existing 409 handler) if it was already ended by someone else — mirrors `endSchoolAssignment`/`endTeacherAssignment` exactly
- [X] T012 [US1] Extend `backend/src/main/java/com/hls/organization/internal/OrganizationController.java`: `POST /api/v1/organization/zone-manager-assignments` and `DELETE /api/v1/organization/zone-manager-assignments/{assignmentId}` (Director/Admin-only via the existing `requireDirectorOrAdmin` helper); add `@ExceptionHandler(ZoneNotFoundException.class)` → 404
- [X] T013 [P] [US1] Add `AccountabilityServiceTest` cases (mocking `school.api.ZoneQueries`): `assignManagerToZone_newManager_createsAssignment`, `assignManagerToZone_sameManagerAgain_isNoOp`, `assignManagerToZone_unknownZoneId_throwsZoneNotFound`, `assignManagerToZone_secondDifferentManager_bothCurrentlyCoverTheSameZone` (AC2), `removeManagerFromZone_endsAssignment`
- [X] T014 [P] [US1] Add `OrganizationIntegrationTest` case: `assignManagerToZone_thenRemove_reflectsInCoverage` — real HTTP, a real Zone created via `school`'s endpoint (not a stand-in), then the new zone-manager-assignment endpoints

**Checkpoint**: Managers can be assigned to and removed from Zone coverage — User Story 1 is independently functional (MVP).

---

## Phase 3: User Story 2 - A School's Manager Must Come From Its Zone's Covering Managers (Priority: P1)

**Goal**: FR-003/FR-004 — `assignSchoolManager` rejects a Manager who doesn't currently cover the School's Zone, or a School with no current Zone, or a Zone with no covering Managers.

**Independent Test**: Assign a School to a Zone with a known set of covering Managers (via `school`'s real endpoint + US1's new endpoint), then attempt to assign that School's accountable Manager to someone inside vs. outside that set.

### Implementation for User Story 2

- [X] T015 [US2] Add a `school.api.ZoneQueries` constructor dependency to `AccountabilityService` (used by both T010 and this story); implement the precondition in `assignSchoolManager(...)`, checked first, before the existing no-op/conflict logic (data-model.md's pseudocode): call `zoneQueries.currentZoneForSchool(schoolId)` → `UNASSIGNED` throws `SchoolManagerNotInZoneException` (FR-004); otherwise check `ZoneManagerAssignmentRepository.findByZoneIdAndManagerIdAndEffectiveToIsNull(zoneId, managerId)` → absent throws `SchoolManagerNotInZoneException` (FR-003/FR-004); present, proceed unchanged
- [X] T016 [US2] Update `backend/src/test/java/com/hls/organization/OrganizationModuleTest.java`: `@ApplicationModuleTest(mode = BootstrapMode.ALL_DEPENDENCIES)` (research.md §8 — `organization` now has a real bean dependency on `school.api.ZoneQueries`)
- [X] T017 [US2] Add `@ExceptionHandler(SchoolManagerNotInZoneException.class)` to `OrganizationController` → 422 (contracts/zone-api.yaml)
- [X] T018 [P] [US2] Update existing `AccountabilityServiceTest` `assignSchoolManager` cases (mocking `school.api.ZoneQueries` to return `CURRENT_ZONE` + a currently-open `ZoneManagerAssignment`, so they keep passing under the new precondition — plan.md Summary's named risk); add new cases: `assignSchoolManager_managerNotCoveringZone_throwsSchoolManagerNotInZone`, `assignSchoolManager_schoolHasNoCurrentZone_throwsSchoolManagerNotInZone`, `assignSchoolManager_zoneHasNoCoveringManagers_throwsSchoolManagerNotInZone`, `assignSchoolManager_twoSchoolsSameZoneDifferentManagers_bothSucceed` (AC3); add `assignTeacherManager_neverCallsZoneQueries` (SC-004 — verify zero interactions with the mocked `ZoneQueries`)
- [X] T019 [P] [US2] Update existing `OrganizationIntegrationTest` `assignSchoolManager`-calling cases to seed a real Zone (via `school`'s endpoint) and Zone coverage (via US1's new endpoint) first; add new cases: `assignSchoolManager_managerCoveringSchoolsZone_succeeds`, `assignSchoolManager_managerNotCoveringSchoolsZone_returns422`, `assignSchoolManager_schoolWithNoZone_returns422`, `assignTeacherManager_stillWorksWithNoZoneSetupAtAll` (quickstart.md Scenario 4, SC-004)

**Checkpoint**: School-Manager assignment is correctly Zone-constrained — User Stories 1-2 are both independently functional.

---

## Phase 4: User Story 3 - Director/Admin Sees Zone Coverage at a Glance (Priority: P2)

**Goal**: FR-005 — a single lookup returns a Zone's currently covering Managers and currently assigned Schools.

**Independent Test**: Set up a Zone with known covering Managers and Schools, then confirm a single lookup returns exactly that Zone's current Managers and Schools.

### Implementation for User Story 3

- [X] T020 [US3] Implement `AccountabilityService.zoneCoverage(zoneId)`: `managerIds` from `ZoneManagerAssignmentRepository.findByZoneIdAndEffectiveToIsNull(zoneId)`; `schoolIds` from `school.api.ZoneQueries.currentSchoolsForZone(zoneId)` — composed into one `ZoneCoverage`, never persisted (data-model.md)
- [X] T021 [US3] Extend `OrganizationController`: `GET /api/v1/organization/zones/{zoneId}/coverage`
- [X] T022 [P] [US3] Add `AccountabilityServiceTest` cases: `zoneCoverage_returnsCurrentManagersAndSchools`, `zoneCoverage_zoneWithNoSchoolsYet_returnsEmptySchoolListNotError` (AC2)
- [X] T023 [P] [US3] Add `OrganizationIntegrationTest` case: `getZoneCoverage_returnsManagersAndSchoolsInOneLookup` (quickstart.md Scenario 3)

**Checkpoint**: All three user stories are independently functional.

---

## Phase 5: Polish & Cross-Cutting Concerns

- [X] T024 [P] Extend `frontend/src/pages/AssignmentsPage/organizationClient.ts`: add `ZoneManagerAssignmentView`/`ZoneCoverage` types and `assignManagerToZone`/`removeManagerFromZone`/`getZoneCoverage` calls; update the existing school-assignment error handling to surface the new 422 message
- [X] T025 Extend `frontend/src/pages/AssignmentsPage/AssignmentsPage.tsx`: add an "Assign Manager to Zone" form (`data-testid="assign-manager-to-zone-form"`, zoneId + managerId) and a "Zone Coverage" lookup form (`data-testid="zone-coverage-form"`, zoneId, showing manager and school ids) — following the page's existing assign/portfolio/unassigned section pattern
- [X] T026 [P] Extend `frontend/src/pages/AssignmentsPage/AssignmentsPage.test.tsx`: cases for assigning a Manager to a Zone, and for the school-assignment form now showing the new 422 rejection reason
- [X] T027 [P] Run quickstart.md's four scenarios manually (or confirm via the automated equivalents in T014/T019/T023)
- [X] T028 Update `docs/HLS SDD Implementation Plan & Deliverables Tracker.md`'s row 4b (Zone-based Manager scoping) from "Not started" to Done, and add a "Resolved" Section 7 entry documenting the rework
- [X] T029 Run the full backend (`mvn test`) and frontend (`npx vitest run`, `npx eslint .`, `npx tsc -b`) suites to confirm no regressions — this step caught two real regressions a module-scoped test run missed (research.md §10/§11): `IdentityModuleTest` needed the same `BootstrapMode.ALL_DEPENDENCIES` fix (`identity` → `organization` → `school` is a transitive hop `DIRECT_DEPENDENCIES` doesn't reach), and `IdentityIntegrationTest`'s `managerScopeGuard_allowsAssignedManagerAndDeniesUnassignedOne` needed to seed a real Zone (via `school.api.ZoneCommands`) before calling `assignSchoolManager`, which is now Zone-constrained. Both fixed before this task was marked complete.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Foundational (Phase 1)**: No dependencies — BLOCKS all user stories
- **User Story 1 (Phase 2)**: Depends on Phase 1 only
- **User Story 2 (Phase 3)**: Depends on Phase 1; also depends on T010's `ZoneQueries` constructor dependency being added to `AccountabilityService` (shared with T015) — in practice built alongside or immediately after US1
- **User Story 3 (Phase 4)**: Depends on Phase 1 and on US1's `ZoneManagerAssignmentRepository` queries (T010) — no dependency on US2
- **Polish (Phase 5)**: Depends on all three user stories being complete

### Parallel Opportunities

- T002-T007 (Foundational entity/repository/exceptions/DTOs) — different files
- T013/T014 (US1 tests), T018/T019 (US2 tests), T022/T023 (US3 tests) — each pair touches different test files
- Phase 4 (US3) can be built in parallel with Phase 3 (US2) by a different contributor, since both depend only on Phase 1 and US1's repository, not on each other

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Foundational
2. Complete Phase 2: User Story 1 (Managers can be assigned to cover Zones)
3. **STOP and VALIDATE**: a Manager assigned to a Zone is retrievable as currently covering it

### Incremental Delivery

1. Foundational → `ZoneManagerAssignment` ready, contracts declared
2. User Story 1 → Zone-Manager coverage can be assigned/removed (MVP)
3. User Story 2 → the actual scoping correction this feature exists for — `assignSchoolManager` is Zone-constrained
4. User Story 3 → the "who's responsible for this area" visibility
5. Polish → frontend extension, quickstart validation, tracker update

## Notes

- No task defines a Zone or School↔Zone assignment endpoint, table, or entity — both are `school`'s, already shipped (specs/007-school-zone). Every reference to Zone/School-Zone data goes through `school.api.ZoneQueries`.
- T015/T016 are the two tasks that give `organization` its first real cross-module dependency — do these together, since `OrganizationModuleTest` will fail to boot between adding the constructor dependency and switching its bootstrap mode otherwise.
- Commit after each task or logical group, consistent with this repo's established practice.
