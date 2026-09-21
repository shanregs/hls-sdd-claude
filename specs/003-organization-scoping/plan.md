# Implementation Plan: Organization — Manager/School/Teacher Accountability

**Branch**: `003-organization-scoping` | **Date**: 2026-09-21, updated 2026-09-22 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/003-organization-scoping/spec.md`

## Summary

The first real domain module (as opposed to the throwaway status page): a backend service that records, per School and per Teacher, which Manager is currently accountable and preserves the full history of that accountability over time. Technical approach: two append-friendly JPA-backed tables (`SchoolAssignment`, `TeacherAssignment`) where "current" is the row with no end date, guarded by a database-level constraint so at most one can be open per School/Teacher at a time (FR-003); a small public API (`AccountabilityQueries`) other modules call once they exist; and Director/Admin-only REST endpoints in front of it, now protected by Identity & Access's real JWT bearer authentication.

**2026-09-22 re-plan**: Identity & Access (spec 002) has since been implemented (48/53 tasks, 32/32 tests passing) — this plan was refreshed against that real code rather than the original forward-guesses. Three things changed as a result, all detailed in research.md §6-§8: (1) the `CurrentUserResolver` port/adapter this plan originally designed is dropped entirely — Identity's `SecurityConfig` already protects every endpoint app-wide with real JWT bearer auth, so `OrganizationController` just reads `@AuthenticationPrincipal Jwt` directly, the same way Identity's own `AuthController` does; (2) the Complexity Tracking exception this plan originally logged (endpoints role-gated against a stub) is now fully resolved, not just bounded — there is no stub left; (3) Organization needs **zero new backend dependencies** — Identity already added JPA, Flyway (+ its Spring Boot 4 glue module), Spring Modulith, and Spring Security/OAuth2-resource-server to `backend/pom.xml`. Frontend scope was also re-evaluated: Identity shipped a real login (`LoginPage`, `AuthContext`), so the "no login exists yet" reason for deferring Organization's Director/Admin UI no longer holds — a minimal `AssignmentsPage` is now in scope (see Project Structure).

## Technical Context

**Language/Version**: Java 25 (unchanged)

**Primary Dependencies**: All already present in `backend/pom.xml` from Identity's implementation — `spring-boot-starter-data-jpa`, `spring-boot-starter-validation`, `spring-boot-starter-security`, `spring-boot-starter-oauth2-resource-server`, `spring-modulith-starter-core`/`-test`, `spring-boot-flyway` + `flyway-core`/`flyway-database-postgresql`, `spring-boot-testcontainers`, `h2` (test). **No new dependency additions for this module.**

**Storage**: PostgreSQL — `SchoolAssignment`/`TeacherAssignment` tables, migration `V2__create_organization_tables.sql` (Identity's migration is `V1`; this module must not renumber it)

**Testing**: JUnit 5 + Spring Boot Test for the assignment/conflict-detection logic (unit, mocked repositories); Spring Modulith's `@ApplicationModuleTest` (H2, same pattern as `IdentityModuleTest`) for an isolated bootstrap test of the `organization` module on its own; Testcontainers-backed PostgreSQL integration test exercising the real Flyway migration, the DB-level uniqueness constraint (FR-003), the optimistic-conflict path (FR-011), and now real JWT bearer auth end to end (log in via Identity's `/api/v1/auth/login`, use the token against Organization's endpoints) — the same MockMvc + Testcontainers Postgres pattern `IdentityIntegrationTest` already established and debugged; ArchUnit for the module-boundary rule

**Target Platform**: Single EC2/VM in `ap-south-1`, Docker Compose — unchanged

**Project Type**: Web application (Option 2) — **upgraded from backend-only**: Identity's login now exists, so the Director/Admin-facing assignment UI is no longer blocked. A minimal `AssignmentsPage` (assign/reassign/view portfolio/unassigned list) is added to `frontend/`, reusing Identity's `AuthContext`/`authClient` pattern for the bearer token.

**Performance Goals**: SC-001's "under 2 minutes" is a human task-completion target, not a throughput target; no specific req/s goal given the <100-user, ~50-school, ~60-teacher scale

**Constraints**: FR-003 ("at most one active assignment per School/Teacher") enforced as a database constraint (partial unique index), not only an application-level check; FR-011's conflict detection must reject the losing concurrent reassignment; FR-012's "unknown identifier" distinction remains only partially implementable — Teacher/School Master Data still don't exist as validated master tables (research.md §3, unchanged)

**Scale/Scope**: Two new entities, ~9 REST endpoints, one new frontend page, no batch/background processing

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Applies? | Assessment |
|---|---|---|
| I. Operational Truth Must Be Auditable | Yes | Every `SchoolAssignment`/`TeacherAssignment` row is itself an immutable history record (FR-005, FR-006): ended assignments are never edited or deleted, only superseded by a new row. Publishing assignment-change events to the dedicated `audit` module remains deferred since `audit` doesn't exist yet (module 5, after this one). |
| II. Role-Scoped Access and Manager Ownership | **Yes — fully, not partially** (updated 2026-09-22) | This module is the mechanism Principle II depends on for every other module. Writes are DIRECTOR/ADMIN-only (FR-001, FR-002, FR-004), enforced by Identity & Access's now-real JWT bearer auth — no stub remains. |
| III. Payroll/Receivables/Margin Formula-Driven | No | No financial computation in this module. |
| IV. Training & Substitution Parity | No | Not applicable. |
| V. Modular Monolith With Enforced Boundaries | Yes | Lives in `com.hls.organization`, split into `api`/`internal`. Joins `ApplicationModules.verify()` (already covering `status` and `identity`). `ArchitectureTest.java` gets an `organization`-specific internal-access rule, mirroring `identity`'s. **Dependency direction confirmed one-way**: `identity` depends on `organization.api` (for `ManagerScopeGuard`'s scope checks); `organization` depends on nothing from `identity` at all (it reads `Jwt` claims directly, a generic Spring Security type, not an Identity-owned one) — so there is no module cycle for Spring Modulith to reject. |
| VI. Concurrency / Virtual-Thread Batch Processing | No (N/A) | No triggered batch job — every operation is a simple request-path read or write. |
| VII. Reliability, Testability, Incremental Delivery | Yes | FR-003 and FR-011's concurrency-safety rules get unit tests (mocked) and a Testcontainers integration test proving the DB constraint and the conflict-rejection path, written before the implementation. |
| VIII. Security, Identity, Observability | **Yes — fully** (updated 2026-09-22) | Endpoints use Identity's real JWT bearer auth from day one — the "stub `CurrentUserResolver`" this row previously described no longer exists (see Summary). Structured JSON logging with correlation IDs, and now `userId`/`role` in MDC too (Identity's `SecurityMdcInterceptor`, which runs for every authenticated request app-wide, not just Identity's own endpoints), are already in place. |
| IX. Recruitment & Marketing Tracked to Outcome | No | Not applicable. |
| Additional Constraints — stack/deployment | Yes | Java 25 / Spring Boot 4.1.1 / PostgreSQL / single EC2 `ap-south-1` / Docker Compose unchanged. |

**Gate result**: PASS, with no open exceptions. The one exception this plan previously logged (stub identity) is fully resolved, not just bounded — see Complexity Tracking below, now empty.

**Post-design re-check (after Phase 1)**: No new principle concerns from data-model.md or the updated contract. Gate result unchanged: PASS.

## Project Structure

### Documentation (this feature)

```text
specs/003-organization-scoping/
├── plan.md               # This file (/speckit-plan command output)
├── research.md           # Phase 0 output (/speckit-plan command)
├── data-model.md         # Phase 1 output (/speckit-plan command)
├── quickstart.md         # Phase 1 output (/speckit-plan command)
├── contracts/            # Phase 1 output (/speckit-plan command)
│   └── organization-api.yaml
├── checklists/
│   └── requirements.md
└── tasks.md              # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
backend/
├── pom.xml                                          # NO CHANGES — everything needed already added by Identity
├── src/
│   ├── main/
│   │   ├── java/com/hls/
│   │   │   ├── identity/                            # existing (spec 002), untouched — but see research.md §6/§7
│   │   │   │   for the ManagerScopeGuard swap-over this module's api package enables
│   │   │   └── organization/
│   │   │       ├── api/
│   │   │       │   ├── AccountabilityQueries.java   # public interface: currentManagerForSchool/ForTeacher
│   │   │       │   │                                # (FR-007/FR-012) — signature LOCKED to match Identity's
│   │   │       │   │                                # OrganizationAccountabilityStandIn exactly (research.md §7)
│   │   │       │   ├── AccountabilityCommands.java  # public interface: FR-001, FR-002, FR-004, FR-010
│   │   │       │   └── dto/
│   │   │       │       ├── AccountabilityAnswer.java    # current manager | unassigned | unknown (FR-012)
│   │   │       │       ├── PortfolioItem.java
│   │   │       │       └── AssignmentHistoryEntry.java
│   │   │       └── internal/
│   │   │           ├── SchoolAssignment.java             # JPA entity
│   │   │           ├── TeacherAssignment.java            # JPA entity
│   │   │           ├── SchoolAssignmentRepository.java
│   │   │           ├── TeacherAssignmentRepository.java
│   │   │           ├── AccountabilityService.java        # implements api interfaces; enforces FR-003/010/011/013
│   │   │           └── OrganizationController.java       # REST endpoints; reads @AuthenticationPrincipal Jwt
│   │   │                                                 # directly (research.md §6) — NO CurrentUserResolver,
│   │   │                                                 # NO CurrentUser type; both dropped from this plan
│   │   └── resources/
│   │       └── db/migration/
│   │           └── V2__create_organization_tables.sql   # V2, not V1 — Identity's migration already claimed V1
│   └── test/
│       └── java/com/hls/
│           ├── ArchitectureTest.java                     # + organization internal-access rule
│           └── organization/
│               ├── AccountabilityServiceTest.java        # unit: FR-003/010/011/013 rules
│               ├── OrganizationModuleTest.java           # @ApplicationModuleTest, H2 (IdentityModuleTest's pattern)
│               └── OrganizationIntegrationTest.java      # Testcontainers Postgres + real JWT bearer auth
│                                                          # end to end (log in via Identity, call Organization)

frontend/
├── src/
│   ├── pages/
│   │   └── AssignmentsPage/          # NEW (upgraded from backend-only, see Summary) — Director/Admin
│   │       ├── AssignmentsPage.tsx   # assign/reassign, portfolio view, unassigned list
│   │       └── AssignmentsPage.test.tsx
│   └── auth/                         # existing (spec 002), reused as-is — authClient's Bearer-token
│                                      # pattern needs no changes to call Organization's endpoints
```

**Structure Decision**: Extends `backend/`'s modular monolith with a second bounded-context package (`organization`, `api`/`internal` split, same shape as `identity`) and adds one new frontend page. `identity` → `organization` is now a real, one-directional module dependency (`ManagerScopeGuard`/`TeacherScopeGuard`-equivalent scope checks call `organization.api.AccountabilityQueries`); `organization` has no dependency on `identity` at all.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

*(none — the one exception this plan originally logged, stub `CurrentUserResolver` role-gating, is fully resolved now that Identity & Access is implemented; see research.md §6-§7 and the Constitution Check table above)*
