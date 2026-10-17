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
