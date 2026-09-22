# Research: Zone-Based Manager Scoping

No `[NEEDS CLARIFICATION]` markers remain — the two open design questions (spec approach, and how to handle the Teacher-Manager gap) were resolved in conversation before spec.md was written. This phase resolves the technical *how*.

## 1. A Zone can have more than one current Manager — the uniqueness constraint changes shape

**Decision**: `organization_zone_manager_assignment` gets a partial unique index on **`(zone_id, manager_id)` WHERE `effective_to IS NULL`** — not on `zone_id` alone, the way `SchoolAssignment`'s `ux_school_assignment_current` constrains `school_id` alone.

**Rationale**: Spec.md User Story 1, acceptance scenario 3 explicitly requires two Managers to be simultaneously, currently assigned to the same Zone. The existing `(school_id)`-only / `(teacher_id)`-only pattern encodes "at most one current row for this thing," which is exactly wrong here — the right invariant is "at most one current row for this *(Zone, Manager)* pair," which still prevents the same Manager being double-assigned to the same Zone by a race, while allowing a second, different Manager freely.

**Alternatives considered**:
- *Reuse the exact `(zone_id)`-only pattern, modeling "multiple Managers" as multiple Zones sharing a name*: rejected — this would make "how many Managers cover this area" indistinguishable from "how many differently-named areas happen to overlap," which breaks User Story 3's "look up one Zone, see all its Managers" requirement.

## 2. Enforcing "Manager must be in the School's Zone" (FR-004/FR-005)

**Decision**: `AccountabilityService.assignSchoolManager(...)` gains one new check, performed in the same method, inside the same transaction, before the existing assign/reassign logic runs: resolve the School's current Zone (via `SchoolZoneAssignmentRepository`), then check whether a currently-open `ZoneManagerAssignment` row exists for `(that Zone, the chosen Manager)`. If the School has no current Zone, the Zone has no current Managers, or the chosen Manager isn't one of them, throw `SchoolManagerNotInZoneException` before any write happens.

**Rationale**: This is a cross-row business rule ("does a matching row exist elsewhere"), not a single-table constraint a `CHECK` or unique index can express — it has to be an application-level check, the same way FR-011's conflict detection already is (research.md §2 in specs/003). Doing it first, before any write, keeps `assignSchoolManager` atomic: either the whole assignment succeeds with a valid Manager, or nothing changes.

**Alternatives considered**:
- *A database trigger or foreign-key-style constraint across `organization_school_assignment` and `organization_zone_manager_assignment`*: rejected — PostgreSQL can't express "the manager_id in this row must match a manager_id in a *filtered* (current-only) subset of another table" as a plain constraint without a trigger, and this project has no existing precedent for triggers; an application-level check in the same place `AssignmentConflictException` already lives is simpler and consistent with how FR-011 is already enforced.

## 3. A new, distinct exception type, not reusing `AssignmentConflictException`

**Decision**: `SchoolManagerNotInZoneException` is a new, separate public type in `organization.api`, mapped to HTTP `422 Unprocessable Entity` by `OrganizationController` — distinct from `AssignmentConflictException`'s `409 Conflict`.

**Rationale**: The two failures mean different things to a caller. `409` (existing) means "you raced someone else — refresh and retry, your request might succeed later with no changes on your part." `422` (new) means "this request can never succeed as written — the Manager you chose isn't valid for this School's Zone, no amount of retrying fixes that without a different choice." Collapsing them into one error would make the frontend's retry-vs-fix-the-input logic ambiguous.

**Alternatives considered**:
- *Reuse `AssignmentConflictException`/409 for this case too*: rejected for the reason above — a 409 implies retry-worthy, and this isn't.

## 4. Zone query/command types stay `organization.internal` — no new `organization.api` surface (for now)

**Finding**: Nothing outside `organization` needs to call Zone operations directly for this spec — Zone visibility (FR-006) is Director/Admin only, surfaced through `ZoneController`'s REST endpoints, and no other module's scoping logic depends on Zone membership directly (Manager/Teacher scoping still goes through `AccountabilityQueries`/`Commands`, whose public shape is unchanged).

**Decision**: `Zone`, `ZoneManagerAssignment`, `SchoolZoneAssignment`, `ZoneService`, and `ZoneController` all live in `organization.internal`. The only new `organization.api` addition is `SchoolManagerNotInZoneException`, because `AccountabilityCommands`' existing caller contract requires it to be catchable.

**Rationale**: Consistent with this project's "build what's needed" discipline — `organization.api`'s surface should reflect what other modules actually depend on, not what might theoretically be useful later. If a future module needs to query Zone coverage directly, that's a small, additive change to add a public interface then, the same way `TeacherQueries`/`AuditReader` were added when Audit/Teacher actually needed them.

**Alternatives considered**:
- *Publish `ZoneQueries`/`ZoneCommands` in `organization.api` now, speculatively*: rejected — no current caller, and Constitution Principle V's boundary discipline is about what's actually depended on, not preemptive API surface.

## 5. Flyway migration number: decided at implementation time, not now

**Finding**: specs/005-teacher's plan claims `V4` for its own migration, but neither 005 nor 006 has been implemented yet — whichever lands first actually claims `V4`, and the other must then use the next number after that, not blindly reuse what its own plan.md says.

**Decision**: This plan does not hardcode a migration filename. Whoever implements this feature checks `backend/src/main/resources/db/migration/` at that time for the highest existing `V<n>`, and uses `V<n+1>`. If specs/005-teacher implements first, this feature updates its own migration reference to `V5` (and specs/005-teacher's plan.md, if it hasn't been implemented yet either, would need the same kind of check).

**Rationale**: Avoids a repeat of the exact mistake this pattern is designed to prevent — a hardcoded number in a plan written before implementation order is known is just as likely to be wrong as no number at all. Every prior module's migration number was correct because it was assigned relative to what had *already shipped*, not relative to what another in-flight plan predicted.

## 6. Frontend: extend `AssignmentsPage`, don't add a new page

**Decision**: Add Zone management (create Zone, assign/remove Manager, assign School to Zone) as a new section within the existing `AssignmentsPage`, and surface the new `422` rejection reason in the existing school-assignment form's error handling — rather than a separate `ZonesPage`.

**Rationale**: `AssignmentsPage` is already the Director/Admin screen for exactly this kind of organizational-structure management (assign/reassign, portfolio, unassigned list); Zones are one more piece of that same structure, not a separate concern a user would look for elsewhere. Adding a new page for a feature this tightly coupled to the existing assignment flow would fragment one workflow across two screens for no benefit.

**Alternatives considered**:
- *A new dedicated `ZonesPage`*: rejected — no user-facing reason to separate it; Organization's own `AssignmentsPage` already groups assign/portfolio/unassigned as one cohesive screen, and Zone management fits that same mold.
