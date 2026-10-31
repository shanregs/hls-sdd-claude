# Quickstart results: School Monthly Working Days

Run on 2026-10-13 on branch `feature/037-school-working-days`, rebased on `main` at `631c780`.

## Automated checks

| Check | Result |
|---|---|
| Backend, full suite before the rebase | 1216 tests, 0 failures |
| Backend, full suite after the rebase | 1294 tests, 2 failures in `LeaveApplyTest` and `PositionAssignmentTest` (see below); both pass on their own (20 of 20) |
| This feature's backend tests | all pass (rules, schema, CRUD, API per role, grid, read interface, history, year copy, module rules, access model) |
| Frontend type-check and lint | clean (0 errors) |
| Frontend production build | succeeds |
| Frontend feature tests | 22 of 22 pass |
| Accessibility (light and dark) for the page and its four dialogs | pass |
| Frontend, full suite after the rebase | 702 of 703 pass; the one failure is `marketing.test.tsx` (spec 023), which also fails on `main` before this feature (see below) |

## The failures that are not from this feature

- `LeaveApplyTest.twoSimultaneousOverlappingSubmissionsYieldExactlyOneSuccess` (a timing-based race test) and
  `PositionAssignmentTest.aScheduledMoveLeavesTheCurrentPositionUntilItTakesEffect` failed in the full backend run and
  pass when run alone. The second took 1210 seconds in the full run against about 7 seconds alone and failed with a 401,
  which means the sign-in token expired during a stall of the machine. This is a likely cause, not a proven one.
- `marketing.test.tsx > offers adding a prospect only to a role that may create prospects` (spec 023) failed in the full
  frontend run and passed alone. The full suite on `main` before this feature (`631c780`) fails the same test (670 of
  671), so this feature did not cause it. Cause: the test's mock answered `{}` for the dialog's prospect search, so
  `ActivityDialog` crashed on `prospects.find` whenever that request resolved before the assertion. The follow-up change
  adds that endpoint to the test's mock.

## Walkthrough (quickstart.md steps 1 to 11)

**Not done in a browser.** The behaviour behind each step is covered by automated tests:

| Step | Covered by |
|---|---|
| 1 menu item and current-year grid | `SchoolWorkingDaysAccessModelTest`, `WorkingDaysGridTest`, page test |
| 2 add a figure, calendar count beside it | `SchoolWorkingDaysCrudTest`, page test |
| 3 duplicate refused, offer to edit | `SchoolWorkingDaysCrudTest`, page test |
| 4 inline edit, validation, Cancel, finalised-month notice | page test |
| 5 row history | `WorkingDaysHistoryTest`, `HistoryDialogs.test.tsx` |
| 6 delete with confirmation, Deleted rows view | `SchoolWorkingDaysCrudTest`, `WorkingDaysHistoryTest`, page test |
| 7 copy a year | `YearCopyTest`, `CopyYearDialog.test.tsx` |
| 8 two windows: stale save, row deleted meanwhile | `SchoolWorkingDaysCrudTest` (including two simultaneous creates), page test |
| 9 Director cannot delete | `SchoolWorkingDaysApiTest`, page test |
| 10 Manager, Teacher, System: no menu item, 403 | `SchoolWorkingDaysApiTest`, `SchoolWorkingDaysAccessModelTest`, page test |
| 11 phone width, no horizontal page scroll | **not verified**: jsdom has no layout, so only the grid's own scroll setup is checked |

Entry times (SC-001: under 30 seconds to add one figure; SC-002: under 3 minutes for a year, under 1 minute with the
copy) were **not measured**.

## Read interface and performance

- `SchoolWorkingDays` returns the stored figure or the calendar-derived count, labelled, and issues the same number of
  statements for 1 and 50 Schools (`SchoolWorkingDaysReadApiTest`).
