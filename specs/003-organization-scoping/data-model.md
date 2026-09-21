# Data Model: Organization — Manager/School/Teacher Accountability

Derived from spec.md's Key Entities section and Functional Requirements FR-001 through FR-013.

## SchoolAssignment

Represents one period during which a Manager was (or is) accountable for a School.

| Field | Type | Notes |
|---|---|---|
| `id` | UUID (PK) | |
| `schoolId` | UUID | Opaque reference — no FK constraint yet; School Master Data doesn't exist as a table (see research.md §3) |
| `managerId` | UUID | References an existing user identity managed by Identity & Access; no local FK either, same reasoning |
| `effectiveFrom` | timestamp (UTC) | Set when the row is created; never changed afterward |
| `effectiveTo` | timestamp (UTC), nullable | `NULL` means this is the *current* assignment (FR-005). Set exactly once, when the assignment is ended by a reassignment or an explicit end-with-no-replacement (edge case 1) |
| `assignedBy` | UUID | The Director/Admin `userId` who created this row (FR-006) |
| `assignedAt` | timestamp (UTC) | When this row was created — distinct from `effectiveFrom`, which is when the assignment takes effect (both are the same value for v1, since FR-004 says assignments take effect immediately, but the columns are kept separate to hold if future-dated assignments are ever added) |

**Invariants**:
- At most one `SchoolAssignment` per `schoolId` has `effectiveTo IS NULL` at any time — enforced by a partial unique index (research.md §1), not just application logic.
- Rows are never updated except to set `effectiveTo` exactly once (ending the assignment), and never deleted (FR-005, Constitution Principle I).
- Assigning the same `managerId` that is already the current one for a `schoolId` is a no-op (FR-010) — no new row is created.

## TeacherAssignment

Identical shape and invariants to `SchoolAssignment`, with `teacherId` in place of `schoolId`. Kept as a fully separate table (not a polymorphic/shared table) because FR-013 requires School-level and Teacher-level accountability to be independently queryable and independently mutable — a shared table with a "type" discriminator would make that independence a lookup detail instead of a structural guarantee.

| Field | Type | Notes |
|---|---|---|
| `id` | UUID (PK) | |
| `teacherId` | UUID | Opaque reference — see research.md §3 |
| `managerId` | UUID | Same as `SchoolAssignment.managerId` |
| `effectiveFrom` | timestamp (UTC) | |
| `effectiveTo` | timestamp (UTC), nullable | |
| `assignedBy` | UUID | |
| `assignedAt` | timestamp (UTC) | |

## Derived / query-only shapes (not persisted)

These are what the `api` package's DTOs expose — they're computed from the two tables above, never stored directly.

- **AccountabilityAnswer** — one of three states for a given School or Teacher identifier, as of a given date (default: now): `CURRENT_MANAGER(managerId)`, `UNASSIGNED`, or `UNKNOWN_IDENTIFIER` (FR-012; the `UNKNOWN_IDENTIFIER` branch is not truly reachable yet — see research.md §3). Backs FR-007.
- **PortfolioItem** — one row per School or Teacher currently accountable to a given Manager (`itemType`, `itemId`, `since` = that assignment's `effectiveFrom`). Backs FR-008.
- **AssignmentHistoryEntry** — one row per past-or-current `SchoolAssignment`/`TeacherAssignment` for a given identifier, ordered by `effectiveFrom`, exposing `managerId`, `effectiveFrom`, `effectiveTo`, `assignedBy`, `assignedAt`. Backs User Story 2's acceptance scenario 2 (full history with no gaps/overlaps).
- **UnassignedItem** — one row per School or Teacher with no row where `effectiveTo IS NULL` (including identifiers that have *never* been assigned). Backs FR-009.

## State transitions

```
                    assign(schoolId, managerId)
                              │
                              ▼
                 [ no current row exists ]
                              │
                    INSERT effectiveTo = NULL
                              │
                              ▼
                    (CURRENT: this row is now
                     the answer to "who is
                     accountable for schoolId")
                              │
        ┌─────────────────────┼─────────────────────┐
        │                     │                     │
 reassign(newManagerId) end-with-no-replacement  assign(sameManagerId)
        │                     │                     │
   UPDATE effectiveTo=now     UPDATE effectiveTo=now      no-op (FR-010)
   INSERT new row             (no new row)
   (effectiveTo=NULL)               │
        │                           ▼
        ▼                    [ UNASSIGNED state:
   (new CURRENT row)          schoolId has no row
                               with effectiveTo IS NULL —
                               surfaced on FR-009's list ]
```

Both `reassign` and `end-with-no-replacement` go through the same conditional `UPDATE ... WHERE id = ? AND effectiveTo IS NULL` described in research.md §2; if that update affects zero rows, the operation fails with a conflict (FR-011) instead of proceeding.
