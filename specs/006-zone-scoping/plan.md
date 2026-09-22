# Implementation Plan: Zone-Based Manager Scoping (reworked)

**Branch**: `006-zone-scoping` | **Date**: 2026-09-22 (reworked) | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/006-zone-scoping/spec.md`

## Summary

Adds Zone-Manager coverage assignment to the already-shipped `organization` module, then adds one new precondition to its existing `AccountabilityCommands.assignSchoolManager(...)`: the chosen Manager must currently cover the School's Zone. Unlike this spec's original (unimplemented) plan, **Zone and School↔Zone assignment are not defined here** — both are already fully implemented and shipped by the `school` module (`specs/007-school-zone`), and `organization` reads them live through `school.api.ZoneQueries` rather than owning any copy. Technical approach: one new append-friendly table (`organization_zone_manager_assignment`), following the exact `effective_to IS NULL` / partial-unique-index pattern `SchoolAssignment`/`TeacherAssignment` already established — except uniqueness is on `(zone_id, manager_id)`, not `zone_id` alone, since a Zone may have more than one currently-covering Manager at once. Teacher accountability is explicitly untouched (FR-006/FR-007). The one real risk this plan calls out plainly, carried over from the original: `AccountabilityServiceTest`/`OrganizationIntegrationTest`'s existing, already-passing test cases that call `assignSchoolManager` will start failing the moment the new precondition lands, because none of them set up a Zone/Zone-coverage first — updating those fixtures is in scope as part of this plan, not a follow-up.

## Technical Context

**Language/Version**: Java 25 (unchanged)

**Primary Dependencies**: All already present in `backend/pom.xml`. **No new dependency additions.**

**Storage**: PostgreSQL — one new table, `organization_zone_manager_assignment`, in the `organization` module's own schema area. Migration `V8__create_zone_manager_assignment_table.sql` (Identity=V1, Organization=V2, Audit=V3, School/Zone=V4, School/Places=V5, Teacher=V6, Teacher-Salary-History=V7 — research.md §6).

**Testing**: JUnit 5 + Spring Boot Test — extended `AccountabilityServiceTest` (existing file) for the new Zone-Manager assignment methods (FR-001/002) and the new Zone-coverage precondition on `assignSchoolManager` (FR-003/004), plus a case confirming `assignTeacherManager` is completely unaffected (FR-006/007, SC-004); `OrganizationModuleTest` switches to `BootstrapMode.ALL_DEPENDENCIES` (research.md §8 — `organization` now has a real bean dependency on `school.api.ZoneQueries`); extended `OrganizationIntegrationTest` (existing file) with its `assignSchoolManager`-calling tests now seeding a Zone (via `school`'s real endpoint) and Zone-coverage first, plus new Testcontainers cases for the Zone-Manager-assignment and Zone-coverage endpoints themselves, seeded against `school`'s real, already-shipped Zone endpoints — not a stand-in; ArchUnit unchanged (no new module, no new boundary rule needed — the new type lives in `organization.internal` like everything else already does, and `organization`'s new dependency crosses only `school.api`, an already-`@NamedInterface`-annotated public surface).

**Target Platform**: Single EC2/VM in `ap-south-1`, Docker Compose — unchanged

**Project Type**: Web application (Option 2) — backend changes to the existing `organization` module plus a minimal extension to the existing `AssignmentsPage` (Zone-Manager assign/remove/coverage-lookup controls, research.md §9) — no new frontend page. `school`'s own `ZonesPage` (Zone/Place CRUD) is untouched.

**Performance Goals**: No specific throughput target given the <100-user, ~8-manager scale (Requirements §1-2). The new Zone-coverage check is one cross-module call (`school.api.ZoneQueries.currentZoneForSchool`, already a simple indexed lookup — specs/007) plus one extra indexed lookup inside `organization`'s own table, inside `assignSchoolManager`'s existing transaction — not a new request path.

**Constraints**: FR-003/FR-004 (Manager must currently cover the School's Zone) enforced in `AccountabilityService.assignSchoolManager(...)` itself — not only at the database level — since the constraint spans two modules' data (research.md §3). FR-001 (a Zone may have more than one current covering Manager) is enforced by a partial unique index on `(zone_id, manager_id)`, deliberately not on `zone_id` alone (research.md §2). FR-006/FR-007 (Teacher accountability unchanged) is enforced by *not touching* `TeacherAssignment`/`assignTeacherManager` at all — the strongest guarantee for "unchanged" is "no diff." FR-008 (never own Zone/School-Zone data) is enforced structurally: no `Zone` or `SchoolZoneAssignment` type exists anywhere in `organization`'s source at all, only calls to `school.api.ZoneQueries`.

**Scale/Scope**: One new table, 3 new REST endpoints plus one new rejection case on an existing endpoint, one new frontend extension (no new page); two existing shipped test files updated, not just added to.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Applies? | Assessment |
|---|---|---|
| I. Operational Truth Must Be Auditable | Yes | Zone-Manager assignments are append-only rows (never updated except to set `effective_to` once, never deleted), the same pattern `SchoolAssignment`/`TeacherAssignment` already use — consistent, not a new mechanism. |
| II. Role-Scoped Access and Manager Ownership | Yes — this feature *is* Principle II's correction | Zone-Manager writes are Director/Admin-only (FR-001, matching the existing endpoints' pattern). This is the direct implementation of Amendment 1.6.0's corrected model, now correctly scoped per Amendment 1.7.0 (Zone data itself stays in `school`). |
| III. Payroll/Receivables/Margin Formula-Driven | No | Not applicable. |
| IV. Training & Substitution Parity | No | Not applicable. |
| V. Architecture Is a Modular Monolith With Enforced Boundaries | Yes | The new type lives in `com.hls.organization.internal` — no new bounded-context package, no new `ArchitectureTest` rule needed. `organization` gains exactly one new cross-module dependency, on `school.api` (a `@NamedInterface`-public surface) — the precise shape Amendment 1.7.0 requires, and no cycle (`school` depends on nothing). |
| VI. Concurrency Uses Structured, Virtual-Thread Batch Processing | No (N/A) | Simple per-request reads/writes, no batch job. |
| VII. Reliability, Testability, and Incremental Delivery | Yes | New unit + integration tests before implementation; existing tests updated (not left to silently break) as part of the same change, not deferred. |
| VIII. Security, Identity, and Observability | Yes | Reuses the exact same JWT bearer auth / Director-Admin role check `OrganizationController` already implements — no new auth mechanism. |
| IX. Recruitment and Marketing Are Tracked to Outcome | No | Not applicable. |
| Additional Constraints — stack/deployment | Yes | Java 25 / Spring Boot 4.1.1 / PostgreSQL / single EC2 `ap-south-1` / Docker Compose unchanged. |

**Gate result**: PASS, no open exceptions. This plan's defining characteristic — modifying an already-shipped, tested module's behavior (`assignSchoolManager`) and adding `organization`'s first real cross-module dependency — is not itself a Constitution violation; it's flagged prominently in Summary and Complexity Tracking precisely because it's consequential, not because it's disallowed.

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
│   └── zone-api.yaml     # NEW Zone-Manager endpoints + the one new rejection case on an existing one;
│                         # specs/003's organization-api.yaml is left as-is (historical record);
│                         # school's own Zone/Place endpoints (specs/007/008) are not repeated here
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
│   │   │       │   ├── AccountabilityCommands.java       # EXTENDED: + assignManagerToZone/removeManagerFromZone
│   │   │       │   ├── AccountabilityQueries.java         # EXTENDED: + zoneCoverage
│   │   │       │   ├── AssignmentConflictException.java   # UNCHANGED — a different rejection reason
│   │   │       │   ├── SchoolManagerNotInZoneException.java   # NEW — FR-003/FR-004's rejection (research.md §4)
│   │   │       │   └── dto/
│   │   │       │       ├── ZoneManagerAssignmentView.java       # NEW
│   │   │       │       └── ZoneCoverage.java                     # NEW — composed at read time (research.md §1)
│   │   │       └── internal/
│   │   │           ├── SchoolAssignment.java                       # UNCHANGED
│   │   │           ├── TeacherAssignment.java                      # UNCHANGED — FR-006/FR-007
│   │   │           ├── ZoneManagerAssignment.java                    # NEW JPA entity
│   │   │           ├── ZoneManagerAssignmentRepository.java           # NEW
│   │   │           ├── AccountabilityService.java                     # MODIFIED: new Zone-Manager assignment
│   │   │           │                                                   # methods (research.md §7); assignSchoolManager(...)
│   │   │           │                                                   # gains the Zone-coverage precondition
│   │   │           │                                                   # (FR-003/FR-004); assignTeacherManager(...)
│   │   │           │                                                   # UNTOUCHED (FR-006/007); gains a constructor
│   │   │           │                                                   # dependency on school.api.ZoneQueries
│   │   │           └── OrganizationController.java                     # MODIFIED: new endpoints (contracts/zone-api.yaml)
│   │   │                                                                 # + new @ExceptionHandler for
│   │   │                                                                 # SchoolManagerNotInZoneException -> 422
│   │   └── resources/
│   │       └── db/migration/
│   │           └── V8__create_zone_manager_assignment_table.sql
│   └── test/
│       └── java/com/hls/organization/
│           ├── AccountabilityServiceTest.java   # MODIFIED — existing assignSchoolManager cases now seed Zone
│           │                                    # coverage first (mocked school.api.ZoneQueries); new cases for
│           │                                    # FR-001/002/003/004; a new case proving assignTeacherManager
│           │                                    # is untouched (SC-004)
│           ├── OrganizationIntegrationTest.java  # MODIFIED — same seeding fix (real school endpoints), plus
│           │                                     # new Zone-Manager-assignment/coverage endpoint cases
│           └── OrganizationModuleTest.java        # MODIFIED — BootstrapMode.ALL_DEPENDENCIES (research.md §8)

frontend/
├── src/
│   ├── pages/
│   │   └── AssignmentsPage/          # EXTENDED, not new — a Zone-Manager assign/remove/coverage-lookup
│   │       ├── AssignmentsPage.tsx   # section (research.md §9), plus the school-assignment form now surfaces
│   │       │                         # the new 422 "manager doesn't cover this school's zone" error
│   │       ├── AssignmentsPage.test.tsx
│   │       └── organizationClient.ts  # + zone-manager-assignment/coverage endpoint calls
```

