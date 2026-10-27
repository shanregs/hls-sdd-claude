# Implementation Plan: Recruitment, Teacher, Manager and Expenses Reports (014c)

**Branch**: `spec/014c-reports-people` | **Date**: 2026-10-27 | **Spec**: [spec.md](spec.md)

## Summary

Four `ReportSource` beans on the 014a framework, each published by the module that owns the figures' meaning or by
`reporting`, plus ten small additive read-only amendments to merged modules (005, 015, 016, 023, 025, 026, 027, 035). The
Manager report is built from the `ManagerColumns` beans of the dashboard so common columns equal the comparison table by
construction. No table, no migration. Web screens reuse the generic 014a report page.

## Technical Context

**Language/Version**: Java 21, Spring Boot, Spring Modulith (as in the repo); TypeScript, React
**Dependencies**: `reporting.api` (014a contract) and the owning modules' `*.api` packages only
**Storage**: none; audit rows through the 014a `ReportExportAudit`
**Testing**: JUnit with the repo's integration-test pattern; Vitest and Testing Library
**Constraints**: first page under 3 s at 500 Teachers and 50 Managers; export under 30 s; a failing section degrades only itself
**Scale**: about 500 Teachers, tens of Managers, a few dozen colleges

## Constitution Check (constitution 3.0.0 on main)

| Principle | Check |
| --- | --- |
| Role and permission impact section | Present in spec; four new keys |
| Modules talk through `api` packages | Yes; every internal figure is exposed first through an amendment (A1-A10) |
| Audit every export | Via 014a `ReportExportAudit` |
| Scope server-side, before counting | `ReportScope` plus per-report rules below |
| Read-only, no table | Yes |

No violations, no constitution change. Re-check after 014b lands.

## Findings (main `29e3f63`; 014a read from worktree `hls-014a-impl`, branch `impl/014a-reports-framework`, commits d49f475, 83f0454, e7bb4db)

- **014a contract as built**: `ReportSource` has `definition()`, `sections()` (each a `SectionSource` with `key`, `title`, `columns`, `read(ReportContext)`), `monthState`, `notes`, `lastFinalMonth`. `ReportDefinition(key, title, PermissionModule, Set<ReportFilter>, hasMonthState, exportAllowed)`. `ReportContext` offers `userId`, `roles`, `today`, `scope` (`ReportScope(orgWide, zoneIds, schoolIds)`), `request`, `month()`, `memo`. `ReportRequest(month, zoneId, schoolId, teacherId, managerId)`. `ReportFilter` is ZONE, SCHOOL, TEACHER, MANAGER. `Cell(State, value)`, `ReportPage.Column/Row/Section`. Internals: `DefaultMonth`, `ReportCsvWriter`, `ReportExportAudit`, `ReportRegistry`, `ReportScopeResolver`, `ReportService`. Permission keys so far `REPORT_ATTENDANCE`, `REPORT_PAYROLL`, `MY_REPORT` (the plan names win over code where they differ; code names are used here).
- **Gap in the 014a contract (A0)**: no season. Recruitment needs a season parameter (Q1). Smallest additive change: `ReportFilter.SEASON` and a `season` (String) component on `ReportRequest`, with the existing constructor kept as an overload. To be made by 014c after 014a merges (preferred), or by 014a before merge if the owner wishes. It touches no other report.
- **Section model**: one source per report with several sections fits: Recruitment (colleges, totals), Teacher (status counts, rows), Manager (one section), Expenses (spend, budget, pending, costs).
- **035 module dependency direction**: `activitycalendar` imports `identity`, `designation`, `organization`, `recruitment`, `school`, `tasks`, `training`; `reporting` imports nothing from `activitycalendar`. A `ManagerColumns` bean in `activitycalendar` adds `activitycalendar -> reporting.api`, exactly as `tasks` and `recruitment.marketing` already do. No cycle. Its `api` package-info says nothing else reads it; the new bean is discovered by Spring, so no `activitycalendar.api` type is needed and that note stays true.
- **Migrations**: main last `V120`; `V95`, `V100`, `V110`, `V120` used; `V130` (030, `impl/030-manager-incentives`) and `V140` (039, `spec/039-user-themes`) planned. 014c adds none.

## Design

### Reports (`com.hls.reporting.internal.report`, one class each, beans)

