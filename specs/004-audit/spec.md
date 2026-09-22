# Feature Specification: Audit Trail

**Feature Branch**: `004-audit`

**Created**: 2026-09-22

**Status**: Draft

**Input**: User description: "Build a single, append-only audit trail that every other module writes to whenever a financial or attendance-affecting record is created, changed, or corrected. Each entry must capture what changed, who changed it, when, and the value before and after — for attendance edits, payroll corrections, school payment recording, expense approvals, and substitution assignments alike (Requirements §3, §5, §6, §9/9a; Constitution Principle I). Directors and Admins must be able to view the full history of any record. Entries can never be edited or deleted once written, even by an Admin — corrections must appear as new entries, not overwrites."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Every Financial or Attendance Change Is Automatically Recorded (Priority: P1)

As any module in the system creates, changes, or corrects a financial or attendance-affecting record — a teacher's attendance, a payroll/payout figure, a school payment, an expense claim, or a substitution assignment — the change is captured as a permanent entry showing what changed, who changed it, when, and the value before and after, without that module's users having to do anything extra to make it happen.

**Why this priority**: This is the foundation the entire feature exists for — Constitution Principle I requires every financial/attendance change to be traceable, and nothing else in this feature (viewing history, guaranteeing immutability) has any value if changes aren't captured in the first place.

**Independent Test**: Can be fully tested by making a change to an attendance, payroll, school payment, expense, or substitution record through its owning module and confirming a matching audit entry exists with the correct actor, timestamp, and before/after values — independent of any viewing UI.

**Acceptance Scenarios**:

1. **Given** a Manager changes a teacher's attendance status for a given day from Present to Leave, **When** the change is saved, **Then** an entry is recorded showing the manager as actor, the time of the change, "Present" as the value before, and "Leave" as the value after, linked to that attendance record.
2. **Given** an Admin/Director triggers a payroll run that recalculates a teacher's net salary, **When** the recalculated payout differs from a previous run for the same month, **Then** an entry is recorded showing the old and new payout figures, the actor who triggered the recalculation, and when it happened.
3. **Given** a Manager records a payment received from a school, **When** the payment is saved, **Then** an entry is recorded capturing the payment's details as the "after" value with no "before" value (a new record), the manager as actor, and the timestamp.
4. **Given** an Admin approves an expense claim above the approval threshold, **When** the approval is saved, **Then** an entry is recorded capturing the status change from "Pending" to "Approved", the approving actor, and the timestamp.
5. **Given** a Manager assigns a substitute teacher to cover another teacher's absence, **When** the assignment is saved, **Then** an entry is recorded capturing the substitution's details, the assigning manager, and the timestamp.

---

### User Story 2 - Directors and Admins Can View a Record's Full History (Priority: P2)

A Director or Admin who is looking at any financial or attendance-affecting record — for example a specific attendance entry, a teacher's payslip for a month, a school payment, an expense claim, or a substitution assignment — can pull up every entry ever recorded against that record, in chronological order, and see exactly what changed, who changed it, and when.

**Why this priority**: Recording history has no operational value until someone can actually use it to answer "what happened here and who did it" — this is the payoff of User Story 1, and it's what Directors/Admins will reach for during reconciliation, disputes, or investigating a discrepancy.

**Independent Test**: Can be fully tested by taking a record with a known sequence of prior changes, opening its history as a Director or Admin, and confirming every change appears in order with the correct actor, timestamp, and before/after values — independently of how those entries were originally created.

**Acceptance Scenarios**:

1. **Given** a record with three recorded changes over time, **When** a Director opens that record's history, **Then** all three entries are shown in chronological order, each with its actor, timestamp, and before/after values.
2. **Given** a record with no changes since its creation, **When** an Admin opens that record's history, **Then** the single "created" entry is shown.
3. **Given** a Manager (not a Director or Admin) attempts to open a record's history, **When** the request is made, **Then** access is denied.
4. **Given** a record has since been archived or deactivated in its owning module, **When** a Director opens its history, **Then** the full history is still shown, unaffected by the record's current status.

---

### User Story 3 - History Can Never Be Altered, Only Added To (Priority: P3)

Once an entry has been written, nobody — including an Admin — can change or remove it. If a past change turns out to have been wrong, the correction is captured as a brand-new entry that points back at the same record, so the full sequence of "what we believed at the time" remains visible rather than being erased.

**Why this priority**: This is what makes the history in User Story 1/2 trustworthy rather than just another editable field — it's the guarantee that lets a Director rely on the audit trail during a dispute or reconciliation, but it only matters once entries exist and can be viewed, so it builds on the first two stories.

**Independent Test**: Can be fully tested by attempting to modify or remove an existing entry through every available interface and confirming each attempt is rejected while the original entry remains intact, then separately confirming that applying a correction produces an additional entry rather than changing the original.

**Acceptance Scenarios**:

