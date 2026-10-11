# Feature Specification: System Settings

**Feature Branch**: `feature/011-system-settings`

**Created**: 2026-10-11

**Status**: Draft

**Input**: Roadmap row 011: the System role gets a SYSTEM DASHBOARD (health, users, recent audit) and, under SYSTEM
CONFIGURATION, a System Settings screen and a Security Settings screen (password, lockout, OTP and session policies).
The screens also absorb the three `hls.mobile.*` settings of spec 018. No payroll or expense limits (they stay with
013a and 015).

## Clarifications

### Session 2026-10-11

Decided from the roadmap and the existing code; no stakeholder was asked (recorded as assumptions, see the end).

- Q: Which values are editable at runtime? -> A: Eleven values in two groups. *System*: the minimum Android app
  version, the location wait seconds and the location reuse seconds (the three `hls.mobile.*` settings). *Security*:
  password minimum length; lockout maximum failed attempts and lockout minutes; OTP resend cooldown, maximum
  consecutive requests and request lockout hours; session renewal days and access-token seconds.
- Q: What about the OTP code lifetime (5 minutes), the wrong-code limit (5) and the per-minute request cap (3)? -> A:
  They stay fixed in code. They are security constants whose change is a code review, not a screen edit.
- Q: Where does an unedited value come from? -> A: The deployed configuration default (the `hls.mobile.*` and
  `hls.session.*` properties), then a built-in default. An edit stores an override. The three OTP values already live
  in a table (`otp_policy_settings`); that table is folded into the new store and removed.
- Q: Do edits apply at once? -> A: Yes for new decisions: the next sign-in attempt, OTP request, token issue or
  app-config call reads the new value. Nothing already issued is changed: sessions and access tokens keep their
  expiry, and an already locked account stays locked until its own time ends or an admin unlocks it.
- Q: Who can see and edit? -> A: System and Admin by default (View, Edit). Director has no access by default
  (Constitution Principle II) but the matrix may grant it. No other role can ever hold these grants.
- Q: One screen or two? -> A: Two, as the roadmap names them: System Settings (mobile) and Security Settings.
- Q: What is on the System dashboard? -> A: Health (database, application version, database migration version,
  start time and uptime), users (total, active, inactive, locked now, per role), and the 10 most recent audit events
  (change history and sign-ins), plus a count of failed sign-ins in the last 24 hours.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - See the Health of the System at a Glance (Priority: P1)

The System user opens the dashboard and sees whether the platform is up, how many people use it and what happened
lately, without any business data.

**Why this priority**: it replaces three placeholders the System user sees today and is the landing page of the role.

**Independent Test**: sign in as System; the dashboard shows health, user counts and recent audit events. Sign in as
Teacher; the endpoint answers 403.

**Acceptance Scenarios**:

1. **Given** the database is reachable, **When** System opens the dashboard, **Then** the database shows UP with the
   application version, migration version, start time and uptime.
2. **Given** 12 users of whom 2 are inactive and 1 is locked, **Then** the counts read total 12, active 10, inactive
   2, locked 1, and per-role counts are shown.
3. **Given** a sign-in failure and a permission change happened, **Then** both appear in the recent events,
   newest first, at most 10, with the actor's name.
4. **Given** the caller's roles do not grant View on teacher, school or other business entities, **Then** change
   events of those entity types are left out of the recent events (System never sees business data).

### User Story 2 - Change the Security Policies (Priority: P1) MVP

The System user (or Admin) tunes password length, lockout, OTP throttling and session length without a redeploy.

**Independent Test**: set the lockout attempts to 3; three wrong passwords lock the account. Set the minimum password
length to 12; an 11-character reset password is refused.

**Acceptance Scenarios**:

1. **Given** the defaults, **When** System opens Security Settings, **Then** every value, its unit, its allowed range
   and whether it is the default or has been changed is shown.
2. **Given** a valid change, **When** saved, **Then** the new value applies to the next decision and an audit entry
   records actor, setting, previous value and new value.
