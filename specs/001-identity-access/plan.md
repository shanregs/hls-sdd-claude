# Implementation Plan: Identity & Access

**Branch**: `001-identity-access` | **Date**: 2026-09-23 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-identity-access/spec.md`

## Summary

Let any user, regardless of role, sign in either with a password (identified by phone number or an
optional username) or with a one-time SMS code to their phone (Constitution v2.3.0), with no manual
role selection, short-lived access plus rotating refresh sessions, lockout, logout, self-service
session management, and password reset by a code delivered to phone (SMS) or, if registered, email —
the user's choice. This spec establishes the `identity` module's authentication core and the
project's shared frontend foundations (theme, form patterns) that spec 002 builds its
access-model/shell work on top of. It
ships a placeholder post-login landing, replaced by spec 002's real dashboards.

## Technical Context

**Language/Version**: Java 25 (backend, Spring Boot 4.1.1-based, per `backend/pom.xml`); TypeScript
5.7 with React 19 (frontend, per `frontend/package.json`).

**Primary Dependencies**:
- Backend: Spring Web, Spring Security (password hashing via `PasswordEncoder`, method security),
  Spring Security OAuth2 Resource Server (validates this spec's own JWTs on every protected
  endpoint), `jjwt` (issues/signs the access token and rotating refresh/renewal credential), Spring
  Data JPA, Flyway (schema), `bucket4j` (OTP/reset-code rate limiting, already in `backend/pom.xml`)
  — all already present in `backend/pom.xml`, none of this is a new dependency. An `EmailGateway`
  interface + dev-stub implementation is added alongside the existing `SmsGateway` (research.md §1),
  mirroring the same pattern; no email-provider SDK is added in this spec (research.md §12).
- Frontend: React Router (routing — first introduced here, reused unchanged by spec 002), MUI /
  Material UI core + `ThemeProvider` (component library and light/dark theming — first introduced
  here; spec 002 reuses these tokens rather than redefining them), `react-hook-form` (sign-in,
  OTP-entry, and password-reset forms — first introduced here, reused by spec 002's later forms).
  These are the same choices spec 002's planning settled on for the whole project, applied here
  first since this spec ships first.

**Storage**: PostgreSQL via Flyway migrations, adding: `app_user` (display name, normalized unique
phone number, optional unique username, optional unique email, password hash, active flag,
failed-attempt count, lock-until), `role_assignment` (user id, one of the 5 fixed roles), `session`
(device description, sign-in time, last activity, status), `renewal_credential` (single-use, chained
to a session), `one_time_code` (delivery target — phone or email — purpose, expiry, used flag,
wrong-attempt count), `login_history_event` (append-only, per FR-019's attribute list).

**Testing**: Backend — JUnit 5, Spring Boot Test (`@WebMvcTest`/`@SpringBootTest`), Spring Security
Test, Spring Modulith Test, ArchUnit (module-boundary check), Testcontainers (PostgreSQL) for
repository/service tests, Awaitility for lockout/expiry timing tests. Frontend — Vitest + React
Testing Library, plus an axe-core-based accessibility check (introduced here for FR-024's WCAG 2.2
AA requirement; spec 002 reuses this same tooling rather than introducing a second one).

**Target Platform**: Browser (desktop, tablet, phone widths) served by the React SPA; backend as the
single Spring Boot deployable per the constitution's deployment constraints.

**Project Type**: Web application (frontend + backend).

**Performance Goals**: Staff reach the landing screen within 10 seconds of valid credentials;
Teachers within 60 seconds including SMS delivery (SC-001). Password/OTP verification endpoints
target p95 < 300ms server-side (internal engineering target supporting SC-001, not itself a spec
requirement).

**Constraints**: fail-closed authorization — every capability except the explicit public list
(FR-001) refuses unauthenticated callers; no credential-enumeration leakage (FR-008); passwords
never stored in recoverable form (FR-017); no built-in default credentials, ever (FR-020); WCAG 2.2
AA and no horizontal scroll at 360px on sign-in/reset/landing/Profile (FR-024); reuse-detected
renewal credentials revoke the entire session chain (FR-010).

**Scale/Scope**: Under 100 users growing ~30%/year (constitution deployment constraint). This spec
covers authentication only — no business-module screens, no permission matrix (spec 002), no user
management UI (spec 004); it exposes only a minimal internal user-creation/deactivation capability
for bootstrap and tests (FR-021).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Principle I (Operational Truth Must Be Auditable)** — PASS. FR-019 requires a login-history
  event for every authentication outcome, append-only, published for spec 003's Audit module —
  this spec is the source of that data, not merely a consumer of it.
- **Principle II (Five Fixed Roles, Configurable Permissions)** — PASS. Exactly five fixed roles,
  auto-detected post-authentication, no role picker (FR-002/FR-003). This spec does not yet build
  the permission matrix (spec 002) — it only establishes who the user is and which roles they hold,
  as spec.md's Role & Permission Impact section states explicitly.
- **Principle IV (Role-Based Experience in One Shared Application)** — PASS for what's in scope:
  sign-in/reset/landing/Profile built on one design system with light/dark theming, WCAG 2.2 AA, no
  horizontal scroll at phone width (FR-024). The landing itself is an explicit placeholder (FR-022),
  superseded by spec 002's real dashboards — this is a documented, intentional partial per spec.md.
- **Principle VII (Modular Monolith)** — PASS. All of this spec's persistence and logic lives in the
  `identity` module; no new bounded context is introduced.
- **Principle IX (Reliability, Testability)** — PASS, with explicit test obligations: lockout,
  reuse-detection, rate-limiting, and fail-closed behavior are each independently testable
  (User Stories 1-6's Independent Test sections), not deferred.
- **Principle X (Security, Identity, Observability)** — PASS, this is the primary scope: JWT access
  + rotating refresh, fail-closed by default, passwords never recoverable, no default credentials,
  structured logging of every authentication event. Directly implements v2.3.0's generalized login
  model — every role gets both phone+OTP and password (phone-or-username) sign-in, and password
  reset by phone or registered email. The backend is the sole enforcement point; there is no menu to
  hide yet since spec 002 has not shipped.
- **Principles III, V, VI, VIII, XI** — not applicable (no data-scope model, payroll, training,
  concurrency, or recruitment content in this spec).

No violations requiring justification. Complexity Tracking is not needed.

## Project Structure

### Documentation (this feature)

```text
specs/001-identity-access/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
│   └── auth-api.md
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
backend/src/main/java/com/hls/identity/
├── user/
│   ├── AppUser.java                       # entity: name, phone, username?, email?, password hash, status, lock state
│   ├── RoleAssignment.java                # entity: user id + one of the 5 fixed roles
│   ├── AppUserRepository.java             # findByPhone, findByUsername (case-insensitive), findByEmail
│   └── UserAdminService.java              # minimal internal create/deactivate (FR-021), no UI
├── auth/
│   ├── PasswordAuthService.java           # any-role sign-in by phone-or-username identifier (FR-005/FR-027)
│   ├── JwtTokenProvider.java              # issues/validates the short-lived access token
│   ├── AuthController.java                # POST /auth/login, /auth/logout
│   └── PasswordResetService.java          # OTP-driven reset via phone or email (FR-016), ends all sessions
├── otp/
│   ├── OneTimeCode.java                   # entity: delivery target (phone/email), purpose, expiry, used, wrong-attempts
│   ├── OneTimeCodeRepository.java
│   ├── OtpService.java                    # request/verify, rate limiting via bucket4j, any role (FR-006)
│   ├── OtpController.java                 # POST /auth/otp/request, /auth/otp/verify
│   ├── SmsGateway.java                    # interface + dev-stub implementation (research.md §1)
│   └── EmailGateway.java                  # interface + dev-stub implementation (research.md §12), reset only
├── session/
│   ├── Session.java, RenewalCredential.java
│   ├── SessionRepository.java, RenewalCredentialRepository.java
│   ├── SessionService.java                # renew, reuse-detection/revocation, list/end, logout
│   └── SessionController.java             # POST /auth/renew, GET/DELETE /me/sessions
├── loginhistory/
│   ├── LoginHistoryEvent.java             # append-only entity, FR-019's attribute list
│   ├── LoginHistoryEventRepository.java
│   └── LoginHistoryPublisher.java         # records + publishes for spec 003 to consume later
└── bootstrap/
    └── BootstrapUserInitializer.java      # creates Admin/System from deployment config on startup

