# Feature Specification: School Master Data — Zones

**Feature Branch**: `007-school-zone`

**Created**: 2026-09-22

**Status**: Draft

**Input**: User description: "zones are part of school master data - zones to be defined like zone id, zone name - later we can link it school master with corresponding zone." Pulls the Zone concept out of the `organization` module (where specs/006-zone-scoping originally placed it) and into `school` (School Master Data) instead, since a Zone groups Schools and is therefore School data, not Manager data — see `.specify/memory/constitution.md` Amendment 1.7.0. This is a deliberately minimal, pulled-forward slice of the full School Master Data module (tracker row 7): just `Zone(id, name)` and each School's current Zone. School's other master-data fields (profile, branch, assigned manager reference, contact/billing details) are out of scope here and remain tracker row 7's job, to be specified later.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Director/Admin Defines Zones (Priority: P1)

A Director or Admin creates a Zone — a named geographic area — so Schools can later be grouped into it and Managers can later be assigned to cover it.

**Why this priority**: Nothing else in this feature (or in specs/006-zone-scoping, which depends on this one) has anywhere to attach to until Zones exist.

**Independent Test**: Can be fully tested by creating a Zone and confirming it's immediately retrievable by id, with the name entered.

**Acceptance Scenarios**:

1. **Given** no existing Zones, **When** Director/Admin creates a Zone with a name, **Then** the Zone exists and is retrievable with that exact name.
2. **Given** an existing Zone, **When** Director/Admin looks it up by id, **Then** its name is returned unchanged.

---

### User Story 2 - Director/Admin Assigns a School to a Zone (Priority: P1)

A Director or Admin records which Zone a School currently belongs to. A School belongs to exactly one Zone at a time; changing it is a new, timestamped assignment, not an overwrite of the old one.

**Why this priority**: This is the actual "link School master data to its corresponding Zone" the feature description asks for — without it, Zones exist but nothing uses them.

**Independent Test**: Can be fully tested by assigning a School to a Zone, confirming that Zone is retrievable as the School's current one, then reassigning it to a different Zone and confirming the change while the prior assignment remains visible in history.

**Acceptance Scenarios**:

1. **Given** a School with no Zone assigned yet, **When** Director/Admin assigns it to a Zone, **Then** that Zone is retrievable as the School's current Zone.
2. **Given** a School already assigned to Zone A, **When** Director/Admin assigns it to Zone B instead, **Then** Zone B is now the School's current Zone, and the School's history still shows it was previously in Zone A.
3. **Given** two Director/Admin users both attempt to change the same School's Zone assignment at the same time, naming the same current assignment as the one they intend to end, **When** both requests are processed, **Then** exactly one succeeds and the other is rejected as a conflict — never both silently applied.

---

### User Story 3 - Anyone With Access Can View a School's Zone and a Zone's Schools (Priority: P2)

Director/Admin (and, in-process, other modules such as Organization) can look up which Zone a School currently belongs to, and which Schools currently belong to a given Zone.

**Why this priority**: Read visibility is what makes Stories 1-2 actually useful — in particular, this is the read path specs/006-zone-scoping's Manager-scoping correction depends on.

**Independent Test**: Can be fully tested by assigning several Schools to a Zone and confirming a single lookup returns exactly that set, and by looking up an unassigned School and confirming it's reported as having no current Zone rather than an error.

**Acceptance Scenarios**:

1. **Given** three Schools currently assigned to the same Zone, **When** that Zone's Schools are looked up, **Then** all three are returned.
2. **Given** a School with no Zone assigned yet, **When** its current Zone is looked up, **Then** the answer indicates "no Zone assigned," not an error.

---

### Edge Cases

- What happens when Director/Admin looks up a Zone id that doesn't exist? A clear "not found" result, not a silent empty answer indistinguishable from a real, empty Zone.
- What happens when a School's Zone assignment is looked up before it's ever been assigned one? Reported as unassigned (User Story 3, acceptance scenario 2) — consistent with how the rest of this system already represents "never assigned" (specs/003-organization-scoping's unassigned-items handling).
- What happens when Director/Admin tries to create a Zone with the same name as an existing one? Allowed — this feature does not require Zone names to be unique (no operational reason has been identified to block it, and disambiguating "is this really a duplicate or two different areas that happen to share a name" is a judgment call left to Director/Admin, not the system).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST allow Director/Admin to create a Zone with a name.
- **FR-002**: The system MUST allow Director/Admin to retrieve a Zone by id.
- **FR-003**: The system MUST allow Director/Admin to assign a School to a Zone.
- **FR-004**: The system MUST allow Director/Admin to reassign a School to a different Zone, preserving the prior assignment as history rather than overwriting it (Constitution Principle I).
- **FR-005**: The system MUST reject a School-Zone reassignment that names an assignment already changed by someone else, rather than silently applying on top of it.
- **FR-006**: The system MUST allow retrieval of a School's current Zone, distinguishing "no Zone assigned yet" from an error.
- **FR-007**: The system MUST allow retrieval of every School currently assigned to a given Zone.
- **FR-008**: The system MUST make Zone and School-Zone data available for other modules to read as reference data, without those modules needing to duplicate or re-enter it.
- **FR-009**: The system MUST NOT delete a Zone or a School-Zone assignment record once created.

### Key Entities *(include if feature involves data)*

- **Zone**: A named geographic area. `id`, `name`. Never deleted once created.
- **School-Zone Assignment**: One period during which a School belonged to a particular Zone. A School belongs to exactly one Zone at a time; changing it ends the prior assignment and opens a new one, never overwriting the prior record.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of Zones created by Director/Admin are immediately retrievable with the exact name entered.
- **SC-002**: 100% of School-Zone assignments and reassignments are retrievable as history — the prior Zone remains visible after a reassignment, verified through testing.
- **SC-003**: 100% of concurrent, conflicting School-Zone reassignment attempts result in exactly one success and the rest rejected, never more than one silently applied, verified through testing.
- **SC-004**: A lookup of a Zone's currently assigned Schools, or a School's current Zone, completes as a single request with no need to cross-reference other data.

## Assumptions

- This feature is a deliberately minimal, pulled-forward slice of the full School Master Data module (tracker row 7) — School's other master-data fields (profile, branch, contact/billing details, assigned manager reference) are out of scope here and will be specified when that module is built.
- School is still represented as an opaque identifier (no real School master record exists yet) — the same narrowing specs/003-organization-scoping already documented for its own School/Teacher references. School-Zone assignment therefore follows the same opaque-identifier, append-only assignment pattern Organization already established for School-Manager assignment, rather than being a field on a School entity that doesn't exist yet.
- Zone names are not required to be unique.
- Who may create Zones and assign Schools to them (Director/Admin) mirrors the same role split already established for master-data maintenance elsewhere in this system (Constitution Principle II) — no new role or permission model is introduced.
