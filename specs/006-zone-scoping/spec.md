# Feature Specification: Zone-Based Manager Scoping

**Feature Branch**: `006-zone-scoping`

**Created**: 2026-09-22

**Status**: Draft

**Input**: User description: "the business runs like there will be zones i.e some areas. The schools belonging to that zone will be managed by single manager or more managers if schools are many. The teachers that are contracted to that schools in the zone managed by the manager will be under that manager." Corrects a simplification in the already-shipped Organization module (specs/003-organization-scoping), which modeled Manager↔School and Manager↔Teacher as two independent assignments. See `.specify/memory/constitution.md` Amendment 1.6.0 and `docs/HLS Teacher Management System — Requirements.md` §2/§10/§13 for the corrected operating model this spec implements.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Director/Admin Defines Zones and Their Managers (Priority: P1)

A Director or Admin creates a Zone (a geographic area) and assigns one or more Managers to cover it — a second Manager is added once a Zone has enough Schools that one person can't reasonably cover them all.

**Why this priority**: Nothing else in this feature has anywhere to attach to until Zones and their Managers exist — this is the foundation.

**Independent Test**: Can be fully tested by creating a Zone, assigning a Manager to it, and confirming that Manager is retrievable as one of the Zone's current Managers.

**Acceptance Scenarios**:

1. **Given** no existing Zones, **When** Director/Admin creates a Zone with a name, **Then** the Zone exists and has no Managers assigned yet.
2. **Given** an existing Zone, **When** Director/Admin assigns a Manager to it, **Then** that Manager appears as one of the Zone's currently assigned Managers.
3. **Given** a Zone with one assigned Manager and growing school coverage needs, **When** Director/Admin assigns a second Manager to the same Zone, **Then** both Managers are currently assigned to that Zone at once.
4. **Given** a Manager assigned to a Zone, **When** Director/Admin removes that assignment, **Then** the Manager no longer appears as currently covering that Zone.

---

### User Story 2 - A School's Manager Must Come From Its Zone's Managers (Priority: P1)

A School belongs to exactly one Zone. When a Director/Admin assigns (or reassigns) a School's accountable Manager, the chosen Manager must currently be one of that Zone's assigned Managers — assigning someone outside the Zone's Manager pool is rejected.

**Why this priority**: This is the actual scoping correction — without this constraint enforced, a School could end up "managed" by someone with no real accountability for that area, which is exactly the leakage Constitution Principle II says must be prevented.

**Independent Test**: Can be fully tested by assigning a School to a Zone with a known set of Managers, then attempting to assign that School to a Manager inside vs. outside that set.

**Acceptance Scenarios**:

1. **Given** a School assigned to a Zone with Manager A covering it, **When** Director/Admin assigns Manager A as that School's accountable Manager, **Then** the assignment succeeds.
2. **Given** the same School and Zone, **When** Director/Admin attempts to assign Manager X — who is not currently assigned to that Zone — as the School's accountable Manager, **Then** the assignment is rejected.
3. **Given** a Zone with two assigned Managers (A and B), **When** Director/Admin assigns one School in that Zone to Manager A and a different School in the same Zone to Manager B, **Then** both assignments succeed — each School still has exactly one accountable Manager, drawn from the Zone's shared pool.
4. **Given** a School not yet assigned to any Zone, **When** Director/Admin attempts to assign it an accountable Manager, **Then** the assignment is rejected until the School is first assigned to a Zone.

---

### User Story 3 - Director/Admin Sees Zone Coverage at a Glance (Priority: P2)

A Director or Admin can look up a Zone and see which Managers currently cover it and which Schools currently belong to it, to answer "who's responsible for this area" and "is this Zone adequately staffed" without cross-referencing multiple screens.

**Why this priority**: Valuable operational visibility, but it's a read-only view over data Stories 1-2 already create and enforce — it doesn't change what's allowed, just what's easy to see.

**Independent Test**: Can be fully tested by setting up a Zone with known Managers and Schools, then confirming a single lookup returns exactly that Zone's current Managers and Schools.

**Acceptance Scenarios**:

1. **Given** a Zone with two assigned Managers and three Schools, **When** Director/Admin looks up that Zone, **Then** both Managers and all three Schools are shown as currently belonging to it.
2. **Given** a Zone with no Schools assigned yet, **When** Director/Admin looks it up, **Then** it shows its assigned Managers and an empty School list, not an error.

---

### Edge Cases

