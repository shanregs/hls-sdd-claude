# Research: Organization — Manager/School/Teacher Accountability

No `[NEEDS CLARIFICATION]` markers remain in the Technical Context (the `/speckit-clarify` pass resolved the two decisions that mattered — who can write assignments, and School/Teacher independence). This phase instead resolves the technical *how* for the constraints Technical Context flagged.

## 1. Enforcing "at most one active assignment" (FR-003) under concurrent writes

**Decision**: Represent the current assignment as the row with `effective_to IS NULL`, and enforce "at most one such row per School/Teacher" with a PostgreSQL **partial unique index** (`UNIQUE (school_id) WHERE effective_to IS NULL`, and the equivalent for `teacher_id`), created in the Flyway migration — not only checked in application code.

**Rationale**: A partial unique index makes the invariant impossible to violate even under concurrent requests, without needing a separate lock table or a serializable transaction around every write. It's a standard PostgreSQL pattern for "at most one current/open row" and keeps the guarantee at the layer that actually has to enforce it.

**Alternatives considered**:
- *Application-level check-then-insert only*: rejected — two concurrent requests can both pass the check before either commits, exactly the race FR-011 says must not silently happen.
- *A separate "current assignment" pointer table, updated alongside history rows*: rejected — introduces a second source of truth that can desync from the history rows if a write updates one but not the other; the partial index keeps history and "current" as one source.

## 2. Conflict detection on reassignment (FR-011)

