# Research: Substitute Teachers, Day-Rate Pay and Reconciliation

Read from the specs and code on main (2026-11-01).

## R1. What a substitute is assumed to be today

- 005: `teacher.user_id` is a nullable link to the account, "cleared on exit"; a Teacher has status IN_TRAINING, ACTIVE, ON_LEAVE, EXITED and placements. A Substitute can therefore exist without an account today; what is missing is the type.
- 017a: any ACTIVE Teacher with no leave, not placed (or placed in a closed School) is "available"; the credit is a separate record, attendance untouched; the substitute is notified and has a "My Substitutions" page (assumes a login).
- 008: the grid, rollup and lock are per placed Teacher; a Teacher self-marks within three days; marks without placement are refused; a built-in status S exists.
- 013a/013b: every payroll person has a monthly salary in effect; a Teacher is a payroll person when placed with payable dates; a line is earned - deduction + adjustments; `SUBSTITUTE_CREDIT` is a reserved adjustment kind. Designation salary is only a suggested monthly amount.
- 005a: designation kind TEACHER or MANAGER, with a dated history per person; no rates.
- Identity: Teacher OTP login by phone; Teacher role dashboards.

## R2. Decision: Teacher engagement type (N1 A)

A column on the Teacher, not a designation kind. Reason: 005a's kind is fixed after first use and unique per kind; a Teacher moving between Regular and Substitute would be awkward. Alternative B recorded in clarification-questions.md.

## R3. Decision: how payroll takes a day-rate line

Preferred: a new person source "Substitute" in 013b whose line has earned = sum of rated days, deduction 0, adjustments allowed (Corrections). 013b assumes a salary row for each person (`SalaryInEffect`); the Substitute source bypasses it. Risk R1: the run line columns (`earned`, `deduction`, `adjustments`, `net`) already fit; the frozen working (`payroll_run_line_working`?) must accept day entries; verify at implementation. Alternative: use `SUBSTITUTE_CREDIT` adjustments on a zero-salary line; rejected because Q3 and Q8 ask for "no base salary" and a single line with days and rate, and the adjustment path hides the working.

## R4. Decision: ports, not dependencies

`payroll.api.DayRateWork` is implemented by `substitution.payout`. `payroll` reads days and rates; `substitution` reads `payroll.api.DayRates` and `DayRatePay`. This creates `substitution -> payroll.api`, which already exists for other reasons (017a depends on none of payroll today; 030 and 015 depend on payroll.api the same way). No cycle.

## R5. Decision: attendance kept by this feature (N3 A)

The 008 grid is built on placements and working days; substitutes have neither. A day record per allocation with Present, Absent and Not marked is simple, auditable and keeps 008 untouched except for exclusion. Lock per substitute and month (Admin or Director), refused while a live day is unmarked, reopen with a reason, mirrors 008's behaviour. Alternative B (S code in the grid) rejected: needs 008 to change its lock rule.

## R6. Decision: rate by designation, effective-dated

Follow `designation_salary` (013a): append-only rows (designation, amount, effective date), latest effective on or before the date wins, ties by sequence, edits by Admin and Director with audit, screen under Salary Structures. The designation a substitute held on a date comes from `designation.api` (005a history). Mid-month rate change handled per day.

## R7. Decision: back-pay (Q5, N4 A, N5 A)

Migration: for each live credit before go-live create a day record Attended (migrated), flagged for review; credits for non-Substitute Teachers are listed "settle first" and are not created as payable. Open months: paid in their run. Approved months: 013b supplementary run per month already exists (contains people not yet paid for that month); the Substitute source must work in a supplementary run through `unpaidPresentDays`. Locking: migrated months must be locked per substitute-month (the migration offers a bulk lock to Admin/Director). Risk: if 013b cannot start a supplementary run for a month with an approved primary run containing no Substitute, the amendment must allow it (research at implementation). Alternative B (one back-pay line in the first open run) needs past-month lines in 013b: larger change.

## R8. Corrections after approval

Unchanged from the earlier plan: no clawback, day flagged "correction needed", Admin raises a Correction (013b kind), linked by `SUBSTITUTE-CORRECTION:<dayId>`; `CorrectionRaising` added to `payroll.api` if there is no cross-module path (verify at implementation).

## R9. Notifications and Teacher page

A Substitute has no login: 017a notifications go to the School's Zone Manager (else Admin and Director); the Teacher "My Substitutions" page is not built for them. The old US4 (Teacher view) is replaced by the substitute record view for Zone Manager, Admin and Director.

## R10. Migration numbers

V150 is the highest on main today; V160 and V161 planned. Re-check at implementation (other specs, including the 014b/041 line, may take slots).

## R11. Items to verify in code at implementation

1. `payroll_run_line` working structure can hold a day list for a person without salary rows; supplementary run eligibility for people absent from the primary run.
2. `designation.api` can answer "designation of a Teacher on a date" in bulk.
3. `teacher.api` can filter Active Substitutes; 008 queries exclude them.
4. Existing credits' Teachers: how many are not Substitute-type after N1 (affects the "settle first" list size).
