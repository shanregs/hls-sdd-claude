# Implementation Plan: School Contracts (MoU)

**Branch**: `012-school-contracts` | **Date**: 2026-10-05 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/012-school-contracts/spec.md`

## Summary

Adds the `schoolbilling` bounded context (Constitution Principle VII) with its first half, the contract, and retires
the interim Teacher–School placement of spec 005. The business process is: **MoU contract → Teacher mapping →
attendance capture (spec 008) → month-end billing and salary computation (specs 022 and 013)**. This spec delivers
the first two steps and keeps the third working through the mapping.

- **The MoU contract** per School: number of Teachers, salary the same for all or different for each (one position
  per Teacher), start and end date, and the signing details (date signed, School signatories, HLS Zone Manager
  and/or Director). Only Admin and Director create it; it is never edited, a change is a new MoU.
- **Teacher mapping**: `schoolbilling` takes over the dated Teacher–School rows (`contract_assignment`), now tied
  to a contract position. `teacher` keeps asking "where is this Teacher?" through a new interface in `teacher.api`
  that `schoolbilling` implements, so `TeacherDirectory`, attendance, leave and the Teachers screen do not change.
  A migration copies every placement (same ids) and creates a "MoU pending" contract for each School that had some.
- **Maintenance**: a School Contracts list (status, filled and vacant positions, ends soon), a School's contract
  history, end and cancel, and the signatory candidates.
- **A public `SchoolContracts` interface** that spec 022 (billing) and spec 013 (payroll) read.

New code touches `identity` (permissions, navigation), `teacher` (the placement interface and the Teachers screen
wording) and adds the frontend feature `schoolbilling`. Billing, payments and month close are spec 022.

## Technical Context

**Language/Version**: Java 25 (Spring Boot 4.1.1-based); TypeScript 5.7 with React 19. Unchanged.

**Primary Dependencies**: Spring Web, Spring Data JPA, Spring Security, Spring Modulith, Flyway, ArchUnit. Frontend:
React Router, MUI. No new dependency.

**Storage**: PostgreSQL via `V20__create_school_contract_tables.sql` (contract, contract_position,
contract_signatory, contract_assignment; copies the placements) and `V21__drop_teacher_placement.sql`. Money is
`NUMERIC(12,2)`. Two gist exclusion constraints on assignments (one Teacher, one position). Details in
[data-model.md](./data-model.md).

**Testing**: JUnit 5, Spring Boot Test, Testcontainers (`IntegrationTestBase`). Unit tests for the contract rules
(positions, salary modes, signing validation, one active contract, re-mapping) before the services. Per-role and
per-scope tests on every endpoint (Zone Manager A against Zone Manager B's School). A migration test with
old-shaped placements, and a regression test that attendance and leave figures for a demo month are identical
before and after. Audit-entry assertions for every change. ArchUnit and Modulith rules
(`SchoolBillingModuleRulesTest`). Frontend: Vitest + Testing Library with a fixture per role, axe in both themes.

**Target Platform**: Browser; single Spring Boot deployable.

**Project Type**: Web application (frontend + backend).

**Performance Goals**: the contracts list for 200 Schools loads in under 1 s (one paged query); mapping a Teacher
and re-mapping 30 Teachers each complete in under 2 s.

**Constraints**: scope is applied in every query through `ScopeQueries` (a School outside it is a 404); signing
details and positions are insert-only; a position holds one Teacher on any date and a Teacher one School (database
constraints, not only code); every contract change, mapping and re-mapping writes one audit entry in the same
transaction; the migration keeps ids, dates and statuses of all placements.

**Scale/Scope**: about 100 users, tens of Schools, hundreds of Teachers; 4 tables, about 12 endpoints, 2 screens
(contracts list, School contract page) plus the Teachers screen wording and position field.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Principle I (Operational Truth Must Be Auditable)** - PASS. Every contract creation, end, cancellation,
  mapping, move and re-mapping is audited through `ChangeRecorder`; contracts, signatories and positions are never
  edited, a change is a new contract; assignments keep their history.
- **Principle II (Five Fixed Roles, Configurable Permissions)** - PASS. No new role. Module `SCHOOL_CONTRACTS`
  seeded per the spec's table, runtime-editable, ineligible for Teacher and System. The constitution's Default role
  access matrix gets a "School Contracts" row at merge, with the Admin and Director grants the constitution already
  gives them (View, Create, Edit).
- **Principle III (Data Scope)** - PASS. `ScopeQueries` on every endpoint; the Zone Manager sees only their Schools;
  the responsible Manager is read from `organization`, never copied. Tests per boundary.
- **Principle IV (Role-Based Experience)** - PASS. Server-driven navigation item, hidden actions, DD/MM/YYYY, ₹ with
  Indian grouping, WCAG 2.2 AA, loading, empty and error states, phone width.
- **Principle V (Formula-Driven)** - PASS. The contract holds the salary the School pays per position, which spec
  022 turns into the bill from attendance; nothing here is typed per month.
- **Principle VII (Modular Monolith)** - PASS with one structural change: `teacher.api` gains an interface that
  `schoolbilling` implements, so `teacher` does not depend on `schoolbilling`. `schoolbilling` depends on
  `teacher.api`, `school.api`, `organization.api` and public `identity` types. ArchUnit and Modulith verify no cycle
  and no access to `internal`.
- **Principle IX (Reliability, Testability)** - PASS, requires the tests listed above, rules first.
- **Principle X (Security)** - PASS. Actor from the JWT; positions and amounts validated server-side (positive, two
  decimals, count 1 to 500); free text length-limited and shown as plain text; each HLS signatory must be the School's
  Zone Manager or an active Director (a client cannot name anyone else).
- **Principles VI, VIII, XI** - not applicable (Principle VIII's batch work belongs to billing, spec 022).

No violations requiring justification. Complexity Tracking is not needed. Moving the placement table between modules
is the largest risk; it is handled by the interface, the same-id migration, and the migration and regression tests.

## Project Structure

### Documentation (this feature)

```text
specs/012-school-contracts/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/
│   └── school-contracts-api.md
├── checklists/requirements.md
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
backend/src/main/java/com/hls/
├── identity/
│   ├── permissions/PermissionModule.java       # + SCHOOL_CONTRACTS
│   ├── permissions/PermissionEligibility.java  # Teacher and System excluded
│   ├── permissions/PermissionMatrixService.java    # + seed per spec table
│   └── accessmodel/{NavigationCatalog,AccessModelService}.java  # + OPERATIONS -> School Contracts, scope Assigned
├── teacher/
│   ├── api/TeacherPlacementSource.java         # new interface (implemented by schoolbilling)
│   ├── internal/                               # TeacherPlacement, TeacherPlacementRepository,
│   │                                           #   TeacherPlacementService, PlacementStatus REMOVED;
│   │                                           #   TeacherDirectoryImpl, TeacherSchoolHooks, TeacherService,
│   │                                           #   ManagerTeacherCountEnricher, TeacherScopeService use the interface
│   └── web/TeacherController.java              # placement endpoints call the interface (+ optional positionId)
└── schoolbilling/
    ├── api/                                    # SchoolContracts, ContractView, PositionView, TeacherPosition
    ├── internal/                               # Contract, ContractPosition, ContractSignatory, ContractAssignment
    │                                           #   + repositories; ContractService, AssignmentService (moved placement
    │                                           #   rules + positions), RemapService, ContractListService,
    │                                           #   SignatoryCandidates, SchoolContractsImpl, TeacherPlacementSourceImpl,
    │                                           #   ContractDevSeeder
    └── web/                                    # ContractController, MapTeachersController

