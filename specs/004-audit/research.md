# Research: Audit Trail

No `[NEEDS CLARIFICATION]` markers remain in the Technical Context — spec.md's Assumptions section already resolved the open scope questions (viewer roles, per-record granularity, migration out of scope). This phase resolves the technical *how* for the constraints Technical Context flagged.

## 1. Building a module whose real callers don't exist yet

**Finding**: Unlike `organization` (which Identity's `ManagerScopeGuard` was already calling, via a stand-in, before `organization` existed), no attendance/payroll/school-payment/expense/substitution module exists in the codebase yet — `audit`'s five illustrative record types (spec.md User Story 1) have no owning module to call `AuditWriter.record(...)` from.

**Decision**: Build `audit`'s public `api` package (`AuditWriter`, `AuditReader`, DTOs) as the real, locked contract future modules will call in-process — exactly as designed, no stand-in on audit's own side. Prove User Story 1 (the write path) and FR-004 (atomicity) with tests that call `AuditWriter` directly, simulating what a future module's service method will do, inside a real Spring-managed transaction. This mirrors what spec.md's own Assumptions section already states: "this feature defines and builds the capability itself, not the other modules' use of it."

**Rationale**: `audit` is infrastructure every future financial/attendance module depends on (Constitution Principle V names it as "the single append-only store" other modules write through) — it has to exist and be stable before those modules can be built against it, the same dependency-ordering logic that put `organization` before payroll in the module tracker. Waiting for a real caller to exist first would invert that ordering.

**Alternatives considered**:
- *Defer this module until at least one real financial/attendance module exists*: rejected — that module would then need to build its own ad hoc history mechanism first (as `identity` did for login/security events, see §6) or go without auditability entirely while waiting, either of which contradicts Constitution Principle I.

## 2. Keeping `audit` generic across record types it doesn't know about yet

**Decision**: `AuditRecordRequest`/`AuditEntryView` identify the record being audited with plain strings — `sourceModule` (e.g. `"attendance"`), `entityType` (e.g. `"AttendanceRecord"`), `entityId` (the owning module's own identifier, stringified). `beforeValue`/`afterValue` are opaque strings (the calling module's own serialization, typically JSON) that `audit` stores and returns verbatim without parsing or validating their structure.

**Rationale**: `audit` cannot depend on Java types from modules that don't exist yet (`attendance.api.AttendanceRecord` isn't a thing), and per Constitution Principle V the dependency direction must stay one-way — future modules depend on `audit.api`, `audit` depends on nothing from them. Opaque strings are the only shape that's simultaneously generic enough for any future module and still satisfies FR-003's "what changed... before... after" requirement.

