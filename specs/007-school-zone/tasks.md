---

description: "Task list for the School Master Data (Zones) module implementation"
---

# Tasks: School Master Data — Zones

**Input**: Design documents from `/specs/007-school-zone/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/school-zone-api.yaml, quickstart.md (all present)

**Tests**: Included. Constitution Principle VII and this module's own conflict-detection/history requirements (FR-004/FR-005) commit to test-first development, mirroring every prior module's approach.

**Organization**: Tasks are grouped by user story (spec.md's three: US1/US2 = P1, US3 = P2).

**Cross-cutting note**: This is the fourth backend bounded-context package (`com.hls.school`, after `identity`/`organization`/`audit`) — no other module is touched by this feature. specs/006-zone-scoping (Zone-based Manager scoping) depends on this module's `ZoneQueries` once it's reworked and implemented, but that's a separate, later feature.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: Maps the task to spec.md's US1–US3

## Phase 1: Setup

- [X] T001 Create `backend/src/main/java/com/hls/school/api/` and `backend/src/main/java/com/hls/school/internal/` package directories with a `package-info.java` marker in each, matching `identity`/`organization`/`audit`'s `api`/`internal` split. Annotate `api/package-info.java` with `@org.springframework.modulith.NamedInterface("api")` from the start (specs/003 tasks.md T031's lesson, already applied up front by specs/004/005). **No `pom.xml` changes**: everything this module needs is already present.

## Phase 2: Foundational (Blocking Prerequisites)

**🚨 CRITICAL**: No user story task may start until this phase is complete.

- [X] T002 Create Flyway migration `backend/src/main/resources/db/migration/V<next>__create_school_tables.sql` (research.md — determine `<next>` from the highest existing `V<n>` at implementation time) creating: `school_zone` (`id UUID PK`, `name VARCHAR NOT NULL`, `created_at TIMESTAMPTZ NOT NULL`, `created_by UUID NOT NULL`); `school_zone_assignment` (`id UUID PK`, `school_id UUID NOT NULL`, `zone_id UUID NOT NULL`, `effective_from TIMESTAMPTZ NOT NULL`, `effective_to TIMESTAMPTZ NULLABLE`, `assigned_by UUID NOT NULL`, `assigned_at TIMESTAMPTZ NOT NULL`) with partial unique index `CREATE UNIQUE INDEX ux_school_zone_assignment_current ON school_zone_assignment (school_id) WHERE effective_to IS NULL` (data-model.md — at most one current Zone per School, same technique as `organization_school_assignment`)
- [X] T003 [P] Create `Zone` JPA entity in `backend/src/main/java/com/hls/school/internal/Zone.java`: `id`, `name`, `createdAt`, `createdBy` — set once at construction, no setters (data-model.md: never deleted)
- [X] T004 [P] Create `SchoolZoneAssignment` JPA entity in `backend/src/main/java/com/hls/school/internal/SchoolZoneAssignment.java`: identical shape/invariants to `organization.internal.SchoolAssignment` (`id`, `schoolId`, `zoneId`, `effectiveFrom`, `effectiveTo`, `assignedBy`, `assignedAt`, an `end(Instant)` method settable exactly once, an `isCurrent()` helper)
- [X] T005 [P] Create `ZoneRepository` in `backend/src/main/java/com/hls/school/internal/ZoneRepository.java`: extends bare `Repository<Zone, UUID>`, declares only `save`, `findById`, `findAll` — no delete method (FR-009, mirrors `AuditEntryRepository`'s established pattern)
- [X] T006 [P] Create `SchoolZoneAssignmentRepository` in `backend/src/main/java/com/hls/school/internal/SchoolZoneAssignmentRepository.java`: `findBySchoolIdAndEffectiveToIsNull(UUID)`, `findBySchoolIdOrderByEffectiveFromAsc(UUID)`, `findByZoneIdAndEffectiveToIsNull(UUID)` (for FR-007), and `endIfStillCurrent(UUID id, Instant now)` (`@Modifying`, conditional `UPDATE ... WHERE id = ? AND effective_to IS NULL`, same pattern as `SchoolAssignmentRepository`)
- [X] T007 [P] Create `ZoneAssignmentConflictException` in `backend/src/main/java/com/hls/school/api/ZoneAssignmentConflictException.java`: public `RuntimeException` (FR-005 — callers of `ZoneCommands` must be able to catch it, same role as `organization.api.AssignmentConflictException`)
- [X] T008 [P] Create the `api/dto` value types in `backend/src/main/java/com/hls/school/api/dto/`, with a `package-info.java` also annotated `@NamedInterface("api")`: `ZoneView.java` (`id`, `name`), `SchoolZoneAnswer.java` (`state`: `CURRENT_ZONE`/`UNASSIGNED` + nullable `zoneId`, same "answer" shape as `organization.api.dto.AccountabilityAnswer`)
- [X] T009 [P] Extend `backend/src/test/java/com/hls/ArchitectureTest.java` with a `school`-specific rule: nothing outside `com.hls.school` may depend on `com.hls.school.internal..`, mirroring the existing rules
- [X] T010 [P] Create `backend/src/test/java/com/hls/school/SchoolModuleTest.java` using Spring Modulith's `@ApplicationModuleTest`, same H2 override pattern as `AuditModuleTest`; default `BootstrapMode.STANDALONE` is expected to be sufficient — `school` depends on nothing, and nothing depends on `school` yet either (research.md §3 — flag for whoever implements specs/006-zone-scoping next, since that will change)

**Checkpoint**: Foundation ready — user story phases below may now begin.

---

## Phase 3: User Story 1 - Director/Admin Defines Zones (Priority: P1) 🎯 MVP

**Goal**: Director/Admin can create a Zone and retrieve it by id.

**Independent Test**: Create a Zone, confirm it's immediately retrievable with the name entered.

### Tests for User Story 1

> Write these first; confirm they fail before implementing.

- [X] T011 [P] [US1] `ZoneServiceTest` cases: `createZone` persists a new Zone retrievable by id with the exact name (US1 AC1/AC2) — in `backend/src/test/java/com/hls/school/ZoneServiceTest.java`
- [X] T012 [P] [US1] `SchoolIntegrationTest` case covering quickstart.md Scenario 1: real HTTP `POST /school/zones` then `GET /school/zones/{id}`; a lookup of a non-existent id returns `404` (Edge Cases) — in `backend/src/test/java/com/hls/school/SchoolIntegrationTest.java`

### Implementation for User Story 1

- [X] T013 [US1] Create `ZoneQueries` interface (`findById(UUID) -> Optional<ZoneView>`, FR-002) and `ZoneCommands` interface (`createZone(String name, UUID actingUserId) -> UUID`, FR-001) in `backend/src/main/java/com/hls/school/api/ZoneQueries.java` and `ZoneCommands.java` — depends on T008
- [X] T014 [US1] Implement `ZoneService` (partial: `createZone`/`findById` only) in `backend/src/main/java/com/hls/school/internal/ZoneService.java` — depends on T003, T005, T013
- [X] T015 [US1] Implement `ZoneController` (partial) in `backend/src/main/java/com/hls/school/internal/ZoneController.java` per `contracts/school-zone-api.yaml`: `POST /api/v1/school/zones`, `GET /api/v1/school/zones/{zoneId}` (`404` if not found) — reads `@AuthenticationPrincipal Jwt` directly, Director/Admin check same as every other controller — depends on T014
- [X] T016 [US1] Create `frontend/src/pages/ZonesPage/zoneClient.ts` and `ZonesPage.tsx` (+ `ZonesPage.test.tsx`, partial: create Zone + list/view) — reuses `authClient`'s existing Bearer-token pattern — depends on T015

**Checkpoint**: User Story 1 fully functional and independently testable (MVP).

---

## Phase 4: User Story 2 - Director/Admin Assigns a School to a Zone (Priority: P1)

**Goal**: Assign/reassign a School's current Zone, with history preserved and conflicting concurrent reassignments rejected.

**Independent Test**: Assign a School to a Zone, confirm it's current; reassign to a different Zone and confirm the change while history remains; fire two conflicting reassignments and confirm exactly one succeeds.

**Depends on**: User Story 1 (needs `ZoneService`/`ZoneController` to exist first).

### Tests for User Story 2

- [X] T017 [P] [US2] `ZoneServiceTest` cases: `assignSchoolToZone` with no `endsAssignmentId` creates the School's first assignment (US2 AC1); reassignment with a valid `endsAssignmentId` ends the prior row and opens a new one (US2 AC2); reassignment naming an already-ended `endsAssignmentId` throws `ZoneAssignmentConflictException` (US2 AC3, FR-005) — in `ZoneServiceTest.java`
- [X] T018 [P] [US2] `SchoolIntegrationTest` cases covering quickstart.md Scenario 2 (assign, reassign, confirm current Zone) and Scenario 3 (two concurrent reassignments naming the same `endsAssignmentId` — exactly one `200`, the other `409`, SC-003) — in `SchoolIntegrationTest.java`

### Implementation for User Story 2

- [X] T019 [US2] Extend `ZoneCommands` with `assignSchoolToZone(schoolId, zoneId, endsAssignmentId, actingUserId) -> UUID` (FR-003/FR-004) in `backend/src/main/java/com/hls/school/api/ZoneCommands.java` — depends on T013
- [X] T020 [US2] Implement `assignSchoolToZone` in `ZoneService`: same create/reassign/conditional-conflict-check logic as `AccountabilityService.assignSchoolManager` (specs/003 research.md §2) — depends on T004, T006, T007, T019
- [X] T021 [US2] Implement `POST /api/v1/school/school-zone-assignments` in `ZoneController` per `contracts/school-zone-api.yaml`, plus an `@ExceptionHandler(ZoneAssignmentConflictException.class)` returning `409` — depends on T020
- [X] T022 [US2] Extend `ZonesPage`: assign/reassign a School to a Zone form (+ test cases) — depends on T016, T021

**Checkpoint**: User Stories 1 and 2 both independently functional.

---

## Phase 5: User Story 3 - View a School's Zone and a Zone's Schools (Priority: P2)

**Goal**: Retrieve every School currently in a Zone, and a School's current Zone (distinguishing "unassigned" from an error).

**Independent Test**: Assign several Schools to a Zone, confirm a lookup returns exactly that set; look up an unassigned School and confirm it reports "unassigned," not an error.

**Depends on**: User Stories 1 and 2 (nothing to view without Zones and School-Zone assignments existing).

### Tests for User Story 3

- [X] T023 [P] [US3] `ZoneServiceTest` cases: `currentSchoolsForZone` returns exactly the Schools currently assigned to that Zone (US3 AC1); `currentZoneForSchool` returns `UNASSIGNED` for a School with no assignment, not an error (US3 AC2, Edge Cases) — in `ZoneServiceTest.java`
- [X] T024 [P] [US3] `SchoolIntegrationTest` case covering quickstart.md Scenario 4 — in `SchoolIntegrationTest.java`

### Implementation for User Story 3

- [X] T025 [US3] Extend `ZoneQueries` with `currentSchoolsForZone(zoneId) -> List<UUID>` (FR-007) and `currentZoneForSchool(schoolId) -> SchoolZoneAnswer` (FR-006) in `backend/src/main/java/com/hls/school/api/ZoneQueries.java`; implement both in `ZoneService` — depends on T006, T013, T014
- [X] T026 [US3] Implement `GET /api/v1/school/zones/{zoneId}/schools` and `GET /api/v1/school/schools/{schoolId}/zone` in `ZoneController` per `contracts/school-zone-api.yaml` — depends on T025
- [X] T027 [US3] Extend `ZonesPage`: view a Zone's current Schools — depends on T022, T026

**Checkpoint**: All three user stories independently functional.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [X] T028 [P] Run all 4 quickstart.md scenarios end-to-end via their automated equivalents: `cd backend && ./mvnw test -Dtest=ZoneServiceTest,SchoolIntegrationTest,SchoolModuleTest` and `cd frontend && npx vitest run src/pages/ZonesPage`
- [X] T029 [P] Confirm `ZoneController`'s structured log output already carries `requestId`/`userId`/`role` via Identity's app-wide interceptor/filter with no extra wiring needed, the same finding every prior module's Polish phase already confirmed — confirm by inspection
- [X] T030 Run the full ArchUnit + Spring Modulith verification suite (`./mvnw test -Dtest=ArchitectureTest,SchoolModuleTest,OrganizationModuleTest,IdentityModuleTest,AuditModuleTest,ApplicationModulesTest`) and fix any boundary violation surfaced by adding this fourth module
- [X] T031 [P] Finalize T002's Flyway version number against whatever has actually shipped by implementation time (research.md)
- [X] T032 [P] Update `docs/HLS SDD Implementation Plan & Deliverables Tracker.md` row 4a from "Not started" to Done once all phases above pass

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies.
- **Foundational (Phase 2)**: Depends on Setup — blocks every user story phase.
- **User Stories (Phase 3–5)**: All depend on Foundational. US2 needs US1's `ZoneService`/`ZoneController` to exist first (extends the same files, does not replace them). US3 needs both — nothing meaningful to view without Zones (US1) and School-Zone assignments (US2).
- **Polish (Phase 6)**: Depends on all three user stories.

### Parallel Opportunities

- T001 (Setup) has nothing to run alongside.
- T003–T010 (Foundational) can run in parallel once T002's migration is written.
- Within each story, `[P]`-marked test tasks run in parallel with each other before implementation begins.

## Parallel Example: Foundational Phase

```bash
Task: "Zone JPA entity in backend/src/main/java/com/hls/school/internal/Zone.java"
Task: "SchoolZoneAssignment JPA entity in backend/src/main/java/com/hls/school/internal/SchoolZoneAssignment.java"
Task: "ZoneRepository in backend/src/main/java/com/hls/school/internal/ZoneRepository.java"
Task: "SchoolZoneAssignmentRepository in backend/src/main/java/com/hls/school/internal/SchoolZoneAssignmentRepository.java"
Task: "ZoneAssignmentConflictException in backend/src/main/java/com/hls/school/api/ZoneAssignmentConflictException.java"
Task: "ZoneView/SchoolZoneAnswer DTOs in backend/src/main/java/com/hls/school/api/dto/"
Task: "ArchitectureTest school-specific rule"
Task: "SchoolModuleTest.java"
```

## Implementation Strategy

### MVP First

User Story 1 alone (create/retrieve Zones) is real but limited value on its own. A realistically useful v1 needs **US1 + US2** together, since Zones with nothing assigned to them don't yet serve specs/006-zone-scoping's purpose. US3 (visibility) can follow.

1. Setup → Foundational (blocking)
2. US1 → validate independently
3. US2 → validate independently (assign/reassign/conflict)
4. US3 → validate independently (coverage views)

### Format Validation

All 32 tasks above follow `- [ ] T### [P?] [Story?] Description with file path`: Setup/Foundational/Polish tasks carry no `[Story]` label; every Phase 3–5 task carries its `[US#]` label; every task names a concrete file path.
