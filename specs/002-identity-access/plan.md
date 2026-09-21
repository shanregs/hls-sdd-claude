# Implementation Plan: Identity & Access

**Branch**: `002-identity-access` | **Date**: 2026-09-21 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/002-identity-access/spec.md`

## Summary

The module that authenticates every user (password + optional MFA for Director/Manager/Admin/Accounts Officer, OTP for Teachers) and enforces role-scoped access on every request. Technical approach: Spring Security backs password/JWT/session mechanics; a `User`/`Session`/`AuthAuditEntry` persistence model (no separate `AccessScope` table — per today's clarification, Manager scope is read live from the Organization module's public API, fail-closed on failure); a small set of REST auth endpoints; and, for the first time in this project, real frontend login/OTP screens in the existing `frontend/` React app, since this module is what makes a login screen meaningful at all. React Native mobile app work is explicitly deferred — Teachers can complete OTP login through the web portal for v1 (FR-008 already allows this channel).

## Technical Context

**Language/Version**: Java 25 (backend, unchanged); TypeScript/React (frontend, extending the existing Vite app from the warm-up feature)

**Primary Dependencies**: Spring Boot 4.1.1 — adding `spring-boot-starter-security`, `spring-boot-starter-oauth2-resource-server` (JWT validation), `spring-security-crypto` (BCrypt, transitively via `-security`); Spring Modulith 2.1.1 (`spring-modulith-starter-core`/`-test`, already introduced by Organization — this is the second module to join `ApplicationModules.verify()`); Flyway (next migration after Organization's, exact version number reserved at implementation time — see Complexity Tracking on build order); Bucket4j (in-memory OTP rate limiting per Requirements §12, sufficient at single-EC2 scale); React (extending the existing app with login/OTP/session-management pages)

**Storage**: PostgreSQL — new tables for `User`, `Session`, `AuthAuditEntry`, plus two supporting tables the spec's Key Entities don't name but FR-008/009/016 require: `OtpChallenge` and `PasswordResetToken` (both short-lived, single-use). No `AccessScope` table — that data stays owned by Organization (spec 003) and is read live (Clarifications session 2026-09-21).

**Testing**: JUnit 5 + Spring Boot Test + `spring-security-test` (MockMvc with security context) for auth flows and role-scoping logic; Spring Modulith `@ApplicationModuleTest` for `identity` in isolation; Testcontainers PostgreSQL for the real schema, lockout, and session-revocation paths; ArchUnit for the module-boundary rule; React Testing Library for the new login/OTP/session pages.

**Target Platform**: Single EC2/VM in `ap-south-1`, Docker Compose — unchanged. SMS gateway is accessed through an `OtpSender` interface with a dev/test implementation now; the real India SMS gateway integration is a separate vendor decision already logged as out-of-scope in spec.md's Assumptions.

**Project Type**: Web application (Option 2) — extends both `backend/` and the existing `frontend/`. React Native mobile app bootstrap is explicitly out of scope for this plan (see Assumptions): Teacher OTP login via the web portal satisfies FR-008 without it, and standing up a new mobile project (Expo vs. bare workflow, Android tooling) is a big enough decision to deserve its own plan rather than riding along with Identity's.

**Performance Goals**: SC-001 (<15s staff web login), SC-002 (<60s Teacher OTP login including delivery), SC-005 (<60s session-revocation propagation) — all already numeric in spec.md's Success Criteria.

**Constraints**: FR-019/Edge Case (Organization scope-check unreachable) MUST fail closed — implemented as a bounded-timeout call to Organization's public API, denying on timeout as well as on error; FR-017 (unique phone number) enforced as a DB unique constraint, not just an application check; passwords hashed with BCrypt (Requirements §12), never stored or logged in plaintext; JWT signing key and SMS gateway credentials sourced from environment config only (Constitution Principle VIII) — never committed.

**Scale/Scope**: <100 users total across all five roles — no distributed session store or distributed rate limiter needed; a single in-process/in-instance mechanism suffices for both.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Applies? | Assessment |
|---|---|---|
| I. Operational Truth Must Be Auditable | Yes | `AuthAuditEntry` rows are append-only (FR-013); every login attempt and access-denied event is captured with actor, timestamp, and role, never edited after the fact. |
| II. Role-Scoped Access and Manager Ownership | Yes | This module is the primary mechanism enforcing this principle (FR-002 through FR-006, FR-021). Manager scope is read live from Organization rather than duplicated (Clarifications 2026-09-21), so there is exactly one source of truth for "who can see what," matching the principle's own "prevent cross-manager leakage" language. Teacher self-scoping (FR-005/FR-021) gets its own `TeacherScopeGuard`, resolved entirely within this module's own `User` data — added 2026-09-21 after `/speckit-analyze` found the original design had `ManagerScopeGuard` but no Teacher equivalent. |
| III. Payroll/Receivables/Margin Formula-Driven | No | No financial computation in this module. |
| IV. Training & Substitution Parity | No | Not applicable. |
| V. Modular Monolith With Enforced Boundaries | Yes | Lives in `com.hls.identity`, split into `api`/`internal` like `organization`. This is the second module in `ApplicationModules.verify()`'s coverage; `ArchitectureTest` is extended with an `identity`-specific internal-access rule, mirroring Organization's. |
| VI. Concurrency / Virtual-Thread Batch Processing | No (N/A) | No triggered batch job — login, session, and lockout operations are all simple request-path work, correctly staying on Spring's default model. |
| VII. Reliability, Testability, Incremental Delivery | Yes | FR-004/014/019 (scope enforcement, fail-closed) and FR-009/010 (rate limiting, lockout) are security-critical and get test-first coverage, per the constitution's test-first mandate for anything touching access control. |
| VIII. Security, Identity, Observability | Yes | This module is close to a direct implementation of Principle VIII: JWT (short-lived access + rotating refresh), OTP via an abstracted India SMS gateway, RBAC + live attribute-based scoping, secrets from environment config only, structured logging with correlation IDs (reusing the warm-up feature's `logback-spring.xml`/`CorrelationIdFilter`). First module where this principle is fully rather than partially satisfied. |
| IX. Recruitment & Marketing Tracked to Outcome | No | Not applicable. |
| Additional Constraints — stack/security | Yes | Java 25/Spring Boot 4.1.1/PostgreSQL/single-EC2 unchanged; BCrypt password hashing per Requirements §12; DPDP-relevant personal data (phone, email) is stored, but a full consent/retention policy is out of this module's declared scope (spec Assumptions don't claim it) — noted, not treated as a gap this plan must close. |

**Gate result**: PASS. One structural finding, not a principle violation, is logged in Complexity Tracking: this module's Manager-scoping requirements now depend on Organization's (spec 003) public API, which inverts the tracker's original Identity-before-Organization build order.

**Post-design re-check (after Phase 1)**: data-model.md's two implementation-only tables (`OtpChallenge`, `PasswordResetToken`) and contracts/identity-api.yaml's endpoint set don't introduce anything not already covered by the FRs above — both are short-lived, single-use, and hold no data beyond what FR-008/FR-009/FR-016 already require. Gate result unchanged: PASS.

## Project Structure

### Documentation (this feature)

```text
specs/002-identity-access/
├── plan.md               # This file (/speckit-plan command output)
├── research.md           # Phase 0 output (/speckit-plan command)
├── data-model.md         # Phase 1 output (/speckit-plan command)
├── quickstart.md         # Phase 1 output (/speckit-plan command)
├── contracts/            # Phase 1 output (/speckit-plan command)
│   └── identity-api.yaml
├── checklists/
│   └── requirements.md
└── tasks.md               # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
backend/
├── pom.xml                                        # + spring-boot-starter-security,
│                                                   #   spring-boot-starter-oauth2-resource-server,
│                                                   #   bucket4j-core
├── src/
│   ├── main/
│   │   ├── java/com/hls/
│   │   │   ├── organization/                      # existing (spec 003), untouched
│   │   │   └── identity/
│   │   │       ├── api/                                # (no CurrentUserResolver/AuthenticatedUser —
│   │   │       │                                        # dropped 2026-09-22, see specs/003's research.md
│   │   │       │                                        # §6: organization.internal.CurrentUserResolver
│   │   │       │                                        # never got built; would have made organization
│   │   │       │                                        # depend on identity while identity already
│   │   │       │                                        # depends on organization.api — a module cycle)
│   │   │       └── internal/
│   │   │           ├── User.java, Session.java, AuthAuditEntry.java,
│   │   │           │   OtpChallenge.java, PasswordResetToken.java       # JPA entities
│   │   │           ├── UserRepository.java, SessionRepository.java, ... # Spring Data repositories
│   │   │           ├── AuthenticationService.java      # password/OTP verification, lockout (FR-010)
│   │   │           ├── TokenService.java               # JWT issuance + refresh rotation (FR-011)
│   │   │           ├── ManagerScopeGuard.java           # calls organization.api.AccountabilityQueries,
│   │   │           │                                    # fail-closed on error/timeout (FR-019)
│   │   │           ├── TeacherScopeGuard.java            # compares caller's own User.linkedTeacherId to
│   │   │           │                                     # the target record's teacher id (FR-005/FR-021);
│   │   │           │                                     # no cross-module call needed, unlike Manager's
│   │   │           ├── OtpSender.java                   # interface; dev/test impl for now
│   │   │           └── AuthController.java              # REST endpoints, see contracts/identity-api.yaml
│   │   └── resources/
│   │       └── db/migration/
│   │           └── V{n}__create_identity_tables.sql     # {n} = next after Organization's migration
│   └── test/
│       └── java/com/hls/
│           ├── ArchitectureTest.java                     # + identity internal-access rule
│           └── identity/
│               ├── AuthenticationServiceTest.java        # unit: lockout, OTP, password rules
│               ├── ManagerScopeGuardTest.java             # unit: fail-closed behavior (FR-019)
│               ├── TeacherScopeGuardTest.java             # unit: own-record allowed, other-teacher denied,
│               │                                          # missing linkage denied by default (FR-021)
│               ├── IdentityModuleTest.java                # @ApplicationModuleTest
│               └── IdentityIntegrationTest.java           # Testcontainers: full login/session/lockout flows

