# Feature Specification: Place Bulk Import

**Feature Branch**: `010-place-bulk-import`

**Created**: 2026-09-22

**Status**: Draft

**Input**: User description: "Zones need to be linked with places by PIN code — not a complete street-level address. There will be villages with different PIN codes, cities, towns. A Zone is roughly a district in Tamil Nadu; if a district is bigger it can be split into a few Zones, and the places inside that district get mapped accordingly to those Zones." Confirmed that this mapping is exactly what `specs/008-school-places` (already implemented) provides — Zone/Place already support it. Follow-up: `POST /api/v1/school/places` (specs/008) only adds one Place at a time, which doesn't scale to onboarding hundreds or thousands of villages/towns/cities across Tamil Nadu's districts. This feature adds bulk import — extending the already-implemented `school` module (specs/008-school-places) rather than reopening it, consistent with this project's established pattern.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Director/Admin Bulk-Imports Many Places in One Request (Priority: P1)

A Director or Admin submits a batch of places — each with its target Zone, name, and PIN code — in a single request, instead of calling the single-place endpoint hundreds of times.

**Why this priority**: This is the entire reason the feature exists — onboarding a district's worth of villages one HTTP call at a time is impractical; nothing else in this feature has value until batches can be submitted at all.

**Independent Test**: Can be fully tested by submitting a batch of several places across one or more Zones and confirming every valid place is immediately retrievable afterward, exactly as if each had been added individually.

**Acceptance Scenarios**:

1. **Given** an existing Zone, **When** Director/Admin submits a batch of several places all targeting that Zone, **Then** every place in the batch is created and immediately retrievable under that Zone.
2. **Given** two existing Zones, **When** Director/Admin submits a single batch containing places for both Zones, **Then** each place is created under the Zone its own row specified.

---

### User Story 2 - Bad Rows Don't Block Good Rows, and Failures Are Clearly Reported (Priority: P1)

