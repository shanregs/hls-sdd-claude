# Data Model: School Master Data — Places

Derived from spec.md's Key Entities section and Functional Requirements FR-001 through FR-007. Extends `specs/007-school-zone/data-model.md` — `Zone` and `SchoolZoneAssignment` are unchanged and not repeated here.

## Place

A named locality (city, town, or village) with a PIN code, belonging to one Zone.

| Field | Type | Notes |
|---|---|---|
| `id` | UUID (PK) | |
| `zoneId` | UUID, not null | The Zone this place belongs to — no FK constraint (consistent with `SchoolZoneAssignment`'s own opaque-reference style, though `Zone` itself is a real table this module owns, so a FK is possible; kept consistent with the rest of this module's style regardless) |
| `name` | String, not null | City/town/village name. Not unique (spec.md Edge Cases) |
| `pincode` | String, not null | Indian PIN code. Not unique (spec.md Edge Cases — a PIN code can span multiple recorded places) |
| `createdAt` | timestamp (UTC), not null | |
| `createdBy` | UUID, not null | The Director/Admin `userId` who added it |

**Invariants**: Never deleted once added (FR-007) — enforced by `PlaceRepository` exposing no delete method (research.md §3, mirroring `ZoneRepository`).

## Derived / query-only shapes (not persisted)

- **PlaceView** — `id`, `zoneId`, `name`, `pincode`. The read-side shape of one `Place` row, returned by every `PlaceQueries` method and the REST endpoints.

## State transitions

None — `Place` has exactly one transition: non-existent → added (FR-001). There is no update or delete path (research.md §2).
