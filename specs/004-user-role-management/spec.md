# Feature Specification: User & Role Management

**Feature Branch**: `004-user-role-management`

**Created**: 2026-10-03

**Status**: Draft

**Input**: User description: "004-user-role-management: SYSTEM / SYSTEM CONFIGURATION → User Management (create user, assign one or more of the 5 fixed roles, activate/deactivate, reset password) and Role & Permission Management (edit the role→permission matrix seeded in 002, every edit audited via 003's Change History, with a last-admin safeguard preventing the only remaining Admin from being deactivated or losing Admin). Restricted to Admin and System roles. Depends on 002-access-model-app-shell and 003-audit, both already implemented."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Create a New Staff or Teacher Account (Priority: P1) 🎯 MVP

An Admin or System user who needs to onboard a new staff member or teacher opens User Management
and creates their account: display name, phone number, one or more of the five fixed roles, and
optionally a username, email, and an initial password (or leaves it OTP-only).

**Why this priority**: Until this exists, every account in the system has to be created by a
developer running internal code directly (spec 001's `UserAdminService` has no HTTP endpoint or
screen). This is the one capability that unblocks every other onboarding flow.

**Independent Test**: As Admin, create a new user with the Manager role and a phone number; confirm
the new user can immediately sign in (by password if one was set, or by OTP) and is recognized with
exactly that role.

**Acceptance Scenarios**:

1. **Given** an Admin is on User Management, **When** they submit a new user's name, phone, and one
   role, **Then** the account is created and appears in the user list with that role and active
   status.
2. **Given** an Admin assigns two roles (e.g., Manager and Director) to a new user, **When** the
   user signs in, **Then** they are recognized by both roles with no role picker.
3. **Given** a phone number already belongs to an existing user, **When** an Admin tries to create
   another account with that phone number, **Then** the system refuses with a clear duplicate
   message and creates nothing.

---

### User Story 2 - Find and Review Existing Users (Priority: P1)

An Admin or System user opens User Management and searches or browses the full list of user
accounts to find one by name or phone, and sees each one's roles and active/inactive status at a
glance before taking any action on it.

**Why this priority**: Every other story in this spec (role changes, deactivation, reset) starts
from finding the right user first; without a list/search there is no way to reach them.

**Independent Test**: With several seeded users of different roles, search by a partial name and by
a phone number and confirm the matching user(s) appear with their current roles and status;
searching for a name that matches no one shows an empty state, not an error.

**Acceptance Scenarios**:

1. **Given** multiple users exist, **When** an Admin searches by part of a display name, **Then**
   only matching users appear, each showing display name, phone, roles, and active/inactive status.
2. **Given** no user matches the search, **When** the Admin views the results, **Then** an empty
   state is shown, not an error.
3. **Given** a Director, Manager, or Teacher is signed in, **When** they attempt to reach User
   Management (menu or direct link), **Then** the menu item is not shown and the direct route shows
   "not authorized."

---

### User Story 3 - Change a User's Roles (Priority: P2)

An Admin or System user whose organization's needs changed opens an existing user's account and
adds or removes one or more of the five fixed roles, without having to recreate the account.

**Why this priority**: Roles change over time (a Teacher is promoted to Manager, a Manager also
takes on Director duties); recreating accounts to reflect this would lose history and session
continuity.

**Independent Test**: Add the Director role to an existing Manager-only user; confirm they are
recognized by both roles on their next sign-in. Remove one of the two roles; confirm they are
recognized by only the remaining role afterward.

**Acceptance Scenarios**:

1. **Given** a user holds one role, **When** an Admin adds a second role, **Then** the user's next
   sign-in recognizes both roles.
2. **Given** a user holds two roles, **When** an Admin removes one, **Then** the user's next
   sign-in recognizes only the remaining role.
3. **Given** a user is the only account holding the Admin role, **When** anyone attempts to remove
   their Admin role, **Then** the system refuses with a clear explanation and the role is not
   removed (the last-admin safeguard; see User Story 6).

---

### User Story 4 - Deactivate and Reactivate a User (Priority: P2)

An Admin or System user deactivates a user who has left or is on long-term leave, immediately
shutting them out of every sign-in method and ending their active sessions; later, if the person
returns, the Admin or System user reactivates the same account rather than recreating it.

**Why this priority**: Spec 001 already enforces deactivation at the sign-in/session layer
internally; this story is what finally lets an Admin or System user *trigger* that enforcement
through a real screen, and adds the matching reactivation path spec 003 reserved space for.

**Independent Test**: Deactivate an active user with two open sessions; confirm both sessions end
and no sign-in method succeeds for them afterward. Reactivate the same user; confirm their original
roles are intact and they can sign in again.