**Alternatives considered**:
- *A typed `Map<String, Object>` of changed fields instead of opaque before/after strings*: rejected — still requires `audit` to agree on a value-typing scheme with every future caller (numbers vs. dates vs. enums serialize differently), which is exactly the kind of coupling the opaque-string approach avoids; a human-readable `summary` field (FR-003's "what changed") plus opaque before/after covers the requirement without it.

## 3. Guaranteeing FR-004 — an audited change and its audit entry succeed or fail together

**Decision**: `AuditWriter.record(...)` is an ordinary in-process method call, invoked by the calling module's own service method *inside that method's existing Spring-managed transaction* (default `@Transactional` propagation, `REQUIRED`). If `record(...)` throws (e.g. a constraint violation), the surrounding transaction rolls back, undoing the caller's own write along with it — no special coordination code needed.

**Rationale**: This is a single-database modular monolith (Constitution Principle V), not a distributed system — same-transaction in-process calls are the simplest mechanism that gives FR-004's atomicity guarantee for free, and it's the same pattern `organization`'s and `identity`'s own single-database writes already rely on implicitly. A distributed pattern (outbox table + async relay) would solve a problem this architecture doesn't have.

**Alternatives considered**:
- *Transactional outbox / async audit relay*: rejected — adds a background relay process, a second failure mode ("the business write succeeded but the relay hasn't caught up yet"), and eventual- rather than immediate-consistency, none of which this single-DB monolith needs; it's the right pattern for a message-broker or multi-service architecture, which Constitution Principle V explicitly says this system is not (no microservices by default).

## 4. Enforcing "never edited or deleted, even by an Admin" (FR-005, FR-006)

**Decision**: `AuditEntryRepository` extends bare `Repository<AuditEntry, UUID>` (Spring Data's no-method marker interface) and hand-declares only `save(AuditEntry)` and read-only finder methods — no `delete`/`deleteById`/`update` method exists anywhere in the codebase for this entity. `AuditController` exposes only a `GET` endpoint; no `PUT`/`PATCH`/`DELETE` mapping for audit entries exists at all, at any role. A "correction" is always a brand-new `AuditWriter.record(...)` call producing a new row (FR-006) — `audit` has no concept of "edit an existing entry" in its public API for a caller to even attempt.

**Rationale**: This is exactly the pattern `identity.internal.AuthAuditEntryRepository` already established for its own (different) audit table — "deliberately narrower than `JpaRepository`... there is nothing to accidentally call." Reusing a proven, existing in-codebase pattern is lower-risk than introducing a new one, and it makes the guarantee a compile-time fact (the method literally doesn't exist to call), not a runtime check that could have a bug or a bypass.

**Alternatives considered**:
- *Database-level `REVOKE UPDATE, DELETE ON audit_entry FROM <app role>`, in addition to the narrow repository*: considered as a defense-in-depth hardening step, but rejected for this plan — the project currently runs Flyway migrations and the application itself under one shared PostgreSQL role (`hls`, per `docker-compose.yml`), with no separate least-privilege runtime role yet. Introducing one is real infrastructure work outside this feature's scope, and as the table's owner that same role could always re-`GRANT` itself the privilege back, so the guarantee would be soft in this specific setup, not a hard boundary — not worth the added migration complexity for a guarantee the narrow-repository pattern already gives against every code path the application actually has. Worth revisiting if/when a dedicated low-privilege application DB role is introduced for other reasons.

## 5. Breaking ties when multiple entries land in the same instant (FR-011)

**Decision**: `audit_entry` gets a `BIGSERIAL` `sequence_no` column (via the Flyway migration) in addition to `occurred_at`. History is always ordered `ORDER BY sequence_no ASC` — `occurred_at` is stored and displayed for its business meaning ("when did this happen"), but `sequence_no`, assigned by the database at insert time, is the actual, unambiguous ordering key.

**Rationale**: Two entries can legitimately share the same millisecond under concurrent writes (Edge Case 2); a database-assigned auto-increment column can never collide, unlike a wall-clock timestamp, and needs no extra locking to guarantee.

**Alternatives considered**:
- *Order by `occurred_at` alone, accept same-instant ties as arbitrarily ordered*: rejected — FR-011 explicitly requires an unambiguous order, and "arbitrary" isn't that.

## 6. Why Identity's existing `AuthAuditEntry` (login/session/security events) is out of scope here

**Finding**: `identity.internal` already has its own self-contained, append-only audit log (`AuthAuditEntry`/`AuthAuditLogger`/`AuthAuditEntryRepository`, table `identity_auth_audit_entry`) for login, session, and account-lockout events, built before this module existed. Constitution Principle V says "no module writes audit history directly to its own tables" — read literally, `identity` doing exactly that for its own table could look like a conflict with this feature.

**Decision**: Leave `identity`'s existing mechanism as-is; do not migrate it to route through the new `audit` module as part of this feature.

**Rationale**: Principle V's "no module writes audit history directly to its own tables" clause exists to mechanically implement Principle I, and Principle I's scope is explicit — "every **financial or attendance** change must be traceable." Login success/failure, session revocation, and account lockout are security/access events, not financial- or attendance-affecting records; they fall outside what this feature (and Principle I) covers, the same way spec.md's Functional Requirements only ever name attendance, payroll, school payment, expense, and substitution records. Migrating `identity`'s security log into `audit` would be a real, separate piece of work with its own risk (touching already-tested, shipped code) for a category of event this feature was never scoped to cover.

**Alternatives considered**:
- *Migrate `identity`'s audit log into the new `audit` module now, for full Principle V literalism*: rejected as out of scope — not requested by spec.md, not required by Principle I's actual (financial/attendance) scope, and would mean editing tested code in another already-shipped module for a rule that reads narrower than that migration would imply. Flagged here for traceability in case a future amendment widens Principle V's literal scope.

## 7. Viewing surface: one generic endpoint, not one per record type

**Decision**: A single `GET /api/v1/audit/{entityType}/{entityId}/history` endpoint (Director/Admin only, FR-007/FR-008) serves every record type, rather than a family of type-specific endpoints (e.g. one for attendance, one for payroll).

**Rationale**: No financial/attendance modules exist yet to motivate type-specific shapes, and `entityType`/`entityId` are already opaque per decision §2 — a single generic lookup is the only shape that doesn't require guessing at modules that don't exist. A minimal `AuditHistoryPage` (Director/Admin, entity type + entity id lookup) is added to `frontend/` on the same reasoning `organization`'s `AssignmentsPage` used: Identity's real login already exists, so nothing blocks building it now.

**Alternatives considered**:
- *Wait for a real caller module before building any viewing UI*: rejected — User Story 2 is independently valuable and independently testable against entries written by the test-only simulated caller from §1; there's no reason to gate it on a specific future module existing.
