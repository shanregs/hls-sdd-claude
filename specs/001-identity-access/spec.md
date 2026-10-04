# Feature Specification: Identity & Access

**Feature Branch**: `001-identity-access`

**Created**: 2026-09-23

**Status**: Draft

**Input**: User description: "Identity & Access (roadmap spec 001, module `identity`) under Constitution v2.0.0. Users authenticate and the system automatically identifies their role(s), with no manual role selection. Exactly five fixed roles: Admin, Director, Manager, Teacher, System. A user may hold more than one role. The scope covers:
- staff password login and Teacher OTP login
- short-lived session plus rotating refresh with reuse detection
- lockout, logout, self-service session management, and password reset by OTP
- deactivation
- login-history events
- bootstrap of the first Admin and System users
- a post-login placeholder landing
- a login experience built on the new design system

Out of scope: the permission matrix and navigation (002), audit screens (003), user and role management screens (004), the security-settings UI (011), and the mobile app."

**Amendment (2026-09-23, Constitution v2.3.0)**: the login model is generalized. OTP sign-in is no
longer Teacher-only — every user may sign in by phone + one-time SMS code, or by password identified
by either their phone number or a username. Password reset can deliver its code to the user's phone
or, if they have one registered, their email address. User Stories 1 and 2 below, and the FRs and
entities they depend on, are updated accordingly; this is additive to the rest of the spec.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Any User Signs In With a Password, by Phone Number or Username (Priority: P1)

A user of any role opens the application and signs in with a password, identified by either their
phone number or their username (whichever they set up or were given). They do not choose a role.
The system recognizes who they are and which of the five roles they hold, then takes them into the
application. There they see their name and roles and can log out.

**Why this priority**: nothing else in the system can be used until people can sign in and be
identified by role. Every later spec builds on this.

**Independent Test**: create a user holding one or more roles with both a phone number and a
username, sign in once with the phone number and password, and once with the username and password;
confirm both reach the landing showing their name and exactly their roles, with no role picker at
any point.

**Acceptance Scenarios**:

1. **Given** an active user with the Manager role and a password set, **When** they sign in with
   their correct phone number and password, **Then** they are signed in and the landing shows their
   name and "Manager".
2. **Given** the same user also has a username set, **When** they sign in with their username and
   the same password instead, **Then** they are signed in identically.
3. **Given** an active user holding both Admin and Director, **When** they sign in, **Then** the
   landing shows both roles, and no step asks them to choose one.
4. **Given** any user, **When** they enter a wrong password, an unregistered phone number, or an
   unregistered username, **Then** sign-in is refused with the identical generic "your phone
   number/username or password is incorrect" message in every case, revealing nothing about which
   part was wrong or whether the identifier is registered.
5. **Given** a signed-in user, **When** they select Logout, **Then** their session ends and
   returning to any in-app page requires signing in again.
6. **Given** a user who has never had a password set, **When** they attempt password sign-in with
   any identifier and any password, **Then** they receive the identical generic failure message —
   password sign-in is only possible once a password exists on the account.

---

### User Story 2 - Any User Signs In With a One-Time Code (Priority: P1)

A user of any role enters their registered phone number, receives a one-time code by SMS, and enters
it to sign in. No password is needed for this method, regardless of whether the account has one set.

**Why this priority**: Teachers are the largest user group and rely on this as their primary method;
it is now also available to every other role as a password-free alternative. Attendance and leave
self-service in later specs depends on Teachers being able to sign in this way.

**Independent Test**: request a code for any active user's phone number, then enter the delivered
code. A development SMS stub is sufficient. Confirm the user is signed in and shown all of their
roles.

**Acceptance Scenarios**:

1. **Given** an active user of any role, **When** they request a code for their phone number and
   enter it within 5 minutes, **Then** they are signed in with all of their roles.
2. **Given** a code older than 5 minutes, **When** it is entered, **Then** sign-in is refused with
   a "code expired, request a new one" message.
3. **Given** a code was already used successfully, **When** it is entered again, **Then** it is
   refused.
4. **Given** a phone number that has already requested 3 codes within the last minute, **When** a
   4th is requested, **Then** the request is refused with a "too many requests, try again shortly"
   message and no SMS is sent.
5. **Given** a phone number that does not belong to any active user, **When** a code is requested,
   **Then** the screen shows the same neutral "if this number is registered, a code has been sent"
   confirmation, and no SMS is sent.
