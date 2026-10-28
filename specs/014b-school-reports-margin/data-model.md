# Data Model: School, Collections and Margin Reports (014b)

No table and no migration. Everything below is computed at read time and returned through the 014a `ReportPage` shape (sections of rows of cells; a cell is a value, no data yet, not available or not matched, with a reason where it applies).

## Margin report (`margin`, key `REPORT_MARGIN`)

Filters: ZONE, SCHOOL, TEACHER. Month state from the pay run (research D4).

**Section `schools`** (totals): one row per School: Zone, Teachers matched, Fee, Pay, Margin. Totals row: organization (or the caller's scope).

**Section `zones`**: one row per Zone with the same columns.

**Section `teachers`**: one row per Teacher and School.

| Column | Meaning |
| --- | --- |
| Teacher (row label) | name |
| School, Zone | where the fee line is |
| Billed days | `due_line.worked_days` at this School, as a share of `month_days` |
| Fee | sum of `due_line.amount` for the Teacher at this School in the month (after cancelled dues are dropped) |
| Pay | split share of the payslip net (below) |
| Margin | Fee less Pay |
| Status | matched, or "not matched: reason" |

**Rules**

- Matched: the Teacher has a fee line and a payable payslip line in the month's run. `Pay(s) = round(net x days(s) / sum days)`, with the remainder paisa to the School with the most days (ties: lower School id), so `sum Pay(s) = net` exactly.
- Not matched: the Fee or Pay cell for the missing side shows the reason; Margin is "not matched"; the row is excluded from every total and from "Teachers matched".
- No run for the month: Pay and Margin are "no data yet" on every row; Fee shows; totals show Fee only.
- Zone Manager: only fee lines at Schools in scope; the pay shown is the part split to those Schools.

## Collections report (`collections`, key `REPORT_COLLECTIONS`)

Filters: ZONE, SCHOOL. Month state NONE with the note "ageing as of <date>".

**Section `month`**: one row per School for the chosen month, then Zone totals and the organization total.

| Column | Meaning |
| --- | --- |
| Billed | `SchoolMonth.owed` (the due less its adjustments) |
| Collected | `SchoolMonth.collected` |
| Outstanding | `SchoolMonth.balance` (never below zero) |
| Due on | `SchoolMonth.dueOn` |

**Section `ageing`**: one row per School for the position over all months as of today, then Zone totals and the organization total.

| Column | Meaning |
| --- | --- |
| Outstanding | sum of balances of live dues |
| Not yet due | balances whose due date is today or later |
| 0 to 30, 31 to 60, 61 to 90, Over 90 | balances by days past due (the 022 Ageing buckets) |

Invariant: Not yet due + the four buckets = Outstanding, in every row and total.

## School report (`school`, key `REPORT_SCHOOL`)

Filters: ZONE, SCHOOL. Month state from attendance lock. Section `schools`: one row per School in scope.

| Column | Source |
| --- | --- |
| Zone | School directory |
| Teachers placed | placements covering the last day of the month |
| Contract end | `ContractView.endsOn` of the contract in effect that day (no data yet if none) |
| Attendance % | `SchoolAttendance` (equals the Attendance report `schools` row) |
| Substitution: by substitute, by Manager, open | days of requirements raised for the School in the month |
| Observations done, Open issues | `PerformanceSummary.bySchool` |
| Billed, Collected, Outstanding | `SchoolReceivables.schoolMonths` for the month |
| Stage | `SchoolStages` (Won, Active, ..., or "not applicable") |

## New read types (published in the owners' `api` packages)

| Type | Fields |
| --- | --- |
| `SchoolReceivables.TeacherSchoolBilled` | teacherId, schoolId, month, amount, workedDays, monthDays |
| `SchoolReceivables.SchoolAgeing` | schoolId, outstanding, notYetDue, d0to30, d31to60, d61to90, over90 |
| `SubstitutionCover.SchoolCover` | schoolId, bySubstitute, byManager, open (day values) |
| `SchoolAttendance.SchoolRollup` | schoolId, teachers, working, present, absent, leave, lossOfPay |
| `SchoolStages.Stage` | schoolId, stage (text or null) |

## State transitions

None; the reports hold no state.
