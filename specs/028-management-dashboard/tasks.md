---

description: "Task list for feature implementation"
---

# Tasks: Management Dashboard

**Input**: Design documents from `/specs/028-management-dashboard/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/dashboard-api.md, contracts/module-interfaces.md,
contracts/tile-catalog.md, quickstart.md. Specs 005, 008, 009, 013b, 016, 023, 025, 032 are on `main` (the sources this
spec reads). No migration, no new permission module, no new navigation item.

**Defaults taken for the plan's open items (no owner answer was given; change before implementing if wrong)**:

1. **Manager's own Dashboard (US9, FR-016)**: keep the existing spec-005 Zone, School and Teacher count cards in
   `ManagerDashboard.tsx` unchanged and render the Manager's scoped tiles from the manifest *below* them. (Alternative of replacing the cards with tiles is not planned.)
2. **Admin and Director see the same Dashboard**: differences come only from the permission matrix; no Director-specific layout.
3. **Constitution patch number** (PATCH to Principle VII naming `reporting`): left as `NEXT` and assigned at merge, because 026 and 015 also claim the next patch.

**Tests**: included as first-class tasks. The spec's success criteria require them: a parity test per tile (SC-003), per
endpoint and role (SC-004), missing and failing sources (SC-005), a test tile (SC-006), Payroll permission (SC-007). Run one Maven
test class at a time (`mvn -o test -Dtest=...`); Docker must be running for integration tests.

**Organization**: grouped by user story (spec.md US1-US9). Backend `reporting` core and the front-end shell come first
(Foundational and US1); each later story adds the source classes for its area plus their tests. US6 (inbox), US7 (extensibility
proof), US8 (Zone and period), US9 (Manager scoping) follow.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: US1-US9; absent for Setup/Foundational/Polish

## Path Conventions

Backend `backend/src/main/java/com/hls/{reporting/{api,internal,web},<module>/internal}/...`, tests under
`backend/src/test/java/com/hls/{reporting,<module>}/...`; frontend `frontend/src/features/dashboard/...` and
`frontend/src/dashboards/...`; Postman in `postman/`.

---

## Phase 1: Setup

- [x] T001 Verify the prerequisites and STOP and report if any is missing (do not stub): in `backend/src/main/java/com/hls/` confirm `identity` permission checking (`PermissionGuard`), `organization.api.ScopeQueries` and `ManagerQueries`, `teacher.api.TeacherScopeQueries`, `designation.api.BusinessDate`, `attendance.api.AttendanceReadApi.teachersAwayOn`, `ManagerAttendanceReadApi`, `PendingManagerRequests`, `tasks.internal.TaskQueries.summary`, `recruitment.marketing.internal.DashboardService`, `recruitment.internal.RecruitmentDashboardService`, `payroll.run.internal.RunService` and `OwedService`; record the exact names found (and any that differ) in `specs/028-management-dashboard/research.md` section 1
- [x] T002 Create the package skeletons `backend/src/main/java/com/hls/reporting/{api,internal,web}/` each with `package-info.java` (`api` annotated as a Modulith named interface depending only on `identity`), and `backend/src/test/java/com/hls/reporting/ReportingModuleRulesTest.java` (ArchUnit: `reporting.api` uses only `identity` types; `reporting.internal` uses only `reporting.api`, `identity`, `organization.api`, `designation.api`; no module outside `reporting` uses `reporting.internal` or `reporting.web`; `reporting` reads no other module's `internal` or tables); extend `backend/src/test/java/com/hls/ApplicationModulesTest.java` expectations if the module list is asserted there

---

## Phase 2: Foundational (blocks every user story)

**Purpose**: the SPI, the registry that applies grants, the area reader that isolates failures, and the three endpoints.

- [x] T003 [P] Write failing unit tests for the result type in `backend/src/test/java/com/hls/reporting/TileResultTest.java`: a number exists only in an `OK` result; `noData`, `notAvailable`, `notWorkingDay` carry no values; `values` is empty unless `state` is `OK`; states are exactly `OK, NO_DATA, NOT_AVAILABLE, NOT_WORKING_DAY, ERROR`
- [x] T004 [P] Create the SPI in `backend/src/main/java/com/hls/reporting/api/`: `TileSource` (`tiles()`, `read(TileDefinition, TileContext)`), `TileDefinition(key, area, order, label, periodKind, permissionModule, action, scope, zoneAware, link)`, `Area` (`MANAGERS, SCHOOLS, RECRUITMENT, TEACHERS, ATTENDANCE_LEAVE, PAYROLL`), `ScopeKind` (`ALL, ORG_WIDE_ONLY`), `PeriodKind` (`TODAY, MONTH, ALL_TIME`), `Link(path, query)`, `TileContext` (`userId`, `roles`, `today`, `month`, `zoneId`, `memo(key, supplier)`), `TileResult` with static factories `ok`, `noData`, `notAvailable`, `notWorkingDay` and `Value(label, value, unit COUNT/RUPEES/PERCENT/TEXT, link)`, `Table(columns, rows)`, `ApprovalSource`, `ApprovalItem(kind, itemId, requesterUserId, requesterName, subject, askedOn, link)`, `ManagerColumns` with `Column(key, label, linkTemplate)` and the bulk `figures(ctx, managerUserIds)`, exactly as in `contracts/module-interfaces.md`
- [x] T005 Implement `backend/src/main/java/com/hls/reporting/internal/TileRegistry.java`: collects `List<TileSource>`, rejects duplicate keys at start-up, orders tiles by area then `order`, and builds a caller's manifest by checking each tile's grant through `PermissionGuard` before the source is called; tiles with `ORG_WIDE_ONLY` are skipped when the caller holds only the Manager role; for a caller with several roles each key appears once (depends on T004)
- [x] T006 Implement `backend/src/main/java/com/hls/reporting/internal/AreaReader.java`: reads each tile of an area in its own read-only transaction with a 2.5 second limit; a thrown exception or timeout becomes an `ERROR` result ("Could not be read") for that tile only; sets `organizationWide` when a Zone was chosen and the tile is not zone-aware; supplies the per-request `memo` and `takenAt`; never defaults a figure to 0 (depends on T004)
- [x] T007 [P] Implement `backend/src/main/java/com/hls/reporting/internal/ZoneResolver.java` and period parsing: `zoneId` validated through `ScopeQueries` (unreachable or unknown answers 404), `period=YYYY-MM` (bad value answers 400, default current month by `BusinessDate`), `today` from `BusinessDate` (Asia/Kolkata)
- [x] T008 Implement `backend/src/main/java/com/hls/reporting/internal/DashboardService.java` (manifest with `filters.zones` the caller may pick, `defaultPeriod`, `inbox` flag; one area; one tile retry) and `backend/src/main/java/com/hls/reporting/web/DashboardController.java` + `DashboardExceptionAdvice.java` for `GET /api/v1/dashboard`, `GET /areas/{area}`, `GET /areas/{area}/tiles/{key}` per `contracts/dashboard-api.md`: any signed-in user may call; an area or tile not in the caller's manifest answers 404; Teacher and System receive an empty manifest; reads are not audited (depends on T005, T006, T007)
- [x] T009 Write the endpoint tests in `backend/src/test/java/com/hls/reporting/DashboardEndpointsTest.java` (integration, Testcontainers) using a fixed test `TileSource` bean: manifest per role (Admin, Director, Manager, Teacher, System), 404 for area/tile outside the manifest, 400 for a bad `period`, 404 for an unreachable `zoneId`, 401 unauthenticated, no audit row written (SC-004)

**Checkpoint**: the manifest and area endpoints work against a fixed test tile.

---

## Phase 3: User Story 1 - One Screen for Today (Priority: P1) MVP

**Goal**: the Admin and Director open the Dashboard and see areas of tiles with figures, the time they were taken, Refresh, and
links to the lists; the placeholder boxes are gone.

**Independent Test**: sign in as Director with demo data, open the Dashboard, see each built area's figures equal the counts on
the screens they link to, and open a tile to land on the matching list with the filter applied.

### Tests for User Story 1

- [x] T010 [P] [US1] Frontend tests in `frontend/src/features/dashboard/__tests__/ManagementDashboard.test.tsx`: areas render in manifest order; each area loads on its own and appears as soon as ready while another is still loading; loading, empty and error states are consistent; `OK`, `NO_DATA` ("no data yet"), `NOT_AVAILABLE` ("not available yet"), `NOT_WORKING_DAY` and `ERROR` (with a Retry that calls the tile endpoint) render distinctly and never as 0; figures are read only on load and on Refresh and the "figures taken at" time updates
- [x] T011 [P] [US1] Frontend tests in `frontend/src/features/dashboard/__tests__/format.test.ts` and `TileCard.test.tsx`: rupees in Indian digit grouping (`₹1,25,000`), dates DD/MM/YYYY, tile is a keyboard-reachable link reading label and figure together, the link goes to the tile's `path` and `query`
- [x] T012 [P] [US1] Update `frontend/src/dashboards/` tests so `AdminDashboard` and `DirectorDashboard` render `ManagementDashboard` and no `PlaceholderWidget` boxes remain, while Manager, Teacher and System dashboards render as before (FR-016)

### Implementation for User Story 1

- [x] T013 [P] [US1] Create `frontend/src/features/dashboard/dashboardApi.ts` (manifest, area, tile, managers, inbox calls with TypeScript types matching `contracts/dashboard-api.md`) and `frontend/src/features/dashboard/format.ts` (rupee Indian grouping, DD/MM/YYYY, percent)
- [x] T014 [P] [US1] Create `frontend/src/features/dashboard/TileCard.tsx` (values with labels, state rendering, "organization-wide" label, link, accessible name, light and dark theme, no horizontal scroll at phone width) and `AreaSection.tsx` (loads one area independently, per-tile retry)
- [x] T015 [US1] Create `frontend/src/features/dashboard/ManagementDashboard.tsx` (loads the manifest once, renders each `AreaSection`, "figures taken at", Refresh button, hides areas the manifest omits) (depends on T013, T014)
- [x] T016 [US1] Replace the placeholder content of `frontend/src/dashboards/AdminDashboard.tsx` and `frontend/src/dashboards/DirectorDashboard.tsx` with `ManagementDashboard` (keep `PendingLeaveWidget` only if its figure is not already a tile; otherwise remove its use); leave `DashboardRouter.tsx`, `ManagerDashboard.tsx`, `TeacherDashboard.tsx`, `SystemDashboard.tsx` untouched (depends on T015)

**Checkpoint**: the shell shows whatever tiles the backend has registered; later stories fill the areas.

---

## Phase 4: User Story 2 - Managers: Tasks, Visits, Check-ins and Leave (Priority: P1)

**Goal**: Managers area with the task, visit, check-in and leave tiles and the Manager-by-Manager table.

**Independent Test**: with a Manager who has an overdue task, a missed visit and no check-in past the cut-off, the tiles and the Manager's row each show those, and each number links to a list that contains that Manager's records.

### Tests for User Story 2

- [x] T017 [P] [US2] Parity tests in `backend/src/test/java/com/hls/tasks/TaskTilesTest.java`: each of `managers.tasks.today|upcoming|overdue|awaiting|escalated` equals the count the Tasks list returns for the link's `view`; scope (Manager sees own tasks only); zone filter
- [x] T018 [P] [US2] Parity tests in `backend/src/test/java/com/hls/recruitment/marketing/MarketingManagerTilesTest.java` for `managers.visits` (planned, completed, missed equal the Marketing calendar with the status filter for the month)
- [x] T019 [P] [US2] Tests in `backend/src/test/java/com/hls/attendance/manager/ManagerAttendanceTilesTest.java` for `managers.noCheckIn` (equals the Manager Attendance list for today with the missing filter; `NOT_WORKING_DAY` on a weekly off or holiday, never a missed check-in)
- [x] T020 [P] [US2] Tests in `backend/src/test/java/com/hls/leave/manager/ManagerLeaveTilesTest.java` for `managers.onLeaveToday` and `managers.pendingRequests` (leave plus forgotten check-ins, same logic as `PendingManagerRequests`)
- [x] T021 [P] [US2] Tests in `backend/src/test/java/com/hls/reporting/ManagerComparisonTest.java`: merged columns overdue, awaiting, escalated, visits missed, Schools won, days with no check-in per Manager; zone filter; sort by column and direction; each cell link opens that Manager's list; org-wide callers only (Manager-only caller gets 403 on `GET /managers`); a bulk fixed number of queries (no query per Manager)

### Implementation for User Story 2

- [x] T022 [P] [US2] Add per-assignee task counts as a small additive read method on `backend/src/main/java/com/hls/tasks/internal/TaskQueries.java` if absent, then create `backend/src/main/java/com/hls/tasks/internal/TaskTiles.java` (five tiles from one memoized `TaskQueries.summary`, grant Manager Tasks, links `/tasks?view=today|upcoming|overdue|awaiting|escalated`) and `TaskManagerColumns.java` (overdue, awaiting, escalated per assignee)
- [x] T023 [P] [US2] Create `backend/src/main/java/com/hls/recruitment/marketing/internal/MarketingTiles.java` (the `managers.visits` tile now; the Schools tiles are added in US3) and `MarketingManagerColumns.java` (visits missed and Schools won per owner), reusing `DashboardService`; add the status filter to the Marketing calendar link target if it does not yet reproduce the figure ("filter to check" in `contracts/tile-catalog.md`)
- [x] T024 [P] [US2] Create `backend/src/main/java/com/hls/attendance/manager/internal/ManagerAttendanceTiles.java` and `ManagerAttendanceColumns.java` (days with no check-in), reusing missed-check-in alerts, `ManagerAttendanceReadApi` and `WorkingCalendar`; add the "today, missing" filter on the Manager Attendance list if it is not present
- [x] T025 [P] [US2] Create `backend/src/main/java/com/hls/leave/manager/internal/ManagerLeaveTiles.java` (`managers.onLeaveToday`, `managers.pendingRequests`), reusing the pending and on-leave-today queries and `PendingManagerRequests`
- [x] T026 [US2] Implement `backend/src/main/java/com/hls/reporting/internal/ManagerComparisonService.java` (merges `ManagerColumns` by Manager id, names and Zones from `organization.api.ManagerQueries`, Zone filter, sort) and the `managers.comparison` table tile (`ORG_WIDE_ONLY`, grant Manager Tasks), and `GET /api/v1/dashboard/managers?zoneId=&period=&sort=&dir=` in `DashboardController.java` (depends on T008, T022-T024)
- [x] T027 [P] [US2] Create `frontend/src/features/dashboard/ManagerComparisonTable.tsx` with sortable columns, row and cell links, keyboard operable, scrolls within its own container at phone width; test in `frontend/src/features/dashboard/__tests__/ManagerComparisonTable.test.tsx`; render it from `TileCard`/`AreaSection` when a tile carries a `table`

**Checkpoint**: US2 complete; the Managers area is fully populated.

---

## Phase 5: User Story 3 - Schools, Marketing and Recruitment (Priority: P1)

**Goal**: Schools and Recruitment areas with the same totals as the Marketing and Recruitment dashboards.

**Independent Test**: the School and recruitment tiles show the same totals as the Marketing dashboard and the Recruitment dashboard for the same period.

### Tests for User Story 3

- [x] T028 [P] [US3] Parity tests in `backend/src/test/java/com/hls/recruitment/marketing/MarketingTilesTest.java`: `schools.stages`, `schools.finalStage`, `schools.visits`, `schools.winLoss` (win rate `NO_DATA` on a zero denominator), `schools.demandSupply` (equals the Marketing dashboard; `NOT_AVAILABLE` when the supply provider is missing, never 0; not zone-aware so `organizationWide` when a Zone is chosen); scope for Manager
- [x] T029 [P] [US3] Parity tests in `backend/src/test/java/com/hls/recruitment/RecruitmentTilesTest.java`: `recruitment.funnel`, `recruitment.joiningRatio` (`NO_DATA` on zero joiners or offers), `recruitment.readyToDeploy` equal the Recruitment dashboard for the same month

### Implementation for User Story 3

- [x] T030 [US3] Extend `backend/src/main/java/com/hls/recruitment/marketing/internal/MarketingTiles.java` with the five Schools tiles of `contracts/tile-catalog.md` (memoized `DashboardService`, grant Marketing, Zone-aware except `schools.demandSupply`); add the Final Stage awaiting-review filter to the Prospects list link target if missing (depends on T023)
- [x] T031 [P] [US3] Create `backend/src/main/java/com/hls/recruitment/internal/RecruitmentTiles.java` (three tiles, grant Recruitment, not zone-aware, month period) reusing `RecruitmentDashboardService`; add the "ready to deploy" filter on the Induction list if missing

**Checkpoint**: US3 complete.

---

## Phase 6: User Story 4 - Teachers and Operations (Priority: P1)

**Goal**: Teachers, substitution, attendance-away and Teacher-leave tiles.

**Independent Test**: mark a Teacher absent today with an open substitution requirement; the Teachers tile shows one away, the Operations tile one open requirement, and each opens the matching list.

### Tests for User Story 4

- [x] T032 [P] [US4] Parity tests in `backend/src/test/java/com/hls/teacher/TeacherTilesTest.java` for `teachers.byStatus` (in training, active, on leave, exited by stored status, within `TeacherScopeQueries`)
- [x] T033 [P] [US4] Parity tests in `backend/src/test/java/com/hls/substitution/SubstitutionTilesTest.java` for `operations.substitutions` (open, assigned, Manager cover today, as the Substitutions list shows them; cancelled not counted)
- [x] T034 [P] [US4] Tests in `backend/src/test/java/com/hls/attendance/TeacherAttendanceTilesTest.java` for `attendance.awayToday` (absent plus approved leave equals the Attendance list away filter; `NOT_WORKING_DAY` on weekly off or holiday) and in `backend/src/test/java/com/hls/leave/TeacherLeaveTilesTest.java` for `leave.pendingTeacher` (equals `PendingLeaveWidget` / Leave Management pending)

### Implementation for User Story 4

- [x] T035 [P] [US4] Create `backend/src/main/java/com/hls/teacher/internal/TeacherTiles.java` (grant Teachers, Zone-aware, link Teachers with status filter)
- [x] T036 [P] [US4] Create `backend/src/main/java/com/hls/substitution/internal/SubstitutionTiles.java` (grant Substitution, today, link Substitutions today with status filter; add that filter if missing)
- [x] T037 [P] [US4] Create `backend/src/main/java/com/hls/attendance/internal/TeacherAttendanceTiles.java` (reuses `AttendanceReadApi.teachersAwayOn`, `WorkingCalendar`; add the "today, away" filter on the Attendance list if missing)
- [x] T038 [P] [US4] Create `backend/src/main/java/com/hls/leave/internal/TeacherLeaveTiles.java` (same query as `PendingLeaveWidget`)

**Checkpoint**: US4 complete.

---

## Phase 7: User Story 5 - Payroll This Month (Priority: P1)

**Goal**: Payroll area for holders of Payroll View, and nothing for anyone else.

**Independent Test**: with a payroll run awaiting approval, the Payroll tile shows that state and total and opens the run.

### Tests for User Story 5

- [x] T039 [P] [US5] Tests in `backend/src/test/java/com/hls/payroll/run/PayrollTilesTest.java`: `payroll.run` (none, draft, awaiting approval, approved, cancelled; "no run yet" is `OK` text, not an amount of 0), `payroll.paidOwed` (paid and owed equal the run's owed tab via `OwedService`), `payroll.locks` (attendance locked for Teachers and for Managers equals `isLocked`); amounts carry `unit RUPEES`; tiles are `ORG_WIDE_ONLY`
- [x] T040 [P] [US5] Security test in `backend/src/test/java/com/hls/reporting/PayrollPermissionTest.java` (SC-007): with Payroll View removed from Admin, the manifest, the Payroll area response, a direct tile request (404) and every other dashboard response contain no payroll key, amount or text; a Manager-only caller gets 404 for the Payroll area

### Implementation for User Story 5

- [x] T041 [US5] Create `backend/src/main/java/com/hls/payroll/run/internal/PayrollTiles.java` (grant Payroll View, `ORG_WIDE_ONLY`, month period; reuses `RunService` and `OwedService`; link to the run or the Payroll runs list)

**Checkpoint**: US5 complete. Systemic guarantees (SC-005) are tested in Phase 12.

---

## Phase 8: User Story 6 - Approvals Inbox (Priority: P2)

**Goal**: one list of the decisions waiting for the user, oldest first.

**Independent Test**: with one pending Manager leave and one payroll run awaiting approval, the inbox lists two entries, each linking to its decision screen; after the decision the entry leaves the list on refresh.

### Tests for User Story 6

- [x] T042 [P] [US6] Tests in `backend/src/test/java/com/hls/reporting/InboxServiceTest.java`: merge from fake `ApprovalSource`s, grant checked before the source is called (a kind is absent from the list and totals without Approve/Process), the caller's own requests dropped (FR-014), oldest first, `ageDays` in business days, totals per kind complete across pages, page size 50
- [x] T043 [P] [US6] Tests for each source: `backend/src/test/java/com/hls/leave/manager/ManagerLeaveApprovalsTest.java`, `backend/src/test/java/com/hls/attendance/manager/ForgottenCheckInApprovalsTest.java`, `backend/src/test/java/com/hls/recruitment/marketing/FinalStageApprovalsTest.java`, `backend/src/test/java/com/hls/payroll/run/PayrollApprovalsTest.java` (not a run the caller submitted), `backend/src/test/java/com/hls/tasks/TaskApprovalsTest.java`; each item equals the module's own pending list and a decided item is gone on the next call
- [x] T044 [P] [US6] Endpoint tests added to `backend/src/test/java/com/hls/reporting/DashboardEndpointsTest.java` for `GET /inbox` (403 for a Manager-only caller, `kind` filter, paging)

### Implementation for User Story 6

- [x] T045 [US6] Implement `backend/src/main/java/com/hls/reporting/internal/InboxService.java` and `GET /api/v1/dashboard/inbox?kind=&page=&size=` in `DashboardController.java` (depends on T004, T008)
- [x] T046 [P] [US6] Create `backend/src/main/java/com/hls/leave/manager/internal/ManagerLeaveApprovals.java` (`MANAGER_LEAVE`, Manager Leave Approve)
- [x] T047 [P] [US6] Create `backend/src/main/java/com/hls/attendance/manager/internal/ForgottenCheckInApprovals.java` (`FORGOTTEN_CHECK_IN`, Manager Leave Approve)
- [x] T048 [P] [US6] Create `backend/src/main/java/com/hls/recruitment/marketing/internal/FinalStageApprovals.java` (`FINAL_STAGE_PROSPECT`, Marketing Approve)
- [x] T049 [P] [US6] Create `backend/src/main/java/com/hls/payroll/run/internal/PayrollApprovals.java` (`PAYROLL_RUN`, Payroll Approve, excludes runs submitted by the caller)
- [x] T050 [P] [US6] Create `backend/src/main/java/com/hls/tasks/internal/TaskApprovals.java` (`TASK_VERIFICATION`, Manager Tasks Approve)
- [x] T051 [P] [US6] Create `frontend/src/features/dashboard/InboxPanel.tsx` (totals per kind, items with requester, subject, DD/MM/YYYY date, age, one click to the decision screen, Refresh) and `frontend/src/features/dashboard/__tests__/InboxPanel.test.tsx`; render it in `ManagementDashboard.tsx` only when the manifest says `inbox: true` (depends on T015)

**Checkpoint**: US6 complete.

---

## Phase 9: User Story 7 - Add a Tile Later Without Rework (Priority: P2)

**Goal**: prove a later spec can add a tile with no change to `reporting` or to other tiles.

**Independent Test**: a test tile added in the test setup appears under its area for a user who holds its permission, is absent for one who does not, and no other tile changes.

- [ ] T052 [US7] Test in `backend/src/test/java/com/hls/reporting/TileExtensibilityTest.java` (SC-006): register a test `TileSource` (and a test `ManagerColumns` and `ApprovalSource`) through a test configuration only; assert it appears in the right area/order for a permitted user, is absent from manifest and 404 on direct request for one without the grant, a duplicate key fails start-up, and every other tile's result is byte-for-byte unchanged
- [ ] T053 [P] [US7] Document the extension recipe (how a later spec adds a `*Tiles` class, the tile catalog row, the parity test) in `specs/028-management-dashboard/quickstart.md` section 7 and in the `reporting.api` `package-info.java`

---

## Phase 10: User Story 8 - Filter by Zone and Period (Priority: P2)

**Goal**: narrow to one Zone and choose a month.

**Independent Test**: choose one Zone; every tile that can be split by Zone changes to that Zone's figures, and tiles that cannot be split say so.

- [ ] T054 [P] [US8] Backend tests in `backend/src/test/java/com/hls/reporting/ZonePeriodFilterTest.java`: Zone changes only zone-aware tiles and sets `organizationWide: true` on the others; `period` changes `MONTH` tiles and is ignored by `TODAY` tiles; an unreachable Zone is 404; the Manager comparison and inbox respect the Zone/period
- [x] T055 [P] [US8] Frontend tests in `frontend/src/features/dashboard/__tests__/DashboardFilters.test.tsx`: Zone picker lists only manifest zones, month picker defaults to the current month, changing either reloads all areas, "organization-wide" label shown
- [x] T056 [US8] Create `frontend/src/features/dashboard/DashboardFilters.tsx` (Zone select with "All Zones", month input) and wire it into `ManagementDashboard.tsx` and `dashboardApi.ts` (depends on T015)

---

## Phase 11: User Story 9 - A Manager's Own Dashboard Stays Scoped (Priority: P2)

**Goal**: the Manager's Dashboard keeps its existing cards and shows scoped tiles beneath them (default noted at the top).

**Independent Test**: a Manager sees only figures that equal what their scoped lists show; the Manager-by-Manager table, payroll and approvals are absent.

- [ ] T057 [P] [US9] Backend tests in `backend/src/test/java/com/hls/reporting/ManagerScopeTest.java` (SC-004): Manager A sees only Zone 1 figures and own tasks, visits and check-in; no comparison tile, payroll area (404) or inbox (403); Zone 2 as `zoneId` is 404; a user with Manager and Director roles sees the Director's tiles once; Teacher and System manifests are empty
- [ ] T058 [P] [US9] Update `frontend/src/dashboards/ManagerDashboard.test.tsx` so the existing three count cards still render unchanged and scoped tiles render below them, with no comparison table, payroll area or inbox
- [ ] T059 [US9] Edit `frontend/src/dashboards/ManagerDashboard.tsx` to keep the existing cards and render `ManagementDashboard` (manifest-driven, so only permitted tiles appear) below them without testing role names for the new section (depends on T015, T058)

---

## Phase 12: Polish and Cross-Cutting

- [ ] T060 [P] Systemic failure tests in `backend/src/test/java/com/hls/reporting/MissingAndFailingSourceTest.java` (SC-005): for every registered tile, with its source throwing, with it exceeding the time limit, and with its optional provider missing, the result is `ERROR`/`NOT_AVAILABLE`, never `OK` with 0; the other tiles of the area still return
- [ ] T061 [P] Parity completeness test in `backend/src/test/java/com/hls/reporting/TileParityCoverageTest.java`: every key in `contracts/tile-catalog.md` is registered and has a parity test class (fails when a tile is added without one) (SC-003)
- [ ] T062 [P] Performance check (SC-002): a test in `backend/src/test/java/com/hls/reporting/AreaPerformanceTest.java` loading each area on the demo-sized data set within 3 seconds, and assert a fixed number of queries per area
- [ ] T063 [P] Frontend accessibility and layout pass in `frontend/src/features/dashboard/__tests__/` (axe checks on tiles, table, inbox; phone width without horizontal page scroll; light and dark theme tokens)
- [ ] T064 [P] Add the dashboard requests (manifest, each area, tile, managers, inbox) with role variants to `postman/` collection
- [ ] T065 [P] Demo data: ensure the demo seeders create the scenarios in `quickstart.md` prerequisites (overdue task, missed visit, Manager with no check-in past cut-off, Teacher absent with open substitution, pending Manager leave, payroll run awaiting approval) in the existing dev seeder under `backend/src/main/java/com/hls/` (find it from `backend/src/test/java/com/hls/DevSeedTest.java`) and extend `DevSeedTest`
- [ ] T066 [P] Add a one-line "Dashboard figures: not cached" note to `docs/caching.md`; update the roadmap/status doc if one lists spec 028; apply the Principle VII PATCH to `.specify/memory/constitution.md` naming the `reporting` module and its SPI (version number `NEXT`, assigned at merge)
- [ ] T067 Run the automated set from `quickstart.md`: `mvn -f backend/pom.xml test -Dtest='reporting.*,*Tiles*,*Approvals*,ArchitectureTest,ModulithTest'` (one class at a time if slow) and `npm --prefix frontend run test -- dashboard`; fix failures
- [ ] T068 Manual walkthrough of `quickstart.md` sections 1 to 8 with the demo users (Director, Admin, Manager A and B, Teacher, System), including dark mode and phone width; record the result at the end of `quickstart.md`

---

## Dependencies and Execution Order

- Phase 1 then Phase 2 (blocks everything). US1 (frontend shell) needs only Phase 2 and can run in parallel with the source phases.
- US2, US3, US4, US5 are independent of each other after Phase 2 (different modules); US3 extends `MarketingTiles` from US2 (T030 after T023); US2's comparison needs the columns from T022-T024.
- US6 needs Phase 2 and its five sources are independent; T051 needs T015.
- US7 needs at least one real source registered (any of US2-US5) only for realism; the test itself needs only Phase 2.
- US8 backend is part of Phase 2; its frontend (T056) needs T015. US9 needs T015 and the sources it scopes.
- Polish after the stories it checks (T060, T061 after US2-US6).

### Parallel examples

- After Phase 2: T022, T023, T024, T025 (four modules) together; T031, T035-T038 together; T046-T050 together.
- Frontend T013, T014 in parallel with all backend source work.

## Implementation Strategy

1. MVP: Phase 1, Phase 2, US1 plus US2 (Managers area): the Director sees the first real area on the new screen.
2. Add US3, US4, US5 (remaining P1 areas), then ship the Dashboard to Admin and Director.
3. Then US6 (inbox), US8 (filters), US7 (proof of extensibility), US9 (Manager scoping), then Polish.
4. Each tile is delivered with its parity test; no tile is merged without it.
