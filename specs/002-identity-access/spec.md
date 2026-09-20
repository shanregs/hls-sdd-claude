# Feature Specification: Identity & Access

**Feature Branch**: `002-identity-access`

**Created**: 2026-09-21

**Status**: Draft

**Input**: User description: "Identity & Access module for the HLS Teacher Management System. Enable every user to log in and be restricted to the data their role is allowed to see. Five roles: Director (full organization, all schools/teachers/managers), Manager/Area Coordinator (only their own assigned teachers and schools — no visibility into another manager's data), System Assistant/Admin (full organization, master-data maintenance and reconciliation only — no payroll approval authority), Accounts Officer (organization-wide read/reconciliation access to school receivables and teacher payables), and Teacher (own records only: own attendance, payslip, training, expense claims). Director, Manager, Accounts Officer and Admin log in from the web app with a password (optional MFA). Teachers log in from the mobile app, web portal using an OTP sent to their registered phone number. Failed login attempts beyond a threshold lock the account. Sessions expire automatically after inactivity and can be renewed while active; a user's active sessions/devices can be viewed and revoked (by themselves or by an Admin/Director). Every login attempt and every access-denied event is recorded with actor, timestamp, and role for audit purposes. If a Manager's assigned teachers/schools change, their visible data must reflect the new assignment immediately, without re-login."

## Clarifications

### Session 2026-09-21

