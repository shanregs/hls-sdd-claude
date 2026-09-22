# Research: Zone-Based Manager Scoping (reworked)

No `[NEEDS CLARIFICATION]` markers remain. This phase resolves the technical *how* for the narrowed, corrected scope (spec.md's Rework note).

## 1. `organization` reads Zone/School-Zone data through `school.api.ZoneQueries` — never owns it

**Finding**: `school.api.ZoneQueries` (specs/007-school-zone, already implemented and shipped) already provides exactly what this feature needs to *read*: `findById(zoneId)` (does a Zone exist), `currentZoneForSchool(schoolId)` (a School's current Zone, or `UNASSIGNED`), and `currentSchoolsForZone(zoneId)` (every School currently in a Zone).

**Decision**: `organization` takes a real Spring bean dependency on `school.api.ZoneQueries` (the interface; `school.internal.ZoneService` is the implementing bean) — the same read-through-a-public-API pattern `schoolbilling` is planned to use for Teacher/School master data, and the same shape `identity` already uses for `organization.api.AccountabilityQueries`. `organization` creates no Zone or School-Zone table of its own.

**Rationale**: Constitution Principle V (as amended, 1.7.0) is explicit: `organization` reads Zone/School-Zone data through `school`'s public API, never by owning it. This is also the entire point of the rework — the original (unimplemented) plan would have duplicated data `school` now already owns for real.

**Alternatives considered**:
- *Cache/mirror Zone and School-Zone data inside `organization` for query convenience*: rejected — two sources of truth that could drift, for a savings (avoiding one cross-module read) that doesn't matter at this project's scale (<100 users, single EC2 instance).

## 2. A Zone can have more than one currently-covering Manager — the uniqueness constraint shape (unchanged from the original plan)

**Decision**: `organization_zone_manager_assignment` gets a partial unique index on **`(zone_id, manager_id)` WHERE `effective_to IS NULL`** — not on `zone_id` alone, the way `SchoolAssignment`'s `ux_school_assignment_current` constrains `school_id` alone.

**Rationale**: Spec.md User Story 1, acceptance scenario 2 explicitly requires two Managers to be simultaneously, currently assigned to the same Zone. The right invariant is "at most one current row for this *(Zone, Manager)* pair" — this still prevents the same Manager being double-assigned to the same Zone by a race, while allowing a second, different Manager freely. (Carried over unchanged from this spec's original, pre-rework research — this part of the design was never wrong, only where Zone itself lived was.)

## 3. Enforcing "Manager must currently cover the School's Zone" (FR-003/FR-004)

**Decision**: `AccountabilityService.assignSchoolManager(...)` gains one new check, performed first, inside the same transaction, before the existing assign/reassign/conflict logic runs: call `zoneQueries.currentZoneForSchool(schoolId)` — if `UNASSIGNED`, reject (FR-004's "no Zone" case); otherwise, check whether a currently-open `ZoneManagerAssignment` row exists (in `organization`'s own table) for `(that zoneId, the chosen managerId)` — if none, reject (FR-003/FR-004's "not covering" case). Only then does the existing no-op/conflict/create logic run, unchanged.

**Rationale**: This combines a cross-module read (School's current Zone, via `school.api`) with an in-module check (does a covering-Manager row exist), exactly mirroring how `identity.internal.ManagerScopeGuard` already combines a JWT-derived caller id with a cross-module call to `organization.api.AccountabilityQueries`. Checking before the existing no-op/conflict logic keeps `assignSchoolManager` atomic and keeps the new rejection reason unambiguous (a Zone-coverage failure is never confused with an existing-assignment conflict).

**Alternatives considered**:
- *Check Zone coverage only when there's no existing conflict (i.e., after the conflict check)*: rejected — would let a request with an already-invalid Manager choice pass the conflict check first and fail later for a different reason on retry with the same bad input, which is more confusing than failing fast on the actually-wrong part of the request.

## 4. A new, distinct exception type, not reusing `AssignmentConflictException`

**Decision**: `SchoolManagerNotInZoneException` is a new, separate public type in `organization.api`, mapped to HTTP `422 Unprocessable Entity` by `OrganizationController` — distinct from `AssignmentConflictException`'s `409 Conflict`.

**Rationale**: The two failures mean different things to a caller. `409` (existing) means "you raced someone else — refresh and retry, your request might succeed later with no changes on your part." `422` (new) means "this request can never succeed as written — the Manager you chose doesn't cover this School's Zone, no amount of retrying fixes that without a different choice or fixing the Zone setup first." Collapsing them into one error would make the frontend's retry-vs-fix-the-input logic ambiguous. (Carried over unchanged from this spec's original research — still correct.)

## 5. Zone-Manager assignment stays `organization.internal` — no new `organization.api` surface beyond the one exception

**Finding**: Nothing outside `organization` needs to call Zone-Manager operations directly for this spec — Zone coverage visibility (FR-005) is Director/Admin only, surfaced through `OrganizationController`'s REST endpoints, and no other module's scoping logic depends on Zone-Manager coverage directly (Manager/Teacher scoping still goes through `AccountabilityQueries`/`Commands`, whose public shape is unchanged except for the one new exception type).

**Decision**: `ZoneManagerAssignment`, `ZoneManagerAssignmentRepository`, and the Zone-coverage logic all live in `organization.internal` (added to the existing `AccountabilityService`, not a new service class — research.md §7). The only new `organization.api` addition is `SchoolManagerNotInZoneException`.

**Rationale**: Consistent with this project's "build what's needed" discipline (specs/008 research.md §4 already established the same reasoning for `school`) — `organization.api`'s surface should reflect what other modules actually depend on, not what might theoretically be useful later.

## 6. Flyway migration number: checked at implementation time

**Finding**: The highest migration currently on `main` is `V7` (Identity=V1, Organization=V2, Audit=V3, School/Zone=V4, School/Places=V5, Teacher=V6, Teacher-Salary-History=V7).

**Decision**: This feature's migration is `V8__create_zone_manager_assignment_table.sql`.

**Rationale**: Same discipline this codebase has followed every time — assigned relative to what's actually shipped on `main` at implementation time, not predicted in advance.

## 7. `ZoneManagerAssignment` logic joins the existing `AccountabilityService`, not a new service class

**Decision**: `AccountabilityService` gains the new Zone-Manager assignment methods (assign/remove/list-for-zone) alongside its existing School/Teacher accountability methods, implementing a small addition to `AccountabilityCommands`/`AccountabilityQueries` rather than a separate `ZoneManagerService`.

**Rationale**: `AccountabilityService`'s own class doc already states the reasoning this extends: "every operation shares the same 'current row has no effective_to' model... splitting query/command implementations would just duplicate that." Zone-Manager assignment is the same append-only, current-row-has-no-`effective_to` shape as `SchoolAssignment`/`TeacherAssignment` — a third instance of the same pattern, not a new one.

**Alternatives considered**:
- *A separate `ZoneManagerAssignmentService`*: rejected — would duplicate the `effective_to`/conflict-detection discipline `AccountabilityService` already implements twice, for no isolation benefit since both live in the same module and the same class already handles two structurally identical concerns.

## 8. `OrganizationModuleTest` bootstrap mode: `ALL_DEPENDENCIES`, applying the lesson from specs/005-teacher directly

**Finding**: `organization` will now have a real Spring bean dependency on `school.api.ZoneQueries` (research.md §1). specs/005-teacher's implementation discovered, the hard way, that `DIRECT_DEPENDENCIES` only bootstraps a module's *immediate* dependencies — if one of those immediate dependencies itself has a further bean dependency, `DIRECT_DEPENDENCIES` doesn't reach it and the context fails to start. `organization`'s only new dependency here (`school`) has no dependencies of its own (`school` depends on nothing — specs/007/008/010 research.md, repeatedly), so `DIRECT_DEPENDENCIES` would actually be sufficient in this specific case.

**Decision**: Use `BootstrapMode.ALL_DEPENDENCIES` anyway, not `DIRECT_DEPENDENCIES`.

**Rationale**: Applying the lesson proactively rather than re-deriving it: `ALL_DEPENDENCIES` is strictly safer (bootstraps the full transitive graph) and costs nothing extra at this project's scale — there's no reason to reason carefully about whether `DIRECT_DEPENDENCIES` happens to be sufficient today when a future change (e.g., `school` itself gaining a dependency later) could silently break it again. This is the second time this exact category of Spring Modulith gap has been found in this codebase (specs/005-teacher research.md §6 was the first); defaulting to `ALL_DEPENDENCIES` whenever a module gains any real cross-module bean dependency avoids a third rediscovery.

## 9. Frontend: extend `AssignmentsPage`, don't add a new page

**Decision**: Add Zone-Manager coverage management (assign/remove a Manager's Zone coverage, view a Zone's coverage) as a new section within the existing `AssignmentsPage`, and surface the new `422` rejection reason in the existing school-assignment form's error handling — rather than a separate page. (Carried over unchanged from this spec's original research — still correct; `ZonesPage` remains `school`'s own page for Zone/Place CRUD, which this feature doesn't touch.)

**Rationale**: `AssignmentsPage` is already the Director/Admin screen for exactly this kind of organizational-structure management (assign/reassign, portfolio, unassigned list); Zone-Manager coverage is one more piece of that same structure. `ZonesPage` (specs/007/008) is `school`'s own page for Zone/Place master data — this feature adds no Zone/Place-creation UI, so it has no reason to touch that page either.

## 10. Discovered during implementation: the same bootstrap-mode gap ripples one level further, into `identity`

**Finding**: `identity.internal.ManagerScopeGuard` already has a real Spring bean dependency on `organization.api.AccountabilityQueries` (specs/002/003), so `IdentityModuleTest` already used `BootstrapMode.DIRECT_DEPENDENCIES` (not `STANDALONE`) before this feature. Once `organization` itself gained a new dependency on `school.api.ZoneQueries` (research.md §1/§8), `organization` became, from `identity`'s point of view, a *transitive* hop to `school` — exactly the shape `DIRECT_DEPENDENCIES` doesn't reach. Running only `organization`'s own test files green (research.md §8's fix) was not enough: the full backend suite caught `IdentityModuleTest` failing to boot (`required a bean of type 'com.hls.school.api.ZoneQueries' that could not be found`) — a real regression in already-shipped, previously-green code that a module-scoped test run doesn't surface.

**Decision**: `IdentityModuleTest` also switches from `DIRECT_DEPENDENCIES` to `BootstrapMode.ALL_DEPENDENCIES`.

**Rationale**: Confirms research.md §8's own point empirically, one hop further than predicted: a bootstrap-mode fix made for the module gaining the new direct dependency (`organization`) does not automatically cover every module that transitively depends on it (`identity` → `organization` → `school`). The practical lesson: after any module gains a new real cross-module bean dependency, the full backend suite (not just the changed module's own tests) must be run before considering the change complete — a module-scoped green run can hide a breakage one hop upstream.

## 11. Discovered during implementation: an existing `identity` test seeds `assignSchoolManager` without a Zone

**Finding**: `IdentityIntegrationTest.managerScopeGuard_allowsAssignedManagerAndDeniesUnassignedOne` (specs/002/003) calls `organization.api.AccountabilityCommands.assignSchoolManager(...)` directly, with a School that has no Zone at all — this was valid before this feature (no precondition existed) and became a real failure (`SchoolManagerNotInZoneException`) the moment FR-003/FR-004 landed, the same category of already-shipped-test breakage plan.md's Summary/Complexity Tracking already flagged for `organization`'s *own* test files, just in a different module's test file that wasn't anticipated in the original file list.

**Decision**: The test is updated (not left broken) to seed a real Zone via `school.api.ZoneCommands` (also autowired into the test), assign the School to it, and assign the Manager to cover it, before calling `assignSchoolManager` — mirroring the same fix pattern applied to `AccountabilityServiceTest`/`OrganizationIntegrationTest`.

**Rationale**: Same Constitution Principle VII discipline plan.md already commits to for `organization`'s own tests — a red suite from a known, already-understood cause is not an acceptable interim state, regardless of which module's test file it happens to live in.
