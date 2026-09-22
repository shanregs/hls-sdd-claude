# Feature Specification: Zone-Based Manager Scoping

**Feature Branch**: `006-zone-scoping`

**Created**: 2026-09-22 (reworked 2026-09-22, after specs/007-school-zone and specs/008-school-places shipped)

**Status**: Draft

**Input**: User description (original): "the business runs like there will be zones i.e some areas. The schools belonging to that zone will be managed by single manager or more managers if schools are many. The teachers that are contracted to that schools in the zone managed by the manager will be under that manager." Corrects a simplification in the already-shipped Organization module (specs/003-organization-scoping), which modeled Manager↔School and Manager↔Teacher as two independent assignments. See `.specify/memory/constitution.md` Principle II and Amendment 1.6.0/1.7.0, and `docs/HLS Teacher Management System — Requirements.md` §2/§10/§13, for the corrected operating model this spec implements.

**Rework note (2026-09-22)**: This spec was originally written and planned (spec/plan/research/data-model/contracts/tasks, never implemented) when Zone was expected to be owned by `organization` itself. Two corrections landed after that (constitution Amendments 1.6.0 then 1.7.0) before any of this spec's code was written: Zone and School↔Zone assignment are School master data, owned by the `school` module, and already fully implemented and shipped (`specs/007-school-zone`, `specs/008-school-places`). This rework removes everything this spec used to (re)define about Zone and School↔Zone assignment — `school.api.ZoneQueries`/`ZoneCommands` already provide it — and narrows this spec to exactly what `organization` still needs to own per the constitution: Zone–Manager assignment, and constraining School–Manager assignment to a Zone's currently assigned Managers.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Director/Admin Assigns Manager(s) to Cover an Existing Zone (Priority: P1)

