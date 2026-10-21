# Data Model: Management Dashboard

The Dashboard owns **no table and no migration** (spec FR-021, Key Entities). Everything below is an in-memory value built per
request and never stored. Names follow the Java types in `com.hls.reporting.api`.

## TileDefinition

| Field | Type | Notes |
| --- | --- | --- |
| key | string | stable, dotted, for example `managers.tasks.overdue` |
| area | Area | MANAGERS, SCHOOLS, RECRUITMENT, TEACHERS, ATTENDANCE_LEAVE, PAYROLL |
| order | int | order inside the area; fixed in version 1 |
| label | string | plain words saying what is counted |
| periodKind | enum | TODAY, MONTH, ALL_TIME |
| grant | {module, action} | the View grant of the owning module; checked by `reporting` before the source is called |
| scope | enum | ALL (source scopes itself) or ORG_WIDE_ONLY (never offered to a Manager-only caller) |
| zoneAware | boolean | false means labelled organization-wide when a Zone is chosen |
| link | {path, query} | the list screen and filter that reproduces the figure |

## TileContext

`userId`, `roles`, `today` (business date, Asia/Kolkata), `month` (default current), `zoneId?` (validated against the caller's
scope), `memo(key, supplier)` for computations shared by tiles of one request. Discarded when the request ends.

## TileResult

| Field | Type | Notes |
| --- | --- | --- |
| key | string | the definition's key |
| state | enum | OK, NO_DATA ("no data yet"), NOT_AVAILABLE ("not available yet"), NOT_WORKING_DAY, ERROR |
| values | list | `{label, value (number or text), unit COUNT/RUPEES/PERCENT/TEXT, link?}`; empty unless state is OK |
| organizationWide | boolean | true when a Zone is chosen and the tile is not zone-aware |
| note | string? | for example "2 Managers have no joining date and were not counted"; the missing source's name for NOT_AVAILABLE |
| takenAt | instant | when the figure was read |
| table | {columns, rows}? | only for the comparison tile |

Rule: a number appears only in an `OK` result; a zero denominator yields `NO_DATA`; a source that is absent yields no tile or
`NOT_AVAILABLE`; a source that throws or times out yields `ERROR`. A figure is never defaulted to 0.

## ApprovalItem

`kind` (MANAGER_LEAVE, FORGOTTEN_CHECK_IN, FINAL_STAGE_PROSPECT, PAYROLL_RUN, TASK_VERIFICATION), `itemId`, `requesterUserId`
(used only to drop the caller's own requests, not sent), `requesterName`, `subject`, `askedAt`, `link`. The response adds
`ageDays` (business dates) and totals per kind.

## ManagerRow (comparison table)

`managerUserId`, `name`, `zones`, plus one cell per column: `overdueTasks`, `awaitingVerification`, `escalatedTasks`,
`visitsMissed`, `schoolsWon`, `daysNoCheckIn`. Each cell has a link to the list filtered to that Manager. Columns come from
`ManagerColumns` implementations, so a later spec adds a column without editing the table.

## Reused figures (read, not copied)

| Source module | Existing figure used |
| --- | --- |
| tasks | `TaskQueries.Summary` (today, upcoming, overdue, awaiting, escalated), per assignee for the table |
| recruitment.marketing | `DashboardService.DashboardDto` (visits, stages, won, lost, winRate, demand, supply, shortfall), Final Stage count |
| recruitment | `RecruitmentDashboardService.Counts` totals (drives held, interviewed, offered, accepted, joiners, joiningRatio, ready to deploy) |
| attendance.manager | missed check-ins (`MissedAlert`), `ManagerAttendanceReadApi`, month lock state, `PendingManagerRequests` |
| attendance | `AttendanceReadApi.teachersAwayOn`, `isLocked`, `WorkingCalendar` |
| leave | pending Teacher leave count (same query as `PendingLeaveWidget`), Managers on leave today and pending Manager leave |
| teacher | count by `TeacherStatus` within `TeacherScopeQueries` |
| substitution | requirements for today by `RequirementStatus` |
| payroll.run | run for the month: status, total, paid, `OwedService.owed(month)` |
