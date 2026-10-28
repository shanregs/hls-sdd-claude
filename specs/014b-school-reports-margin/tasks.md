# Tasks: School, Collections and Margin Reports (014b)

**Input**: `spec.md`, `plan.md`, `research.md`, `data-model.md`, `contracts/reports-and-read-amendments.md`. **Prerequisite**: 014a and 014c merged (framework, REPORTS menu, `PayrollFigures`).
**Tests**: included (scope, audit and equality-with-owning-screen tests are required by the constitution). Run Maven with `JAVA_HOME` set to JDK 25, one test class at a time.
**Format**: `[ID] [P?] [Story] Description`. [P] means parallel (different files, no dependency on an unfinished task).
**Paths**: `B` = `backend/src/main/java/com/hls`, `T` = `backend/src/test/java/com/hls`, `F` = `frontend/src/features/reports`.

## Phase 1: Permissions, navigation and module

- [ ] T001 Add `REPORT_MARGIN`, `REPORT_COLLECTIONS`, `REPORT_SCHOOL` (actions View, Export) to `B/identity/permissions/PermissionModule.java`
- [ ] T002 Eligibility and defaults in `B/identity/permissions/PermissionEligibility.java` and the defaults seeder: eligible roles Admin, Director, Manager only (Teacher and System can never be granted them); defaults Admin and Director View and Export, Manager View and Export (scope own Schools); `REPORT_PAYROLL` unchanged
- [ ] T003 [P] Three REPORTS items "Margin", "Collections", "School" at order 810 in `B/identity/accessmodel/NavigationCatalog.java`, routes `/reports/margin`, `/reports/collections`, `/reports/school`; update the access-model tests in `T/identity/accessmodel/` that list the REPORTS items (Manager, Admin, Director) and `NavigationSectionOrderTest` if affected
- [ ] T004 [P] Add `REPORT_EXPORT_MARGIN`, `REPORT_EXPORT_COLLECTIONS`, `REPORT_EXPORT_SCHOOL` to the entity map in `B/audit/support/AuditVisibility.java`, each tied to its permission key; test in the audit visibility test that a Manager sees only the exports of reports they hold
- [ ] T005 Create module `B/schoolreports/internal/` (package only, no `package-info` needed, like `peoplereports`); run `T/ApplicationModulesTest.java` to confirm the module graph (depends only on `reporting.api` and the other modules' `api` packages)

## Phase 2: Read-only amendments to merged modules (each with its test)

- [ ] T006 [P] A1 (022) `SchoolReceivables.billedPerTeacherAndSchool(Collection<UUID> teacherIds, Collection<UUID> schoolIds, YearMonth from, YearMonth to)` returning `TeacherSchoolBilled(teacherId, schoolId, month, amount, workedDays, monthDays)` from the `due_line` rows grouped by teacher, month and School (cancelled dues dropped, as in `billedPerTeacher`); either collection empty means "any"; one grouped query. Files: `B/schoolbilling/api/SchoolReceivables.java`, `B/schoolbilling/internal/SchoolReceivablesImpl.java`
- [ ] T006a Test A1 in `T/schoolbilling/`: per Teacher and month the amounts add up to `billedPerTeacher`; a Teacher at two Schools gives two entries whose `workedDays` come from the due lines; a cancelled due contributes nothing; fixed statement count over 5 and 50 Teachers
- [ ] T007 [P] A1 (022) `SchoolReceivables.ageing(Collection<UUID> schoolIds, LocalDate asOf)` returning `SchoolAgeing(schoolId, outstanding, notYetDue, d0to30, d31to60, d61to90, over90)`, built with `LedgerQueryService.bucketsOf` so it cannot drift from the Ageing screen. Files: same two as T006
- [ ] T007a Test ageing: equals the 022 Ageing screen figures for the same School and date; `notYetDue + d0to30 + d31to60 + d61to90 + over90 = outstanding` for every School; a credit note larger than the bill never gives a negative outstanding
- [ ] T008 [P] A2 (022) `SchoolContracts.contractsOfAll(Collection<UUID> schoolIds, LocalDate date)` returning `Map<UUID, ContractView>` (a School with no contract in effect is absent). Files: `B/schoolbilling/api/SchoolContracts.java`, `B/schoolbilling/internal/SchoolContractsImpl.java`
- [ ] T008a Test A2: equals `contractOf` for each of several Schools, including one with no contract; fixed query count
- [ ] T009 [P] A3 (017a) new `B/substitution/api/SubstitutionCover.java`: `Map<UUID, SchoolCover> coverBySchool(Collection<UUID> schoolIds, LocalDate from, LocalDate to)` with `SchoolCover(schoolId, bySubstitute, byManager, open)` as day values (a half day counts 0.5, cancelled requirements excluded); implement in `B/substitution/internal/SubstitutionCoverImpl.java`
- [ ] T009a Test A3 in `T/substitution/`: covered by a substitute, covered by the Manager and open requirements counted separately; half days; cancelled excluded; equals the Substitutions screen list for the same School and range
- [ ] T010 [P] A4 (026) `PerformanceSummary.bySchool(LocalDate from, LocalDate to)` returning `Map<UUID, Counts>` with the same `Counts` and rules as `byManager`. Files: `B/performance/api/PerformanceSummary.java` and its implementation in `B/performance/internal/`
- [ ] T010a Test A4: the sum over a Manager's Schools equals `byManager` for that Manager; one grouped query
- [ ] T011 [P] A5 (023) new `B/recruitment/api/SchoolStages.java`: `Map<UUID, String> stageOf(Collection<UUID> schoolIds)` giving the effective stage of the prospect linked to each School (absent when the School has no prospect record), using the same `EffectiveStageResolver` as the pipeline screen; implement in `B/recruitment/marketing/internal/`
- [ ] T011a Test A5: equals the stage shown on the pipeline for the linked prospect; a School without a prospect is absent
- [ ] T012 [P] A6 (008) new `B/attendance/api/SchoolAttendance.java`: `Map<UUID, SchoolRollup> rollupsBySchool(Collection<UUID> schoolIds, YearMonth month)` with `SchoolRollup(schoolId, teachers, working, present, absent, leave, lossOfPay)`; change the `schools` section of `B/attendance/internal/AttendanceReportSource.java` to read the same grouping so the two cannot differ (Teachers are grouped by their latest placement in the month)
- [ ] T012a Test A6: equals the Attendance report `schools` row for the same School and month; all existing `AttendanceReportTest` cases stay green
- [ ] T013 [P] A7 (005) `TeacherDirectory.placedCountBySchool(Collection<UUID> schoolIds, LocalDate date)` returning `Map<UUID, Integer>` of Teachers whose ACTIVE placement covers the date. Files: `B/teacher/api/TeacherDirectory.java`, its implementation in `B/teacher/internal/`
- [ ] T013a Test A7: equals the Teachers list filtered by School for the same date; a Teacher moved mid-month counts only at the School that covers the date

## Phase 3: US1 Margin report (P1) MVP

**Goal**: School fee minus Teacher pay, per Teacher and School and month, with totals per School and Zone.
**Independent test**: with a billed month and an approved pay run, every row's fee equals the due line, pay equals the payslip net (split by billed days), margin is their difference.

- [ ] T014 [US1] Pure class `B/schoolreports/internal/PaySplit.java`: `split(BigDecimal net, Map<UUID, BigDecimal> daysBySchool)` returning the pay per School as `Pay(s) = round(net x days(s) / sum days)` (rounded half-even to the paisa), the remainder paisa to the School with the most days (ties: lower School id) so `sum Pay(s) = net` exactly; a single School gets the whole net; zero total days is refused with a clear exception
- [ ] T014a [US1] `T/schoolreports/PaySplitTest.java`: one School; 10:20 split of 14,000.00; a three-way split with an odd paisa (the parts add up to the net exactly); a tie goes to the lower School id; zero days refused
- [ ] T015 [US1] `B/schoolreports/internal/MarginReportSource.java` (`ReportSource`, key `margin`, module `REPORT_MARGIN`, filters ZONE, SCHOOL, TEACHER, month state from the pay run): fees from `SchoolReceivables.billedPerTeacherAndSchool` (T006), pay from `PayrollFigures.peopleOf(month)` (Teacher lines, `payable` and `net` not null) split by `PaySplit` on the billed `workedDays`; sections `schools`, `zones`, `teachers` with the columns in `data-model.md`; each optional read in its own `PROPAGATION_REQUIRES_NEW` read-only transaction (research D7)
- [ ] T016 [US1] Matching rules in the same class: a Teacher with a fee line but no payable payslip is "not matched: no payslip for this month" (or "payslip cannot be paid"); a payslip with no fee line is "not matched: no School fee for this month"; not-matched rows are excluded from every total and from "Teachers matched"; a month with no run gives "no data yet" for Pay and Margin (not "not matched") and totals show Fee only
- [ ] T017 [US1] Month state and default month: none / DRAFT or awaiting approval with the note "payroll not approved, figures may change" / APPROVED (from `PayrollFigures.runOf`); `lastFinalMonth` is `PayrollFigures.lastApprovedMonth()`, else the current month
- [ ] T018 [US1] Scope: only fee lines at Schools in `ctx.scope()`; for a Zone Manager the pay shown is the part split to those Schools and the Teacher's other Schools are not shown; a Zone or School outside scope is refused (`ReportOutOfScopeException`)
- [ ] T019 [US1] `T/schoolreports/MarginReportTest.java`: (1) fee = due line, pay = payslip net, margin = fee - pay for three Teachers; (2) a Teacher at two Schools (transfer) gives two rows whose pays add up to the payslip net; (3) due without payslip and payslip without due are "not matched" with the reason and the totals do not change; (4) draft run shows the banner note; (5) no run gives "no data yet"; (6) a Zone Manager sees only their Schools' rows with fee, net pay and margin and gets 403 for `/api/v1/reports/payroll`; (7) a negative margin is shown as negative

## Phase 4: US2 Collections report (P1)

**Goal**: billed, collected, outstanding and ageing by School, Zone and organization, equal to School Billing.
**Independent test**: for a month with a partial collection and an overdue balance, every figure equals School Billing and the buckets add up.

- [ ] T020 [US2] `B/schoolreports/internal/CollectionsReportSource.java` (key `collections`, module `REPORT_COLLECTIONS`, filters ZONE, SCHOOL, month state NONE with the note "ageing as of <today>"): section `month` from `SchoolReceivables.schoolMonths` (Billed = owed, Collected, Outstanding = balance never below zero, Due on) with Zone and organization totals; section `ageing` from `SchoolReceivables.ageing` (T007) with columns Outstanding, Not yet due, 0 to 30, 31 to 60, 61 to 90, Over 90 and Zone and organization totals; `lastFinalMonth` is the current month
- [ ] T021 [US2] Invariant check in the source: in every `ageing` row and total, `Not yet due + 0 to 30 + 31 to 60 + 61 to 90 + Over 90 = Outstanding`; a School never billed has no row; a Zone with no Schools gives no rows and a note
- [ ] T022 [US2] `T/schoolreports/CollectionsReportTest.java`: (1) billed 60,000 and collected 25,000 gives outstanding 35,000; (2) amounts 10, 45, 75 and 120 days past due land in the four buckets; (3) an amount not yet due is in "Not yet due" only; (4) Zone and organization totals equal the sum of their School rows; (5) a credit note: billed and outstanding equal the School Billing screen; (6) a Zone Manager sees only their Schools and gets 403 for another Zone

## Phase 5: US3 School report (P2)

**Goal**: one row per School and month with people, attendance, substitution, performance, billing and prospect stage.
**Independent test**: for a School with Teachers, attendance, a substitution, an observation and a billed month, each cell equals the owning screen.

- [ ] T023 [US3] `B/schoolreports/internal/SchoolReportSource.java` (key `school`, module `REPORT_SCHOOL`, filters ZONE, SCHOOL, month state from the attendance lock, default the last fully locked month else the current): section `schools` with the columns in `data-model.md`, from `TeacherDirectory.placedCountBySchool` (T013), `SchoolContracts.contractsOfAll` (T008), `SchoolAttendance` (T012), `SubstitutionCover` (T009), `PerformanceSummary.bySchool` (T010), `SchoolReceivables.schoolMonths`, `SchoolStages` (T011); each optional read in its own `PROPAGATION_REQUIRES_NEW` read-only transaction
- [ ] T024 [US3] Missing figures: no contract gives "no data yet" for Contract end; no Teachers placed gives "no data yet" for Attendance %; a School with no prospect record gives "not applicable" for Stage; a source that fails gives "not available" for its own columns only
- [ ] T025 [US3] `T/schoolreports/SchoolReportTest.java`: (1) three Teachers placed gives 3 and the contract end equals the contract; (2) Attendance % equals the Attendance report `schools` row; (3) substitution days equal the Substitutions screen; (4) observations and open issues equal Teacher Performance; (5) billed, collected and outstanding equal the Collections report for the same School and month; (6) stage equals the pipeline, "not applicable" without a prospect; (7) a Zone Manager sees only their Schools

## Phase 6: US4 Access, export, failure and volume (P1)

**Goal**: all three reports export through the 014a writer, are audited, scoped by permission, resilient to a failing source and fast at full size.
**Independent test**: export each, compare to the screen, check one audit row each; sign in as Zone Manager, Teacher and System.

- [ ] T026 [US4] `T/schoolreports/SchoolReportsExportTest.java`: for each of the three reports the CSV starts with the header block, contains every screen row label, and writes exactly one audit row (`REPORT_EXPORT_MARGIN`, `_COLLECTIONS`, `_SCHOOL`); a caller without the Export grant is refused the file (403) but still opens the screen; a Teacher and a System user get 403 on screen and file for all three; a Manager name that starts with `=` is written as `'=...`
- [ ] T027 [US4] `T/schoolreports/SchoolReportsSourceDownTest.java` (use `@MockitoSpyBean` and `doThrow`, as in `T/peoplereports/ReportSourceDownTest.java`): payroll down gives Margin Pay and Margin "not available" while Fee shows; ageing down fails only the `ageing` section of Collections; substitution down gives "not available" for the cover columns of the School report only, and every other column still shows
- [ ] T028 [US4] [P] Web test in `F/__tests__/ReportPage.test.tsx`: add the `margin`, `collections` and `school` shapes (a "not matched" cell and a negative margin, a "Not yet due" column, a "not applicable" stage) and check the generic page renders them with the Export button and no new screen code; run `npx vitest run src/features/reports`, `npx tsc --noEmit -p .`, `npx eslint src/features/reports`
- [ ] T029 [US4] `T/schoolreports/SchoolReportsPerformanceTest.java` modelled on `T/peoplereports/PeopleReportsPerformanceTest.java`: at 500 Teachers, 50 Managers and 5 Schools with billing and a pay run, the first page of each report is under 3 s, each export under 30 s, and the statement count equals the count at 20 Teachers and 5 Managers

## Phase 7: Polish

- [ ] T030 SC-006 check: no 014a framework file under `B/reporting/` is changed by this feature (record the result in the PR); no migration was added (`git diff origin/main --name-only -- backend/src/main/resources/db/migration` is empty)
- [ ] T031 Constitution: add a PATCH note to the Sync Impact Report in `.specify/memory/constitution.md` (the Default role access matrix gains Margin, Collections and School report rows: Admin and Director View and Export, Manager View and Export for own Schools, Teacher and System none; Principle VII holds) with the next free patch number
- [ ] T032 Update the 014 row of `docs/spec-roadmap.md` (014b implemented, walkthrough pending) and tick the matching line in the summary table
- [ ] T033 Run the touched suites one class at a time: `T/schoolreports/*`, `T/identity/accessmodel/*`, `AttendanceReportTest`, the new amendment tests, `ApplicationModulesTest`, `ReportingModuleRulesTest`, plus the frontend `vitest run src/features/reports`; then the manual walkthrough from `quickstart.md`

## Dependencies

T001-T005 first. Phase 2 tasks are independent of each other (all [P]) and each test follows its change. US1 needs T006, T014 and PayrollFigures (already merged). US2 needs T007. US3 needs T006's `schoolMonths` (exists), T008-T013 and T020 for the equality test. US4 after at least one report; T028 can start once US1 shapes are known. Polish last.

## Parallel examples

- Phase 2: T006, T007, T008, T009, T010, T011, T012, T013 and their tests can be done by different people at once (different modules).
- After Phase 2: T015-T019 (Margin) and T020-T022 (Collections) in parallel; T023-T025 (School) once T008-T013 are done.
- T028 (web test) in parallel with T026 and T027.

## Implementation strategy

MVP is Phases 1 and 2 (only A1 `billedPerTeacherAndSchool`) plus Phase 3 (Margin): it delivers the report the deck asks for. Then Collections (T007 and Phase 4), then the School report (the other amendments and Phase 5), then export, failure and volume checks (Phase 6) and polish. Commit after each task group; do not add a migration; keep every optional read in its own read-only transaction.
