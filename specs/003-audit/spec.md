# Feature Specification: Audit — Change History, User Activity, Login History

**Feature Branch**: `003-audit`

**Created**: 2026-09-24

**Status**: Draft

**Input**: User description: "003-audit: Append-only audit store (change history, user activity, login history) with an AUDIT menu for Admin and System roles exposing Audit Logs, User Activity, Login History, and Change History. Depends on 001-identity-access and 002-access-model-app-shell, both already implemented."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Review Login History (Priority: P1)

An Admin or System user investigating a suspicious sign-in, a locked-out account, or a user's
complaint about being unable to log in opens Login History and finds every sign-in attempt
(successful or failed, by password or one-time code) for the account in question, with who, when,
how, and the result.

**Why this priority**: Login events already exist (spec 001 emits them) and are the most
immediately actionable security data. This is the smallest slice that proves the audit store, the
AUDIT menu, and role-gating end to end.

**Independent Test**: Sign in successfully, then attempt a failed sign-in, as any user; confirm an
Admin can find both entries in Login History filtered by that user, while a Manager/Teacher/Director
cannot reach the screen at all.

**Acceptance Scenarios**:

1. **Given** a user has signed in successfully and later failed a sign-in, **When** an Admin opens
   Login History and filters by that user, **Then** both events appear with timestamp, method
   (password/OTP), and outcome (success/failure).
2. **Given** no filters are applied, **When** an Admin opens Login History, **Then** the most recent
   sign-in events across all users appear first, paginated.
3. **Given** a Manager, Teacher, or Director is signed in, **When** they attempt to reach Login
   History (menu or direct link), **Then** the menu item is not shown and the direct route shows
   "not authorized."

---

### User Story 2 - Review Change History (Priority: P2)

An Admin, Director, or System user reviewing a permission dispute or a compliance question opens
Change History and finds exactly what changed on a governed record (for example, a role→permission
matrix edit): who changed it, when, and the value before and after.

**Why this priority**: Change auditing is the constitution's core compliance guarantee
(Principle I) and the second data source already available (spec 002 emits permission-matrix
change events), but it is less urgent day-to-day than login investigation.

**Independent Test**: Edit one cell of the role→permission matrix as an Admin; confirm the exact
before/after values and the editing Admin's identity appear in Change History, independent of
Login History or User Activity being populated.

**Acceptance Scenarios**:

1. **Given** an Admin toggles one permission in the role→permission matrix, **When** they open
   Change History afterward, **Then** one new entry shows the role, module, action, the prior
   value, the new value, the actor, and the timestamp.
2. **Given** multiple changes were made by different actors, **When** filtering Change History by
   actor, **Then** only that actor's changes appear.

---

### User Story 3 - Review User Activity (Priority: P2)

An Admin or System user reviewing an account's recent lifecycle wants a chronological feed of
significant account actions distinct from routine sign-ins and field-level edits: password resets,
sessions ended, lockouts, and activation/deactivation.

**Why this priority**: Rounds out the audit picture for account-level investigations (e.g., "was
this account deactivated, and by whom, before or after the disputed login?"), building on the same
store as User Story 1.

**Independent Test**: Trigger a password reset and a session end for a test user; confirm both
appear in User Activity for that user, independent of any permission-matrix edits.

**Acceptance Scenarios**:

1. **Given** a user completes a password reset, **When** an Admin opens User Activity filtered by
   that user, **Then** an entry shows the action ("password reset completed"), the actor (the user
   themselves, or an Admin if performed on their behalf), and the timestamp.
2. **Given** a user's account is deactivated, **When** an Admin opens User Activity for that user,
   **Then** a "deactivated" entry shows the actor who deactivated it and when.

---

### User Story 4 - Browse the Unified Audit Log (Priority: P3)

An Admin or System user wants one combined, searchable, exportable timeline across login history,
change history, and user activity, instead of checking three separate screens, for a broad
investigation or an export to share with an auditor.

**Why this priority**: A convenience and reporting layer over Stories 1–3; valuable but not
required for the underlying audit guarantee, so it lands last.

**Independent Test**: With entries already present from Stories 1–3, open Audit Logs and confirm
all three event types appear together in one chronological list, filterable by type, and
exportable to CSV.

**Acceptance Scenarios**:

1. **Given** at least one login event, one change event, and one activity event exist, **When** an
   Admin opens Audit Logs with no filter, **Then** all three appear together in timestamp order.
2. **Given** a date range and event-type filter are applied, **When** the Admin exports the
   filtered results, **Then** the downloaded CSV matches exactly what is shown on screen.

---

### Edge Cases

- What happens when an audit query (any of the four views) matches no entries? System shows an
  empty state, not an error.
- How does the system handle a query for a user who has since been deactivated? Their historical
  audit entries remain visible and intact; deactivation does not hide or remove prior history.
- What happens when two changes to different records occur at effectively the same timestamp?
  Both are recorded as independent entries; the list orders by timestamp then a stable tie-breaker
  so ordering is deterministic on repeated queries.
- How does the system handle an export request spanning a very large result set? The export is
  bounded by the same filters (e.g., a required date range) applied on screen, so it is never an
  unbounded full-table dump.
- What happens if someone (including Admin or System) attempts to edit or delete an audit entry
  directly (e.g., via a raw API call)? The request is refused; audit entries have no update or
  delete capability at all.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST record every sign-in attempt, successful or failed, by password or
  one-time code, as an immutable Login History entry capturing the actor's identifier (masked
  phone), the method used, the outcome, and the timestamp.
- **FR-002**: The system MUST record every change to the role→permission matrix as an immutable
  Change History entry capturing the actor, the affected role/module/action, the prior value, the
  new value, and the timestamp.