- `RecruitmentReportSource` (`REPORT_RECRUITMENT`): filters SEASON (A0) and optionally ZONE; sections `colleges` and `totals` from `RecruitmentFigures` (A1), induction completed from `InductionProgress`, ready to deploy from `SupplySource`, demand from `DemandFigures` (A2). Zone Manager sees what 016 shows them (no extra scope). No month state.
- `TeacherReportSource` (`REPORT_TEACHER`): filters ZONE, SCHOOL, TEACHER, MANAGER; sections `byStatus` and `teachers`. Rows from `TeacherDirectory` (`teacherInfo`, `lifecycle` A3, `placementsOverlapping`), `PerformanceSummary.byTeacher` (A4), `TeacherMovementSource.movementsBetween` (A5), `AttendanceReadApi.rollupsOf` (014a) for training days. Scope: `TeacherScopeQueries.teacherScope`. List rule per Q4: placed or in training in the month, plus exited in the month; Zone and School from the placement on the last day (exit date for exited). Month state from the attendance lock like 014a.
- `ManagerReportSource` (`REPORT_MANAGER`): filters ZONE, MANAGER; one section. Columns from every `ManagerColumns` bean (tasks 025, activity 035, marketing 023: found by injection, so a missing bean is "not available" for its columns), plus attendance % (`ManagerAttendanceReadApi.months`), training days (`ManagerTrainingDays.attendedDaysOf`), expense claimed (`ExpenseQueries.claimedBy`, A9). Admin and Director: all Managers; Zone Manager: own row only, own expense shown (Q3). Month state from the Manager attendance lock.
- `ExpensesReportSource` (`REPORT_EXPENSES`): filters ZONE; sections `spend` (`approvedSpend`), `budget` (`budgetVsActual`, A10), `pending` (`pending`, labelled as of today), `costs` (`costPerSchool`, `costPerTeacher`). Admin and Director only.

Order of build follows the stories: Manager, Expenses, Teacher, Recruitment.

### Permissions

Add `REPORT_RECRUITMENT`, `REPORT_TEACHER`, `REPORT_MANAGER`, `REPORT_EXPENSES` (View, Export) to `PermissionModule`; eligibility: no Teacher, no System, `REPORT_EXPENSES` not Manager; defaults Admin and Director all four, Manager three (not Expenses); REPORTS nav items.

### Frontend (`frontend/src/features/reports`)

Four column definitions on the generic report page; a season picker for Recruitment (A0); no other new components.

## Read-only amendments to merged modules (decided by this plan; each has a task and a test)

| ID | Spec | Change |
| --- | --- | --- |
| A0 | 014a | `ReportFilter.SEASON` and `ReportRequest.season` |
| A1 | 016 | `recruitment.api.RecruitmentFigures` over `RecruitmentDashboardService` |
| A2 | 023 | `recruitment.api.DemandFigures` (same demand rule as the marketing dashboard) |
| A3 | 005 | `TeacherDirectory.lifecycle(Collection<UUID>)` |
| A4 | 026 | `PerformanceSummary.byTeacher(from, to)` |
| A5 | 027 | `TeacherMovementSource.movementsBetween(from, to)` |
| A6 | 025 | `tasks.assigned`, `tasks.completed` on `TaskManagerColumns` |
| A7 | 035 | `ManagerColumns` bean (`activity.planned`, `.done`, `.missed`, `.rescheduled`) |
| A8 | 023 | `marketing.visitsDone`, `marketing.wonInMonth` on `MarketingManagerColumns` |
| A9 | 015 | `ExpenseQueries.claimedBy(from, to, Collection<UUID>)` |
| A10 | 015 | `ExpenseQueries.budgetVsActual(YearMonth)` |

Details and signatures: [contracts/read-amendments.md](contracts/read-amendments.md). Existing behaviour, endpoints and
dashboard columns do not change; new dashboard columns appear on the comparison table as a side effect of A6-A8 and are
covered by tests.

## Project Structure

```text
specs/014c-recruitment-teacher-manager-expenses/  spec.md plan.md research.md tasks.md quickstart.md source-check.md
                                                   clarification-questions.md contracts/ checklists/
backend/.../reporting/internal/report/   Recruitment/Teacher/Manager/ExpensesReportSource
backend/.../identity/permissions/        four keys, eligibility, defaults, nav
backend/.../{teacher,expenses,recruitment,recruitment/marketing,tasks,performance,transfer,activitycalendar}  amendments
frontend/src/features/reports/           column definitions, season picker
```

## Complexity Tracking

None. No data model: see [research.md](research.md); there is no `data-model.md` because nothing is stored.