6. **Given** a user holding several roles, **When** they sign in by OTP or by password, **Then**
   they receive all of their roles either way.
7. **Given** a code was just requested for a phone number, **When** a new code is requested for the
   same number less than the configured resend cooldown (default 30 seconds) later, **Then** the
   request is refused with a "please wait" message and no SMS is sent.
8. **Given** a phone number has reached the configured consecutive-request limit (default 5) without
   an intervening successful sign-in, **When** one more code is requested, **Then** it is refused and
   further requests for that number are refused for the configured lockout duration (default 4
   hours).
9. **Given** a phone number is locked out per Scenario 8, **When** the user instead enters a
   still-valid, previously issued code and signs in successfully, **Then** the consecutive-request
   count and lock are cleared immediately, and requesting a new code afterward succeeds normally.

---

### User Story 3 - Sessions Stay Alive Safely (Priority: P2)

A signed-in user keeps working without being asked to sign in every few minutes. If a stolen
session credential is replayed, the whole session is shut down.

**Why this priority**: this makes daily use practical while protecting payroll and attendance data.
It depends on Stories 1 and 2.

**Independent Test**: sign in, let the short-lived access expire, and confirm the session renews
silently. Then replay an already-used renewal credential and confirm the whole session is revoked.

**Acceptance Scenarios**:

1. **Given** a signed-in user whose 15-minute access period has elapsed, **When** they continue
   working within 14 days of sign-in, **Then** access renews silently without re-entering
   credentials.
2. **Given** a renewal credential that has already been used once, **When** it is presented again,
   **Then** that session and every renewal descended from it are revoked, and the user must sign in
   again.
3. **Given** 14 days have passed since sign-in, **When** the user tries to continue, **Then** they
   must sign in again.

---

### User Story 4 - Account Lockout After Repeated Failures (Priority: P2)

After 5 consecutive wrong passwords, the account is locked for 30 minutes so passwords cannot be
guessed. The user is told when they can try again.

**Why this priority**: this protects staff accounts that can approve payroll. It depends on
Story 1.

**Independent Test**: enter 5 wrong passwords for one user and confirm the 6th attempt is refused
as locked, even with the correct password. Confirm the unlock time is shown and sign-in works after
30 minutes.

**Acceptance Scenarios**:

1. **Given** a user with 4 consecutive failed attempts, **When** the 5th wrong password is entered,
   **Then** the account locks for 30 minutes and the message shows the time it unlocks.
2. **Given** a locked account, **When** the correct password is entered before the unlock time,
   **Then** sign-in is still refused as locked.
3. **Given** a user with 3 consecutive failures, **When** they then sign in successfully, **Then**
   the failure count resets to zero.
4. **Given** a lockout period has passed, **When** the correct password is entered, **Then**
   sign-in succeeds.

---

### User Story 5 - Manage My Sessions and Reset My Password (Priority: P3)

A signed-in user opens ACCOUNT → Profile. They see their active sessions (device, sign-in time, last
activity) and can end any of them. A user who forgot their password resets it with a code sent to
their registered phone, their registered email, or both at once if they have both registered — their
choice each time.

**Why this priority**: this is self-service hygiene. It reduces support effort but is not needed
for day-one use.

**Independent Test**: sign in from two browsers, end one session from the other's Profile page,
and confirm it is signed out. Separately, reset a password by a code sent to the phone, and again by
a code sent to the registered email, and sign in with the new password each time.

**Acceptance Scenarios**:

1. **Given** a user signed in on two devices, **When** they view Profile, **Then** both sessions
   are listed and the current one is marked "this device".
2. **Given** that list, **When** they end the other session, **Then** that device is signed out at
   its next action.
3. **Given** a user who forgot their password, **When** they choose to receive the reset code by
   SMS, enter the code received, and set a new password that meets the password policy, **Then** the
   new password works, the old one does not, and all their existing sessions are ended.
4. **Given** the same user has a registered email, **When** they instead choose to receive the reset
   code by email, **Then** the code arrives at that email address and completing the reset works
   identically to the SMS path.
5. **Given** a user with a registered email, **When** they choose to receive the code by both SMS
   and email, **Then** the identical code is delivered to both, and entering it from either message
   completes the reset — the code is not restricted to whichever channel is "primary."