**Decision**: A reassignment request names the specific assignment row it intends to end (its id, obtained from the caller's last read). The service ends that row inside a transaction guarded by a `WHERE id = ? AND effective_to IS NULL` update; if zero rows are affected, someone else already ended it first, and the service raises a conflict rather than proceeding to open a new row.

**Rationale**: This reuses the same state (`effective_to IS NULL`) already being enforced by the partial unique index from decision 1, so there's no second locking mechanism to keep consistent with the first. No `@Version` column needed.

**Alternatives considered**:
- *JPA `@Version` optimistic-locking column in addition*: rejected as redundant — the state-transition check already detects the only conflict that matters here (someone else ended the same row first).
- *Pessimistic row locking (`SELECT ... FOR UPDATE`)*: rejected — unnecessary contention for HLS's actual concurrent-write volume (~8 managers, occasional reassignment), and it would hold a lock across a round trip for no benefit over the conditional update.

## 3. FR-012's "unknown identifier" case, given Teacher/School Master Data don't exist yet

**Finding**: FR-012 requires distinguishing "identifier not known to the system" from "identifier known but unassigned." Today, neither Teacher nor School has a real master table to check existence against (Teacher Master Data and School Master Data are sequenced after this module in the tracker).

**Decision**: For this implementation, School/Teacher identifiers are accepted as opaque values (UUID) with **no existence validation** — every syntactically valid identifier that has never been assigned resolves to "known but unassigned" rather than "unknown," which is a deliberate, documented narrowing of FR-012 until real master tables exist to validate against. A follow-up task is recorded (not in this module's `tasks.md`, but as a forward note for the Teacher Master Data / School Master Data plans) to add a foreign-key constraint and wire the "unknown identifier" branch for real once those tables land.

**Rationale**: Blocking this module on Teacher/School Master Data existing first would invert the dependency order the tracker already corrected once (2026-09-21 amendment). Documenting the narrowing here keeps the spec's intent traceable without silently pretending FR-012 is fully implemented today.

**Alternatives considered**:
- *Defer FR-012 entirely out of this module's `tasks.md`*: rejected — the "unassigned vs. unknown" distinction in the response shape should exist from day one (SC-002), even if the "unknown" branch can't yet be truly exercised; retrofitting the response shape later would be a breaking API change for Reporting and other future callers.

## 4. Introducing Spring Modulith for the first time

**Decision**: Add `spring-modulith-starter-core` (main) and `spring-modulith-starter-test` (test), annotate `HlsApplication` with `@Modulith`, and add `OrganizationModuleTest` using `@ApplicationModuleTest` to prove the `organization` module boots in isolation, per the architecture doc §11 pattern. `ArchitectureTest` keeps its existing hand-written ArchUnit rule (extended with an `organization`-specific internal-access rule) alongside Modulith's own `ApplicationModules.verify()`, since the two check different things: Modulith verifies the *whole application's* module graph, while the hand-written rule checks the *specific* named-package list the constitution enumerates.

**Rationale**: This is the first module for which "boundaries" means anything (the warm-up `status` package had zero peers to violate boundaries with); introducing Modulith now, on the second module, establishes the pattern every subsequent module reuses rather than retrofitting it in later.

**Alternatives considered**:
- *ArchUnit alone, no Spring Modulith*: rejected — the amended constitution (v1.5.0) explicitly names "ArchUnit and Spring Modulith verification" together in Principle V; deferring Modulith further would mean re-deriving this decision on a later module under more schema/module pressure.

## 5. Representing the caller's identity ahead of Identity & Access (002) — SUPERSEDED, see §6

**Original decision (2026-09-21)**: `CurrentUserResolver` is a one-method interface (`resolve(HttpServletRequest) -> CurrentUser(userId, role)`) that `OrganizationController` depends on but does not implement. A test/dev-only implementation reads a role from a request header for now; it is replaced by Identity & Access's real JWT-parsing implementation with no change to `OrganizationController` or the `api` package, since both only ever depend on the `CurrentUser` value type.

**Original rationale**: Keeps the role check (FR-001/002/004: DIRECTOR/ADMIN only) real and testable now, while isolating the one piece that's genuinely not buildable yet (real authentication) behind an interface with exactly one swap point.

**Why this is superseded**: Identity & Access (002) is now implemented, so "ahead of 002" no longer applies. §6 replaces this decision entirely — kept here (not deleted) so the reasoning that got this module started before 002 existed stays traceable.

## 6. Representing the caller's identity now that Identity & Access (002) exists (2026-09-22)

**Decision**: Drop `CurrentUserResolver`/`CurrentUser` entirely. `OrganizationController` reads `@AuthenticationPrincipal Jwt jwt` directly — the same pattern `identity.internal.AuthController` already uses for its own protected endpoints (`GET /sessions`, `DELETE /sessions/{id}`, `POST /logout`, `POST /users/{id}/unlock`). Role/subject extraction (`jwt.getSubject()` for userId, `jwt.getClaimAsStringList("roles")` for roles) is ~3 lines, duplicated locally in `OrganizationController` rather than shared, since it's generic Spring Security claim-reading, not Identity-specific logic.

**Rationale**: Identity's `SecurityConfig` already registers a single, app-wide `SecurityFilterChain` and `JwtDecoder` — every endpoint in the application (not just Identity's own) is already behind real bearer-token auth once Identity's beans are in the context. There is nothing left for a port/adapter to abstract: the "not buildable yet" piece §5 was isolating is now just... built. Introducing an interface here would be indirection with no swap point left to justify it.

**Alternatives considered**:
- *Keep the `CurrentUserResolver` interface, just point its real implementation at `identity.api`*: rejected — this would make `organization` depend on `identity` (to obtain the resolver implementation), while `identity`'s own `ManagerScopeGuard` already depends on `organization.api.AccountabilityQueries`. That's a module dependency cycle, which Spring Modulith's `ApplicationModules.verify()` is designed to reject and which Constitution Principle V's boundary discipline doesn't anticipate needing to resolve. Reading `Jwt` directly avoids the cycle entirely: both modules depend on Spring Security's generic type, neither depends on the other for authentication.
- *Have Identity expose a public `identity.api.dto.AuthenticatedUser` + extractor for other modules to reuse*: rejected as unnecessary indirection for 3 lines of claim-reading that every future module needing "who is calling" will want anyway — duplicating it is cheaper than a cross-module dependency for something this small, and avoids the cycle risk above by construction.

## 7. Locking `AccountabilityQueries`'s method signatures to Identity's already-written consumer

**Finding**: Identity's `ManagerScopeGuard` (spec 002, implemented 2026-09-21) was written against a local stand-in — `identity.internal.OrganizationAccountabilityStandIn` — deliberately shaped to match this module's *published* `contracts/organization-api.yaml`/data-model.md `AccountabilityAnswer`. That stand-in has two methods, `currentManagerForSchool(UUID schoolId)` and `currentManagerForTeacher(UUID teacherId)`, each returning `Answer(State state, UUID managerId)` with `State` = `CURRENT_MANAGER`/`UNASSIGNED`/`UNKNOWN_IDENTIFIER`.

**Decision**: `organization.api.AccountabilityQueries` and `organization.api.dto.AccountabilityAnswer` must use exactly this shape — same method names, same three-state enum, same field names. This was already true by construction (the stand-in was written to match the contract), so this is confirmation, not a change: no rework needed on Identity's side when it swaps its stand-in import for the real one.

**Rationale**: Identity already has real, passing tests (`ManagerScopeGuardTest`, 5 cases) written against this exact shape. Changing it now would mean editing tested code in another module for no functional reason.

**Follow-up for Identity (tracked in specs/002-identity-access, not this module's tasks.md)**: once this module's `AccountabilityQueries` exists, specs/002's `ManagerScopeGuard` should swap its import from `OrganizationAccountabilityStandIn` to `organization.api.AccountabilityQueries`, and delete `OrganizationAccountabilityStandIn`/`InMemoryOrganizationStandIn`. Separately, specs/002's task T031 ("implement `identity.api.CurrentUserResolver` satisfying Organization's stub") is now obsolete per §6 above — there is no `CurrentUserResolver` for it to implement — and should be marked obsolete rather than completed when 002's tasks.md is next touched.

## 8. Frontend: `AssignmentsPage` is now in scope

**Finding**: The original plan (2026-09-21) deferred all frontend work because "a login-gated screen cannot be meaningfully built before there is a login." Identity & Access shipped a real login (`LoginPage`, `AuthContext`, `authClient`) on 2026-09-21/22.

**Decision**: Add a minimal `frontend/src/pages/AssignmentsPage/` for Director/Admin to assign/reassign Schools and Teachers, view a Manager's portfolio, and see the unassigned-items list (User Stories 1-3 of spec.md). It reuses `authClient`'s existing Bearer-token pattern (`Authorization: Bearer ${accessToken}` from `AuthContext`) — no changes needed to Identity's frontend code, only a new page that calls Organization's endpoints the same way `SessionsPage` calls Identity's.

**Rationale**: No remaining reason to defer; building it now means Director/Admin can actually use this module through the web app once implemented, rather than only through direct API calls.

**Alternatives considered**:
- *Keep deferring frontend, backend-only again*: rejected — the deferral's stated reason no longer holds, and re-deferring without a new reason would just be inertia.
