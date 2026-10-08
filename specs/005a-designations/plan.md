# Implementation Plan: Designations and Employment Details

**Branch**: `005a-designations` | **Date**: 2026-10-08 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/005a-designations/spec.md` (amendment A1 to spec 005; prerequisite of 013a and 032)

## Summary

Adds a list of **designations** (job titles for Teachers or Managers) under MASTER DATA, and employment details on the
people of spec 005: a Manager gets a dated designation history, an employee id, a joining date and an exit date; a
Teacher gets a current designation and an optional employee id. Admin and Director maintain it; a Zone Manager reads
it in scope. Every change is audited. Other modules read it only through public interfaces, which is what specs 013a
(salary structures) and 032 (Manager attendance) need.

- **New bounded context `designation`** (`api` / `internal` / `web`): the `designation` table, the cross-person
  employee-id registry, the list endpoints, and the interfaces `DesignationDirectory` and `EmployeeIds`. A separate
  module is needed because the list is shared by Managers (module `organization`) and Teachers (module `teacher`),
  which both depend on it, while the list also shows how many people hold each designation, which needs both of
  them: the counts therefore come through a small SPI (`HolderCounter`) that `organization` and `teacher`
  implement, the same enricher pattern spec 005 already uses. There is no dependency cycle.
- **`organization`**: columns on `manager` (employee id, joining date, exit date), the append-only table
  `manager_designation`, the employment endpoints, the "missing details" filter, default exit date on deactivation.
- **`teacher`**: columns on `teacher` (designation id, employee id), the employment endpoint, the "missing
  designation" filter.
- **`identity`**: permission module `DESIGNATIONS`, eligibility, matrix seed, navigation MASTER DATA -> Designations.
- **Frontend**: a Designations page; the Managers and Teachers screens show and edit the new fields.
- **Public interfaces for 013a and 032** (exact names in [contracts/designations-api.md](./contracts/designations-api.md)):
  - `organization.api.ManagerQueries`: `designationOn(managerId, date)`, `designationsOn(managerIds, date)`,
    `employment(managerIds)`, `holderCountsByDesignation(designationIds)`.
  - `teacher.api.TeacherDirectory`: `currentDesignation(teacherId)`, `currentDesignations(teacherIds)`,
    `employment(teacherIds)`, `holderCountsByDesignation(designationIds)`.
  - `designation.api.DesignationDirectory`: `find(id)`, `findAll(ids)` giving existence, kind (TEACHER or MANAGER)
    and the retired flag.

## Technical Context

**Language/Version**: Java 25 (Spring Boot 4.1.1-based); TypeScript 5.7 with React 19. Unchanged.

**Primary Dependencies**: Spring Web, Spring Data JPA, Spring Security, Spring Modulith, Flyway, ArchUnit. Frontend:
React Router, MUI. No new dependency.

**Storage**: PostgreSQL, one file `V24__create_designations.sql`: `designation`, `employee_id_claim`,
`manager_designation` (append-only, trigger), and new columns on `manager` and `teacher`. No cross-module foreign
keys (designation ids are plain ids validated through `designation.api`). Details in [data-model.md](./data-model.md).

**Testing**: JUnit 5, Spring Boot Test, Testcontainers (`IntegrationTestBase`). Unit tests for the rules before the
services (name normalization, employee-id format and key, effective-date rules, designation-on-a-date table of 6+
cases for SC-008, exit-date window). Per-role and per-scope tests on every endpoint (SC-004), audit assertions per
field (SC-002), a concurrency test for duplicate employee ids (SC-003), a query-count test for the bulk interfaces
on 300 people (SC-007), Modulith/ArchUnit rules (`DesignationModuleRulesTest`). Frontend: Vitest + Testing Library
with a fixture per role, axe in both themes.

**Target Platform**: Browser; single Spring Boot deployable.

**Project Type**: Web application (frontend + backend).

**Performance Goals**: bulk reads of designation, employee id and dates for 300 people in a fixed number of
queries and under 2 s (SC-007); the Designations list loads in one page query plus one count query per kind.

**Constraints**: the backend is the security control (Principle X): every change needs `DESIGNATIONS` `EDIT`
(`CREATE` for a new designation), never inferred from `MANAGERS` or `TEACHERS` edit; Manager designation rows are
never updated or deleted (database trigger as well as code); employee id uniqueness is a database constraint
(primary key on the normalized key), not only code; each change writes audit entries in the same transaction.

**Scale/Scope**: about 100 users, tens of Managers, hundreds of Teachers, tens of designations; 3 tables, about 12
endpoints, 1 new screen plus changes to 2.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Principle I (Auditable)** - PASS. Every designation change (create, rename, retire, reactivate), every Manager
  and Teacher field change and every default exit date written on deactivation is audited through `ChangeRecorder`
  with old and new values; Manager designation rows are append-only.
- **Principle II (Five Fixed Roles, Configurable Permissions)** - PASS. No new role. Module `DESIGNATIONS`
  (`VIEW`, `CREATE`, `EDIT`) seeded to Admin and Director, runtime-editable; Teacher and System ineligible; Manager
  eligible for `VIEW` only. The constitution's Default role access matrix gets a Designations row (patch 2.5.1).
- **Principle III (Data Scope)** - PASS. Designations are org-wide master data; the fields on a person are exposed
  through the existing scoped Manager and Teacher queries, so a Zone Manager sees only people in scope (a Teacher
  outside it is a 404 as before). The `employment` interfaces are server-side reads for other modules.
- **Principle IV (Role-Based Experience)** - PASS. Server-driven navigation item under MASTER DATA, hidden actions,
  DD/MM/YYYY, WCAG 2.2 AA, loading, empty and error states, phone width.
- **Principle V (Formula-Driven)** - PASS. This spec stores facts payroll later uses (designation on a date, joining
  and exit dates); no figure is computed here.
- **Principle VII (Modular Monolith)** - PASS with one new module `designation` (no dependency on `organization` or
  `teacher`; both depend on `designation.api`). Counts flow through an SPI implemented by the dependents, so there is
  no cycle. ArchUnit and Modulith verify. The constitution's module list is amended (patch 2.5.1).
- **Principle IX (Reliability, Testability)** - PASS, tests listed above, rules first.
- **Principle X (Security)** - PASS. Actor from the JWT; inputs validated server-side (name 1-80, employee id
  `[A-Za-z0-9-]{1,20}`, dates); free text shown as plain text; the System role gets no business data.
- **Principles VI, VIII, XI** - not applicable.

No violations requiring justification. Complexity Tracking omitted.

## Project Structure

### Documentation (this feature)

```text
specs/005a-designations/
├── plan.md              # This file
├── research.md          # Phase 0
├── data-model.md        # Phase 1
├── quickstart.md        # Phase 1
├── contracts/
│   └── designations-api.md
├── checklists/requirements.md
└── tasks.md             # Phase 2 (/speckit-tasks)
```

### Source Code (repository root)

```text
backend/src/main/java/com/hls/
├── identity/
│   ├── permissions/{PermissionModule,PermissionEligibility,PermissionMatrixService}.java   # + DESIGNATIONS
│   └── accessmodel/{NavigationCatalog,AccessModelService}.java    # + MASTER DATA -> Designations
├── designation/
│   ├── api/                  # DesignationDirectory, EmployeeIds, HolderCounter, BusinessDate, package-info
│   ├── internal/             # Designation, DesignationRepository, DesignationService, EmployeeIdClaim(+Repository),
│   │                         #   EmployeeIdService, DesignationDevSeeder
│   └── web/DesignationController.java
├── organization/
│   ├── api/{ManagerQueries,ManagerView}.java           # + employment, designationOn, holder counts
│   ├── internal/{Manager,ManagerService,ManagerAccountSync}.java, ManagerDesignation(+Repository),
│   │                  ManagerEmploymentService, ManagerHolderCounter, OrganizationDevSeeder
│   └── web/ManagerController.java                      # + employment, designation, missing filter
└── teacher/
    ├── api/{TeacherDirectory,TeacherView}.java         # + employment, designation reads
    ├── internal/{Teacher,TeacherService,TeacherDirectoryImpl}.java, TeacherEmploymentService, TeacherHolderCounter
    └── web/{TeacherController,TeacherMeController}.java

