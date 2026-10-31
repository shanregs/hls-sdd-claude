# Quickstart Results: Payroll Run (spec 013b)

Run on 2026-10-19 from the `impl/013b-polish` branch, after the stories US1 to US8 were merged (#61, #62, #67, #68, #70, #81, #82).

How the walkthrough was done: each scenario of [quickstart.md](./quickstart.md) was checked against the automated tests that run it
through the real HTTP API with a real database (Testcontainers) and the real UI components with the API mocked. There was no
manual browser run (see "Not done").

## Suites (T063)

- Frontend (`npx vitest run`): 85 files, 868 tests, all passed (386 s).
- Backend (`mvn test`, Docker running): 1,542 tests. The first attempt had 218 errors because Docker Desktop was not running
  (`Could not find a valid Docker environment`); that is an environment fault, not a failure. The second, full attempt had 5
  failures outside payroll: `LeaveAuditTest.everyTransitionIsRecordedWithItsReasonForAdmin`,
  `RequirementRaisingTest.theTeacherMarkingThemselvesAbsentRaisesItToo` and three tests of `TeacherFirstAssignedTest`
  (Spring context failed to load). Run alone, those three classes pass. They were not investigated further; the cause is likely
  shared database state or load across classes, and they should be watched in CI.
- Every payroll run class passed in the full run.

## Step by step

| # | Result | Verified by |
| - | ------ | ----------- |
| 1 Start a run for last month; one line per person; totals | Pass | `PayRunLifecycleTest.startForAnEndedMonthCreatesAQueuedDraftThatFinishesWithItsProgressAtTheTotal`, `PayRunComputationTest.aTeacherAManagerAndADirectorGetTheHandComputedLines`, `theCountsAndTotalsEqualTheSumsOfTheLines`; UI: `PayrollRuns.test.tsx`, `PayrollRun.test.tsx` |
| 2 Current month refused | Pass | `PayRunLifecycleTest.startForTheCurrentMonthAndForAFutureMonthIsRefused` |
| 3 Second run for the month refused | Pass | `PayRunLifecycleTest.aSecondPrimaryRunForTheMonthIsRefusedWhileOneIsNotCancelled`, `twoSimultaneousStartsCreateExactlyOneRun`, `PayRunConcurrencyTest` |
| 4 Person with no salary under "Cannot be paid" | Pass | `PayRunComputationTest.aPersonWithNoSalaryOnAPayableDateIsListedApartWithTheDateAndNeverShownWithZero`, `aFailureForOnePersonBecomesACannotPayLine...` |
| 5 Lock the month, compute again, only that line changes | Pass | `PayRunRecomputeTest.lockingTheMissingMonthAndComputingAgainAddsThatLineAndLeavesEveryOtherRowUntouched` |
| 6 A line's working reproduces the figures | Pass (automated) | `LineCalculatorTest`, `PayRulesEarnedPayTest`; UI: the working dialog in `PayrollRun.test.tsx`. Reading it by a human (SC-005) not timed |
| 7 Submitter cannot approve | Pass | `PayRunApprovalTest.aUserHoldingBothRolesWhoSubmittedCannotApproveTheirOwnSubmission`, `PayRunSchemaTest.theApproverCannotBeTheSubmitter` |
| 8 Return with reason, resubmit, approve; locked, payslips exist | Pass | `PayRunApprovalTest.returnNeedsAReason...`, `approveRecordsWhoAndWhenIssuesPayslipsAndRecordsWhoIsPaid`, `theNewSubmitterIsRecordedAfterAReturn`; `PayrollNotificationTest` |
| 9 Approved run cannot be recomputed, cancelled, deleted or changed | Pass | `PayRunApprovalTest.everyAttemptToChangeAnApprovedRunIsRefused`, `PayRunLifecycleTest.anApprovedRunCannotBeComputedAgainOrCancelled`, `PayRunImmutabilityTriggerTest` |
| 10 Later changes leave figures, payslips and export unchanged | Pass | `PayRunFreezingTest` (3 tests), `PayslipTest.aSecondDownloadIsByteIdenticalAfterTheCalendarChanges`, `PayRunCsvTest.anApprovedRunsFileIsIdenticalBeforeAndAfterACalendarChange` |
| 11 +₹2,000 Correction is its own item, in the net, Applied after approval | Pass | `PayAdjustmentTest.aRunTakesTheWaitingAdjustmentIntoTheLineAndTheNetIncludesIt`, `approvalAppliesTheAdjustmentOnceAndItCanNeitherBeWithdrawnNorTakenAgain`; UI: `Adjustments.test.tsx` |
| 12 Incentive refused (reserved) | Pass | `PayAdjustmentTest.theReservedKindsAreRefusedNamingTheSpecThatWillCreateThem` |
| 13 Supplementary run holds only the left-out person; paid once | Pass | `SupplementaryRunTest.itContainsOnlyThePeopleNotYetPaid...`, `itGoesThroughReviewApprovalFreezingAndPayslipsAndEveryPersonIsPaidInExactlyOneRun`, `PaidMonthKeyTest` |
| 14 Supplementary refused while primary is a draft, one is open, or nobody is owed | Pass | `SupplementaryRunTest.aSupplementaryRunIsRefusedWhileThePrimaryIsNotApproved`, `onlyOneSupplementaryRunCanBeOpen...`, `aSupplementaryRunIsRefusedWhenNobodyIsOwed` |
| 15 Payslip and zip; same after calendar change; none for draft or cannot-pay | Pass | `PayslipTest` (8 tests), `PdfSmokeTest`, `PayslipRendererTest`; UI: `PayrollRunPayslips.test.tsx` |
| 16 Manager and Teacher get nothing | Pass | `PayslipTest.aManagerTeacherOrSystemUserGets403AndNoBytes`, `PayrollAuthorizationTest.everyEndpointFollowsTheMatrixForEveryRole` |
| 17 Mark two lines paid; correct one; history kept; amounts unchanged | Pass | `PayPaymentTest.aCorrectionAddsAnEntryKeepsTheOldOnesAndNeverChangesTheAmounts`, `theRunsPaidAndUnpaidFiguresAreTheSumsOfTheLatestEntries`; UI: `PayrollRunPayment.test.tsx` |
| 18 Payment in a draft refused | Pass | `PayPaymentTest.aDraftAwaitingRunAndACannotPayLineRefusePayment` |
| 19 Export of draft and approved run; audited | Pass | `PayRunCsvTest` (7 tests); UI: `PayrollRunExport.test.tsx` |
| 20 1,000 people in under two minutes (SC-002) | Pass | `PayRunScaleTest` (passes alone; can exceed two minutes when the machine is under load) |

Also covered: audit of every write (`PayrollAuditTest`, `PayrollAuditMappingTest`), module boundaries
(`PayrollRunModuleRulesTest`), the database rules (`PayRunSchemaTest`, `PayrollAppendOnlyTest`).

## Financial review (Principle IX)

The owner confirmed all seven points on 2026-10-24 (given in a working session, recorded here by the assistant). Fixtures are the hand-computed ones in the named tests.

| # | Point | Fixture that demonstrates it | Owner confirmation |
| - | ----- | ---------------------------- | ------------------ |
| 1 | Earned-pay formula (each payable day at its own day's salary over working days; constant salary earns exactly the salary) | `PayRulesEarnedPayTest`, `LineCalculatorTest`, `PayRunComputationTest.aTeacherAManagerAndADirectorGetTheHandComputedLines` | Confirmed 2026-10-24 |
| 2 | Rounding: earned pay and deduction each rounded once by the policy; with `UP`/`DOWN` the same direction applies to both, so one side can gain up to one rupee each | `PayRulesEarnedPayTest`, `LineCalculatorTest` | Confirmed 2026-10-24; decision: keep as built (the policy rounds earned pay and deduction once each) |
| 3 | Teacher who changed School in the month uses each date's own School divisor | `PayRulesEarnedPayTest` | Confirmed 2026-10-24 |
| 4 | Manager lock versus calendar: divide by the calendar as computed, warn when the lock-time count differs | `PayRunComputationTest` (Manager cases) | Confirmed 2026-10-24; decision: warn only, no block |
| 5 | Negative net blocks submission | `PayAdjustmentTest.aNegativeNetAfterAnAdjustmentBlocksSubmit`, `PayRunApprovalTest.submitWithANegativeNetLineIsRefusedNamingTheLine` | Confirmed 2026-10-24 |
| 6 | Supplementary run uses the month's policy and divisor as stored at its own computation | `SupplementaryRunTest` | Confirmed 2026-10-24 |
| 7 | School working days (spec 037): payroll uses the calendar-derived days, not the stored School figure | none (a decision, not a behaviour) | Confirmed 2026-10-24; decision: payroll keeps the calendar-derived days; the stored School figure is not adopted |

## Browser walkthrough (2026-10-29)

`e2e/test_payroll_run.py`, run against the local stack with headless Chrome: 3 passed in 30 s.

- The current month is not offered; starting a second run for a month that has one is refused and the list stays (scenarios 2, 3).
- Admin starts a run for July 2026: it finishes computing, the six totals show, a line's working opens, Export CSV is offered on a draft (1, 6, 19).
- After Submit the Admin has no Approve or Cancel button (7). The Director returns it with a reason, the Admin sees the reason, submits again, and the Director is offered Approve (8).
- A Manager sees no pay runs table, no start form and no rupee figures (16).
- By default the run is returned and cancelled at the end so nothing final is left in the database. `APPROVE=1` also approves it and checks that payslips are offered and Compute again and Cancel run are gone; that cannot be undone, so it was not run.

## Not done

- Browser walkthrough: done on 2026-10-29 as a Selenium e2e test (see "Browser walkthrough"). It covers scenarios 1, 2, 3, 6, 7, 8, 16 and the export button of 19. Not covered in the browser: 4, 5, 9 to 15, 17, 18 and the rest of 19 (the automated API and component tests above carry them), and approval itself unless run with `APPROVE=1`.
- No screen-reader check (axe checks in both themes are automated only).
- No human timing of SC-005 (a reviewer reproducing a net from the working screen).
- The Postman collection (`postman/HLS API/013b Payroll Run/`) was not run.
- The owner confirmed the seven financial points above (2026-10-24). The manual browser, screen-reader and SC-005 checks listed here are still open before release.
- Five unrelated backend tests failed in the full run and passed alone (see Suites).
