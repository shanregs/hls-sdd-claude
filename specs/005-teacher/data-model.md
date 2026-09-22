# Data Model: Teacher Master Data

Derived from spec.md's Key Entities section and Functional Requirements FR-001 through FR-011.

## TeacherProfile

One record per teacher. The only persisted entity in this module — the spec's "Profile Change Record" concept (FR-005) is realized through the `audit` module's `AuditEntry` rows (research.md §5), not a second table here. No bank-detail fields (out of scope, spec.md Assumptions, scope correction 2026-09-22).

| Field | Type | Notes |
|---|---|---|
| `id` | UUID (PK) | Assigned at creation (`UUID.randomUUID()`) |
| `name` | String, not null | |
| `phone` | String, not null | Contact phone (FR-001) — administratively independent of the login phone number on `identity_user` (spec.md Assumptions) |
| `email` | String, nullable | |
| `hlsOfferedSalary` | Numeric (₹, monthly), not null | Independent of the school's contracted rate (Requirements §5) — that figure belongs to a future SchoolBilling/Contract module, not this one |
| `status` | Enum (`IN_TRAINING`, `ACTIVE`, `ON_LEAVE`, `EXITED`), not null | FR-003 |
| `createdAt` | timestamp (UTC), not null | |
| `createdBy` | UUID, not null | The Admin `userId` who created this profile |

**Invariants**:

- Rows are never deleted (FR-004) — enforced by `TeacherProfileRepository` exposing no delete method at all (research.md §4).
- A teacher who leaves HLS is represented by `status = EXITED` on the same row, never by removing it; a later return is represented by changing `status` again on that same row (spec.md Edge Cases), never a second profile for the same person.
- Every change to `status`, `phone`/`email`, or `hlsOfferedSalary` produces a corresponding `audit.api.AuditEntry` (`entityType = "TeacherProfile"`, `entityId = this row's id`) in the same transaction as the change itself (FR-005, research.md §5) — the row's *current* values are what's queried directly; its full history is queried through `audit`, not duplicated as extra columns here.

## Derived / query-only shapes (not persisted)

- **TeacherProfileView** — the read-side shape of one `TeacherProfile` row, returned by `TeacherQueries.findById(...)` and the REST endpoints: `id`, `name`, `phone`, `email`, `hlsOfferedSalary`, `status`, `createdAt`. Backs FR-006/007/008.
- **CreateTeacherProfileRequest** — the write-side shape for `TeacherCommands.create(...)`: `name`, `phone`, `email`, `hlsOfferedSalary`, `status` (initial status, typically `IN_TRAINING`). Backs FR-001.
- **UpdateTeacherProfileRequest** — the write-side shape for `TeacherCommands.updateProfile(...)`: every field except `status` is optional (nullable), so a single call can update just the fields that actually changed (`phone`, `email`, `hlsOfferedSalary`) without requiring the caller to resend the whole profile. Backs FR-002.

## State transitions

`status` is the only field with a constrained transition set — every other field is freely editable by Admin at any time (FR-002).

```
        create(initial status)
                 │
                 ▼
        [ status = IN_TRAINING ]  (typical initial value, not enforced —
                 │                  Admin may set any initial status, FR-001)
                 │
        changeStatus(newStatus)
                 │
                 ▼
   ┌─────────────┼─────────────┬─────────────┐
   │             │             │             │
IN_TRAINING → ACTIVE → ON_LEAVE → ACTIVE → EXITED → (reactivated:
   │             │             │             │        IN_TRAINING or ACTIVE)
   └─────────────┴─────────────┴─────────────┘
        (every transition allowed; no illegal-transition rule in
         scope for this module — spec.md names no such constraint,
         and Admin is trusted with unscoped master-data maintenance
         per Constitution Principle II)
```

Every transition — including reactivation after `EXITED` — is the same `changeStatus(...)` call, producing one more `AuditEntry` on the same `TeacherProfile` row (FR-005). There is no separate "reactivate" operation.
