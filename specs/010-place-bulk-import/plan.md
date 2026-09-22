# Implementation Plan: Place Bulk Import

**Branch**: `010-place-bulk-import` | **Date**: 2026-09-22 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/010-place-bulk-import/spec.md`

## Summary

Adds a batch-shaped way to add many `Place`s in one request, extending the already-implemented `school` module (specs/008-school-places) rather than reopening it. A single new endpoint, `POST /api/v1/school/places/bulk-import`, accepts a JSON array of rows (each: zoneId, name, pincode) and returns a per-row success/failure report — a bad row (missing field, or a Zone id that doesn't exist) never blocks the other valid rows in the same batch. No new table, no new module, no new dependency.

## Technical Context

**Language/Version**: Java 25 (unchanged)

**Primary Dependencies**: All already present in `backend/pom.xml`. **No new dependency additions** (research.md §1 — JSON array in, not a CSV/multipart upload).

**Storage**: PostgreSQL — no schema change. Bulk-imported rows land in the existing `school_place` table (specs/008), same shape.

**Testing**: JUnit 5 + Spring Boot Test (`PlaceServiceTest`, extended, unit) for FR-001-008; extended `SchoolIntegrationTest` (Testcontainers) for the real HTTP round trip, partial-success reporting, oversized/empty-batch rejection, and Director/Admin-only enforcement. No new `SchoolModuleTest`/`ArchitectureTest` changes needed (research.md §6).

**Target Platform**: Single EC2/VM in `ap-south-1`, Docker Compose — unchanged

**Project Type**: Web application (Option 2) — extends the existing `ZonesPage` with a bulk-import form (paste/upload rows as JSON, or a small in-page table) rather than a new frontend page.

**Performance Goals**: A 5,000-row batch (the maximum, research.md §4) processed in a single request/transaction — no batch-splitting or async processing needed at this scale.

**Constraints**: FR-002/FR-003 (partial success, per-row reporting) satisfied by validating each row in memory before any `save(...)` call, never throwing for a row-level failure (research.md §2) — this is what makes partial success possible without fighting Spring's default whole-method transaction rollback. FR-004's Zone-existence check is new for this endpoint only, not retrofitted onto the existing single-add endpoint (research.md §3).

**Scale/Scope**: One new REST endpoint, one new method on the existing `PlaceCommands` interface, two new response DTOs (`BulkImportRowResult`, `BulkImportResponse`), one frontend form (no new page).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Applies? | Assessment |
|---|---|---|
| I. Operational Truth Must Be Auditable | Partially | Places are added, never edited/deleted (specs/008 FR-007, unchanged) — bulk import doesn't introduce a "change" needing before/after history; each successfully-added row is itself the only event, same as single-add. |
| II. Role-Scoped Access and Manager Ownership | Yes | Director/Admin-only (FR-007), matching the existing single-place-add endpoint exactly — no new role logic. |
| III–IV, VI, IX | No | Not applicable. |
| V. Architecture Is a Modular Monolith With Enforced Boundaries | Yes | Pure extension of the existing `school` module — no new package, no new dependency direction, no new ArchUnit rule (research.md §6). |
| VII. Reliability, Testability, and Incremental Delivery | Yes | Unit and integration tests for every FR, including the partial-success and oversized-batch edge cases, written before/alongside implementation. |
| VIII. Security, Identity, and Observability | Yes | Reuses the exact same JWT bearer auth every other `school` endpoint already uses. |
| Additional Constraints | Yes | Java 25 / Spring Boot 4.1.1 / PostgreSQL / single EC2 / Docker Compose unchanged. |

**Gate result**: PASS, no open exceptions.

**Post-design re-check (after Phase 1)**: No new principle concerns. Gate result unchanged: PASS.

## Project Structure

### Documentation (this feature)

```text
specs/010-place-bulk-import/
├── plan.md               # This file (/speckit-plan command output)
├── research.md           # Phase 0 output (/speckit-plan command)
├── data-model.md         # Phase 1 output (/speckit-plan command)
├── quickstart.md         # Phase 1 output (/speckit-plan command)
├── contracts/            # Phase 1 output (/speckit-plan command)
│   └── place-bulk-import-api.yaml
├── checklists/
│   └── requirements.md
└── tasks.md              # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
backend/
├── pom.xml                                       # NO CHANGES
├── src/
│   ├── main/
│   │   ├── java/com/hls/school/
│   │   │   ├── api/
│   │   │   │   ├── PlaceCommands.java             # EXTENDED: + bulkImportPlaces(...)
│   │   │   │   ├── PlaceQueries.java              # existing, unchanged
│   │   │   │   └── dto/
│   │   │   │       ├── PlaceView.java                 # existing, unchanged
│   │   │   │       ├── BulkPlaceRow.java                # NEW: zoneId, name, pincode
│   │   │   │       ├── BulkImportRowResult.java           # NEW: index, succeeded, place?, reason?
│   │   │   │       └── BulkImportResponse.java              # NEW: results, successCount, failureCount
│   │   │   └── internal/
│   │   │       ├── PlaceService.java                          # EXTENDED: + bulkImportPlaces(...)
│   │   │       │                                               # (needs ZoneRepository for the
│   │   │       │                                               # existence check, research.md §3)
│   │   │       └── ZoneController.java                          # EXTENDED: + bulk-import endpoint
│   └── test/java/com/hls/school/
│       ├── PlaceServiceTest.java                     # EXTENDED — new cases
│       └── SchoolIntegrationTest.java                 # EXTENDED — new cases

frontend/src/pages/ZonesPage/
├── ZonesPage.tsx          # EXTENDED — bulk-import form
├── ZonesPage.test.tsx      # EXTENDED — new cases
└── zoneClient.ts            # EXTENDED — bulkImportPlaces call
```

**Structure Decision**: Pure extension of the existing `school` module and `ZonesPage` — no new bounded-context package, no new frontend page, no `ArchitectureTest`/`SchoolModuleTest` changes needed. `PlaceService` gains a constructor dependency on `ZoneRepository` (already a bean in the same module) purely for the new Zone-existence check.

## Complexity Tracking

*(none — no Constitution Check violations to justify)*
