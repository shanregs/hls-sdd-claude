---

description: "Task list for Place Bulk Import"
---

# Tasks: Place Bulk Import

**Input**: Design documents from `/specs/010-place-bulk-import/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/place-bulk-import-api.yaml, quickstart.md

**Tests**: Included — this codebase's established practice writes unit + integration tests alongside every service.

**Organization**: Tasks are grouped by user story (spec.md priorities: US1 = P1, US2 = P1, US3 = P2). This is a small, additive extension of the already-implemented `school` module (specs/008-school-places) — no Setup phase is needed (no new package, no new module test, no new migration).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2, US3)

## Path Conventions

`backend/src/main/java/com/hls/school/`, `backend/src/test/java/com/hls/school/`, `frontend/src/pages/ZonesPage/`.

---

## Phase 1: Foundational (Blocking Prerequisites)

**Purpose**: The request/response DTOs and interface method every user story builds on.

**⚠️ CRITICAL**: No user story task can begin until this phase is complete.

- [X] T001 Create `backend/src/main/java/com/hls/school/api/dto/BulkPlaceRow.java`: `record BulkPlaceRow(UUID zoneId, String name, String pincode)` (FR-001, data-model.md)
- [X] T002 [P] Create `backend/src/main/java/com/hls/school/api/dto/BulkImportRowResult.java`: `record BulkImportRowResult(int index, boolean succeeded, PlaceView place, String reason)` — `place` set only when `succeeded`, `reason` set only when not (FR-003, data-model.md)
- [X] T003 [P] Create `backend/src/main/java/com/hls/school/api/dto/BulkImportResponse.java`: `record BulkImportResponse(List<BulkImportRowResult> results, int successCount, int failureCount)` (data-model.md)
- [X] T004 [P] Create `backend/src/main/java/com/hls/school/api/BulkImportBatchException.java`: thrown for batch-level rejection (empty or over the max row count) — carries a message naming the reason (FR-005/FR-006)
- [X] T005 Extend `backend/src/main/java/com/hls/school/api/PlaceCommands.java`: add `BulkImportResponse bulkImportPlaces(List<BulkPlaceRow> rows, UUID actingUserId)`

**Checkpoint**: DTOs and the interface contract exist. User story implementation can now begin.

---

## Phase 2: User Story 1 - Director/Admin Bulk-Imports Many Places in One Request (Priority: P1) 🎯 MVP

**Goal**: FR-001 — submit a batch of places (each with its own target Zone) in one request; every valid row is created and immediately retrievable.

**Independent Test**: Submit a batch of several places across one or more Zones and confirm each is retrievable under the Zone its own row specified.

### Implementation for User Story 1

- [X] T006 [US1] Implement `PlaceService.bulkImportPlaces(...)` in `backend/src/main/java/com/hls/school/internal/PlaceService.java`: inject `ZoneRepository` (new constructor dependency, research.md §3); for each row, validate in memory (never throwing per-row) and `save(...)` only rows that pass; wrap the whole method in `@Transactional` (research.md §2)
- [X] T007 [US1] Extend `backend/src/main/java/com/hls/school/internal/ZoneController.java`: add `POST /api/v1/school/places/bulk-import` (`@RequestBody List<BulkPlaceRow>`), Director/Admin-only via the existing `requireDirectorOrAdmin` helper, returning `BulkImportResponse`
- [X] T008 [P] [US1] Add `PlaceServiceTest` cases: `bulkImportPlaces_allValidRows_createsAllAndReturnsSuccessResults`, `bulkImportPlaces_rowsAcrossMultipleZones_eachCreatedUnderItsOwnZone` (AC2)
- [X] T009 [P] [US1] Add `SchoolIntegrationTest` case: `bulkImportPlaces_multipleValidRows_allImmediatelyRetrievableAfterward` (SC-001, quickstart.md Scenario 1 — real HTTP, a batch of several rows, then `GET .../zones/{zoneId}/places` confirms all present)

**Checkpoint**: A batch of valid places can be submitted and lands correctly — User Story 1 is independently functional (MVP).

---

## Phase 3: User Story 2 - Bad Rows Don't Block Good Rows, and Failures Are Clearly Reported (Priority: P1)

**Goal**: FR-002/003/004 — a batch mixing valid and invalid rows creates every valid row and reports each invalid row's position and reason; nothing is silently dropped or misreported.

**Independent Test**: Submit a batch mixing valid rows with rows missing a field or naming an unknown Zone; confirm valid rows are created and each invalid row is reported individually with its position and reason.

### Implementation for User Story 2

- [X] T010 [US2] Extend `PlaceService.bulkImportPlaces(...)`'s per-row validation (from T006): missing/blank `name` → `"name is required"`; missing/blank `pincode` → `"pincode is required"`; `zoneId` not found via `ZoneRepository.findById(...)` → `"zone <zoneId> does not exist"` (FR-004, data-model.md's validation table) — each produces a failed `BulkImportRowResult` at that row's index, never an exception
- [X] T011 [P] [US2] Add `PlaceServiceTest` cases: `bulkImportPlaces_unknownZoneId_reportedAsFailure_otherValidRowsStillCreated`, `bulkImportPlaces_missingName_reportedWithReason`, `bulkImportPlaces_missingPincode_reportedWithReason`, `bulkImportPlaces_allRowsValid_reportsZeroFailures` (AC3)
- [X] T012 [P] [US2] Add `SchoolIntegrationTest` cases: `bulkImportPlaces_mixedValidAndInvalidRows_partialSuccess_withRowIndexedReasons` (SC-002, quickstart.md Scenario 2), `bulkImportPlaces_asNonDirectorOrAdmin_isDenied` (SC-004, FR-007)

**Checkpoint**: Partial success with clear per-row reporting works end-to-end — User Stories 1-2 are both independently functional.

---

## Phase 4: User Story 3 - A Batch That's Too Large Is Rejected Up Front, Clearly (Priority: P2)

**Goal**: FR-005/FR-006 — an empty or oversized batch is rejected before anything is created, with a clear reason.

**Independent Test**: Submit an empty batch and a batch of 5,001 rows; confirm both are rejected immediately with nothing created.

### Implementation for User Story 3

- [X] T013 [US3] Extend `PlaceService.bulkImportPlaces(...)`: check `rows.isEmpty()` and `rows.size() > 5000` before touching any row, throwing `BulkImportBatchException` with a message naming which limit was hit (FR-005/FR-006, research.md §4); add `@ExceptionHandler(BulkImportBatchException.class)` to `ZoneController` mapping to 400 with the message
- [X] T014 [P] [US3] Add `PlaceServiceTest` cases: `bulkImportPlaces_emptyBatch_throwsBeforeCreatingAnything`, `bulkImportPlaces_exceedsMaxRowCount_throwsBeforeCreatingAnything` (verify zero `save(...)` calls in both)
- [X] T015 [P] [US3] Add `SchoolIntegrationTest` cases: `bulkImportPlaces_emptyBatch_returns400_nothingCreated` (SC-003, quickstart.md Scenario 3), `bulkImportPlaces_oversizedBatch_returns400_nothingCreated`

**Checkpoint**: All three user stories are independently functional.

---

## Phase 5: Polish & Cross-Cutting Concerns

- [X] T016 [P] Extend `frontend/src/pages/ZonesPage/zoneClient.ts`: add `BulkPlaceRow`/`BulkImportRowResult`/`BulkImportResponse` types and a `bulkImportPlaces(accessToken, rows: BulkPlaceRow[])` call
- [X] T017 Extend `frontend/src/pages/ZonesPage/ZonesPage.tsx`: add a "Bulk Import Places" form (`data-testid="bulk-import-form"`) — a textarea for pasting a JSON array of rows (simplest possible input given no CSV parsing in scope, research.md §1), submitting to `bulkImportPlaces` and rendering the per-row results (success count/failure count, each failed row's index and reason)
- [X] T018 [P] Extend `frontend/src/pages/ZonesPage/ZonesPage.test.tsx`: cases for an all-valid batch (shows success count) and a mixed batch (shows per-row failure reasons)
- [X] T019 [P] Run quickstart.md's three scenarios manually (or confirm via the automated equivalents in T009/T012/T015)
- [X] T020 Update `docs/HLS SDD Implementation Plan & Deliverables Tracker.md`'s School Master Data row (4a) to note the bulk-import capability (specs/010), and add a "Resolved" Section 7 entry
- [X] T021 Run the full backend (`mvn test`) and frontend (`npx vitest run`, `npx eslint .`, `npx tsc -b`) suites to confirm no regressions

---

## Dependencies & Execution Order

### Phase Dependencies

- **Foundational (Phase 1)**: No dependencies — BLOCKS all user stories
- **User Story 1 (Phase 2)**: Depends on Phase 1 only
- **User Story 2 (Phase 3)**: Depends on Phase 1; extends the same `bulkImportPlaces` method/endpoint US1 creates, so in practice follows US1 even though there's no *data* dependency between the stories
- **User Story 3 (Phase 4)**: Depends on Phase 1 only — could be built in parallel with US1/US2 by a different contributor, since batch-level rejection is checked before any row-level logic runs
- **Polish (Phase 5)**: Depends on all three user stories being complete

### Parallel Opportunities

- T002/T003/T004 (Foundational DTOs) — different files
- T008/T009 (US1 tests), T011/T012 (US2 tests), T014/T015 (US3 tests) — each pair touches different test files
- Phase 4 (US3) can be built in parallel with Phases 2-3 (US1/US2) since the batch-level checks are independent of per-row logic

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Foundational
2. Complete Phase 2: User Story 1 (submit a valid batch, get everything created)
3. **STOP and VALIDATE**: a batch of valid places lands correctly and is retrievable (SC-001)

### Incremental Delivery

1. Foundational → DTOs and interface method ready
2. User Story 1 → the basic bulk-submit capability (MVP)
3. User Story 2 → partial success with clear per-row reporting — this is what makes the feature actually safe to use on real, imperfect data
4. User Story 3 → guardrail against a clearly-wrong submission
5. Polish → frontend form, quickstart validation, tracker update

## Notes

- No new migration, no new table — bulk-imported rows use the existing `school_place` table exactly as specs/008 defined it.
- `bulkImportPlaces` lives on the existing `PlaceCommands` interface, not a new interface (research.md §5).
- The existing single-add `addPlace` endpoint (specs/008) is untouched — its lack of a Zone-existence check is a known, deliberate asymmetry (research.md §3, spec.md Assumptions), not a bug this feature fixes.
- Commit after each task or logical group, consistent with this repo's established practice.
