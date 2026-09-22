---

description: "Task list for the Audit Trail module implementation"
---

# Tasks: Audit Trail

**Input**: Design documents from `/specs/004-audit/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/audit-api.yaml, quickstart.md (all present)

**Tests**: Included. Constitution Principle VII ("test coverage for calculation logic, access rules, and integration boundaries") and this module's own Constitution Check commit FR-004/FR-005/FR-006/FR-010/FR-011 (atomicity, immutability, attribution, ordering) to test-first development, mirroring specs 002/003's approach.

**Organization**: Tasks are grouped by user story (spec.md's three: US1 = P1, US2 = P2, US3 = P3).

**Cross-cutting note**: No real caller module exists yet (research.md §1) — attendance/payroll/school-payment/expense/substitution are all sequenced after this module in the tracker. User Story 1's tests call `AuditWriter` directly to simulate a future caller, exactly as spec.md's own Assumptions section describes. No new backend dependencies are needed; Identity's implementation already added everything (JPA, Flyway + its Spring Boot 4 glue module, Spring Modulith, Spring Security/OAuth2-resource-server) to `backend/pom.xml`.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: Maps the task to spec.md's US1–US3

## Phase 1: Setup

- [X] T001 Create `backend/src/main/java/com/hls/audit/api/` and `backend/src/main/java/com/hls/audit/internal/` package directories with a `package-info.java` marker in each, matching Identity's/Organization's `api`/`internal` split. Annotate `api/package-info.java` with `@org.springframework.modulith.NamedInterface("api")` from the start — Organization's plan (specs/003 tasks.md T031) found this missing only after the fact and had to retrofit it; do it here up front. **No `pom.xml` changes**: everything this module needs (JPA, Flyway, Modulith, Security) is already present.

## Phase 2: Foundational (Blocking Prerequisites)

**🚨 CRITICAL**: No user story task may start until this phase is complete.

- [X] T002 Create Flyway migration `backend/src/main/resources/db/migration/V3__create_audit_tables.sql` (Identity's is `V1`, Organization's is `V2` — do not renumber either) creating `audit_entry`: `id UUID PRIMARY KEY`, `sequence_no BIGSERIAL NOT NULL` (data-model.md's definitive ordering key, research.md §5), `source_module VARCHAR(64) NOT NULL`, `entity_type VARCHAR(128) NOT NULL`, `entity_id VARCHAR(128) NOT NULL`, `action VARCHAR(32) NOT NULL CHECK (action IN ('CREATED','UPDATED','CORRECTED'))`, `summary TEXT NOT NULL`, `before_value TEXT NULLABLE`, `after_value TEXT NULLABLE`, `actor_user_id UUID NOT NULL`, `actor_role VARCHAR(32) NULLABLE`, `occurred_at TIMESTAMPTZ NOT NULL`, `request_id VARCHAR(64) NULLABLE`; plus `CREATE INDEX idx_audit_entry_entity ON audit_entry (entity_type, entity_id, sequence_no)` for the history lookup (FR-007, FR-011)
- [X] T003 [P] Create `AuditEntry` JPA entity in `backend/src/main/java/com/hls/audit/internal/AuditEntry.java` mapping `audit_entry` per data-model.md's field table; constructor sets every field once at creation (`id`, `sequenceNo` left to the database/JPA-generated), no setters — nothing about this entity is ever mutated after insert (data-model.md invariant)
- [X] T004 [P] Create `AuditEntryRepository` in `backend/src/main/java/com/hls/audit/internal/AuditEntryRepository.java`: extends bare `org.springframework.data.repository.Repository<AuditEntry, UUID>` (not `JpaRepository`/`CrudRepository`) and declares only `save(AuditEntry entry)` and `findByEntityTypeAndEntityIdOrderBySequenceNoAsc(String entityType, String entityId)` — **deliberately no `delete`/`deleteById`/update method anywhere**, mirroring `identity.internal.AuthAuditEntryRepository`'s existing pattern exactly (research.md §4; this is the primary enforcement mechanism for FR-005/FR-006)
- [X] T005 [P] Create the `api/dto` value types in `backend/src/main/java/com/hls/audit/api/dto/`, with a `package-info.java` also annotated `@NamedInterface("api")` (T001's lesson applies here too, since `@NamedInterface`'s `propagate` doesn't reach nested packages automatically — specs/003 tasks.md T031 finding 2): `AuditAction.java` (enum `CREATED`, `UPDATED`, `CORRECTED`), `AuditRecordRequest.java` (`sourceModule`, `entityType`, `entityId`, `action`, `summary`, `beforeValue`, `afterValue`, `actorUserId`, `actorRole`, `requestId` — **no `id`/`sequenceNo`/`occurredAt` fields**, `audit` assigns all three itself, research.md §5), `AuditEntryView.java` (`id`, `sequenceNo`, `sourceModule`, `entityType`, `entityId`, `action`, `summary`, `beforeValue`, `afterValue`, `actorUserId`, `actorRole`, `occurredAt`, `requestId`)
- [X] T006 [P] Extend `backend/src/test/java/com/hls/ArchitectureTest.java` with an `audit`-specific rule: nothing outside `com.hls.audit` may depend on `com.hls.audit.internal..`, mirroring the existing `identity`/`organization` rules
- [X] T007 [P] Create `backend/src/test/java/com/hls/audit/AuditModuleTest.java` using Spring Modulith's `@ApplicationModuleTest`, with the same H2 override `IdentityModuleTest`/`OrganizationModuleTest` use (`spring.datasource.url=jdbc:h2:mem:...`, `spring.flyway.enabled=false`, `spring.jpa.hibernate.ddl-auto=create-drop`); default `BootstrapMode.STANDALONE` is expected to be sufficient since `audit` depends on nothing from any other module and nothing yet depends on `audit` either (research.md §2) — revisit only if a real caller module later adds a dependency, the same way Organization's `T031` had to switch Identity's test to `DIRECT_DEPENDENCIES`

**Checkpoint**: Foundation ready — user story phases below may now begin.

---

## Phase 3: User Story 1 - Every Financial or Attendance Change Is Automatically Recorded (Priority: P1) 🎯 MVP

**Goal**: Any module can call `AuditWriter.record(...)` in-process when it creates, changes, or corrects a financial/attendance-affecting record, and a permanent, correctly-attributed entry results.

**Independent Test**: Call `AuditWriter.record(...)` directly (simulating a future caller, research.md §1) with a representative request for each of the five illustrative record types (attendance, payroll, school payment, expense, substitution); confirm each produces a persisted row with the right actor, timestamp, and before/after values.

### Tests for User Story 1

> Write these first; confirm they fail before implementing.

- [X] T008 [P] [US1] `AuditServiceTest` cases: `record(...)` never trusts a caller-supplied id/timestamp (`AuditRecordRequest` has none to trust) and always assigns `id`/`occurredAt` itself (research.md §5); a `CREATED` action stores a null `beforeValue` while `UPDATED`/`CORRECTED` store both `beforeValue` and `afterValue` (US1 AC1–AC5); a request with a null `actorUserId` is rejected rather than recorded (FR-010) — in `backend/src/test/java/com/hls/audit/AuditServiceTest.java`
- [X] T009 [P] [US1] `AuditIntegrationTest` case: Testcontainers Postgres with `V1`–`V3` migrations applied, calling `AuditWriter.record(...)` directly (no real caller module exists, research.md §1) once per illustrative record type from spec.md User Story 1's five acceptance scenarios (attendance edit, payroll correction, school payment, expense approval, substitution assignment); read each back via the repository and confirm the persisted `sequence_no`/`occurred_at`/actor/before/after match — in `backend/src/test/java/com/hls/audit/AuditIntegrationTest.java`

### Implementation for User Story 1

- [X] T010 [US1] Create `AuditWriter` interface in `backend/src/main/java/com/hls/audit/api/AuditWriter.java`: `UUID record(AuditRecordRequest request)` (FR-002, FR-003, FR-004, FR-010) — depends on T005
- [X] T011 [US1] Implement `AuditService` (partial: `AuditWriter` only) in `backend/src/main/java/com/hls/audit/internal/AuditService.java`: assigns `id = UUID.randomUUID()` and `occurredAt = clock.instant()` (reuses `HlsApplication`'s existing app-wide `Clock` bean, no new bean needed), rejects a null `actorUserId` (FR-010), builds an `AuditEntry` and calls `repository.save(...)` — runs inside whatever transaction the caller is already in (default Spring `@Transactional` propagation is sufficient for FR-004's atomicity, research.md §3; no explicit `@Transactional` annotation needed on this method itself) — depends on T003, T004, T010

**Checkpoint**: User Story 1 fully functional and independently testable (MVP) — auditable via direct `AuditWriter` calls and repository inspection; no REST/UI needed yet.

---

## Phase 4: User Story 2 - Directors and Admins Can View a Record's Full History (Priority: P2)

**Goal**: A Director or Admin can retrieve the complete, chronologically ordered history of any record by its `(entityType, entityId)`; anyone else is denied.

**Independent Test**: `GET /api/v1/audit/{entityType}/{entityId}/history` as a Director/Admin returns every entry written for that pair, oldest first; the same call as a Manager returns 403.

### Tests for User Story 2

- [X] T012 [P] [US2] `AuditServiceTest` case: `history(entityType, entityId)` returns entries ordered by `sequenceNo` ascending (FR-011) and returns an empty list — not an error — for a syntactically valid pair with no recorded entries (edge case) — in `AuditServiceTest.java`
- [X] T013 [P] [US2] `AuditIntegrationTest` cases: a Director/Admin JWT gets `200` with the correct ordered history for a record with multiple prior entries (US2 AC1) and for a record with only its single creation entry (US2 AC2); a Manager JWT gets `403` (US2 AC3, FR-008, SC-005) — in `AuditIntegrationTest.java`

### Implementation for User Story 2

- [X] T014 [US2] Create `AuditReader` interface in `backend/src/main/java/com/hls/audit/api/AuditReader.java`: `List<AuditEntryView> history(String entityType, String entityId)` (FR-007) — depends on T005
- [X] T015 [US2] Implement `AuditReader` on `AuditService` in `backend/src/main/java/com/hls/audit/internal/AuditService.java`: delegates to `AuditEntryRepository.findByEntityTypeAndEntityIdOrderBySequenceNoAsc`, maps each `AuditEntry` to an `AuditEntryView` — depends on T004, T014
- [X] T016 [US2] Implement `AuditController` in `backend/src/main/java/com/hls/audit/internal/AuditController.java`: `GET /api/v1/audit/{entityType}/{entityId}/history` per `contracts/audit-api.yaml` — reads `@AuthenticationPrincipal Jwt` directly (no `CurrentUserResolver`, same pattern `OrganizationController` uses), returns `403` for callers without the DIRECTOR or ADMIN role claim (FR-008) — depends on T015
- [X] T017 [US2] Create `frontend/src/pages/AuditHistoryPage/AuditHistoryPage.tsx` (+ `AuditHistoryPage.test.tsx`): entity-type/entity-id input form, calls the history endpoint, renders an ordered table (action, summary, before/after, actor, timestamp), shows an access-denied message on `403` — reuses `authClient`'s existing Bearer-token pattern from spec 002, no changes to Identity's/Organization's frontend code — depends on T016
- [X] T018 [US2] Wire `AuditHistoryPage` into `frontend/src/App.tsx`'s `AuthenticatedView` nav switch (add an `"audit-history"` view and nav button alongside the existing `"assignments"`/`"sessions"`) — depends on T017

**Checkpoint**: User Stories 1 and 2 both independently functional.

---

## Phase 5: User Story 3 - History Can Never Be Altered, Only Added To (Priority: P3)

**Goal**: Prove the guarantee User Stories 1 and 2 already built in by construction — `AuditEntryRepository` (T004) has no update/delete method and `AuditController` (T016) has no write mapping, so there is no code path for anyone, including an Admin, to alter or remove an existing entry; a correction is always an additional `record(...)` call, never a change to an old row.

**Independent Test**: Attempt every write-shaped HTTP verb against the history endpoint's path and confirm none succeed; write a `CREATED` entry followed by a `CORRECTED` one for the same record and confirm both remain, unchanged.

### Tests for User Story 3

> No new production code is expected in this phase — US1/US2 already built the only write surface this module has. These tests confirm the guarantee holds, not add to it.

- [X] T019 [P] [US3] `AuditIntegrationTest` cases: `PUT`, `PATCH`, and `DELETE` to `/api/v1/audit/{entityType}/{entityId}/history` (and to a plausible single-entry path such as `/api/v1/audit/entries/{id}`, which was never implemented) return `404`/`405` — not `403` — as a Director/Admin caller, proving the operation doesn't exist rather than is merely forbidden (US3 AC1, AC2, FR-005, quickstart.md Scenario 5) — in `AuditIntegrationTest.java`
- [X] T020 [P] [US3] `AuditServiceTest`/`AuditIntegrationTest` case: record a `CREATED` entry, then record a `CORRECTED` entry for the same `(entityType, entityId)` with different `afterValue`; confirm `history(...)` returns both entries in order, the original `CREATED` entry's stored values are unchanged, and its `sequenceNo` remains the lower of the two (US3 AC3, FR-006, SC-004) — in `AuditServiceTest.java` and `AuditIntegrationTest.java`

**Checkpoint**: All three user stories independently functional.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [X] T021 [P] Run all 5 quickstart.md scenarios end-to-end via their automated equivalents: `cd backend && ./mvnw test -Dtest=AuditServiceTest,AuditIntegrationTest,AuditModuleTest` and `cd frontend && npx vitest run src/pages/AuditHistoryPage`
- [X] T022 [P] Confirm `AuditController`'s structured log output already carries `requestId`/`userId`/`role` via Identity's app-wide `SecurityMdcInterceptor`/`CorrelationIdFilter` (spec 002) with no extra wiring needed, the same finding Organization's Polish phase confirmed (specs/003 tasks.md T030) — confirm by inspection; add an explicit test only if it doesn't already hold
- [X] T023 Run the full ArchUnit + Spring Modulith verification suite (`./mvnw test -Dtest=ArchitectureTest,AuditModuleTest,OrganizationModuleTest,IdentityModuleTest,ApplicationModulesTest`) and fix any boundary violation surfaced by adding this third module
- [X] T024 [P] Update `docs/HLS SDD Implementation Plan & Deliverables Tracker.md`'s Audit row once all phases above pass

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies.
- **Foundational (Phase 2)**: Depends on Setup — blocks every user story phase.
- **User Stories (Phase 3–5)**: All depend on Foundational. US1 has no dependency on US2/US3. US2's read path (`AuditService`/`AuditController`) is a second implementation of the same two files US1 creates (write path) — technically the same files, but additive methods, not a rewrite, so US1 must land first but US2 doesn't require re-testing US1. US3 adds no production code at all; it only depends on US1 (T004, the repository) and US2 (T016, the controller) already existing to test against.
- **Polish (Phase 6)**: Depends on all three user stories.

### Parallel Opportunities

- T001 (Setup) has nothing to run alongside — it's the only setup task.
- T003–T007 (Foundational) can run in parallel once T002's migration is written.
- Once Foundational completes, US1 must land first (US2 extends the same `AuditService`/`AuditController` files US1's implementation tasks create) — same shared-file reasoning as specs/003.
- Within each story, `[P]`-marked test tasks run in parallel with each other before implementation begins.
- T019/T020 (US3) can run in parallel with each other, and don't block anything in Polish.

## Parallel Example: Foundational Phase

```bash
Task: "AuditEntry JPA entity in backend/src/main/java/com/hls/audit/internal/AuditEntry.java"
Task: "AuditEntryRepository in backend/src/main/java/com/hls/audit/internal/AuditEntryRepository.java"
Task: "AuditAction/AuditRecordRequest/AuditEntryView DTOs in backend/src/main/java/com/hls/audit/api/dto/"
Task: "ArchitectureTest audit-specific rule"
Task: "AuditModuleTest.java"
```

## Implementation Strategy

### MVP First

User Story 1 alone (the write path, provable only via direct `AuditWriter` calls since no real caller module exists yet) is the smallest deployable increment, and it's genuinely useful on its own — every future attendance/payroll/expense/substitution module can start calling it the moment it lands, even before US2's viewing UI exists. US2 (viewing) is what makes the recorded history actually usable by a Director/Admin, so a realistically demoable v1 needs **US1 + US2** together. US3 adds no new capability — it's proof, via tests, that a guarantee US1/US2 already built in actually holds.

1. Setup → Foundational (blocking)
2. US1 → validate independently (direct `AuditWriter` calls + repository inspection)
3. US2 → validate independently (real HTTP, real JWT auth, Director/Admin vs. Manager)
4. US3 → validate independently (immutability proof — no new code, only tests)

### Format Validation

All 24 tasks above follow `- [ ] T### [P?] [Story?] Description with file path`: Setup/Foundational/Polish tasks carry no `[Story]` label; every Phase 3–5 task carries its `[US#]` label; every task names a concrete file path.
