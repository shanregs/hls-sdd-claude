# 014b School, Collections and Margin Reports: `/speckit-specify` input

2026-10-27. Second third of roadmap row 014. Depends on **014a** (the report framework), 012 and 022 (contracts, dues,
collections), 013b (payslips), 027 (placement), 017a (substitution), 023, 026. Decisions D12 and D14, and the
2026-10-27 answers recorded in `specs/014a-reports-framework/spec.md`.

## Feature description (paste as the argument to `/speckit-specify`)

014b-school-reports-margin: Three reports on the 014a framework, with the same filters, scope, CSV export and audit.

1. **Margin report** (D14: Admin, Director, and the Zone Manager for own Schools only). School fee minus Teacher pay,
   per Teacher, per School and per month, totals per School and Zone.
   - Fee is the 022 monthly due line for the Teacher; pay is the 013b payslip **net** pay.
   - When a Teacher is placed in two Schools in the month (transfer, 027), the pay is **split by placement days** to
     match the due lines.
   - A Teacher with a due but no payslip, or the reverse, is shown "not matched" with the reason and left out of totals,
     never as zero.
   - The Zone Manager sees margin **per School and per Teacher** but has no access to the payroll report.
   - A draft payroll run is labelled "payroll not approved, figures may change".
2. **Collections report** (Admin, Director; a Zone Manager for own Schools). Billed, collected, outstanding and ageing
   (0 to 30, 31 to 60, 61 to 90, over 90 days past due) by School, Zone and organization, equal to School Billing (022).
3. **School report** (Admin, Director; a Zone Manager for own Schools). Per School and month: Teachers placed, contract
   end, attendance % (from 014a), substitution cover (017a), observations and open issues (026), billed, collected,
   outstanding (022), prospect to active stage (023).

Out of scope: Teacher, Recruitment, Manager, Expenses reports (014c), payroll cost report (014a), cost to HLS beyond net
pay, charts, scheduled output.

## What 014b needs from other modules (verified against main on 2026-10-27)

| Need | State on main |
| --- | --- |
| Billed per Teacher per month | Exists: `schoolbilling.api.SchoolReceivables.billedPerTeacher` (amount and School ids) |
| Billed and collected per School month, balance, overdue | Exists: `SchoolReceivables.schoolMonths`, `balances` |
| Ageing buckets | Derivable from `SchoolMonth` (balance, dueOn); the plan decides whether 022 adds a bucket method |
| Fee per Teacher **per School** when a Teacher has two dues | **Missing**: `TeacherBilled` has the total and a School id list only. 022 amendment needed |
| Payslip net per Teacher per month | **Missing**: `payroll.api` has no run read interface (run data is internal). 013b amendment needed: a read-only `PayrollFigures` |
| Placement days per Teacher per School per month | **Missing**: `transfer.api` has events only. Needed from 027 or `schoolbilling` (`Occupancy`/`PlacementMoves`) to split pay |
