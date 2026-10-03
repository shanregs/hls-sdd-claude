# Phase 0 Research: Audit

## 1. Cross-module event delivery: Spring Modulith `@ApplicationModuleListener` vs plain `@EventListener`

**Decision**: `audit` subscribes to `LoginHistoryRecorded` (spec 001) and `PermissionMatrixChanged`
(spec 002) using Spring Modulith's `@ApplicationModuleListener`, not a plain `@EventListener`.

**Rationale**: `spring-modulith-starter-core` is already a backend dependency (added in spec 002).
`@ApplicationModuleListener` is transactional-outbox-backed by Modulith's event publication
registry: if `audit` is down or the handler throws, the registry retries the event after restart
instead of silently dropping it. Given Constitution Principle I's "every ... change MUST be
traceable" and FR-003's requirement that account-lifecycle actions are recorded, losing an event on
a transient failure is not acceptable — a plain in-JVM `@EventListener` gives no such guarantee.
This also matches the precedent already set: `LoginHistoryRecorded`'s and `PermissionMatrixChanged`'s
own Javadoc says they exist "for spec 003's Audit module to subscribe to," anticipating this.

**Alternatives considered**:
- Plain `ApplicationEventPublisher` + `@EventListener`: simplest, but synchronous-by-default and
  loses events on failure with no retry — rejected for an audit trail.
- A message broker (RabbitMQ/Kafka): far beyond this project's deployment constraints (single
  EC2/VM, <100 users) — rejected as premature infrastructure.
- Direct cross-module method calls from `identity` into `audit` (`identity` calls
  `AuditRecorder.record(...)` directly): rejected — it would make `identity` depend on `audit`,
  inverting the dependency the constitution specifies ("No module writes audit history to its own
  tables" implies audit is downstream, not a service identity calls synchronously) and the two
  event Javadocs already comitted to "identity does not depend on spec 003."

## 2. User Activity event sources not yet published by identity

**Decision**: this spec adds five small event-publishing calls inside `identity` (password reset
requested, password reset completed, session ended, account locked/unlocked, account deactivated)
across four files (`PasswordResetService`, `SessionController`, `PasswordAuthService`,
`UserAdminService`) — mirroring the existing `LoginHistoryPublisher`/`PermissionMatrixChanged`
pattern exactly: `identity` publishes a plain event record after its own write, `audit` is the only
subscriber, and `identity` gains no compile-time dependency on `audit`. Account reactivation is
explicitly out of scope (no reactivation capability exists anywhere in the codebase yet — spec 004
adds one); the `AccountActivationChanged` event type still carries a `boolean active` so spec 004
can start publishing `active=true` with no event-shape change.

**Rationale**: these actions already happen in `identity` (spec 001's `PasswordResetService`,
`SessionController`, lockout logic in the auth flow) — the gap is only that they don't yet publish
an event. This is the smallest change that satisfies FR-003 without duplicating identity's business
logic inside `audit`.

**Alternatives considered**:
- Have `audit` poll `identity`'s tables directly: violates Principle VII (module boundaries;
  cross-module calls only through public interfaces) and the "no module writes audit history to its
  own tables" rule taken to its logical converse (audit should not reach into another module's
  tables either).
- Defer User Activity to spec 004 (user-role-management) since that spec also touches account
  lifecycle: rejected — spec 004 depends on 003 per the roadmap (`004` depends on `002, 003`), so
  003 cannot depend on 004's not-yet-existing events; the four events above are all already
  triggerable by spec 001 alone.

## 3. Storage shape: one audit table vs three

**Decision**: three physical tables — `login_history_entry`, `change_history_entry`,
`user_activity_entry` — each append-only, plus a read-side query layer that a single repository
method composes into the unified Audit Logs feed (US4) by `UNION ALL` at query time, not by a
fourth denormalized table.

**Rationale**: the three entry types have genuinely different columns (a login has method/outcome;
a change has field/before/after; an activity has action/detail) — forcing one shared table would
mean many always-null columns and a `payload` JSON blob that regexp-style filtering (FR-005–FR-007)
would have to reach into. Three typed tables keep each one queryable with normal indexed `WHERE`
clauses. The combined view (FR-008) only needs a shared minimal projection (timestamp, actor,
type, summary), which a `UNION ALL` across three typed queries produces cheaply at this project's
scale (<100 users, well under the volumes where a materialized union would be needed).

