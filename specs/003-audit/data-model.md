# Phase 1 Data Model: Audit

All three tables are append-only: rows are inserted once and never updated or deleted by the
application (FR-004; research.md §4). None carries a foreign key to another module's tables —
`user_id`/`actor_user_id` are plain UUID columns, consistent with how `login_history_event`
(spec 001) already references users, so `audit` never has to join across module boundaries at
query time.

## Login History Entry

Maps 1:1 to `LoginHistoryRecorded` (spec 001).

| Column | Type | Notes |
| --- | --- | --- |
| `id` | UUID, PK | |
| `occurred_at` | timestamptz, not null | |
| `source_event_id` | UUID, not null, unique | the `eventId` from `LoginHistoryRecorded` — de-duplicates if Modulith's event registry ever redelivers |
| `user_id` | UUID, nullable | null for an attempt against an unregistered identifier |
| `phone_masked` | text, not null | already masked by `identity` before publishing |
| `method` | text, not null | `PASSWORD` \| `OTP` |
| `event_type` | text, not null | mirrors spec 001's `LoginEventType` (sign-in attempt, lockout, etc.) |
| `outcome` | text, not null | |

Indexes: `(user_id, occurred_at desc)`, `(occurred_at desc)`.

## Change History Entry

Maps 1:1 to `PermissionMatrixChanged` today (spec 002); shaped to accept other modules' future
change events without a schema change (`entity_type`/`entity_id` are generic, not
permission-matrix-specific columns).

| Column | Type | Notes |
| --- | --- | --- |
| `id` | UUID, PK | |
| `occurred_at` | timestamptz, not null | |
| `source_event_id` | UUID, not null, unique | added to `PermissionMatrixChanged` itself (identity) rather than generated at consumption time, so Change History dedups on redelivery the same way Login History does |
| `actor_user_id` | UUID, not null | |
| `entity_type` | text, not null | `PERMISSION_MATRIX` for this spec's only source |
| `entity_id` | text, not null | e.g. `"MANAGER.ATTENDANCE.EDIT"` (role.module.action) for a matrix change |
| `field` | text, not null | e.g. `"granted"` |
| `before_value` | text, nullable | |
| `after_value` | text, nullable | |

Indexes: `(actor_user_id, occurred_at desc)`, `(entity_type, occurred_at desc)`.

## User Activity Entry

Maps 1:1 to five new events this spec adds to `identity` (research.md §2):
`PasswordResetRequested`, `PasswordResetCompleted`, `SessionEnded`, `AccountLockChanged` (used for
both lock and unlock), `AccountActivationChanged` (carries a `boolean active`, but this spec only
ever publishes it with `active=false` — deactivation; `active=true`/reactivation has no caller until
spec 004 adds a reactivation capability, FR-003).

| Column | Type | Notes |
| --- | --- | --- |
| `id` | UUID, PK | |
| `occurred_at` | timestamptz, not null | |
| `source_event_id` | UUID, not null, unique | |
| `actor_user_id` | UUID, nullable | null when the system itself is the actor (e.g., an automatic lockout after N failed attempts has no human actor) |
| `affected_user_id` | UUID, not null | |
| `action` | text, not null | `PASSWORD_RESET_REQUESTED` \| `PASSWORD_RESET_COMPLETED` \| `SESSION_ENDED` \| `ACCOUNT_LOCKED` \| `ACCOUNT_UNLOCKED` \| `ACCOUNT_DEACTIVATED` \| `ACCOUNT_REACTIVATED` (reserved — no producer in this spec, see above) |
| `detail` | text, nullable | free-text context, e.g. which session was ended |

Indexes: `(affected_user_id, occurred_at desc)`, `(occurred_at desc)`.

## Audit Log Entry (read model only, not a table)

The unified feed behind FR-008/US4 is a query-time projection — `UNION ALL` across the three tables
above (research.md §3), not a stored entity:

| Field | Derived from |
| --- | --- |
| `occurredAt` | each source table's `occurred_at` |
| `type` | `LOGIN` \| `CHANGE` \| `ACTIVITY`, a literal per branch of the union |
| `actorUserId` | `user_id` (login) / `actor_user_id` (change, activity) |
| `summary` | a short, type-specific human-readable line built at query time, e.g. `"Password sign-in failed"`, `"MANAGER.ATTENDANCE.EDIT set to true"`, `"Account deactivated"` |

## Validation rules

- Every insert is transactional with the triggering domain event's consumption — if the insert
  fails, Modulith's event publication registry retries (research.md §1); there is no partial/silent
  drop.
- `source_event_id` uniqueness constraints make re-delivery of the same domain event a no-op
  (insert is skipped, not duplicated) rather than an error surfaced to the event publisher.
- No column in any of the three tables is ever updated after insert; the JPA entities expose no
  setters beyond what the constructor requires (same pattern as spec 001's `LoginHistoryEvent`).

## State transitions

None — every entity here is create-only; there is no lifecycle/status field that transitions.
