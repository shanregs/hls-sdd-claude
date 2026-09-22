---

description: "Task list for School Master Data — Places"
---

# Tasks: School Master Data — Places

**Input**: Design documents from `/specs/008-school-places/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/school-places-api.yaml, quickstart.md

**Tests**: Included — this codebase's established practice (specs/004, 006, 007) writes unit + integration tests alongside every service.

**Organization**: Tasks are grouped by user story. This is a small, additive extension of the already-implemented `school` module (specs/007-school-zone) — no Setup phase is needed (no new package, no new dependency, no new module test).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2, US3)

## Path Conventions

Web app: `backend/src/main/java/com/hls/school/`, `backend/src/test/java/com/hls/school/`, `frontend/src/pages/ZonesPage/`

---

## Phase 1: Foundational (Blocking Prerequisites)

**Purpose**: The `Place` table, entity, repository, and read-side DTO that every user story's queries/commands are built on.

**⚠️ CRITICAL**: No user story task can begin until this phase is complete.

- [X] T001 Create migration `backend/src/main/resources/db/migration/V5__create_school_place_table.sql`: table `school_place` with columns `id UUID PRIMARY KEY`, `zone_id UUID NOT NULL`, `name VARCHAR(255) NOT NULL`, `pincode VARCHAR(20) NOT NULL`, `created_at TIMESTAMPTZ NOT NULL`, `created_by UUID NOT NULL` (data-model.md — no delete column, no uniqueness constraint on name or pincode, per FR-002/FR-007); add index `ix_school_place_pincode ON school_place (pincode)` and index `ix_school_place_name ON school_place (lower(name))` (case-insensitive lookup, Edge Cases) and index `ix_school_place_zone_id ON school_place (zone_id)`
- [X] T002 [P] Create entity `backend/src/main/java/com/hls/school/internal/Place.java`: fields `id, zoneId, name, pincode, createdAt, createdBy` (data-model.md), no setters, mirroring `Zone.java`'s style exactly
- [X] T003 [P] Create `backend/src/main/java/com/hls/school/api/dto/PlaceView.java`: `record PlaceView(UUID id, UUID zoneId, String name, String pincode)` per contracts/school-places-api.yaml's `PlaceView` schema
- [X] T004 Create `backend/src/main/java/com/hls/school/internal/PlaceRepository.java`: `extends Repository<Place, UUID>` (bare, not JpaRepository — FR-007 never-deleted, mirroring `ZoneRepository.java`), exposing `Place save(Place place)`, `List<Place> findByPincode(String pincode)`, `List<Place> findByNameIgnoreCase(String name)` (case-insensitive, Edge Cases), `List<Place> findByZoneId(UUID zoneId)` — no delete method

**Checkpoint**: `Place` persists and is queryable at the repository layer. User story implementation can now begin.

---

## Phase 2: User Story 1 - Director/Admin Adds a Place to a Zone (Priority: P1) 🎯 MVP

**Goal**: FR-001 — a Director/Admin can record a place (name + PIN code) under an existing Zone, and it's immediately retrievable.

**Independent Test**: Add a place to a Zone via `POST /api/v1/school/places`; confirm the response shows that Zone, and a direct repository/lookup query returns it.

### Implementation for User Story 1

- [X] T005 [US1] Create `backend/src/main/java/com/hls/school/api/PlaceCommands.java`: interface with `PlaceView addPlace(UUID zoneId, String name, String pincode, UUID actingUserId)` per FR-001, Director/Admin-only (enforced by controller, not this interface — mirrors `ZoneCommands.java`)
- [X] T006 [US1] Create `backend/src/main/java/com/hls/school/internal/PlaceService.java` implementing `PlaceCommands` (and, per T009, `PlaceQueries`): `addPlace` generates a new `UUID`, persists via `PlaceRepository.save`, returns `PlaceView` — no uniqueness check against existing name/pincode (FR-002, research.md §1)
- [X] T007 [US1] Extend `backend/src/main/java/com/hls/school/internal/ZoneController.java`: inject `PlaceService`; add `POST /api/v1/school/places` reading `PlaceRequest(UUID zoneId, String name, String pincode)`, calling `requireDirectorOrAdmin(jwt)` then `placeService.addPlace(...)`, returning `PlaceView` (contracts/school-places-api.yaml `addPlace`)
- [X] T008 [P] [US1] Add `PlaceServiceTest` cases in `backend/src/test/java/com/hls/school/PlaceServiceTest.java` (new file): `addPlace_persistsAndReturnsPlaceViewWithGivenZone`, `addPlace_allowsSameNameUnderDifferentZone_noRejection`, `addPlace_allowsSamePincodeUnderDifferentZones_bothRecordedIndependently` (Acceptance Scenario 2) — repository mocked, mirroring `ZoneServiceTest.java`'s structure
- [X] T009 [P] [US1] Add `SchoolIntegrationTest` case (extend existing `backend/src/test/java/com/hls/school/SchoolIntegrationTest.java`): `addPlace_asDirectorOrAdmin_succeeds_asOtherRole_isDenied` — real Testcontainers Postgres + JWT auth, asserting 200 for Director/Admin and 403 otherwise

**Checkpoint**: A place can be added to a Zone and is durably stored — User Story 1 is independently functional.

---

## Phase 3: User Story 2 - Anyone Can Look Up Which Zone a Place Belongs To (Priority: P1)

**Goal**: FR-003/FR-004/FR-005 — given a PIN code or a name, return every matching place's Zone; an empty, non-error result when nothing matches.

**Independent Test**: Add a place, then `GET /api/v1/school/places?pincode=...` and separately `?name=...`; confirm both return the place's Zone. Query an unrecorded PIN code/name and confirm an empty array, not an error.

### Implementation for User Story 2

- [X] T010 [US2] Create `backend/src/main/java/com/hls/school/api/PlaceQueries.java`: interface with `List<PlaceView> findByPincode(String pincode)` (FR-003) and `List<PlaceView> findByName(String name)` (FR-004, case-insensitive) — both return an empty list, never null or an error, for no matches (FR-005)
- [X] T011 [US2] Implement `findByPincode`/`findByName` in `backend/src/main/java/com/hls/school/internal/PlaceService.java` (extends T006's class), trimming whitespace on the input before querying (Edge Cases), mapping `Place` rows to `PlaceView`
- [X] T012 [US2] Extend `backend/src/main/java/com/hls/school/internal/ZoneController.java`: add `GET /api/v1/school/places?pincode=&name=` — exactly one of the two query params is expected; call the matching `PlaceQueries` method; return `List<PlaceView>` (contracts/school-places-api.yaml `findPlaces`); Director/Admin-only via `requireDirectorOrAdmin(jwt)`, consistent with every other endpoint in this controller
- [X] T013 [P] [US2] Add `PlaceServiceTest` cases: `findByPincode_returnsEveryMatchingPlaceAcrossZones` (Acceptance Scenario 3 — multiple places, different Zones, same PIN code), `findByPincode_forUnknownPincode_returnsEmptyList`, `findByName_isCaseInsensitive`, `findByName_forUnknownName_returnsEmptyList`
- [X] T014 [P] [US2] Add `SchoolIntegrationTest` case: `findPlaces_byPincodeSpanningTwoZones_returnsBothPlaces_andUnknownPincodeReturnsEmptyArray` — real HTTP round trip confirming the JSON response is `[]` (not a 404/error) for no matches

**Checkpoint**: The core lookup capability works end-to-end — User Stories 1 and 2 are both independently functional.

---

## Phase 4: User Story 3 - Director/Admin Views Every Place in a Zone (Priority: P2)

**Goal**: FR-006 — retrieve every place currently recorded under a given Zone in one call.

**Independent Test**: Add several places to a Zone, then `GET /api/v1/school/zones/{zoneId}/places` and confirm exactly that set is returned; query a Zone with no places and confirm an empty list.

### Implementation for User Story 3

- [X] T015 [US3] Add `List<PlaceView> findByZoneId(UUID zoneId)` to `backend/src/main/java/com/hls/school/api/PlaceQueries.java` (FR-006)
- [X] T016 [US3] Implement `findByZoneId` in `backend/src/main/java/com/hls/school/internal/PlaceService.java`
- [X] T017 [US3] Extend `backend/src/main/java/com/hls/school/internal/ZoneController.java`: add `GET /api/v1/school/zones/{zoneId}/places`, Director/Admin-only, returning `List<PlaceView>` (contracts/school-places-api.yaml `getZonePlaces`)
- [X] T018 [P] [US3] Add `PlaceServiceTest` cases: `findByZoneId_returnsExactlyThatZonesPlaces`, `findByZoneId_forZoneWithNoPlaces_returnsEmptyList`
- [X] T019 [P] [US3] Add `SchoolIntegrationTest` case: `getZonePlaces_returnsExactlyThreeAddedPlaces_andEmptyZoneReturnsEmptyList`

**Checkpoint**: All three user stories are independently functional.

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Frontend surface for the three stories, plus doc/tracker updates.

- [X] T020 [P] Extend `frontend/src/pages/ZonesPage/zoneClient.ts`: add `PlaceView` interface (`id, zoneId, name, pincode`), `addPlace(accessToken, zoneId, name, pincode)`, `findPlacesByPincode(accessToken, pincode)`, `findPlacesByName(accessToken, name)`, `getZonePlaces(accessToken, zoneId)` — following the existing `request<T>` helper pattern
- [X] T021 Extend `frontend/src/pages/ZonesPage/ZonesPage.tsx`: add an "Add Place" form (zoneId/name/pincode inputs, `data-testid="add-place-form"`), a "Find Places" form (pincode-or-name input plus a toggle/radio for which, `data-testid="find-places-form"`, rendering each result's zoneId), and a "Zone's Places" form (`data-testid="zone-places-form"`) mirroring the existing Zone/School forms' structure
- [X] T022 [P] Extend `frontend/src/pages/ZonesPage/ZonesPage.test.tsx` with cases covering: adding a place, finding places by pincode, finding places by name, an empty-result find, and listing a Zone's places
- [X] T023 Run quickstart.md's four scenarios manually (or confirm via the automated tests in T009/T014/T019/T022) and update `docs/HLS SDD Implementation Plan & Deliverables Tracker.md`'s School Master Data row to note the Places slice (specs/008) is implemented, alongside the Zone slice (specs/007)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Foundational (Phase 1)**: No dependencies — BLOCKS all user stories
- **User Story 1 (Phase 2)**: Depends on Phase 1 only
- **User Story 2 (Phase 3)**: Depends on Phase 1 only (not on US1's controller changes — `PlaceQueries` is a separate interface from `PlaceCommands`, though both land in the same `PlaceService` class, so T006 and T011 touch the same file sequentially)
- **User Story 3 (Phase 4)**: Depends on Phase 1 only (same file-sharing note as US2)
- **Polish (Phase 5)**: Depends on US1-3 being complete (frontend calls all their endpoints)

### Within Each User Story

- Interface (`PlaceCommands`/`PlaceQueries` method) before its `PlaceService` implementation
- `PlaceService` implementation before the `ZoneController` endpoint that calls it
- Controller endpoint before its integration test
- Unit tests ([P]) can be written alongside implementation, run after

### Parallel Opportunities

- T002 and T003 (different files, both depend only on T001's table existing conceptually, not literally — can run in parallel)
- T008/T009 (US1 tests), T013/T014 (US2 tests), T018/T019 (US3 tests) — each pair touches different test files or independent new test methods
- T020 (frontend client) can start as soon as the corresponding backend endpoint (T007/T012/T017) is done, in parallel with other stories' backend work

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Foundational
2. Complete Phase 2: User Story 1 (add a place)
3. **STOP and VALIDATE**: a place can be added and is durably stored
4. Note: User Story 2 (lookup) is what actually delivers the feature's value (spec.md: "recording places has no value until the lookup works") — do not stop at US1 alone in practice; it is listed as the nominal MVP slice only because it has no dependencies.

### Incremental Delivery

1. Foundational → Place table/entity/repository ready
2. User Story 1 → places can be added
3. User Story 2 → the "which Zone is this place in" lookup this feature exists for
4. User Story 3 → Director/Admin visibility into a Zone's full place list
5. Polish → frontend surface, quickstart validation, tracker update

## Notes

- No Setup phase, no new `ArchitectureTest`/`SchoolModuleTest` changes (research.md §3) — this is a pure extension of the already-covered `com.hls.school` package.
- `PlaceCommands`/`PlaceQueries`/`PlaceService`/`ZoneController` are all extended, not newly created controllers/services, per plan.md's Project Structure.
- Commit after each task or logical group, consistent with this repo's established practice for specs/004/007.