- What happens when Director/Admin removes a Manager from a Zone while that Manager is still the accountable Manager of one or more Schools in that Zone? The removal succeeds, and those Schools keep their current Manager as-is — the Zone-membership constraint is enforced when a School's Manager is *assigned*, not retroactively re-checked against every existing assignment whenever Zone membership changes.
- What happens when a School's Zone is changed to a different Zone after it already has an accountable Manager? The existing assignment is untouched; the constraint applies the next time that School's Manager is assigned or reassigned, now checked against the new Zone's Managers.
- What happens when Director/Admin tries to assign a School's Manager, but the School's Zone currently has zero assigned Managers? The assignment is rejected — there's no valid Manager to choose from until at least one is assigned to that Zone.
- What happens to a Teacher's accountable Manager when their contracted School's Manager changes? Nothing, in this feature — a Teacher's accountable Manager remains a directly, independently assigned relationship (unchanged from the already-shipped Organization module) until a future module can derive it from the Teacher's actual School contract (see Assumptions).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST allow Director/Admin to create a Zone with a name.
- **FR-002**: The system MUST allow Director/Admin to assign one or more Managers to a Zone, and to remove a Manager's assignment to a Zone.
- **FR-003**: The system MUST allow Director/Admin to assign a School to exactly one Zone.
- **FR-004**: The system MUST reject assigning a Manager as a School's accountable Manager unless that Manager is currently assigned to the School's Zone.
- **FR-005**: The system MUST reject assigning a School's accountable Manager while that School has no Zone assigned, or while its Zone has no Managers currently assigned.
- **FR-006**: The system MUST allow Director/Admin to view a Zone's currently assigned Managers and currently assigned Schools.
- **FR-007**: The system MUST NOT change how a Teacher's accountable Manager is assigned or queried — that relationship remains the independently-assigned mechanism the Organization module already provides, pending a future module that can derive it from the Teacher's actual School contract.
- **FR-008**: The system MUST continue to enforce that School-level accountability (now Zone-constrained) and Teacher-level accountability remain two separately queryable relationships, since FR-007 keeps the latter unchanged.

### Key Entities *(include if feature involves data)*

- **Zone**: A named geographic area. The unit Manager coverage is organized around. Never deleted once created (consistent with how the rest of this system treats organizational structure — Constitution Principle I).
- **Zone-Manager Assignment**: One period during which a Manager was (or is) assigned to cover a Zone. A Zone may have more than one currently-assigned Manager at once. Past assignments are preserved, never overwritten, matching how School- and Teacher-Manager assignments already work in the Organization module.
- **School-Zone Assignment**: One period during which a School belonged to a particular Zone. A School belongs to exactly one Zone at a time.
- **School-Manager Assignment** *(existing, constrained by this feature)*: Unchanged in shape from the already-shipped Organization module, except that assigning it now requires the chosen Manager to be currently covering the School's Zone (FR-004).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of attempts to assign a School's accountable Manager to someone outside that School's Zone's currently assigned Managers are rejected, verified through testing.
- **SC-002**: 100% of attempts to assign a School's accountable Manager to someone currently covering that School's Zone succeed (given no other conflict), verified through testing.
- **SC-003**: Director/Admin can retrieve a Zone's full current coverage (its Managers and its Schools) in a single lookup.
- **SC-004**: Zero regressions in existing Teacher-Manager assignment behavior — every test that passed for that capability before this feature continues to pass unchanged.

## Assumptions

- A Teacher's accountable Manager remains the existing, independently-assigned relationship from the Organization module — it is **not** derived from Zone/School coverage by this feature. Deriving it for real requires knowing which School a Teacher is actually contracted to, which belongs to the future SchoolBilling module's Teacher–School–Manager Contract (not yet built). This feature only corrects School-level Manager accountability to be Zone-constrained; Teacher-level accountability is revisited once that Contract exists.
- Removing a Manager from a Zone, or moving a School to a different Zone, does not retroactively invalidate or auto-reassign any existing School-Manager assignment — the Zone-membership constraint is checked at assignment time only (see Edge Cases). Reassigning an "orphaned" School (whose Manager is no longer in its Zone) to a valid Manager remains a manual Director/Admin action.
- A School belongs to exactly one Zone at a time; this feature does not support a School spanning multiple Zones.
- Neither School nor Teacher has real master-data tables yet (both are opaque identifiers here, the same narrowing the Organization module's spec already documented) — School-Zone and School-Manager assignments reference an opaque School identifier, not a validated School record.
- This feature does not change Teacher-Manager assignment's independence (FR-007/FR-008), so specs/005-teacher's plan — which depends on `identity.api.ManagerScopeQueries`/`TeacherScopeQueries` staying stable — needs no changes as a result of this feature.
