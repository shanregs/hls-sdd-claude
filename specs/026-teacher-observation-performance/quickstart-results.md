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

## Not done

- **Full backend suite.** A full `mvn test` was started and stopped at 76 classes and 465 tests (the database connection pool began
  refusing connections, so the run was stopped by hand). One failure was seen before that, in spec 038:
  `YearEntryApiTest.twoSimultaneousSavesOfTheSameNewMonthMakeOneRowAndNoServerError` (expected 200, got 409). It is a concurrency
  test outside this feature; not yet checked on `main`.
- **Full frontend suite** (`npx vitest run` over every feature) was not completed.
- **Quickstart sections 1 to 7** (plan, record, correction, Needs improvement loop, Principal feedback, referral, lists, automatic
  checks) were not walked through in the running app.
