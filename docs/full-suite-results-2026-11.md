# Full-suite results, 2026-11 (specs 005a, 012, 014c, 016, 023, 026)

Run on 2026-11-08 from a worktree of origin/main (8357a66), Docker testcontainers, JDK 25.0.4.1.
Backend: one class at a time (`mvn -q test -Dtest=<class>`), 114 classes, result read from
`target/surefire-reports/TEST-<class>.xml` (not from the exit code). Frontend: `npx vitest run src/features/<folder>` per folder.
Another agent ran Maven concurrently, so the machine was loaded.

This is a run of the packages each spec owns, not of the whole suite (all modules). The tasks that say
"full backend and frontend suite" therefore stay unticked; the counts below are recorded in each task note.

## Backend

| Spec | Packages / classes | Classes | Tests | Result |
|---|---|---|---|---|
| 005a designations | `com.hls.designation` | 3 | 11 | pass (1 load failure, passed alone, see below) |
| 012 school contracts | `com.hls.schoolbilling` (all, incl. MOU classes), `identity.SchoolContractsApproveGrantTest`, `teacher.TeacherPlacementServiceTest`, `teacher.TeacherEmploymentApiTest` | 36 | 208 | pass |
| 014c reports | `com.hls.expenses` (+internal), `com.hls.peoplereports` | 30 | 140 | pass (1 load failure, passed alone) |
| 016 campus recruitment | `com.hls.recruitment` (excl. marketing) | 14 | 62 | pass |
| 023 marketing and MOU | `com.hls.recruitment.marketing`, MOU classes in `schoolbilling` (counted in 012 row) | 22 | 73 | pass |
| 026 performance | `com.hls.performance` | 9 | 57 | pass after test-rule fix (see below) |
| Total | | 114 | 551 | 0 failing after reruns |

## Frontend (vitest, per feature folder)

| Spec | Folder | Files | Tests | Result |
|---|---|---|---|---|
| 005a | designations | 1 | 7 | pass |
| 012, 023 | schoolbilling | 7 | 54 | pass |
| 016 | recruitment | 1 | 12 | pass |
| 023 | marketing | 1 | 20 | pass (the marketing.test.tsx timeout seen under load before did not recur) |
| 026 | performance | 1 | 27 | pass |
| 014c | expenses | 5 | 49 | pass |
| 014c | reports | 1 | 18 | pass |
| Total | | 17 | 187 | pass |

## Failures and causes

1. `designation.EmployeeIdsAndInterfacesTest` (first pass 1 of 3 failed: statement count 3, expected 1). Rerun alone: 3 of 3 pass.
   Statement-count test under load with a concurrent Maven run; not reproduced. Worth watching: if it recurs on an idle machine it is an N+1.
2. `peoplereports.PeopleReportsPerformanceTest` (first pass: expenses statements 7, expected 6). Rerun alone: 1 of 1 pass. Same kind (statement count, not reproduced).
3. `performance.PerformanceModuleRulesTest.onlyNotificationAndThePerformanceModuleUseThePerformanceApi` failed 12 times, deterministic
   (also on rerun alone). Cause: the ArchUnit rule allowed only `performance` and `notification` to use `performance.api`, but spec 014
   reports (`peoplereports.internal.TeacherReportSource`, `schoolreports.internal.SchoolReportSource`) legitimately read `PerformanceSummary`.
   Not a product bug, a stale rule. Fixed in a separate commit (rule now also allows `peoplereports..` and `schoolreports..`); class now 5 of 5.

No product bugs found. No date-boundary failures.

## Task decisions

All six tasks stay unticked: each asks for the whole backend and frontend suite (modules not owned by the spec were not run),
and 026 T056 and 014c T034 also need a browser walkthrough. Counts are recorded in each task note.