frontend/
├── src/
│   ├── pages/
│   │   ├── LoginPage/            # password login + MFA prompt (User Story 1)
│   │   ├── TeacherOtpLoginPage/   # OTP request/verify (User Story 2)
│   │   ├── PasswordResetPage/     # self-service reset (FR-016)
│   │   └── SessionsPage/          # view/revoke active sessions (User Story 5)
│   └── auth/
│       ├── AuthContext.tsx        # access token held in memory only (research.md frontend §7)
│       └── authClient.ts          # login/refresh/logout calls; refresh token lives in an
│                                   # httpOnly cookie the backend sets, never read by JS
```

**Structure Decision**: Extends `backend/`'s modular monolith with a second bounded-context package (`identity`, `api`/`internal` split, same shape as `organization`) and extends the existing `frontend/` Vite/React app with real auth pages for the first time. No new top-level directories. React Native mobile app work is deferred (see Assumptions) rather than started here.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|---------------------------------------|
| This module's Manager-scoping requirements (FR-004, FR-014, FR-019) depend on Organization's (spec 003) `AccountabilityQueries` public API, inverting the tracker's original build order (Identity sequenced before Organization) | Today's clarification (spec 002, Clarifications session 2026-09-21) explicitly rejected Identity keeping its own duplicate `AccessScope` copy, precisely to avoid a second source of truth for the same fact and to make FR-014/SC-006 hold by construction | Reverting to Identity owning its own `AccessScope` copy (rejected in Clarifications) would restore the original build order but reintroduce the sync/staleness problem the clarification was written to eliminate; the smaller cost is a build-order note here rather than a data-integrity risk in production |

**Recommendation for the tracker** (not applied by this command): given the dependency above, Organization's own implementation (`/speckit-tasks` → `/speckit-implement` for spec 003) should land before or alongside Identity's implementation of User Story 3 (Manager scoping) specifically — the other four user stories (staff login, Teacher OTP, lockout, session management) have no such dependency and can proceed independently. Consider whether `docs/HLS SDD Implementation Plan & Deliverables Tracker.md` needs another sequencing amendment, similar to the 2026-09-21 Teacher/School/Contract correction.
