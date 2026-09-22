# Data Model: School Master Data — Zones

Derived from spec.md's Key Entities section and Functional Requirements FR-001 through FR-009.

## Zone

A named geographic area.

| Field | Type | Notes |
|---|---|---|
| `id` | UUID (PK) | |
| `name` | String, not null | No uniqueness enforced (spec.md Edge Cases) |
| `createdAt` | timestamp (UTC), not null | |
| `createdBy` | UUID, not null | The Director/Admin `userId` who created it |

**Invariants**: Never deleted once created (FR-009) — enforced by `ZoneRepository` exposing no delete method (research.md §2).

## SchoolZoneAssignment

One period during which a School belonged to a particular Zone. Identical shape and invariants to `organization.internal.SchoolAssignment` (specs/003), with `zoneId` in place of `managerId`.

| Field | Type | Notes |
|---|---|---|
| `id` | UUID (PK) | |
| `schoolId` | UUID | Opaque reference — no School master table exists yet (spec.md Assumptions) |
| `zoneId` | UUID | |
| `effectiveFrom` | timestamp (UTC), not null | |
| `effectiveTo` | timestamp (UTC), nullable | `NULL` means this is the School's *current* Zone |
| `assignedBy` | UUID, not null | |
| `assignedAt` | timestamp (UTC), not null | |

**Invariants**:
- At most one currently-open row per `schoolId` — enforced by a partial unique index (research.md §1), same technique as `SchoolAssignment`'s own constraint.
- Rows are never updated except to set `effectiveTo` exactly once (ending the assignment), and never deleted (FR-004, Constitution Principle I).
- A reassignment names the current row it intends to end; if that row was already ended by someone else first, the reassignment is rejected as a conflict (FR-005) rather than silently proceeding — same conditional-`UPDATE` technique as `SchoolAssignment` (research.md §1).

## Derived / query-only shapes (not persisted)

- **ZoneView** — `id`, `name`. The read-side shape of one `Zone` row (FR-002).
- **SchoolZoneAnswer** — one of two states for a given School identifier: `CURRENT_ZONE(zoneId)` or `UNASSIGNED` (FR-006) — same "answer" pattern `organization.api.dto.AccountabilityAnswer` already established for "current manager or unassigned."

## State transitions

Identical shape to `SchoolAssignment`'s own state diagram (specs/003 data-model.md), substituting Zone for Manager:

```
        assignSchoolToZone(schoolId, zoneId)
                    │
         [ no current row exists ]
                    │
          INSERT effectiveTo = NULL
                    │
                    ▼
         (CURRENT: this row is now
          the answer to "what Zone
          is schoolId currently in")
                    │
    reassignSchoolToZone(newZoneId, endsAssignmentId)
                    │
          UPDATE effectiveTo = now
                (conditional — WHERE id = ? AND
                 effective_to IS NULL; 0 rows
                 affected -> conflict, FR-005)
          INSERT new row (effectiveTo = NULL)
                    │
                    ▼
           (new CURRENT row)
```
