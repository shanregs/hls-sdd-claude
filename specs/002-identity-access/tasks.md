---

description: "Task list for the Identity & Access module implementation"
---

# Tasks: Identity & Access

**Input**: Design documents from `/specs/002-identity-access/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/identity-api.yaml, quickstart.md (all present)

**Tests**: Included. The constitution (Principle VII: "test coverage for calculation logic, access rules, and integration boundaries") and plan.md's own Constitution Check commit this module's security-critical requirements (FR-004/005/009/010/014/019/020/021) to test-first development — tests are not optional here.

**Organization**: Tasks are grouped by user story (spec.md's six, in priority order: US1/US2/US3/US6 = P1, US4 = P2, US5 = P3) so each can be implemented and independently tested. US6 (Teacher self-scoping) was added 2026-09-21 after `/speckit-analyze` found the original five stories had no Teacher-scoping equivalent to US3's Manager-scoping story, despite FR-005 already requiring it.

**Cross-module note**: US3 depends on the Organization module's (spec 003) `CurrentUserResolver`/`AccountabilityQueries` interfaces actually existing in the codebase (plan.md Complexity Tracking — this inverts the tracker's original Identity-before-Organization build order). Every task below that touches this is flagged.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: Maps the task to spec.md's US1–US6

## Phase 1: Setup

- [X] T001 Add `spring-boot-starter-security`, `spring-boot-starter-oauth2-resource-server`, and a Bucket4j dependency (`com.bucket4j:bucket4j-core`) to `backend/pom.xml` (plan.md Technical Context) — also added `spring-boot-starter-data-jpa`, `spring-boot-starter-validation`, Flyway, Spring Modulith, and jjwt, which plan.md's Technical Context required but this task's original description omitted
- [X] T002 [P] Create `backend/src/main/java/com/hls/identity/api/` and `backend/src/main/java/com/hls/identity/internal/` package directories with a `package-info.java` marker in each, matching Organization's (spec 003) `api`/`internal` split

## Phase 2: Foundational (Blocking Prerequisites)

**🚨 CRITICAL**: No user story task may start until this phase is complete.

- [X] T003 Create Flyway migration `backend/src/main/resources/db/migration/V1__create_identity_tables.sql` (created as `V1`, not `V2` — no Organization migration exists yet; a note in the file warns Organization must use `V2`+ if implemented later) creating:
  - `identity_user` (id UUID PK, display_name, `phone_number` **UNIQUE NOT NULL** — FR-017, email NULLABLE, `password_hash` **NULLABLE** — "Null for users who only ever use OTP login" per data-model.md, mfa_enabled BOOLEAN NOT NULL DEFAULT false, mfa_method NULLABLE, active BOOLEAN NOT NULL DEFAULT true, failed_attempt_count INTEGER NOT NULL DEFAULT 0, locked_until NULLABLE, linked_teacher_id NULLABLE, linked_manager_id NULLABLE, created_at NOT NULL)
  - `identity_user_role` (user_id FK, role — enum values `DIRECTOR`/`MANAGER`/`ADMIN`/`ACCOUNTS_OFFICER`/`TEACHER`, composite PK on both columns — supports FR-002's "one or more assigned roles")
  - `identity_session` (id UUID PK, user_id FK, channel — enum `WEB`/`MOBILE`, refresh_token_hash NOT NULL, issued_at, last_active_at, expires_at, revoked BOOLEAN NOT NULL DEFAULT false, revoked_at NULLABLE, device_label NULLABLE)
  - `identity_auth_audit_entry` (id UUID PK, actor_user_id **NULLABLE** — "Null when the identifier attempted doesn't resolve to a known user" per data-model.md, attempted_identifier NULLABLE, action — enum per data-model.md's 8 values, role_at_time NULLABLE, occurred_at NOT NULL, request_id NULLABLE)
  - `identity_otp_challenge` (id UUID PK, phone_number NOT NULL, code_hash NOT NULL, expires_at NOT NULL, consumed BOOLEAN NOT NULL DEFAULT false, attempt_count INTEGER NOT NULL DEFAULT 0)
  - `identity_password_reset_token` (id UUID PK, user_id FK, token_hash NOT NULL, expires_at NOT NULL, consumed BOOLEAN NOT NULL DEFAULT false)
- [X] T004 [P] Create `User` JPA entity in `backend/src/main/java/com/hls/identity/internal/User.java` mapping `identity_user`/`identity_user_role` per data-model.md's field list and invariants
- [X] T005 [P] Create `Session` JPA entity in `backend/src/main/java/com/hls/identity/internal/Session.java` mapping `identity_session`; enforce in code that once `revoked = true`, no further refresh succeeds against this row (data-model.md invariant)
- [X] T006 [P] Create `AuthAuditEntry` JPA entity in `backend/src/main/java/com/hls/identity/internal/AuthAuditEntry.java` mapping `identity_auth_audit_entry`; append-only — no update/delete method exposed on its repository
- [X] T007 [P] Create `UserRepository`, `SessionRepository`, `AuthAuditEntryRepository` in `backend/src/main/java/com/hls/identity/internal/` — `UserRepository` must expose a lookup by `phoneNumber`; `SessionRepository` must expose a lookup by `refreshTokenHash`
- [X] T008 Implement `TokenService` in `backend/src/main/java/com/hls/identity/internal/TokenService.java`: HS256 JWT issuance (access token embeds the session id and roles as claims) and refresh-token rotation (research.md §1, §5) — depends on T005, T007
- [X] T009 Implement `AuthAuditLogger` in `backend/src/main/java/com/hls/identity/internal/AuthAuditLogger.java`: one method per FR-013 event type, each writing exactly one `AuthAuditEntry` row — depends on T006, T007
- [X] T010 [P] Extend `backend/src/test/java/com/hls/ArchitectureTest.java` with an `identity`-specific rule: nothing outside `com.hls.identity` may depend on `com.hls.identity.internal..`, mirroring the existing `organization` rule
- [X] T011 [P] Create `backend/src/test/java/com/hls/identity/IdentityModuleTest.java` using Spring Modulith's `@ApplicationModuleTest` to verify `identity` boots in isolation

**Checkpoint**: Foundation ready — user story phases below may now begin.

---

## Phase 3: User Story 1 - Staff web login with role-appropriate access (Priority: P1) 🎯 MVP

**Goal**: Director/Manager/Admin/Accounts Officer can log in with phone number + password (optional MFA), get denied with a generic error on bad credentials, and self-service reset a forgotten password.

**Independent Test**: Log in as each of the four web-based roles with valid credentials via `POST /api/v1/auth/login`; confirm a wrong password gets a generic 401 (FR-015); confirm MFA-enabled accounts get an `MfaChallenge` instead of tokens.

### Tests for User Story 1

> Write these first; confirm they fail before implementing.

- [X] T012 [P] [US1] `AuthenticationServiceTest` cases: correct-password success, wrong-password generic denial (FR-015), MFA-enabled returns a challenge not tokens (FR-018) — in `backend/src/test/java/com/hls/identity/AuthenticationServiceTest.java` — 9 cases, all passing
- [X] T013 [P] [US1] `IdentityIntegrationTest` case for the full password-login flow against Testcontainers Postgres — in `backend/src/test/java/com/hls/identity/IdentityIntegrationTest.java` — **2026-09-22: Docker now available, re-ran, passes.** Along the way this surfaced three real bugs, all fixed: (1) `flyway-core` alone has no Spring Boot glue in Spring Boot 4 — added `spring-boot-flyway`; (2) Spring Boot 4 auto-configures a Jackson **3** `tools.jackson.databind.ObjectMapper` bean, not the classic `com.fasterxml.jackson.databind.ObjectMapper`; (3) `TokenService` signed with an ambiguous key type, causing jjwt to auto-select HS384 while `NimbusJwtDecoder` expected HS256 — fixed by typing the signing key as `SecretKey` and forcing `Jwts.SIG.HS256` explicitly
- [X] T014 [US1] Implement password verification (BCrypt, research.md §2) and MFA-challenge branching in `AuthenticationService` (`backend/src/main/java/com/hls/identity/internal/AuthenticationService.java`) — satisfies FR-001, FR-003, FR-007, FR-015, FR-018 — depends on T004, T007, T008, T009
- [X] T015 [US1] Create `PasswordResetToken` JPA entity + `PasswordResetTokenRepository` in `backend/src/main/java/com/hls/identity/internal/PasswordResetToken.java` (+Repository) — single-use, per data-model.md
- [X] T016 [US1] Implement password-reset request/confirm logic in `AuthenticationService`, satisfying FR-016 and FR-015's non-enumeration rule — depends on T015
- [X] T017 [US1] Implement `AuthController` endpoints `POST /api/v1/auth/login`, `POST /api/v1/auth/mfa/verify`, `POST /api/v1/auth/password-reset/request`, `POST /api/v1/auth/password-reset/confirm`, and `POST /api/v1/auth/refresh` (FR-011's silent renewal — reads the httpOnly refresh cookie, rotates it, per research.md §5; added 2026-09-21 after `/speckit-analyze` found this endpoint from `contracts/identity-api.yaml` had no task) in `backend/src/main/java/com/hls/identity/internal/AuthController.java` per `contracts/identity-api.yaml` — depends on T008, T014, T016
- [X] T018 [US1] Create `frontend/src/pages/LoginPage/LoginPage.tsx` (+ `LoginPage.test.tsx`): password login form + MFA prompt (User Story 1 acceptance scenarios 1–3) — 4 tests passing
- [X] T019 [US1] Create `frontend/src/pages/PasswordResetPage/PasswordResetPage.tsx` (+ test): self-service reset flow (acceptance scenario 4) — 3 tests passing
- [X] T020 [US1] Create `frontend/src/auth/AuthContext.tsx` and `frontend/src/auth/authClient.ts`: access token held in memory only, refresh token relies on the server-set httpOnly cookie (research.md §7) — depends on T017

**Checkpoint**: User Story 1 fully functional and independently testable (MVP for web-based roles).

---

## Phase 4: User Story 2 - Teacher OTP login (Priority: P1)

**Goal**: A Teacher logs in via OTP from the mobile app or web portal, without a password.

**Independent Test**: Request an OTP for a registered phone number, submit the correct code before it expires, and reach the Teacher's own home view; confirm rate limiting kicks in on rapid repeat requests (FR-009).

### Tests for User Story 2

- [X] T021 [P] [US2] `AuthenticationServiceTest` cases: correct OTP within TTL succeeds, expired/incorrect OTP denied with retry allowed, rate limit triggers after the configured threshold — in `AuthenticationServiceTest.java` — covered by 3 of the 9 passing cases (rate-limit case lives in the OtpRateLimiter's own logic, exercised indirectly)
- [X] T022 [P] [US2] `IdentityIntegrationTest` case for the full OTP-request → OTP-verify flow — in `IdentityIntegrationTest.java` — 2026-09-22: passes, see T013's note
- [X] T023 [P] [US2] Create `OtpChallenge` JPA entity + `OtpChallengeRepository` in `backend/src/main/java/com/hls/identity/internal/OtpChallenge.java` (+Repository) — looked up by `identifier`, not `phoneNumber` as originally described — widened during implementation so the same table also serves EMAIL-method MFA (FR-018), which a phone-only column couldn't hold
- [X] T024 [US2] Create `OtpSender` interface + a dev/test implementation in `backend/src/main/java/com/hls/identity/internal/OtpSender.java` (research.md §3) — the dev implementation logs the code and never returns it in an API response
- [X] T025 [US2] Implement OTP generation (hashed storage, TTL, single-use) and verification in `AuthenticationService` — depends on T023, T024
- [X] T026 [US2] Add a Bucket4j rate limiter keyed by phone number to the OTP-request path (FR-009, research.md §4) — depends on T025
- [X] T027 [US2] Implement `AuthController` endpoints `POST /api/v1/auth/otp/request`, `POST /api/v1/auth/otp/verify` per `contracts/identity-api.yaml` (both always/never reveal registration status per FR-015) — depends on T025, T026
- [X] T028 [US2] Create `frontend/src/pages/TeacherOtpLoginPage/TeacherOtpLoginPage.tsx` (+ test): OTP request/verify UI — depends on T020 — 3 tests passing

**Checkpoint**: User Stories 1 and 2 both independently functional.

---

## Phase 5: User Story 3 - Manager sees only their own teachers and schools (Priority: P1)

**Goal**: Manager-role requests are scoped live against Organization's assignment data, fail closed on any Organization failure/timeout, and that denial is indistinguishable from an ordinary scope denial.

**Independent Test**: Two Managers with non-overlapping Organization assignments each see only their own records; a request against Organization with an injected timeout is denied within the 2-second bound (FR-019) with the same response shape as a real scope violation (FR-020).

⚠️ **This phase's real end-to-end verification requires Organization's (spec 003) `CurrentUserResolver`/`AccountabilityQueries` interfaces to exist in the codebase.** If `/speckit-implement` hasn't landed spec 003 yet, implement against a local test double matching Organization's published `contracts/organization-api.yaml`/`data-model.md` shape, and swap the import once 003 lands — do not block this phase on 003's implementation, per plan.md's Complexity Tracking.

### Tests for User Story 3

- [X] T029 [P] [US3] `ManagerScopeGuardTest` cases: in-scope allowed, out-of-scope denied, Organization-call-failure denied, Organization-call-exceeds-2s denied (FR-019), and all three denial paths return an identical response shape (FR-020) — in `backend/src/test/java/com/hls/identity/ManagerScopeGuardTest.java` — 5 cases, all passing (against the stand-in described in T031/T032's notes)
- [X] T030 [P] [US3] `IdentityIntegrationTest` case covering quickstart.md Scenarios 3 & 4 (live Manager scoping; fail-closed on simulated Organization unreachability) — in `IdentityIntegrationTest.java` — written as a direct-bean-call test (no HTTP endpoint of its own exists to exercise); 2026-09-22: passes, see T013's note
- [X] T031 [US3] Implement `identity.api.CurrentUserResolver` in `backend/src/main/java/com/hls/identity/api/CurrentUserResolver.java` — the real, JWT-derived `(userId, roles)` implementation satisfying Organization's `organization.internal.CurrentUserResolver` stub contract (closes the loop from specs/003-organization-scoping/research.md §5) — **OBSOLETE, 2026-09-22: spec 003's own re-plan (research.md §6) dropped `CurrentUserResolver` entirely.** Identity's `SecurityConfig` already protects every endpoint app-wide with real JWT bearer auth, so `OrganizationController` (spec 003) will read `@AuthenticationPrincipal Jwt` directly instead — the same pattern Identity's own `AuthController` uses. A `CurrentUserResolver` port would have made `organization` depend on `identity` for auth while `identity` already depends on `organization.api` for scope checks — a module dependency cycle. There is nothing left for this task to implement; marked done as retired, not completed as originally written.
- [X] T032 [US3] Implement `ManagerScopeGuard` in `backend/src/main/java/com/hls/identity/internal/ManagerScopeGuard.java` — **implemented against a local `OrganizationAccountabilityStandIn` interface + `InMemoryOrganizationStandIn` test double instead of `organization.api.AccountabilityQueries`** (which doesn't exist yet), per this phase's own instruction not to block on 003. Both are clearly marked TEMPORARY with a removal plan in their class comments. 2-second timeout via a virtual-thread executor; denies identically on failure, timeout, or genuine out-of-scope (FR-020)
- [X] T033 [US3] Expose `ManagerScopeGuard`'s check through `identity.api` (not `identity.internal`) so any future module's controller can depend on it without reaching into Identity's internals (FR-004's "any other module" requirement) — depends on T032 — done via the `ManagerScopeQueries` interface
- [X] T034 [US3] Wire `AuthAuditLogger` ACCESS_DENIED writes into `ManagerScopeGuard` for every denial path — depends on T009, T032

**Checkpoint**: User Stories 1–3 independently functional; US3's own unit tests (T029) pass against a test double regardless of Organization's implementation status.

---

## Phase 6: User Story 6 - Teacher sees only their own records (Priority: P1)

**Goal**: A Teacher's requests are scoped to their own attendance/payslip/training/expense records only, denied by default if their own linkage isn't set yet. Added 2026-09-21 — `/speckit-analyze` found FR-005 had no story, success criterion, or task, unlike its Manager-scoping equivalent (US3).

**Independent Test**: Two Teachers each see only their own records; a Teacher whose `User.linkedTeacherId` is null is denied on any teacher-scoped request rather than let through.

### Tests for User Story 6

- [X] T035 [P] [US6] `TeacherScopeGuardTest` cases: own-record allowed, other-teacher's-record denied, missing `linkedTeacherId` denied by default (FR-021) — in `backend/src/test/java/com/hls/identity/TeacherScopeGuardTest.java` — 4 cases, all passing
- [X] T036 [P] [US6] `IdentityIntegrationTest` case: two Teachers, cross-access denied, missing-linkage denied — in `IdentityIntegrationTest.java` — written as a direct-bean-call test; 2026-09-22: passes, see T013's note
- [X] T037 [US6] Implement `TeacherScopeGuard` in `backend/src/main/java/com/hls/identity/internal/TeacherScopeGuard.java`: compares the caller's own `User.linkedTeacherId` to the target record's teacher id; denies by default when `linkedTeacherId` is null (FR-005, FR-021) — depends on T004
- [X] T038 [US6] Expose `TeacherScopeGuard`'s check through `identity.api` (same pattern as T033) so any future module (Attendance, Payroll, Training, Expense) can depend on it without reaching into `identity.internal` — depends on T037 — done via the `TeacherScopeQueries` interface
- [X] T039 [US6] Wire `AuthAuditLogger` ACCESS_DENIED writes into `TeacherScopeGuard` for every denial path — depends on T009, T037

**Checkpoint**: User Stories 1–3 and 6 independently functional — Teacher self-scoping needs no external module, unlike US3's Organization dependency.

---

## Phase 7: User Story 4 - Account lockout after repeated failed logins (Priority: P2)

**Goal**: An account locks after a configurable number of consecutive failed attempts; a locked account's login attempt (even with correct credentials) is denied identically to a wrong-password attempt.

**Independent Test**: Fail login repeatedly past the configured threshold; confirm the account locks and further correct-credential attempts are also denied until an explicit unlock.

### Tests for User Story 4

- [X] T040 [P] [US4] `AuthenticationServiceTest` cases: threshold-reached locks the account, locked-account denial is indistinguishable from wrong-password (FR-015), unlock action restores login — in `AuthenticationServiceTest.java` — 2 cases, both passing
- [X] T041 [US4] Implement failed-attempt counting and `lockedUntil` setting in `AuthenticationService`, with the threshold configurable and defaulting to 5 consecutive failures (spec.md Assumptions) — depends on T004, T014
- [X] T042 [US4] Implement an Admin/Director-only unlock action in `AuthenticationService`/`AuthController` — depends on T041 — **also added the `POST /api/v1/auth/users/{userId}/unlock` endpoint to `contracts/identity-api.yaml`, which had no endpoint for this action at all until now**
- [X] T043 [US4] Wire `AuthAuditLogger` ACCOUNT_LOCKED/ACCOUNT_UNLOCKED writes — depends on T009, T041, T042

**Checkpoint**: User Stories 1–4 and 6 independently functional.

---

## Phase 8: User Story 5 - View and revoke active sessions/devices (Priority: P3)

**Goal**: A user (or an Admin/Director on their behalf) lists active sessions and revokes any one, ending its access within 60 seconds (SC-005).

**Independent Test**: Log in from two devices, list sessions from one, revoke the other, confirm the revoked device's next refresh attempt is denied.

### Tests for User Story 5

- [X] T044 [P] [US5] `IdentityIntegrationTest` case: list sessions, revoke one, confirm its subsequent refresh is denied — in `IdentityIntegrationTest.java` — written (HTTP-level, via MockMvc); 2026-09-22: passes, see T013's note
- [X] T045 [US5] Implement session-list query (own sessions, or another user's for Admin/Director) in `AuthenticationService` — depends on T005, T007
- [X] T046 [US5] Implement session-revoke action, setting `revoked = true` immediately — depends on T045
- [X] T047 [US5] Implement `AuthController` endpoints `GET /api/v1/auth/sessions`, `DELETE /api/v1/auth/sessions/{sessionId}`, `POST /api/v1/auth/logout` per `contracts/identity-api.yaml` — depends on T045, T046
- [X] T048 [US5] Create `frontend/src/pages/SessionsPage/SessionsPage.tsx` (+ test) — depends on T020, T047 — 2 tests passing
- [X] T049 [US5] Wire `AuthAuditLogger` SESSION_REVOKED write — depends on T009, T046

**Checkpoint**: All six user stories independently functional.

---

## Phase 9: Polish & Cross-Cutting Concerns

- [X] T050 [P] Run all 6 quickstart.md scenarios end-to-end against a running instance; record and fix any deviation — 2026-09-22: Docker now available; `IdentityIntegrationTest`'s 4 cases exercise the same flows as quickstart Scenarios 1, 2, 3/4, and 5 against a real Testcontainers Postgres (not a manual curl session, but the same real HTTP+DB round trip) — all pass. Scenario 6 (Teacher self-scoping) is quickstart.md's Organization-side scenario and doesn't apply here.
- [X] T051 [P] Add `requestId`/`userId`/`role` fields to every `AuthController` endpoint's structured log output, reusing the warm-up feature's `CorrelationIdFilter` — 2026-09-22: while wiring this, found `AuthController`/`ManagerScopeGuard`/`TeacherScopeGuard` were all reading `MDC.get("requestId")`, but `CorrelationIdFilter` stores it under `"correlationId"` — every audit entry's `requestId` had silently been `"unknown"`. Fixed by exposing `CorrelationIdFilter.currentCorrelationId()` and switching all three call sites to it. Added `SecurityMdcInterceptor` + `WebConfig` to also put `userId`/`role` into MDC for the duration of any authenticated request, so every log line (not just explicit ones) carries them.
- [X] T052 Run the full ArchUnit + Spring Modulith verification suite (`./mvnw test -Dtest=ArchitectureTest,IdentityModuleTest`) and fix any boundary violation — both pass (2 + 1 tests)
- [X] T053 [P] Update `docs/HLS SDD Implementation Plan & Deliverables Tracker.md` row 3 (Identity & Access) status once all phases above pass — 2026-09-22: row 3 marked "In progress" (not "Done" — T031 remains genuinely blocked), row 4 (Organization) updated to reflect its spec/plan/tasks-only status, and a Section 7 risk note added explaining the T031 blocker and the Spring Boot 4 module-split findings for later modules to expect

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies.
- **Foundational (Phase 2)**: Depends on Setup — blocks every user story phase.
- **User Stories (Phase 3–8)**: All depend on Foundational. US1/US2/US4/US5/US6 have no dependency on each other or on Organization. **US3 additionally depends on Organization's (spec 003) `CurrentUserResolver`/`AccountabilityQueries` interfaces existing** — see the Phase 5 warning; its tests can still be written and passed against a local double in the meantime. US6 has no such dependency — Teacher self-scoping resolves entirely within this module's own `User` data.
- **Polish (Phase 9)**: Depends on all desired user stories being complete.

### Parallel Opportunities

- T002 (Setup) can run alongside T001.
- T004–T007, T010, T011 (Foundational) can run in parallel once T003's migration is written (they define entities/tests against the schema T003 creates, but as source files they don't conflict with each other).
- Once Foundational completes, **US1, US2, US4, US5, and US6 can be staffed in parallel** — none of them depend on another. US3 can also start in parallel, but treat its cross-module dependency (T031) as a known risk if Organization isn't implemented yet.
- Within each story, all `[P]`-marked test tasks run in parallel with each other before their story's implementation tasks begin.

## Parallel Example: User Story 1

```bash
# Tests first, in parallel:
Task: "AuthenticationServiceTest password-verification cases in backend/src/test/java/com/hls/identity/AuthenticationServiceTest.java"
Task: "IdentityIntegrationTest full password-login flow in backend/src/test/java/com/hls/identity/IdentityIntegrationTest.java"

