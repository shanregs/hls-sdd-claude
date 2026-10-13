# Implementation Plan: School Monthly Working Days (School Master Data)

**Branch**: `037-school-monthly-working-days` | **Date**: 2026-10-13 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/037-school-monthly-working-days/spec.md`

## Summary

A master table holding, for each School and calendar month, the number of working days that School counts (0 to the
days in the month, in half days), a MASTER DATA screen to maintain it, and one published read interface other modules
use.

- **Record**: one row per School and month (`school_working_days`), unique on `(school_id, year, month)`, with an
  optimistic-lock version. Hard delete by Admin only; every create, edit and delete is audited and also appended to the
  feature's own history table.
- **Where it lives**: a new sub-package **`attendance.workingdays`** of the existing `attendance` module, the same
  placement 032 used for `attendance.manager`. It cannot live in `school`, because `attendance` already reads `school`
  and the fallback needs the spec 008 calendar (`WorkingCalendar`), so `school` -> `attendance` would be a cycle. No
  new top-level module, so no Principle VII amendment.
- **Fallback**: with no row, the answer is the number of working dates the spec 008 calendar gives that School for that
  month, labelled `CALENDAR_DERIVED`; a stored row is labelled `STORED`.
- **Published interface**: `attendance.api.SchoolWorkingDays` (`forMonth`, `forMonth` for many Schools, `forYear`,
  `forRange`), constant number of queries, no person data, no screen permission needed (module-to-module read).
- **Screen**: MASTER DATA -> School Working Days, an MUI X data grid: filters (School, Zone, year), search by School
  name, sort, page; inline edit with Cancel; add row; delete with confirmation; per-row history; "Deleted rows" view;
  copy a year (P3, in scope per the clarification).
- **Consumers**: payroll (013a/013b), Manager attendance (032) and the rollups (008) adopt the interface in separate
  amendments; until then nothing changes (FR-012).

Clarified rules (spec session 2026-10-13): year copy is in this delivery; a fixed notice on edit and delete about
finalised months, with no live check; months accepted from current year minus 5 to plus 5; editing a row another person
already deleted is refused and never recreates it; a "Deleted rows" history view built from audit entries.

## Technical Context

**Language/Version**: Java 25 (Spring Boot 4.1.1-based); TypeScript 5.7 with React 19. Unchanged.

**Primary Dependencies**: Spring Web, Spring Data JPA, Spring Security, Spring Modulith, Flyway, ArchUnit. Frontend:
React Router, MUI and `@mui/x-data-grid` (already used). No new dependency.

**Storage**: PostgreSQL through one migration, **V52** (V51 is the highest on `origin/main` as implemented; take the next free
number when implementing). Two tables: `school_working_days` and `school_working_days_history`. Details in
[data-model.md](./data-model.md).

**Testing**: JUnit 5, Spring Boot Test, Testcontainers (`IntegrationTestBase`). A pure `WorkingDaysRules` validator
(range by month, leap February, half steps, year window) is tested first with hand-written tables; per-role tests on
every endpoint; concurrency tests (stale version, row deleted); the read API in bulk with a query counter; ArchUnit rules
(`WorkingDaysModuleRulesTest`); frontend Vitest with a fixture per role and axe in both themes.

**Target Platform**: Browser (web only). Mobile does not show this screen.

**Project Type**: Web application (frontend + backend), single Spring Boot deployable.

**Performance Goals**: a filtered grid page for 500 Schools and 6,000 rows in a year in under 2 s (SC-008): one query
for the year's rows, one `SchoolDirectory` read, one `WorkingCalendar.workingDaysBySchool` call for the year, all
independent of the number of Schools; the read API answers a year for many Schools in the same fixed number of queries.

**Constraints**: the backend refuses every request outside the caller's permission and fails closed; the actor comes
from the JWT; "today" for the year window is `WorkingCalendar.businessToday()` (Asia/Kolkata), never the device; an edit
or delete carries the row version; refused changes write no audit and no history row.

**Scale/Scope**: up to about 500 Schools x 12 months = 6,000 rows per year; 4 permission actions; about 10 endpoints; one
screen with 4 dialogs; 2 tables.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Principle I (Auditable)** - PASS. Create, edit, delete and each copied row publish `EntityChanged` (actor, time, field,
  old and new value) in the same transaction as the write, so the central trail has every change; a deleted row's last
  figure is kept. The actor's roles at the time are recorded in the feature's own history table (`actor_roles`), which is
  append-only (no update or delete path) and is what the screen shows. Refused changes write nothing.
- **Principle II (Five fixed roles, configurable permissions)** - PASS. No new role. One permission module
  `SCHOOL_WORKING_DAYS` (View, Create, Edit, Delete) seeded for Admin (all) and Director (View, Create, Edit);
  runtime-editable. Teacher and System are ineligible; Manager is eligible for View only and off by default.
- **Principle III (Data scope)** - PASS. Admin and Director are organization-wide. A Manager who is later granted View
  is limited to their assigned Schools through the same `organization` scope query attendance uses; the published read
  interface carries no person data.
- **Principle IV (Role-based experience)** - PASS. Server-driven menu item (MASTER DATA); unauthorized actions hidden,
  not disabled; deep link without View shows the not-authorized page; loading, empty and error states; inline
  validation; DD/MM/YYYY; WCAG 2.2 AA; no horizontal page scroll at phone width.
- **Principle V (Payroll formula-driven)** - PASS. The figure is one input that payroll can read; nothing is typed
  per payslip. Adoption is a later amendment; until then payroll keeps its calculation.
- **Principle VII (Modular monolith)** - PASS with an additive interface: `attendance.api` gains `SchoolWorkingDays`
  (and its small records). The new `attendance.workingdays.internal` reads `school.api` (`SchoolDirectory`) and
  `organization.api` (scope) only; nothing reads its tables. ArchUnit verifies.
- **Principle VIII (Concurrency)** - the screen path is the request model. Year copy is one transaction over at most
  12 rows; no batch concurrency needed.
- **Principle IX (Reliability, testability)** - PASS, tests first as above. The figure will affect pay once adopted, so
  the rules table in research.md gets the same financial review as 013a before any consumer adopts it.
- **Principle X (Security)** - PASS. Permission and scope enforced on every endpoint independent of the menu; fail
  closed; request bodies validated (ranges, enums, length); reasons and notes are not part of this feature.
- **Principles VI, XI** - not applicable.

Constitution amendment (PATCH 2.5.4, as for earlier master-data specs): the Default role access matrix gains a School
Working Days row; the navigation model gains the MASTER DATA item. No principle changes.

No violations requiring justification. Complexity Tracking is not needed.

**Post-design re-check (after data-model.md and contracts)**: unchanged, all PASS. The one design point worth watching
is the grid's in-memory sort and page over one year's rows (research.md section 4); it is bounded by 6,000 rows.

## Project Structure

### Documentation (this feature)

```text
specs/037-school-monthly-working-days/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/
│   ├── school-working-days-api.md        # HTTP, for the screen
│   └── school-working-days-read-api.md   # Java interface, for payroll, attendance and Manager attendance
├── checklists/requirements.md
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
backend/src/main/java/com/hls/
├── identity/
│   ├── permissions/PermissionModule.java        # + SCHOOL_WORKING_DAYS(VIEW, CREATE, EDIT, DELETE)
│   ├── permissions/PermissionEligibility.java   # Admin/Director/Manager(View only); Teacher and System excluded;
│   │                                            #   new map (the existing ones are full)
│   ├── permissions/PermissionMatrixService.java # seed: Admin all four, Director View/Create/Edit
│   └── accessmodel/{NavigationCatalog,AccessModelService}.java  # + MASTER DATA item, data scope
├── attendance/
│   ├── api/                                     # SchoolWorkingDays (read interface), WorkingDaysAnswer records
│   └── workingdays/
│       ├── internal/                            # SchoolWorkingDaysRow (+ repository), WorkingDaysHistory (+ repository),
│       │                                        #   WorkingDaysRules (pure validator), WorkingDaysService (CRUD, audit),
│       │                                        #   WorkingDaysGridService (filter/sort/page, calendar reference),
│       │                                        #   YearCopyService, SchoolWorkingDaysImpl (read API),
│       │                                        #   WorkingDaysScope, WorkingDaysDevSeeder
│       └── web/                                 # SchoolWorkingDaysController, WorkingDaysExceptionAdvice (if needed)
└── (audit consumes EntityChanged; no change)

backend/src/main/resources/db/migration/V52__create_school_working_days.sql

frontend/src/
├── features/schoolWorkingDays/                  # SchoolWorkingDaysPage, WorkingDaysGrid, AddRowDialog,
│                                                #   DeleteDialog, HistoryDialog, DeletedRowsDialog, CopyYearDialog,
│                                                #   schoolWorkingDaysApi.ts (+ tests)
├── App.tsx                                      # route /master-data/school-working-days with the View guard
└── a11y/a11y.test.tsx                           # + the new page in both themes

docs/spec-roadmap.md                             # + row 037 (number reserved, see spec Assumptions)
.specify/memory/constitution.md                  # PATCH 2.5.4: matrix row and MASTER DATA item
```

**Structure Decision**: web application with the feature inside the `attendance` module as a sub-package, following the
`attendance.manager` precedent. A standalone `workingdays` module was rejected: it would add a Principle VII module list
change for one table. Placing it in `school` was rejected: a `school` -> `attendance` dependency creates a cycle.

## Complexity Tracking

Not needed; no constitution violations.