**Acceptance Scenarios**:

1. **Given** an active user has open sessions, **When** an Admin deactivates them, **Then** all of
   that user's sessions end immediately and no further sign-in attempt succeeds for them.
2. **Given** a deactivated user, **When** an Admin reactivates them, **Then** they can sign in again
   and are recognized by exactly the roles they held before deactivation.
3. **Given** a user is the only account holding the Admin role, **When** anyone attempts to
   deactivate them, **Then** the system refuses with a clear explanation (the last-admin safeguard;
   see User Story 6).

---

### User Story 5 - Reset Another User's Password (Priority: P2)

An Admin or System user helps a locked-out or forgetful user by setting a new password directly on
their account, without needing that user's involvement (distinct from spec 001's self-service reset
by SMS/email, which the user triggers for themselves).

**Why this priority**: A common support request ("I can't sign in and I don't have my phone handy")
that currently has no answer short of a developer touching the database.

**Independent Test**: As Admin, set a new password for a user with an existing active session;
confirm that session ends immediately and the user can sign in with the new password but not the
old one.

**Acceptance Scenarios**:

1. **Given** a user account, **When** an Admin sets a new password for it, **Then** the user's
   existing sessions all end and any account lockout is cleared, exactly as a self-service reset
   would.
2. **Given** a user who previously had no password (OTP-only), **When** an Admin sets one, **Then**
   the user can subsequently sign in with either method.
3. **Given** a new password that is shorter than the system's minimum length or equal to the user's
   phone number, **When** an Admin submits it, **Then** the system refuses with the same password
   policy message spec 001 already uses for self-service resets.

---

### User Story 6 - The Last Admin Cannot Be Locked Out (Priority: P2)

The system refuses any single action — deactivation or role removal — that would leave zero active
user accounts holding the Admin role, so the organization is never locked out of its own user and
permission administration.

**Why this priority**: Without this safeguard, a mistake in User Stories 3 or 4 could permanently
lock everyone out of User Management and Role & Permission Management, with no account left able to
fix it.

**Independent Test**: With exactly one active Admin account, attempt to deactivate it and,
separately, attempt to remove its Admin role; confirm both attempts are refused with a clear
message and the account's status/roles are unchanged. Create a second Admin account; confirm the
same two attempts now succeed on the first account.

**Acceptance Scenarios**:

1. **Given** exactly one active user holds the Admin role, **When** anyone attempts to deactivate
   that user, **Then** the system refuses and the user remains active.
2. **Given** exactly one active user holds the Admin role, **When** anyone attempts to remove their
   Admin role (with or without removing other roles at the same time), **Then** the system refuses
   and their Admin role remains.
3. **Given** two active users hold the Admin role, **When** one of them deactivates the other or
   removes the other's Admin role, **Then** the action succeeds, because at least one active Admin
   remains afterward.

---

### Edge Cases

- What happens when a search matches no users? An empty state is shown, not an error (User Story
  2).
- What happens when an Admin tries to create a user with a phone number already in use? Refused
  with a clear duplicate message; nothing is created (User Story 1, reusing spec 001's existing
  validation).
- What happens when the acting Admin deactivates or resets the password of their *own* account?
  Allowed the same as acting on any other user (their own session ends immediately for a
  self-targeted deactivation or reset), unless it would trip the last-admin safeguard.
- What happens when removing a role would leave a user with zero roles at all? Refused with a clear
  message — every user account must hold at least one of the five fixed roles.
- What happens when the last-admin safeguard and a role-removal happen in the same request (e.g.,
  removing Admin and Director together from the last Admin)? The whole request is refused as one
  unit; no partial role change is applied.
- What happens when System reactivates a deactivated Admin account? Allowed; reactivation never
  triggers the last-admin safeguard, since it only ever increases the number of active Admins.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Admin and System users MUST be able to create a new user account with a display name,
  phone number, one or more of the five fixed roles, and optionally a username, email, and an
  initial password.
- **FR-002**: Admin and System users MUST be able to view, search, and filter the full list of user
  accounts by name and phone, seeing each one's display name, phone, roles, and active/inactive
  status.
- **FR-003**: Admin and System users MUST be able to add or remove one or more role assignments on
  an existing user account, except where doing so would leave that user holding zero roles.
