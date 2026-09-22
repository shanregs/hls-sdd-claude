# Feature Specification: Teacher Master Data

**Feature Branch**: `005-teacher`

**Created**: 2026-09-22

**Status**: Draft

**Input**: User description: "Build the module that maintains each teacher's profile: name, contact details, bank details for payout, the monthly salary HLS has offered them, and their current status (in training / active / on leave / exited). A System Assistant/Admin creates and maintains these records; a Director and the teacher's assigned Manager can view them; a teacher can view (but not edit) their own profile from the mobile app. This is reference data other modules (Attendance, Payroll, Training, Substitution) look up but never own — status changes (e.g. a teacher exiting) must be tracked with a timestamp, not just silently overwritten (Requirements §10; Constitution Principle I)." **Scope correction (2026-09-22)**: bank details are excluded from this module for now — see Assumptions.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Admin Onboards a New Teacher's Profile (Priority: P1)

An Admin (System Assistant/Admin) creates a new teacher's profile — name, contact details, the monthly salary HLS has offered them, and an initial status — so the teacher exists as a single, authoritative record other modules can rely on from day one.

**Why this priority**: Nothing else in this feature has any value until a profile can actually be created — this is the foundation every other story builds on.

**Independent Test**: Can be fully tested by having an Admin create a profile for a new teacher and confirming it's immediately retrievable with exactly the details entered.

**Acceptance Scenarios**:

1. **Given** a new teacher who has no existing profile, **When** Admin creates a profile with name, contact details, HLS-offered salary, and an initial status, **Then** the profile is saved and immediately retrievable with those exact details.
2. **Given** Admin is creating a profile, **When** a required detail (e.g., salary) is missing, **Then** the profile is not saved and Admin is told what's missing.

---

### User Story 2 - Admin Maintains a Profile With Full History (Priority: P2)

An Admin updates an existing teacher's contact details or HLS-offered salary, or changes their status (e.g., "in training" to "active" once training completes, "active" to "on leave," or "active" to "exited" when a teacher leaves HLS) — with every change preserved, never silently overwritten.

**Why this priority**: Teacher data changes constantly in practice (salary revisions, contact changes, status transitions) — without trustworthy maintenance, the profile Story 1 created quickly goes stale or becomes disputed.

**Independent Test**: Can be fully tested by updating a field or status on an existing profile and confirming both the new value and the prior value (with when the change happened) remain retrievable afterward.

**Acceptance Scenarios**:

1. **Given** an existing profile, **When** Admin updates the HLS-offered salary, **Then** the profile reflects the new salary and the prior salary, and when it changed, remain retrievable.
2. **Given** a teacher finishes training, **When** Admin changes their status from "in training" to "active," **Then** the profile reflects "active" going forward and the change is timestamped.
3. **Given** a teacher leaves HLS, **When** Admin sets their status to "exited," **Then** the profile is not deleted — it remains retrievable, now showing "exited," with the full history of everything that came before still intact.

---

### User Story 3 - Director and the Assigned Manager View a Profile (Priority: P2)

A Director can view any teacher's profile. A Manager can view the profile of a teacher currently assigned to them, but not of a teacher who isn't.

**Why this priority**: Master data has no operational value until the people who need it — Directors overseeing the organization, Managers running their own teachers — can actually see it; this is what makes Stories 1-2's data useful day to day.

**Independent Test**: Can be fully tested by having a Director view any profile, and a Manager view a profile for a teacher currently assigned to them and attempt (and fail) to view one that isn't.

**Acceptance Scenarios**:

1. **Given** any teacher's profile, **When** a Director views it, **Then** the full profile is shown, regardless of who the teacher is currently assigned to.
2. **Given** a teacher currently assigned to Manager A, **When** Manager A views that teacher's profile, **Then** the full profile is shown.
3. **Given** a teacher not currently assigned to Manager B, **When** Manager B attempts to view that teacher's profile, **Then** access is denied.
4. **Given** a teacher is reassigned from Manager A to Manager B, **When** either Manager subsequently attempts to view that teacher's profile, **Then** Manager B can view it and Manager A can no longer.

---

### User Story 4 - A Teacher Views Their Own Profile, Read-Only (Priority: P3)

A Teacher opens their own profile from the mobile app — name, contact details, HLS-offered salary, and status — but has no way to change any of it.

**Why this priority**: Valuable for transparency and reducing "what does HLS have on file for me" questions, but it's the smallest slice of value here since Admin (Story 1-2) and Director/Manager (Story 3) already cover the operationally critical paths.

**Independent Test**: Can be fully tested by having a Teacher open their own profile and confirming it shows their current details with no edit capability offered anywhere, and confirming they cannot open another teacher's profile.

**Acceptance Scenarios**:

1. **Given** a Teacher is logged in, **When** they open their profile, **Then** they see their own current name, contact details, salary, and status.
2. **Given** a Teacher is viewing their profile, **When** they look for a way to edit any field, **Then** no edit capability is offered.
3. **Given** a Teacher is logged in, **When** they attempt to view another teacher's profile, **Then** access is denied.

---

### Edge Cases

- What happens when a Manager with no teachers currently assigned to them attempts to view any teacher's profile? Access is denied, the same as for any teacher not currently assigned to them.
- What happens when a teacher who previously exited returns to HLS? Their existing profile is reactivated by changing its status again (e.g., "exited" back to "in training"), rather than a new, separate profile being created for the same person.
- What happens when Admin "updates" a field to the exact value it already holds? The profile is unaffected either way; no user-visible behavior depends on whether this produces a new history entry.
- What happens when a teacher's assigned Manager changes while a Manager already has that profile open? The change in access takes effect based on who is currently assigned at the time of each request, not at the time a screen was first opened.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST allow Admin to create a new teacher profile capturing name, contact details, HLS-offered monthly salary, and an initial status.
- **FR-002**: The system MUST allow Admin to update an existing teacher's contact details and HLS-offered monthly salary.
- **FR-003**: The system MUST allow Admin to change a teacher's status among at least: in training, active, on leave, and exited.
- **FR-004**: The system MUST NOT allow a teacher profile to be deleted; a teacher who has left HLS is represented by status "exited," never by removing the record.
- **FR-005**: The system MUST preserve, for every change to a teacher's status, contact details, or salary, the value before the change, the value after, and when the change happened — a correction MUST always be retrievable as history, never as a silent overwrite of what was there before.
- **FR-006**: The system MUST allow a Director to view any teacher's profile.
- **FR-007**: The system MUST allow a Manager to view the profile of a teacher currently assigned to them, and MUST deny a Manager access to the profile of a teacher not currently assigned to them.
- **FR-008**: The system MUST allow a Teacher to view their own profile in full.
- **FR-009**: The system MUST NOT allow a Teacher to edit any field of their own profile, and MUST NOT allow a Teacher to view another teacher's profile.
- **FR-010**: The system MUST restrict the ability to create or edit any teacher profile to the Admin role — Director, Manager, and Teacher have view-only access at most, and only to the profiles this spec grants them.
- **FR-011**: The system MUST make each teacher's profile available as reference data other modules can read, without those modules needing to duplicate or re-enter it themselves.

### Key Entities *(include if feature involves data)*

- **Teacher Profile**: One record per teacher. Captures name, contact details, HLS-offered monthly salary, and current status (in training / active / on leave / exited). Never deleted; a teacher's departure and any later return are both represented as status changes on the same record. Bank details for payout are explicitly out of scope for this module (see Assumptions) — they will be added, likely by a dedicated payout-details feature, once Payroll needs them.
- **Profile Change Record**: One entry per change made to a Teacher Profile's status, contact details, or salary. Captures what changed, the value before and after, and when it happened, so a profile's full history remains visible rather than only its current state.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of teacher profiles created by Admin are immediately retrievable by a Director with the exact details entered.
- **SC-002**: 100% of changes to a teacher's status, contact details, or salary produce a retrievable record of the prior value and when it changed, verified through testing.
- **SC-003**: A Manager can view profiles for currently-assigned teachers and is denied access to non-assigned teachers' profiles in 100% of tested cases.
- **SC-004**: A Teacher can retrieve their own profile but has no means of modifying any field, in 100% of tested cases.
- **SC-005**: Zero teacher profiles are ever removed from the system once created, regardless of status changes.

## Assumptions

- **Bank details for payout are excluded from this module's scope for now** (scope correction, 2026-09-22) — the original description included them, but they are deferred to a later, dedicated addition (likely alongside Payroll, when a payout-details feature is actually needed). Every mention of "bank details" in the original input above should be read in light of this correction; no bank-related field, requirement, or acceptance scenario is in scope here.
- "System Assistant/Admin" refers to the single Admin role already established for this system; there is no separate System Assistant role to distinguish.
- Only "in training," "active," "on leave," and "exited" are in scope as status values here. An earlier "Recruited" pre-training stage, mentioned for a future Recruitment module, is out of scope for this module and will be reconciled once that module exists.
- A Manager's view access is based on whichever teachers are currently assigned to them at the moment of each request — consistent with how manager-scoped visibility already works elsewhere in the system. Reassignment immediately changes who can view a given teacher's profile; no prior-Manager grandfathering is included.
- A teacher's contact details recorded here are administratively independent of the phone number they use to log in — they're commonly the same value in practice, but this module does not assume or enforce that they must be.
- This module owns and exposes teacher profile data only; it does not implement Attendance, Payroll, Training, or Substitution — those modules will read this data once they exist.
