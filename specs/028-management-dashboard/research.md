# Research: Management Dashboard

Decisions taken while planning, with what was read in the code. Format: Decision, Rationale, Alternatives.

## 1. What the code offers today

- No `reporting` package. `AdminDashboard` and `DirectorDashboard` are placeholder boxes plus `PendingLeaveWidget`;
  `DashboardRouter` composes dashboards by testing role names (existing behaviour, kept for Manager, Teacher and System).
- Figures exist but sit in `internal` packages behind module controllers: `tasks.internal.TaskQueries.summary` (today,
  upcoming, overdue, awaiting, escalated, done; scoped per caller), `recruitment.marketing.internal.DashboardService`
  (visits, stages, won, lost, win rate, per-Zone and per-owner wins, demand, supply, scope-aware),
  `recruitment.internal.RecruitmentDashboardService` (funnel counts, `joiningRatio` string, ready to deploy),
  `payroll.run.internal.RunService` / `OwedService` (runs by month and state, owed people), `substitution` requirements
  (`OPEN, ASSIGNED, MANAGER_COVER, CANCELLED`), `TeacherStatus` (in training, active, on leave, exited).
- Public read interfaces already usable: `attendance.api.ManagerAttendanceReadApi` (month figures, `locked`),
  `AttendanceReadApi.teachersAwayOn` and `isLocked`, `WorkingCalendar`, `PendingManagerRequests` (leave and forgotten
  check-ins awaiting a decision), `TaskActivities`, `organization.api.ScopeQueries` / `ManagerQueries`,
  `teacher.api.TeacherScopeQueries`, `designation.api.BusinessDate`, `recruitment.api.SupplySource`.
- There is no shared approvals store (C3 arrives with spec 015), so pending decisions are asked of each module.
- Migrations on main stop at V53; this spec adds none.

## 2. Where the SPI lives and which way it points

**Decision**: `reporting.api` defines `TileSource`, `ApprovalSource` and `ManagerColumns`. Each owning module implements them in
its own `internal` package and exposes nothing else new. `reporting.internal` collects every implementation as a `List<...>`
bean, as the month lock collects `PendingManagerRequests`.
**Rationale**: spec FR-013 says a tile is "registered by the owning module's spec", which is this direction; `reporting` then
needs no dependency on tasks, recruitment, payroll, leave, attendance, teacher or substitution, so no cycle is possible and a
later spec adds a class in its own module with no change to `reporting`. Figures come from the services the screens already use,
so a tile and its list agree (SC-003). The module list in the constitution changes by one line. This still satisfies FR-003:
`reporting` never reads another module's tables, and the source classes read only their own module's services.
**Alternatives**: `reporting` reading each module's api (needs a new public read interface per module plus a dependency from
`reporting` on all of them; every later spec would edit `reporting`); a database view or SQL across tables (forbidden, FR-003).

## 3. Tile description and result

**Decision**: `TileDefinition(key, area, order, label, periodKind TODAY|MONTH|ALL_TIME, grant {module, action}, scope
ALL|ORG_WIDE_ONLY, zoneAware, link {path, query})`, read by `TileResult read(TileDefinition, TileContext)`. `TileContext` carries
user id, roles, business date, month, optional Zone filter (already checked against the caller's scope) and a per-request memo
(`memo(key, supplier)`). `TileResult` is `{key, state, values[{label, value, unit COUNT|RUPEES|PERCENT|TEXT, link?}], note?,
takenAt}`; `state` is `OK, NO_DATA, NOT_AVAILABLE, NOT_WORKING_DAY, ERROR`. A table tile carries `columns` and `rows`.
**Rationale**: one shape for numbers, pairs, distributions (stage counts) and states keeps the web renderer simple and makes
"never 0 for a missing source" a property of the type: a number exists only in an `OK` result. The memo lets the five task
tiles, or the five marketing tiles, share one query set.
**Alternatives**: each area returns its own DTO (the web would need a renderer per area and a new one per later spec); area
sources instead of tile sources (a failure would blank the whole area, against FR-013).

## 4. Permission, scope and "never sent"

**Decision**: `reporting` checks the grant named in the definition (through the existing `PermissionGuard`) before calling the
source, so a denied tile is neither listed in the manifest nor read. The source applies data scope itself and must not widen it.
Tiles marked `ORG_WIDE_ONLY` (Manager-by-Manager table, payroll tiles, inbox) are skipped when the caller holds only the Manager
role. A direct request for an area or tile not in the caller's manifest answers 404; an unknown or out-of-scope Zone answers 404.
For a user with several roles the grants and scope are already the union (identity), and tiles are keyed so each appears once.
**Rationale**: spec FR-015, FR-020, SC-004 and SC-007; same 404 rule as 026. Payroll amounts only come from `PayrollTiles`,
which is reached only after the Payroll View grant check.

## 5. Zone and period

**Decision**: the web sends `zoneId` and `period=YYYY-MM` to the area endpoint. `reporting` validates the Zone through
`ScopeQueries` and passes it in the context. Zone-aware sources filter; others ignore it, and the result carries
`organizationWide: true` so the card is labelled (FR-012). Default period is the current month; "today" tiles ignore the period.
Marketing uses its own `period` text, passed as `YYYY-MM`.
**Alternatives**: Zone as a role-style scope (rejected: it is a view filter, not an authorization change).

## 6. Manager-by-Manager table and the inbox

**Decision (table)**: `ManagerColumns` is a third SPI: column definitions (key, label, link template) plus
`Map<UUID managerUserId, Map<String columnKey, Long>> figures(ctx)`. Tasks contribute overdue, awaiting and escalated per
assignee; marketing visits missed and Schools won per owner; Manager attendance days with no check-in.
`ManagerComparisonService` merges by Manager id, takes names and Zones from `organization.api.ManagerQueries`, applies the Zone
filter and sorts by the chosen column.
**Decision (inbox)**: `ApprovalSource` returns `ApprovalItem(kind, itemId, requesterUserId, requesterName, subject, askedAt,
link)` for the caller; `InboxService` drops items whose requester is the caller (FR-014), sorts oldest first and totals per kind.
Kinds now: `MANAGER_LEAVE`, `FORGOTTEN_CHECK_IN` (through the existing pending-request logic of `leave.manager` and
`attendance.manager`), `FINAL_STAGE_PROSPECT` (marketing), `PAYROLL_RUN` (runs awaiting approval, not submitted by the caller),
`TASK_VERIFICATION` (awaiting verification). Page size 50; totals are always complete.
**Rationale**: the modules already hold these queries for their own lists; the inbox is a thin merge.

## 7. Failure isolation and the 3 second budget

**Decision**: `AreaReader` reads the area's tiles in parallel on a small bounded pool (virtual threads), each in its own read-only
transaction with a time limit of 2.5 s; a thrown exception or a timeout becomes that tile's `ERROR` result (the web offers a
retry) and is logged with the correlation id; the area still returns. The web fires one request per area, so a slow area never
delays another (FR-017, SC-002).
**Rationale**: a source failing must be a failed tile, never a 0 and never a failed page (FR-007, SC-005).
**Alternatives**: one request for everything (one slow module delays all); a cache (docs/caching.md rule 1 forbids it for live
counts that must equal the lists).

