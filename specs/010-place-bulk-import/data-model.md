# Data Model: Place Bulk Import

Derived from spec.md's Key Entities section and Functional Requirements FR-001 through FR-008. Extends `specs/008-school-places/data-model.md` — `Place` itself is unchanged, no new persisted entity.

## No new persisted entity

A bulk-imported `Place` row is stored identically to one added via the existing single-add endpoint — same `school_place` table, same fields, same invariants (never deleted, no uniqueness on name or PIN code). This feature adds a batch-shaped way to call the same write path, not a new table.

## Derived / query-only shapes (not persisted)

- **BulkPlaceRow** — the write-side shape of one row in a submitted batch: `zoneId`, `name`, `pincode`. Identical fields to the existing single-add `PlaceRequest`, just one element of a list. Backs FR-001.
- **BulkImportRowResult** — the read-side shape of one row's outcome, returned in the response: its `index` (position in the submitted batch, 0-based), and either a created `PlaceView` (on success) or a `String reason` (on failure) — never both. Backs FR-003.
- **BulkImportResponse** — the overall response shape: the list of `BulkImportRowResult`, plus a `successCount`/`failureCount` summary so the caller doesn't have to count the list itself to know whether the batch was fully, partially, or not-at-all successful (SC-002).

## Validation rules (per row, FR-004)

| Check | Failure reason reported |
|---|---|
| `name` present (non-blank) | `"name is required"` |
| `pincode` present (non-blank) | `"pincode is required"` |
| `zoneId` resolves to an existing Zone | `"zone <zoneId> does not exist"` |

## Batch-level validation (FR-005/FR-006, checked before any row is processed)

| Check | Failure |
|---|---|
| Batch is non-empty | 400, "batch must not be empty" |
| Batch size `<= 5000` | 400, "batch exceeds the maximum of 5000 rows" |

## State transitions

None beyond `Place`'s own (non-existent → added, specs/008 data-model.md) — bulk import doesn't introduce a new lifecycle, it's a batch-shaped call to the existing one.