- **FR-004**: Admin and System users MUST be able to deactivate an active user account, which
  immediately ends all of that user's active sessions and refuses any further sign-in by any method
  (reusing spec 001's existing deactivation enforcement).
- **FR-005**: Admin and System users MUST be able to reactivate a previously deactivated user
  account, restoring exactly the role assignments it held at the time of deactivation and allowing
  sign-in again.
- **FR-006**: Admin and System users MUST be able to set a new password directly on another user's
  account ("admin-triggered reset"), which ends all of that user's existing sessions and clears any
  account lockout, under the same password policy spec 001 already enforces for self-service resets.
- **FR-007**: The system MUST refuse, as a single rejected action with no partial effect, any
  deactivation or role-removal request that would leave zero active user accounts holding the Admin
  role (the last-admin safeguard), with a clear explanation of why it was refused.
- **FR-008**: Every user-management action (create, role change, deactivate, reactivate,
  admin-triggered password reset) MUST be recorded as an immutable User Activity entry via spec
  003's audit module, capturing the actor, the affected user, the action, and the timestamp.
- **FR-009**: The system MUST deny access to User Management — menu, screen, and API — to Director,
  Manager, and Teacher, consistent with the default role access matrix.
- **FR-010**: All four prior spec 001 identity rules continue to apply unchanged to accounts created
  or edited here: duplicate-phone rejection, the five-role-only constraint, the password policy, and
  deactivation's sign-in/session enforcement.

### Key Entities

- **User Account**: the existing `AppUser`/`RoleAssignment` pair from spec 001 — this spec adds no
  new entity, only new ways to create and edit them (role changes, deactivate/reactivate, an
  admin-triggered password reset) through a real screen and API instead of internal-only code.

## Role & Permission Impact *(mandatory — Constitution Principles II–IV)*

| Role     | Menu (section → item)                                                    | Default actions | Data scope |
| -------- | ------------------------------------------------------------------------- | ---------------- | ---------- |
| Admin    | SYSTEM → User Management (new); SYSTEM → Role & Permissions (existing, unchanged) | View, Create, Edit | Org-wide |
| Director | none for User Management; SYSTEM → Role & Permissions (existing, unchanged) | — for User Management | None for User Management |
| Manager  | none                                                                       | —                 | None |
| Teacher  | none                                                                       | —                 | None |
| System   | SYSTEM CONFIGURATION → User Management (new); SYSTEM CONFIGURATION → Role & Permissions (existing, unchanged) | View, Create, Edit | Org-wide for user accounts (identity data, not business data) |

Role & Permission Management itself (editing the role→permission matrix) was already fully
delivered by spec 002, with its own audit trail (spec 003) and its own matrix-level safeguard
(preventing the last Admin/Director/System role from losing the ability to manage the matrix). This
spec does not change that capability; it adds the separate, user-level last-admin safeguard
described in User Story 6, which protects against zero active Admin *users*, independent of whether
the Admin *role* still holds its permissions.

**New permission keys**: `userManagement.view`, `userManagement.create`, `userManagement.edit` —
seeded `true` for Admin and System only, `false` for Director, Manager, and Teacher, matching the
Constitution's default role access matrix row for User Management.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: An Admin can create a new user account and have that person sign in successfully
  within 2 minutes of account creation.
- **SC-002**: An Admin can locate a specific existing user by name or phone and deactivate their
  account within 3 actions (search, select, deactivate).
- **SC-003**: 100% of attempts by Director, Manager, or Teacher to reach User Management, by menu
  or direct link, are refused.
- **SC-004**: 100% of attempts to deactivate or remove the Admin role from the last active Admin
  account are refused, with no partial change ever applied.
- **SC-005**: 100% of user-management actions performed during testing (create, role change,
  deactivate, reactivate, password reset) appear in User Activity within 5 seconds.

## Assumptions

- Role & Permission Management (editing the role→permission matrix) is already fully implemented by
  specs 002 and 003 — including its own audit trail and its own matrix-level safeguard for the
  Admin/Director/System roles' ability to manage the matrix — and is reused unchanged by this spec.
  This spec's only new safeguard is the user-level one in User Story 6 (zero active Admin *users*),
  which is independent of that existing role-level safeguard.
- An admin-triggered password reset (User Story 5) sets a new password directly on the target
  account, reusing spec 001's existing password-policy validation and its existing
  session-termination/lockout-clearing behavior, rather than sending the target user an OTP code.
  How the new password is then communicated to that user is an operational detail outside this
  system's scope.
- Reactivating a deactivated user (User Story 4) restores exactly the role assignments recorded at
  the time of deactivation, since deactivation only changes the `active` flag and never touches role
  assignments.
- The last-admin safeguard (User Story 6, FR-007) protects only the Admin role, matching this
  spec's description; no equivalent protection exists for the System role.
- A role-assignment change made here takes effect for an already-signed-in user within one access
  token's lifetime (currently 15 minutes, spec 001), consistent with how the existing JWT issuance
  and renewal model already works — there is no requirement to immediately invalidate a live
  session's current access token on a role change.
