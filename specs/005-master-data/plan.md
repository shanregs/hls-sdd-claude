# Implementation Plan: Master Data (Zones, Schools, Managers, Teachers)

**Branch**: `005-master-data` | **Date**: 2026-10-03 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/005-master-data/spec.md`

## Summary

Adds the three bounded contexts Constitution Principle VII reserves for master data and wires them
into the role-based foundation from specs 001-004:

- `school` owns **Zones**, **Places** (including bulk import) and **Schools** (each School in
  exactly one Place; its Zone is its Place's Zone).
- `organization` owns **Manager** records, the dated **Zone-Manager** and **School-Manager**
  assignments, the rule that a School's Manager must cover the School's Zone, and the single
  shared **scope query** every later module uses (`ScopeQueries`). It reads Zone/School data only
  through `school`'s public API.
- `teacher` owns **Teachers** (profile, final-exit status machine), dated **placements** (interim
  Teacher-School link, including future-dated moves), append-only **salary history**, and the
  Teacher-level scope query. It reads scope from `organization` and Schools from `school` through
  their public APIs.

Cross-module vetoes (a School move that would strand its Manager; deactivating a School that still
has Teachers; deleting a Zone that still has Managers) are done with small guard interfaces defined
in the *owning* module's public API and implemented by the *dependent* module, so the module graph
stays one-directional (`teacher -> organization -> school`) with no cycles. Every change publishes
one generic `EntityChanged` event defined in `audit`'s public API; `audit` appends it to Change
History - no module writes audit tables. `identity` gains five permission modules, seed grants and
navigation items (additive). The frontend adds a MASTER DATA section (Zones, Schools, Managers,
Teachers), real Manager dashboard widgets, and a Teacher block on My Profile.

## Technical Context

**Language/Version**: Java 25 (backend, Spring Boot 4.1.1-based); TypeScript 5.7 with React 19
(frontend) - unchanged from specs 001-004.

**Primary Dependencies**: Spring Web, Spring Data JPA (optimistic locking via `@Version`), Spring
Security (JWT resource server), Spring Modulith (`@ApplicationModuleListener`, `NamedInterface`
packages), Flyway, ArchUnit - all already in `backend/pom.xml`; no new backend dependency.
Frontend: React Router, MUI core, MUI X DataGrid Community, `react-hook-form` - all present; no new
frontend dependency.

**Storage**: PostgreSQL via Flyway. Three new migrations (`V11` school tables, `V12` organization
tables, `V13` teacher tables), each module owning its own tables with no cross-module foreign keys
(ids only, validated through the owning module's public API - the same posture `audit` uses). No
change to any audit table: master-data changes land in the existing `change_history_entry`.

**Testing**: Backend - JUnit 5, Spring Boot Test, Testcontainers (PostgreSQL), per-role/per-action
authorization tests on every endpoint and **per scope boundary** (two Managers, different Zones)
on every list/search/detail/export (Constitution Principle IX), Spring Modulith/ArchUnit module
verification extended for the three new modules. Frontend - Vitest + React Testing Library, role
fixtures asserting menu/action visibility, axe-core in both themes.

**Target Platform**: Browser (desktop, tablet, phone widths); single Spring Boot deployable.

**Project Type**: Web application (frontend + backend).

**Performance Goals**: Bulk import of 1,000 Places reports per-row results in under 30 s (SC-004);
Manager dashboard counts in under 3 s (SC-007); list endpoints paginated (default 25, max 100).
Scope resolution is a handful of indexed queries per request - at under 100 users and low thousands
of Teachers no caching is needed, which also gives "takes effect on the next request" (FR-022) for
free.

**Constraints**: Every endpoint independently enforces permission (`PermissionGuard`) **and** data
scope server-side (Principle X); an out-of-scope record is indistinguishable from a missing one
(404, FR-020). Salary data is only reachable through `TEACHER_SALARY`-guarded endpoints and is
absent from every other DTO (FR-019). The "School's Manager covers the School's Zone" invariant
must hold after every operation, enforced atomically (all-or-nothing, FR-009). Placement history
never overlaps and has no gaps (FR-012). Concurrent edits are detected, not overwritten (FR-026).

**Scale/Scope**: Under 100 users growing ~30%/year; tens of Zones, hundreds of Schools, low
thousands of Teachers, up to 5,000 Places per import. Three modules, ~9 tables, ~35 endpoints,
four new list screens plus dialogs.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Principle I (Operational Truth Must Be Auditable)** - PASS. Every create/edit/status/assignment/
  placement/salary/deactivation change publishes `EntityChanged` (actor, time, entity, field,
  before, after); `audit` appends it to Change History. Salary history and assignments are
  append-only tables as well, so the audit trail is corroborated by the domain's own history.
- **Principle II (Five Fixed Roles, Configurable Permissions)** - PASS. No role added. Five new
  permission modules (`ZONES`, `SCHOOLS`, `MANAGERS`, `TEACHERS`, `TEACHER_SALARY`) are seeded to
  the Constitution's default matrix (spec.md Role & Permission Impact) and stay runtime-editable.
- **Principle III (Data Scope and Zone-Based Manager Ownership)** - PASS, and this spec is where it
  becomes real: Manager -> Zones -> Schools -> Teachers derived from assignments and placements,
  exposed once as `ScopeQueries` / `TeacherScopeQueries`, applied to lists, search, counts, exports
  and detail views. A School's Manager is always one of its Zone's Managers (enforced). The interim
  Teacher-School placement is documented as temporary in the spec, data model and UI label, as the
  Constitution requires.
- **Principle IV (Role-Based Experience)** - PASS. New `NavigationCatalog` items under MASTER DATA
  are server-driven; row/toolbar actions are gated by the access model's per-action grants; Manager
  dashboard widgets and Teacher My Profile are derived from the same model; consistent
  list/search/pagination, empty/loading/error states and inline validation; dates DD/MM/YYYY,
  amounts in rupees with Indian grouping.
- **Principle V (Payroll/Receivables/Margin)** - N/A here; the salary history is the dated input
  Payroll will later consume, deliberately stored append-only and queryable "as of" a date.
- **Principle VII (Modular Monolith)** - PASS with one deliberate mechanism. Packages `school`,
  `organization`, `teacher`, with public APIs in `*.api` named interfaces and everything else
  internal. Dependency direction `teacher -> organization -> school`; `school` never imports its
  dependents - it exposes guard SPIs that they implement (research.md section 2). `audit` is the
  only audit store. ArchUnit + `ApplicationModulesTest` must stay green.
- **Principle IX (Reliability, Testability)** - PASS, requires: per-role authorization tests on every
  endpoint; per-scope-boundary tests (Manager A vs Manager B) on every list/search/detail/export;
  invariant tests for the Manager-covers-Zone rule, placement non-overlap, status machine, salary
  "as of"; UI tests of menu and action visibility per role.
- **Principle X (Security, Identity, Observability)** - PASS. Backend is the control; 404 for
  out-of-scope ids; fail-closed guards; the Manager's School edit is field-limited server-side
  (FR-006); structured logs carry no salary values; the Audit screens hide master-data and salary
  entries from callers lacking the matching `VIEW` grant, so System never sees school or teacher
  data (research.md section 16).
- **Principles VI, VIII, XI** - not applicable.

No violations requiring justification. Complexity Tracking is not needed.

## Project Structure

### Documentation (this feature)

```text
specs/005-master-data/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/
│   └── master-data-api.md
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
backend/src/main/java/com/hls/
├── audit/
│   ├── api/EntityChanged.java                 # new named interface: the one generic change event
│   ├── changehistory/EntityChangedConsumer.java   # new: appends to change_history_entry
│   └── support/AuditVisibility.java           # new: entity type -> required VIEW grant filter
├── identity/
│   ├── permissions/PermissionModule.java      # + ZONES, SCHOOLS, MANAGERS, TEACHERS, TEACHER_SALARY
│   ├── permissions/PermissionMatrixService.java   # + seed defaults per spec.md table
│   └── accessmodel/NavigationCatalog.java     # + MASTER DATA items
├── school/
│   ├── api/                                   # NamedInterface: ZoneQueries/Commands, PlaceQueries/
│   │                                          #   Commands, SchoolQueries/Commands, views, and the
│   │                                          #   guard SPIs (SchoolChangeGuard, ZoneChangeGuard,
│   │                                          #   SchoolDeactivationGuard)
│   ├── internal/                              # entities, repositories, services (Zone, Place, School)
│   └── web/                                   # ZoneController, PlaceController, SchoolController
├── organization/
│   ├── api/                                   # ManagerQueries/Commands, ScopeQueries, ScopeView
│   ├── internal/                              # Manager, ZoneManagerAssignment, SchoolManagerAssignment,
│   │                                          #   services, guard implementations
│   └── web/                                   # ManagerController, ScopeController (me/scope)
└── teacher/
    ├── api/                                   # TeacherQueries/Commands, TeacherScopeQueries, views
    ├── internal/                              # Teacher, TeacherPlacement, SalaryHistoryEntry, services,
    │                                          #   SchoolDeactivationGuard implementation
    └── web/                                   # TeacherController, TeacherSalaryController, MyProfile