3. **Given** any value outside its range or not a whole number, **When** saved, **Then** nothing in that save is
   stored and the screen names each bad field.
4. **Given** the lockout is changed from 5 to 3, **Then** a currently locked account is not unlocked and a signed-in
   session is not ended.
5. **Given** an OTP setting is changed, **Then** the next OTP request is judged by the new value (the cached copy is
   dropped on save).

### User Story 3 - Change the Mobile App Settings (Priority: P2)

System sets the minimum app version and the location timings that the Android app reads before sign-in.

**Independent Test**: raise the minimum version to 2.0.0; the public app-config returns it and an app reporting 1.9.0
is turned away by the version gate. Lower it again and the app is let in.

**Acceptance Scenarios**:

1. **Given** the minimum version is edited, **Then** `/api/v1/mobile/app-config` and the version gate both use it at
   once (the public response keeps its five-minute cache hint).
2. **Given** a malformed version such as "2" or "abc", **Then** the save is refused.
3. **Given** the stored value is never edited, **Then** the deployed `hls.mobile.*` property still decides.

### User Story 4 - Know Who Changed a Setting (Priority: P2)

Every change shows in Change History with the actor, the setting, the old value and the new value, to Admin and System
(and to any role granted the matching View).

**Acceptance Scenarios**:

1. **Given** a save that changes two values, **Then** two entries are written, one per value; a value saved
   unchanged writes none.
2. **Given** a caller without View on the setting group, **Then** that group's entries are hidden from Change History.

### Edge Cases

- Two saves at once: the later wins per value, each is audited with its own before value taken in the same
  transaction.
- A stored value is later outside a range tightened by a release: it is ignored, the next tier applies, and the
  screen shows it as the default (never a crash at sign-in).
- A stored row holds text that is not a number (manual database edit): treated as not set; the default applies.
- A save that sets a value equal to the default stores an explicit override (the value is then pinned even if the
  deployment default changes) and shows as "changed".
- The public app-config must stay reachable without sign-in and must never reveal any security setting.
- The password minimum applies to new passwords only; existing passwords are not invalidated.
- The dashboard is read-only and must still render if the audit lookup fails: the audit panel shows a message, the
  rest renders.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST keep the eleven settings listed in the Clarifications, each with a key, group, unit,
  allowed range and built-in default (see data-model.md).
- **FR-002**: The effective value of a setting MUST be the stored override, else the deployed configuration default,
  else the built-in default.
- **FR-003**: System Settings (mobile) and Security Settings MUST each be readable with View and changeable with
  Edit on their own permission module; a save is all-or-nothing.
- **FR-004**: A value MUST be a whole number inside its range (the minimum app version a `major.minor.patch`
  version). Anything else MUST be refused with a message per field, storing nothing.
- **FR-005**: Password reset and admin password reset MUST read the minimum length from Security Settings, and the
  rule message MUST state the current length.
- **FR-006**: Password sign-in MUST read the maximum failed attempts and lockout minutes from Security Settings.
- **FR-007**: OTP request throttling MUST read the resend cooldown, maximum consecutive requests and lockout hours
  from Security Settings (replacing `otp_policy_settings`, whose values are carried over).
- **FR-008**: Newly issued renewal credentials, their cookies, and access tokens MUST use the session renewal days and
  access-token seconds from Security Settings. Credentials and tokens already issued MUST be unaffected.
- **FR-009**: The public app-config and the mobile version gate MUST read the three mobile settings from System
  Settings at request time.
- **FR-010**: Every changed value MUST write a Change History entry (actor, entity type `SYSTEM_SETTING` or
  `SECURITY_SETTING`, key, previous value, new value) in the same transaction as the change. Change History MUST hide
  these entity types from callers without View on the matching module.
- **FR-011**: The System dashboard MUST return health, user counts, failed sign-ins in the last 24 hours and the 10
  most recent audit events visible to the caller, behind the dashboard permission, with no business data.
