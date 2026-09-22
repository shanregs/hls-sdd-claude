# Data Model: Zone-Based Manager Scoping

Derived from spec.md's Key Entities section and Functional Requirements FR-001 through FR-008. Extends the `organization` module's existing data model (specs/003-organization-scoping/data-model.md) — `SchoolAssignment` and `TeacherAssignment` are unchanged and not repeated here.

## Zone

A named geographic area. The first entity in `organization` with real attributes of its own, rather than an opaque UUID reference to a not-yet-built module.

| Field | Type | Notes |
|---|---|---|
| `id` | UUID (PK) | |
| `name` | String, not null | No uniqueness enforced (spec.md doesn't require it) |
| `createdAt` | timestamp (UTC) | |
| `createdBy` | UUID | The Director/Admin `userId` who created it |

**Invariants**: Never deleted (Constitution Principle I, matching how every other organizational-structure record in this system is treated).

## ZoneManagerAssignment

One period during which a Manager was (or is) assigned to cover a Zone. Unlike `SchoolAssignment`/`TeacherAssignment`, **more than one can be simultaneously current for the same Zone** (different Managers) — research.md §1.

| Field | Type | Notes |
|---|---|---|
| `id` | UUID (PK) | |
| `zoneId` | UUID | |
| `managerId` | UUID | |
| `effectiveFrom` | timestamp (UTC) | |
| `effectiveTo` | timestamp (UTC), nullable | `NULL` means this Manager currently covers this Zone |
| `assignedBy` | UUID | |
| `assignedAt` | timestamp (UTC) | |

**Invariants**:
- At most one **currently-open row per `(zoneId, managerId)` pair** — enforced by a partial unique index (research.md §1), not the `(zoneId)`-alone pattern `SchoolAssignment` uses. Different Managers may each have their own currently-open row for the same Zone.
- Rows are never updated except to set `effectiveTo` exactly once, never deleted.

## SchoolZoneAssignment

One period during which a School belonged to a particular Zone. Same shape and invariants as `SchoolAssignment` (specs/003), with `zoneId` in place of `managerId` — a School has at most one current Zone.

| Field | Type | Notes |
|---|---|---|
| `id` | UUID (PK) | |
| `schoolId` | UUID | Opaque — School Master Data still doesn't exist (same narrowing specs/003 already documented) |
| `zoneId` | UUID | |
| `effectiveFrom` | timestamp (UTC) | |
| `effectiveTo` | timestamp (UTC), nullable | `NULL` means this is the School's current Zone |
| `assignedBy` | UUID | |
| `assignedAt` | timestamp (UTC) | |

**Invariants**: At most one currently-open row per `schoolId` — enforced by a partial unique index on `(schoolId)`, identical in shape to `SchoolAssignment`'s own constraint.

## Modified: SchoolAssignment's write path

No schema change to `organization_school_assignment` itself. `AccountabilityService.assignSchoolManager(...)` (specs/003) gains a precondition, checked before any row is written (research.md §2):

```
resolve School's current SchoolZoneAssignment.zoneId
  → none found: reject (FR-005)
check a currently-open ZoneManagerAssignment exists for (that zoneId, the chosen managerId)
  → none found: reject (FR-004/FR-005)
  → found: proceed with the existing assign/reassign logic, unchanged
```

## Unmodified: TeacherAssignment

No schema or behavior change (FR-007/FR-008). Documented here only to make the boundary explicit: this feature does not touch it.

## State transitions

Zone and ZoneManagerAssignment follow the identical creation/end pattern `SchoolAssignment` already established (specs/003 data-model.md) — no new transition shape, just a new dimension (multiple concurrent Managers per Zone):

```
        createZone(name)
              │
              ▼
        [ Zone exists, 0 current Managers ]
              │
    assignManagerToZone(managerId)  ──┐ (repeatable for
              │                       │  different managerIds —
              ▼                       │  each gets its own row)
   [ +1 currently-open                │
     ZoneManagerAssignment ] ◄────────┘
              │
   removeManagerFromZone(managerId)
              │
              ▼
   [ that (zone, manager) row's
     effectiveTo is set; other
     managers' rows unaffected ]
```

```
        assignSchoolToZone(schoolId, zoneId)
                     │
                     ▼
        [ SchoolZoneAssignment: schoolId's
          current zoneId = zoneId ]
                     │
   assignSchoolToZone(schoolId, differentZoneId, endsAssignmentId)
                     │
                     ▼
        [ old row ended, new row opened —
          existing SchoolAssignment (Manager)
          is NOT touched, per spec.md Assumptions ]
```
