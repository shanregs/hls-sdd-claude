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
- Q: Should Accounts Officer remain a fifth distinct role, or be dropped/merged into Admin? → A: Keep it — the source Requirements doc (§2) names real people in this role (Suresh as A.O., Ilangovan as A.D.) with a distinct authority boundary (reconciliation access, no payroll approval) that Director/Admin/Manager don't cover; dropping it would remove a real access-control distinction, not just a label.
- Q: Should this module own its own copy of Manager-to-Teacher/School assignment data, or read it live from the Organization module (spec 003, built after this spec)? → A: Read it live from Organization's public API on each request that needs scope-checking — no separate copy stored in this module. This makes FR-014/SC-006's "reflects immediately, no re-login" true by construction (nothing to keep in sync) and keeps Organization the single owner of that data, per the constitution's module-boundary rule.
- Q: If Organization's scope-check API is unreachable or times out, should the request be denied (fail closed) or allowed through on a cached/last-known scope (fail open)? → A: Fail closed — deny the request. Constitution Principle II treats cross-manager leakage as the primary risk to prevent; a temporary denial during a rare, short outage (single-EC2, <100 users) is a far smaller cost than a scoping failure that leaks another manager's data.

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
4. **Given** the Organization module's scope-check does not respond within 2 seconds, **When** a Manager makes a request that needs a scope check, **Then** the request is denied with the same generic response a Manager would get for a record genuinely outside their scope (FR-019, FR-020).

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

### User Story 6 - Teacher sees only their own records (Priority: P1)

A Teacher logs in and can see and act only on their own attendance, payslip, training, and expense records, with no visibility into another teacher's data.

**Why this priority**: Analogous to User Story 3's Manager-scoping risk (Constitution Principle II) — Teachers are the largest user group in the system (Requirements §1), and a Teacher reaching another teacher's payslip or attendance would be exactly the kind of cross-record leakage Principle II exists to prevent. This was originally missed as its own story despite FR-005 already requiring it (found via `/speckit-analyze`, 2026-09-21).

**Independent Test**: Log in as two different Teachers; confirm each sees only their own attendance/payslip/training/expense records and that direct attempts to access another teacher's record are denied.

**Acceptance Scenarios**:

1. **Given** a Teacher is authenticated, **When** they view their own attendance, payslip, training, or expense data, **Then** only their own records are returned.
2. **Given** a Teacher attempts to directly access another teacher's record (e.g., by entering another record's identifier directly), **When** that request is made, **Then** the system denies access rather than returning the data.
3. **Given** a Teacher's `User` record has no linked Teacher identifier yet (e.g., before the later Teacher Master Data module populates it), **When** they attempt to access any teacher-scoped record, **Then** the request is denied by default rather than ambiguously allowed.

---

### Edge Cases