**Alternatives considered**:
- One polymorphic `audit_entry` table with a `type` discriminator and nullable type-specific
  columns: rejected for the reasons above (sparse columns, harder filtering, harder to add a new
  entry type's own columns later without touching the shared table).
- A separate `audit_log` materialized/denormalized table populated by triggers or a write-time fan-
  out: rejected as unnecessary complexity — three read queries `UNION ALL`'d at this scale is fast
  enough, and every module still writes to exactly one place (its own typed table), matching
  Principle I.

## 4. Immutability enforcement (FR-004)

**Decision**: enforce "no edit, no delete" at the application layer only (the repositories exposed
by `audit` provide `save`/`find` methods and never an `update` or `delete` method — there is no
`AuditRecordController` write endpoint at all beyond the internal `record(...)` calls triggered by
domain events), consistent with how `LoginHistoryEvent` already works in spec 001 (no update/delete
methods on `LoginHistoryEventRepository` either). Database-level `REVOKE UPDATE, DELETE` grants are
out of scope for this spec (the app runs as a single DB role today; a stricter DB-level guarantee is
a deployment/ops decision, not this feature's).

**Rationale**: matches the existing precedent exactly (no new pattern to introduce) and keeps the
guarantee testable at the Spring Data repository level (an ArchUnit rule can assert no `delete*` or
`save`-after-fetch update path exists on audit entities, same style as the existing
`ArchitectureRulesTest`).

**Alternatives considered**: DB triggers or `REVOKE`-based enforcement — more robust in theory, but
this project's constitution places the enforcement responsibility on "the backend is the security
control" (Principle X) generally, and no other module in this codebase uses DB-level immutability
triggers yet; introducing one only for audit would be an inconsistent, one-off pattern.

## 5. Pagination, filtering, and export mechanics

**Decision**: Spring Data `Pageable` (page/size/sort) on every list endpoint, same as any other list
screen in the system (Constitution Principle IV: "consistent tables with pagination, sorting, and
filters"). CSV export reuses the same filter parameters as the list endpoint, streams rows without
loading the full result set into memory (`ResponseBodyEmitter` or a streaming
`ResponseEntity<StreamingResponseBody>`), and requires the same mandatory date-range bound already
established for other exports in this project's Additional Constraints, preventing an unbounded
full-table dump (edge case in spec.md).

**Rationale**: no new infrastructure — `spring-boot-starter-web` and `spring-data-jpa` already
support all of this. Streaming export avoids an OOM risk on a single small EC2/VM instance at the
"at least 10,000 entries" scale from SC-004.

**Alternatives considered**: an async export job with a downloadable-later link — over-engineered
for this project's scale (<100 users) and not requested by any functional requirement; a synchronous
streamed CSV response satisfies FR-009/SC-006 directly.

## 6. Decommissioning `identity`'s own `login_history_event` table

**Decision**: once this spec's `LoginHistoryEventConsumer` (login history) is live and verified,
`LoginHistoryPublisher` stops writing to its own `login_history_event` table, and that table, its
JPA entity (`LoginHistoryEvent`), and its repository are removed via a follow-up Flyway migration.
`identity` keeps publishing `LoginHistoryRecorded` (unchanged) — only the local persistence is
dropped.

**Rationale**: Constitution Principle VII is explicit: "No module writes audit history to its own
tables." Spec 001 pre-dated `audit`'s existence and had nowhere else to persist login events, so it
wrote its own table as an interim measure — its own Javadoc already anticipated this
(`LoginHistoryRecorded`: "for spec 003's Audit module to subscribe to"). With `audit` now the real
store, keeping the interim table would leave a standing constitution violation. It's safe to remove:
nothing in the codebase reads `LoginHistoryEventRepository` today (verified by search) — it is
purely a write sink with no controller, service, or UI ever querying it — so dropping it loses no
reachable functionality and needs no data migration.

**Alternatives considered**:
- Leave both tables in place permanently ("identity keeps its own copy"): rejected — this is the
  exact pattern Principle VII forbids, and keeping an unread, unmaintained duplicate store is a
  liability (schema drift, double the migration surface) for zero benefit.
- Have `identity` read from `audit`'s table instead of maintaining its own: rejected — that would
  make `identity` depend on `audit`, inverting the one-directional dependency this spec's design
  otherwise preserves throughout (§1); `identity` needs no read access to login history at all today.
