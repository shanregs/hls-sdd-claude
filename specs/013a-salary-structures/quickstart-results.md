# Quickstart results: Salary Structures and Pay Policy (spec 013a)

Run on 2026-10-11 against the implementation on this branch. **Every step below was checked by an automated test
(Testcontainers PostgreSQL, Vitest); nobody has yet clicked through the screens in a browser**, so the two timings of
SC-005 and the manual keyboard and phone-width checks of T049 are still open (see "Not done").

## 1. Salary history (US1, SC-001)

| Step | Outcome | Evidence |
| --- | --- | --- |
| Every designation is listed; one without a salary shows "no salary yet" | Pass | `SalaryStructureApiTest.theListSaysNoSalaryYetBeforeTheFirstEffectiveDate`; Vitest `SalaryStructures.test.tsx` |
| Record a salary, then a later one; both in the history, the first unchanged | Pass | `SalaryStructureApiTest.recordsASalaryAndReadsItBackWithTheEarlierRowUnchanged` |
| The salary in effect on a date is the latest row on or before it; same date goes to the row recorded last | Pass | `SalaryResolverTest` (11 cases), `SalaryStructureApiTest.twoRowsOnOneDate...`, `PayRulesTest` |
| Amount 0, -1, 100.123, over 9,999,999.99, no date, a date before this month, a note over 500 characters: each refused with the problem named | Pass | `SalaryStructureApiTest.everyBadSalaryIsRefusedWithTheProblemNamed`, `PayrollValidationTest` (month boundary at the first of the month) |
| 50 designations list in under 1 s, in a fixed number of queries | Pass | `SalaryStructureApiTest.theListOfFiftyDesignationsLoadsFast`, `PayRulesBulkTest.theDesignationListUsesAFixedNumberOfQueries...` |

## 2. Append-only (SC-002)

| Step | Outcome | Evidence |
| --- | --- | --- |
| A direct SQL `UPDATE` and `DELETE` on both tables are refused; invalid inserts refused by the check constraints | Pass | `PayrollAppendOnlyTest` |
| Every addition shows who, when, and the prior and new value in Change History | Pass | `SalaryStructureAuditTest`, `PayPolicyAuditTest` |

## 3. Pay policy (US2)

| Step | Outcome | Evidence |
| --- | --- | --- |
| A new installation shows calendar month, working-day divisor, half = 0.500, nearest rupee | Pass | `PayPolicyApiTest.aNewInstallationReturnsTheStandardRules`, `PayrollAppendOnlyTest.theSeededVersionOne...` |
| A new version keeps the old one; each date uses the version in effect on it; ties to the version recorded last | Pass | `PayPolicyApiTest` |
| Fraction 0, 1, -0.5, 1.5, 0.5555, an unknown rounding, divisor or pay month, a missing or too-early date: refused | Pass | `PayPolicyApiTest.everyBadVersionIsRefusedWithTheProblemNamed`, Vitest `PayPolicy.test.tsx` |

## 4. Rules for payroll (US3, SC-003)

| Fixture | Outcome | Evidence |
| --- | --- | --- |
| ₹26,000 over 26 days = ₹1,000 a day, ₹500 a half day; ₹30,000 over 26 kept unrounded; three days round once to ₹3,462 | Pass | `LossOfPayCalculatorTest` |
| A full month; a Teacher first placed on the 11th | Pass | `PayableDaysCalculatorTest`, `PayRulesTest` |
| A transfer between Schools with different weekly offs (no day twice or lost) | Pass | `PayableDaysCalculatorTest`, `PayRulesCalendarTest` |
| An exit mid-month; a holiday month; a month with no working days (`NoWorkingDays`, no division) | Pass | `PayableDaysCalculatorTest`, `PayRulesCalendarTest` |
| A Teacher with no recorded salary is `NoSalary`, never the designation's amount, never zero | Pass | `PayRulesTest.aTeacherWithNoRecordedSalary...` |
| A Manager: designation salary, joining date starts the month, no designation / no salary give stated reasons | Pass | `PayRulesTest` |
| A Manager's unpaid days are `Unavailable`; the total is `Cannot` | Pass | `PayRulesTest.aManagersUnpaidDays...` |
| A raise mid-month values each unpaid day at its own day's salary | Pass | `LossOfPayCalculatorTest`, `PayRulesTest.aMidMonthRaise...` |
| Same inputs give the same answer; the bulk forms equal the single forms | Pass | `PayRulesTest.theSameInputsGiveTheSameAnswer...` |
| Bulk forms run in a fixed number of queries for 300 people; 300 salaries in under 2 s | Pass | `PayRulesBulkTest` |

