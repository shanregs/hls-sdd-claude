---

description: "Task list for the Zone-Based Manager Scoping module implementation"
---

# Tasks: Zone-Based Manager Scoping

**Input**: Design documents from `/specs/006-zone-scoping/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/zone-api.yaml, quickstart.md (all present)

**Tests**: Included. Constitution Principle VII ("test coverage for calculation logic, access rules, and integration boundaries") and this feature's own Complexity Tracking (plan.md) commit to updating — not just extending — the already-shipped `AccountabilityServiceTest`/`OrganizationIntegrationTest`, since FR-004/FR-005 will otherwise break their existing `assignSchoolManager` cases.

**Organization**: Tasks are grouped by user story (spec.md's three: US1/US2 = P1, US3 = P2).

**Cross-cutting note**: This feature extends the already-shipped `organization` module in place — no new bounded-context package, no new `ArchitectureTest` rule, no new frontend page (extends the existing `AssignmentsPage`). No new backend dependencies are needed. The exact Flyway migration number is left as `V<next>` throughout (research.md §5) — resolve it against whatever's actually in `backend/src/main/resources/db/migration/` at implementation time.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: Maps the task to spec.md's US1–US3

## Phase 1: Setup

- [ ] T001 Create Flyway migration `backend/src/main/resources/db/migration/V<next>__create_zone_tables.sql` (research.md §5 — determine `<next>` from the highest existing `V<n>` at implementation time; do not renumber any other module's migration) creating: `organization_zone` (`id UUID PK`, `name VARCHAR NOT NULL`, `created_at TIMESTAMPTZ NOT NULL`, `created_by UUID NOT NULL`); `organization_zone_manager_assignment` (`id UUID PK`, `zone_id UUID NOT NULL`, `manager_id UUID NOT NULL`, `effective_from TIMESTAMPTZ NOT NULL`, `effective_to TIMESTAMPTZ NULLABLE`, `assigned_by UUID NOT NULL`, `assigned_at TIMESTAMPTZ NOT NULL`) with partial unique index `CREATE UNIQUE INDEX ux_zone_manager_assignment_current ON organization_zone_manager_assignment (zone_id, manager_id) WHERE effective_to IS NULL` — **on the pair, not `zone_id` alone** (data-model.md, research.md §1 — a Zone may have more than one current Manager); `organization_school_zone_assignment` (same shape as `organization_school_assignment`, `zone_id` in place of `manager_id`) with partial unique index `CREATE UNIQUE INDEX ux_school_zone_assignment_current ON organization_school_zone_assignment (school_id) WHERE effective_to IS NULL` (at most one current Zone per School, matching `SchoolAssignment`'s existing pattern exactly)

## Phase 2: Foundational (Blocking Prerequisites)

**🚨 CRITICAL**: No user story task may start until this phase is complete.

- [ ] T002 [P] Create `Zone` JPA entity in `backend/src/main/java/com/hls/organization/internal/Zone.java`: `id`, `name`, `createdAt`, `createdBy` — constructor sets all fields once, no setters (data-model.md: "Never deleted once created")
- [ ] T003 [P] Create `ZoneManagerAssignment` JPA entity in `backend/src/main/java/com/hls/organization/internal/ZoneManagerAssignment.java`: same shape/invariants as `SchoolAssignment` (`id`, `zoneId`, `managerId`, `effectiveFrom`, `effectiveTo`, `assignedBy`, `assignedAt`, an `end(Instant)` method settable exactly once, an `isCurrent()` helper)
- [ ] T004 [P] Create `SchoolZoneAssignment` JPA entity in `backend/src/main/java/com/hls/organization/internal/SchoolZoneAssignment.java`: identical shape/invariants to T003 with `schoolId`/`zoneId` in place of `schoolId`/`managerId`
- [ ] T005 [P] Create `ZoneRepository` in `backend/src/main/java/com/hls/organization/internal/ZoneRepository.java`: extends bare `Repository<Zone, UUID>` (not `JpaRepository`), declares only `save(Zone)`, `findById(UUID)`, `findAll()` — no delete method (data-model.md invariant, mirrors `AuditEntryRepository`'s established pattern)
- [ ] T006 [P] Create `ZoneManagerAssignmentRepository` in `backend/src/main/java/com/hls/organization/internal/ZoneManagerAssignmentRepository.java`: `findByZoneIdAndEffectiveToIsNull(UUID zoneId)` (returns a `List`, not `Optional` — multiple current rows possible, research.md §1), `findByZoneIdAndManagerIdAndEffectiveToIsNull(UUID zoneId, UUID managerId)` (returns `Optional`, for the FR-004 membership check), and `endIfStillCurrent(UUID id, Instant now)` (`@Modifying`, conditional `UPDATE ... WHERE id = ? AND effective_to IS NULL`, same pattern as `SchoolAssignmentRepository`)
- [ ] T007 [P] Create `SchoolZoneAssignmentRepository` in `backend/src/main/java/com/hls/organization/internal/SchoolZoneAssignmentRepository.java`: `findBySchoolIdAndEffectiveToIsNull(UUID schoolId)`, `findBySchoolIdOrderByEffectiveFromAsc(UUID schoolId)`, `endIfStillCurrent(UUID id, Instant now)` — same shape as `SchoolAssignmentRepository`
- [ ] T008 [P] Create `SchoolManagerNotInZoneException` in `backend/src/main/java/com/hls/organization/api/SchoolManagerNotInZoneException.java`: public `RuntimeException` (research.md §3 — distinct from `AssignmentConflictException`; callers of `AccountabilityCommands` must be able to catch it)

**Checkpoint**: Foundation ready — user story phases below may now begin.

---

## Phase 3: User Story 1 - Director/Admin Defines Zones and Their Managers (Priority: P1) 🎯 MVP

**Goal**: Director/Admin can create a Zone and assign one or more Managers to cover it, with more than one Manager able to be simultaneously current for the same Zone.

**Independent Test**: Create a Zone, assign a Manager to it, confirm that Manager is retrievable as currently covering it; assign a second, different Manager to the same Zone and confirm both are current at once.

### Tests for User Story 1

> Write these first; confirm they fail before implementing.

- [ ] T009 [P] [US1] `ZoneServiceTest` cases: `createZone` persists a new Zone retrievable by id (US1 AC1); `assignManagerToZone` creates a currently-open `ZoneManagerAssignment` (US1 AC2); assigning a **second, different** Manager to the same Zone leaves both currently open at once (US1 AC3, data-model.md's `(zone_id, manager_id)` uniqueness, not `zone_id` alone); `removeManagerFromZone` ends only that Manager's row, leaving any other Manager's row for the same Zone untouched (US1 AC4) — in `backend/src/test/java/com/hls/organization/ZoneServiceTest.java`
- [ ] T010 [P] [US1] `OrganizationIntegrationTest` case covering quickstart.md Scenario 1: real HTTP `POST /organization/zones`, two `POST .../managers` calls with different `managerId`s, then confirm both appear via a lookup — in `backend/src/test/java/com/hls/organization/OrganizationIntegrationTest.java`

### Implementation for User Story 1

- [ ] T011 [US1] Implement `ZoneService` (partial: Zone + Zone-Manager operations only) in `backend/src/main/java/com/hls/organization/internal/ZoneService.java`: `createZone(name, actingUserId)`, `assignManagerToZone(zoneId, managerId, actingUserId)`, `removeManagerFromZone(zoneId, managerId, actingUserId)`, `currentManagersForZone(zoneId)` — depends on T002, T003, T005, T006
- [ ] T012 [US1] Implement `ZoneController` (partial) in `backend/src/main/java/com/hls/organization/internal/ZoneController.java` per `contracts/zone-api.yaml`: `POST /api/v1/organization/zones`, `GET /api/v1/organization/zones` (list, for discovery), `POST /api/v1/organization/zones/{zoneId}/managers`, `DELETE /api/v1/organization/zones/{zoneId}/managers/{managerId}` — reads `@AuthenticationPrincipal Jwt` directly, same Director/Admin check `OrganizationController` already uses — depends on T011
- [ ] T013 [US1] Extend `frontend/src/pages/AssignmentsPage/organizationClient.ts` and `AssignmentsPage.tsx` (+ `AssignmentsPage.test.tsx`) with a Zone section: create Zone, assign/remove Manager (research.md §6 — no new page) — depends on T012

**Checkpoint**: User Story 1 fully functional and independently testable — Zones and Zone-Manager assignment usable on their own, before the School-Manager constraint (US2) exists.

---

## Phase 4: User Story 2 - A School's Manager Must Come From Its Zone's Managers (Priority: P1)

**Goal**: Assigning a School's accountable Manager is rejected unless that Manager currently covers the School's Zone; Teacher-Manager assignment remains completely untouched.

**Independent Test**: Assign a School to a Zone with a known Manager pool, then attempt to assign that School's Manager both inside and outside that pool; separately, confirm `assignTeacherManager` still works with zero Zone setup.

**Depends on**: User Story 1 (needs `ZoneService`'s Zone/Zone-Manager operations to exist first — there's nothing to test a School's Manager *against* without a Zone that has Managers).

### Tests for User Story 2

- [ ] T014 [P] [US2] **Update** `AccountabilityServiceTest`: every existing test case that calls `assignSchoolManager` now first seeds a Zone with the chosen Manager assigned to it (via the mocked `ZoneService`/repositories) — these currently pass without any Zone setup and will start failing once T017/T018 land, so this update must ship in the same change, not after (plan.md Complexity Tracking). Add new cases: assigning within the Zone succeeds (US2 AC1); assigning a Manager not in the Zone throws `SchoolManagerNotInZoneException` (US2 AC2, FR-004); a School with no Zone assigned throws (US2 AC4, FR-005); a Zone with zero current Managers throws (FR-005); and a case proving `assignTeacherManager` succeeds with **no** Zone/Zone-Manager setup at all (SC-004) — in `backend/src/test/java/com/hls/organization/AccountabilityServiceTest.java`
- [ ] T015 [P] [US2] **Update** `OrganizationIntegrationTest`: existing `assignSchoolManager`-calling cases now seed a Zone/Zone-Manager assignment first via real HTTP calls; add cases covering quickstart.md Scenario 2 (in-zone succeeds, out-of-zone or no-zone → `422`) and Scenario 4 (`assignTeacherManager` succeeds with zero Zone setup, proving SC-004 over real HTTP, not just the unit level) — in `OrganizationIntegrationTest.java`

### Implementation for User Story 2

- [ ] T016 [US2] Extend `ZoneService` (School-Zone operations) in `backend/src/main/java/com/hls/organization/internal/ZoneService.java`: `assignSchoolToZone(schoolId, zoneId, endsAssignmentId, actingUserId)` (same create/reassign/conflict shape as `AccountabilityService.assignSchoolManager`, FR-011's conflict semantics reused) and `isManagerCurrentlyInSchoolsZone(schoolId, managerId)` (FR-004/FR-005's membership check: resolve the School's current `SchoolZoneAssignment`, then check a currently-open `ZoneManagerAssignment` exists for that `(zoneId, managerId)` pair — `false` if the School has no current Zone or the Zone has no current Managers) — depends on T004, T007, T011
- [ ] T017 [US2] Modify `AccountabilityService.assignSchoolManager(...)` in `backend/src/main/java/com/hls/organization/internal/AccountabilityService.java`: call `zoneService.isManagerCurrentlyInSchoolsZone(schoolId, managerId)` before the existing assign/reassign logic runs, throwing `SchoolManagerNotInZoneException` if `false` (FR-004/FR-005, research.md §2) — **`assignTeacherManager` is not modified at all** (FR-007/FR-008) — depends on T008, T016
- [ ] T018 [US2] Add `POST /api/v1/organization/school-zone-assignments` to `ZoneController` per `contracts/zone-api.yaml`, and a new `@ExceptionHandler(SchoolManagerNotInZoneException.class)` returning `422` in `backend/src/main/java/com/hls/organization/internal/OrganizationController.java` — depends on T016, T017
- [ ] T019 [US2] Extend `AssignmentsPage`: assign-School-to-Zone control, and surface the new `422` error message on the existing School-assignment form (research.md §6) — depends on T013, T018

**Checkpoint**: User Stories 1 and 2 both independently functional; Teacher assignment proven unaffected by both test suites.

---

## Phase 5: User Story 3 - Director/Admin Sees Zone Coverage at a Glance (Priority: P2)

**Goal**: A single lookup shows a Zone's currently assigned Managers and currently assigned Schools together.

**Independent Test**: Set up a Zone with known Managers (US1) and Schools (US2), then confirm one lookup returns exactly that Zone's current Managers and Schools, including the empty-Schools case.

**Depends on**: User Stories 1 and 2 (coverage has nothing meaningful to show without both Managers and Schools existing).

### Tests for User Story 3

- [ ] T020 [P] [US3] `ZoneServiceTest` cases: `zoneCoverage(zoneId)` returns the Zone's current `managerIds` and current `schoolIds` together (US3 AC1); a Zone with Managers but no Schools yet returns an empty `schoolIds` list, not an error (US3 AC2) — in `ZoneServiceTest.java`
- [ ] T021 [P] [US3] `OrganizationIntegrationTest` case covering quickstart.md Scenario 3 — in `OrganizationIntegrationTest.java`

### Implementation for User Story 3

- [ ] T022 [US3] Implement `zoneCoverage(zoneId)` in `ZoneService`: aggregates current `ZoneManagerAssignment` rows and current `SchoolZoneAssignment` rows for the given Zone into one view (FR-006) — depends on T011, T016
- [ ] T023 [US3] Implement `GET /api/v1/organization/zones/{zoneId}` in `ZoneController` per `contracts/zone-api.yaml`, returning the coverage view from T022 — depends on T022
- [ ] T024 [US3] Extend `AssignmentsPage`: Zone coverage view (select a Zone, see its current Managers and Schools) — depends on T019, T023

**Checkpoint**: All three user stories independently functional.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [ ] T025 [P] Run all 4 quickstart.md scenarios end-to-end via their automated equivalents: `cd backend && ./mvnw test -Dtest=ZoneServiceTest,AccountabilityServiceTest,OrganizationIntegrationTest,OrganizationModuleTest` and `cd frontend && npx vitest run src/pages/AssignmentsPage`
- [ ] T026 [P] Confirm `ZoneController`'s structured log output already carries `requestId`/`userId`/`role` via Identity's app-wide `SecurityMdcInterceptor`/`CorrelationIdFilter` with no extra wiring needed, the same finding Organization's, Audit's, and (pending) Teacher's own Polish phases already confirmed — confirm by inspection; add an explicit test only if it doesn't already hold
- [ ] T027 Run the full ArchUnit + Spring Modulith verification suite (`./mvnw test -Dtest=ArchitectureTest,OrganizationModuleTest,IdentityModuleTest,AuditModuleTest,ApplicationModulesTest`) and confirm no boundary rule changes are needed — every new type is `organization.internal`, so the existing rule already covers it
- [ ] T028 [P] Finalize T001's Flyway version number against whatever has actually shipped by the time this feature is implemented (research.md §5) — rename the migration file if specs/005-teacher claimed `V4` first
- [ ] T029 [P] Update `docs/HLS SDD Implementation Plan & Deliverables Tracker.md` row 4a (added 2026-09-22) from "Not started" to Done once all phases above pass

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies.
- **Foundational (Phase 2)**: Depends on Setup — blocks every user story phase.
- **User Stories (Phase 3–5)**: All depend on Foundational. Unlike most prior specs' user stories, these are **not** mutually independent: US2 needs US1's `ZoneService` (Zone/Zone-Manager operations) to exist first, and US3 needs both US1 (Managers) and US2 (Schools) to have anything meaningful to show. This mirrors specs/003's own US1→US2 dependency (reassign builds on assign) rather than the fully-parallel case Audit's or Teacher's stories were.
- **Polish (Phase 6)**: Depends on all three user stories.

### Parallel Opportunities

- T001 (Setup) has nothing to run alongside — it's the only setup task.
- T002–T008 (Foundational) can run in parallel once T001's migration is written.
- US1 must land first (T011's `ZoneService` is extended, not replaced, by T016); within US1, T009/T010 (tests) run in parallel with each other before T011 begins.
- Within US2 and US3, `[P]`-marked test tasks run in parallel with each other before their implementation tasks begin.

## Parallel Example: Foundational Phase

```bash
Task: "Zone JPA entity in backend/src/main/java/com/hls/organization/internal/Zone.java"
Task: "ZoneManagerAssignment JPA entity in backend/src/main/java/com/hls/organization/internal/ZoneManagerAssignment.java"
Task: "SchoolZoneAssignment JPA entity in backend/src/main/java/com/hls/organization/internal/SchoolZoneAssignment.java"
Task: "ZoneRepository in backend/src/main/java/com/hls/organization/internal/ZoneRepository.java"
Task: "ZoneManagerAssignmentRepository in backend/src/main/java/com/hls/organization/internal/ZoneManagerAssignmentRepository.java"
Task: "SchoolZoneAssignmentRepository in backend/src/main/java/com/hls/organization/internal/SchoolZoneAssignmentRepository.java"
Task: "SchoolManagerNotInZoneException in backend/src/main/java/com/hls/organization/api/SchoolManagerNotInZoneException.java"
```

## Implementation Strategy

### MVP First

User Story 1 alone (Zones + Zone-Manager assignment) is a real, deployable increment — Director/Admin can start organizing Managers into Zones immediately, even before the School-Manager constraint exists. But the actual *correction* this feature exists for is User Story 2 (the constraint itself); a demo that stops at US1 hasn't yet fixed the leakage Constitution Principle II names. A realistically meaningful v1 needs **US1 + US2** together. US3 (coverage view) is valuable operational visibility but changes no behavior on its own.

1. Setup → Foundational (blocking)
2. US1 → validate independently (Zones and Zone-Manager assignment, no constraint yet)
3. US2 → validate independently (the constraint itself + the Teacher-unaffected proof) — **this is where the two existing shipped test files get updated, not just extended**
4. US3 → validate independently (coverage view)

### Format Validation

All 29 tasks above follow `- [ ] T### [P?] [Story?] Description with file path`: Setup/Foundational/Polish tasks carry no `[Story]` label; every Phase 3–5 task carries its `[US#]` label; every task names a concrete file path.