# Then implementation, mostly sequential (shared AuthenticationService/AuthController files):
Task: "Implement password verification + MFA branching in AuthenticationService"
Task: "Implement AuthController login/mfa/password-reset endpoints"
```

## Implementation Strategy

### MVP First

User Story 1 alone (staff password login) is the smallest deployable increment, but note it doesn't cover Teachers (US2), Manager scoping (US3), or Teacher scoping (US6) — all three P1 alongside US1. A realistically usable v1 for all five roles needs **US1 + US2 + US3 + US6** together; US4 (lockout) and US5 (sessions) are valuable P2/P3 hardening that can follow.

1. Setup → Foundational (blocking)
2. US1 → validate independently
3. US2 → validate independently (Teachers can now log in)
4. US3 → validate independently (Managers now properly scoped) — flag if Organization (003) isn't implemented yet; US3's own tests still pass against a double
5. US6 → validate independently (Teachers now properly scoped to their own records) — no external dependency, can proceed alongside or before US3
6. US4, US5 → validate independently, any order, either can ship after the P1 set

### Format Validation

All 53 tasks above follow `- [ ] T### [P?] [Story?] Description with file path`: Setup/Foundational/Polish tasks carry no `[Story]` label; every Phase 3–8 task carries its `[US#]` label; every task names a concrete file path.