backend/src/main/resources/db/migration/
└── V<next..next+N>__identity_*.sql        # app_user, role_assignment, session, renewal_credential,
                                            # one_time_code, login_history_event

backend/src/test/java/com/hls/identity/
├── auth/, otp/, session/, bootstrap/      # unit + Testcontainers integration tests per user story
└── ArchitectureRulesTest.java             # new: identity's persistence stays inside `identity`

frontend/src/
├── theme/
│   ├── tokens.ts                          # color/spacing/type tokens, light + dark palettes
│   └── ThemeModeProvider.tsx              # toggle + persisted preference (device storage)
├── auth/
│   ├── SignInPage.tsx                     # Staff (password) / Teacher (OTP) tabs, Forgot password
│   ├── OtpEntryPage.tsx
│   ├── ForgotPasswordPage.tsx
│   └── useAuth.ts                         # session state, login/logout/renew calls
├── account/
│   └── ProfilePage.tsx                    # sessions list + end session, reused unchanged by spec 002
└── app/
    └── LandingPlaceholder.tsx             # FR-022's placeholder, replaced by spec 002
```

**Structure Decision**: Web application (Option 2: `backend/` + `frontend/`, the repo's existing
layout). All backend work lands inside the `identity` module (five new sub-packages: `user`, `auth`,
`otp`, `session`, `loginhistory`, plus `bootstrap`) — no new bounded context. Frontend work
establishes the theme and form-handling conventions spec 002 builds on, plus the Profile screen spec
002 explicitly reuses rather than rebuilding.

## Complexity Tracking

> Fill ONLY if Constitution Check has violations that must be justified

No violations. Table intentionally omitted.
