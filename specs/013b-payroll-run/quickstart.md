# Quickstart: validating the Payroll Run

How to prove 013b works end to end once implemented. Each step names the user story and the test class that will carry it.
Run it with the dev demo data (`hls.seed.demo-data=true`), which gives one Teacher, one Manager and the Admin and
Director sign-ins used in the earlier specs.

## Prerequisites

- Backend and database running (`scripts/run-all.ps1` locally), Docker for the integration tests.
- Admin `9800000001` and Director `9800000002` (password `Password123!`).
- A Teacher and a Manager with salaries recorded and attendance locked for the previous month (the dev seed does this).

## Scenarios

| # | Story | Steps | Expected |
| - | ----- | ----- | -------- |
| 1 | US1 | Admin: OPERATIONS → Payroll → Start run for last month | A draft run; progress reaches done; one line per person with salary, payable days, working days, unpaid days, earned, deduction, adjustments, net; totals at the top |
| 2 | US1 | Start a run for the current month | Refused: the month has not ended |
| 3 | US1 | Start a second run for the same month | Refused; the existing run is shown |
| 4 | US1 | Add a person with no salary; start the run | They are under "Cannot be paid in this run" with the reason; the other lines are unchanged |
| 5 | US1 | Lock the person's attendance month; Compute again | The person now has a line; the other lines are unchanged; the change list names them |
| 6 | US2 | Open a line | The working shows the salary rows, payable dates, divisor, each unpaid day with value and reason, rounding, policy; the figures reproduce the line |
| 7 | US2 | Submit; sign in as the same person with both roles; Approve | Refused: the submitter cannot approve |
| 8 | US2 | Director: Return with a reason; Admin sees it; submit again; Director approves | Approved and locked; approver and time recorded; payslips exist |
| 9 | US2 | Try to recompute, cancel, delete or change a line of the approved run | Refused every time |
| 10 | US3 | Approve a run; then add a holiday, reopen a Manager's attendance month, backdate a salary, change the policy | Every figure, payslip and export row is unchanged |
| 11 | US4 | Record a +₹2,000 Correction; start the next run | It is its own line item on the person's line, in the net; after approval it is Applied and cannot be edited |
| 12 | US4 | Record an adjustment of kind Incentive | Refused (reserved) |
| 13 | US5 | Approve a run that left one person out; lock their month; start a supplementary run | It holds only that person; approve; they are paid once; the first run is unchanged |
| 14 | US5 | Start a supplementary run while the primary is a draft, or while one is open, or when nobody is owed | Each is refused |
| 15 | US6 | Download one payslip and the zip; change the calendar; download again | Same figures; no payslip for a draft run or a cannot-pay line |
| 16 | US6 | Sign in as a Manager and a Teacher; open Payroll or a payslip URL | Not authorised; no figures |
| 17 | US7 | Mark two lines paid with date, mode and reference; correct one | Counts and amounts update; the older details stay in history; amounts of the run never change |
| 18 | US7 | Mark a line paid in a draft run | Refused |
| 19 | US8 | Export a draft and an approved run | Both state the month and the status; the approved rows equal the screen and the payslips; each export is audited |
| 20 | FR-010 | Run for 1,000 generated people | Done in under two minutes while another screen stays usable (SC-002) |

## Automated evidence (target)

| Area | Test class (backend unless noted) |
| ---- | --------------------------------- |
| Earned pay arithmetic, hand-computed fixtures (SC-001) | `PayRulesEarnedPayTest`, `LineCalculatorTest` |
| Lifecycle, cannot-be-paid, recompute diff | `PayRunLifecycleTest`, `PayRunComputationTest` |
| Freezing after approval (SC-003) and the trigger | `PayRunFreezingTest`, `PayRunImmutabilityTriggerTest` |
| Separation of duties, concurrency (two starts, two approvals) | `PayRunApprovalTest`, `PayRunConcurrencyTest` |
| Adjustments | `PayAdjustmentTest` |
| Supplementary runs, exactly once (SC-007) | `SupplementaryRunTest`, `PaidMonthKeyTest` |
| Payment | `PayPaymentTest` |
| Payslip PDF and zip | `PayslipTest` |
| CSV (SC-008) and formula neutralisation | `PayRunCsvTest` |
| Authorization per role on every endpoint | `PayrollAuthorizationTest` |
| Audit of every write, mapping of audited types | `PayrollAuditTest` |
| 1,000 people (SC-002) | `PayRunScaleTest` |
| Module boundaries | `PayrollRunModuleRulesTest` |
| Screens per role, both themes, axe | `PayrollRuns.test.tsx`, `PayrollRun.test.tsx`, `Adjustments.test.tsx` (frontend) |

## Financial review (Principle IX), to be completed before the release

The payroll owner confirms, with the fixtures:

1. **Earned-pay formula**: each payable day at its own day's salary over the month's working days. A constant-salary person
   payable all month earns exactly the salary.
2. **Rounding**: earned pay and the deduction are each rounded once by the pay policy. With the rounding rules `UP` and
   `DOWN`, the same direction applies to both, which can favour one side by up to one rupee each; confirm this is intended or
   ask for earned pay to be rounded to the nearest rupee whatever the rule.
3. **Teacher who changed School in the month**: each date uses its own School's divisor, so a full-month Teacher in two
   Schools may earn a few rupees more or less than the plain salary.
4. **Manager lock versus calendar**: the run divides by the calendar as it is when computed and shows a warning when the
   Manager's lock-time working-day count differs. Confirm a warning (not a block) is right.
5. **Adjustments and negative net**: a negative net blocks submission; confirm.
6. **Supplementary run**: confirm the same month's policy and divisor are used, as stored at computation of that run.
7. **School working days (spec 037)**: payroll divides by the calendar-derived working days, not by the stored School
   figure of spec 037 (which may include half days and wins over the calendar for the School). Decide whether payroll
   adopts the stored figure; if so it is an amendment to 013a's `PayRules`, not to the run.

Results go in `quickstart-results.md` after implementation, with the "Not done" list stated honestly (manual browser
walkthrough, screen-reader check, human timing of SC-005).
