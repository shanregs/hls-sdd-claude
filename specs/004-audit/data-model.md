# Data Model: Audit Trail

Derived from spec.md's Key Entities section and Functional Requirements FR-001 through FR-011.

## AuditEntry

A single, permanent record of one change to one financial- or attendance-affecting record from another module. The only persisted entity in this module.

| Field | Type | Notes |
|---|---|---|
| `id` | UUID (PK) | Assigned by `audit` at write time (`UUID.randomUUID()`), same pattern as `identity.internal.AuthAuditEntry` |
| `sequenceNo` | BIGSERIAL | Database-assigned, monotonically increasing. The definitive ordering key (research.md §5) — never set by application code |
| `sourceModule` | String | Which module wrote this entry, e.g. `"attendance"`, `"payroll"` (research.md §2) |
| `entityType` | String | The owning module's name for the record, e.g. `"AttendanceRecord"`, `"Payslip"` — opaque to `audit` |
| `entityId` | String | The owning module's own identifier for the record, stringified — opaque to `audit`, no FK (the owning table may not even exist in this database) |
| `action` | Enum (`CREATED`, `UPDATED`, `CORRECTED`) | Matches spec.md's "created, changed, or corrected" (FR-002) |
| `summary` | String | Human-readable description of what changed (FR-003's "what changed"), e.g. `"status: Present -> Leave"` |
| `beforeValue` | String, nullable | Caller-supplied, typically JSON; `NULL` for `CREATED` (User Story 1, acceptance scenario 3) |
| `afterValue` | String, nullable | Caller-supplied, typically JSON; always present in practice since nothing is hard-deleted (Constitution Principle I) |
| `actorUserId` | UUID | The identified actor who made the change (FR-010) — never null, `audit` refuses to record an unattributed change |
| `actorRole` | String, nullable | Snapshot of the actor's role at the time, same rationale as `identity.internal.AuthAuditEntry.roleAtTime` (roles can change later; the entry should reflect what was true then) |
| `occurredAt` | timestamp (UTC) | Server-assigned (`Clock.instant()`), never trusted from the caller — display/business meaning; `sequenceNo` is the real order (research.md §5) |
| `requestId` | String, nullable | Correlation ID (Constitution Principle VIII), propagated from the caller's own request context the same way `identity`'s audit log already does |

**Invariants**:

- Rows are only ever inserted, never updated or deleted (FR-005, FR-006) — enforced by `AuditEntryRepository` exposing no update/delete method at all (research.md §4), the same pattern as `identity.internal.AuthAuditEntryRepository`.
- `beforeValue`/`afterValue`/`summary` are opaque to `audit` — it stores and returns them verbatim, never parses or validates their internal structure (research.md §2).
- A correction to previously recorded information is always a new row (`action = CORRECTED`) referencing the same `(entityType, entityId)`, never a change to an existing row (FR-006).
- History for a given `(entityType, entityId)` is retrieved ordered by `sequenceNo ASC`, which stays valid even if the record itself is later archived/deactivated in its owning module (FR-009) — `audit` has no relationship to the record's current status, only to its own append-only history.

## Derived / query-only shapes (not persisted)

What the `api` package's DTOs expose — computed directly from `AuditEntry` rows, nothing additional stored.

- **AuditEntryView** — the read-side shape of one `AuditEntry` row, returned by `AuditReader.history(...)` and by the REST endpoint: `id`, `sequenceNo`, `sourceModule`, `entityType`, `entityId`, `action`, `summary`, `beforeValue`, `afterValue`, `actorUserId`, `actorRole`, `occurredAt`, `requestId`. Backs User Story 2.
- **AuditRecordRequest** — the write-side shape passed to `AuditWriter.record(...)`: `sourceModule`, `entityType`, `entityId`, `action`, `summary`, `beforeValue`, `afterValue`, `actorUserId`, `actorRole`, `requestId`. No `id`, `sequenceNo`, or `occurredAt` — all three are assigned by `audit` itself, never accepted from a caller (research.md §5; FR-010's attribution guarantee would otherwise be spoofable).

## State transitions

There is no state machine — `AuditEntry` rows have exactly one transition: non-existent → inserted. There is no second state (no "ended", "corrected-in-place", or "deleted" status on the row itself). A correction is a wholly new row, not a transition of an old one:

```
   AuditWriter.record(request)
              │
              ▼
   [ audit assigns id, sequenceNo, occurredAt ]
              │
              ▼
      INSERT (append-only)
              │
              ▼
   (permanent — no further transition
    is possible for this row; a later
    correction inserts an unrelated
    new row against the same entityId)
```
