# Contracts: new reports and read-only amendments (014c)

All additive. Callers apply their own permission and scope (the owning module does not). Bulk forms use a fixed number of queries.

## New reports (`ReportSource` beans)

| Key | Module key | Filters | Sections | Month state |
| --- | --- | --- | --- | --- |
| `recruitment` | `REPORT_RECRUITMENT` | SEASON, ZONE | `colleges`, `totals` | none |
| `teacher` | `REPORT_TEACHER` | ZONE, SCHOOL, TEACHER, MANAGER | `byStatus`, `teachers` | attendance lock |
| `manager` | `REPORT_MANAGER` | ZONE, MANAGER | `managers` | Manager attendance lock |
| `expenses` | `REPORT_EXPENSES` | ZONE | `spend`, `budget`, `pending`, `costs` | none (pending labelled as of today) |

Endpoints are the 014a ones: `GET /api/reports`, `/api/reports/{key}`, `/api/reports/{key}/export`.

## Amendments

**A0 (014a)** `reporting.api.ReportFilter.SEASON`; `ReportRequest` gains `String season` (existing 5-argument constructor kept, season null).

**A1 (016)** `recruitment.api.RecruitmentFigures`
`RecruitmentFigures.Result bySeason(String seasonOrNull, UUID zoneIdOrNull)` where `Result(List<CollegeRow> colleges, Counts totals)`; `Counts` has drivesHeld, candidates, interviewed, selected, offered, accepted, joined, joiningRatio (nullable). Same arithmetic as `RecruitmentDashboardService.dashboard`; the dashboard is changed to call it.

**A2 (023)** `recruitment.api.DemandFigures`
`long vacantPositionsOfWonSchools()` using the rule in the marketing `DashboardService` (vacant positions of won Schools with a live contract); the dashboard calls it.

**A3 (005)** `TeacherDirectory`
`Map<UUID, Lifecycle> lifecycle(Collection<UUID> teacherIds)`, `record Lifecycle(String status, LocalDate statusEffectiveOn, LocalDate joinedOn /* nullable */)`.

**A4 (026)** `PerformanceSummary`
`Map<UUID, Counts> byTeacher(LocalDate from, LocalDate to)`, keyed by Teacher id, same `Counts` and rules as `byManager`.

**A5 (027)** `TeacherMovementSource`
`Map<UUID, Movements> movementsBetween(LocalDate from, LocalDate to)`, `record Movements(int transfers, int replacements)` for Approved or Applied requests whose effective date falls in the range; Teachers with none absent.

**A6 (025)** `TaskManagerColumns`: add `tasks.assigned` (tasks assigned to the Manager with a creation date in the context month) and `tasks.completed` (completed in the month), one bulk query.

**A7 (035)** `activitycalendar.internal.ActivityManagerColumns implements ManagerColumns`: `activity.planned`, `activity.done` (done plus done late), `activity.missed`, `activity.rescheduled` per Manager for `ctx.month()`, from `CalendarSummary.byManager`.

**A8 (023)** `MarketingManagerColumns`: add `marketing.visitsDone` and `marketing.wonInMonth`; `marketing.visitsMissed` and `marketing.won` unchanged.

**A9 (015)** `ExpenseQueries`
`Map<UUID, ClaimedTotal> claimedBy(LocalDate from, LocalDate to, Collection<UUID> claimantUserIds)`, `record ClaimedTotal(long claims, BigDecimal submitted, BigDecimal approved)`.

**A10 (015)** `ExpenseQueries`
`List<BudgetLine> budgetVsActual(YearMonth month)`, `record BudgetLine(UUID zoneId /* null = Head office */, String areaName, BigDecimal budget /* null = none set */, BigDecimal actual, BigDecimal pending, BigDecimal remaining, boolean over)`, delegating to `BudgetQueries.overview`.
