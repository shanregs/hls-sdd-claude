# Implementation Plan: School Master Data — Places

**Branch**: `008-school-places` | **Date**: 2026-09-22 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/008-school-places/spec.md`

## Summary

A small, additive extension to the already-implemented `school` module (`specs/007-school-zone`): a `Place` entity (name + PIN code, belonging to one Zone) and the lookup that makes "which Zone is this place in" a real, answerable query. No new bounded context, no new module-boundary rule — this reuses `school`'s existing `api`/`internal` split, adding one new table, one new repository, one new service, and new endpoints on the existing `ZoneController`.

## Technical Context

**Language/Version**: Java 25 (unchanged)

**Primary Dependencies**: All already present in `backend/pom.xml`. **No new dependency additions.**

**Storage**: PostgreSQL — one new table, `school_place`, migration `V5__create_school_place_table.sql` (Identity=V1, Organization=V2, Audit=V3, School/Zone=V4).

**Testing**: JUnit 5 + Spring Boot Test (`PlaceServiceTest`, unit, mocked repository) for FR-001-007; Testcontainers-backed PostgreSQL integration test (`SchoolIntegrationTest`, extended — not a new file) for real JWT bearer auth and the multi-match lookup cases (US2 AC3). No `SchoolModuleTest`/`ArchitectureTest` changes needed — `Place` lives in the same `com.hls.school` package the existing rules already cover, and adding one more internal type doesn't change `school`'s dependency footprint (still nothing, per specs/007 research.md §3), so `STANDALONE` bootstrap is re-verified, not re-decided.

**Target Platform**: Single EC2/VM in `ap-south-1`, Docker Compose — unchanged

**Project Type**: Web application (Option 2) — extends the existing `ZonesPage` with a Places section rather than adding a new frontend page.

**Performance Goals**: No specific throughput target given this project's scale. Simple per-request reads/writes.

**Constraints**: FR-007 (never deleted) enforced by a narrow repository interface with no delete method, the now-repeatedly-proven pattern. FR-002/Edge Cases (no uniqueness on name or PIN code) means lookups return lists, not single results — `findByPincode`/`findByName` are designed as multi-result from the start, not retrofitted.

**Scale/Scope**: One new entity/table, ~4 new REST endpoints (add place, lookup by PIN code, lookup by name, list a Zone's places), one frontend extension (no new page).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Applies? | Assessment |
|---|---|---|
| I. Operational Truth Must Be Auditable | Partially | Places are added, never deleted or edited (FR-007) — there's no "change" for Principle I's traceability requirement to apply to; adding one is itself the only event, and it's simply retrievable, not something requiring before/after history. |
| II. Role-Scoped Access and Manager Ownership | Yes | Adding a place is Director/Admin-only (FR-001), matching every other master-data-maintenance endpoint. |
| III–IV, VI, IX | No | Not applicable. |
| V. Architecture Is a Modular Monolith With Enforced Boundaries | Yes | Extends the existing `school` module in place — no new package, no new dependency direction. `Place`/`PlaceRepository`/`PlaceService` are `school.internal`; `PlaceQueries`/`PlaceCommands`/`PlaceView` are `school.api`, following the exact pattern `Zone`/`ZoneRepository`/`ZoneService`/`ZoneQueries` already established. |
| VII. Reliability, Testability, and Incremental Delivery | Yes | Unit and integration tests for every FR, written before implementation. |
| VIII. Security, Identity, and Observability | Yes | Reuses the exact same JWT bearer auth every other `school` endpoint already uses. |
| Additional Constraints | Yes | Java 25 / Spring Boot 4.1.1 / PostgreSQL / single EC2 / Docker Compose unchanged. |

**Gate result**: PASS, no open exceptions.

**Post-design re-check (after Phase 1)**: No new principle concerns. Gate result unchanged: PASS.

## Project Structure

### Documentation (this feature)

```text
specs/008-school-places/
├── plan.md               # This file (/speckit-plan command output)
├── research.md           # Phase 0 output (/speckit-plan command)
├── data-model.md         # Phase 1 output (/speckit-plan command)
├── quickstart.md         # Phase 1 output (/speckit-plan command)
├── contracts/            # Phase 1 output (/speckit-plan command)
│   └── school-places-api.yaml
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
│   │   │   │   ├── ZoneQueries.java               # existing (specs/007), unchanged
│   │   │   │   ├── ZoneCommands.java               # existing (specs/007), unchanged
│   │   │   │   ├── PlaceQueries.java                # NEW: findByPincode/findByName/findByZoneId (FR-003/004/006)
│   │   │   │   ├── PlaceCommands.java                # NEW: addPlace (FR-001)
│   │   │   │   └── dto/
│   │   │   │       └── PlaceView.java                 # NEW: id, zoneId, name, pincode
│   │   │   └── internal/
│   │   │       ├── Zone.java, ZoneService.java, ...   # existing (specs/007), unchanged
│   │   │       ├── Place.java                          # NEW JPA entity
│   │   │       ├── PlaceRepository.java                 # NEW — extends bare Repository<>, no delete (FR-007)
│   │   │       ├── PlaceService.java                     # NEW — implements PlaceQueries + PlaceCommands
│   │   │       └── ZoneController.java                   # EXTENDED: + place endpoints, PlaceService injected
│   │   └── resources/db/migration/
│   │       └── V5__create_school_place_table.sql   # V5 — Identity=V1, Organization=V2, Audit=V3, School/Zone=V4
│   └── test/java/com/hls/school/
│       ├── PlaceServiceTest.java                     # NEW — unit: FR-001-007 rules
│       └── SchoolIntegrationTest.java                 # EXTENDED — new cases, same file (specs/007)

frontend/src/pages/ZonesPage/
├── ZonesPage.tsx          # EXTENDED — add-place form, PIN/name lookup, zone's-places view
├── ZonesPage.test.tsx      # EXTENDED — new cases
└── zoneClient.ts            # EXTENDED — place endpoint calls
```

**Structure Decision**: Pure extension of the existing `school` module — no new bounded-context package, no new frontend page, no `ArchitectureTest`/`SchoolModuleTest` changes needed. Dependency direction is unchanged: `school` still depends on nothing from any other module.

## Complexity Tracking

*(none — no Constitution Check violations to justify)*