backend/src/main/resources/db/migration/V24__create_designations.sql
backend/src/test/java/com/hls/designation/            # rules, API per role, audit, ids, module rules
backend/src/test/java/com/hls/{organization,teacher}/ # employment tests, bulk interface tests

frontend/src/features/designations/{DesignationsPage.tsx,designationsApi.ts,+tests}
frontend/src/features/{managers,teachers}/            # fields, editor dialog, missing filter
frontend/src/App.tsx                                  # + guarded route /master-data/designations

docs/postman/                       # new folder "Designations"
docs/spec-roadmap.md                # 005a row
.specify/memory/constitution.md     # Designations row, module list (patch 2.5.1)
```

**Structure Decision**: web application (unchanged layout). One new backend module with the `api` / `internal` /
`web` split, one migration, additive changes to `organization`, `teacher` and `identity`, one new frontend feature.

### Delivery slices (for tasks.md, each independently demoable)

1. **Foundation**: permission, navigation, migration `V24`, module skeleton, entities.
2. **List of designations (US1)**: the Designations screen.
3. **Manager details (US2)** and **Teacher details (US3)**: fields, history, uniqueness, public interfaces.
4. **Missing details (US4)**: counts, filters.
5. **Polish**: seed, docs, Postman, constitution, full test run.

## Complexity Tracking

No violations. Table intentionally omitted.
