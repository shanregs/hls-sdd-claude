# Implementation Plan: Reports Framework, Attendance and Payroll Reports (014a)

**Branch**: `spec/014-reports` | **Date**: 2026-10-27 | **Spec**: [spec.md](spec.md)

## Summary

Add a report layer to the existing `reporting` module (created by spec 028): a `ReportSource` contract beside `TileSource`, a
registry, one controller with generic list and CSV endpoints, scope and month-state handling shared by all reports, and the
first three reports (Attendance, Payroll, My Report). The module stores nothing: no table, no migration. Each report reads
the owning module through a published interface; where 028 already reads a figure (attendance, payroll run), the report
calls the same query. Web screens go under `frontend/src/features/reports`.

## Technical Context

**Language/Version**: Java 21, Spring Boot (as in the repo); TypeScript, React (frontend)
**Primary Dependencies**: existing modules only (`attendance.api`, `leave.api`, `payroll.api`, `audit`, `reporting.api`)
**Storage**: none (read-only); audit rows through the existing audit module
**Testing**: JUnit and the repo's integration-test pattern for the server; Vitest and Testing Library for the web
**Target Platform**: web (the Android app has no reports in version 1)
**Constraints**: first page under 3 s at 500 Teachers and 50 Managers; export under 30 s; a failing source degrades one column
**Scale/Scope**: about 500 Teachers, tens of Managers, 12 months per cycle

## Constitution Check (constitution 3.0.0, 2026-10-26)

| Principle | Check |
| --- | --- |
| Role and permission impact section | Present in spec; keys below |
| Modular monolith, modules talk through `api` packages | Reports read `*.api` only; one new read interface in payroll |
| Audit every export | Yes (FR-010) |
| Data scope applied server-side | Yes, before counting (FR-006) |
| Read-only, no table | Yes |

No violations. Needs a re-check after 014b adds cross-module margin reads.

## Findings from the codebase (checked against main at `0dbd85c`)

- **`reporting.api`** has `TileSource`, `TileDefinition`, `TileContext` (userId, roles, today, month, zoneId, memo),
  `TileResult` (states OK, NO_DATA, NOT_AVAILABLE, NOT_WORKING_DAY, ERROR), `ManagerColumns`, `ApprovalSource`, `Area`.
  `reporting.internal` has `TileRegistry`, `ZoneResolver`, `AreaReader` (per-source time limit), `RequestTileContext`.
  Reuse: the state vocabulary, `ZoneResolver` for the Zone filter, the time-limit reader for fail-soft columns.
- **REPORTS menu and permission keys do not exist in code.** Spec 002 shows `REPORTS` only in a sketch of the grid. The
  `PermissionModule` enum has no report entry, `NavigationCatalog` has no REPORTS section. 014a adds the REPORTS section,
  the modules `REPORT_ATTENDANCE` and `REPORT_PAYROLL` (View, Export) and `MY_REPORT` (View) to `PermissionModule`, their
  eligibility in `PermissionEligibility` (no Teacher grant on the two report modules, no System), their defaults, and nav
  items. The sketch row `REPORTS: V V —` in spec 002 is superseded by per-report keys; recorded here as an amendment to 002.
- **Attendance**: `AttendanceReadApi.rollupOf`, `lockedTeachers`, `ManagerAttendanceReadApi.months` and `LossOfPayDays`
  give the figures; `WorkingCalendar` and `SchoolWorkingDays` give working days. Per-person calls: use the bulk forms
  (`months`, `lockedTeachers`); `rollupOf` is per Teacher, so the report needs a bulk method (task T012 adds
  `AttendanceReadApi.rollupsOf(Collection, YearMonth)`).
