# Implementation Plan: Teacher Master Data

**Branch**: `005-teacher` | **Date**: 2026-09-22 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/005-teacher/spec.md`

## Summary

The first master-data module: a `teacher` bounded context owning each teacher's profile (name, contact, bank details, HLS-offered salary, status) — reference data every future operational module (Attendance, Payroll, Training, Substitution) will read but never own. Technical approach: a single JPA-backed `TeacherProfile` table with a narrow, no-delete repository (FR-004); every create/update/status-change call routes through the real `audit.api.AuditWriter` (FR-005) — this module becomes Audit's first genuine caller, not just its own test harness. Access control reuses infrastructure Identity already built and shipped for exactly this purpose: `identity.api.ManagerScopeQueries.isAllowedForTeacher(...)` for Manager-scoped viewing and `identity.api.TeacherScopeQueries.isAllowed(...)` for Teacher self-viewing — `teacher` never needs its own dependency on `organization.api` at all. The one gap those interfaces don't close is *how* a Teacher discovers their own `teacherId`; closing it requires one small, additive touch to Identity's already-shipped `TokenService` (adds a `teacherId` JWT claim when `User.linkedTeacherId` is set), detailed in research.md §3.

## Technical Context

**Language/Version**: Java 25 (unchanged)

**Primary Dependencies**: All already present in `backend/pom.xml` — `spring-boot-starter-data-jpa`, `spring-boot-starter-validation`, `spring-boot-starter-security`, `spring-boot-starter-oauth2-resource-server`, `spring-modulith-starter-core`/`-test`, `spring-boot-flyway` + `flyway-core`/`flyway-database-postgresql`, `spring-boot-testcontainers`, `h2` (test). **No new dependency additions for this module.**

**Storage**: PostgreSQL — one new table, `teacher_profile`, migration `V4__create_teacher_tables.sql` (Identity claimed `V1`, Organization `V2`, Audit `V3`)

**Testing**: JUnit 5 + Spring Boot Test (`TeacherServiceTest`, unit, mocked repository + mocked `AuditWriter`) for FR-001-005/010 rules; Spring Modulith's `@ApplicationModuleTest` (`TeacherModuleTest`, H2) — unlike Audit's, this one needs `BootstrapMode.DIRECT_DEPENDENCIES` from the start, since `teacher` has real bean dependencies on `identity.api` and `audit.api` (research.md §6 — Organization's tasks.md T031 had to retrofit this after the fact for Identity; `teacher` doesn't repeat that mistake); Testcontainers-backed PostgreSQL integration test (`TeacherIntegrationTest`) exercising real JWT bearer auth for all four roles, real Manager scoping (seeded via Organization's real `AccountabilityCommands`), real Teacher self-view (seeded via a `User` with `linkedTeacherId`, logging in for real to prove the new JWT claim round-trips), and reading the resulting history back through Audit's real `GET /api/v1/audit/.../history` endpoint; ArchUnit for the module-boundary rule

**Target Platform**: Single EC2/VM in `ap-south-1`, Docker Compose — unchanged

**Project Type**: Web application (Option 2) — backend module plus two frontend pieces: an Admin-facing `TeacherProfilesPage` (create/edit/change-status/lookup, Director/Manager get the same page's view-only paths) and a Teacher-facing `MyProfilePage` (read-only, calls the new `/teachers/me` convenience endpoint) — following the established precedent (`TeacherOtpLoginPage` et al.) of building teacher-facing flows as web pages for now, since no React Native mobile app exists yet in this codebase (research.md §7)

**Performance Goals**: No specific throughput target given the <100-user, ~60-teacher scale (Requirements §1). Writes ride inside the same transaction as their audit entry (same pattern Audit's own research.md §3 established), adding one extra insert per write, not a new latency budget.

**Constraints**: FR-004 (never deleted) enforced by a narrow repository interface with no delete method, mirroring `AuditEntryRepository`/`AuthAuditEntryRepository`'s established pattern (research.md §4). FR-005 (history, never silently overwritten) is satisfied by routing every write through `audit.api.AuditWriter` rather than building a second, bespoke history mechanism (research.md §2). Bank-detail "encryption at rest" (Constitution's Security section) is treated as a deployment-layer concern (encrypted disk volume) rather than application-level column encryption, consistent with this project's minimal-infra scale and lack of any existing precedent for app-level field crypto (research.md §5).

**Scale/Scope**: One new entity/table, ~5 REST endpoints, two new frontend pages, one small additive touch to Identity's `TokenService` (research.md §3); no batch/background processing.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Applies? | Assessment |
|---|---|---|
| I. Operational Truth Must Be Auditable | Yes | Every create/update/status-change (FR-001-003) writes an entry via the real `audit.api.AuditWriter` (FR-005) — this module is Audit's first real caller, not a simulated one. |
| II. Role-Scoped Access and Manager Ownership | Yes — reuses the mechanism built for it | Admin writes unscoped (master-data maintenance, matching Principle II's own description of the role); Director reads unscoped; Manager reads scoped to their *current* assigned teachers via `identity.api.ManagerScopeQueries` (which itself calls `organization.api` — no new scoping logic invented here); Teacher reads only their own profile via `identity.api.TeacherScopeQueries`. |
| III. Payroll/Receivables/Margin Formula-Driven | No | Supplies an input (HLS-offered salary) to the future Payroll module; computes nothing itself. |
| IV. Training & Substitution Parity | No | Not applicable — this module supplies reference data those future modules will read, but doesn't implement them. |
| V. Architecture Is a Modular Monolith With Enforced Boundaries | Yes | New `com.hls.teacher` package, `api`/`internal` split, joins `ApplicationModules.verify()` and gets an `ArchitectureTest` rule. Dependency direction is one-way: `teacher` → `identity.api` (scoping) and `teacher` → `audit.api` (history); nothing depends on `teacher.internal`; neither `identity` nor `audit` depends on `teacher`, so no cycle. |
| VI. Concurrency Uses Structured, Virtual-Thread Batch Processing | No (N/A) | Simple per-request CRUD; no triggered batch job of its own. |
| VII. Reliability, Testability, and Incremental Delivery | Yes | Unit (mocked), Modulith isolation, and Testcontainers integration tests for every FR, written before implementation. |
| VIII. Security, Identity, and Observability | Yes | Real JWT bearer auth reused end to end (no new auth mechanism); `requestId`/`userId`/`role` continue via Identity's existing app-wide `SecurityMdcInterceptor`/`CorrelationIdFilter` with no extra wiring (same finding Organization's and Audit's Polish phases already confirmed). Bank-detail protection addressed as a deployment-layer decision (research.md §5), explicitly not silently skipped. |
| IX. Recruitment and Marketing Are Tracked to Outcome | No | Not applicable. |
| Additional Constraints — stack/deployment | Yes | Java 25 / Spring Boot 4.1.1 / PostgreSQL / single EC2 `ap-south-1` / Docker Compose unchanged. Directly satisfies the "Teacher... normalized relational entities with historical continuity" data-model constraint. |

**Gate result**: PASS, no open exceptions. The one cross-module touch this plan makes to already-shipped code (Identity's `TokenService`, research.md §3) is additive and backward-compatible, not a boundary violation — Identity's own `api` package is what other modules already depend on, and this change doesn't alter that contract.

**Post-design re-check (after Phase 1)**: No new principle concerns from data-model.md or the contract. Gate result unchanged: PASS.

## Project Structure

### Documentation (this feature)

```text
specs/005-teacher/
├── plan.md               # This file (/speckit-plan command output)
├── research.md           # Phase 0 output (/speckit-plan command)
├── data-model.md         # Phase 1 output (/speckit-plan command)
├── quickstart.md         # Phase 1 output (/speckit-plan command)
├── contracts/            # Phase 1 output (/speckit-plan command)
│   └── teacher-api.yaml
├── checklists/
│   └── requirements.md
└── tasks.md              # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
backend/
├── pom.xml                                       # NO CHANGES — everything needed already added by Identity
├── src/
│   ├── main/
│   │   ├── java/com/hls/
│   │   │   ├── identity/
│   │   │   │   └── internal/
│   │   │   │       └── TokenService.java         # SMALL TOUCH (research.md §3): buildAccessToken(...) gains
│   │   │   │                                      # a linkedTeacherId param; adds "teacherId" claim when non-null.
│   │   │   │                                      # issueForNewSession/refresh's 4 call sites (AuthenticationService)
│   │   │   │                                      # pass user.getLinkedTeacherId() through — additive, no signature
│   │   │   │                                      # removed, no existing test's expectations broken.
│   │   │   ├── organization/                     # existing (spec 003), untouched
│   │   │   ├── audit/                            # existing (spec 004), untouched — teacher becomes its first
│   │   │   │                                      # real caller via audit.api.AuditWriter, no audit-side changes
│   │   │   └── teacher/
│   │   │       ├── api/
│   │   │       │   ├── TeacherQueries.java        # public: findById/exists (FR-011) — forward note (research.md §8):
│   │   │       │   │                               # could let Organization's FR-012 "unknown identifier" branch
│   │   │       │   │                               # finally be wired for real; NOT done in this plan
│   │   │       │   ├── TeacherCommands.java        # public: create/updateProfile/changeStatus (FR-001/002/003)
│   │   │       │   └── dto/
│   │   │       │       ├── TeacherStatus.java          # IN_TRAINING | ACTIVE | ON_LEAVE | EXITED
│   │   │       │       ├── TeacherProfileView.java
│   │   │       │       ├── CreateTeacherProfileRequest.java
│   │   │       │       └── UpdateTeacherProfileRequest.java
│   │   │       └── internal/
│   │   │           ├── TeacherProfile.java             # JPA entity
│   │   │           ├── TeacherProfileRepository.java   # extends bare Repository<>, no delete method (FR-004,
│   │   │           │                                    # research.md §4 — same pattern as AuditEntryRepository)
│   │   │           ├── TeacherService.java              # implements TeacherQueries + TeacherCommands; calls
│   │   │           │                                     # audit.api.AuditWriter inside its own transaction
│   │   │           └── TeacherController.java            # REST endpoints; reads @AuthenticationPrincipal Jwt
│   │   │                                                 # directly + identity.api.ManagerScopeQueries/
│   │   │                                                 # TeacherScopeQueries for scoping (no new scope logic)
│   │   └── resources/
│   │       └── db/migration/
│   │           └── V4__create_teacher_tables.sql        # V4 — Identity=V1, Organization=V2, Audit=V3
│   └── test/
│       └── java/com/hls/
│           ├── ArchitectureTest.java                     # + teacher internal-access rule
│           ├── identity/
│           │   └── TokenServiceTest.java                 # NEW cases: teacherId claim present/absent (research.md §3)
│           └── teacher/
│               ├── TeacherServiceTest.java                # unit: FR-001-005/010 rules
│               ├── TeacherModuleTest.java                 # @ApplicationModuleTest, BootstrapMode.DIRECT_DEPENDENCIES
│               │                                          # from the start (research.md §6)
│               └── TeacherIntegrationTest.java             # Testcontainers Postgres + real JWT auth for all 4
│                                                            # roles; seeds Manager scope via Organization's real
│                                                            # AccountabilityCommands; reads history back via
│                                                            # Audit's real GET /api/v1/audit/.../history

