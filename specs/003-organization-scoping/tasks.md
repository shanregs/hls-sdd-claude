---

description: "Task list for the Organization module implementation"
---

# Tasks: Organization — Manager/School/Teacher Accountability

**Input**: Design documents from `/specs/003-organization-scoping/`

**Prerequisites**: plan.md (updated 2026-09-22 against Identity's real implementation), spec.md, research.md, data-model.md, contracts/organization-api.yaml, quickstart.md (all present)

**Tests**: Included. Constitution Principle VII ("test coverage for calculation logic, access rules, and integration boundaries") and this module's own Constitution Check commit FR-003/FR-010/FR-011/FR-013 (concurrency-safety and history-integrity rules) to test-first development, mirroring spec 002's approach.

**Organization**: Tasks are grouped by user story (spec.md's three: US1/US2 = P1, US3 = P2).

**Cross-cutting note (2026-09-22 re-plan)**: no `CurrentUserResolver` port exists in this design — `OrganizationController` reads `@AuthenticationPrincipal Jwt` directly, the same pattern `identity.internal.AuthController` uses. No new backend dependencies are needed; Identity's implementation already added everything (JPA, Flyway + its Spring Boot 4 glue module, Spring Modulith, Spring Security/OAuth2-resource-server) to `backend/pom.xml`.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: Maps the task to spec.md's US1–US3

## Phase 1: Setup

- [X] T001 [P] Create `backend/src/main/java/com/hls/organization/api/` and `backend/src/main/java/com/hls/organization/internal/` package directories with a `package-info.java` marker in each, matching Identity's (spec 002) `api`/`internal` split — **no `pom.xml` changes**: everything this module needs (JPA, Flyway, Modulith, Security) is already present

## Phase 2: Foundational (Blocking Prerequisites)

**🚨 CRITICAL**: No user story task may start until this phase is complete.

- [X] T002 Create Flyway migration `backend/src/main/resources/db/migration/V2__create_organization_tables.sql` (Identity's migration is `V1` — do not renumber it) creating:
  - `organization_school_assignment` (id UUID PK, school_id UUID NOT NULL, manager_id UUID NOT NULL, effective_from TIMESTAMPTZ NOT NULL, effective_to TIMESTAMPTZ NULLABLE — `NULL` means current per data-model.md, assigned_by UUID NOT NULL, assigned_at TIMESTAMPTZ NOT NULL) with a **partial unique index** `CREATE UNIQUE INDEX ux_school_assignment_current ON organization_school_assignment (school_id) WHERE effective_to IS NULL` (research.md §1 — enforced at the DB level, not just application code)
  - `organization_teacher_assignment` — identical shape with `teacher_id` in place of `school_id`, and its own partial unique index `ux_teacher_assignment_current`
- [X] T003 [P] Create `SchoolAssignment` JPA entity in `backend/src/main/java/com/hls/organization/internal/SchoolAssignment.java` mapping `organization_school_assignment`; expose a method to end the assignment (`effectiveTo`, set exactly once — data-model.md invariant) and never allow mutating any other field after creation
- [X] T004 [P] Create `TeacherAssignment` JPA entity in `backend/src/main/java/com/hls/organization/internal/TeacherAssignment.java` — identical shape/invariants to `SchoolAssignment`, kept as a fully separate table per data-model.md ("FR-013 requires School-level and Teacher-level accountability to be independently queryable and independently mutable")
- [X] T005 [P] Create `SchoolAssignmentRepository` in `backend/src/main/java/com/hls/organization/internal/SchoolAssignmentRepository.java`: `findBySchoolIdAndEffectiveToIsNull`, `findBySchoolIdOrderByEffectiveFromAsc`, and a conditional-update method for the conflict check (`UPDATE ... WHERE id = ? AND effective_to IS NULL`, research.md §2) — a `@Modifying` query returning the affected-row count, not an entity
- [X] T006 [P] Create `TeacherAssignmentRepository` in `backend/src/main/java/com/hls/organization/internal/TeacherAssignmentRepository.java` — same shape as T005 for `teacherId`
- [X] T007 [P] Create the `api/dto` value types in `backend/src/main/java/com/hls/organization/api/dto/`: `AccountabilityAnswer.java` (state: `CURRENT_MANAGER`/`UNASSIGNED`/`UNKNOWN_IDENTIFIER` + nullable managerId — **signature locked to match Identity's already-tested `OrganizationAccountabilityStandIn.Answer` exactly, research.md §7**), `PortfolioItem.java` (itemType, itemId, since), `AssignmentHistoryEntry.java` (id, managerId, effectiveFrom, effectiveTo, assignedBy, assignedAt), `UnassignedItem.java` (itemType, itemId, nullable lastEndedAt)
- [X] T008 [P] Extend `backend/src/test/java/com/hls/ArchitectureTest.java` with an `organization`-specific rule: nothing outside `com.hls.organization` may depend on `com.hls.organization.internal..`, mirroring the existing `identity` rule
- [X] T009 [P] Create `backend/src/test/java/com/hls/organization/OrganizationModuleTest.java` using Spring Modulith's `@ApplicationModuleTest`, with the same H2 override `IdentityModuleTest` uses (`spring.datasource.url=jdbc:h2:mem:...`, `spring.flyway.enabled=false`, `spring.jpa.hibernate.ddl-auto=create-drop`) so this check has no Docker dependency

**Checkpoint**: Foundation ready — user story phases below may now begin.

---

## Phase 3: User Story 1 - Assign a Manager to Schools and Teachers (Priority: P1) 🎯 MVP

**Goal**: Director/Admin can assign a Manager to a School or Teacher that currently has none; assigning the already-current Manager again is a no-op (FR-010).

**Independent Test**: `POST /api/v1/organization/school-assignments` with a fresh `schoolId` and `managerId`, then `GET .../accountable-manager` returns that Manager immediately (SC-001).

### Tests for User Story 1

> Write these first; confirm they fail before implementing.

- [X] T010 [P] [US1] `AccountabilityServiceTest` cases: assign to an unassigned School/Teacher succeeds (US1 AC1/AC2), assigning the already-current Manager again is a no-op that creates no new row (US1 AC3, FR-010) — in `backend/src/test/java/com/hls/organization/AccountabilityServiceTest.java`
- [X] T011 [P] [US1] `OrganizationIntegrationTest` case: log in via Identity's real `/api/v1/auth/login` (Testcontainers Postgres, both `V1`+`V2` migrations applied), then assign and query over real HTTP — in `backend/src/test/java/com/hls/organization/OrganizationIntegrationTest.java`

### Implementation for User Story 1

- [X] T012 [US1] Create `AccountabilityQueries` interface in `backend/src/main/java/com/hls/organization/api/AccountabilityQueries.java`: `AccountabilityAnswer currentManagerForSchool(UUID schoolId)` and `AccountabilityAnswer currentManagerForTeacher(UUID teacherId)` — **exact method names/signatures required, research.md §7**, satisfies FR-007
- [X] T013 [US1] Create `AccountabilityCommands` interface in `backend/src/main/java/com/hls/organization/api/AccountabilityCommands.java`: assign/reassign/end methods for both School and Teacher (FR-001, FR-002, FR-004, FR-010)
- [X] T014 [US1] Implement `AccountabilityService` in `backend/src/main/java/com/hls/organization/internal/AccountabilityService.java`: assign-to-unassigned and no-op-on-same-manager logic (FR-001, FR-002, FR-010) — depends on T003–T007
- [X] T015 [US1] Implement `OrganizationController` in `backend/src/main/java/com/hls/organization/internal/OrganizationController.java`: `POST /api/v1/organization/school-assignments`, `POST /api/v1/organization/teacher-assignments`, `GET /api/v1/organization/schools/{schoolId}/accountable-manager`, `GET /api/v1/organization/teachers/{teacherId}/accountable-manager` per `contracts/organization-api.yaml` — reads `@AuthenticationPrincipal Jwt` directly (research.md §6, no `CurrentUserResolver`), rejects non-DIRECTOR/ADMIN callers — depends on T012–T014
- [X] T016 [US1] Create `frontend/src/pages/AssignmentsPage/AssignmentsPage.tsx` (+ test): assign form for School/Teacher (research.md §8) — reuses `authClient`'s existing Bearer-token pattern from spec 002, no changes to `identity`'s frontend code

**Checkpoint**: User Story 1 fully functional and independently testable (MVP).

---

## Phase 4: User Story 2 - Reassign Accountability Without Losing History (Priority: P1)

**Goal**: Director/Admin can reassign a School/Teacher to a different Manager without losing history; a past-dated query still returns the prior Manager; a conflicting concurrent reassignment is rejected, not silently overwritten.

**Independent Test**: Assign, then reassign; a current-time query returns the new Manager, a query dated before the reassignment still returns the old one, and the history endpoint shows both periods with no gap (SC-002, SC-003).

### Tests for User Story 2

- [X] T017 [P] [US2] `AccountabilityServiceTest` cases: reassign ends the prior row and opens a new one (US2 AC1), full history has no gaps/overlaps (US2 AC2), a reassignment naming an already-ended `endsAssignmentId` is rejected as a conflict rather than silently proceeding (FR-011), **and reassigning a School's accountable Manager leaves an independently-assigned Teacher's own accountable Manager unchanged (FR-013)** — added 2026-09-22 after `/speckit-analyze` found FR-013 had no dedicated test, unlike its implementation (T003/T004/T014/T019 already keep the two entirely separate) — in `AccountabilityServiceTest.java`
- [X] T018 [P] [US2] `OrganizationIntegrationTest` cases: two concurrent reassignments naming the same `endsAssignmentId` — exactly one succeeds (200), the other gets 409 (SC-004); **and quickstart.md Scenario 4 end to end** (assign a Teacher to Manager A, reassign that Teacher's School to Manager B, confirm the Teacher's own accountable-manager query still returns Manager A, FR-013) — in `OrganizationIntegrationTest.java`

### Implementation for User Story 2

- [X] T019 [US2] Implement reassignment in `AccountabilityService`: the conditional `UPDATE ... WHERE id = ? AND effective_to IS NULL` from T005/T006, raising a conflict exception (mapped to 409) when it affects zero rows (FR-011, research.md §2) — depends on T014
- [X] T020 [US2] Implement `managerForSchoolAsOf`/`managerForTeacherAsOf` query logic in `AccountabilityService` and `AccountabilityQueries` (FR-005) — depends on T012
- [X] T021 [US2] Implement `schoolAssignmentHistory`/`teacherAssignmentHistory` query logic (ordered by `effectiveFrom`, no gaps/overlaps) — depends on T012
- [X] T022 [US2] Extend `OrganizationController`: `asOf` query parameter on the accountable-manager endpoints, `GET .../assignment-history` endpoints, `DELETE .../{assignmentId}` end-without-replacement endpoints, and a `409` `ConflictError` response mapping for T019's exception — depends on T015, T019–T021
- [X] T023 [US2] Extend `AssignmentsPage`: reassign action and a history view — depends on T016, T022

**Checkpoint**: User Stories 1 and 2 both independently functional.

---

## Phase 5: User Story 3 - See a Manager's Current Portfolio and Catch Unassigned Schools/Teachers (Priority: P2)

**Goal**: Director/Admin can view a Manager's current portfolio and a list of every unassigned School/Teacher (including ones ended without a replacement).

**Independent Test**: Assign a subset of Schools/Teachers, leave others unassigned or end one without a replacement; the portfolio endpoint returns exactly the assigned subset, and the unassigned endpoint surfaces the rest, distinguishing never-assigned (`lastEndedAt: null`) from ended-without-replacement.

### Tests for User Story 3

- [X] T024 [P] [US3] `AccountabilityServiceTest` cases: portfolio returns exactly a Manager's current items and none of their past ones (US3 AC1), unassigned list includes both never-assigned and ended-without-replacement items with the right `lastEndedAt` distinction (US3 AC2/AC3, FR-009) — in `AccountabilityServiceTest.java`
- [X] T025 [P] [US3] `OrganizationIntegrationTest` case covering quickstart.md Scenario 5 (end without replacement → appears on the unassigned list within the same session, SC-005) — in `OrganizationIntegrationTest.java`

### Implementation for User Story 3

- [X] T026 [US3] Implement `portfolioForManager` and `unassigned` query logic in `AccountabilityService`/`AccountabilityQueries` (FR-008, FR-009) — depends on T012, T014
- [X] T027 [US3] Extend `OrganizationController`: `GET /api/v1/organization/managers/{managerId}/portfolio`, `GET /api/v1/organization/unassigned` per `contracts/organization-api.yaml` — depends on T015, T026
- [X] T028 [US3] Extend `AssignmentsPage`: portfolio view and unassigned-items list — depends on T016, T027

**Checkpoint**: All three user stories independently functional.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [X] T029 [P] Run all 5 quickstart.md scenarios end-to-end against a running instance (Docker available per 2026-09-22 session — no environment blocker expected this time) — done via quickstart.md's own "Automated equivalents": `OrganizationIntegrationTest` covers Scenarios 1/2/3/4/5 one-for-one (assign→query, reassign+asOf+history, conflicting-reassignment→409, School/Teacher independence, end-without-replacement→unassigned list) and `AccountabilityServiceTest` covers the same rules at the unit level; both suites pass
- [X] T030 [P] Confirm `OrganizationController`'s structured log output already carries `requestId`/`userId`/`role` via Identity's app-wide `SecurityMdcInterceptor`/`CorrelationIdFilter` (spec 002) with no extra wiring needed here; add an explicit test if it doesn't — confirmed by inspection: `HlsApplication`'s `CorrelationIdFilter` registration uses `addUrlPatterns("/*")` and `WebConfig` registers `SecurityMdcInterceptor` with no `addPathPatterns` restriction, so both apply app-wide, not just to Identity's own endpoints; no new test needed
- [X] T031 Run the full ArchUnit + Spring Modulith verification suite (`./mvnw test -Dtest=ArchitectureTest,OrganizationModuleTest,IdentityModuleTest`) and fix any boundary violation — extended to include `ApplicationModulesTest` too; found and fixed two real Spring Modulith gaps surfaced only once T032 gave Identity a real dependency on Organization: (1) neither module's `api` package was annotated `@org.springframework.modulith.NamedInterface("api")`, so Spring Modulith treated both as fully internal — added the annotation to `identity.api`'s and `organization.api`'s `package-info.java`; (2) `@NamedInterface`'s `propagate` only reaches types in the *same* package, not a nested one, so `organization.api.dto` needed its own `package-info.java` with the identical `"api"` name; (3) `IdentityModuleTest` used `@ApplicationModuleTest`'s default `BootstrapMode.STANDALONE`, which no longer boots now that `ManagerScopeGuard` has a real bean dependency on `organization.api.AccountabilityQueries` — switched to `BootstrapMode.DIRECT_DEPENDENCIES`. All 4 suites pass; full `mvn test` is 52/52 green
- [X] T032 [P] **Cross-spec follow-up (research.md §7)**: in `specs/002-identity-access`, swap `identity.internal.ManagerScopeGuard`'s import from `OrganizationAccountabilityStandIn` to this module's real `organization.api.AccountabilityQueries`, then delete `OrganizationAccountabilityStandIn.java`/`InMemoryOrganizationStandIn.java` — depends on T012 existing for real — done; `ManagerScopeGuardTest` and `IdentityIntegrationTest` updated to depend on the real `AccountabilityQueries`/`AccountabilityCommands` interfaces instead of the retired stand-in, no other call site needed to change (signature was locked to match exactly, research.md §7)
- [X] T033 [P] Update `docs/HLS SDD Implementation Plan & Deliverables Tracker.md` row 4 (Organization) status once all phases above pass

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies.
- **Foundational (Phase 2)**: Depends on Setup — blocks every user story phase.
- **User Stories (Phase 3–5)**: All depend on Foundational. US1 has no dependency on US2/US3. US2 reuses US1's assign path (T019 extends T014) but is independently testable once it's added. US3 reuses the same service/controller files but adds new methods, not new dependencies on US1/US2's own logic.
- **Polish (Phase 6)**: Depends on all three user stories. T032 additionally depends on this module actually existing (obviously) and should be done as the very last step, since it retires code in a different spec.

### Parallel Opportunities

- T001 (Setup) has nothing to run alongside — it's the only setup task.
- T003–T009 (Foundational) can run in parallel once T002's migration is written.
- Once Foundational completes, US1 must land first (US2/US3 both extend `AccountabilityService`/`OrganizationController`, the same files US1 creates) — this module's stories are less independent than spec 002's were, since there are only two shared implementation files instead of one-per-concern.
- Within each story, `[P]`-marked test tasks run in parallel with each other before implementation begins.

## Parallel Example: Foundational Phase

```bash
Task: "SchoolAssignment JPA entity in backend/src/main/java/com/hls/organization/internal/SchoolAssignment.java"
Task: "TeacherAssignment JPA entity in backend/src/main/java/com/hls/organization/internal/TeacherAssignment.java"
Task: "SchoolAssignmentRepository in backend/src/main/java/com/hls/organization/internal/SchoolAssignmentRepository.java"
Task: "TeacherAssignmentRepository in backend/src/main/java/com/hls/organization/internal/TeacherAssignmentRepository.java"
Task: "AccountabilityAnswer/PortfolioItem/AssignmentHistoryEntry/UnassignedItem DTOs in backend/src/main/java/com/hls/organization/api/dto/"
```

## Implementation Strategy

### MVP First

User Story 1 alone (assign to an unassigned School/Teacher) is the smallest deployable increment, but note the spec's own acceptance testing for User Story 1 (`Independent Test`) doesn't exercise reassignment or history — those are US2. A realistically usable v1 needs **US1 + US2** together, since "assign once and never reassign" isn't how HLS actually operates (Requirements §3.2 notes teachers move between schools mid-month). US3 (portfolio/unassigned visibility) is valuable but can follow.

1. Setup → Foundational (blocking)
2. US1 → validate independently
3. US2 → validate independently (reassignment + history + conflict detection)
4. US3 → validate independently (portfolio + unassigned visibility)

### Format Validation

All 33 tasks above follow `- [ ] T### [P?] [Story?] Description with file path`: Setup/Foundational/Polish tasks carry no `[Story]` label; every Phase 3–5 task carries its `[US#]` label; every task names a concrete file path.