## 8. Business date, working day and "not a working day"

**Decision**: `TileContext.today()` comes from `BusinessDate`. Attendance sources ask `WorkingCalendar` and return
`NOT_WORKING_DAY` on a weekly off or holiday instead of counts (FR-010). Managers without a joining date, and Teachers with no
School, are not counted; the tile `note` says how many could not be counted (`ManagerMonth.NoJoiningDate`).

## 9. Unbuilt modules and "not available yet"

**Decision**: modules not built (015, 022, 024, 026, 027, 029, 030) register nothing, so their tiles are absent (the choice
FR-007 allows). Where a built tile depends on an optional provider, the source returns `NOT_AVAILABLE` naming the missing thing:
the Schools demand-against-supply tile uses `SupplySource`, which marketing already treats as optional.
**Rationale**: no placeholder card to remove later; a later spec only adds a class (SC-006). Spec 026 plans a
`PerformanceSummary` for 028; when 026 ships it adds a `TileSource` the same way and may build it from that summary.

## 10. Frontend

**Decision**: `features/dashboard` fetches `GET /dashboard` (manifest: areas, the tile definitions the caller may see, the
filters allowed), then one request per area. A card shows label, values, period caption, the "organization-wide" label when
needed, the state text for a non-`OK` result and a retry on `ERROR`. The page shows `takenAt` and a Refresh button; no timer.
Links are server-built paths so the screen has no routing table of its own. `AdminDashboard` and `DirectorDashboard` render
`ManagementDashboard`; `DashboardRouter` stays, and renders it once when either role is held so a tile never appears twice.
Amounts use the shared INR formatter and DD/MM/YYYY.
**Rationale**: FR-016, FR-017, FR-019. Phone width: cards wrap in a grid; the comparison table scrolls inside its own container
only; dark mode through the theme tokens.

## 11. Links must reproduce the figure

**Decision**: each tile's `link` names an existing screen and the filter that gives the same count. Where a screen cannot yet
take that filter (candidates: Substitutions "uncovered today", Teachers "away today", the Final Stage list), adding the filter
is part of that tile's task, and the per-tile parity test fails until it exists (SC-003). See
[contracts/tile-catalog.md](contracts/tile-catalog.md); each such case is marked "filter to check".

## 12. Testing the extension point

**Decision**: a `@TestConfiguration` registers a test `TileSource` (area, grant, link). Tests show it appears for a user with the
grant, is absent without it, leaves every other tile's result unchanged, and, when it throws, is the only `ERROR` (SC-006,
SC-005). A second test replaces each real source by "absent" and by "throws" and asserts no `OK` zero appears.

## 13. Definition of "uncovered" for substitutions

**Decision**: for today, `OPEN` is uncovered, `ASSIGNED` is assigned, `MANAGER_COVER` counts as covered and is shown on its own
line, `CANCELLED` is excluded. This matches the states the Substitutions list already filters on.
**Alternatives**: counting `MANAGER_COVER` as uncovered (it hides that a Manager is covering, and the class does happen).