A Director or Admin assigns one or more Managers to cover an existing Zone (created via School Master Data's Zone capability, `specs/007-school-zone`) — a second Manager is added once a Zone has enough Schools that one person can't reasonably cover them all.

**Why this priority**: Nothing else in this feature has anywhere to attach to until a Zone has at least one covering Manager — this is the foundation.

**Independent Test**: Can be fully tested by assigning a Manager to an existing Zone and confirming that Manager is retrievable as one of the Zone's currently covering Managers.

**Acceptance Scenarios**:

1. **Given** an existing Zone with no Managers assigned yet, **When** Director/Admin assigns a Manager to it, **Then** that Manager appears as one of the Zone's currently covering Managers.
2. **Given** a Zone with one covering Manager and growing school coverage needs, **When** Director/Admin assigns a second Manager to the same Zone, **Then** both Managers currently cover that Zone at once.
3. **Given** a Manager covering a Zone, **When** Director/Admin removes that assignment, **Then** the Manager no longer appears as currently covering that Zone.
4. **Given** a Zone identifier that doesn't correspond to any existing Zone, **When** Director/Admin attempts to assign a Manager to it, **Then** the assignment is rejected.

---

### User Story 2 - A School's Manager Must Come From Its Zone's Covering Managers (Priority: P1)

A School belongs to exactly one Zone (School Master Data, already implemented). When a Director/Admin assigns (or reassigns) a School's accountable Manager, the chosen Manager must currently cover that School's Zone — assigning someone outside the Zone's covering-Manager pool is rejected.

**Why this priority**: This is the actual scoping correction — without this constraint enforced, a School could end up "managed" by someone with no real accountability for that area, which is exactly the leakage Constitution Principle II says must be prevented.

**Independent Test**: Can be fully tested by assigning a School to a Zone with a known set of covering Managers, then attempting to assign that School's accountable Manager to someone inside vs. outside that set.

**Acceptance Scenarios**:

1. **Given** a School currently in a Zone covered by Manager A, **When** Director/Admin assigns Manager A as that School's accountable Manager, **Then** the assignment succeeds.
2. **Given** the same School and Zone, **When** Director/Admin attempts to assign Manager X — who does not currently cover that Zone — as the School's accountable Manager, **Then** the assignment is rejected.
3. **Given** a Zone covered by two Managers (A and B), **When** Director/Admin assigns one School in that Zone to Manager A and a different School in the same Zone to Manager B, **Then** both assignments succeed — each School still has exactly one accountable Manager, drawn from the Zone's shared covering-Manager pool.
4. **Given** a School with no current Zone, **When** Director/Admin attempts to assign it an accountable Manager, **Then** the assignment is rejected until the School is first assigned to a Zone (via School Master Data's existing capability).

---

### User Story 3 - Director/Admin Sees Zone Coverage at a Glance (Priority: P2)

A Director or Admin can look up a Zone and see which Managers currently cover it and which Schools currently belong to it, to answer "who's responsible for this area" and "is this Zone adequately staffed" without cross-referencing multiple screens.

**Why this priority**: Valuable operational visibility, but it's a read-only view over data Stories 1-2 already create and enforce (plus School↔Zone data the `school` module already exposes) — it doesn't change what's allowed, just what's easy to see.

**Independent Test**: Can be fully tested by setting up a Zone with known covering Managers and Schools, then confirming a single lookup returns exactly that Zone's current Managers and Schools.

**Acceptance Scenarios**:

1. **Given** a Zone with two covering Managers and three Schools currently in it, **When** Director/Admin looks up that Zone's coverage, **Then** both Managers and all three Schools are shown.
2. **Given** a Zone with no Schools currently in it, **When** Director/Admin looks up its coverage, **Then** it shows its covering Managers and an empty School list, not an error.

---

### Edge Cases

- What happens when Director/Admin removes a Manager from a Zone while that Manager is still the accountable Manager of one or more Schools in that Zone? The removal succeeds, and those Schools keep their current Manager as-is — the Zone-coverage constraint is enforced when a School's Manager is *assigned*, not retroactively re-checked against every existing assignment whenever Zone coverage changes.
- What happens when a School's Zone is changed to a different Zone (via School Master Data's existing capability) after it already has an accountable Manager? The existing Manager assignment is untouched; the constraint applies the next time that School's Manager is assigned or reassigned, now checked against the new Zone's covering Managers.
- What happens when Director/Admin tries to assign a School's Manager, but the School's Zone currently has zero covering Managers? The assignment is rejected — there's no valid Manager to choose from until at least one is assigned to cover that Zone.
- What happens to a Teacher's accountable Manager when their contracted School's Manager changes? Nothing, in this feature — a Teacher's accountable Manager remains a directly, independently assigned relationship (unchanged from the already-shipped Organization module) until a future module can derive it from the Teacher's actual School contract (see Assumptions).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST allow Director/Admin to assign one or more Managers to cover an existing Zone, and to remove a Manager's coverage of a Zone.
- **FR-002**: The system MUST reject assigning a Manager to cover a Zone identifier that does not correspond to an existing Zone (read through `school`'s public Zone API — this module never owns Zone data itself).
- **FR-003**: The system MUST reject assigning a Manager as a School's accountable Manager unless that Manager currently covers the School's Zone (the School's current Zone read through `school`'s public API).
- **FR-004**: The system MUST reject assigning a School's accountable Manager while that School has no current Zone, or while its Zone has no Managers currently covering it.
- **FR-005**: The system MUST allow Director/Admin to view a Zone's currently covering Managers and currently assigned Schools (the latter read through `school`'s public API) in a single lookup.
- **FR-006**: The system MUST NOT change how a Teacher's accountable Manager is assigned or queried — that relationship remains the independently-assigned mechanism the Organization module already provides, pending a future module that can derive it from the Teacher's actual School contract.
- **FR-007**: The system MUST continue to enforce that School-level accountability (now Zone-constrained) and Teacher-level accountability remain two separately queryable relationships, since FR-006 keeps the latter unchanged.
- **FR-008**: The system MUST NOT own, duplicate, or re-implement Zone or School↔Zone assignment data — both are read exclusively through `school.api.ZoneQueries`, the same read-through-a-public-API pattern `schoolbilling` already uses for Teacher/School master data (constitution Principle V).

### Key Entities *(include if feature involves data)*

- **Zone** *(owned by `school`, not this feature — referenced only)*: A named geographic area, already implemented (`specs/007-school-zone`). This feature reads Zone identity through `school.api.ZoneQueries.findById(...)` to validate a Zone id exists (FR-002); it does not store or duplicate Zone data.
- **Zone-Manager Assignment** *(new, owned by this feature)*: One period during which a Manager was (or is) assigned to cover a Zone. A Zone may have more than one currently-covering Manager at once. Past assignments are preserved, never overwritten, matching how School- and Teacher-Manager assignments already work in the Organization module.
- **School-Zone Assignment** *(owned by `school`, not this feature — referenced only)*: Already implemented (`specs/007-school-zone`); this feature reads a School's current Zone through `school.api.ZoneQueries.currentZoneForSchool(...)` (FR-003/FR-004) and a Zone's current Schools through `currentSchoolsForZone(...)` (FR-005). It does not store or duplicate this data.
- **School-Manager Assignment** *(existing, constrained by this feature)*: Unchanged in shape from the already-shipped Organization module, except that assigning it now requires the chosen Manager to currently cover the School's Zone (FR-003).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of attempts to assign a School's accountable Manager to someone who does not currently cover that School's Zone are rejected, verified through testing.
- **SC-002**: 100% of attempts to assign a School's accountable Manager to someone who currently covers that School's Zone succeed (given no other conflict), verified through testing.
- **SC-003**: Director/Admin can retrieve a Zone's full current coverage (its covering Managers and its Schools) in a single lookup.
- **SC-004**: Zero regressions in existing Teacher-Manager assignment behavior — every test that passed for that capability before this feature continues to pass unchanged.

## Assumptions

- A Teacher's accountable Manager remains the existing, independently-assigned relationship from the Organization module — it is **not** derived from Zone/School coverage by this feature. Deriving it for real requires knowing which School a Teacher is actually contracted to, which belongs to the future SchoolBilling module's Teacher–School–Manager Contract (not yet built). This feature only corrects School-level Manager accountability to be Zone-constrained; Teacher-level accountability is revisited once that Contract exists.
- Removing a Manager from a Zone, or moving a School to a different Zone, does not retroactively invalidate or auto-reassign any existing School-Manager assignment — the Zone-coverage constraint is checked at assignment time only (see Edge Cases). Reassigning an "orphaned" School (whose Manager no longer covers its Zone) to a valid Manager remains a manual Director/Admin action.
- Zone itself, and a School's membership in a Zone, are entirely out of this feature's scope to create, change, or view directly — those are `school`'s existing, already-implemented capabilities (`specs/007-school-zone`). This feature only adds Zone–Manager coverage and the Manager-must-cover-the-Zone constraint on top of them.
- This feature does not change Teacher-Manager assignment's independence (FR-006/FR-007), so specs/005-teacher's plan — which depends on `identity.api.ManagerScopeQueries`/`TeacherScopeQueries` staying stable — needs no changes as a result of this feature.
