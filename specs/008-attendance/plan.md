# Implementation Plan: Attendance

**Branch**: `008-attendance` | **Date**: 2026-10-04 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/008-attendance/spec.md`

## Summary

Adds the `attendance` bounded context (Constitution Principle VII) on top of spec 005's master data:

- One **current mark per Teacher per date** (status code, whole or half day value, optional note,
  the School of the placement in effect that date), with an append-only **mark history** and
  Change History events for every change.
- A pure, heavily unit-tested **rollup calculator** (working days, worked, leave, training,
  unmarked, weighted total) driven by the Teacher's placements, per-School weekly off days, the
  organization-wide non-working dates, status code weights and the marks.
- **Self-marking** by Teachers inside a 3-day window, **supervisor marking** (Manager in scope,
  Admin, Director) for any unlocked date, and a rule that a supervisor-set day can no longer be
  changed by the Teacher.
- **Month grids** (Manager: their scope; Admin/Director: everyone, with Zone/School/Manager/status
  filters), a Teacher calendar and history, CSV export.
- **Setup**: status codes, default weekly off days, per-School overrides, non-working dates.
- **Month lock and reopen** per Teacher-month: lock requires every working day marked and the month
  ended; reopen needs a reason; the rollup is frozen at lock.
- A small public **read contract** (`attendance.api`) so leave, payroll and reports never recompute
  attendance.
- *(2026-10-04)* A **Holiday Calendar** screen under MASTER DATA, visible to every role and editable
  by Admin and Director (permission module `HOLIDAY_CALENDAR`), with a yearly and a monthly calendar
  that highlight holidays and a download icon that saves the year or month as a **PDF** (built in
  the browser). **Day cells** in the grids and calendars are coloured by kind (weekly off, holiday,
  leave/absent) with a text label as well. Three more status codes (`S`, `H`, `A`) are seeded by
  `V15`, and Attendance Setup moves to MASTER DATA.

`attendance` depends on `teacher`, `organization` and `school` only through their public APIs
(`attendance -> teacher -> organization -> school`). `teacher.api` gains one read-only
`TeacherDirectory` (placements per date range, teacher info). `identity` gets four permission
modules, seeds, navigation and data-scope entries; `audit` gets visibility mappings. No new
dependency of `audit` on any module.

## Technical Context

**Language/Version**: Java 25 (backend, Spring Boot 4.1.1-based); TypeScript 5.7 with React 19
(frontend) - unchanged.

**Primary Dependencies**: Spring Web, Spring Data JPA (`@Version`), Spring Security, Spring
Modulith, Flyway, ArchUnit - all already present. Frontend: React Router, MUI core, `react-hook-form`
- all present; the month grid is a custom MUI table (28-31 day columns, sticky first column), not a
DataGrid. One new frontend dependency, added for User Story 9: `jspdf` (client-side PDF of the
holiday calendar, loaded on demand; research.md section 16). No new backend dependency.

**Storage**: PostgreSQL via Flyway: `V14__create_attendance_tables.sql` (status codes with seeded
defaults, marks, mark history, weekly-off settings, non-working dates, teacher-month state and
events) and the data-only `V15__seed_more_attendance_status_codes.sql` (`S`, `H`, `A`). The Holiday
Calendar view and PDF add no tables. No cross-module foreign keys; `teacher_id`, `school_id`, `user_id` are plain ids
validated through the owning module's public API.

**Testing**: JUnit 5, Spring Boot Test, Testcontainers (shared singleton container via
`IntegrationTestBase`), per-role/per-action authorization tests on every endpoint, two-Manager
scope-boundary tests on every grid/detail/export, a table-driven unit suite for the rollup
calculator, a lock/mark concurrency test, ArchUnit rules for the new module. Frontend: Vitest +
Testing Library with role fixtures, axe-core in both themes.

**Target Platform**: Browser (desktop, tablet, phone widths); single Spring Boot deployable. Native
mobile and offline capture are out of scope (spec Assumptions).

**Project Type**: Web application (frontend + backend).

**Performance Goals**: SC-007 - first page of a 500-Teacher month grid in under 3 s and a CSV export
of the same filter in under 30 s. Achieved by loading placements, marks and settings in bulk for the
page of Teachers (no per-Teacher queries) and streaming the export.

**Constraints**: Every endpoint enforces permission **and** data scope server-side, using only
`ScopeQueries`/`TeacherScopeQueries` (never its own scoping); out-of-scope Teachers are
indistinguishable from missing (404). Business dates (today, the 3-day window, "month has ended",
"unmarked up to today") use the **Asia/Kolkata** calendar date, not the UTC date the application
`Clock` yields (research.md section 9). Lock and mark writes for one Teacher-month are serialized
(research.md section 7). A locked Teacher-month is immutable except through reopen.

**Scale/Scope**: Under 100 users, low thousands of Teachers, ~30 marks per Teacher-month; one
module, 7 tables, ~25 endpoints, five screens.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Principle I (Operational Truth Must Be Auditable)** - PASS. Every mark, correction, clear,
  lock, reopen, relock, code, calendar change and export publishes `EntityChanged` (actor, time,
  before, after); mark values additionally live in an append-only mark history and the lock events
  table, so the trail is corroborated by the domain's own history. Audit rows are never edited.
- **Principle II (Five Fixed Roles, Configurable Permissions)** - PASS. No new role. Four
  permission modules seeded to the spec's table and runtime-editable; Admin/Director hold
  `PROCESS` (lock/reopen) as Constitution v2 gives Admin payroll authority.
- **Principle III (Data Scope)** - PASS. All scoping comes from the shared scope APIs delivered in
  spec 005. Manager grids/marks cover Teachers in their *current* scope; Teacher self-service is
  own record only; System sees nothing.
- **Principle IV (Role-Based Experience)** - PASS. New server-driven navigation items (OPERATIONS
  and MY ATTENDANCE sections), actions gated by the access model, consistent loading/empty/error
  states, DD/MM/YYYY dates, keyboard- and screen-reader-usable grid.
- **Principle VII (Modular Monolith)** - PASS. `attendance` reads other modules only via
  `teacher.api.TeacherDirectory`, `teacher.api.TeacherScopeQueries`, `organization.api.*` and
  `school.api.SchoolDirectory`; exposes `attendance.api` for later specs; ArchUnit rules extended.
- **Principle IX (Reliability, Testability)** - PASS, requires: per-role authorization tests per
  endpoint, per-scope-boundary tests, calculator tables, lock invariants, concurrency, UI role
  fixtures.
- **Principle X (Security, Identity, Observability)** - PASS. Backend is the control; supervisor-set
  days protected from Teacher overwrite server-side; no secrets; structured logs without PII.
- **Principles V, VI, VIII, XI** - not applicable now (V: payroll later reads the frozen rollup via
  the read contract; VI: training calendar belongs to spec 016; VIII: no batch processing yet).

No violations requiring justification. Complexity Tracking is not needed.

## Project Structure

### Documentation (this feature)

```text
specs/008-attendance/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/
│   └── attendance-api.md
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
backend/src/main/java/com/hls/
├── identity/
│   ├── permissions/PermissionModule.java       # + ATTENDANCE, TEACHER_ATTENDANCE, MY_ATTENDANCE, ATTENDANCE_SETUP
│   ├── permissions/PermissionMatrixService.java    # + seed defaults per spec.md table
│   └── accessmodel/{NavigationCatalog,AccessModelService}.java  # + OPERATIONS / MY ATTENDANCE items, scopes
├── audit/support/AuditVisibility.java          # + attendance entity types -> required VIEW grant
├── teacher/api/TeacherDirectory.java           # new: placements per range, teacher info, list placed in month
├── teacher/internal/TeacherDirectoryImpl.java
└── attendance/
    ├── api/                                    # AttendanceReadApi, RollupView, MarkView (named interface)
    ├── internal/                               # entities, repositories, services, RollupCalculator,
    │                                           #   BusinessCalendar (Asia/Kolkata), CsvExporter
    └── web/                                    # MarkController, GridController, SetupController,
                                                #   LockController, ExportController

