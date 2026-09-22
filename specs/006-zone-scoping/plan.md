# Implementation Plan: Zone-Based Manager Scoping

**Branch**: `006-zone-scoping` | **Date**: 2026-09-22 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/006-zone-scoping/spec.md`

## Summary

Extends the already-shipped `organization` module with a `Zone` entity and Zone–Manager assignment, then adds one new precondition to its existing `AccountabilityCommands.assignSchoolManager(...)`: the chosen Manager must currently be assigned to the School's Zone (FR-004/FR-005). Technical approach: two new append-friendly tables (`organization_zone`, `organization_zone_manager_assignment`, `organization_school_zone_assignment`) following the exact `effective_to IS NULL` / partial-unique-index pattern `SchoolAssignment`/`TeacherAssignment` already established — except `ZoneManagerAssignment`'s uniqueness is on `(zone_id, manager_id)`, not `zone_id` alone, since a Zone may have more than one currently-assigned Manager at once (spec.md User Story 1, scenario 3). Teacher accountability is explicitly untouched (FR-007/FR-008) — this plan changes nothing about `TeacherAssignment`. The one real risk this plan calls out plainly: `AccountabilityServiceTest`/`OrganizationIntegrationTest`'s existing, already-passing test cases that call `assignSchoolManager` will start failing the moment the new precondition lands, because none of them set up a Zone/Zone-Manager assignment first — updating those fixtures is in scope as part of this plan, not a follow-up.

## Technical Context

**Language/Version**: Java 25 (unchanged)

**Primary Dependencies**: All already present in `backend/pom.xml` — `spring-boot-starter-data-jpa`, `spring-boot-starter-validation`, `spring-boot-starter-security`, `spring-boot-starter-oauth2-resource-server`, `spring-modulith-starter-core`/`-test`, `spring-boot-flyway` + `flyway-core`/`flyway-database-postgresql`, `spring-boot-testcontainers`, `h2` (test). **No new dependency additions.**

**Storage**: PostgreSQL — three new tables (`organization_zone`, `organization_zone_manager_assignment`, `organization_school_zone_assignment`) in the `organization` module's own schema area. Migration number is **not fixed here** (research.md §5): Identity=V1, Organization=V2, Audit=V3, and specs/005-teacher's not-yet-implemented plan claims V4 — whichever of 005/006 is actually implemented first takes the next number, the other follows; neither renumbers the other.

**Testing**: JUnit 5 + Spring Boot Test (`ZoneServiceTest`, unit, mocked repositories) for FR-001-006; an **updated** `AccountabilityServiceTest` (existing file) covering the new Zone-membership precondition on `assignSchoolManager` (FR-004/FR-005) and confirming `assignTeacherManager` is completely unaffected (FR-007/FR-008, SC-004); `OrganizationModuleTest` unchanged (still `STANDALONE`, no new module dependency introduced — this is all within `organization`); an **updated** `OrganizationIntegrationTest` (existing file) with its `assignSchoolManager`-calling tests now seeding a Zone/Zone-Manager assignment first, plus new Testcontainers cases for the Zone endpoints themselves; ArchUnit unchanged (no new module, no new boundary rule needed — `Zone*` classes live in `organization.internal` like everything else already does).

**Target Platform**: Single EC2/VM in `ap-south-1`, Docker Compose — unchanged

**Project Type**: Web application (Option 2) — backend changes to the existing `organization` module plus a minimal extension to the existing `AssignmentsPage` (Zone create/assign-manager/assign-school controls, per research.md §6) — no new frontend page.

**Performance Goals**: No specific throughput target given the <100-user, ~8-manager scale (Requirements §1-2). The new Zone-membership check is one extra indexed lookup inside `assignSchoolManager`'s existing transaction, not a new request path.

**Constraints**: FR-004/FR-005 (Manager must be in the School's Zone) enforced in `AccountabilityService.assignSchoolManager(...)` itself — not only at the database level — since the constraint is "does a currently-open `ZoneManagerAssignment` row exist for (school's zone, chosen manager)," which needs a query, not a schema-level CHECK. FR-002 (a Zone may have more than one current Manager) is enforced by a partial unique index on `(zone_id, manager_id)`, deliberately not on `zone_id` alone (research.md §1). FR-007/FR-008 (Teacher accountability unchanged) is enforced by *not touching* `TeacherAssignment`/`assignTeacherManager` at all — the strongest guarantee for "unchanged" is "no diff."

**Scale/Scope**: Three new tables, ~6 new REST endpoints plus one new rejection case on an existing endpoint, one new frontend extension (no new page); two existing shipped test files updated, not just added to.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Applies? | Assessment |
|---|---|---|
| I. Operational Truth Must Be Auditable | Yes | Zone/Zone-Manager/School-Zone assignments are append-only rows (never updated except to set `effective_to` once, never deleted), the same pattern `SchoolAssignment`/`TeacherAssignment` already use — consistent, not a new mechanism. |
| II. Role-Scoped Access and Manager Ownership | Yes — this feature *is* Principle II's correction | Zone/School-Zone/Zone-Manager writes are Director/Admin-only (FR-001-003, matching the existing endpoints' pattern). This is the direct implementation of Amendment 1.6.0's corrected model. |
| III. Payroll/Receivables/Margin Formula-Driven | No | Not applicable. |
| IV. Training & Substitution Parity | No | Not applicable. |
| V. Architecture Is a Modular Monolith With Enforced Boundaries | Yes | All new types live in `com.hls.organization.internal` — no new bounded-context package, no new `ArchitectureTest` rule needed. `organization`'s dependency footprint is unchanged (still depends on nothing from other modules). |
| VI. Concurrency Uses Structured, Virtual-Thread Batch Processing | No (N/A) | Simple per-request reads/writes, no batch job. |
| VII. Reliability, Testability, and Incremental Delivery | Yes | New unit + integration tests before implementation; existing tests updated (not left to silently break) as part of the same change, not deferred. |
| VIII. Security, Identity, and Observability | Yes | Reuses the exact same JWT bearer auth / Director-Admin role check `OrganizationController` already implements — no new auth mechanism. |
| IX. Recruitment and Marketing Are Tracked to Outcome | No | Not applicable. |
| Additional Constraints — stack/deployment | Yes | Java 25 / Spring Boot 4.1.1 / PostgreSQL / single EC2 `ap-south-1` / Docker Compose unchanged. |

**Gate result**: PASS, no open exceptions. This plan's defining characteristic — modifying an already-shipped, tested module's behavior — is not itself a Constitution violation; it's flagged prominently in Summary and Complexity Tracking precisely because it's consequential, not because it's disallowed.

**Post-design re-check (after Phase 1)**: No new principle concerns from data-model.md or the contract. Gate result unchanged: PASS.

## Project Structure

### Documentation (this feature)

```text
specs/006-zone-scoping/
├── plan.md               # This file (/speckit-plan command output)
├── research.md           # Phase 0 output (/speckit-plan command)
├── data-model.md         # Phase 1 output (/speckit-plan command)
├── quickstart.md         # Phase 1 output (/speckit-plan command)
├── contracts/            # Phase 1 output (/speckit-plan command)
│   └── zone-api.yaml     # NEW endpoints + the one new rejection case on an existing one;
│                         # specs/003's organization-api.yaml is left as-is (historical record)
├── checklists/
│   └── requirements.md
└── tasks.md              # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
backend/
├── pom.xml                                          # NO CHANGES
├── src/
│   ├── main/
│   │   ├── java/com/hls/
│   │   │   └── organization/
│   │   │       ├── api/
│   │   │       │   ├── AccountabilityCommands.java       # UNCHANGED signature; assignSchoolManager(...)
│   │   │       │   │                                      # may now throw SchoolManagerNotInZoneException
│   │   │       │   ├── AccountabilityQueries.java         # UNCHANGED
│   │   │       │   ├── AssignmentConflictException.java   # UNCHANGED — a different rejection reason (FR-011)
│   │   │       │   ├── SchoolManagerNotInZoneException.java   # NEW — FR-004/FR-005's rejection (research.md §3)
│   │   │       │   └── dto/
│   │   │       │       └── (existing DTOs unchanged; no new api/dto types — Zone views/commands
│   │   │       │         stay internal-only for now, research.md §4)
│   │   │       └── internal/
│   │   │           ├── SchoolAssignment.java                   # UNCHANGED
│   │   │           ├── TeacherAssignment.java                  # UNCHANGED — FR-007/FR-008
│   │   │           ├── Zone.java                                # NEW JPA entity
│   │   │           ├── ZoneManagerAssignment.java                # NEW JPA entity
│   │   │           ├── SchoolZoneAssignment.java                  # NEW JPA entity
│   │   │           ├── ZoneRepository.java                        # NEW
│   │   │           ├── ZoneManagerAssignmentRepository.java        # NEW
│   │   │           ├── SchoolZoneAssignmentRepository.java          # NEW
│   │   │           ├── ZoneService.java                              # NEW — Zone CRUD/query + the
│   │   │           │                                                  # membership check AccountabilityService calls
│   │   │           ├── AccountabilityService.java                    # MODIFIED: assignSchoolManager(...) gains the
│   │   │           │                                                  # Zone-membership precondition (FR-004/FR-005);
│   │   │           │                                                  # assignTeacherManager(...) UNTOUCHED (FR-007/008)
│   │   │           ├── OrganizationController.java                    # MODIFIED: new @ExceptionHandler for
│   │   │           │                                                  # SchoolManagerNotInZoneException -> 422
│   │   │           └── ZoneController.java                            # NEW — zone endpoints (contracts/zone-api.yaml)
│   │   └── resources/
│   │       └── db/migration/
│   │           └── V<next>__create_zone_tables.sql   # exact number decided at implementation time (research.md §5)
│   └── test/
│       └── java/com/hls/organization/
│           ├── AccountabilityServiceTest.java   # MODIFIED — existing assignSchoolManager cases now seed a Zone/
│           │                                    # Zone-Manager assignment first; new cases for FR-004/FR-005;
│           │                                    # a new case proving assignTeacherManager is untouched (SC-004)
│           ├── OrganizationIntegrationTest.java  # MODIFIED — same seeding fix, plus new Zone endpoint cases
│           ├── OrganizationModuleTest.java        # UNCHANGED
│           └── ZoneServiceTest.java                # NEW — unit coverage for FR-001-006

