# Quickstart: validating Salary Structures and Pay Policy (spec 013a)

Prerequisites: spec 005a merged (designations exist), the dev profile with the demo seed (Admin and Director users),
PostgreSQL through Testcontainers or the compose file. Use the Postman folder "Salary Structures" for requests; paths
are in [contracts/salary-structures-api.md](./contracts/salary-structures-api.md).

## 1. Salary history (US1, SC-001, SC-005)

1. Sign in as Admin and open OPERATIONS → Salary Structures. Every designation is listed; one without a salary shows
   "no salary yet".
2. Record ₹30,000 from 01/11/2026, then ₹32,000 from 01/02/2027 for the same designation.
3. Open its history: two rows, the first unchanged. The salary in effect on 15/12/2026 is ₹30,000 and on 15/02/2027 is
   ₹32,000 (the `PayRulesTest` table checks 10+ such dates).
4. Try amount 0, -5, 100.123, no date, and a date in a previous month: each is refused with the problem named.

## 2. Append-only (SC-002)

1. Run `PayrollAppendOnlyTest`: a direct `UPDATE` and a direct `DELETE` on both tables fail.
2. Open Audit → Change History: each addition shows who, when, and the prior and new value.

## 3. Pay policy (US2)

1. Open the policy: calendar month, working-day divisor, half = 0.5, nearest rupee.
2. Record a version from the first of the month with rounding `DOWN`; the old version remains and a date before it
   still shows the old one. A half-day fraction of 0 or 1 is refused, and so is a start date before this month.

## 4. Rules for payroll (US3, SC-003)

Run `PayRulesTest` (hand-computed fixtures). It covers: a full month; a Teacher first placed on the 11th; a transfer
between two Schools with different weekly offs (no day twice or lost); an exit mid-month; a holiday month; a Teacher
with no recorded salary (`NoSalary`, never zero); a month with no working days (`NoWorkingDays`); a Manager's unpaid
days (`Unavailable`); ₹26,000 over 26 days = ₹1,000 a day and ₹500 a half day; a raise mid-month valuing each unpaid
day at its own day's salary; rounding once on the total.

## 5. Access (SC-004)

Run `SalaryStructuresAuthorizationTest`: Zone Manager, Teacher and System get no menu item and 403 on every endpoint;
Admin and Director succeed. In the browser, sign in as a Zone Manager and open `/operations/salary-structures`
directly: "not authorized".

## 6. Gaps before payroll (US4, SC-006)

With one Manager on a designation that has no salary, open the list: that designation is flagged with the number of
people affected. A Teacher designation shows the note that its amount is only a default.

## Results

Record the outcome of each step in `quickstart-results.md` when the walkthrough is done, as for earlier specs.
