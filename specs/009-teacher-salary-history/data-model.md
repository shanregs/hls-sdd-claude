# Data Model: Teacher Salary History

Derived from spec.md's Key Entities section and Functional Requirements FR-001 through FR-009. Extends `specs/005-teacher/data-model.md` — `TeacherProfile` loses its `hlsOfferedSalary` field (research.md §2), everything else there is unchanged.

## TeacherSalaryHistory

One record per recorded salary amount for a teacher. Never edited or deleted once recorded (FR-004).

| Field | Type | Notes |
|---|---|---|
| `id` | UUID (PK) | |
| `teacherId` | UUID, not null | The owning Teacher Profile — no FK constraint, consistent with this module's and `school`'s established opaque-reference style |
| `amount` | Numeric (₹, monthly), not null | Same precision/scale as specs/005's original `hlsOfferedSalary` (`NUMERIC(12,2)`) |
| `effectiveFrom` | `LocalDate`, not null | Calendar date, not a timestamp (research.md §4) |
| `createdAt` | timestamp (UTC), not null | When this entry was recorded — distinct from `effectiveFrom` (a backdated correction has a `createdAt` after its `effectiveFrom`, Edge Cases) |
| `createdBy` | UUID, not null | The Admin `userId` who recorded it |

**Invariants**:

- Rows are never deleted or edited (FR-004) — enforced by `TeacherSalaryHistoryRepository` exposing no delete/update-by-id method at all, mirroring every other never-deleted repository in this codebase.
- Every `TeacherProfile` has at least one `TeacherSalaryHistory` row from the moment it's created (FR-002) — `TeacherService.create(...)` writes both rows in the same transaction.
- "Current salary" = the row with the greatest `effectiveFrom` that is `<= today`, tie-broken by the greatest `createdAt` (research.md §5 — same-day duplicate entries, most-recently-recorded wins).
- "Salary as of date X" = the row with the greatest `effectiveFrom` that is `<= X`, same tie-break; no matching row means "not yet recorded as of that date" (FR-007), not an error and not zero.

## Changes to `TeacherProfile` (specs/005-teacher)

- **Removed**: `hlsOfferedSalary` column/field and the `updateSalary(BigDecimal)` mutator — salary no longer lives on this entity at all (research.md §2).
- Everything else (`id`, `name`, `phone`, `email`, `status`, `createdAt`, `createdBy`, `updateContact(...)`, `changeStatus(...)`) is unchanged.

## Derived / query-only shapes (not persisted)

- **TeacherProfileView** (specs/005-teacher, unchanged shape) — `hlsOfferedSalary` is now populated by looking up the current `TeacherSalaryHistory` row for that teacher at read time, not read off a stored column.
- **SalaryAsOfAnswer** — `(State state, BigDecimal amount)`, `State { RECORDED, NOT_YET_RECORDED }` — the same "answer" pattern `school.api.dto.SchoolZoneAnswer` already established for "current value or a clear not-yet-assigned state" (FR-007).
- **CreateTeacherProfileRequest** (specs/005-teacher, unchanged shape) — `hlsOfferedSalary` here is now specifically "the initial salary, recorded as the first history entry" (FR-002), not a profile column being set directly.
- **UpdateTeacherProfileRequest** (specs/005-teacher, changed) — drops `hlsOfferedSalary`; now only `name`/`phone`/`email` (research.md §3).
- **RecordSalaryChangeRequest** (new) — `(BigDecimal amount, LocalDate effectiveFrom)`, `effectiveFrom` nullable/optional (defaults to today — FR-003).

## State transitions

`TeacherSalaryHistory` has exactly one transition: non-existent → recorded (FR-001/FR-003). There is no update or delete path — a correction is a new entry with an earlier `effectiveFrom` than when it was recorded (Edge Cases' "backdated correction"), never a change to an existing row.
