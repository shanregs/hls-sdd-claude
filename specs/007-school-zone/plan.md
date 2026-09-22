# Implementation Plan: School Master Data — Zones

**Branch**: `007-school-zone` | **Date**: 2026-09-22 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/007-school-zone/spec.md`

## Summary

The first `school` module code: a deliberately minimal slice of School Master Data — `Zone(id, name)` and each School's current Zone — pulled forward ahead of School's other master-data fields because specs/006-zone-scoping's Manager-scoping correction needs Zone to exist first (Constitution Amendment 1.7.0). Technical approach: mirrors `organization`'s own established shape almost exactly — one plain, never-deleted `Zone` table and one append-only `SchoolZoneAssignment` table using the identical `effective_to IS NULL` / partial-unique-index / conditional-update-conflict pattern `SchoolAssignment` already proved in specs/003, since School is still an opaque identifier (no real School entity exists yet, the same narrowing specs/003 already documented for itself). A public `ZoneQueries`/`ZoneCommands` API is the whole point of this module existing now — `organization`'s specs/006-zone-scoping depends on it directly, the way `identity` already depends on `organization.api`.

## Technical Context

**Language/Version**: Java 25 (unchanged)

**Primary Dependencies**: All already present in `backend/pom.xml` — `spring-boot-starter-data-jpa`, `spring-boot-starter-validation`, `spring-boot-starter-security`, `spring-boot-starter-oauth2-resource-server`, `spring-modulith-starter-core`/`-test`, `spring-boot-flyway` + `flyway-core`/`flyway-database-postgresql`, `spring-boot-testcontainers`, `h2` (test). **No new dependency additions.**

**Storage**: PostgreSQL — two new tables, `school_zone` and `school_zone_assignment`, in a new migration. Exact Flyway version number resolved against whatever's on disk at implementation time (Identity=V1, Organization=V2, Audit=V3; neither Teacher (specs/005) nor Zone-scoping (specs/006) has been implemented yet either, so this plan does not hardcode a number — see tasks.md T001).

**Testing**: JUnit 5 + Spring Boot Test (`ZoneServiceTest`, unit, mocked repositories) for FR-001-007; Spring Modulith's `@ApplicationModuleTest` (`SchoolModuleTest`, H2) — `BootstrapMode.STANDALONE` is expected to be sufficient, the same reasoning Audit's research.md used: `school` depends on nothing from any other module, and as of this feature nothing depends on `school` either (specs/006-zone-scoping will be the first real dependant, added when it's implemented) — revisit only if that changes before this test is written; Testcontainers-backed PostgreSQL integration test (`SchoolIntegrationTest`) exercising real JWT bearer auth and the conflict-detection path (FR-005/SC-003); ArchUnit for the new module-boundary rule (a fourth `com.hls.*` bounded context, after `identity`/`organization`/`audit`).

**Target Platform**: Single EC2/VM in `ap-south-1`, Docker Compose — unchanged

**Project Type**: Web application (Option 2) — backend module plus a minimal Director/Admin frontend page (`ZonesPage`: create Zone, assign School to Zone, view Zone's Schools), reusing the established `AuthContext`/Bearer-token pattern.

**Performance Goals**: No specific throughput target given the <100-user, ~50-school scale (Requirements §1). Simple per-request reads/writes, no batch processing.

**Constraints**: FR-009 (Zone never deleted) enforced by a narrow repository interface with no delete method (the now-four-times-proven pattern: `AuthAuditEntryRepository`, `AuditEntryRepository`, and this one). FR-004/FR-005 (reassignment history + conflict detection) reuses `SchoolAssignment`'s exact conditional-`UPDATE`-returns-affected-row-count technique (specs/003 research.md §2) rather than inventing a new one.

**Scale/Scope**: Two new entities/tables, ~5 REST endpoints, one new frontend page, one new bounded-context package (`com.hls.school`) — the fourth, after `identity`/`organization`/`audit`.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Applies? | Assessment |
|---|---|---|
| I. Operational Truth Must Be Auditable | Yes | `SchoolZoneAssignment` rows are append-only (never updated except to set `effective_to` once, never deleted) — the same pattern every other assignment table in this system already uses. |
| II. Role-Scoped Access and Manager Ownership | Yes | Zone/School-Zone writes are Director/Admin-only (master-data maintenance, matching Principle II's description of that role), same pattern every existing write endpoint uses. |
| III. Payroll/Receivables/Margin Formula-Driven | No | Not applicable. |
| IV. Training & Substitution Parity | No | Not applicable. |
| V. Architecture Is a Modular Monolith With Enforced Boundaries | Yes — this module *is* what Amendment 1.7.0 names | New `com.hls.school` package, `api`/`internal` split, joins `ApplicationModules.verify()`, gets an `ArchitectureTest` rule. `school` depends on nothing from any other module; nothing in this feature depends on `school` either (specs/006-zone-scoping, not yet implemented, will be the first real dependant) — no cycle risk. |
| VI. Concurrency Uses Structured, Virtual-Thread Batch Processing | No (N/A) | Simple per-request CRUD. |
| VII. Reliability, Testability, and Incremental Delivery | Yes | Unit, Modulith isolation, and Testcontainers integration tests for every FR, written before implementation. |
| VIII. Security, Identity, and Observability | Yes | Real JWT bearer auth reused end to end; `requestId`/`userId`/`role` continue via Identity's existing app-wide interceptor/filter with no extra wiring (the same finding every prior module's Polish phase already confirmed). |
| IX. Recruitment and Marketing Are Tracked to Outcome | No | Not applicable. |
| Additional Constraints — stack/deployment | Yes | Java 25 / Spring Boot 4.1.1 / PostgreSQL / single EC2 `ap-south-1` / Docker Compose unchanged. Directly satisfies the "normalized relational entities with historical continuity" data-model constraint. |

**Gate result**: PASS, no open exceptions.

**Post-design re-check (after Phase 1)**: No new principle concerns from data-model.md or the contract. Gate result unchanged: PASS.

## Project Structure

### Documentation (this feature)

```text
specs/007-school-zone/
├── plan.md               # This file (/speckit-plan command output)
├── research.md           # Phase 0 output (/speckit-plan command)
├── data-model.md         # Phase 1 output (/speckit-plan command)
├── quickstart.md         # Phase 1 output (/speckit-plan command)
├── contracts/            # Phase 1 output (/speckit-plan command)
│   └── school-zone-api.yaml
├── checklists/
│   └── requirements.md
└── tasks.md              # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
backend/
├── pom.xml                                       # NO CHANGES
├── src/
│   ├── main/
│   │   ├── java/com/hls/
│   │   │   └── school/
│   │   │       ├── api/
│   │   │       │   ├── ZoneQueries.java           # public: findById, currentSchoolsForZone,
│   │   │       │   │                               # currentZoneForSchool (FR-002/006/007) — this is
│   │   │       │   │                               # what specs/006's organization module will depend on
│   │   │       │   ├── ZoneCommands.java           # public: createZone, assignSchoolToZone (FR-001/003/004)
│   │   │       │   ├── ZoneAssignmentConflictException.java  # public — FR-005, same role as
│   │   │       │   │                                           # organization.api.AssignmentConflictException
│   │   │       │   └── dto/
│   │   │       │       ├── ZoneView.java               # id, name
│   │   │       │       └── SchoolZoneAnswer.java        # state: CURRENT_ZONE | UNASSIGNED (FR-006)
│   │   │       └── internal/
│   │   │           ├── Zone.java                          # JPA entity
│   │   │           ├── SchoolZoneAssignment.java            # JPA entity
│   │   │           ├── ZoneRepository.java                   # extends bare Repository<>, no delete (FR-009)
│   │   │           ├── SchoolZoneAssignmentRepository.java     # same shape as SchoolAssignmentRepository
│   │   │           ├── ZoneService.java                         # implements ZoneQueries + ZoneCommands
│   │   │           └── ZoneController.java                      # REST endpoints; @AuthenticationPrincipal Jwt
│   │   │                                                        # directly, same pattern every controller uses
│   │   └── resources/
│   │       └── db/migration/
│   │           └── V<next>__create_school_tables.sql   # exact number resolved at implementation time
│   └── test/
│       └── java/com/hls/
│           ├── ArchitectureTest.java                     # + school internal-access rule
│           └── school/
│               ├── ZoneServiceTest.java                    # unit: FR-001-007 rules
│               ├── SchoolModuleTest.java                    # @ApplicationModuleTest, H2
│               └── SchoolIntegrationTest.java                # Testcontainers Postgres + real JWT bearer auth

frontend/
├── src/
│   ├── pages/
│   │   └── ZonesPage/          # NEW — Director/Admin: create Zone, assign School to Zone, view Zone's Schools
│   │       ├── ZonesPage.tsx
│   │       ├── ZonesPage.test.tsx
│   │       └── zoneClient.ts
│   └── auth/                   # existing, reused as-is
```

**Structure Decision**: A fourth backend bounded-context package (`school`, `api`/`internal` split, same shape as `identity`/`organization`/`audit`), plus one new frontend page. Dependency direction: `school` depends on nothing from any other module; nothing depends on `school.internal`. specs/006-zone-scoping (not yet implemented) will add the first real dependency on `school.api` once it's reworked and implemented — this plan does not modify `organization` at all.

## Complexity Tracking

*(none — no Constitution Check violations to justify)*
