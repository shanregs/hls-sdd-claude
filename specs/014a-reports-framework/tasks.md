# Tasks: Reports Framework, Attendance and Payroll Reports (014a)

**Input**: `specs/014a-reports-framework/spec.md`, `plan.md`
**Tests**: included (the constitution requires scope and audit tests).
**Format**: `[ID] [P?] [Story] Description`. [P] means parallel (different files).

## Phase 1: Setup and permissions

- [x] T001 Add `REPORT_ATTENDANCE` (View, Export), `REPORT_PAYROLL` (View, Export) and `MY_REPORT` (View) to `backend/src/main/java/com/hls/identity/permissions/PermissionModule.java`
- [x] T002 Set eligibility and seeded defaults (Admin, Director: both reports; Manager: Attendance; Teacher: MY_REPORT; no System) in `PermissionEligibility.java` and the seeder; test that Manager cannot be granted `REPORT_PAYROLL` by default
- [x] T003 [P] Add the REPORTS section and items (Attendance, Payroll, My Report) to `NavigationCatalog.java`; update its tests and the access-model menu expectations
- [x] T004 [P] Record the decided amendment to spec 002 (per-report keys replace the single REPORTS row) in `specs/002-access-model-app-shell/spec.md`, and add a test that the permission matrix lists the three new modules and no `REPORTS` module

## Phase 2: Framework (blocks every story)

- [ ] T005 [P] Contract types in `backend/src/main/java/com/hls/reporting/api/`: `ReportDefinition`, `ReportSource`, `ReportContext`, `ReportPage`, `ReportRequest`, `Cell` (value, NO_DATA, NOT_AVAILABLE, NOT_MATCHED), `MonthState`
- [ ] T006 `ReportScopeResolver` in `reporting/internal/report/`: Admin and Director org-wide, Manager through the Manager's Zones, Schools and Teachers, Teacher own; refuses a Zone, School, Teacher or Manager filter outside scope (reuse `ZoneResolver`)
- [ ] T007 [P] `DefaultMonth` (last locked month, else current) with a test for no locked month
- [ ] T008 `ReportRegistry` (sources found by key, menu per caller from grants) and `ReportService` (grant check, scope, fail-soft per column using the `AreaReader` time limit)
- [ ] T009 [P] `ReportCsvWriter`: header block (report, month, filter, month state, time made), streaming, formula neutralisation for `=`, `+`, `-`, `@`; unit tests
- [ ] T010 `ReportExportAudit`: writes who, report, filter and time through the audit module; test that every export writes one row
- [ ] T011 `ReportController` in `reporting/web/`: `GET /api/reports`, `GET /api/reports/{key}`, `GET /api/reports/{key}/export`; extend the advice; integration test for 403 on a missing grant and for a missing Export grant

## Phase 3: US1 Attendance and LOP report (P1) MVP

- [ ] T012 Add bulk `rollupsOf(Collection<UUID>, YearMonth)` to `attendance/api/AttendanceReadApi.java` and its implementation (decided amendment to 008, additive)
- [ ] T012a Test `rollupsOf` equals `rollupOf` for each Teacher over 3 Teachers and 2 months, and uses a fixed number of queries
- [ ] T013 [US1] `AttendanceReportSource` for Teachers: working days, present, absent, leave, LOP days, attendance %; roll-ups by School, Zone, organization; zero working days gives NO_DATA
- [ ] T014 [US1] Manager rows from `ManagerAttendanceReadApi.months`: working days, checked-in days, leave, missed check-ins, Manager attendance %
- [ ] T015 [US1] Month state: OPEN when not all people in scope are locked (`lockedTeachers`, manager lock); label in page and export
- [ ] T016 [US1] Tests: figures equal the attendance grid and the 028 attendance tiles for a seeded month; a Zone Manager cannot read another Zone; hand-count of 20 of 25 days is 80%
- [ ] T017 [P] [US1] Frontend: `frontend/src/features/reports/` report list, generic report page (month, Zone, School, Teacher, Manager filters, table, totals, state banner), Attendance columns; component tests

## Phase 4: US2 Payroll cost report (P1)

- [ ] T018 Add `payroll/api/PayrollFigures` (read-only: run state for a month; per person gross, LOP, reimbursements, net, kind, Zone and School) and implement in `payroll/run/internal` over the existing run data (decided amendment to 013b, read-only and additive); also reused by 014b
- [ ] T018a Test `PayrollFigures` returns the same totals as the run total and the run export for an approved and a draft run, and no figure for a month with no run
- [ ] T019 [US2] `PayrollReportSource`: totals by kind of person, Zone and School, run state, cycle table by month; "no run yet" gives NO_DATA; draft run is labelled
- [ ] T020 [US2] Tests: totals equal the run total, the run export and the dashboard payroll tile; Zone Manager and Teacher get 403 and no menu item
- [ ] T021 [P] [US2] Frontend: Payroll columns and cycle table

## Phase 5: US3 CSV export and audit (P1)

- [ ] T022 [US3] Wire Export for both reports with the same filter and scope; tests that file rows equal screen rows and a user without Export is refused
- [ ] T023 [P] [US3] Frontend Export button gated by the Export grant; download with the filter

## Phase 6: US4 My Report (P2)

- [ ] T024 [US4] `MyReportSource`: a Teacher's own attendance, leave (009) and payslip history (via `PayrollFigures` own rows); a Manager's view of own Teachers uses the Attendance scope
- [ ] T025 [US4] Tests: Teacher cannot read another Teacher by filter or id; a Teacher moved out of a Manager's School disappears from later months
- [ ] T026 [P] [US4] Frontend: MY ATTENDANCE → My Report page

## Phase 7: Polish and verification

- [ ] T027 Test report added on the framework with no framework change (SC-006), kept as a test fixture
- [ ] T028 Source-failure test: one source throws, other columns still load (SC-005)
- [ ] T029 Performance check at 500 Teachers and 50 Managers (first page under 3 s, export under 30 s)
- [ ] T030 Check no migration was added (none expected); if one is needed, take the next free number above V120 on current main
- [ ] T031 Update `docs/spec-roadmap.md` 014 row with the implemented state and the quickstart in `specs/014a-reports-framework/quickstart.md`; run the full backend and frontend suites

## Dependencies

T001-T004, then T005-T011 (framework), then US1 and US2 in parallel (T012 before T013, T018 before T019), US3 after at least one report, US4 after T018 and T013. Polish last. 014b and 014c start after Phase 2.