## 5. Access (SC-004)

| Step | Outcome | Evidence |
| --- | --- | --- |
| Zone Manager, Teacher and System get 403 on all five endpoints and no menu item; Admin and Director succeed | Pass | `SalaryStructuresAuthorizationTest`, `PermissionEligibilityTest` (the matrix refuses a grant to them; there is no Edit) |
| A deep link shows "Not authorized" and fetches nothing | Pass (component level) | Vitest `SalaryStructures.test.tsx`, `PayPolicy.test.tsx`, `MissingSalary.test.tsx`. **Not clicked in a real browser.** |
| No endpoint or screen model returns a person's pay; `TeacherView` has no salary member | Pass | `PayRulesBoundaryTest` |

## 6. Gaps before payroll (US4, SC-006)

| Step | Outcome | Evidence |
| --- | --- | --- |
| A Manager designation with Managers and no salary is flagged with the count; recording a salary clears it | Pass | `MissingSalaryReportTest` |
| A Teacher designation counts Teachers with no salary of their own, whatever the designation's amount; carries the "only a default" note | Pass | `MissingSalaryReportTest`, `SalaryStructureApiTest.theTeacherNoteIsOnTeacherDesignationsOnly` |

## Not done

- **SC-005 timings** (record a salary in under a minute; find the salary in effect on a date from its history in under 30
  seconds) are about a person using the screens and have not been timed. The list and history load well inside a second.
- **Manual accessibility pass (T049)**: axe finds no WCAG 2.2 AA violation on the three screens in light and dark
  (`SalaryStructures.test.tsx`, `PayPolicy.test.tsx`), every form field has a label, and errors are announced
  (`role="alert"`). Keyboard order and the phone-width layout were not tried on a device.
- **Amendment A2 to spec 008 is not built**, so unpaid days come from the status-code category fallback (research.md,
  section 4 notes). The seeded absent code `A` is in the LEAVE category, so an absence is a LEAVE-category mark that no
  leave request backs. A hand-entered `L` mark with no request is therefore also treated as an absence.
- The brief demo-data seeder (`PayrollDevSeeder`) was added; it has been started by `DevSeedTest` but nobody has looked at
  the seeded list in the browser.

## Financial review of the formulas (T051, Constitution Principle IX)

A desk check of research.md section 4 against decisions D2, D12 and D13 of `docs/spec-roadmap.md`. **This is the
author's own check; the constitution asks for a financial review before 013b, which still needs a person who owns the
pay rules to sign off.**

- **D12, fixed monthly salary, versioned by effective date, no components**: `designation_salary` and the Teacher's own
  history are append-only dated rows; the one in effect on a date is the latest on or before it (`SalaryResolver`). Agrees.
- **D12, loss of pay = salary ÷ the month's working days, per unpaid day**: `LossOfPayCalculator.fullDay`, with the
  salary in effect on the unpaid day and the working days of that day's month on that day's School calendar. Agrees.
  Rounding is applied once, to the month total, to a whole rupee by the policy's rule (clarification 1).
- **D13 and D2, paid only for days placed in a School; training unpaid; salary starts on the first School assignment**:
  a Teacher's payable dates are the working dates inside an ACTIVE placement span on that School's calendar; induction
  days have no School, so they are never payable. A Manager is payable from the joining date to the exit date. Agrees.
- **Points the reviewer should decide**, because the spec leaves them open or this implementation chose:
  1. A month is calculated under the policy version in effect on its **last day**, so a version dated mid-month governs
     that whole month's half-day fraction and rounding.
  2. An **unmarked** working day is neither paid nor unpaid by this interface; it is for 013b to decide.
  3. A half-day absence is the policy fraction (0.5 by default) of a day, taken from the mark's day value (1.00 or 0.50).
  4. For a Teacher placed in two Schools, `workingDaysInMonth` is that of the School placed in last; each unpaid day is
     still valued over its own School's month.