backend/src/main/resources/db/migration/V14__create_attendance_tables.sql
backend/src/test/java/com/hls/attendance/       # calculator, marks, grid scope, setup, lock, export tests

frontend/src/features/attendance/
├── MyAttendancePage.tsx, AttendanceHistoryPage.tsx     # Teacher
├── AttendanceGridPage.tsx, TeacherAttendancePage.tsx    # Admin/Director and Manager (one shared AttendanceGrid)
├── AttendanceGrid.tsx, MarkDialog.tsx, TeacherMonthPanel.tsx
├── LockMonthDialog.tsx, ReopenDialog.tsx, LockControls.tsx
├── AttendanceSetupPage.tsx (StatusCodesPanel)           # MASTER DATA → Attendance Setup
├── HolidayCalendarPage.tsx                              # MASTER DATA → Holiday Calendar (all roles)
│     ├── HolidayCalendarView.tsx   # yearly / monthly calendar with highlighted holidays
│     ├── holidayPdf.ts             # jspdf: year or month → PDF (dynamic import)
│     └── CalendarPanel.tsx         # weekly off + holiday editing (Admin/Director)
├── dayStyle.ts                     # label + tinted background per kind of day, shared by grids and calendars
└── attendanceApi.ts, monthUtils.ts
frontend/src/features/common/RowActionButton.tsx    # icon + tooltip Edit/Delete/View (shared by all grids)
frontend/src/App.tsx                                # + guarded routes
```

**Structure Decision**: Web application (`backend/` + `frontend/`, unchanged layout). One new backend
module following the `api` / `internal` / `web` split, one migration, additive changes to
`identity`, `audit` and `teacher.api`, and one new frontend feature folder.

### Delivery slices (for tasks.md, each independently demoable)

1. **Foundation**: permissions, navigation, audit visibility, `TeacherDirectory`, module skeleton,
   migration with seeded status codes, business calendar helper.
2. **Setup and rollup engine (US5, US3 core)**: status codes, weekly off days and dates;
   `RollupCalculator` with its table-driven tests.
3. **Marking (US1, US2)**: self-marking with the 3-day window and supervisor protection, supervisor
   marking and clearing, mark history.
4. **Grids and history (US4, US7)**: Manager and Admin/Director grids with filters, Teacher
   calendar and history, rollup detail.
5. **Lock, reopen, export (US6, US8)**: lock validation, reopen/relock, frozen rollups, CSV export.
6. **Holiday Calendar and day colours (US9, FR-013 colours)**: `HOLIDAY_CALENDAR` permission and
   MASTER DATA navigation, `V15` status codes, the grid cell `category`, the shared day-colour
   helper, the yearly/monthly calendar view and the PDF download.

## Complexity Tracking

No violations. Table intentionally omitted.