- Grid page with 40 Schools x 12 months (480 rows) answers in under 2 seconds with the same number of statements as a
  year with 2 rows (`WorkingDaysGridTest`). The full SC-008 case (500 Schools x 12 months = 6,000 rows) was run on 2026-10-16 in
  `WorkingDaysGridTest.aFilteredPageOf500SchoolsAnd6000RowsAnswersInUnderTwoSeconds`: a filtered page (year, name search,
  sorted by working days, 25 per page) took 60 to 64 ms over 5 runs after one warm-up, and an unfiltered page of 100
  rows took 165 ms. Both are well under the 2 second target. **SC-008 met.**

## T045 walkthrough re-run (2026-10-27, local Docker stack, API level)

Driven through `/api/v1/school-working-days` as the seeded Admin (`asha.admin`), Director, Manager, Teacher and System accounts.
The browser was **not** used, so steps 8 (two windows, as a screen) and 11 (375 px) and the SC-001/SC-002 entry timings are
**still open** and need a person at a browser.

| Step | Result |
| --- | --- |
| 1 list | Pass. Current-year grid returns rows with `calendarDerived` beside each figure. |
| 2 add | Pass. School One, March 2027, 26: 201, days in month 31, calendar count 27. |
| 3 duplicate | Pass. 409 `DUPLICATE` carrying the existing row (26). |
| 4 edit | Pass. 25.5 accepted; 25.3 and 32 refused with "must be 0 to 31, in half days". Cancel is a screen action, not run. |
| 5 history | Pass. EDIT (26 to 25.5) then CREATE, newest first. |
| 6 delete | Pass. 204. Deleted rows for School One 2027 lists March, last figure 25.5, by Asha Admin with the time. |
| 7 copy | Pass. 2026 to 2027: 11 months created, January skipped as `EXISTS`. A 29-day February (leap year 2028) copied to 2027 is skipped as `EXCEEDS_DAYS` (28 days). |
| 8 stale and deleted (API) | Pass. Save with an old version: 409 `STALE` with the current figure. Edit of a deleted row: 404 `ROW_DELETED`; the row was not recreated and the history by key shows DELETE, EDIT, CREATE. |
| 9 Director | Pass. List, create, edit and copy work; delete is 403. |
| 10 Manager, Teacher, System | Pass. 403 on the list endpoint for each. |

Backend checks re-run on 2026-10-27 with `mvn -o` (JDK 25): `SchoolWorkingDaysApiTest` 6, `SchoolWorkingDaysCrudTest` 9,
`SchoolWorkingDaysReadApiTest` 10, `WorkingDaysGridTest` 8 (includes the 500 Schools x 12 months case), `WorkingDaysHistoryTest` 6,
`WorkingDaysModuleRulesTest` 5, `WorkingDaysRulesTest` 7, `WorkingDaysSchemaTest` 3, `YearCopyTest` 5, `YearEntryApiTest` 22,
`SchoolWorkingDaysAccessModelTest` 4, `ApplicationModulesTest` 1: all passed, 0 failures.

Demo records were added to the local database (School One 2027, School Two 2027 and 2028); reset with
`docker compose -f docker-compose.local.yml down -v`.

## Browser run (2026-10-29, Selenium, headless Chrome 156, local Docker stack)

`e2e/test_school_working_days.py` (run `pytest -v` in `e2e/`): 6 of 6 pass.

| Step | Result |
| --- | --- |
| 4 edit, 25.3 and 32 refused ("Enter 0 to 31, in half days."), Cancel leaves the figure | Pass |
| 8 two windows: second save gets "Changed by someone else" with the current figure; edit after a delete in the other window gets "deleted by someone else", the row leaves the grid and is not recreated (also after reload) | Pass |
| 9 Director: Add works, no Delete button | Pass |
| 10 Manager: not-authorized page | Pass |
| 11 375 px wide: no horizontal page scroll | Pass |
| SC-001 add one figure | 2.8 to 3.3 s scripted (a floor, not a person's time) |

SC-002 was not measured by a person; accepted on 2026-10-29 on the scripted run (decision: user). T045 ticked.