- What happens when the SMS gateway used for OTP delivery is unavailable? Teacher/OTP-based login must fail gracefully with a clear message, not hang indefinitely. Teachers have no alternative login credential in this case (by design, for v1) — the existing Manager-assisted workflows (e.g., a Manager marking attendance on a teacher's behalf, per Requirements §3.1) are the accepted mitigation until the gateway recovers.
- What happens when a Manager's assignment changes while they have an item open that is no longer in their scope (e.g., mid-edit on a teacher just reassigned away)? The next action they take against that record must be denied — this holds naturally under the live-query model (Clarifications session 2026-09-21), since every action re-checks Organization fresh with no cached result to go stale.
- What happens when Organization successfully responds but reports a record as unassigned or unrecognized, as opposed to Organization itself being unreachable? This is not FR-019's fail-closed case — Organization answered successfully — it is an ordinary FR-004 denial, since a record with no manager currently assigned (or no record at all) is not affirmatively assigned to the requesting Manager either.
- How does the system handle a session that expires mid-task? The next request must require re-authentication rather than silently failing or exposing stale data.
- What happens when an Accounts Officer or Admin attempts an action outside their defined authority (e.g., Accounts Officer attempting payroll approval)? The action must be denied even though they can view the underlying data.
- What happens if OTP requests are made for a phone number that isn't registered to any Teacher account? The system must not reveal whether the number is registered, to avoid enumerating valid accounts.
- What happens when a Manager-scoped request needs a scope check and the Organization module's API is unreachable or does not respond within 2 seconds (FR-019)? The request must be denied (fail closed), never allowed through on a cached or assumed scope (Clarifications session 2026-09-21).
- What happens when a Teacher's `User` record has no linked Teacher identifier yet? Any teacher-scoped request must be denied by default (FR-021) — unlike Manager scoping, this check needs no cross-module call, since the Teacher's own linkage lives on their `User` record.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST require every user to authenticate before accessing any role-specific data or action.
- **FR-002**: System MUST support five roles — Director, Manager/Area Coordinator, System Assistant/Admin, Accounts Officer, and Teacher — each with a distinct, defined access scope. A single user MAY be assigned more than one role simultaneously (e.g., a Manager who is also the Accounts Officer); their effective access scope MUST be the union of all assigned roles' permissions.
- **FR-003**: Director and System Assistant/Admin roles MUST see and act across the entire organization (all schools, teachers, and managers).
- **FR-004**: Manager/Area Coordinator role MUST see and act only on teachers and schools explicitly assigned to them; any attempt to access another manager's teacher or school records MUST be denied. This applies to every request that operates on a specific Teacher or School record, in this module or any other module in the system — not only to Identity & Access's own endpoints.
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
- **FR-019**: System MUST deny a Manager-scoped request whenever the Organization module's scope-check does not complete within 2 seconds — treated identically to an outright failure ("unreachable or timed out") — rather than falling back to a cached or assumed scope. 2 seconds is deliberately tighter than the 3-second bounded-wait precedent set by the System Status Page's data-store reachability check (spec 001), since this check runs on every Manager-scoped request rather than a single health probe. This rule applies only to Manager-role requests; Director, System Assistant/Admin, and Accounts Officer requests carry no per-manager scope check to begin with (FR-003, FR-006 already grant them organization-wide access), so they are unaffected by FR-019 and by any Organization outage. This check happens on Manager-scoped data requests made after login, not during the login flow itself (FR-007), so it does not count against SC-001's login-time budget.
- **FR-020**: A denial caused by FR-019's fail-closed rule MUST be indistinguishable, from the caller's perspective, from an ordinary denial caused by the Manager genuinely lacking access to that record — the response must not reveal that Organization was unreachable, consistent with FR-015's principle of not leaking system/account state through error responses.
- **FR-021**: System MUST enforce FR-005 (Teacher sees only their own records) by comparing the requesting Teacher's own linked Teacher identifier against the target record's teacher identifier; this applies to every request that operates on a specific Teacher-owned record, in this module or any other, mirroring FR-004's scope for Managers. A Teacher whose `User` record has no linked Teacher identifier yet MUST be denied by default on any teacher-scoped request, never granted broad access as a fallback.

### Key Entities *(include if feature involves data)*

- **User**: id, display name, one or more assigned roles (Director/Manager/Admin/Accounts Officer/Teacher), unique phone number (primary login identifier for all roles), optional email, credential reference, active/locked status, linked Teacher or Manager profile where applicable.
- **Session**: user id, channel (web/mobile app), issued time, last-active time, revoked flag — represents one active login.
- **AccessScope** *(not stored by this module)*: the set of teacher and school identifiers currently assigned to a given manager — the boundary enforced for Manager-role requests. This module reads it live from the Organization module (spec 003) on each request that needs a scope check; it is not a table this module owns or keeps its own copy of (Clarifications session 2026-09-21).
- **AuthAuditEntry**: actor (user id or attempted identifier), action (login success / login failure / access denied / session revoked), timestamp, role at time of action.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A staff member (Director, Manager, Admin, or Accounts Officer) can log in and reach their role-appropriate home view in under 15 seconds under normal network conditions.
- **SC-002**: A Teacher can complete OTP login, from either the mobile app or web portal, in under 60 seconds including OTP delivery, under normal network conditions.
- **SC-003**: 100% of attempts by a Manager to access a teacher or school record outside their current assignment are blocked.
- **SC-004**: 100% of tested brute-force login-attempt sequences result in the account being locked once the configured failed-attempt threshold is reached.
- **SC-005**: A revoked session loses all access within 60 seconds of revocation, verified on any subsequent request from that session.
- **SC-006**: When a Manager's assignment changes, their very next request after the change reflects the new scope 100% of the time, with no re-login required.
- **SC-007**: 100% of scope checks that fail or exceed the 2-second bound (FR-019) result in the request being denied — never allowed through on a cached, assumed, or partial scope.
- **SC-008**: 100% of attempts by a Teacher to access another teacher's attendance, payslip, training, or expense record are blocked, including when the Teacher's own linked Teacher identifier is not yet set.

## Assumptions

- Multi-factor authentication for web-based roles (Director/Manager/Accounts Officer/Admin) is optional and configurable per account, not mandatory for v1.
- Teacher and staff contact details (phone numbers used for OTP, initial credentials) are assumed to already exist as part of each person's user record; this module defines login and authorization behavior, not full profile data entry — teacher-specific profile fields beyond login contact details are owned by the later Teacher Master Data module.
- The configurable failed-attempt lockout threshold defaults to an industry-standard value (e.g., 5 consecutive failures) unless HLS specifies a different number.
- The specific India SMS gateway used to deliver OTPs is a separate technical/vendor decision (per Requirements §12); this spec defines the OTP login behavior expected of the system, not gateway selection.
- Enforcing Manager access scope (FR-004, FR-014, FR-019) depends on assignment data — which teacher/school belongs to which manager — owned by the Organization module (spec 003) and read live via its public API on each scope-checking request (Clarifications session 2026-09-21), not duplicated into this module's own storage. Since Organization was specified and planned after this spec, this module is expected to be exercised with a stand-in/test implementation of that API in the interim.
- **This dependency inverts the original build-order assumption**: the project's implementation tracker originally sequenced Identity & Access before Organization, assuming Identity had no dependency on it. That assumption no longer holds for User Story 3 specifically (Manager scoping) — Organization's implementation needs to exist, at least as a working API, before FR-004/FR-014/FR-019 can be verified against the real thing rather than a stand-in. The other four user stories (staff login, Teacher OTP, lockout, session management) have no such dependency and are unaffected.
- "Web portal" and "mobile app" are treated as two channels through which a Teacher may complete the same OTP login flow, not two separate authentication mechanisms.
- A scope check that runs concurrently with a reassignment in Organization will see either the pre-change or the post-change assignment, never a partial/torn state — this follows from Organization's own guarantee (spec 003, FR-003) that at most one assignment is ever active per School/Teacher at a time, so this spec adds no extra concurrency rule of its own beyond reading Organization's answer as given.
- FR-014 and FR-019 govern two different, non-overlapping situations rather than conflicting: FR-019 applies during the (rare, 2-second-bounded) window where Organization cannot be reached at all; FR-014 applies to the ordinary case where Organization is reached and successfully reports a newly changed assignment.
- This module MUST NOT introduce a cached or duplicated copy of Organization's assignment data in any future revision without an explicit new clarification superseding this session's decision — caching would reintroduce the dual-source-of-truth problem this session eliminated.
- Because Identity and Organization are two packages within the same modular-monolith deployable (Constitution Principle V), a breaking change to Organization's public API is caught at compile time, not discovered at runtime as it would be with a networked service — no separate API-versioning scheme is needed for this dependency today.
- Teacher self-scoping (FR-005, FR-021, User Story 6) needs no cross-module dependency the way Manager scoping needs Organization: the Teacher's own linkage (`User.linkedTeacherId`) is already stored on this module's own `User` record, so there is no equivalent fail-closed/timeout rule to write — a missing linkage is simply treated as "deny by default," resolvable entirely within this module.
- Director, System Assistant/Admin, and Accounts Officer roles (FR-003, FR-006) have no dedicated scope-enforcement mechanism in this module because they are "no restriction" roles by definition — there is nothing for this module to check beyond role membership. Full verification of their org-wide access is deferred until the modules holding the actual data (School, Teacher, Payroll, etc.) exist to be queried; FR-006's "no payroll approval authority" carve-out specifically is enforced by the future Payroll module checking role, not by this module.
