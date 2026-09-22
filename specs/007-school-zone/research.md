# Research: School Master Data — Zones

No `[NEEDS CLARIFICATION]` markers remain. This module mirrors `organization`'s already-proven patterns closely enough that most decisions here are "reuse X," not new design.

## 1. School-Zone assignment reuses `SchoolAssignment`'s exact conflict/history pattern

**Decision**: `SchoolZoneAssignment` has the same shape and the same `endIfStillCurrent` conditional-`UPDATE`-returns-affected-row-count technique as `organization.internal.SchoolAssignment`/`SchoolAssignmentRepository` (specs/003 research.md §1-2): a partial unique index on `(school_id) WHERE effective_to IS NULL` enforces "at most one current Zone per School" at the database level, and reassignment names the row it intends to end, raising `ZoneAssignmentConflictException` if that row was already ended by someone else (FR-005, SC-003).

**Rationale**: This exact problem (an opaque-identifier "current row" with reassignment and race-safe conflict detection) was already solved once, in the same codebase, for the same underlying identifier (`schoolId`). Reusing it is lower-risk than any alternative and keeps the two nearly-identical assignment tables (`organization_school_assignment` and this module's `school_zone_assignment`) behaviorally consistent for anyone reading the code later.

**Alternatives considered**:
- *A `Zone` foreign-key column directly on a future `School` entity, updated in place*: rejected — there is no `School` entity yet (School Master Data's other fields are still tracker row 7, out of scope here per spec.md Assumptions), and an in-place-update column would lose FR-004's history requirement anyway; the append-only assignment table is the only shape that satisfies both constraints at once.

## 2. `Zone` itself: narrow repository, no delete — reusing the now-standard pattern

**Decision**: `ZoneRepository` extends bare `Repository<Zone, UUID>`, declaring only `save`/`findById`/`findAll` — no delete method, mirroring `AuditEntryRepository`'s (specs/004) and `AuthAuditEntryRepository`'s (specs/002) established pattern.

**Rationale**: Same reasoning each prior use of this pattern already established: the guarantee (FR-009, never deleted) becomes a compile-time fact instead of a runtime check that could have a bug.

## 3. `school` is the fourth bounded context — module isolation test can stay `STANDALONE`, for now

**Decision**: `SchoolModuleTest` uses the default `BootstrapMode.STANDALONE`.

**Rationale**: `school` depends on nothing from any other module, and — as of *this* feature's implementation — nothing depends on `school` either, since specs/006-zone-scoping's rework (which will add the first real dependency, `organization` → `school.api`) is a separate, not-yet-implemented feature. This mirrors exactly the situation Audit's research.md §6 was in when it was first built. Flag for whoever implements specs/006-zone-scoping next: that feature's own `OrganizationModuleTest` will likely need `DIRECT_DEPENDENCIES` once it adds the real dependency on `school.api` — the same switch Organization's own tasks.md T031 had to make for Identity, and the same thing Teacher's plan (specs/005) already anticipated doing from the start.

## 4. Public API shape: locked to what specs/006-zone-scoping's (unimplemented) plan already expects

**Finding**: specs/006-zone-scoping's original research.md (before this rework) already sketched the shape Organization would need to check Zone membership: "does a Manager's Zone match the School's Zone." That maps directly to `ZoneQueries.currentZoneForSchool(schoolId)` here.

**Decision**: `ZoneQueries`/`ZoneCommands`/`SchoolZoneAnswer` are designed now, in this module, to be the exact shape specs/006-zone-scoping's rework will consume — the same "lock the shape to the already-known consumer" discipline specs/003 used for `AccountabilityQueries` when Identity's stand-in was already written against it (specs/003 research.md §7).

**Rationale**: Avoids a second round of rework when specs/006-zone-scoping is next planned — the consuming shape is decided once, here, rather than guessed at twice.

## 5. Frontend: a new `ZonesPage`, not folded into `AssignmentsPage`

**Decision**: A new, separate `ZonesPage` (Director/Admin: create Zone, assign School to Zone, view a Zone's Schools) rather than extending `organization`'s existing `AssignmentsPage`.

**Rationale**: `AssignmentsPage` is Organization's own screen for Manager/School/Teacher accountability; Zone/School master data is a different module's concern now (this rework's whole point). specs/006-zone-scoping's own (separately reworked) plan may still extend `AssignmentsPage` for the Zone-*Manager* side of things, but School-Zone assignment itself belongs with the module that owns it.

**Alternatives considered**:
- *Extend `AssignmentsPage` anyway, since it's the existing "assign things" screen*: rejected — this is exactly the ownership conflation this whole correction (Amendment 1.7.0) exists to fix; putting School-Zone UI inside Organization's page would recreate the same boundary blur at the frontend layer.