6. **Given** a user with no registered email, **When** they open the reset flow, **Then** only the
   SMS option is offered — there is no "both" choice without two registered channels.
7. **Given** a code was sent to both channels and used successfully from one of them, **When** the
   same code is then entered from the other channel's message, **Then** it is refused as already
   used — sending to both never allows the same code to complete two resets.
8. **Given** a locked account, **When** the user completes a password reset by any channel,
   **Then** the lock is cleared.

---

### User Story 6 - First Users Exist at Deployment; Deactivated Users Are Shut Out (Priority: P2)

When the system is first deployed, one Admin and one System user are created from deployment
configuration, so someone can sign in. No credentials are written into the product. When a user is
deactivated, they cannot sign in and any session they have ends.

**Why this priority**: without bootstrap users nobody can sign in to a fresh installation.
Deactivation is essential when staff leave.

**Independent Test**: deploy with bootstrap configuration and confirm the Admin and System users
can sign in. Deactivate a signed-in user through the minimal user-creation capability and confirm
their session ends and they cannot sign in again.

**Acceptance Scenarios**:

1. **Given** a fresh deployment with bootstrap Admin and System details configured, **When** the
   system starts, **Then** both users exist with their roles and can sign in.
2. **Given** bootstrap users already exist, **When** the system restarts, **Then** no duplicate
   users are created and existing passwords are not overwritten.
3. **Given** bootstrap configuration is missing on a fresh deployment, **When** the system starts,
   **Then** it reports clearly that no initial Admin/System user was created. It does not invent
   default credentials.
4. **Given** a signed-in user who is then deactivated, **When** they make their next request,
   **Then** it is refused and they are returned to sign-in, and a new sign-in by any method is
   refused.

---

### Edge Cases

- **The same phone number is entered for two users**: rejected. Phone numbers are unique across all
  users, whatever roles they hold.
- **Phone numbers are entered in different formats** (with or without +91, spaces, or a leading
  0): they are normalized to one canonical Indian mobile format before matching, so they are
  treated as the same number.
- **The same username is entered for two users**: rejected. Usernames are unique across all users
  (case-insensitively), independent of the phone-number uniqueness domain.
- **A username that looks like a phone number** (e.g. all digits): still resolved as a username
  lookup if it does not match a registered phone number after normalization; there is no ambiguity
  in practice because the two uniqueness domains are checked independently and a login attempt
  simply tries whichever one the entered value matches.
- **A user has no username set**: they can still sign in by phone number and password (if a
  password is set), or by phone and OTP. A username is optional, never required.
- **A user requests an OTP to sign in**: available regardless of role or whether a password is set
  on the account — OTP sign-in no longer depends on holding any particular role.
- **A user with no password set tries password sign-in**: refused with the generic credentials
  message, identically to a wrong password on an account that has one. They can still sign in by
  OTP.
- **A user's roles change while they are signed in**: the change takes effect at their next
  session renewal (within 15 minutes) or next sign-in.
- **The SMS gateway is unavailable**: the user is told the code could not be sent and to try again
  later. The failure is recorded, and it does not count toward the rate limit as a delivered code.
- **The email gateway is unavailable** (password-reset-by-email path): the user is told the code
  could not be sent and is offered the SMS option instead. The failure is recorded.
- **Multiple wrong OTP entries**: after 5 wrong entries for one code, that code is invalidated and a
  new one must be requested. This is separate from FR-029's consecutive-*request* limit — wrong
  *verification* attempts against one code do not count as additional requests.
- **The consecutive-request lockout window passes without a successful verification**: the count
  resets to zero and requests are allowed again — the lockout is not permanent, only time-limited.
- **`otp_policy_settings` is queried but its single configuration row is missing** (a data-integrity
  error, not a normal runtime condition): the system fails loudly rather than silently falling back
  to a guessed default, so misconfiguration is caught immediately rather than masked.
- **A user holding zero roles** (possible only through data error): sign-in is refused with an
  "account not configured, contact your administrator" message.
- **The same email is entered for two users**: rejected. Registered emails are unique across all
  users when present, though an email is optional and only ever used for password-reset delivery —
  never as a sign-in identifier.
- **"Both" is requested for a user with no registered email**: treated as SMS-only — there is no
  eligible email destination to send to, and the request still returns the same neutral response
  (FR-007). The "both" option is not shown by the UI in this case in the first place.
