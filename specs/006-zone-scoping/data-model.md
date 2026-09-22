# Data Model: Zone-Based Manager Scoping (reworked)

Derived from spec.md's Key Entities section and Functional Requirements FR-001 through FR-008. Extends the `organization` module's existing data model (specs/003-organization-scoping/data-model.md) — `SchoolAssignment` and `TeacherAssignment` are unchanged and not repeated here. Zone and School-Zone Assignment are **not** defined here — they are `school`'s data (`specs/007-school-zone/data-model.md`), only referenced through `school.api.ZoneQueries`.

## ZoneManagerAssignment (new, owned by `organization`)

One period during which a Manager was (or is) assigned to cover a Zone. Unlike `SchoolAssignment`/`TeacherAssignment`, **more than one can be simultaneously current for the same Zone** (different Managers) — research.md §2.

| Field | Type | Notes |
|---|---|---|
| `id` | UUID (PK) | |
| `zoneId` | UUID | Opaque reference into `school`'s `Zone` table — no FK constraint, consistent with this module's and `school`'s established opaque-reference style; existence is validated at write time via `school.api.ZoneQueries.findById(...)` (FR-002), not a database constraint |
| `managerId` | UUID | |
| `effectiveFrom` | timestamp (UTC) | |
| `effectiveTo` | timestamp (UTC), nullable | `NULL` means this Manager currently covers this Zone |
| `assignedBy` | UUID | |
| `assignedAt` | timestamp (UTC) | |

**Invariants**:
- At most one **currently-open row per `(zoneId, managerId)` pair** — enforced by a partial unique index (research.md §2), not the `(zoneId)`-alone pattern `SchoolAssignment` uses. Different Managers may each have their own currently-open row for the same Zone.
- Rows are never updated except to set `effectiveTo` exactly once, never deleted.

## Modified: `SchoolAssignment`'s write path (no schema change)

No schema change to `organization_school_assignment` itself. `AccountabilityService.assignSchoolManager(...)` (specs/003) gains a precondition, checked before any write and before the existing no-op/conflict logic (research.md §3):

```
call school.api.ZoneQueries.currentZoneForSchool(schoolId)
  → UNASSIGNED: reject with SchoolManagerNotInZoneException (FR-004's "no Zone" case)
  → CURRENT_ZONE(zoneId): continue
check organization's own ZoneManagerAssignmentRepository for a currently-open
row for (zoneId, the chosen managerId)
  → none found: reject with SchoolManagerNotInZoneException (FR-003/FR-004's "not covering" case)
  → found: proceed with the existing assign/reassign/no-op/conflict logic, unchanged
```

## Unmodified: `TeacherAssignment`

No schema or behavior change (FR-006/FR-007). Documented here only to make the boundary explicit: this feature does not touch it.

## Referenced, not owned: `Zone` and `SchoolZoneAssignment` (`school` module)

This feature reads, but never writes or duplicates:
- `school.api.ZoneQueries.findById(zoneId)` — to validate a Zone id exists before assigning a Manager to cover it (FR-002).
- `school.api.ZoneQueries.currentZoneForSchool(schoolId)` — a School's current Zone, or `UNASSIGNED` (FR-003/FR-004).
- `school.api.ZoneQueries.currentSchoolsForZone(zoneId)` — every School currently in a Zone, for the coverage lookup (FR-005).

See `specs/007-school-zone/data-model.md` for `Zone`'s and `SchoolZoneAssignment`'s own field definitions and invariants — not repeated here, since this feature does not own them.

## Derived / query-only shapes (not persisted)

- **ZoneCoverage** — the read-side shape of a Zone-coverage lookup (FR-005): `zoneId`, the list of currently-covering `managerId`s (from this feature's own `ZoneManagerAssignment` table), and the list of currently-assigned `schoolId`s (from `school.api.ZoneQueries.currentSchoolsForZone(...)`). Composed at read time from two sources, never persisted as its own row.

## State transitions

`ZoneManagerAssignment` follows the identical creation/end pattern `SchoolAssignment`/`TeacherAssignment` already established (specs/003 data-model.md) — no new transition shape, just a new dimension (multiple concurrent Managers per Zone):

```
   assignManagerToZone(zoneId, managerId)  ──┐ (repeatable for
              │                              │  different managerIds —
              ▼                              │  each gets its own row)
   [ +1 currently-open                       │
     ZoneManagerAssignment ] ◄───────────────┘
              │
   removeManagerFromZone(zoneId, managerId)
              │
              ▼
   [ that (zone, manager) row's
     effectiveTo is set; other
     managers' rows unaffected ]
```