backend/src/main/resources/db/migration/V20__create_school_contract_tables.sql
backend/src/main/resources/db/migration/V21__drop_teacher_placement.sql
backend/src/test/java/com/hls/schoolbilling/        # rules, migration, scope, authorization, positions, re-map,
                                                    #   audit, regression, module rules
backend/src/test/java/com/hls/teacher/              # existing placement tests move to the interface

frontend/src/features/schoolbilling/
├── ContractsListPage.tsx          # School Contracts list with filters
├── SchoolContractPage.tsx         # history, MoU form, positions, mapped Teachers, end and cancel
├── MapTeachersDialog.tsx          # map and re-map to a new contract
└── schoolContractsApi.ts
frontend/src/features/teachers/{PlacementDialog,TeachersPage,MyTeacherProfile}.tsx   # "interim" -> assignment, + position
frontend/src/App.tsx                                  # + guarded routes under /operations/school-contracts

docs/postman/                       # new folder "School Contracts"
docs/spec-roadmap.md                # 012 row; new 022 row; 008, 009, 010 rows corrected
.specify/memory/constitution.md     # School Contracts row in the Default role access matrix (patch note)
```

**Structure Decision**: Web application (unchanged layout). One new backend module with the `api` / `internal` /
`web` split, two migrations, the placement code moved out of `teacher.internal`, additive changes to `identity`,
and one new frontend feature folder (shared with spec 022).

### Delivery slices (for tasks.md, each independently demoable)

1. **Foundation**: permissions, navigation, migration `V20`, module skeleton, `TeacherPlacementSource` and the moved
   assignment service; the existing placement tests pass unchanged (US2).
2. **MoU contract (US1)**: contract service, signing validation, endpoints, the MoU form and history.
3. **Teacher mapping (US3)**: positions in the assignment service, map and re-map, the mapping dialog.
4. **Maintenance (US4)**: the list, end and cancel, ends-soon, filters.
5. **Polish**: demo data, docs, Postman, a11y, `V21`, roadmap and constitution row.

## Complexity Tracking

No violations. Table intentionally omitted.