**Structure Decision**: Extends the existing `organization` module in place — no new bounded-context package, no new frontend page. The new type (`ZoneManagerAssignment`/`ZoneManagerAssignmentRepository`) lives in `organization.internal`, reachable only from within `organization` (the existing `ArchitectureTest` rule already covers this — no new rule needed). The one new `api`-level type is `SchoolManagerNotInZoneException`, public for the same reason `AssignmentConflictException` is. `organization`'s dependency footprint changes for the first time: it now depends on `school.api`, one-directional, no cycle.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

*(no Constitution violations — but this plan modifies already-shipped, tested behavior and adds organization's first real cross-module dependency, which deserves the same scrutiny a violation would get)*

| Change | Why Needed | Simpler Alternative Rejected Because |
|--------|------------|---------------------------------------|
| `AccountabilityService.assignSchoolManager(...)` gains a new rejection case, changing behavior for existing callers | FR-003/FR-004 require it — this *is* the feature | Leaving the old, unconstrained behavior and only enforcing Zone coverage in a new, separate endpoint was considered and rejected: it would let the old endpoint keep silently accepting non-covering assignments, which is precisely the leakage Constitution Principle II says must be prevented. |
| `AccountabilityServiceTest`/`OrganizationIntegrationTest` (already shipped, already passing) are modified, not just extended | Their existing `assignSchoolManager` cases will fail once FR-003/FR-004 land, since none of them set up Zone coverage first | Leaving them broken and "fixing later" was rejected — Constitution Principle VII requires tests to reflect real behavior; a red suite from a known, already-understood cause is not an acceptable interim state for this project. |
| `organization` gains a real Spring bean dependency on `school.api.ZoneQueries` — its first cross-module dependency | FR-003/FR-004/FR-005/FR-008 require reading Zone/School-Zone data live from `school`, never owning a copy (Amendment 1.7.0) | Owning a local copy/cache of Zone or School-Zone data was considered (this spec's own original, pre-rework plan did exactly that) and rejected — that is precisely the module-boundary violation Amendment 1.7.0 corrected before any code was written. |
