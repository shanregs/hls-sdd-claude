# Quickstart Results: Manager Attendance (spec 032)

Run on 2026-10-13 from the `feature/032-polish` branch, after the stories US1 to US7 were merged (#44 to #55).

How the walkthrough was done: every step of [quickstart.md](./quickstart.md) was checked against the automated tests that
run it through the real HTTP API with a real database (Testcontainers) and the real UI components with the API mocked.
There was no manual browser run (see "Not done" at the end). The dev seed was checked too: `DevSeedTest` signs in as Manoj
with `hls.seed.demo-data=true` and reads his seeded month.

## Step by step

| Step | Result | Verified by |
| --- | --- | --- |
| 1.1 Check in as Office with a location | Pass | `CheckInApiTest.checkInRecordsTheServerTimeLocationAndDayType`; UI: `ManagerCheckIn.test.tsx` (sends the day type and the location) |
| 1.2 Second check-in, and a blocked location, are refused with the reason | Pass | `CheckInApiTest.aSecondCheckInIsRefusedWithAConflict`, `aLocationIsRequiredAndMustBeInRange`; UI: the location-blocked message in `ManagerCheckIn.test.tsx` |
| 1.3 Check out; a day with no check-out is flagged and still counts as worked | Pass | `CheckInApiTest.checkOutNeedsACheckInAndOnlyHappensOnce`, `MissingCheckOutTest.aDayWithACheckInAndNoCheckOutIsFlaggedAfterTheDayEnds`, `ManagerAttendanceReadApiTest` (no check-out is worked) |
| 1.4 Admin or Director completes the missing check-out with a reason; the history shows both | Pass | `MissingCheckOutTest.anAdminAndADirectorCompleteItToo`, `CorrectionApiTest` (audit of the completion), the day dialog history in `ManagerGrid.test.tsx` |
| 2.1 Field day with no purpose refused, with a purpose accepted and shown in the grid | Pass | `FieldDayTest` (9 tests), `ManagerGrid.test.tsx` |
| 2.2 Photo shown to the Manager, Admin and Director; a Teacher cannot open it | Pass | `CheckInPhotoTest.aJpgIsAttachedAndOpenedByTheManagerAnAdminAndADirectorOnly` |
| 3.1 Two days of casual leave requested, approved, shown as paid Leave | Pass | `ManagerLeaveRequestApiTest`, `ManagerLeaveDecisionTest.approvalWritesPaidLeaveOntoTheWorkingDaysOnlyAndTellsTheManager` |
| 3.2 Loss-of-Pay day is unpaid; a half day with the other half worked | Pass | `ManagerLeaveDecisionTest.aLossOfPayRequestMakesUnpaidLeaveDays`, `halfDaysAtEitherEndAreWrittenAsHalfLeave`, `ManagerAttendanceReadApiTest.theMonthMatchesTheHandComputedFigures` |
| 3.3 Nobody approves their own request, including a Manager who is also an Admin | Pass | `ManagerLeaveDecisionTest.aManagerNeverDecidesAndNobodyDecidesTheirOwnRequest`, `RegularizationApiTest` (the same rule for forgotten check-ins) |
| 3.4 Check-in on a full-day leave refused; after a revoke it is allowed | Pass | `CheckInApiTest.fullDayLeaveRefusesACheckInAndHalfDayLeaveAcceptsOne`, `ManagerLeaveAttendanceTest.aFullDayOfLeaveRefusesACheckInAndAHalfDayAcceptsOne`, `ManagerLeaveDecisionTest.revokingNeedsAReasonAndRemovesTheDays` |
| 4 Missed check-in: Manager, Director and Admin told once; a later check-in marks the day late and removes it from the list | Pass | `MissedCheckInJobTest` (9 tests, including twice-run, exclusions, the cut-off change and a schedule of at most two minutes), `MissedListApiTest` |
| 5.1 Grid filtered by Zone; a correction with a reason keeps the original and tells the Manager | Pass | `ManagerGridTest`, `CorrectionApiTest` (history, notification, audit) |
| 5.2 Forgotten check-in requested, approved and marked "added later"; a locked month refuses it | Pass | `RegularizationApiTest` |
| 5.3 A Manager with no joining date cannot check in or request leave and shows "no joining date" | Pass | `CheckInApiTest.aManagerWithNoJoiningDateIsRefusedWithTheReason`, `ManagerLeaveRequestApiTest.aDateBeforeTheJoiningDateAndNoJoiningDateAreRefused`, `MyManagerMonthTest` |
| 6 Lock refused while a request is pending and lists it; lock all; writers refused; reopen with a reason | Pass | `ManagerMonthLockTest` (11 tests), `ManagerLeavePendingRequestsTest`; UI: the lock tests in `ManagerGrid.test.tsx` |
| 7 Payroll reads the month; `PayRules` answers a Manager's unpaid days and total loss of pay; an Admin's or Director's is an explicit "none" | Pass | `ManagerAttendanceReadApiTest` (9), `PayRulesTest`, `PayRulesCalendarTest` |
| 8 Access: a Manager reaches only their own data; Teacher and System get 403 everywhere; Admin and Director succeed | Pass | `ManagerAttendanceAuthorizationTest`, `MyManagerAttendanceAuthorizationTest`, `ManagerLeaveAuthorizationTest`, `MissedListApiTest`, `ManagerAttendanceSettingsTest`, the lock test for who may lock; UI: "is refused for a role without the page" in each page's test |

## Suites

- Module boundaries (T069): `ManagerAttendanceModuleRulesTest`, `AttendanceModuleRulesTest`, `LeaveModuleRulesTest`,
  `PayrollModuleRulesTest` and `ApplicationModulesTest` (Spring Modulith) pass with no change to the rules. The existing
  attendance, leave and payroll tests pass unchanged except the two payroll tests that asserted the old
  "Manager attendance is not recorded yet" answer (replaced in US7).
- Dev seed (T066): `ManagerAttendanceDevSeeder` and `ManagerLeaveDevSeeder` give Manoj eight past working days (normal,
  no check-out, Field with a purpose, approved leave, absence, late, normal, normal) and a pending request; `DevSeedTest`
  covers it.
- Postman (T067): the folder "032 Manager Attendance" has 32 requests, with the refusals in each description. All 36
  files parse as YAML; the requests were not run from Postman.
- Full backend suite: 1328 tests. The final run had one failure, `TaskAuditAndNotificationTest`, which got a 401 after the machine slept for about 50 minutes during the test (elapsed 2959 s, so the token had expired); it passes alone (3 of 3). An earlier run failed the same way in `AttendanceGridControllerTest` (elapsed 3528 s), which also passes alone. The same earlier run showed `ManagerControllerTest.candidates...` failing because `ManagerService.candidates()` only looked at the first 500 Manager users; fixed in this change by reading every page, and it passes in the full run.
- Full frontend suite: 722 of 723. The one failure is `marketing.test.tsx > offers adding a prospect only to a role that may create prospects`, which also failed in the earlier PRs; it passes alone and is not related to this spec. `tsc -b` and eslint have no errors.

## Accessibility pass (T070)

Checked by axe (WCAG 2.0 A and AA and 2.2 AA) in light and dark themes on the five screens (Check-in, My Month, Manager
Attendance, My Leave, Manager Leave), on the day dialog with its correction error showing, and on the location-blocked
state. Tested in code as well: every form field has a label (axe `label` rule), validation errors use `role="alert"`, the
keyboard order of the month request form and the correction form follows the reading order, and the form rows that
placed two fields side by side (dates, times, the check-in times) now stack below the small breakpoint so a 320 px phone
does not scroll sideways. The month grid scrolls inside its own container, not the page.

## Financial review of the unpaid-day rules (T072)

Reviewed against research.md section 3, decisions D7, D12 and D15 of the roadmap, Principle IX and Development Workflow
item 5. This is the implementer's review; it still needs a sign-off from whoever owns payroll before release.

- **Formula (D12).** Loss of pay is the person's salary divided by the month's working days, per unpaid day, each day at
  the salary in effect that day, summed and rounded once by the policy of the month's last day. A Manager's unpaid days
  are the dates in their employment with no check-in and no approved leave, and Loss-of-Pay leave; a half day counts as the
  policy's half-day fraction. Tests cover a full day, a half day, Loss-of-Pay leave, a mid-month raise, an exit in the
  middle of the month (unpaid only until the exit, valued over the whole month's working days) and a month with nothing
  unpaid. An Admin or Director has no unpaid days by rule, which is a stated answer.
- **Never silently zero.** A Manager with no joining date, or an unknown Manager, gets `Unavailable` and `Cannot` with a
  reason, never "none". A Manager with an unpaid day and no recorded salary gets `Cannot`.
- **Location and photo (D15).** Check-in and check-out require a location in range; the photo is optional. Locations and
  photos are not in anything payroll reads.
- **D7.** D7 is about Teachers marking their own days. For Managers the equivalent is the device check-in; any later change
  is a correction or an approved forgotten check-in by an Admin or Director, with a required reason and the original kept
  in the history, never a silent overwrite (Development Workflow item 6).
- **Audit trail.** Check-ins, corrections, completions, forgotten check-ins, leave decisions, the lock, the reopen with its
  reason and the check-in times are all audited (`MANAGER_DAY`, `MANAGER_REGULARIZATION`, `MANAGER_LEAVE`,
  `MANAGER_MONTH`, `MANAGER_ATTENDANCE_SETTING`) and visible in the Change History to those who may see them.
- **Module boundaries.** Payroll reads Manager attendance only through `attendance.api.ManagerAttendanceReadApi`;
  `attendance` does not depend on `leave`.

Two points for 013b (the payroll run), found in this review and not changed here:

1. `PayRules` does not look at whether the month is locked, and for a month still in progress the days that are not over
   yet are pending, not unpaid. A run must therefore use only months that have ended and are locked
   (`ManagerMonth.Found.locked`), or it will under-deduct.
2. After a lock, the frozen unpaid dates are fixed but the value of a day still divides by the live calendar's working days
   of the month. A holiday added to the calendar after the lock would change the value of a frozen day. This is the same
   for Teachers today; the run should either refuse a calendar change in a locked month or freeze the divisor too.

## Not done

- A manual browser walkthrough (location prompt, camera, real phone widths, screen reader) and a timing check of SC-001
  (check-in under 30 seconds, check-out under 20). The requests themselves answer in well under a second in the tests,
  but the time a person takes is not measured.
- Colour contrast was checked by axe in jsdom, which cannot compute contrast for every element; the colours come from the
  shared theme tokens already checked in the earlier specs.
- The Postman requests were not run against a server.