- **A code sent to both channels is entered from the email message after already being used from the
  SMS message**: refused as already used, identically to reusing any other single-use code.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST require authentication for every capability except these public
  ones: password sign-in, OTP request, OTP verification, session renewal, password-reset request
  and completion, and the system status check. Anything not on this list MUST be refused to an
  unauthenticated caller (fail closed). Spec 018 adds one more: the Android app's non-sensitive
  app-configuration response.
- **FR-002**: The system MUST support exactly five fixed roles: Admin, Director, Manager, Teacher,
  System. Roles MUST NOT be creatable, renamable, or deletable by any user.
- **FR-003**: A user MUST hold at least one role and MAY hold several. After sign-in the system
  MUST present all of the user's roles. It MUST NOT ask the user to pick one.
- **FR-004**: Each user MUST have exactly one phone number, unique across all users and normalized
  to a canonical Indian mobile format. It is always a valid sign-in identifier for OTP, and, when a
  password is set, for password sign-in too.
- **FR-005**: Any user with a password set on their account, regardless of role, MUST be able to
  sign in with that password, identified by either their phone number or their username (FR-025) if
  they have one. A user with no password set MUST NOT be able to sign in by password (generic
  failure message, FR-008).
- **FR-006**: Any user, regardless of role and regardless of whether a password is set, MUST be able
  to sign in with a one-time code sent by SMS to their phone number. The code is valid for 5 minutes,
  single-use, and invalidated after 5 wrong entries.
- **FR-007**: The system MUST allow at most 3 code requests per phone number per minute. Code
  requests for numbers that are not eligible MUST get the same neutral response as eligible ones
  and MUST NOT send an SMS.
- **FR-008**: Failed sign-in responses MUST NOT reveal whether a phone number, username, or email
  is registered, nor which part of the submitted credentials was wrong (identifier vs. password vs.
  no password set).
- **FR-009**: A successful sign-in MUST establish a session consisting of short-lived access (15
  minutes) and a renewal credential valid up to 14 days from sign-in. Each renewal issues a new
  renewal credential and invalidates the previous one.
- **FR-010**: If a renewal credential that has already been used is presented again, the system
  MUST revoke that session and every renewal descended from it.
- **FR-011**: The signed-in identity presented to the rest of the system MUST include the user's
  identifier, all of their roles, and their display name. It MUST also include their linked Teacher
  record identifier when they hold the Teacher role and one is linked.
- **FR-012**: After 5 consecutive failed password attempts, the account MUST be locked for 30
  minutes. During the lock, sign-in MUST be refused even with the correct password, and the unlock
  time MUST be shown. A successful sign-in resets the failure count.
- **FR-013**: Lockout threshold, lockout duration, code validity, code rate limit, access period,
  and renewal period MUST be deployment configuration, with the defaults above. There is no UI for
  them in this feature.
- **FR-014**: A signed-in user MUST be able to log out, which ends their current session
  immediately.
- **FR-015**: A signed-in user MUST be able to list their own active sessions (device/browser,
  sign-in time, last activity, current-session marker) and end any of them. They MUST NOT be able to
  see or end other users' sessions.
- **FR-016**: Any user MUST be able to reset their password using a one-time code, delivered to
  their registered phone by SMS, to their registered email, or to both at once — the user's choice
  at the time of the request. Only channels the user actually has registered are offered ("both" is
  offered only when both a phone and an email are on file). When sent to both, the identical code
  is delivered to each; it remains single-use overall — successfully completing a reset with the
  code invalidates it on every channel it was sent to, not just the one used. A successful reset
  MUST end all of that user's sessions and clear any lockout.
- **FR-017**: Passwords MUST be at least 10 characters. They MUST NOT equal the phone number, and
  they MUST NOT be stored in recoverable form.
- **FR-018**: A deactivated user MUST NOT be able to sign in by any method. Deactivation MUST end
  all of the user's sessions so that their next request is refused.
- **FR-019**: The system MUST record a login-history event for every successful sign-in, failed
  sign-in, lockout, logout, session ended by the user, session revoked by reuse detection, code
  request, and password reset. Each event MUST capture the timestamp, the user if known, the
  identifier as entered (phone, username, or email — masked), the method, the outcome, the client IP
  address, and the device description. The events MUST be retained append-only and published for
  the Audit module (spec 003) to surface.