- **Payroll**: `payroll.api` has policy types only (`EarnedPay`, `PayRules`, `SalaryInEffect`). Run data (`PayRun`,
  `PayRunLine`, `RunTotals`, `RunViews`) is `payroll.run.internal`, and the 028 `PayrollTiles` sits inside it. 014a adds
  `payroll.api.PayrollFigures` (read-only: run state and per-person gross, LOP, reimbursements, net for a month; also the
  Teacher's own payslip rows). 014b needs the same interface for margin.
- **School receivables** `schoolbilling.api.SchoolReceivables` exists (billedPerTeacher, schoolMonths, balances); not used
  by 014a.
- **Migrations**: main's last migration is `V120__create_school_receivables.sql` (others in flight: V95, V100, V110).
  014a needs **none**. If a later task finds one is needed, take the next free number above V120 after re-checking main.
- **Constitution**: version 3.0.0 on main, no change needed.

## Design

### Contract (in `com.hls.reporting.api`)

- `ReportDefinition`: key, title, `PermissionModule`, filters offered (month, zone, school, teacher, manager), columns
  (key, label, unit), whether it has a month-state, export allowed.
- `ReportSource`: `definition()`, `read(ReportRequest, ReportContext) -> ReportPage` and `stream(...)` for export.
- `ReportContext`: the caller (id, roles), scope sets (Zone ids, School ids, Teacher ids, Manager ids, resolved once),
  `month`, and a memo.
- `ReportPage`: rows, totals, `monthState` (LOCKED, OPEN, DRAFT, APPROVED, PAID, NONE), and `notes`.
- `Cell`: reuses the 028 vocabulary: value, NO_DATA, NOT_AVAILABLE, NOT_MATCHED.

### Internal (`com.hls.reporting.internal.report`)

`ReportRegistry`, `ReportScopeResolver` (Admin and Director org-wide; Manager through `TeacherScopeQueries`-style scope;
Teacher own), `DefaultMonth` (last locked month, else current), `ReportCsvWriter` (header block, formula neutralisation,
streaming), `ReportExportAudit`, `ReportService`.

### Web (`com.hls.reporting.web`)

`GET /api/reports` (menu for the caller), `GET /api/reports/{key}` (page, filters in the query), `GET /api/reports/{key}/export`
(CSV, requires Export). Errors use the same advice as the dashboard.

### Reports

- `AttendanceReportSource` (`REPORT_ATTENDANCE`): Teacher rows with working days, present, absent, leave, LOP, %; roll-ups by School, Zone and
  organization; Manager rows with `ManagerMonth`.
- `PayrollReportSource` (`REPORT_PAYROLL`): through `PayrollFigures`.
- `MyReportSource` (`MY_REPORT`): Teacher self rows; a Manager reads their Teachers through the Attendance scope.

### Frontend (`frontend/src/features/reports`)

Report list from `/api/reports`, one generic report page (month picker, filters, table, totals, month-state banner, Export
button), reusing the table and permission-gating components used by the dashboard and attendance screens.

## Project Structure

```text
specs/014a-reports-framework/  spec.md plan.md tasks.md clarification-questions.md checklists/
backend/src/main/java/com/hls/reporting/api/            ReportDefinition, ReportSource, ReportContext, ReportPage, Cell
backend/src/main/java/com/hls/reporting/internal/report/ registry, scope, default month, CSV, audit, service, sources
backend/src/main/java/com/hls/reporting/web/             ReportController
backend/src/main/java/com/hls/payroll/api/PayrollFigures.java     (new read interface; implementation in payroll.run.internal)
backend/src/main/java/com/hls/attendance/api/AttendanceReadApi    (+ bulk rollups)
backend/src/main/java/com/hls/identity/...                       (modules, eligibility, nav)
frontend/src/features/reports/
```

## Risks and open points for the user

1. A new `PayrollFigures` interface is an amendment to 013b (read-only, additive). Needs approval at plan review.
2. A bulk rollup method on `AttendanceReadApi` is an additive amendment to 008.
3. Amends spec 002: per-report keys replace the single REPORTS row in its sketch.
4. For 014b, 022 and 027 amendments are listed in `docs/spec-inputs/014b-school-reports-margin.md`.

## Complexity Tracking

None.