1. **Given** an existing entry, **When** any user, including an Admin, attempts to edit its values, **Then** the attempt is rejected and the entry is unchanged.
2. **Given** an existing entry, **When** any user, including an Admin, attempts to delete it, **Then** the attempt is rejected and the entry remains in the record's history.
3. **Given** a previously recorded value turns out to have been entered incorrectly, **When** the correct value is entered, **Then** a new entry is added showing the correction, and the original (incorrect) entry remains visible and unchanged in the record's history.

---

### Edge Cases

- What happens if the underlying change (e.g., saving an attendance edit) succeeds in its own module but the audit entry fails to save? The triggering change must not be allowed to complete without its audit entry — an unaudited financial or attendance change is not an acceptable outcome.
- What happens when two changes to the same record are saved at nearly the same moment by two different actors? Both changes must produce their own entry, and the record's history must show both in a clear, unambiguous order.
- What happens when a bulk operation (e.g., a payroll run recalculating pay for 60 teachers at once) changes many records in one action? Each individual record's change is captured as its own entry, so each record's history stays complete and specific to it.
- What happens when someone looks up the history of a record that doesn't exist or was never audited? The system indicates no history was found, rather than showing an empty history as if the record had simply never changed.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST provide a single append-only store of history that is the only place financial- or attendance-affecting changes are recorded — no other part of the system may keep a separate, competing history for these records.
- **FR-002**: The system MUST record an entry whenever a financial- or attendance-affecting record is created, changed, or corrected by any part of the system, including at minimum attendance, payroll/payout, school payment, expense, and substitution-assignment records.
- **FR-003**: Each entry MUST capture, at minimum: which record it relates to, what changed, who made the change, when it was made, the value before the change, and the value after the change.
- **FR-004**: The system MUST NOT allow the record-affecting change that triggers an entry to complete unless its entry is also successfully recorded.
- **FR-005**: The system MUST prevent any entry from being edited or deleted after it is written, regardless of the role or authority of the person attempting it.
- **FR-006**: The system MUST require that a correction to previously recorded information be captured as a new entry rather than as a modification of an existing one.
- **FR-007**: The system MUST allow Directors and Admins to retrieve the complete, chronologically ordered history of any financial- or attendance-affecting record.
- **FR-008**: The system MUST deny access to a record's history for any role other than Director or Admin.
- **FR-009**: The system MUST continue to show a record's full history even after the record itself has been archived, deactivated, or otherwise made inactive in its owning module.
- **FR-010**: The system MUST attribute every entry to a specific, identified actor — an unattributable or anonymous change MUST NOT be recorded.
- **FR-011**: The system MUST preserve an unambiguous chronological order of entries for a given record, even when multiple entries are recorded within the same second.

### Key Entities *(include if feature involves data)*

- **Audit Entry**: A single, permanent record of one change to one financial- or attendance-affecting record. Captures the record it relates to (and which module owns that record), a description of what changed, the value before, the value after, the actor who made the change, and when it happened. Never modified or removed once written.
- **Auditable Record**: Any record from another module (attendance entry, payroll/payout, school payment, expense claim, substitution assignment, and similarly sensitive records from future modules) whose creation, change, or correction must produce an Audit Entry. Owned entirely by its own module; the audit module only stores history about it, never the record itself.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of creations, changes, and corrections made to attendance, payroll, school payment, expense, and substitution-assignment records during testing produce a matching, retrievable audit entry.
- **SC-002**: A Director or Admin can retrieve the complete history of any record and see every prior change within a few seconds of requesting it.
- **SC-003**: Zero attempts to edit or delete an existing audit entry succeed, across every role including Admin, verified through testing.
- **SC-004**: 100% of corrections to previously recorded financial or attendance information result in an additional entry rather than a change to an existing one, verified through testing.
- **SC-005**: A user without Director or Admin access is unable to retrieve any record's history in 100% of attempts, verified through testing.

## Assumptions

- Only the Director and Admin roles can view audit history in this feature; whether Managers can view history scoped to their own teachers/schools is left for a future iteration, since the feature description names only Director and Admin as viewers.
- The audit module records one entry per individual record affected by a change, including within bulk operations like a payroll run — a single bulk action that touches many records produces many entries, one per affected record, not one combined entry.
- The audit-write and the business change it accompanies are treated as a single unit: if the audit entry cannot be saved, the business change is not saved either. This favors a visible failure over a silent, unaudited success, consistent with Constitution Principle I.
- Viewing history in this feature is scoped to looking up a specific record's history (e.g., "show me everything that happened to this attendance entry"); broader browsing or filtering of audit entries across records, actors, or time ranges (e.g., "show me everything Manager X changed this month") is not included and can be added later if needed.
- One-time historical data migration from the existing spreadsheet is out of scope for this feature and will be addressed separately when the import tool is built.
- Modules that don't yet exist in the system (payroll, school payment, expense, substitution, training) will call into this audit capability once they are built; this feature defines and builds the capability itself, not the other modules' use of it.
