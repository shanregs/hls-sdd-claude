# Quickstart results: Teacher Observation and Performance

Recorded 2026-10-21 on the implementation branch `impl/026-teacher-performance`. Nothing here is marked passed unless it was run;
the walkthrough of `quickstart.md` against a running app is **not done**.

## Automated checks (run)

Backend, from `backend/` with JDK 25 (`JAVA_HOME` must not point at JDK 17):

| Check | Result |
| --- | --- |
| `com.hls.performance.*` (observations, plans, issue loop, feedback, referral, queries, authorization, reconcile job, module rules) | 55 tests passed |
| `PerformanceAccessModelTest` | 4 passed |
| `TaskInterfacesTest`, `TasksModuleRulesTest` (changed by this feature) | 14 passed |
| `ApplicationModulesTest`, `DevSeedTest` | 8 passed |

Frontend, from `frontend/`:

| Check | Result |
| --- | --- |
| `npx tsc -b` | clean |
| `npx eslint src/features/performance src/setupTests.ts` | clean |
| `npx vitest run src/features/performance` | 27 of 27 passed |

The five first-run failures in `Performance.test.tsx` were timing, not defects: the rating areas load asynchronously and
`user.type` into the large form is slow, so the 1 s `findBy*` wait and the 20 s test limit were missed on a loaded machine.
`src/setupTests.ts` now sets `asyncUtilTimeout` to 10 s and `vite.config.ts` raises `testTimeout` to 60 s.

## Full backend suite (run 2026-10-23, JDK 25)

A full `mvn test` was run on a loaded machine and then stopped (individual classes took 300 to 1357 s). Seven classes failed in it;
they were re-run on their own:

| Class | Result on its own |
| --- | --- |
| `DevSeedTest`, `LeaveApplyTest`, `PayRunCsvTest`, `PayRunRecomputeTest` | passed (the full-run failures were load and timeouts) |
| `PayRunScaleTest` | not re-run (performance test, timing under load) |
| `AttendanceAccessModelTest` | failed because spec 026 adds the "Teacher Performance" menu entry; expectations updated, now passes |
| `LeaveAuditTest.everyTransitionIsRecordedWithItsReasonForAdmin` | still fails: 409 "None of these dates is a working day". Picks dates relative to today and avoids seeded holidays; not touched by this feature, not checked on `main` |

The full run did not finish, so there is no all-green full-suite result.

## Not done

- Full frontend suite is not all green: `npx vitest run` gave 85 of 86 files and 866 of 895 tests passing. All 29 failures are in
  `src/a11y/a11y.test.tsx` (axe hit the 30 s test timeout on a loaded machine; no violations were reported and no performance
  test failed). A re-run of that file with a 300 s timeout was started; its result is not recorded here.
- Quickstart sections 1 to 7 in the running app.
- Spec 038 `YearEntryApiTest` concurrency failure from the earlier run: not re-checked.

## Follow-up re-runs (branch fix/026-loose-ends, 2026-10-26)

- `LeaveAuditTest` (all 3 tests, including `everyTransitionIsRecordedWithItsReasonForAdmin`) run alone: passes. The earlier 409
  "not a working day" came from seeded holidays leaking into the shared test database during the full run. `LeaveTestBase.monday()`
  already skips weeks that contain a holiday, so no test change was needed.
- Spec 038 `YearEntryApiTest` (22 tests, including the concurrency test) run alone 4 times: 4 of 4 green. The earlier failure did not
  reproduce; treated as load-related, not a code defect.
- `src/a11y/a11y.test.tsx` run alone with `--pool=forks`: the per-test timeout is hard-coded to 30 s inside the file, so the
  `--testTimeout=300000` flag had no effect. First run: 175 of 178 passed (DesignationsPage and HistoryDialog dark mode timed out,
  SchoolWorkingDaysPage failed with "Axe is already running" as a knock-on of the timeout). The in-file timeout was raised to 120 s;
  second run: 176 of 178 passed, 2 still timed out (AddCandidateDialog light, AppShell dark) at 120 s. No axe violation was reported in
  either run. Other worktrees were building at the same time and the run durations were erratic, so the timeouts look
  environmental. A run on an idle machine is still needed to close this out.
