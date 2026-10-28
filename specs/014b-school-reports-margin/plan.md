# Implementation Plan: School, Collections and Margin Reports (014b)

**Branch**: `spec/014b-school-reports` | **Date**: 2026-10-28 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/014b-school-reports-margin/spec.md`

## Summary

Three `ReportSource` beans (Margin, Collections, School) on the 014a framework, in a new module `schoolreports` that reads the owning modules through their `api` packages only, as 014c's `peoplereports` does. No table, no migration, no new endpoint: the 014a controller serves them and the generic web page renders them.

The only new arithmetic is the pay split. It uses the working days each due line already bills at each School (`due_line.worked_days`), so the split matches the due lines by construction and **no 027 amendment is needed** (research D1; the Margin report has no Teacher filter, D10). Everything else is a read-only, bulk amendment to a merged module (022, 017a, 026, 023, 008, 005), each with a test that its figure equals the owning screen.

## Technical Context

**Language/Version**: Java 21 / Spring Boot (backend), TypeScript React + MUI (web, generic report page unchanged)

**Primary Dependencies**: existing only (`reporting.api`, and the `api` packages of `schoolbilling`, `payroll`, `attendance`, `teacher`, `school`, `substitution`, `performance`, `recruitment`)

**Storage**: none (computed at read time); **no migration**

**Testing**: JUnit + Testcontainers (one Maven class at a time, JDK 25), Vitest for the web page (already covers the generic page)

**Target Platform**: web SPA; the Android app has no report screens

**Project Type**: web + API

**Performance Goals**: first page under 3 s, export under 30 s at 500 Teachers and 50 Managers; statements do not grow with volume

**Constraints**: read-only; a failing source shows "not available" for its own figures only (each optional read runs in its own read-only transaction, as fixed in 014c); figures equal the owning screens

**Scale/Scope**: about 5 to 50 Schools per Zone, 500 Teachers; three reports, seven sections

## Constitution Check

- Principle I (auditable): every export writes one audit row through the 014a `ReportExportAudit`; the reports change nothing. PASS.
- Principle II/III (roles and scope): three new permission keys `REPORT_MARGIN`, `REPORT_COLLECTIONS`, `REPORT_SCHOOL` (View, Export) for Admin, Director and, for their own Schools, Manager; Teacher and System ineligible. Scope comes from the 014a `ReportScope`; a Zone Manager's Margin never touches `REPORT_PAYROLL`. A PATCH amendment adds three rows to the Default role access matrix. PASS.
- Principle V (formula-driven money): fee is the 022 due line, pay is the 013b payslip net, margin and split are computed from them in one pure class with unit tests; nothing is re-entered. PASS.
- Principle VII (modular monolith): new module `schoolreports` depends only on `*.api` packages and `reporting.api`; nothing depends on it. Each amendment adds an interface to the owner's `api` package, implemented in its `internal`. `ApplicationModulesTest` and the reporting module rules stay green. PASS.
- Principle IX (tests): scope per role, equality with the owning screens, export, audit, source-down and performance tests, as in 014c. PASS.
- Result: PASS, no violations to justify. Re-checked after design (data-model, contracts): unchanged.

## Project Structure

### Documentation (this feature)

```text
specs/014b-school-reports-margin/
  spec.md  plan.md  research.md  data-model.md  quickstart.md  tasks.md (next step)
  contracts/reports-and-read-amendments.md
  checklists/requirements.md
```

### Source Code (repository root)

```text
backend/src/main/java/com/hls/
  schoolreports/internal/   MarginReportSource, CollectionsReportSource, SchoolReportSource, PaySplit (pure)
  identity/permissions/     PermissionModule (+3 keys), PermissionEligibility, defaults
  identity/accessmodel/     NavigationCatalog (+3 REPORTS items at order 810)
  audit/support/            AuditVisibility (+3 REPORT_EXPORT_* entries)
  schoolbilling/api/        SchoolReceivables (+billedPerTeacherAndSchool, +ageing), SchoolContracts (+contractsOfAll)
  schoolbilling/internal/   implementations; ageing reuses LedgerQueryService.bucketsOf so it equals the Ageing screen
  substitution/api/         SubstitutionCover (new read port)
  performance/api/          PerformanceSummary (+bySchool)
  recruitment/api/          SchoolStages (new read port: prospect-to-active stage per School)
  attendance/api/           SchoolAttendance (new read port; AttendanceReportSource uses the same grouping)
  teacher/api/              TeacherDirectory (+placedCountBySchool)

backend/src/test/java/com/hls/schoolreports/   PaySplitTest, MarginReportTest, CollectionsReportTest, SchoolReportTest,
                                                SchoolReportsExportTest, SchoolReportsSourceDownTest, SchoolReportsPerformanceTest
frontend/src/features/reports/                  no new screen; one test with the three report shapes
docs/spec-roadmap.md                            014 row
```

**Structure Decision**: One new module for the three reports because Margin and School each join several modules and none of those modules may depend on the others. The generic web page is reused unchanged.

## Complexity Tracking

No constitution violations. One decision worth noting (research D1): the pay split uses the billed working days of the due lines instead of a new placement-days interface from 027, which removes a cross-module change and makes fee and pay agree by construction.
