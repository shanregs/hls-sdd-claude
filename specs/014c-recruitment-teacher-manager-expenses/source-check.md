# Source check: figures against main's public interfaces (014c)

2026-10-27, checked against main at `29e3f63`. "Public" means an `*.api` package or a published `ManagerColumns` bean. All
amendments are additive and read-only (spec FR-015); none changes behaviour of a merged module. 014a names used:
`ReportSource`, `ReportContext`, `ReportPage`, `Cell`, `DefaultMonth`, `ReportCsvWriter`, `ReportExportAudit`,
`PermissionModule` keys (re-check against `impl/014a-reports-framework` at plan time).

## Recruitment report

| Figure | Source today | Status |
| --- | --- | --- |
| Drives held, candidates (assessed), interviewed, selected, offers issued, accepted, joined, joining ratio, per college and season | `RecruitmentDashboardService.dashboard(...)` in `recruitment.internal` (016) | Not public. **016 amendment A1**: a read-only `recruitment.api.RecruitmentFigures` (per college and season, plus totals) delegating to the same service, so the report equals the dashboard. "Joiners" = the dashboard's `placed`/joined count; confirm at plan |
| Induction completed | `recruitment.api.InductionProgress.completedTeacherIds()` | Public, no change |
| Ready to deploy | `recruitment.api.SupplySource.readyToDeployCount()` | Public, no change |
| Demand vs supply | demand computed inside `recruitment.marketing.internal.DashboardService` (023); supply via `SupplySource` | Not public. **023 amendment A2**: `recruitment.api.DemandFigures` (vacant positions of won Schools with a live contract), same method as the marketing dashboard |

## Teacher report

| Figure | Source today | Status |
| --- | --- | --- |
| Name, status, user, placement spans | `teacher.api.TeacherDirectory.teacherInfo`, `placementsOverlapping`; `TeacherPlacementSource.currentOf` | Public |
| Joined or exited in the month, tenure | status effective date and joining date on the Teacher record (`TeacherView.statusEffectiveOn`) | Only in the web view. **005 amendment A3**: `TeacherDirectory.lifecycle(Collection<UUID>)` returning status, status-effective date and joining date (or "no joining date"); confirm the joining-date field at plan |
| Scope per Zone Manager | `teacher.api.TeacherScopeQueries.teacherScope` | Public |
| Observations done, open issues | `performance.api.PerformanceSummary` has `byManager`, `byZone` only | **026 amendment A4**: `PerformanceSummary.byTeacher(from, to)` returning `Counts` per Teacher id (same counting rules) |
| Transfers and replacements in the month | `teacher.api.TeacherMovementSource` has only id sets (transferred, replaced ever) | **027 amendment A5**: `TeacherMovementSource.movementsBetween(from, to)` returning counts of transfers and replacements per Teacher (Approved or Applied) |
| Training attendance | `AttendanceReadApi.rollupOf` (`trainingAvailable`, `trainingAttended`) | Public; 014a adds bulk `rollupsOf`. No 024 change under the default (Q5) |
| Teachers by status | `TeacherDirectory.allTeachers()` | Public |

## Manager productivity report

| Figure | Source today | Status |
| --- | --- | --- |
| Overdue, escalated (and awaiting) tasks | `TaskManagerColumns` (`tasks.overdue`, `tasks.escalated`) | Public bean |
| Tasks assigned, completed in the month | `TaskQueries.Summary` (internal) counts overdue/awaiting/escalated/done as of now, not by month | **025 amendment A6**: add `tasks.assigned` and `tasks.completed` (by month) to `TaskManagerColumns`; dashboard also gains the columns, no existing column changes |
| Planned, completed, missed, rescheduled activity | `activitycalendar.internal.CalendarSummary.byManager`; the 035 `api` package is intentionally empty and no `ManagerColumns` bean | **035 amendment A7**: a `ManagerColumns` bean in `activitycalendar` (`activity.planned`, `.done`, `.missed`, `.rescheduled`) using the existing summary; module dependency direction to confirm at plan |
| Visits missed, Schools won | `MarketingManagerColumns` (`marketing.visitsMissed`, `marketing.won`) | Public bean; `marketing.won` is all-time. **023 amendment A8**: add `marketing.visitsDone` and `marketing.wonInMonth` (via `WonSchools` plus owner) |
| Training days attended | `attendance.api.ManagerTrainingDays.attendedDaysOf` | Public, no change |
| Attendance % | `ManagerAttendanceReadApi.months` (`ManagerMonth.Found`) | Public, same as 014a Attendance |
| Expense claimed | `ExpenseQueries` has `owedTo`, `approvedSpend`, `pending`, cost measures; no per-claimant total | **015 amendment A9**: `ExpenseQueries.claimedBy(from, to, Collection<UUID>)` (submitted and approved totals per claimant) |

## Expenses report

| Figure | Source today | Status |
| --- | --- | --- |
| Spend by area, category, month | `ExpenseQueries.approvedSpend` | Public |
| Pending claims and age | `ExpenseQueries.pending` | Public (as of today) |
| Cost per School, per Teacher | `ExpenseQueries.costPerSchool`, `costPerTeacher` | Public |
| Budget vs actual and remaining | `BudgetQueries.overview(YearMonth)` in `expenses.internal` | Not public. **015 amendment A10**: `ExpenseQueries.budgetVsActual(YearMonth)` returning area, budget, actual, pending, remaining (delegating to `BudgetQueries`) |

## Summary of amendments by spec

| Spec | Amendment | Kind |
| --- | --- | --- |
| 005 | A3 `TeacherDirectory.lifecycle` | new read method |
| 015 | A9 `claimedBy`, A10 `budgetVsActual` | two read methods on `ExpenseQueries` |
| 016 | A1 `RecruitmentFigures` | new read interface over the dashboard service |
| 023 | A2 `DemandFigures`, A8 two Manager columns | new read interface, two columns |
| 025 | A6 assigned and completed columns | two columns on an existing bean |
| 026 | A4 `PerformanceSummary.byTeacher` | new read method |
| 027 | A5 `movementsBetween` | new read method |
| 035 | A7 `ManagerColumns` bean | new bean, no new data |
| 024, 032, 008, 028 | none (014a already adds `rollupsOf`) | none |

## Migrations

Main: V95 (029), V100 (031), V110 (024), V120 (022). Planned and present on branches: V130 `create_incentives` (030,
`impl/030-manager-incentives`), V140 `create_user_preferences` (039, `spec/039-user-themes`). 014c needs none (FR-014); if the
plan finds one is required it takes the next free number above V140 after re-checking main and open branches. Do not reuse
V95, V100, V110, V120, V130 or V140.
