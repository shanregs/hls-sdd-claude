# Phase 0 Research: Master Data

## 1. One generic `EntityChanged` audit event instead of one event class per change

**Decision**: `audit` exposes a single public event record, `com.hls.audit.api.EntityChanged`
(`eventId`, `occurredAt`, `actorUserId`, `entityType`, `entityId`, `field`, `beforeValue`,
`afterValue`). `school`, `organization` and `teacher` publish one per changed field inside the same
transaction as the write; a new `EntityChangedConsumer` in `audit.changehistory` appends a
`change_history_entry`, deduplicating on `eventId` like every other consumer.

**Rationale**: spec 003 designed `change_history_entry` as generic on purpose (entity type / id /
field / before / after). This spec has roughly twenty kinds of change (zone rename, school edit,
assignment, placement, status, salary...). One event class per kind would add twenty near-identical
records and twenty consumer methods. A single event type keeps `audit` closed to modification: new
modules publish the same event, with no audit change at all.

**Dependency direction**: `school`/`organization`/`teacher` -> `audit.api`; `audit` still depends
only on `identity`. No cycle. `ApplicationModuleBoundaryTest`'s expected-consumed set gains
`com.hls.audit.api.EntityChanged`.

**Alternatives considered**: per-module event classes consumed by `audit` (rejected: audit would
depend on every module and need an edit per feature - the opposite of the "sink" design); modules
calling an `AuditWriter` service directly (rejected: loses the transactional-outbox durability
spec 003 chose, and couples callers to audit's write path).

## 2. Guard SPIs keep the module graph acyclic

**Decision**: where one module must be able to veto another module's change, the *owning* module
defines a small interface in its public API and the *dependent* module implements it as a Spring
bean. The owner injects `List<...Guard>` and calls each before committing:

| Guard (defined in) | Implemented by | Veto |
| --- | --- | --- |
| `school.api.SchoolChangeGuard` | `organization` | moving a School to a Place in a Zone its Manager does not cover |
| `school.api.ZoneChangeGuard` | `organization` | deleting a Zone that still has Managers assigned |
| `school.api.SchoolDeactivationGuard` | `teacher` | deactivating a School with active Teachers placed in it or scheduled to arrive |

Conversely `organization` owns the Zone-Manager removal rule ("Manager still has Schools in that
Zone") and reads School/Zone data through `school.api` queries.

**Rationale**: Principle VII demands `organization` read Zone and School data through `school`'s
public API, so `school` cannot import `organization`. Events cannot veto. A guard SPI is the
smallest mechanism that preserves one-directional dependencies and still lets the rule run
synchronously inside the originating transaction (atomic, no partial effect).

**Alternatives considered**: merging `school` and `organization` into one module (rejected:
contradicts Principle VII's module list); a mediator/orchestrator module (rejected: a fourth module
for a handful of checks); making School-Manager assignment live in `school` (rejected: Manager
records and assignments are explicitly `organization`'s).

## 3. Scope: one query object, derived on every request

**Decision**: `organization.api.ScopeQueries.scopeOf(userId, roles)` returns a `ScopeView`
(`orgWide` boolean, `zoneIds`, `schoolIds`), computed per request from the current assignment
tables: Admin/Director -> `orgWide`; Manager -> Zones of their Manager record plus Schools assigned
to them; Teacher/System -> empty. A user holding several roles gets the union (`orgWide` wins).
`teacher.api.TeacherScopeQueries.teacherIdsInScope(userId, roles)` layers on top: Teachers whose
placement *today* is in a scope School (org-wide: all; Teacher role: only their own record).
Controllers pass the `ScopeView` into repository queries as a predicate; they never filter in
memory.

**Rationale**: FR-020/FR-021/FR-022 and Principle III. Deriving per request (no cache, no snapshot
in the JWT) means assignment changes apply on the next request, matching spec 004's approach for
deactivation. At this scale the cost is a few indexed lookups.

**Out-of-scope behavior**: detail endpoints load the record *through the scoped query*; empty ->
404, so a guessed id cannot be told apart from a missing one.

**Alternatives considered**: putting scope in the JWT (rejected: stale for up to 15 minutes,
contradicts FR-022); a database row-level-security policy (rejected: more machinery than 100 users
justify, and scope depends on multi-table joins the app already owns).

## 4. A School's Zone is derived from its Place, never stored independently

**Decision**: `school` table stores `place_id` only. `zone_id` is read through the Place. The
School list DTO includes the Place and its Zone. Moving a School is "change place"; the
`SchoolChangeGuard` runs when the target Place's Zone differs.

**Rationale**: spec clarification (Session 2026-10-03, Q1): the Zone is the Place's Zone. Storing
only `place_id` makes Zone/Place disagreement unrepresentable. A Place may not change Zone while it
has Schools (FR-005a) so existing Schools can never be silently moved to a different Zone by a
Place edit.

## 5. Assignments are dated, append-only rows; "current" is a query, not a flag

**Decision**: `zone_manager_assignment` and `school_manager_assignment` rows carry `starts_on` and
nullable `ends_on`. Assigning inserts a row; unassigning sets `ends_on`; reassigning a School ends
the old row and inserts the new one in the same transaction. A partial unique index enforces at most
one current School-Manager row per School. "Current" = `ends_on IS NULL`.

**Rationale**: FR-010 (history retained, nothing overwritten) and later modules needing "who was
this School's Manager on date X".

**Concurrency**: every operation that changes a Manager's Zones or Schools, and every guard that
reads them, first takes a pessimistic write lock on the `manager` row (or rows, in id order) inside
the same transaction, as the last-admin guard does in spec 004. This makes "remove a Manager from a
Zone" and "assign a School in that Zone to the Manager" mutually exclusive.

## 6. Teacher placements: dated rows, no scheduler

**Decision**: `teacher_placement` rows (`teacher_id`, `school_id`, `starts_on`, `ends_on`,
`status` in `ACTIVE`, `CANCELLED`, `CORRECTED`). The placement in effect on date D is the `ACTIVE`
row with `starts_on <= D` and (`ends_on IS NULL` or `D <= ends_on`). A future-dated move inserts
the new row with a future `starts_on` and sets the current row's `ends_on` to the day before; no
background job is needed because "current" is evaluated against today on every read, so the move
"takes effect automatically" simply by the date passing (spec Clarifications Q5). At most one
future row per Teacher exists; cancelling it marks it `CANCELLED` and clears the current row's
`ends_on`. A move dated exactly the current row's `starts_on` marks the old row `CORRECTED` (kept for
history) and inserts the replacement. A date earlier than the current `starts_on` is refused.
Database constraints (an exclusion constraint on a date range per Teacher over `ACTIVE` rows)
back the no-overlap rule. The constraint needs the `btree_gist` extension: `V13` runs
`CREATE EXTENSION IF NOT EXISTS btree_gist`; if the production database user cannot create
extensions, the deployment guide lists it as a prerequisite run by a privileged user. The service-level
overlap check is the primary rule and is always tested; the constraint is a backstop.

**Alternatives considered**: a scheduled job applying future moves (rejected: adds failure modes
and a second source of truth); storing "current school" on the Teacher row (rejected: loses history
and as-of queries).

## 7. Teacher status as a small explicit state machine

**Decision**: allowed transitions are `IN_TRAINING -> ACTIVE`, `ACTIVE <-> ON_LEAVE`, and any ->
`EXITED` (final). Implemented as a static transition table in the Teacher aggregate; every
transition records an effective date and publishes `EntityChanged(field="status")`. Exit also ends
the current placement on the exit date, cancels any scheduled placement, and clears
`teacher.user_id` (releasing the account link, spec FR-016).

**Rationale**: spec Clarifications Q3 (exit is final). One table is the single place to unit test.

## 8. Teacher-user link lives on the Teacher row

**Decision**: `teacher.user_id` (nullable, unique) links to the account; `GET /api/v1/teachers/me`
resolves the Teacher by the caller's user id. The existing `app_user.linked_teacher_id` column from
spec 001 is left untouched and unused by this spec.

**Rationale**: it keeps `identity` free of any `teacher` knowledge (the column would otherwise need
`identity` to hold a Teacher id it cannot validate) and keeps the unique-link rule in the module
that owns Teachers. Linking validates the user holds the Teacher role through `identity`'s existing
public `user` interface.

## 9. Salary: separate permission, endpoints and DTOs

**Decision**: `TEACHER_SALARY` is its own permission module with `VIEW` and `CREATE`. Salary lives
in its own table and endpoints (`/api/v1/teachers/{id}/salary`); the Teacher profile/list DTOs have
no salary field at all, so no other code path can leak it. "Current" and "as of" are queries over
`teacher_salary_history ordered by effective_on`. Corrections append a new row. A lookup before the
first effective date returns "no salary recorded".

**Rationale**: FR-019 and Principle X: separating by endpoint and DTO makes the privacy guarantee
structural rather than a convention a future query could forget.

## 10. Manager School edit is field-limited in the service, not just the UI

**Decision**: the School update endpoint accepts a full profile body but `SchoolService.update`
takes the caller's capability: a Manager caller may only change `contactPerson`, `contactPhone` and
`address`; any other changed field returns 403 with a plain message. Admin/Director may change all
fields. Place and Manager changes use their own endpoints (Admin/Director only).

**Rationale**: spec Clarifications Q2 and FR-006; the check must live server-side (Principle X).

## 11. Optimistic locking for concurrent edits

**Decision**: `Zone`, `School`, `Manager`, `Teacher` carry a JPA `@Version`; update request bodies
include the `version` the client loaded; a mismatch returns 409 "This record was changed by someone
else. Reload and try again." Append-only tables need no version.

**Rationale**: FR-026; low contention, so optimistic is the right fit.

## 12. Bulk import: one transaction, per-row validation in memory

**Decision**: `POST /api/v1/places/bulk-import` accepts `{zoneId, rows:[{name, pinCode}]}`, rejects
empty or over 5,000 rows up front, loads the Zone's existing `(lower(name), pinCode)` set once,
validates each row (name present, PIN code six digits), classifies each as `ADDED`,
`ALREADY_EXISTS` (also for repeats earlier in the same file) or `REJECTED(reason)`, and saves all
`ADDED` rows in a single batch. The response lists every row's outcome by position. The frontend
parses pasted `name,pinCode` lines into rows; file upload is out of scope for v1.

**Rationale**: spec Clarifications Q4 and SC-004 (1,000 rows in under 30 s is trivially met by one
batch insert).

## 13. Pagination, search and DTO conventions

**Decision**: reuse the `page`/`size` (default 25, max 100) and `{content,page,size,totalElements}`
envelope from specs 003/004. Each list endpoint takes an optional `query` (case-insensitive match on
name, plus phone/PIN where relevant) and its own filters (Zone, School, status). Errors use
`{"reason": "..."}`.

## 14. Frontend: MASTER DATA section, scope-aware dashboard, Teacher block on My Profile

**Decision**: four guarded routes (`/master-data/zones`, `/schools`, `/managers`, `/teachers`) under
the existing `RouteGuard`; action buttons rendered only when the access model grants the matching
action (the FR-012 pattern from spec 004). The Manager dashboard calls `GET /api/v1/me/scope`
(Zone and School counts) and `GET /api/v1/teachers?size=1` (Teacher total, already scoped).
`ProfilePage` shows a Teacher block when `GET /api/v1/teachers/me` returns a record for a Teacher.
Dates are formatted DD/MM/YYYY and amounts with Indian digit grouping through one shared formatter.

## 15. Seed and demo data

**Decision**: extend `DevDataSeeder` (dev-only, property-gated) with one Zone, two Places, two
Schools, a Manager record for Manoj Manager with assignments and two Teachers, so Postman and the
quickstart have data. No effect in tests or production.

## 16. Audit screens must not leak master data to System (or salary to anyone without access)

**Decision**: `audit`'s Change History and unified Audit Logs queries apply a visibility filter:
each `entity_type` maps to the `PermissionModule` whose `VIEW` the caller must hold (table in
data-model.md). The filter reuses `identity`'s existing `PermissionGuard`/matrix (already a public
named interface), so it follows runtime matrix edits. Existing entity types (`PERMISSION_MATRIX`)
are unaffected. The CSV export applies the same filter.

**Rationale**: spec 003 made the audit screens Admin/System. Without this, the System role - which
Constitution Principle II bars from teacher and school data - could read every master-data change,
including salary amounts, through Change History. Putting the rule in `audit` (not in each
publisher) keeps one enforcement point and keeps publishers ignorant of who reads.

**Alternatives considered**: not publishing salary amounts to audit (rejected: Principle I requires
prior and new values); a second audit store for restricted data (rejected: Principle VII says one
store).

## 17. Two more SPIs: scope and read-model enrichment for `school` views

**Decision**: besides the three veto guards (section 2), `school.api` defines
`SchoolScopeProvider` (returns the School ids a caller may see) and two read-model enrichers,
`SchoolViewEnricher` and `ZoneViewEnricher` (return extra attributes per id). `organization`
implements the scope provider and contributes `manager` (School views) and `managerCount` (Zone
views); `teacher` contributes `teacherCount` (School views). `SchoolService` merges the extras into
the JSON it returns.

**Rationale**: School and Zone lists must be Manager-scoped and must show each School's Manager and
Teacher count, but those facts belong to `organization` and `teacher`, which depend on `school`
(Principle VII). The School endpoints therefore stay in `school` and ask the dependents for the
parts they own, through interfaces `school` defines, rather than `school` importing them. With no
scope provider present, a non-Admin/Director caller sees nothing (fail closed), which is why
Managers see no Schools until US3 lands.

`organization.api` likewise defines `ManagerQueries` (`managerOfSchool(schoolId)`,
`managerSummary(managerId)`) and `ManagerViewEnricher` (extra attributes per Manager id). `teacher`
implements the enricher to add `teacherCount` to Manager views and uses `ManagerQueries` to show each
Teacher's accountable Manager, so `organization` never imports `teacher`.

**Alternatives considered**: moving the School HTTP layer into `organization` (rejected: School
CRUD is `school`'s responsibility, and `organization` would then own endpoints for data it does not
own); a frontend that joins School, Manager and Teacher lists (rejected: pushes scoping and
consistency into the client).

## 18. The access model reports a data scope for the new modules

**Decision**: `AccessModelService.dataScope` also reports `ZONES` and `MANAGERS` -> `ORG_WIDE` for
Admin/Director, and `SCHOOLS` and `TEACHERS` -> `ORG_WIDE` for Admin/Director and `ASSIGNED` for
Manager; nothing for Teacher or System. A multi-role user gets the widest scope, as Dashboard does
today.

**Rationale**: it keeps the frontend and later specs consistent with Constitution Principle III
without each recomputing scope from roles, at the cost of a few lines in spec 002's service.
