# Implementation Plan: Audit Trail

**Branch**: `004-audit` | **Date**: 2026-09-22 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/004-audit/spec.md`

## Summary

The single append-only history store Constitution Principle V names but that doesn't exist yet: a new `audit` bounded-context module that any other module will call, in-process, whenever it creates, changes, or corrects a financial- or attendance-affecting record. Technical approach: one JPA-backed, insert-only table (`audit_entry`) behind a narrow `AuditEntryRepository` that exposes no update/delete method at all (mirroring `identity.internal.AuthAuditEntryRepository`'s existing pattern); a public `api` package (`AuditWriter`, `AuditReader`) with entity-agnostic (opaque string) request/view DTOs, since no attendance/payroll/school-payment/expense/substitution module exists yet to shape those DTOs around; and one Director/Admin-only REST endpoint plus a minimal frontend lookup page for User Story 2. Because no real caller module exists yet, User Story 1 (the write path) and FR-004 (atomicity) are proven by tests that call `AuditWriter` directly, exactly as spec.md's own Assumptions section describes.

## Technical Context

**Language/Version**: Java 25 (unchanged)

**Primary Dependencies**: All already present in `backend/pom.xml` from Identity's implementation — `spring-boot-starter-data-jpa`, `spring-boot-starter-validation`, `spring-boot-starter-security`, `spring-boot-starter-oauth2-resource-server`, `spring-modulith-starter-core`/`-test`, `spring-boot-flyway` + `flyway-core`/`flyway-database-postgresql`, `spring-boot-testcontainers`, `h2` (test). **No new dependency additions for this module.**

**Storage**: PostgreSQL — one new table, `audit_entry`, migration `V3__create_audit_tables.sql` (Identity's is `V1`, Organization's is `V2`; this module must not renumber either)

**Testing**: JUnit 5 + Spring Boot Test (`AuditServiceTest`, unit, mocked repository) for FR-004/FR-010/FR-011's rules; Spring Modulith's `@ApplicationModuleTest` (`AuditModuleTest`, H2, same pattern as `IdentityModuleTest`/`OrganizationModuleTest`) for an isolated bootstrap test; Testcontainers-backed PostgreSQL integration test (`AuditIntegrationTest`) exercising the real Flyway migration, real JWT bearer auth (Director/Admin 200, Manager 403), the write-then-read round trip via a direct `AuditWriter` call (research.md §1), and the same-transaction rollback proof for FR-004 (research.md §3); ArchUnit for the module-boundary rule

**Target Platform**: Single EC2/VM in `ap-south-1`, Docker Compose — unchanged

**Project Type**: Web application (Option 2) — backend module plus one minimal frontend page (`AuditHistoryPage`, Director/Admin entity-type/entity-id lookup), reusing Identity's `AuthContext`/`authClient` Bearer-token pattern the same way `organization`'s `AssignmentsPage` already does (research.md §7)

**Performance Goals**: SC-002's "a few seconds" is a human task-completion target, not a throughput target; no specific req/s goal given the <100-user scale. Writes ride inside the caller's own existing transaction (research.md §3), so `audit` adds no meaningful latency budget of its own beyond one extra insert.

**Constraints**: FR-004 (an audited change and its entry succeed or fail together) is satisfied by same-transaction, in-process calls — no outbox/distributed-transaction infrastructure needed in this single-database monolith (research.md §3). FR-005/FR-006 (never edited or deleted, corrections only append) are enforced by a narrow repository interface with no update/delete method and a controller with no write endpoint at all — not additionally enforced via database-level privilege revocation for this plan (research.md §4, alternative considered and rejected). FR-011's ordering uses a database-generated `sequence_no` as the real tiebreaker, independent of wall-clock `occurred_at` (research.md §5).

**Scale/Scope**: One new entity/table, one REST endpoint (`GET .../history`), one new frontend page; no batch/background processing of its own.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Applies? | Assessment |
|---|---|---|
| I. Operational Truth Must Be Auditable | Yes — this module *is* the mechanism | Every FR-002-covered record's create/change/correction produces an entry (FR-002, FR-003) via same-transaction writes (FR-004), attributed to a real actor (FR-010), never edited or deleted (FR-005), corrections only append (FR-006). This is Principle I's direct implementation, not an application of it to some other concern. |
| II. Role-Scoped Access and Manager Ownership | Yes | Viewing history is DIRECTOR/ADMIN-only (FR-007, FR-008) — Managers get no history access in this feature (spec.md Assumptions), matching Principle II's Director/Admin-vs-Manager split; no per-manager scoping logic is needed here since Director/Admin already see the full organization. |
| III. Payroll/Receivables/Margin Formula-Driven | No | `audit` records values other modules compute; it computes nothing itself. |
| IV. Training & Substitution Parity | No | `audit` treats every record type symmetrically by construction (research.md §2's opaque, module-agnostic design) — there's no module-specific logic here to favor one record type over another, but this module doesn't implement training/substitution features themselves. |
| V. Architecture Is a Modular Monolith With Enforced Boundaries | Yes — this module *is* what the principle names | Lives in `com.hls.audit`, `api`/`internal` split, same shape as `identity`/`organization`. Joins `ApplicationModules.verify()` and gets an `ArchitectureTest` internal-access rule. Dependency direction is one-way and inbound-only: future modules will depend on `audit.api`; `audit` depends on nothing from any other module (research.md §2). `identity`'s pre-existing, separate `AuthAuditEntry` mechanism (login/session/security events) is deliberately left as-is, not migrated — research.md §6 traces why that's a legitimate boundary reading of Principle V's "no module writes audit history directly to its own tables", not an oversight. |
| VI. Concurrency Uses Structured, Virtual-Thread Batch Processing | No (N/A) | `audit` has no triggered batch job of its own — every write is a single-row insert riding inside the calling module's own request/transaction. A future bulk operation (e.g. a payroll run) producing many entries is many individual in-process calls, not a batch `audit` runs itself. |
| VII. Reliability, Testability, and Incremental Delivery | Yes | FR-004's atomicity and FR-005/FR-006's immutability get unit tests (mocked), a Modulith isolation test, and a Testcontainers integration test proving the real rollback and real-auth paths, written before implementation. |
| VIII. Security, Identity, and Observability | Yes | Viewing endpoint uses Identity's real JWT bearer auth from day one (same `@AuthenticationPrincipal Jwt` pattern `organization` established, research.md — no stub). `requestId` on every entry continues the correlation-ID pattern `identity`'s `SecurityMdcInterceptor` already populates app-wide. |
| IX. Recruitment and Marketing Are Tracked to Outcome | No | Not applicable. |
| Additional Constraints — stack/deployment | Yes | Java 25 / Spring Boot 4.1.1 / PostgreSQL / single EC2 `ap-south-1` / Docker Compose unchanged. Also directly satisfies the "audit logs must be represented as normalized relational entities with historical continuity" data-model constraint. |

**Gate result**: PASS, no open exceptions.

**Post-design re-check (after Phase 1)**: No new principle concerns from data-model.md or the contract. Gate result unchanged: PASS.

## Project Structure

### Documentation (this feature)

```text
specs/004-audit/
├── plan.md               # This file (/speckit-plan command output)
├── research.md           # Phase 0 output (/speckit-plan command)
├── data-model.md         # Phase 1 output (/speckit-plan command)
├── quickstart.md         # Phase 1 output (/speckit-plan command)
├── contracts/            # Phase 1 output (/speckit-plan command)
│   └── audit-api.yaml
├── checklists/
│   └── requirements.md
└── tasks.md              # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
backend/
├── pom.xml                                     # NO CHANGES — everything needed already added by Identity
├── src/
│   ├── main/
│   │   ├── java/com/hls/
│   │   │   ├── identity/                        # existing (spec 002), untouched — its own AuthAuditEntry
│   │   │   │                                    # mechanism is a separate, out-of-scope concern (research.md §6)
│   │   │   ├── organization/                    # existing (spec 003), untouched
│   │   │   └── audit/
│   │   │       ├── api/
│   │   │       │   ├── AuditWriter.java          # public interface: record(AuditRecordRequest) -> UUID (FR-002/003/004/010)
│   │   │       │   ├── AuditReader.java          # public interface: history(entityType, entityId) -> List<AuditEntryView> (FR-007)
│   │   │       │   └── dto/
│   │   │       │       ├── AuditAction.java          # CREATED | UPDATED | CORRECTED
│   │   │       │       ├── AuditRecordRequest.java   # no id/sequenceNo/occurredAt fields — audit assigns them (research.md §5)
│   │   │       │       └── AuditEntryView.java
│   │   │       └── internal/
│   │   │           ├── AuditEntry.java              # JPA entity
│   │   │           ├── AuditEntryRepository.java    # extends bare Repository<>, no delete/update method (research.md §4)
│   │   │           ├── AuditService.java             # implements AuditWriter + AuditReader; assigns id/occurredAt
│   │   │           └── AuditController.java          # GET-only REST endpoint; reads @AuthenticationPrincipal Jwt directly
│   │   │                                              # (no CurrentUserResolver — same pattern OrganizationController uses)
│   │   └── resources/
│   │       └── db/migration/
│   │           └── V3__create_audit_tables.sql       # V3 — Identity claimed V1, Organization claimed V2
│   └── test/
│       └── java/com/hls/
│           ├── ArchitectureTest.java                  # + audit internal-access rule
│           └── audit/
│               ├── AuditServiceTest.java              # unit: FR-004/FR-010/FR-011 rules
│               ├── AuditModuleTest.java               # @ApplicationModuleTest, H2 (IdentityModuleTest's pattern)
│               └── AuditIntegrationTest.java          # Testcontainers Postgres + real JWT bearer auth;
│                                                       # calls AuditWriter directly to simulate a future caller (research.md §1)

frontend/
├── src/
│   ├── pages/
│   │   └── AuditHistoryPage/         # NEW — Director/Admin entity-type/entity-id history lookup (research.md §7)
│   │       ├── AuditHistoryPage.tsx
│   │       └── AuditHistoryPage.test.tsx
│   └── auth/                         # existing (spec 002), reused as-is — same Bearer-token pattern
│                                      # AssignmentsPage already uses to call Organization's endpoints
```

**Structure Decision**: Extends `backend/`'s modular monolith with a third bounded-context package (`audit`, `api`/`internal` split, same shape as `identity`/`organization`) and adds one new frontend page. Dependency direction is inbound-only and one-way: future modules (attendance, payroll, school payment, expense, substitution) will depend on `audit.api` once they exist; `audit` depends on nothing from any other module in this codebase, including `identity` and `organization` (research.md §2) — the same "generic, no upward dependency" reasoning `organization.api` used for its own opaque School/Teacher identifiers before master-data tables existed (specs/003 research.md §3), applied here because `audit`'s callers don't exist at all yet rather than merely lacking master data.

## Complexity Tracking

*(none — no Constitution Check violations to justify)*