frontend/
├── src/
│   ├── pages/
│   │   └── AssignmentsPage/          # EXTENDED, not new — a Zone create/assign-manager/assign-school-to-zone
│   │       ├── AssignmentsPage.tsx   # section (research.md §6), plus the school-assignment form now surfaces
│   │       │                         # the new 422 "manager not in this school's zone" error
│   │       ├── AssignmentsPage.test.tsx
│   │       └── organizationClient.ts  # + zone endpoint calls
```

**Structure Decision**: Extends the existing `organization` module in place — no new bounded-context package, no new frontend page. Every new backend type lives in `organization.internal` (Zone/ZoneManagerAssignment/SchoolZoneAssignment/ZoneService/ZoneController), reachable only from within `organization` (the existing `ArchitectureTest` rule already covers this — no new rule needed). The one new `api`-level type is `SchoolManagerNotInZoneException`, public for the same reason `AssignmentConflictException` is: callers of `AccountabilityCommands` need to be able to catch it.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

*(no Constitution violations — but this plan modifies already-shipped, tested behavior, which deserves the same scrutiny a violation would get)*

| Change | Why Needed | Simpler Alternative Rejected Because |
|--------|------------|---------------------------------------|
| `AccountabilityService.assignSchoolManager(...)` gains a new rejection case, changing behavior for existing callers | FR-004/FR-005 require it — this *is* the feature | Leaving the old, unconstrained behavior and only enforcing Zone membership in a new, separate endpoint was considered and rejected: it would let the old endpoint keep silently accepting out-of-zone assignments, which is precisely the leakage Constitution Principle II says must be prevented — two ways to do the same write, one of them wrong, is worse than one way that's right. |
| `AccountabilityServiceTest`/`OrganizationIntegrationTest` (already shipped, already passing) are modified, not just extended | Their existing `assignSchoolManager` cases will fail once FR-004/FR-005 land, since none of them set up a Zone first | Leaving them broken and "fixing later" was rejected — Constitution Principle VII requires tests to reflect real behavior; a red suite from a known, already-understood cause is not an acceptable interim state for this project. |