- **FR-020**: On startup, the system MUST create one Admin user and one System user from deployment
  configuration if they do not already exist. It MUST NOT create duplicates or overwrite existing
  passwords, and it MUST NOT fall back to built-in default credentials.
- **FR-021**: The system MUST provide a minimal internal capability to create a user (name, phone,
  roles, optional username, optional email, optional linked Teacher identifier, optional initial
  password) and to deactivate a user. The bootstrap and automated tests use it. Screens for this
  capability are delivered by spec 004.
- **FR-022**: After sign-in, the application MUST show a landing screen with the user's name, all
  of their roles, and a Logout action, plus ACCOUNT → Profile for session management. This
  placeholder is replaced by the role-based dashboard and navigation in spec 002.
- **FR-023**: The sign-in experience MUST be a single page with two clearly labelled options,
  Password (identified by phone number or username) and One-time code (by phone number), plus a
  "Forgot password?" flow offering SMS and, when registered, email as delivery choices. It MUST show
  distinct, plain-language messages for each of these cases: wrong credentials, locked account (with
  unlock time), expired code, too many code requests, deactivated account, and SMS/email delivery
  failure. It MUST NOT show a role picker.
- **FR-024**: The sign-in, password-reset, landing, and Profile screens MUST be built on the
  project's shared design system, with a clean, uncluttered visual style (consistent color tokens,
  spacing, and type) and a light/dark theme toggle that persists across sessions. They MUST be
  usable at phone width without horizontal scrolling, fully keyboard-operable, and meet WCAG 2.2
  AA. Dates and times show as DD/MM/YYYY in Indian Standard Time.
- **FR-025**: A user MAY have a username: 3-30 characters, letters/digits/periods/underscores only,
  unique across all users case-insensitively. It is optional, never auto-derived from the phone
  number or display name, and is set through the internal user-creation capability (FR-021) until
  spec 004 delivers self-service screens for it.
- **FR-026**: A user MAY have a registered email address, unique across all users when present. It
  is used only for password-reset code delivery (FR-016) and never as a sign-in identifier, never
  shown as an alternative to phone/username on the sign-in page itself.
- **FR-027**: Sign-in by password MUST accept either a phone number or a username in the same
  identifier field, resolving which one was entered without requiring the user to say which type it
  is: the system tries a normalized-phone match first, then a username match, and treats a miss on
  both identically to a wrong password (FR-008).
- **FR-028**: Requesting a new one-time code for the same destination MUST be refused with a
  "please wait" message if less than a configurable resend cooldown (default 30 seconds) has passed
  since the previous request for that destination. This applies to both sign-in and password-reset
  OTP requests (User Stories 2 and 5), independently of the existing per-minute rate limit (FR-007).
