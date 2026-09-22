# Feature Specification: School Master Data — Places

**Feature Branch**: `008-school-places`

**Created**: 2026-09-22

**Status**: Draft

**Input**: User description: "zone is nothing but a set of places i.e town, cities, villages to be grouped under a name i.e zone name... i will add the places i.e zipcodes, cities, towns, villages that fall under each and every zone - so when we query which zone is a place in it will answer clearly." Extends `specs/007-school-zone` (already implemented) with the actual places — named localities identified by name and/or postal (PIN) code — that make up each Zone, so "which Zone covers this place" becomes a real, answerable lookup rather than something only Director/Admin remembers.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Director/Admin Adds a Place to a Zone (Priority: P1)

A Director or Admin records a place — a city, town, or village, identified by name and its PIN code — as belonging to a specific Zone.

**Why this priority**: Nothing else in this feature has anywhere to attach to until places exist and are linked to a Zone.

**Independent Test**: Can be fully tested by adding a place to a Zone and confirming it's immediately retrievable with the Zone it was assigned to.

**Acceptance Scenarios**:

1. **Given** an existing Zone, **When** Director/Admin adds a place with a name and PIN code to it, **Then** the place is retrievable, showing that Zone.
2. **Given** two different Zones, **When** Director/Admin adds places with the same PIN code to each (a PIN code area can span more than one named locality, or the reverse — multiple small villages can share one PIN code), **Then** both places are recorded independently, each under its own Zone.

---

### User Story 2 - Anyone Can Look Up Which Zone a Place Belongs To (Priority: P1)

Given a PIN code or a place name, the system answers clearly which Zone (or Zones, if more than one place matches) that place belongs to.

**Why this priority**: This is the actual capability the feature exists for — recording places has no value until the lookup works.

**Independent Test**: Can be fully tested by adding a place to a Zone, then looking it up by its PIN code and separately by its name, confirming both return the correct Zone.

**Acceptance Scenarios**:

1. **Given** a place with a known PIN code assigned to Zone A, **When** that PIN code is looked up, **Then** Zone A is returned.
2. **Given** a place with a known name assigned to Zone B, **When** that name is looked up, **Then** Zone B is returned.
3. **Given** a PIN code that spans multiple recorded places in different Zones, **When** that PIN code is looked up, **Then** every matching place and its Zone are returned, not just one arbitrarily.
4. **Given** a PIN code or name that has never been recorded, **When** it's looked up, **Then** the system clearly reports no match, not an error and not a misleading empty-but-ambiguous result.

---

### User Story 3 - Director/Admin Views Every Place in a Zone (Priority: P2)

Director/Admin can look up a Zone and see every place currently recorded under it.

**Why this priority**: Visibility that makes Stories 1-2's data auditable and correctable — someone can spot a place added to the wrong Zone.

**Independent Test**: Can be fully tested by adding several places to a Zone and confirming a single lookup returns exactly that set.

**Acceptance Scenarios**:

1. **Given** a Zone with three places added to it, **When** that Zone's places are looked up, **Then** all three are returned.
2. **Given** a Zone with no places added yet, **When** its places are looked up, **Then** an empty list is returned, not an error.

---

### Edge Cases

- What happens when Director/Admin adds a place with a name that's already recorded under a *different* Zone? Allowed — real place names are not unique across India (many villages share common names), and even PIN codes can legitimately span more than one recorded place (Edge Case in User Story 1, AC2). The system does not attempt to deduplicate or reject based on name/PIN-code collision.
- What happens when a place is added under the wrong Zone by mistake? Out of scope for this feature (see Assumptions) — no correction/move operation is included; a follow-up feature can add one if this turns out to be needed often enough to justify it.
- What happens when a PIN code is looked up using extra whitespace or mixed case in a name search? The system matches meaningfully (e.g., trims whitespace, case-insensitive name matching) rather than requiring an exact byte-for-byte match a user would have no reliable way to produce.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST allow Director/Admin to add a place — a name (city/town/village) and a PIN code — under a specific Zone.
- **FR-002**: The system MUST allow more than one place to share the same PIN code, each independently recorded (possibly under different Zones).
- **FR-003**: The system MUST allow looking up every place matching a given PIN code, returning each one's Zone.
- **FR-004**: The system MUST allow looking up every place matching a given name (case-insensitive), returning each one's Zone.
- **FR-005**: The system MUST clearly distinguish "no place matches this PIN code/name" from an error or from a real, empty-but-valid result.
- **FR-006**: The system MUST allow Director/Admin to retrieve every place currently recorded under a given Zone.
- **FR-007**: The system MUST NOT delete a place once added.

### Key Entities *(include if feature involves data)*

- **Place**: A named locality (city, town, or village) with a PIN code, recorded as belonging to one Zone. Never deleted once added. Not required to be unique by name or PIN code — real-world places legitimately share both.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of places added by Director/Admin are immediately retrievable with the Zone they were assigned to.
- **SC-002**: A PIN code or place name lookup returns every matching place's Zone in a single request, verified through testing.
- **SC-003**: A lookup for a PIN code or name with no recorded matches clearly reports zero results in 100% of tested cases, never mistaken for an error.
- **SC-004**: A Zone's full list of recorded places is retrievable in a single request.

## Assumptions

- This feature only adds and looks up places — it does not include moving a place to a different Zone or editing/removing one once added. If reassignment turns out to be a real, recurring need, it can be added as a follow-up feature (the same way `specs/007-school-zone` itself was factored out of the original Zone-scoping work).
- Place names and PIN codes are not required to be unique, individually or in combination — real Indian postal/administrative geography doesn't guarantee either (Edge Cases).
- This feature extends the `school` module (`specs/007-school-zone`, already implemented) rather than reopening it, consistent with how that module itself was split out of the original Zone-scoping work.
