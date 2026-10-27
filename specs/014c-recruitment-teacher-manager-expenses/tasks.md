# Tasks: Recruitment, Teacher, Manager and Expenses Reports (014c)

**Input**: `spec.md`, `plan.md`, `contracts/read-amendments.md`. **Prerequisite**: 014a merged (framework, REPORTS menu).
**Tests**: included (scope, audit and equality-with-owning-screen tests are required by the constitution).
**Format**: `[ID] [P?] [Story] Description`. [P] means parallel (different files).

## Phase 1: Permissions and framework addition

- [X] T001 Add `REPORT_RECRUITMENT`, `REPORT_TEACHER`, `REPORT_MANAGER`, `REPORT_EXPENSES` (View, Export) to `backend/src/main/java/com/hls/identity/permissions/PermissionModule.java`
- [X] T002 Eligibility and defaults (Admin, Director all four; Manager Recruitment, Teacher, Manager; none for Teacher or System; `REPORT_EXPENSES` not grantable to Manager) in `PermissionEligibility.java` and the seeder; test the matrix
- [X] T003 [P] REPORTS nav items for the four reports in `NavigationCatalog.java`; update its tests and the access-model menu expectations
- [X] T004 A0 (amendment to 014a, additive): `ReportFilter.SEASON` and `ReportRequest.season` with the old constructor kept; framework test that existing reports are unaffected and a season is parsed from the query

## Phase 2: Read-only amendments to merged modules (each with its test)

- [X] T005 [P] A3 (005) `TeacherDirectory.lifecycle` bulk; confirm the joining-date field first and record the fallback if absent
- [X] T005a Test A3: status, effective date and joining date for 3 Teachers, unknown ids absent, fixed query count
- [X] T006 [P] A4 (026) `PerformanceSummary.byTeacher`
- [X] T006a Test A4: per-Teacher counts equal the 026 Teacher screen; sum over Teachers of a Manager equals `byManager`
- [X] T007 [P] A5 (027) `TeacherMovementSource.movementsBetween` in `teacher.api`, implemented in `transfer`
- [X] T007a Test A5: an Approved and an Applied transfer and a replacement counted in their month only; Draft and Rejected excluded
- [ ] T008 [P] A9 (015) `ExpenseQueries.claimedBy`
- [ ] T008a Test A9: totals per claimant equal My Expenses; Teachers with no claims absent
- [ ] T009 [P] A10 (015) `ExpenseQueries.budgetVsActual` delegating to `BudgetQueries.overview`
- [ ] T009a Test A10: rows equal the 015 budget screen; Head office row has a null zone; no budget gives null budget, real actual
- [ ] T010 [P] A1 (016) `recruitment.api.RecruitmentFigures`; the dashboard service calls it
- [ ] T010a Test A1: figures equal the existing dashboard output for a seeded season (regression test unchanged), zone filter works
- [ ] T011 [P] A2 (023) `recruitment.api.DemandFigures`; marketing dashboard calls it
- [ ] T011a Test A2: value equals the marketing dashboard demand
- [ ] T012 [P] A6 (025) `tasks.assigned` and `tasks.completed` on `TaskManagerColumns`
- [ ] T012a Test A6: counts by month for two Managers; existing three columns unchanged; one bulk query
- [ ] T013 [P] A7 (035) `ActivityManagerColumns` bean; check module-dependency test (`activitycalendar -> reporting.api`, no cycle) still passes
- [ ] T013a Test A7: planned, done, missed, rescheduled equal `CalendarSummary.byManager` for the month
- [ ] T014 [P] A8 (023) `marketing.visitsDone`, `marketing.wonInMonth` on `MarketingManagerColumns`
- [ ] T014a Test A8: counts for a seeded month; `marketing.visitsMissed` and `marketing.won` unchanged
- [ ] T015 Dashboard comparison regression: the new columns appear and existing column values are unchanged (028 tests)

## Phase 3: US1 Manager productivity (P1) MVP

- [ ] T016 [US1] `ManagerReportSource` in `reporting/internal/report/`: inject all `ManagerColumns`, add attendance % (`ManagerAttendanceReadApi.months`), training days (`ManagerTrainingDays.attendedDaysOf`), expense claimed (A9); month state from the Manager lock
- [ ] T017 [US1] Scope: Admin and Director all Managers; Zone Manager own row only; Zone and Manager filters outside scope refused
- [ ] T018 [US1] Tests: common columns equal the dashboard comparison table; no planned activity gives NO_DATA; activity source down gives NOT_AVAILABLE for those columns only; Zone Manager sees own row and own expense (Q3); open label
- [ ] T019 [P] [US1] Frontend column definitions and page test

## Phase 4: US2 Expenses (P1)

- [ ] T020 [US2] `ExpensesReportSource` with sections `spend`, `budget`, `pending` (labelled as of today), `costs`; "no Schools won" and "no Teachers" states
- [ ] T021 [US2] Tests: figures equal 015 screens and the dashboard expense tile; Zone Manager and Teacher get 403 and no menu item; Head office row
- [ ] T022 [P] [US2] Frontend

## Phase 5: US3 Teacher (P1)

- [ ] T023 [US3] `TeacherReportSource`: sections `byStatus` and `teachers`; list rule of Q4; Zone and School from the last-day placement (exit date for exited); training days from `rollupsOf`; joined/exited from A3, issues and observations from A4, movements from A5
- [ ] T024 [US3] Scope through `TeacherScopeQueries`; Zone Manager sees own Teachers only; mid-month mover appears once
- [ ] T025 [US3] Tests: status counts add to rows; exited in month listed once and marked; exited earlier not listed; no placement shows "not placed"; counts equal 026 and 027 screens
- [ ] T026 [P] [US3] Frontend

## Phase 6: US4 Recruitment (P2)

- [ ] T027 [US4] `RecruitmentReportSource` with season filter (T004), colleges and totals from A1, induction from `InductionProgress`, ready to deploy from `SupplySource`, demand from A2; selected zero gives NO_DATA ratio; missing providers give NOT_AVAILABLE
- [ ] T028 [US4] Tests: equals the 016 and 023 dashboards; Zone Manager sees exactly the 016 view
- [ ] T029 [P] [US4] Frontend season picker and columns

## Phase 7: US5 Export and polish

- [ ] T030 [US5] Export for all four through the 014a writer and audit; tests: rows equal screen, refusal without Export, one audit row per export, formula neutralisation on names
- [ ] T031 SC-006 check: no 014a framework file changed except T004 (record in the PR); SC-005 source-failure test per report
- [ ] T032 Performance check at 500 Teachers and 50 Managers (first page under 3 s, export under 30 s)
- [ ] T033 Check no migration was added; if one is needed take the next free number above V140 on current main and open branches
- [ ] T034 Update `docs/spec-roadmap.md` 014 row; run the full backend and frontend suites; manual walkthrough from `quickstart.md`

## Dependencies

T001-T004 first; Phase 2 tasks are independent of each other (all [P]) and each test follows its change. US1 needs T012, T013, T014, T008 and T016's inputs; US2 needs T009; US3 needs T005-T007; US4 needs T004, T010, T011. US5 after at least one report. Polish last. 014a must be merged before T004 and before any report source.