- **FR-029**: A destination MUST NOT be allowed more than a configurable number of consecutive
  one-time-code requests (default 5) without an intervening successful verification. The request
  that would cross this limit MUST be refused, and further requests for that destination MUST be
  refused for a configurable lockout duration (default 4 hours) from that point. A successful
  verification for that destination resets the count to zero immediately, clearing any lock. The
  resend-cooldown seconds (FR-028), the consecutive-request limit, and the lockout duration MUST be
  read from configuration stored in the database (not fixed deployment config like FR-013's values),
  so they can be retuned without a redeploy; no screen exists yet to edit them (spec 011's job).

### Key Entities

- **User**: a person who can sign in. Attributes: display name, unique normalized phone number,
  optional unique username, optional unique email (reset delivery only), active/deactivated status,
  optional password (protected), optional linked Teacher identifier, failed-attempt count, and
  lock-until time.
- **Role Assignment**: links a User to one of the five fixed roles. A user has one or more.
- **Session**: one signed-in device for a User. It holds the sign-in time, last activity, device
  description, and status (active, ended, revoked), and it owns a chain of renewal credentials.
- **Renewal Credential**: a single-use credential within a Session that is replaced on every
  renewal. Reuse of a replaced credential revokes the Session.
- **One-Time Code**: a short-lived code for sign-in or password reset. It is tied to a delivery
  target (a phone number for sign-in and SMS-based reset; a phone number or an email address for
  reset) and a purpose, and it records its expiry, whether it was used, and how many wrong attempts
  were made.
- **Login-History Event**: an append-only record of an authentication event, with the attributes
  listed in FR-019.
- **OTP Policy Settings**: a single, database-stored configuration record holding the resend
  cooldown, the consecutive-request limit, and the consecutive-request lockout duration (FR-028,
  FR-029). Read fresh on every OTP request; not part of deployment config (FR-013).

## Role & Permission Impact *(mandatory — Constitution Principles II–IV)*

This feature establishes *who the user is and which roles they hold*. It introduces no business
menus and no permission-matrix entries. The matrix and the server-driven navigation arrive in
spec 002.

| Role     | Menu (section → item)                     | Default actions                        | Data scope                   |
| -------- | ----------------------------------------- | -------------------------------------- | ---------------------------- |
| Admin    | ACCOUNT → Profile (my sessions), Logout   | Sign in (password or OTP), manage own sessions | Own sessions only     |
| Director | ACCOUNT → Profile (my sessions), Logout   | Sign in (password or OTP), manage own sessions | Own sessions only     |
| Manager  | ACCOUNT → Profile (my sessions), Logout   | Sign in (password or OTP), manage own sessions | Own sessions only     |
| Teacher  | ACCOUNT → My Profile (my sessions), Logout | Sign in (password or OTP), manage own sessions | Own sessions only    |
| System   | ACCOUNT → Profile (my sessions), Logout   | Sign in (password or OTP), manage own sessions | Own sessions only     |

**New permission keys**: none. Spec 002 will seed `account.profile.view` and related keys when the
matrix is introduced. The minimal user-creation capability (FR-021) is internal only in this
feature and has no user-facing endpoint.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A user signing in by password with valid credentials reaches the post-login landing in
  under 10 seconds from opening the sign-in page. A user signing in by OTP does so in under 60
  seconds including SMS delivery, regardless of role.
- **SC-002**: In 100% of tested cases, the landing shows exactly the roles assigned to the user.
  No user is ever asked to choose a role.
- **SC-003**: 100% of requests to non-public capabilities without a valid session are refused,
  verified by tests covering every capability.
- **SC-004**: 100% of replayed renewal credentials result in the whole session being revoked.
- **SC-005**: After 5 consecutive wrong passwords, 100% of further attempts within 30 minutes are
  refused, including attempts with the correct password.
- **SC-006**: Every authentication event type in FR-019 produces exactly one login-history record,
  verified by tests for each type.
- **SC-007**: A deactivated user's next request is refused within 15 minutes of deactivation at
  most, and immediately for any request that needs session renewal.
- **SC-008**: The sign-in and Profile screens pass an automated WCAG 2.2 AA check with zero
  critical violations, and they render without horizontal scrolling at 360 px width.
- **SC-009**: At least 90% of first-time users in a hallway test sign in successfully on the first
  attempt without help.

## Assumptions

- Password sign-in and OTP sign-in are both available to every role (Constitution v2.3.0); which
  methods a given account can actually use depends only on what is set on it — a password (for
  password sign-in) and always a phone number (for OTP, unconditionally). A username is an optional
  second identifier for password sign-in only; it is never usable for OTP, since OTP delivery is
  SMS-only and needs a phone number.
- The dev-stub SMS/email senders (logging codes locally) remain the default so no test or local run
  needs real credentials. The real SMS provider, MSG91 (research.md §1, §15), is implemented and
  selected via configuration (`hls.sms.provider=msg91` plus an auth key and a DLT-approved template
  ID) — enabling it needs an MSG91 account and DLT template approval, not further code. No real
  email provider is implemented yet; a transactional email provider is still to be procured.
- Registered email is optional and exists solely as a second password-reset delivery channel; it is
  not a sign-in identifier and this spec adds no "sign in with email" method.
- Deactivation takes effect at the next session renewal (at most 15 minutes) for access already
  issued. This is an accepted trade-off of short-lived access. Renewal is refused immediately.
- Login-history events are stored by `identity` and published for spec 003 (Audit), which will
  surface them in AUDIT → Login History. No audit screens are in scope here.
- Phone numbers are Indian mobile numbers (10 digits, optionally prefixed +91 or 0).
- Password policy (minimum 10 characters, not the phone number) is the initial default. It becomes
  editable under Security Settings in spec 011.
- The public system status check stays unauthenticated for operational monitoring.
- Dependencies: none upstream. Specs 002, 003, and 004 depend on this one.