backend/src/main/resources/db/migration/
├── V11__create_school_tables.sql
├── V12__create_organization_tables.sql
└── V13__create_teacher_tables.sql

backend/src/test/java/com/hls/{school,organization,teacher}/   # integration + unit + module tests

frontend/src/features/
├── zones/        # ZonesPage, ZoneDialog, PlacesPanel, BulkImportPlacesDialog, api
├── schools/      # SchoolsPage, SchoolDialog, api
├── managers/     # ManagersPage, ManagerDialog, AssignZonesDialog, AssignSchoolManagerDialog, api
└── teachers/     # TeachersPage, TeacherDialog, PlacementDialog, StatusDialog, SalaryPanel, api
frontend/src/dashboards/ManagerDashboard.tsx   # real widgets (replaces placeholders)
frontend/src/account/ProfilePage.tsx           # + Teacher profile block
frontend/src/App.tsx                           # + four guarded routes
```

**Structure Decision**: Web application (`backend/` + `frontend/`, unchanged layout). Three new
backend modules following the established `api` (named interface) / `internal` split, one Flyway
migration per module, additive changes to `identity` (permissions, navigation) and `audit` (one
public event type and one consumer), and four new frontend feature folders mirroring
`features/users` and `features/audit`.

### Delivery slices (for tasks.md, each independently demoable)

1. **Foundation**: permission modules/seed/nav, `audit.api.EntityChanged` + consumer, module
   skeletons and migrations, shared pagination/DTO helpers.
2. **Zones and Places (US1, US8)**: Zone CRUD, Places, lookup, bulk import.
3. **Schools and Managers (US2, US3)**: School CRUD with Place, Manager records, assignments, the
   Manager-covers-Zone invariant and guards.
4. **Teachers and scope (US4, US5, US9)**: Teacher CRUD, status machine, placements, `ScopeQueries`
   enforcement everywhere, salary history.
5. **Experience (US6, US7)**: Manager dashboard widgets, Teacher My Profile, accessibility pass.

## Complexity Tracking

No violations. Table intentionally omitted.
