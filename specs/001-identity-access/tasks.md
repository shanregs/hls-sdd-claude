---

description: "Task list for feature implementation"
---

# Tasks: Identity & Access

**Input**: Design documents from `/specs/001-identity-access/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/auth-api.md, quickstart.md

**Tests**: included as first-class tasks — Constitution Principle IX requires test coverage for
access rules and integration boundaries, and this spec's own Independent Test per user story is
exactly that kind of test.

**Organization**: tasks are grouped by user story (spec.md's US1-US6, in priority order).

## Path Conventions

Web application per plan.md: `backend/src/main/java/com/hls/identity/...` (Java/Spring) and
`frontend/src/...` (React/TypeScript).

---

## Phase 1: Setup

- [x] T001 Add `react-router-dom`, `@mui/material`, `@mui/icons-material`, `@emotion/react`,
      `@emotion/styled`, and `react-hook-form` to `frontend/package.json` dependencies and install
      them. (These become the project's standing choices — spec 002 reuses them.)
- [x] T002 [P] Add an axe-core-based accessibility testing dependency (e.g. `vitest-axe`) to
      `frontend/package.json` devDependencies for the WCAG 2.2 AA checks required by FR-024/SC-008.
      (Spec 002 reuses this same setup.)
- [x] T003 [P] Run `mvn -q -pl backend compile` and `npm --prefix frontend run build` to confirm
      both projects build cleanly before adding this feature's code.

**Checkpoint**: dependencies installed, both projects build.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: user/role storage, session/renewal storage, login-history storage, JWT issuance, the
fail-closed security baseline, and the frontend's theme/auth-context scaffolding every user story
below builds on.

**🚨 CRITICAL**: no user story task may start until this phase is complete.

- [x] T004 Create Flyway migration(s) for `app_user` and `role_assignment` in
      `backend/src/main/resources/db/migration/`, per data-model.md: `app_user` (id, display_name,
      phone UNIQUE, password_hash nullable, active default true, linked_teacher_id nullable,
      failed_attempt_count default 0, lock_until nullable); `role_assignment` (id, user_id FK,
      role enum, UNIQUE `(user_id, role)`).
- [x] T005 [P] Create `AppUser` entity in
      `backend/src/main/java/com/hls/identity/user/AppUser.java` matching T004's columns.
- [x] T006 [P] Create `RoleAssignment` entity in
      `backend/src/main/java/com/hls/identity/user/RoleAssignment.java` with `role` restricted to
      `ADMIN`, `DIRECTOR`, `MANAGER`, `TEACHER`, `SYSTEM` (Constitution Principle II — fixed set).
- [x] T007 Create `AppUserRepository` and `RoleAssignmentRepository` in
      `backend/src/main/java/com/hls/identity/user/`. Depends on T005, T006.
- [x] T008 [P] Implement the Indian mobile phone normalizer (strip spaces/hyphens, strip a leading
      `+91` or `0`, validate the remaining 10 digits) in
      `backend/src/main/java/com/hls/identity/user/PhoneNumberNormalizer.java` (research.md §6,
      FR-004).
- [x] T009 Implement `UserAdminService.createUser(displayName, phone, roles, linkedTeacherId,
      initialPassword)` and `.deactivateUser(userId)` in
      `backend/src/main/java/com/hls/identity/user/UserAdminService.java` (FR-021, internal-only,
      no HTTP endpoint in this spec). `createUser` MUST reject a duplicate normalized phone
      (FR-004). Depends on T007, T008.
- [x] T010 Create Flyway migration for `session` and `renewal_credential`, per data-model.md:
      `session` (id, user_id FK, device_description, signed_in_at, last_activity_at, status enum
      `ACTIVE`/`ENDED`/`REVOKED`); `renewal_credential` (id, session_id FK, credential_hash,
      issued_at, used_at nullable, superseded_by nullable self-FK).
- [x] T011 [P] Create `Session` entity + `SessionRepository` in
      `backend/src/main/java/com/hls/identity/session/`.
- [x] T012 [P] Create `RenewalCredential` entity + `RenewalCredentialRepository` in
      `backend/src/main/java/com/hls/identity/session/`, storing `credential_hash` only, never the
      plain credential (research.md §7).
- [x] T013 Implement `SessionService.createSession(user, deviceDescription)` in
      `backend/src/main/java/com/hls/identity/session/SessionService.java`: creates a `Session` and
      its first `RenewalCredential`, returning the plain (unhashed) credential value for the caller
      to set as a cookie. Renewal/reuse-detection logic is added in User Story 3 (T041). Depends on
      T011, T012.
- [x] T014 Create Flyway migration for `login_history_event` (append-only; no update/delete
      permitted at the application layer) per data-model.md's full column/enum list.
- [x] T015 [P] Create `LoginHistoryEvent` entity + `LoginHistoryEventRepository` in
      `backend/src/main/java/com/hls/identity/loginhistory/`.
- [x] T016 Implement `LoginHistoryPublisher.record(...)` in
      `backend/src/main/java/com/hls/identity/loginhistory/LoginHistoryPublisher.java`: persists the
      event and publishes a Spring Modulith application event (`LoginHistoryRecorded`) for spec
      003's Audit module to subscribe to later (FR-019, research.md §9). Depends on T015.
- [x] T017 Implement `JwtTokenProvider` in
      `backend/src/main/java/com/hls/identity/auth/JwtTokenProvider.java` using `jjwt`: issues a
      15-minute access token carrying user id + resolved roles; validates tokens statelessly
      (research.md §7).
- [x] T018 Configure Spring Security in
      `backend/src/main/java/com/hls/identity/security/SecurityConfig.java`: OAuth2 Resource Server
      validation of `JwtTokenProvider`'s tokens; deny-by-default for every endpoint except the
      public list in FR-001 (`/auth/login`, `/auth/otp/request`, `/auth/otp/verify`,
      `/auth/renew`, `/auth/password-reset/complete`, and the system status check). Depends on
      T017.
- [x] T019 [P] ~~Add `ArchitectureRulesTest` (ArchUnit)~~ Superseded: the repo already has
      `backend/src/test/java/com/hls/ApplicationModulesTest.java`, which runs Spring Modulith's own
      `ApplicationModules.verify()` across the whole module graph. Modulith already treats
      `identity`'s sub-packages as internal (inaccessible from other modules) by default, so a
      second, narrower ArchUnit test would duplicate that guarantee rather than add one.
- [x] T020 [P] Implement `ThemeModeProvider` and design tokens in
      `frontend/src/theme/ThemeModeProvider.tsx` and `frontend/src/theme/tokens.ts`: MUI
      `ThemeProvider` with light/dark palettes from one token set, read from `localStorage` before
      first paint, sensible default when storage is unavailable (FR-024).
- [x] T021 [P] Implement `useAuth` context skeleton in `frontend/src/auth/useAuth.ts`: holds the
      access token in memory only (never `localStorage`/`sessionStorage`, research.md §3), exposes
      `login`, `logout`, `renew` call stubs wired to the endpoints in `contracts/auth-api.md`.

**Checkpoint**: user/role/session/login-history storage, JWT issuance, the fail-closed security
baseline, and frontend theme/auth-context scaffolding all exist. User story work can now begin.

---

## Phase 3: User Story 1 - Staff Log In and Are Recognized by Role (Priority: P1) 🎯 MVP

**Goal**: an Admin/Director/Manager/System user signs in with phone + password and is recognized by
all of their roles, with no role picker.

**Independent Test**: create a staff user holding one or more roles, sign in, confirm the landing
shows their name and exactly their roles, and confirm no role picker ever appears.

### Tests for User Story 1

- [x] T022 [P] [US1] Backend integration test in
      `backend/src/test/java/com/hls/identity/auth/PasswordAuthIntegrationTest.java`: correct
      credentials sign in and show all held roles (single- and multi-role); wrong password and an
      unregistered phone both produce the identical generic message (FR-008, Acceptance Scenarios
      1-3, 5).
- [x] T023 [P] [US1] Frontend test in `frontend/src/auth/SignInPage.test.tsx` (Staff tab): submits
      phone/password, shows the generic error on failure, never renders a role-selection control.

### Implementation for User Story 1

- [x] T024 [US1] Implement `PasswordAuthService.authenticate(phone, password)` in
      `backend/src/main/java/com/hls/identity/auth/PasswordAuthService.java`: BCrypt verification
      (research.md §2), generic failure for wrong password/unregistered/no-password-set (FR-008,
      edge cases). Depends on T007, T008.
- [x] T025 [US1] Implement `AuthController` in
      `backend/src/main/java/com/hls/identity/auth/AuthController.java`:
      `POST /api/v1/auth/login` (calls `PasswordAuthService`, then `SessionService.createSession`
      and `JwtTokenProvider`, sets the `HttpOnly`/`Secure`/`SameSite=Strict` renewal cookie per
      `contracts/auth-api.md`) and `POST /api/v1/auth/logout`. Depends on T013, T017, T024.
- [x] T026 [US1] Implement `SignInPage.tsx` in `frontend/src/auth/SignInPage.tsx`: Staff (password)
      tab fully wired to `useAuth.login`; the Teacher (OTP) tab exists as a stub, wired in User
      Story 2. No role-picker UI at any point (FR-023).
- [x] T027 [US1] Implement `LandingPlaceholder.tsx` in `frontend/src/app/LandingPlaceholder.tsx`
      showing the signed-in user's name, all of their roles, and Logout (FR-022) — replaced by spec
      002's real dashboards.
- [x] T028 [US1] In `AuthController`'s login/logout handlers, call `LoginHistoryPublisher.record(...)`
      for `SIGN_IN_SUCCESS`, `SIGN_IN_FAILURE`, and `LOGOUT` (FR-019 subset for this story). Depends
      on T016, T025.

**Checkpoint**: User Story 1 is independently functional — staff can sign in, be recognized by
role, see a placeholder landing, and log out.

---

## Amendment Phase: Generalize Password Login to Phone-or-Username (Constitution v2.3.0)

**Purpose**: User Story 1 originally shipped as phone-only, staff-only password login. This phase
reworks the already-built T004/T005/T007/T009/T024/T025/T026 to add the optional username/email
identity fields and phone-or-username resolution, per the amended spec.md/data-model.md. It must
complete before Phase 4 (US2) and Phase 7 (US5) below, which build on the amended `AppUser`/
`UserAdminService`.

- [x] T028a Extend the `app_user` Flyway migration (or add a follow-up migration) with `username`
      (nullable, UNIQUE case-insensitively via a maintained `username_lower` column) and `email`
      (nullable, UNIQUE) per data-model.md's amended AppUser table.
- [x] T028b Add `username`, `usernameLower`, and `email` fields (with getters/a setter for
      username+email) to `AppUser.java`; keep `usernameLower` in sync whenever `username` is set.
- [x] T028c Add `findByUsernameLower(String)` and `findByEmail(String)` to `AppUserRepository`.
- [x] T028d Extend `UserAdminService.createUser(...)` with optional `username` and `email`
      parameters, rejecting a duplicate `username_lower` or `email` (FR-025, FR-026).
- [x] T028e Rework `PasswordAuthService.authenticate(identifier, password)` per research.md §13: try
      a normalized-phone lookup first, fall back to a case-insensitive username lookup on any miss
      (including a normalization failure), and produce the identical generic failure regardless of
      which lookup (or the password check) failed.
- [x] T028f Rename `AuthController`'s login request field from `phone` to `identifier`
      (contracts/auth-api.md) and update its generic failure message to
      "Your phone number/username or password is incorrect."
- [x] T028g Update `SignInPage.tsx`'s Password mode to one "Phone number or username" field (no
      longer a Staff-only tab), consistent with FR-023/FR-027.
- [x] T028h Update `PasswordAuthIntegrationTest` and `SignInPage.test.tsx` for the `identifier`
      field and add a username-login case alongside the existing phone-login case.

**Checkpoint**: password sign-in works by phone or username, any role; ready for Phase 4 (US2) and
Phase 7 (US5) to build on the amended user model.

---

## Phase 4: User Story 2 - Any User Signs In With a One-Time Code (Priority: P1)

**Goal**: a user of any role signs in with a phone number and an SMS one-time code, no password
needed (Constitution v2.3.0 — OTP is no longer Teacher-only).

**Independent Test**: request a code for any active user's phone (dev SMS stub), enter it, confirm
sign-in with all of that user's roles.

### Tests for User Story 2

- [x] T029 [P] [US2] Backend integration test in
      `backend/src/test/java/com/hls/identity/otp/OtpLoginIntegrationTest.java`: valid code within 5
      minutes signs in *any role, including one with a password already set*; expired code refused;
      reused code refused; a 4th request within a minute is rate-limited with no SMS sent; an
      ineligible/unregistered phone gets the same neutral confirmation as an eligible one
      (Acceptance Scenarios 1-6).
- [x] T030 [P] [US2] Frontend test in `frontend/src/auth/SignInPage.test.tsx` (One-time code mode)
      and `frontend/src/auth/OtpEntryPage.test.tsx`: request-then-verify flow, expired/wrong-code
      messages.

### Implementation for User Story 2

- [x] T031 [US2] Create Flyway migration for `one_time_code` (`channel` enum `SMS`/`EMAIL`,
      `destination` text, `purpose` enum `SIGN_IN`/`PASSWORD_RESET`, code_hash, expires_at, used_at
      nullable, wrong_attempt_count default 0) per data-model.md. `purpose = SIGN_IN` rows are
      always `channel = SMS` (enforced in `OtpService`, not the schema).
- [x] T032 [US2] Create `OneTimeCode` entity + `OneTimeCodeRepository` in
      `backend/src/main/java/com/hls/identity/otp/`. Depends on T031.
- [x] T033 [US2] Implement the `SmsGateway` interface and a logging dev-stub implementation in
      `backend/src/main/java/com/hls/identity/otp/SmsGateway.java` (research.md §1) — MSG91 is the
      real target, swappable behind this interface, not implemented in this spec.
- [x] T034 [US2] Implement `OtpService` in
      `backend/src/main/java/com/hls/identity/otp/OtpService.java`: `request(destination, channel,
      purpose)` — rejecting `channel != SMS` for `purpose = SIGN_IN` — using a `bucket4j` bucket
      keyed by destination (3/minute, FR-007, research.md §4), always returning the neutral
      response, regardless of the account's role or whether it has a password; `verify(destination,
      code, purpose)` enforcing 5-minute expiry, single-use, and invalidation after 5 wrong entries
      (FR-006, edge cases). Depends on T008, T032.
- [x] T035 [US2] Implement `OtpController` in
      `backend/src/main/java/com/hls/identity/otp/OtpController.java`:
      `POST /api/v1/auth/otp/request` and `POST /api/v1/auth/otp/verify`; on successful `SIGN_IN`
      verification, calls `SessionService.createSession` and `JwtTokenProvider` exactly like
      `AuthController`'s login path, for a user of any role. Depends on T013, T017, T034.
- [x] T036 [US2] Wire `SignInPage.tsx`'s "One-time code" mode and `OtpEntryPage.tsx` to
      `POST /auth/otp/request` / `/verify` via `useAuth`, showing the neutral confirmation and the
      expired/too-many-requests messages (FR-023). Depends on T021, T026.
- [x] T037 [US2] Call `LoginHistoryPublisher.record(...)` for `OTP_REQUESTED`,
      `SIGN_IN_SUCCESS`/`SIGN_IN_FAILURE` (method `OTP`) in `OtpController` (FR-019 subset).
      Depends on T016, T035.

**Checkpoint**: User Stories 1 and 2 both work — every role can sign in by either method.

---

## Phase 5: User Story 3 - Sessions Stay Alive Safely (Priority: P2)

**Goal**: access renews silently within the 14-day session; replaying a used renewal credential
revokes the whole chain.

**Independent Test**: let access expire and confirm silent renewal; replay a used renewal
credential and confirm the whole session and its descendants are revoked.

### Tests for User Story 3

- [x] T038 [P] [US3] Backend integration test in
      `backend/src/test/java/com/hls/identity/session/SessionRenewalIntegrationTest.java`: renewal
      within 14 days succeeds and issues a new credential; presenting an already-used credential
      revokes that session and every descendant credential's session; renewal after 14 days is
      refused (Acceptance Scenarios 1-3).
- [x] T039 [P] [US3] Frontend test in `frontend/src/auth/useAuth.test.ts`: an expired access token
      triggers a silent `/auth/renew` call and retries the original request without user-visible
      interruption.

### Implementation for User Story 3

- [x] T040 [US3] Implement `SessionService.renew(renewalCredentialPlainValue)` in
      `SessionService.java`: look up by hash, reject if already `used_at`-set (cascade-revoke that
      session and every credential chained via `superseded_by`), otherwise mark it used, create the
      next credential in the chain, and return it (FR-009/FR-010, research.md §7). Depends on T013.
- [x] T041 [US3] Add `POST /api/v1/auth/renew` to `AuthController` (or a dedicated
      `SessionController`): reads the `HttpOnly` cookie, calls `SessionService.renew`, sets the new
      cookie, returns a new access token (contracts/auth-api.md). Depends on T040.
- [x] T042 [US3] Implement silent renewal in `useAuth.ts`: on a 401 from an expired access token,
      call `/auth/renew` once and retry the original request transparently. Depends on T021.
- [x] T043 [US3] Call `LoginHistoryPublisher.record(...)` for `RENEWAL` and
      `SESSION_REVOKED_REUSE` in the renew path (FR-019 subset). Depends on T016, T041.

**Checkpoint**: User Stories 1-3 work together — sessions renew safely across all sign-in methods.

---

## Phase 6: User Story 4 - Account Lockout After Repeated Failures (Priority: P2)

**Goal**: 5 consecutive wrong passwords lock the account for 30 minutes, with the unlock time shown.

**Independent Test**: enter 5 wrong passwords, confirm the 6th (even correct) attempt is refused as
locked with the unlock time shown, and confirm the count resets after a successful sign-in.

### Tests for User Story 4

- [x] T044 [P] [US4] Backend integration test in
      `backend/src/test/java/com/hls/identity/auth/LockoutIntegrationTest.java` (Awaitility for
      timing): 5th failure locks for 30 minutes; correct password during lock is still refused;
      count resets to 0 after success; sign-in succeeds once the lock window passes (Acceptance
      Scenarios 1-4).

### Implementation for User Story 4

- [x] T045 [US4] Extend `PasswordAuthService` with the lockout rule (FR-012): increment
      `failed_attempt_count` on failure, set `lock_until` (now + 30 minutes) at 5, refuse with a
      423-style result including `lock_until` while locked, reset the count to 0 on success. Depends
      on T024.
- [x] T046 [US4] Show the lockout message and unlock time in `SignInPage.tsx`'s Staff tab. Depends
      on T026, T045.
- [x] T047 [US4] Call `LoginHistoryPublisher.record(...)` for `LOCKOUT` (FR-019 subset). Depends on
      T016, T045.

**Checkpoint**: User Stories 1-4 work together.

---

## Phase 7: User Story 5 - Manage My Sessions and Reset My Password by Phone or Email (Priority: P3)

**Goal**: a signed-in user sees and can end their own sessions from Profile; anyone can reset a
forgotten password by an OTP delivered to their phone or, if registered, their email (FR-016,
Constitution v2.3.0).

**Independent Test**: sign in from two browsers, end one from the other's Profile page; separately,
reset a password once by an SMS code and once by an email code (for a user with both registered),
and sign in with the new password each time.

### Tests for User Story 5

- [x] T048 [P] [US5] Backend integration test in
      `backend/src/test/java/com/hls/identity/session/SessionManagementIntegrationTest.java` and
      `backend/src/test/java/com/hls/identity/auth/PasswordResetIntegrationTest.java`: sessions list
      shows only the caller's own, marks "this device," ending one signs it out; a reset via SMS and
      a reset via email both succeed and end all sessions and clear any lockout; a user with no
      registered email is offered only the SMS channel (Acceptance Scenarios 1-6).
- [x] T049 [P] [US5] Frontend test in `frontend/src/account/ProfilePage.test.tsx` and
      `frontend/src/auth/ForgotPasswordPage.test.tsx` (channel choice rendered only when available).

### Implementation for User Story 5

- [x] T050 [US5] Implement `GET /api/v1/me/sessions` and `DELETE /api/v1/me/sessions/{sessionId}` in
      a `SessionController` (`backend/src/main/java/com/hls/identity/session/SessionController.java`),
      restricted to the caller's own sessions (403 otherwise, FR-015). Depends on T011, T013.
- [x] T050a [US5] Implement the `EmailGateway` interface and a logging dev-stub implementation in
      `backend/src/main/java/com/hls/identity/otp/EmailGateway.java` (research.md §12), used only by
      `PasswordResetService` below.
- [x] T050b [US5] Implement `GET /api/v1/auth/password-reset/channels?identifier=...` in
      `AuthController`, resolving the identifier (phone-or-username, research.md §13) and returning
      `["SMS"]` or `["SMS","EMAIL"]` without revealing whether the identifier is registered
      (contracts/auth-api.md). Depends on T009.
- [x] T051 [US5] Implement `PasswordResetService` in
      `backend/src/main/java/com/hls/identity/auth/PasswordResetService.java`: reuses `OtpService`
      with purpose `PASSWORD_RESET` and the caller's chosen `channel` (`SMS` → phone, `EMAIL` →
      registered email via `EmailGateway`, rejected if that channel isn't registered); on
      completion, updates `password_hash` (BCrypt, ≥10 chars, not equal to phone, FR-017), ends all
      of the user's sessions, and clears `lock_until`/`failed_attempt_count` (FR-016). Depends on
      T009, T034, T045, T050a.
- [x] T052 [US5] Add `POST /api/v1/auth/password-reset/complete` to `AuthController`
      (contracts/auth-api.md). Depends on T051.
- [x] T053 [US5] Implement `ProfilePage.tsx` (`frontend/src/account/ProfilePage.tsx`: sessions list +
      end-session action) and `ForgotPasswordPage.tsx` (`frontend/src/auth/ForgotPasswordPage.tsx`:
      calls the channels endpoint (T050b) and renders only the returned options). This is the exact
      Profile screen spec 002 reuses unchanged. Depends on T020, T050, T050b, T052.
- [x] T054 [US5] Call `LoginHistoryPublisher.record(...)` for `SESSION_ENDED_BY_USER` and
      `PASSWORD_RESET` (FR-019 subset). Depends on T016, T050, T052.

**Checkpoint**: User Stories 1-5 work together.

---

## Amendment Phase: Password-Reset Code to Both SMS and Email at Once (FR-016, research.md §14)

**Purpose**: extends the already-built password-reset path (T031-T034, T050a-T052) so `channel:
"BOTH"` sends one code to phone and email together, consumable from either, single-use across both.

- [x] T054a Add `BOTH` to `OtpChannel` (backend enum) and the frontend `OtpChannel` type.
- [x] T054b Add `OneTimeCodeRepository.findByCodeHashAndPurpose(String, OtpPurpose)`, used to find
      sibling rows created by the same `BOTH` request.
- [x] T054c Rework `OtpService`'s destination resolution (research.md §13) to return a **list** of
      `(channel, destination)` pairs instead of one: `SIGN_IN` → `[(SMS, phone)]`; `PASSWORD_RESET` +
      `SMS`/`EMAIL` → the one matching pair (as before); `PASSWORD_RESET` + `BOTH` → `[(SMS, phone),
      (EMAIL, email)]`, dropping the email pair silently if no email is registered (edge case).
- [x] T054d Rework `OtpService.request(...)` to create one `OneTimeCode` row per resolved pair,
      sharing the same generated code's hash and expiry, and send via the matching gateway for each.
- [x] T054e Rework `OtpService.verify(...)` to check candidates across all resolved pairs (matching
      on any one), and on success mark every unused row sharing that `code_hash` + `purpose` as used
      (T054b's query) — not just the row that matched — so the code cannot complete a second reset
      from the other channel (Acceptance Scenario 7).
- [x] T054f [P] Backend test: requesting `BOTH` for a user with both phone and email delivers the
      same code to both dev-stub gateways; verifying from either succeeds; verifying again from the
      other afterward is refused as already used. A `BOTH` request for a user with no email behaves
      identically to `SMS`.
- [x] T054g Update `ForgotPasswordPage.tsx`: when `GET /password-reset/channels` returns both `SMS`
      and `EMAIL`, offer a third choice, "Both," alongside the two single-channel options; hide it
      when only one channel is available.

**Checkpoint**: password reset supports SMS-only, email-only, and both-at-once, all single-use.

---

## Amendment Phase: Real MSG91 SmsGateway and Delivery-Failure Handling (research.md §15-16)

**Purpose**: replaces the dev-stub-only `SmsGateway` with a real, selectable MSG91 implementation
for production deployments, and closes the previously-unimplemented "gateway is unavailable" edge
case now that a real (fallible) gateway exists.

- [x] T054h Add `SmsDeliveryException` / `EmailDeliveryException` (unchecked) — thrown by a gateway
      implementation when a code could not be sent.
- [x] T054i Implement `Msg91SmsGateway` in
      `backend/src/main/java/com/hls/identity/otp/Msg91SmsGateway.java`: calls MSG91's Flow API
      (`POST /api/v5/flow`, `authkey` header, `template_id` + `recipients[].{mobiles,var1}` body,
      research.md §15) via `RestClient`; wraps any `RestClientException` as `SmsDeliveryException`.
      Active only when `hls.sms.provider=msg91`; `DevStubSmsGateway` stays the default
      (`matchIfMissing = true`).
- [x] T054j Add `hls.sms.provider` (default `dev-stub`) and `hls.sms.msg91.{auth-key,template-id,
      base-url}` to `application.yml`, the latter two with **no defaults** so a `provider=msg91`
      deployment missing either fails fast at startup (Constitution Principle X — no blank-credential
      fallback).
- [x] T054k Rework `OtpService.request(...)` to catch per-destination delivery exceptions: if every
      resolved destination fails, refund the rate-limit token (`bucket.addTokens(1)`) and return the
      new `RequestOutcome.DELIVERY_FAILED`; persist a `OneTimeCode` row only for destinations that
      actually sent (research.md §16).
- [x] T054l Map `DELIVERY_FAILED` in `OtpController` to `503` with a plain-language failure message
      (contracts/auth-api.md) — the one intentionally non-neutral response, safe because a gateway
      outage is not account-specific.
- [x] T054m [P] Unit test `Msg91SmsGateway` against a `MockRestServiceServer`-bound `RestClient`:
      asserts the exact request shape (country-code-prefixed mobile, `var1` = code, `authkey`
      header) and that a server error is wrapped as `SmsDeliveryException`.
- [x] T054n [P] Unit test `OtpService.request(...)` with a fake always-failing `SmsGateway`: asserts
      `DELIVERY_FAILED` is returned, no `OneTimeCode` row is persisted, and the rate-limit bucket is
      not depleted by the failed attempt (three consecutive failing requests all reach the gateway
      rather than the third being rejected as `RATE_LIMITED`).

**Checkpoint**: a real SMS provider can be enabled by configuration alone, with no code change and
no silent failure mode.

---

## Amendment Phase: OTP Request Rate Limiting — Resend Cooldown and Consecutive-Request Lockout (FR-028/FR-029, research.md §17)

**Purpose**: adds a UI resend cooldown and a backend-enforced consecutive-request lockout on top of
the existing FR-007 per-minute bucket, with the three thresholds stored in the database (not fixed
deployment config) so they can be tuned without a redeploy.

- [x] T059a Add Flyway migration `V6__create_otp_policy_settings.sql`: single-row table
      `otp_policy_settings` (`id SMALLINT PRIMARY KEY DEFAULT 1 CHECK (id = 1)`,
      `resend_cooldown_seconds INTEGER NOT NULL DEFAULT 30`, `max_consecutive_requests INTEGER NOT
      NULL DEFAULT 5`, `consecutive_request_lockout_hours INTEGER NOT NULL DEFAULT 4`, `updated_at
      TIMESTAMPTZ NOT NULL DEFAULT now()`), seeded with the one default row (data-model.md).
- [x] T059b [P] Add `OtpPolicySettings` JPA entity and `OtpPolicySettingsRepository extends
      JpaRepository<OtpPolicySettings, Short>` in `backend/src/main/java/com/hls/identity/otp/`.
- [x] T059c Implement `OtpRequestThrottle` (`@Component`) in
      `backend/src/main/java/com/hls/identity/otp/OtpRequestThrottle.java`: in-memory
      `ConcurrentHashMap<String, State>` keyed by `purpose:channel:destination`, evaluating against
      the current `OtpPolicySettings` row and the current instant — resend cooldown check first, then
      consecutive-request-count-vs-lockout check; returns `ALLOWED`/`TOO_SOON`/`LOCKED` with the
      correct `retryAfter` `Duration` for each (research.md §17).
- [x] T059d Rework `OtpService.request(...)` to call `OtpRequestThrottle.evaluate(...)` before the
      existing bucket4j check, returning the new `RequestResult(RequestOutcome, Duration retryAfter)`
      record in place of the bare `RequestOutcome`; add `RESEND_TOO_SOON` and
      `TOO_MANY_CONSECUTIVE_REQUESTS` to `RequestOutcome`. `OtpService.verify(...)` calls
      `OtpRequestThrottle.resetOnSuccessfulVerification(key)` on a successful verification (FR-029).
- [x] T059e Rework `OtpController.request(...)` to switch on `RequestResult.outcome()`, mapping
      `RESEND_TOO_SOON`/`TOO_MANY_CONSECUTIVE_REQUESTS` to `429` with a `Retry-After` header and the
      messages in contracts/auth-api.md ("Please wait N seconds before requesting another code." /
      "Too many requests. Please try again in about N hour(s).").
- [x] T059f [P] Unit test `OtpRequestThrottleTest`: rejects a second request inside the 30s cooldown
      with the correct `retryAfter`; allows a request once the cooldown has passed; locks for 4 hours
      on the 6th consecutive request; the lock clears once the lockout window passes; a successful
      verification resets the counter immediately (research.md §17).
- [x] T059g [P] Update `OtpServiceDeliveryFailureTest` for the new `OtpService` constructor
      (`OtpPolicySettingsRepository`, `OtpRequestThrottle`), using a disabled-throttle
      `OtpPolicySettings(0, 1000, 4)` so the test continues to isolate the bucket4j-refund behavior
      from research.md §16 rather than the new throttle.
- [x] T059h [P] Frontend: add `OtpRequestError` (carries `retryAfterSeconds` parsed from the
      `Retry-After` response header) to `frontend/src/auth/authApi.ts`; extract a shared
      `useCountdown()` hook (`frontend/src/auth/useCountdown.ts`).
- [x] T059i [P] Frontend: wire a disabled "Resend code in {n}s" state into
      `frontend/src/auth/OtpEntryPage.tsx` and `frontend/src/auth/ForgotPasswordPage.tsx`, driven by
      `OtpRequestError.retryAfterSeconds` via `useCountdown()`.

**Checkpoint**: an OTP resend inside the cooldown window is rejected by both the UI (button disabled
with a live countdown) and the API (429 with `Retry-After`); a 6th consecutive request within the
lockout window is refused for the configured number of hours; all three thresholds live in
`otp_policy_settings` and can be changed by an UPDATE statement with no redeploy.

---

## Phase 8: User Story 6 - First Users Exist at Deployment; Deactivated Users Are Shut Out (Priority: P2)

**Goal**: bootstrap Admin/System users exist on first deployment; a deactivated user is shut out
everywhere.

**Independent Test**: deploy with bootstrap configuration and confirm both users can sign in;
deactivate a signed-in user and confirm their session ends and no further sign-in succeeds.

### Tests for User Story 6

- [x] T055 [P] [US6] Backend integration test in
      `backend/src/test/java/com/hls/identity/bootstrap/BootstrapAndDeactivationIntegrationTest.java`:
      fresh startup creates both bootstrap users; a restart does not duplicate them or reset
      passwords; missing bootstrap config is reported clearly with no invented defaults; a
      deactivated user's next request is refused and no sign-in method succeeds for them
      (Acceptance Scenarios 1-4).

### Implementation for User Story 6

- [x] T056 [US6] Implement `BootstrapUserInitializer` (`ApplicationRunner`) in
      `backend/src/main/java/com/hls/identity/bootstrap/BootstrapUserInitializer.java`: reads
      bootstrap phone/password/name from environment configuration, calls
      `UserAdminService.createUser` idempotently for Admin and System, and reports clearly (not
      silently) if configuration is missing (FR-020, research.md §8). Depends on T009.
- [x] T057 [US6] Enforce the `active` flag in `PasswordAuthService`, `OtpService`, and the JWT
      validation path (`SecurityConfig`/`JwtTokenProvider`): a deactivated user's existing session is
      refused on its next request, and no sign-in method succeeds for them (FR-018). Depends on
      T024, T034, T018.
- [x] T058 [US6] Wire `UserAdminService.deactivateUser` to end all of that user's active `Session`
      rows immediately (FR-018). Depends on T009, T013.

**Checkpoint**: all six user stories are independently functional together — this spec is
complete, and spec 002 can now build on a real authenticated principal.

---

## Final Phase: Polish & Cross-Cutting Concerns

- [x] T059 [P] Add an axe-core accessibility suite covering `SignInPage`, `OtpEntryPage`,
      `ForgotPasswordPage`, `LandingPlaceholder`, and `ProfilePage` in both themes, asserting zero
      critical WCAG 2.2 AA violations (FR-024/SC-008).
- [x] T060 [P] Run all six of `quickstart.md`'s manual scenarios end-to-end and record the results.
- [x] T061 [P] Update `docs/spec-roadmap.md` row 001's status to "Implemented" once every checkpoint
      above has passed.
- [x] T062 Run the full backend suite (`mvn test`, including the ArchUnit check from T019) and the
      full frontend suite (`npm run test`, including T059's axe-core checks) together and confirm
      both are green.

---

## Phase: Sessions grid and System session management (added 2026-10-04, User Story 5)

- [x] T063 [US5] Backend: `DELETE /api/v1/me/sessions` ends all of the caller's sessions including
      the current one; list oldest first; `SessionEndingService` ends sessions and records each for
      audit (`SESSION_ENDED_BY_USER` / new `SESSION_ENDED_BY_ADMIN`, `SessionEnded` event) (FR-015)
- [x] T064 [US5] Permissions: new modules `MY_SESSIONS` (View, Delete; seeded true for all five
      roles; the Sessions menu item and the three `/me/sessions` endpoints enforce it) and
      `SESSION_MANAGEMENT` (View, Delete; seeded for System, grantable only to matrix managers)
      in `PermissionModule`, `PermissionEligibility`, `PermissionMatrixService.seedDefaults`
      (idempotent) and `NavigationCatalog` (All Sessions for System) (FR-015, FR-015a)
- [x] T065 [US5] Backend: `AdminSessionController` (`/api/v1/admin/sessions`: list with owner and
      optional user filter, end one, end all of a user or of everyone) (FR-015a)
- [x] T066 [US5] Backend tests in `SessionEndingTest`: list order and current marker, end current,
      end all, another user's session refused, System list and filter, System end one / a user's /
      everyone's including its own, other roles refused, every role holds MY_SESSIONS by default,
      taking Delete or View away from a role is enforced and restorable
- [x] T067 [US5] Frontend: `SessionsTable` (session number, since when, origin, delete icon),
      `SessionsPage` (ACCOUNT → Sessions; delete icon per row and delete-all at the top, each after a
      confirmation, signing out when the current session is among them; no delete icons without
      `MY_SESSIONS.DELETE`), `AllSessionsPage` (System: user filter, per-row delete, delete-all for
      the chosen user or everyone, paging), `sessionOrigin.ts` (browser/OS or Android app + version),
      shared `ConfirmDialog`; the sessions list no longer sits on Profile or Settings
- [x] T068 [US5] Frontend tests (`SessionsPage`, `AllSessionsPage`, `sessionOrigin`) and both pages in
      the axe suite in both themes
- [x] T070 [US1] Sign-in page: a link under the password form switches to the one-time code tab (every role may
      use either method); the demo Teacher has a password too (spec 009 T041)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: no dependencies.
- **Foundational (Phase 2)**: depends on Setup; blocks every user story.
- **User Stories (Phase 3-8)**: all depend on Foundational.
  - **US2-US6 share Foundational's `SessionService.createSession`/`AppUser`/`LoginHistoryPublisher`**
    but do not depend on each other's *code* — each is independently testable per its own scenario.
  - Practically, build in priority order (US1 → US2 → US3 → US4 → US5 → US6) since US1/US2 are the
    only ways to reach a signed-in state that later stories exercise, even though nothing prevents
    parallel staffing once Foundational is done.
- **Polish (Final Phase)**: depends on all six user stories being complete.

### Parallel Opportunities

- Within Foundational: T005/T006 (entities), T008 (phone normalizer), T011/T012 (session entities),
  T015 (login-history entity), T019 (ArchUnit), T020/T021 (frontend theme/auth-context) can run in
  parallel once their own prerequisites land.
- Within each user story: test tasks marked `[P]` run in parallel with each other; independent
  implementation tasks (e.g. backend service vs. frontend page) marked `[P]` run in parallel.

---

## Parallel Example: User Story 1

```bash
# Tests together:
Task: "Backend integration test for staff password sign-in in backend/src/test/java/com/hls/identity/auth/PasswordAuthIntegrationTest.java"
Task: "Frontend test for SignInPage Staff tab in frontend/src/auth/SignInPage.test.tsx"
```

---

## Implementation Strategy

### MVP First (User Stories 1 and 2 Only)

1. Complete Phase 1 (Setup) and Phase 2 (Foundational).
2. Complete Phase 3 (US1) and Phase 4 (US2). At this point every role can sign in and reach the
   placeholder landing — this is the minimum spec 002 needs to build on.
3. **Stop and validate** against spec.md's User Story 1 and 2 acceptance scenarios.

### Incremental Delivery

1. Setup + Foundational → foundation ready.
2. US1 → validate.
3. US2 → validate → MVP demo (every role can sign in).
4. US3 → validate (sessions survive safely).
5. US4 → validate (lockout).
6. US5 → validate (self-service).
7. US6 → validate (bootstrap + deactivation) → spec complete.
8. Final Phase → quickstart run, roadmap update, full suite green.
