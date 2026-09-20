---

description: "Task list for System Status Page implementation"
---

# Tasks: System Status Page

**Input**: Design documents from `/specs/001-system-status-page/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/status-api.yaml, quickstart.md (all present)

**Tests**: Included — the constitution (Principle VII) requires test coverage for all modules, and plan.md/research.md §4 already commit to a unit + Testcontainers-integration testing strategy for this feature.

**Organization**: Tasks are grouped by user story (from spec.md) to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: Which user story this task belongs to (US1, US2, US3)
- File paths are exact, per plan.md's Project Structure

## Path Conventions

Web application (Option 2, per plan.md): `backend/src/...` (Java/Spring Boot, Maven), `frontend/src/...` (React/TypeScript), `docker-compose.yml` at repo root. Nothing under `backend/` or `frontend/` exists yet — this is the first feature, so Phase 1 also bootstraps the skeleton.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Bootstrap the repo skeleton — nothing under `backend/`/`frontend/` exists yet.

- [X] T001 Create backend Maven project with Spring Boot 4.1.1 parent, `spring-boot-starter-web`, `spring-boot-starter-jdbc`, PostgreSQL JDBC driver, and `micrometer-core` dependencies, targeting Java 25, in `backend/pom.xml`
- [X] T002 [P] Create the Spring Boot application entry point in `backend/src/main/java/com/hls/HlsApplication.java`
- [X] T003 [P] Create the frontend React + TypeScript project scaffold (`package.json`, `tsconfig.json`, dev-server config) in `frontend/package.json`
- [X] T004 [P] Create `docker-compose.yml` at the repo root wiring the backend app container to a Postgres container, per plan.md's single EC2/VM + Docker Compose deployment target
- [X] T005 [P] Configure `backend/src/main/resources/application.yml` with the PostgreSQL `DataSource` connection properties and an application-version property
- [X] T006 [P] Add an ArchUnit test dependency and an initial boundary rule asserting the `com.hls.status` package has no dependency on any other application package, per plan.md's Constitution Check (Principle V), in `backend/src/test/java/com/hls/ArchitectureTest.java`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Infrastructure every user story needs. **No user story work starts until this phase is complete.**

- [X] T007 [P] Create the `StatusResponse` DTO with fields `status` (enum `OK`/`DEGRADED`), `version` (string), `serverTime` (ISO-8601 datetime, IST offset), `dataStoreReachable` (boolean), `checkDurationMs` (integer) — and the derivation rule from data-model.md: "`status = OK` if and only if `dataStoreReachable = true`; otherwise `status = DEGRADED`" — in `backend/src/main/java/com/hls/status/StatusResponse.java`
- [X] T008 [P] Configure structured JSON logging (Logback + JSON encoder) with a request-correlation id propagated via MDC, per research.md §5, in `backend/src/main/resources/logback-spring.xml`
- [X] T009 [P] Create the base React app shell with a route to the status page that has no authentication guard, per FR-001 ("reachable without requiring staff to log in"), in `frontend/src/App.tsx`

**Checkpoint**: Foundation ready — user story implementation can now begin.

---

## Phase 3: User Story 1 - Confirm the system is running (Priority: P1) 🎯 MVP

**Goal**: A staff member opens the status page and sees an "OK" status, current version, and current IST server time; reloading reflects a fresh check, never a cached one.

**Independent Test**: Open the status page URL with the app and Postgres both running normally — the page shows "OK".

### Tests for User Story 1

> Write these tests FIRST; confirm they FAIL before implementing.

- [X] T010 [P] [US1] Unit test: `StatusService` returns `status=OK`, `dataStoreReachable=true` when the (mocked) `DataSource` connection is valid, in `backend/src/test/java/com/hls/status/StatusServiceTest.java`
- [X] T011 [P] [US1] Integration test (Testcontainers Postgres): `GET /api/status` returns HTTP 200 with `status="OK"` when Postgres is reachable, in `backend/src/test/java/com/hls/status/StatusIntegrationTest.java`
- [X] T012 [P] [US1] Frontend test: `StatusPage` renders "OK" status, version, and `serverTime` from a mocked successful API response, in `frontend/src/pages/StatusPage/StatusPage.test.tsx`

### Implementation for User Story 1

- [X] T013 [US1] Implement `StatusService.checkStatus()` using `Connection.isValid(3)` (research.md §1) to determine `dataStoreReachable`, assembling a `StatusResponse` (status, version, IST `serverTime`, `dataStoreReachable`, `checkDurationMs`), and incrementing a Micrometer counter `status.check.total{result=OK}` (research.md §5) — depends on T007; must make T010 pass — in `backend/src/main/java/com/hls/status/StatusService.java`
- [X] T014 [US1] Implement `StatusController` exposing `GET /api/status` per `contracts/status-api.yaml`, with no authentication/authorization configured (FR-001), always returning HTTP 200 — depends on T013; must make T011 pass — in `backend/src/main/java/com/hls/status/StatusController.java`
- [X] T015 [US1] Wire the application version (from Maven build metadata) into an application property consumed by `StatusService` — depends on T001, T013 — in `backend/src/main/resources/application.yml`
- [X] T016 [US1] Implement `StatusPage.tsx`: fetch `GET /api/status` on load, render status/version/serverTime, with no client-side caching across requests so a reload always reflects a fresh check (SC-002), mapping the API's "OK"/"DEGRADED" enum values to the spec's display labels ("OK"/"Degraded") — depends on T009; must make T012 pass — in `frontend/src/pages/StatusPage/StatusPage.tsx`

**Checkpoint**: User Story 1 is fully functional and independently testable — MVP.

---

## Phase 4: User Story 2 - Detect that the core data store is unreachable (Priority: P2)

**Goal**: A staff member opening the status page while the core data store is unavailable sees "Degraded" rather than a false "OK"; status returns to "OK" automatically once the data store recovers, with no app restart.

**Independent Test**: Stop Postgres, reload the status page — it shows "Degraded". Restart Postgres, reload again — it shows "OK".

### Tests for User Story 2

- [X] T017 [US2] Unit test: `StatusService` returns `status=DEGRADED`, `dataStoreReachable=false` when the (mocked) `DataSource` connection throws or is invalid, in `backend/src/test/java/com/hls/status/StatusServiceTest.java`
- [X] T018 [US2] Unit test: `StatusService` returns `status=DEGRADED` when the reachability check does not complete within the 3-second bound (FR-006), in `backend/src/test/java/com/hls/status/StatusServiceTest.java`
- [X] T019 [US2] Integration test (Testcontainers Postgres): pause the Postgres container mid-test (not stop — see note below) and confirm `GET /api/status` still returns HTTP 200 with `status="DEGRADED"` (Acceptance Scenario 1); unpause and confirm a subsequent call returns `status="OK"` without restarting the app (Acceptance Scenario 2), in `backend/src/test/java/com/hls/status/StatusIntegrationTest.java`
- [X] T020 [US2] Frontend test: `StatusPage` renders a "Degraded" state visually distinct from "OK" given a mocked Degraded API response, in `frontend/src/pages/StatusPage/StatusPage.test.tsx`

### Implementation for User Story 2

- [X] T021 [US2] Extend `StatusService.checkStatus()` to catch connection failures and apply the 3-second bounded wait, setting `dataStoreReachable=false`/`status=DEGRADED` on failure or timeout, and incrementing `status.check.total{result=DEGRADED}` — depends on T013; must make T017/T018 pass — in `backend/src/main/java/com/hls/status/StatusService.java`
- [X] T022 [US2] Confirm `StatusController` still returns HTTP 200 (never an error status) when `StatusService` reports `DEGRADED`, per FR-007 — depends on T014, T021; must make T019 pass — in `backend/src/main/java/com/hls/status/StatusController.java`
- [X] T023 [US2] Update `StatusPage.tsx` to visually distinguish the "Degraded" state from "OK", ensuring the "DEGRADED" enum value renders as "Degraded" per spec.md's display language — depends on T016; must make T020 pass — in `frontend/src/pages/StatusPage/StatusPage.tsx`

**Checkpoint**: User Stories 1 and 2 both work independently.

---

## Phase 5: User Story 3 - View status from any device (Priority: P3)

**Goal**: The status page remains fully readable on a phone-width screen, with no horizontal scrolling.

**Independent Test**: Open the status page at ~375px viewport width — status, version, and timestamp remain visible without horizontal scrolling.

### Tests for User Story 3

- [X] T024 [US3] Frontend test: `StatusPage` layout produces no horizontal overflow at a 375px-wide viewport (Acceptance Scenario 1), in `frontend/src/pages/StatusPage/StatusPage.test.tsx` — **caveat**: jsdom does not perform real CSS layout, so this is a structural proxy test (no fixed pixel widths), not a true rendered-overflow check; see quickstart.md for the manual verification that actually exercises this

### Implementation for User Story 3

- [X] T025 [US3] Apply responsive layout/CSS to `StatusPage.tsx` (stacked/flex layout, readable font sizing) so status, version, and timestamp remain fully visible without horizontal scrolling at phone width — depends on T023; must make T024 pass — in `frontend/src/pages/StatusPage/StatusPage.tsx`

**Checkpoint**: All three user stories are independently functional.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [ ] T026 [P] Execute quickstart.md's manual validation steps end-to-end for all three user stories and record results, in `specs/001-system-status-page/quickstart.md`
- [ ] T027 Run the full automated test suite (`mvn test` in `backend/`, `npm test` in `frontend/`) and confirm all tests pass, including the ArchUnit boundary rule from T006
- [ ] T028 [P] Review structured log output and confirm a correlation id is present on status-check log lines, per research.md §5, in `backend/src/main/resources/logback-spring.xml`
- [X] T029 [P] [US1] Add an assertion to `StatusServiceTest` (or `StatusIntegrationTest`) confirming `checkDurationMs` stays under the 3-second bound under normal conditions, per SC-004, in `backend/src/test/java/com/hls/status/StatusServiceTest.java`
- [X] T030 [P] Add a frontend timing check confirming the rendered status is available within 5 seconds of page load, per SC-001, in `frontend/src/pages/StatusPage/StatusPage.test.tsx` — **caveat**: verifies the component itself adds no artificial delay; true end-to-end timing needs a real browser/network, covered manually via quickstart.md

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately.
- **Foundational (Phase 2)**: Depends on Setup completion — blocks all user stories.
- **User Stories (Phase 3+)**: All depend on Foundational phase completion. US1 has no dependency on US2/US3. US2 and US3 both extend files US1 created (`StatusService.java`, `StatusController.java`, `StatusPage.tsx`), so — despite being independently testable in behavior — they are best implemented sequentially in priority order (P1 → P2 → P3) rather than in parallel, to avoid file conflicts.
- **Polish (Phase 6)**: Depends on all three user stories being complete.

### Within Each User Story

- Tests are written first and must fail before implementation.
- Backend service logic before controller before frontend consumption.
- Story complete (all its tests passing) before moving to the next priority.

### Parallel Opportunities

- T002, T003, T004, T005, T006 (Setup) can all run in parallel once T001 exists.
- T007, T008, T009 (Foundational) can all run in parallel.
- T010, T011, T012 (US1 tests) can all run in parallel — three different files.
- T026 and T028 (Polish) can run in parallel.
- US2's and US3's test/implementation tasks mostly touch files already created in US1, so they are marked without `[P]` even within their own phase — true team parallelism on this feature is limited by its small file footprint.

---

## Parallel Example: User Story 1

```bash
# Launch all three US1 tests together (different files):
Task: "Unit test StatusService OK path in backend/src/test/java/com/hls/status/StatusServiceTest.java"
Task: "Integration test GET /api/status OK path in backend/src/test/java/com/hls/status/StatusIntegrationTest.java"
Task: "Frontend test StatusPage OK rendering in frontend/src/pages/StatusPage/StatusPage.test.tsx"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup.
2. Complete Phase 2: Foundational (blocks all stories).
3. Complete Phase 3: User Story 1.
4. **STOP and VALIDATE**: run `mvn test`/`npm test`, then the manual quickstart.md steps for User Story 1.
5. This is already a demonstrable, deployable warm-up feature at this point.

### Incremental Delivery

1. Setup + Foundational → foundation ready.
2. User Story 1 → test independently → demo (MVP).
3. User Story 2 → test independently → demo (Degraded detection now works).
4. User Story 3 → test independently → demo (responsive layout confirmed).
5. Polish (Phase 6) → full quickstart.md pass + observability check.

---

## Notes

- `[P]` tasks touch different files with no incomplete dependency.
- `[Story]` label maps every user-story-phase task to its story for traceability.
- Given this feature's small file footprint, most US2/US3 tasks build directly on US1's files rather than being fully parallel with each other — sequential priority order is the realistic path here, not a limitation of the task breakdown.
- Commit after each task or logical group.
- Stop at any checkpoint to validate a story independently before continuing.
