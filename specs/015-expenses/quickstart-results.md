# Quickstart results: Expenses

Run on 2026-10-23 on the `impl/015-expenses` branch. Everything below was run as automated tests against a real PostgreSQL
(Testcontainers) and, for the screens, against mocked API responses in jsdom. **No manual browser walkthrough was done.**

## Scenarios and the tests that cover them

| # | Scenario | Automated test |
| --- | --- | --- |
| 1 | Raise and submit, another Manager gets "not found", reviewers notified | `ClaimLifecycleApiTest`, `MyExpensesApiTest.anotherManagerCannotReadAClaimOrItsBill`, `ClaimLifecycleApiTest.aClaimantNeverDecides...`; screens: `MyExpenses.test.tsx` |
| 2 | Validation (amount, dates, Other, bill, file type, size, sixth bill) | `ClaimValidationTest`; form messages in `MyExpenses.test.tsx` |
| 3 | Retry and duplicate | `ClaimRetryAndDuplicateTest` (SC-010) |
| 4 | Links | `ClaimLinkTest` |
| 5 | Decide, self-decision, concurrent decisions | `ClaimDecisionTest`, `SelfDecisionTest` (SC-004); `ExpensesReview.test.tsx` |
| 6 | Payment, reversal, company-paid | `ClaimPaymentTest` (SC-005), `ClaimReversalTest`, `ReimbursementIntakeTest` |
| 7 | Budget | `BudgetVersusActualTest` (SC-006), `BudgetApiTest`; `BudgetVersusActual.test.tsx` |
| 8 | Pending, costs, CSV | `PendingAgeingTest`, `CostMeasureTest` (SC-007), `ClaimCsvTest`, `ExpenseQueriesInterfaceTest`; `CostMeasures.test.tsx` |
| 9 | Access | `ExpenseAuthorizationTest`, `ExpensesAccessModelTest`, `ExpenseAuditVisibilityTest` (SC-008) |
| 10 | After paid | `ExpensesSchemaTest` (trigger), `ClaimPaymentTest` (service) (SC-009) |

## Financial review (Principle IX): the seven points and their fixtures

Owner confirmation status for every point: **not yet given**.

1. Actual = Approved, Paid and Paid by company by expense date: `BudgetVersusActualTest.onlyApprovedPaidAndPaidByCompany...` (13,250.75 actual).
2. Pending = Submitted; a reversed approval moves to pending: `BudgetVersusActualTest.aReversedApprovalMoves...`, `ClaimReversalTest.aReversedClaimTurnsFromActual...`.
3. Remaining = budget minus actual with pending beside it; over when actual exceeds budget: `BudgetVersusActualTest.theWorkedExampleOfTheSpec` (20,000 / 12,000 / 5,000 / 8,000, then over by 2,000), zero budget case.
4. A claim reaches payroll once: `ClaimPaymentTest.anApprovedClaimReachesTheNextRunOnce...` (run, cancel, re-run, approve: one adjustment, status Paid with payroll); paid-outside withdraws the offer: `ClaimPaymentTest.recordingAClaimPaidOutside...`; the database trigger refuses any change to a paid claim: `ExpensesSchemaTest`.
5. Cost per School (Marketing plus linked Travel and Fuel over Schools won): `CostMeasureTest.costPerSchoolCountsMarketingAndAcquisitionTravelOnly` (30,000 / 3 = 10,000.00; overhead excluded).
6. Cost per Teacher (Training Stay of batches ending in the period over completers): `CostMeasureTest.costPerTeacherDivides...` (60,000 / 12 = 5,000.00).
7. Rounding half-up to two decimals: `CostMeasureTest.roundingIsHalfUp...` (100.00 / 3 = 33.33; 100.02 / 3 = 33.34).

## Test results

- Migration used: **V90** (`V90__create_expenses.sql`), chosen so it does not collide with V70 (035), V80+ (027) or other parallel specs.
- New backend classes: 28 test classes, all passing when run on their own.
- Full backend suite (first run, loaded machine): 1,732 tests, 10 failures and 3 errors. Triage:
  - mine, fixed and re-run green: `AttendanceAccessModelTest` (3), `NavigationSectionOrderTest` (1) (the new menu items and the Teacher OPERATIONS section; the lists also lacked the earlier Teacher Performance item), `TasksModuleRulesTest` (expenses may read `tasks.api`).
  - environment, pass when re-run alone: `TeacherFirstAssignedTest` (PostgreSQL "too many clients"), `PayRunRecomputeTest`, `SupplementaryRunTest`, `DevSeedTest` (long runs under load).
  - not related, left alone: `LeaveAuditTest` (the test picks dates that are not working days on today's date), `PayRunScaleTest` (took 2m27s against a 2m limit on the loaded machine).
- Full frontend suite: 943 tests, 27 failures, all in `src/a11y/a11y.test.tsx` dark-mode pages (timeouts under load; re-run alone with `--pool=forks` the file passes, 178 of 178; none of the expenses tests). The expenses tests (5 files, 48 tests) pass.

## What was not done

- Manual browser walkthrough of the screens and the quickstart in a running app.
- Screen-reader check.
- Postman requests were written but not run.
- Human timing of SC-001 (2 minutes to raise a claim) and SC-002 (30 seconds to decide).
- Owner confirmation of the financial review.