frontend/
├── src/
│   ├── pages/
│   │   ├── TeacherProfilesPage/          # NEW — Admin create/edit/status-change; Director/Manager view
│   │   │   ├── TeacherProfilesPage.tsx
│   │   │   ├── TeacherProfilesPage.test.tsx
│   │   │   └── teacherClient.ts
│   │   └── MyProfilePage/                # NEW — Teacher-facing, read-only, calls GET /teachers/me
│   │       ├── MyProfilePage.tsx
│   │       └── MyProfilePage.test.tsx
│   └── auth/                             # existing, reused as-is — same Bearer-token pattern
```

**Structure Decision**: Extends `backend/`'s modular monolith with a fourth bounded-context package (`teacher`, `api`/`internal` split, same shape as `identity`/`organization`/`audit`) and adds two new frontend pages. Dependency direction: `teacher` → `identity.api` (both scoping interfaces) and `teacher` → `audit.api` (`AuditWriter`); nothing depends on `teacher.internal`; `identity` and `audit` depend on nothing from `teacher`, so no cycle. The one exception to "no changes to existing modules" is the small, additive `TokenService` touch in `identity` (research.md §3) — necessary because nothing in Identity's already-shipped code currently has any way to tell a Teacher their own `teacherId`.

## Complexity Tracking

*(none — no Constitution Check violations to justify. The `TokenService` touch is a necessary, additive integration point, not a boundary violation — see Gate result above.)*