- Q: Can one person hold more than one role at the same time — for example, a Manager who is also the organization's Accounts Officer? → A: Yes — a user can be assigned multiple roles simultaneously; their effective access scope is the union of those roles' permissions (matches the real pattern in HLS's data, e.g. Suresh appearing as both Manager and Accounts Officer).
- Q: How should a staff member recover their account if they forget their web password? → A: Self-service reset — a one-time code/link is sent to the user's registered phone or email to set a new password.
- Q: What identifier do staff members type in to log in on the web? → A: Phone number — the same channel Teachers use for OTP; each user's phone number is their unique login identifier regardless of role.
- Q: When a staff member enables optional MFA, what second factor should the system use? → A: The user's choice of either an SMS one-time code to their registered phone number, or an email one-time code (if an email is on file) — not an authenticator app.
- Q: If the SMS gateway is temporarily down, should Teachers have any alternative way to log in themselves? → A: No — Teachers have no fallback login credential for v1; Managers act on their behalf using existing Manager-assisted workflows (e.g., marking attendance) until the gateway recovers.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Staff web login with role-appropriate access (Priority: P1)

A Director, Manager/Area Coordinator, System Assistant/Admin, or Accounts Officer logs into the web application with their password and lands on a view scoped to what their role is allowed to see.

**Why this priority**: This is the entry point for every non-teacher operation in the system — without it, no other module can be used by staff. It is the foundational MVP.

**Independent Test**: Log in as each of the four web-based roles with valid credentials; confirm each reaches the application and sees only the data/actions appropriate to their role (verified in User Story 3 for the Manager case specifically).

**Acceptance Scenarios**:

1. **Given** a Director/Manager/Admin/Accounts Officer has a valid web account, **When** they submit correct credentials on the web login screen, **Then** they are authenticated and taken to their role's home view.
2. **Given** a user submits an incorrect password, **When** the login is attempted, **Then** access is denied with a generic error that does not reveal whether the username or password was wrong.
3. **Given** MFA is enabled for a user's account, **When** they submit correct credentials, **Then** they are prompted for the second factor before being granted access.
4. **Given** a staff member has forgotten their password, **When** they request a password reset, **Then** a one-time code/link is sent to their registered phone or email, and using it lets them set a new password without administrator involvement.

---

### User Story 2 - Teacher OTP login (Priority: P1)

A Teacher logs in from either the mobile app or the web portal using a one-time passcode sent to their registered phone number, without needing to remember a password.

**Why this priority**: Teachers are the largest user group and their daily attendance/payslip/training workflows depend entirely on being able to log in. Equally critical to Story 1 as an MVP entry point.

**Independent Test**: Request an OTP for a registered teacher phone number from either the mobile app or web portal, submit the correct OTP, and confirm the teacher reaches their own home view.

**Acceptance Scenarios**:

1. **Given** a Teacher enters their registered phone number on the mobile app or web portal, **When** they request a login code, **Then** an OTP is sent to that phone number.
2. **Given** a Teacher has received a valid OTP, **When** they submit it before it expires, **Then** they are authenticated and taken to their own home view.
3. **Given** a Teacher submits an expired or incorrect OTP, **When** the login is attempted, **Then** access is denied and they are able to request a new OTP.
4. **Given** a Teacher requests OTPs repeatedly in quick succession, **When** the request rate exceeds a configured limit, **Then** further OTP requests for that phone number are temporarily blocked.

---

### User Story 3 - Manager sees only their own teachers and schools (Priority: P1)

A Manager/Area Coordinator logs in and can see and act only on the teachers and schools explicitly assigned to them, with no visibility into another manager's teachers, schools, or data.

**Why this priority**: Cross-manager data leakage is a named risk in the project constitution (Principle II) and a core trust requirement for the whole system — this is as critical as being able to log in at all.

**Independent Test**: Log in as two different Managers with non-overlapping assignments; confirm each sees only their own teachers/schools and that direct attempts to view or act on the other manager's records are denied.

**Acceptance Scenarios**:

1. **Given** a Manager is assigned a specific set of teachers and schools, **When** they view their dashboard or any listing, **Then** only their assigned teachers and schools appear.
2. **Given** a Manager attempts to directly access a teacher or school record not assigned to them (e.g., by entering another record's identifier directly), **When** that request is made, **Then** the system denies access rather than returning the data.
3. **Given** a Manager's assignment (which teachers/schools they cover) changes, **When** they make their next request after the change, **Then** their visible data reflects the new assignment immediately, without requiring them to log out and back in.

---

### User Story 4 - Account lockout after repeated failed logins (Priority: P2)

A user's account is automatically locked after a configurable number of consecutive failed login attempts, protecting against brute-force password/OTP guessing.

**Why this priority**: An important security control, but the system is still usable end-to-end (Stories 1–3) without it being the very first thing built.

**Independent Test**: Attempt to log in with an incorrect password/OTP repeatedly beyond the configured threshold; confirm the account becomes locked and further correct-credential attempts are also denied until unlocked.

**Acceptance Scenarios**:

1. **Given** a user has failed to log in a configured number of consecutive times, **When** the next failed attempt occurs, **Then** the account is locked and further login attempts are denied even with correct credentials.
2. **Given** an account is locked, **When** an authorized unlock action is performed, **Then** the account becomes available for login again.

---

### User Story 5 - View and revoke active sessions/devices (Priority: P3)

A user, or an Admin/Director acting on their behalf, can see a list of that user's currently active sessions/devices and revoke any one of them, immediately ending that session's access.

**Why this priority**: Valuable for security incident response (e.g., a lost phone) but not required for the system's day-one core workflows.

**Independent Test**: Log in from two different devices/browsers as the same user, view the active session list from one of them, revoke the other, and confirm the revoked session can no longer make authenticated requests.

**Acceptance Scenarios**:

1. **Given** a user has active sessions on more than one device, **When** they (or an Admin/Director) view their session list, **Then** each active session is listed with enough detail to identify it (e.g., device/channel and last-active time).
2. **Given** a session is revoked, **When** that session's device attempts any further request, **Then** the request is denied and the device must re-authenticate.

---

### Edge Cases

- What happens when the SMS gateway used for OTP delivery is unavailable? Teacher/OTP-based login must fail gracefully with a clear message, not hang indefinitely. Teachers have no alternative login credential in this case (by design, for v1) — the existing Manager-assisted workflows (e.g., a Manager marking attendance on a teacher's behalf, per Requirements §3.1) are the accepted mitigation until the gateway recovers.
- What happens when a Manager's assignment changes while they have an item open that is no longer in their scope (e.g., mid-edit on a teacher just reassigned away)? The next action they take against that record must be denied.
- How does the system handle a session that expires mid-task? The next request must require re-authentication rather than silently failing or exposing stale data.
- What happens when an Accounts Officer or Admin attempts an action outside their defined authority (e.g., Accounts Officer attempting payroll approval)? The action must be denied even though they can view the underlying data.
- What happens if OTP requests are made for a phone number that isn't registered to any Teacher account? The system must not reveal whether the number is registered, to avoid enumerating valid accounts.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST require every user to authenticate before accessing any role-specific data or action.
- **FR-002**: System MUST support five roles — Director, Manager/Area Coordinator, System Assistant/Admin, Accounts Officer, and Teacher — each with a distinct, defined access scope. A single user MAY be assigned more than one role simultaneously (e.g., a Manager who is also the Accounts Officer); their effective access scope MUST be the union of all assigned roles' permissions.
- **FR-003**: Director and System Assistant/Admin roles MUST see and act across the entire organization (all schools, teachers, and managers).
- **FR-004**: Manager/Area Coordinator role MUST see and act only on teachers and schools explicitly assigned to them; any attempt to access another manager's teacher or school records MUST be denied.
- **FR-005**: Teacher role MUST see and act only on their own attendance, payslip, training, and expense records.
- **FR-006**: Accounts Officer role MUST have organization-wide read and reconciliation access to school receivables and teacher payables, without payroll approval authority.
- **FR-007**: Director, Manager/Area Coordinator, Accounts Officer, and System Assistant/Admin roles MUST be able to log in from the web application using their registered phone number as the login identifier plus a password, with an optional multi-factor authentication step.
- **FR-008**: Teacher role MUST be able to log in from either the mobile app or the web portal using a one-time passcode sent to their registered phone number.
- **FR-009**: System MUST rate-limit OTP requests per phone number to prevent abuse.
- **FR-010**: System MUST lock an account after a configurable number of consecutive failed login attempts, and require an explicit unlock action before further login attempts succeed.
- **FR-011**: System MUST issue time-limited session credentials that expire automatically after a period of inactivity, and that can be silently renewed while the user remains active.
- **FR-012**: A user, or an Admin/Director acting on that user's behalf, MUST be able to view that user's active sessions/devices and revoke any one of them, immediately ending that session's access.
- **FR-013**: System MUST record every login attempt (success and failure) and every access-denied event, capturing the actor, timestamp, and role.
- **FR-014**: When a Manager's assigned teachers/schools change, their visible data MUST reflect the new assignment on their very next request, without requiring re-login.
- **FR-015**: System MUST NOT reveal whether a given phone number, email, or credential exists in error messages for failed login, password-reset, or OTP-request attempts.
- **FR-016**: A staff member (Director, Manager, Admin, or Accounts Officer) MUST be able to self-service reset a forgotten password by requesting a one-time code/link sent to their registered phone number (or email, if also on file), without requiring Admin/Director intervention.
- **FR-017**: Each user's phone number MUST be unique across the system and serve as their single login identifier, independent of how many roles (FR-002) they hold.
- **FR-018**: When MFA is enabled for a user's account, the system MUST let that user choose their second factor between an SMS one-time code sent to their registered phone number, or an email one-time code sent to their registered email (if on file).

### Key Entities *(include if feature involves data)*

- **User**: id, display name, one or more assigned roles (Director/Manager/Admin/Accounts Officer/Teacher), unique phone number (primary login identifier for all roles), optional email, credential reference, active/locked status, linked Teacher or Manager profile where applicable.
- **Session**: user id, channel (web/mobile app), issued time, last-active time, revoked flag — represents one active login.
- **AccessScope**: manager id, the set of teacher and school identifiers currently assigned to that manager — the boundary enforced for Manager-role requests.
- **AuthAuditEntry**: actor (user id or attempted identifier), action (login success / login failure / access denied / session revoked), timestamp, role at time of action.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A staff member (Director, Manager, Admin, or Accounts Officer) can log in and reach their role-appropriate home view in under 15 seconds under normal network conditions.
- **SC-002**: A Teacher can complete OTP login, from either the mobile app or web portal, in under 60 seconds including OTP delivery, under normal network conditions.
- **SC-003**: 100% of attempts by a Manager to access a teacher or school record outside their current assignment are blocked.
- **SC-004**: 100% of tested brute-force login-attempt sequences result in the account being locked once the configured failed-attempt threshold is reached.
- **SC-005**: A revoked session loses all access within 60 seconds of revocation, verified on any subsequent request from that session.
- **SC-006**: When a Manager's assignment changes, their very next request after the change reflects the new scope 100% of the time, with no re-login required.

## Assumptions

- Multi-factor authentication for web-based roles (Director/Manager/Accounts Officer/Admin) is optional and configurable per account, not mandatory for v1.
- Teacher and staff contact details (phone numbers used for OTP, initial credentials) are assumed to already exist as part of each person's user record; this module defines login and authorization behavior, not full profile data entry — teacher-specific profile fields beyond login contact details are owned by the later Teacher Master Data module.
- The configurable failed-attempt lockout threshold defaults to an industry-standard value (e.g., 5 consecutive failures) unless HLS specifies a different number.
- The specific India SMS gateway used to deliver OTPs is a separate technical/vendor decision (per Requirements §12); this spec defines the OTP login behavior expected of the system, not gateway selection.
- Enforcing Manager access scope (FR-004, FR-014) depends on assignment data — which teacher/school belongs to which manager — that is populated in full once the later Teacher/School/Contract (roster) modules are built; this module defines the access-control mechanism that consumes that assignment data as it becomes available, and is expected to be exercised with representative test data before those modules exist.
- "Web portal" and "mobile app" are treated as two channels through which a Teacher may complete the same OTP login flow, not two separate authentication mechanisms.
