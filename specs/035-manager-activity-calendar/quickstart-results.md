# Quickstart results: Manager Activity Calendar

Recorded 2026-10-24 on `impl/035-calendar-mvp` after merging `origin/main` (specs 015, 027, 028 included). JDK 25 for Maven.
Nothing is marked passed unless it was run. The browser walkthrough by a person is **not done**.

## Merge notes (T057)

- Conflicts were in the constitution, `PermissionEligibility`, `AttendanceAccessModelTest`, `TasksModuleRulesTest` and `App.tsx`;
  both sides were kept (Expenses and My Expenses beside Activity Calendar).
- Migrations: V52, V53, V60, V70 (035), V80 (027), V90 (015). V70 is unique and ordering is valid.
- Constitution: 027 is 2.5.8, 015 is 2.5.9, this feature is 2.5.10. No renumbering was needed.
- Bean clash with the Dashboard resolved by naming ours `CalendarZoneResolver`; `SchoolDirectory.allZones()` now comes from main.

## Full suites

| Suite | Result |
| --- | --- |
| Backend `mvn test` (256 test classes, 1405 tests) | 3 failures, none caused by 035 (below) |
| Frontend `vitest run` (104 files, 1009 tests, includes `a11y.test.tsx`) | all passed |
| Frontend `npm run build` | passed |
| Frontend `tsc -b` | clean |
| Frontend `eslint src` | 1 error in `features/dashboard/InboxPanel.tsx` (spec 028, set state in an effect), plus 6 warnings; none in calendar code |

Backend failures:

| Test | Cause | Pre-existing? |
| --- | --- | --- |
| `LeaveApplyTest.twoSimultaneousOverlappingSubmissionsYieldExactlyOneSuccess` | returns [500, 201] instead of [201, 409] | Yes: fails the same on the reference checkout of main (`hls-main-check`, spec 026 commit) |
| `LeaveAuditTest.everyTransitionIsRecordedWithItsReasonForAdmin` | date picking, same as recorded in spec 026 | Yes: fails the same on the reference checkout |
| `DevSeedTest.teacherPerformanceDemoDataHasObservationsAnIssueAndAPlan` | empty list in the full run only | Not a defect: passes when `DevSeedTest` is run alone on this branch (8 of 8) and on the reference checkout; order or shared-data flake in the full run |

The roughly 40 `a11y.test.tsx` failures seen earlier did not recur: the whole file passed in the full run. They were most likely
timeouts under load; nothing in 035 was changed for them.

## Quickstart sections, by automated evidence

| Section | Evidence | Result |
| --- | --- | --- |
| 1 and 2 Month and Director view | `CalendarMonthIntegrationTest` (3), `StatusRulesTest` (7), `SourceAdaptersTest` (4), feed tests `DriveActivitiesFeedTest`, `MarketingActivitiesFeedTest`, `TaskActivitiesFeedTest` | passed |
| 3 Manager view and refusals | `CalendarAccessTest` (5), `CalendarScopeMatrixTest`, `CalendarZoneResolverTest` (5), `ActivityCalendarAccessModelTest` (5) | passed |
| 4 Needs attention | `NeedsAttentionTest` (2) | passed |
| 5 Filters and layout | `CalendarFilterTest` (4); frontend calendar page tests | passed (the "persist month and filters on refresh" step is in the frontend tests only) |
| 6 Training absent, source failure | `SourceResilienceTest` (4) | passed |
| 7 Read-only and speed | `CalendarReadOnlyTest` (2, GET only and 405 on writes), `ActivityCalendarModuleRulesTest` (5), `CalendarPerformanceTest` (500 items, 20 Managers) | passed |
| 8 Regression of amended feeds | `DrivePlannedBetweenUnchangedTest`, `MarketingPlannedBetweenUnchangedTest`, `TaskPlannedBetweenUnchangedTest`, `FieldDayTest` | passed |

## Needs a person

- Sign in as Admin, Director, Manager and Teacher in the running web app and look at the calendar (grid, list, summary,
  "more" overflow, dark mode, narrow width).
- Complete a visit and move a drive date in their own screens and confirm the calendar shows both on reload (section 7, last item).
- Screen-reader pass over the calendar page.