- **FR-003**: The system MUST record significant account lifecycle actions — password reset
  requested, password reset completed, session ended, account locked, account unlocked, account
  deactivated — as immutable User Activity entries capturing the actor, the affected user, the
  action, and the timestamp. Account reactivation is deferred: no reactivation capability exists
  yet anywhere in the system (spec 004 adds one), so this spec records deactivation only; the
  action type reserves space for reactivation so spec 004 needs no schema change to emit it.
- **FR-004**: Audit entries (Login History, Change History, User Activity) MUST NOT be editable or
  deletable through any capability, including by Admin or System; a correction MUST appear only as
  a new entry, never as a modification of an existing one.
- **FR-005**: Admin and System users MUST be able to view, search, and filter Login History by
  user, date range, method, and outcome.
- **FR-006**: Admin and System users MUST be able to view, search, and filter Change History by
  actor, affected record/entity, and date range, and see the prior and new value for each change.
- **FR-007**: Admin and System users MUST be able to view, search, and filter User Activity by
  actor, affected user, action type, and date range.
- **FR-008**: Admin and System users MUST be able to view a combined Audit Logs feed merging Login
  History, Change History, and User Activity into one chronological list, filterable by event type
  and date range, and searchable by user/actor.
- **FR-009**: Admin and System users MUST be able to export the current filtered results of any of
  the four audit views (Audit Logs, Login History, Change History, User Activity) to CSV.
- **FR-010**: The system MUST deny access to all four audit views and their underlying data — menu,
  screen, and API — to Director, Manager, and Teacher, consistent with the default role access
  matrix.
- **FR-011**: All four audit views MUST present results with pagination, sorting by most-recent
  first, and the filters described in FR-005–FR-008, consistent with other list screens in the
  system.
- **FR-012**: The system MUST retain all audit entries indefinitely; no capability in this feature
  purges, archives, or otherwise deletes audit history.

### Key Entities

- **Login History Entry**: an immutable record of one sign-in attempt — actor (masked phone),
  method (password/OTP), outcome (success/failure/locked/etc.), and timestamp.
- **Change History Entry**: an immutable record of one field-level change to a governed record —
  actor, entity/record affected, the field, the prior value, the new value, and timestamp.
- **User Activity Entry**: an immutable record of one account lifecycle action — actor, the user
  affected, the action type, optional detail, and timestamp. Action types: password reset
  requested/completed, session ended, account locked/unlocked, account deactivated (reactivation
  reserved for spec 004, per FR-003).
- **Audit Log Entry**: the unifying view across the three entities above, used for the combined,
  filterable, exportable Audit Logs screen.

## Role & Permission Impact *(mandatory — Constitution Principles II–IV)*

| Role     | Menu (section → item)                                                         | Default actions | Data scope |
| -------- | ------------------------------------------------------------------------------ | ---------------- | ---------- |
| Admin    | AUDIT → Audit Logs, Login History, Change History, User Activity               | View, Export      | Org-wide |
| Director | none                                                                            | —                 | None |
| Manager  | none                                                                            | —                 | None |
| Teacher  | none                                                                            | —                 | None |
| System   | AUDIT → Audit Logs, Login History, Change History, User Activity               | View, Export      | Org-wide (audit data only; System still sees no business data) |

This feature does not touch Role & Permission Management itself; it only reads what that screen
(spec 004) will later write, via the Change History entries spec 002 already emits.

**New permission keys**: `audit.logs.view`, `audit.logs.export`, `audit.loginHistory.view`,
`audit.loginHistory.export`, `audit.changeHistory.view`, `audit.changeHistory.export`,
`audit.userActivity.view`, `audit.userActivity.export`.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: An Admin can locate a specific login event for a named user within 3 filter actions
  (e.g., select user, select date range, read result).
- **SC-002**: 100% of role→permission matrix edits made during testing appear in Change History
  within 5 seconds of the edit.
- **SC-003**: 100% of sign-in attempts (success and failure) generated during testing appear in
  Login History with no gaps or duplicates.
- **SC-004**: Every audit view returns a clear empty state (not an error) for a filter combination
  with no matches, and remains responsive when paginating through at least 10,000 entries.
- **SC-005**: 100% of attempts by Director, Manager, or Teacher accounts to reach an audit screen or
  its underlying data, by menu or direct link, are refused.
- **SC-006**: 100% of exported CSV rows match the on-screen filtered results at the time of export.

## Assumptions

- This spec subscribes to the domain events spec 001 (`LoginHistoryRecorded`) and spec 002
  (`PermissionMatrixChanged`) already publish specifically for the audit module, per their code
  comments. It does not require re-deriving login or permission-change data another way.
- This spec adds the additional event publishing identity needs for User Activity (password reset
  requested/completed, session ended, account locked/unlocked, account deactivated), following the
  same publish-only pattern identity already uses — identity does not depend on the audit module,
  only the reverse.
- No retention, archival, or purge policy applies; every audit entry is kept indefinitely
  (Constitution Principle I).
- Audit views in this spec are read and export only; annotating, commenting on, or resolving audit
  entries is out of scope.
- Export uses the CSV format already established elsewhere in the system (Additional Constraints);
  PDF export is out of scope for audit.
- Identity's existing `login_history_event` table (spec 001) is decommissioned by this spec once
  this feature's Login History consumer is proven working end-to-end: nothing in the codebase reads
  that table today (only `LoginHistoryPublisher` writes it), so removing the write is a clean cutover
  with no data-migration need. This is required by Constitution Principle VII ("No module writes
  audit history to its own tables") — see plan.md's Constitution Check and tasks.md's decommission
  task.