When a batch contains some rows that are invalid (a missing name or PIN code, or a Zone identifier that doesn't exist), the valid rows are still imported, and the caller gets back a clear, row-by-row account of what succeeded and what failed and why.

**Why this priority**: Real place-master data entry is error-prone at scale (typos in a spreadsheet, a Zone id copy-pasted wrong for one row) — a bulk operation that discards an entire large batch over one bad row, or that fails silently, is worse than not having bulk import at all.

**Independent Test**: Can be fully tested by submitting a batch mixing valid and invalid rows and confirming the valid ones are created while each invalid one is reported individually with enough detail to fix and resubmit it.

**Acceptance Scenarios**:

1. **Given** a batch where some rows are valid and others reference a Zone identifier that does not exist, **When** the batch is submitted, **Then** the valid rows are created and each invalid row is reported with its position in the batch and the reason it failed, and the response makes clear which rows succeeded and which did not.
2. **Given** a batch where a row is missing a required field (name or PIN code), **When** the batch is submitted, **Then** that row is reported as failed with the missing field named, and every other valid row in the same batch is still created.
3. **Given** a batch that is entirely valid, **When** it is submitted, **Then** the response clearly shows every row succeeded, with no ambiguity about partial failure.

---

### User Story 3 - A Batch That's Too Large Is Rejected Up Front, Clearly (Priority: P2)

An oversized batch (far beyond a normal district's worth of places) is rejected immediately with a clear reason, rather than being accepted and then timing out, silently truncating, or overwhelming the system.

**Why this priority**: Protects the system from an accidental or malformed submission (e.g., an entire national place-master file pasted in by mistake) without requiring this to be figured out the hard way; lower priority than Stories 1-2 because it's a guardrail, not the feature's core value.

**Independent Test**: Can be fully tested by submitting a batch larger than the allowed maximum and confirming it is rejected immediately with a clear "too many rows" reason, with nothing from it imported.

**Acceptance Scenarios**:

1. **Given** a batch larger than the maximum allowed size, **When** it is submitted, **Then** the entire batch is rejected before anything is imported, with a clear reason naming the limit.

---

### Edge Cases

- What happens when the same place (same Zone, name, and PIN code) appears twice within one submitted batch? Both rows are imported independently — specs/008's existing rule (no uniqueness on name or PIN code) applies equally to bulk import; duplicates are not detected or rejected.
- What happens when a batch is empty? Rejected with a clear "empty batch" reason, not silently accepted as a no-op success.
- What happens when a row's Zone identifier is syntactically well-formed but doesn't correspond to any existing Zone? Reported as a failed row (Zone not found), the same as any other row-level validation failure — not a whole-batch failure.
- What happens when the caller isn't Director or Admin? The entire request is denied, consistent with every other place/Zone-maintenance endpoint in this system — there's no scenario where a non-privileged caller imports some rows.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST allow Director/Admin to submit a batch of places — each specifying a target Zone, a name, and a PIN code — in a single request.
- **FR-002**: The system MUST create every row in a submitted batch that passes validation, even when other rows in the same batch fail — a batch is not all-or-nothing.
- **FR-003**: The system MUST report, for each row in a submitted batch, whether it succeeded (and the created place's identity) or failed (and why), identified by the row's position in the batch.
- **FR-004**: The system MUST validate, per row, that a name and a PIN code are present, and that the specified Zone identifier corresponds to an existing Zone — a row failing any of these is reported as a failed row, not created.
- **FR-005**: The system MUST reject a batch that is empty, with a clear reason, before creating anything.
- **FR-006**: The system MUST reject a batch that exceeds a defined maximum row count, with a clear reason naming the limit, before creating anything from it.
- **FR-007**: The system MUST restrict bulk import to the Director/Admin roles, consistent with the existing single-place-add endpoint (specs/008 FR-001).
- **FR-008**: The system MUST apply the same never-deleted, no-uniqueness rules to bulk-imported places as to individually-added ones (specs/008 FR-002/FR-007) — bulk import is a faster way to call the same underlying capability, not a different one.

### Key Entities *(include if feature involves data)*

- **Place** (specs/008-school-places, unchanged): a bulk-imported place is stored identically to an individually-added one — no new entity or field is introduced by this feature.
- **Bulk Import Row Result** (not persisted): the per-row outcome of one submission — its position in the batch, and either the created place's identity or a failure reason. Exists only in the response to the submitting caller; nothing about a batch submission itself is stored as its own record.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A batch of 500 valid places for a single Zone is fully imported and every one immediately retrievable in a single request, verified through testing.
- **SC-002**: In a batch mixing valid and invalid rows, 100% of valid rows are created and 100% of invalid rows are reported with a specific, row-identified reason, in every tested case.
- **SC-003**: An oversized batch results in zero places created, verified through testing.
- **SC-004**: A non-Director/Admin caller's bulk-import request results in zero places created, in 100% of tested cases.

## Assumptions

- Bulk import accepts a batch as structured data (a JSON array of rows) in the request body — not a raw CSV/spreadsheet file upload. Converting a spreadsheet to that shape (e.g., in the browser, client-side) is assumed to be straightforward and is not this feature's concern; a true file-upload/CSV-parsing capability can be added later if this turns out to be a real friction point, the same way this feature itself was split out of specs/008 once one-row-at-a-time turned out to be the real friction point.
- The maximum batch size is 5,000 rows per request — large enough for a big Tamil Nadu district's full place list in one submission, small enough to keep a single request's processing time and payload size reasonable. This number is a starting default, not a validated capacity limit; it can be revisited once real usage patterns are known.
- Unlike the existing single-place-add endpoint (specs/008), which performs no check that a submitted Zone identifier actually exists, bulk import does check this per row (FR-004) — deliberately more careful, since a typo'd Zone id is far easier to spot and fix immediately when adding one place than when it's buried in a batch of hundreds. The single-add endpoint's existing behavior is unchanged by this feature.
- This feature only adds a way to submit many places at once — it does not add bulk *update* or bulk *delete* (places remain never-deleted and never-edited, specs/008 FR-007), and it does not change how a single place is added, viewed, or looked up.