- **FR-012**: The role to permission matrix MUST gain three modules: System Dashboard (View), System Settings (View,
  Edit) and Security Settings (View, Edit). Seeded defaults: System has all three; Admin has System Settings and
  Security Settings; Director, Manager and Teacher have none. Only Admin, Director and System can ever hold them.
- **FR-013**: The navigation MUST list System Settings and Security Settings under SYSTEM CONFIGURATION for System
  (and under SYSTEM for Admin when granted) only when the View grant exists. The backend MUST enforce the grants
  regardless of the menu.
- **FR-014**: The edit screens MUST show unsaved-change state, per-field errors, the default value, and last changed
  by and when, and be usable at phone width.
- **FR-015**: No payroll, expense or approval limit is a setting here.

### Key Entities

- **System Setting**: one stored override: key, value text, updated at, updated by. The catalog (keys, ranges,
  defaults) is code, not data.
- **Setting Catalog Entry** (code): key, group, label, unit, minimum, maximum, built-in default, deployed-property
  name.
- **System Dashboard**: a computed read model, nothing stored.

## Role & Permission Impact *(mandatory — Constitution Principles II–IV)*

| Capability | Admin | Director | Manager | Teacher | System |
| --- | --- | --- | --- | --- | --- |
| System Dashboard | grantable | grantable | cannot | cannot | View |
| System Settings (mobile) | View, Edit | grantable | cannot | cannot | View, Edit |
| Security Settings | View, Edit | grantable | cannot | cannot | View, Edit |

- Permission modules: `SYSTEM_DASHBOARD` (View), `SYSTEM_SETTINGS` (View, Edit), `SECURITY_SETTINGS` (View, Edit).
  Eligibility: Admin, Director and System only; Manager and Teacher are refused at grant time. They are not business
  modules, so System may hold them.
- Data scope: none. These are platform values, not business records; the dashboard's audit feed is filtered by the
  caller's own view grants on business entity types, so System sees no business change events.
- Menus: SYSTEM DASHBOARD > Dashboard (exists); SYSTEM CONFIGURATION > System Settings, Security Settings (System);
  SYSTEM > the same two items for Admin and any granted Director.
- Constitution deltas: the Default role access matrix row "System & Security Settings" gains Admin (View, Edit)
  alongside System, because Admin holds full operational authority; Director stays off by default. Recorded as a
  PATCH amendment (2.5.3) in the constitution header.
- Audit: every changed value (FR-010). Reading is not audited.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A change to any of the eleven values takes effect on the next decision with no restart.
- **SC-002**: 100% of changed values appear in Change History with actor, old and new values; 0 for unchanged saves.
- **SC-003**: Teacher, Manager and (by default) Director receive 403 on every settings endpoint, and 403 on the
  dashboard; System receives 200 on all.
- **SC-004**: The dashboard loads in under 2 seconds with 1,000 users.
- **SC-005**: Out-of-range or malformed input never changes a stored value.
- **SC-006**: Existing sign-in, OTP, session and mobile tests pass unchanged in behaviour at the default values.

## Assumptions

- The eleven settings above are the whole scope; further policy values (for example password complexity classes) are
  future work.
- Defaults equal today's behaviour (10, 5, 30 minutes, 30 s, 5, 4 h, 14 days, 900 s; mobile 0.0.0, 4 s, 10 s).
- Ranges: password length 8-64; attempts 3-20; lockout minutes 1-1440; resend cooldown 10-600 s; consecutive
  requests 1-20; request lockout hours 1-72; renewal days 1-90; access-token seconds 300-3600; location wait 1-30 s;
  location reuse 0-300 s.
- A single application instance (research §4 of spec 001): in-process cache invalidation is enough; a 60 second
  time limit is the safety net.
- Admin is allowed the settings by default (decision of this spec, see Role & Permission Impact).
- The dashboard's session count is out of scope (the sessions screen exists).
