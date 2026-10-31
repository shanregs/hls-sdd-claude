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

## Idle-machine a11y re-run (2026-10-27)

`npx vitest run a11y` from `frontend/` on an idle machine: 178 of 178 passed in 324 s. This closes the a11y item above.

## Quickstart walkthrough (2026-10-27, local stack, API level)

Run against `docker-compose.local.yml` (backend with demo data, V60 applied), driving `/api/v1/teacher-performance` as the seeded
Director (9800000002), Manager (9800000003) and Teacher (9800000004) accounts. The browser screens were **not** walked; the
frontend side is covered by the vitest suites. The demo seeder has one Manager (Manoj) and no second Manager, so "Manager B"
checks used a Teacher outside Manoj's scope (Lakshmi Priya, School Two, no Manager).

| Section | Result |
| --- | --- |
| 1 Plan an observation | Pass. Director plan: 201, one Observation task and one `OBSERVATION_PLANNED` notification. Re-plan: 200, same plan, date and reason changed, still one task and one notification. Teacher in training: 400 with the reason. Teacher outside the Manager's scope: 404. A reason of Other with no text: 400. A Teacher on leave was not tried. |
| 2 Record and submit | Pass. Average 4.5, Good, `permissionRecorded=false`, Observation task Completed. Correction with a reason: version 2 current, version 1 readable by the Director through `?versions=true`. Not held (Principal declined): no ratings, flagged in the Director's `principalDeclined` list, the plan stays open and a new plan is accepted. Teacher account: 403 on plans, observations, issues and summary. Manager B reading Manager A's record: **not run** (no second Manager). |
| 3 Needs improvement loop | Pass. Needs improvement opens an Open issue; a corrective action gives Under correction, round 1, and a follow-up task due the chosen date; a further Needs improvement then a second action gives round 2; Good gives Resolved - improved. Director cancel of an Open issue: Cancelled, kept in the list. |
| 4 Principal feedback | Pass. Action required returns `offerIssue=true`; opening an issue from it gives an Open issue; planning from it updated the Teacher's existing open plan (200, `created=false`). Withdraw with a reason and the record version: Withdrawn and still listed. |
| 5 Referral | Pass. Unsuitable opens an Open issue with refer allowed; a 9-character reason is refused (400, 20 or more needed); a valid referral gives Referred and a `REFERRAL_RECORDED` notification to the Director. The "close as Resolved - continue" alternative was run on a second issue (Resolved, Continue). The 027 interface path (report a decision, Resolved - replaced) is integration-test only and was not run here. |
| 6 Lists and Teacher page | Pass. Filters by result, School, Manager and status return consistent counts; the Teacher page lists observations, feedback and issues, latest first, with the open plan. Summary for October: 8 done, 2 planned, 0 overdue, 2 open issues, 1 referral, 2 resolved, matching the lists. |
| 7 Automatic checks | Not walked. Reconcile job (reassign on Manager change, Resolved - exited) and the 2,000-observation load check were not run because they change shared demo data; the reconcile job is covered by the `com.hls.performance.*` tests above. |

Notes: the walkthrough created demo records on the local database (Karthik Raja's observations and issues, one Tara feedback
withdrawn); reset with `docker compose -f docker-compose.local.yml down -v`.

## Browser run (2026-10-29, Selenium, headless Chrome 156, local Docker stack)

`e2e/test_teacher_performance.py` (run `python -m pytest test_teacher_performance.py -v` in `e2e/`): 4 of 4 pass.

| Section | Result |
| --- | --- |
| 1 Director plans an observation for Karthik Raja; planning the same Teacher again leaves one plan with the new date and reason | Pass |
| 1 the Manager sees the plan in Planned | Pass |
| 2 Manager opens the Teacher page, starts an observation, rates every area 2, shows Average 2, chooses Needs improvement, submits: "Permission not recorded" shown | Pass |
| 2 the submitted observation completes the task (Planned list empty for the Teacher) | Pass |
| 3 Needs improvement opens an Open issue at once (Issues list) | Pass |
| 6 Teacher account: not-authorized page | Pass |
| 375 px: Observations, Planned, Issues and Principal feedback lists have no horizontal page scroll | Pass |

Not run in the browser: Manager B scope (one Manager in the demo seed), the correction/version, Not held, corrective action and referral screens, feedback (section 4), the 027 path, and section 7. T056 stays unticked for those and the full-suite runs.
The run leaves demo records (a Needs improvement observation and an Open issue for Karthik Raja); reset with `docker compose -f docker-compose.local.yml down -v`.
