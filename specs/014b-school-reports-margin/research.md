# Research: School, Collections and Margin Reports (014b)

Checked against `main` at `48e9bbe` (014a and 014c merged), 2026-10-28.

## D1. Pay split basis: the billed working days of the due lines

- **Decision**: split a Teacher's payslip net across Schools in proportion to `due_line.worked_days` at each School for the month. A full-month line stores `worked_days = month_days`, so a Teacher at one School is not split.
- **Rationale**: `DueCalculator` already bills a part month as salary x working days at the School / the School's working days, and stores `worked_days` per line. Using the same days makes the fee and the pay cover exactly the same span, which is what "match the due lines" asks for. It also needs no placement-days interface from 027 (`transfer.api` has events only) and no second source of truth for "days placed".
- **Alternatives**: (a) calendar placement days from `TeacherDirectory.placementsOverlapping`: needs per-School working calendars to match billing and can disagree with the due line around holidays and contract dates; (b) a new 027 interface: more cross-module work for a less exact result.
- **Edge**: a Teacher with fee lines in two Schools gets two Margin rows; their pays add up to the payslip net to the paisa (remainder paisa to the School with the most days, ties to the lower School id).

## D2. Fee per Teacher per School needs one 022 read

- **Finding**: `SchoolReceivables.TeacherBilled` holds a total and a list of School ids. The SQL behind it already groups by teacher, month and School, then merges.
- **Decision**: add `billedPerTeacherAndSchool(teacherIds-or-schoolIds, from, to)` returning `TeacherSchoolBilled(teacherId, schoolId, month, amount, workedDays, monthDays)`. For a Zone Manager the report asks by School ids, so the read takes School ids as well as Teacher ids (two bulk forms, same query).
- **Rationale**: one grouped query, constant statements, same cancelled-due rule as `billedPerTeacher`.

## D3. Matching and "not matched"

- A Teacher is matched when they have at least one fee line in the month **and** a payable payslip line in that month's run (`PayrollFigures.peopleOf(month)`, kind Teacher, `payable` and `net` not null).
- Otherwise the Teacher's fee rows show "not matched": reason "no payslip for this month" (no run, or Teacher not in the run), "payslip cannot be paid" (line not payable), or "no School fee for this month" (payslip but no fee line; shown under the School of the payslip line, or "not placed" when none). Not-matched rows are left out of every total.
- A month with no run at all: pay and margin are "no data yet" for every row (not "not matched"), with a note; fee still shows.

## D4. Month state and default month

- **Margin**: month state from the run (`runOf(month)`): none -> NONE; draft or awaiting approval -> DRAFT with the note "payroll not approved, figures may change"; approved or paid -> APPROVED. Default month: `lastApprovedMonth()`, else the current month.
- **Collections**: no lock concept in billing, so month state NONE and a header note "ageing as of <today>". Default month: the current month.
- **School**: month state from attendance lock (the same rule as the Attendance report); default: last fully locked month, else current.

## D5. Collections: two sections

- The spec asks for billed, collected, outstanding and ageing. A month's dues and an overdue position are different things, so the report has two sections:
  - `month`: for the chosen month per School, billed (owed after adjustments), collected, outstanding for that month's dues, from `schoolMonths`; totals per Zone and organization. Equals the 022 School ledger rows for that month.
  - `ageing`: the outstanding position across all months as of today per School, with "not yet due", 0 to 30, 31 to 60, 61 to 90 and over 90 days past due; totals per Zone and organization. Equals the 022 Ageing screen.
- Ageing comes from a new `SchoolReceivables.ageing(schoolIds, asOf)` that reuses `LedgerQueryService.bucketsOf`, so the buckets cannot drift from the screen. Spec FR-006's rule (buckets plus not yet due equal outstanding) holds inside each `ageing` row.

## D6. School report sources

| Column | Source | State |
| --- | --- | --- |
| Teachers placed (last day of month) | `TeacherDirectory.placementsOverlapping` grouped by School | new bulk read `placedCountBySchool(schoolIds, date)` |
| Contract end | `SchoolContracts.contractOf(schoolId, date)` is per School | new bulk `contractsOfAll(schoolIds, date)` |
| Attendance % | same grouping as the Attendance report's `schools` section | new `attendance.api.SchoolAttendance`, which `AttendanceReportSource` also uses so the two cannot differ |
| Substitution cover | none by School and month | new `substitution.api.SubstitutionCover` (days covered by a substitute, by the Manager, left open) |
| Observations, open issues | `PerformanceSummary.byManager/byTeacher/byZone` only | new `bySchool(from, to)` |
| Billed, collected, outstanding | `SchoolReceivables.schoolMonths` | exists |
| Prospect to active stage | marketing `EffectiveStageResolver` is internal | new `recruitment.api.SchoolStages.stageOf(schoolIds)`; a School with no prospect record gives "not applicable" |

## D7. Isolation of failing sources

Each optional source (payroll, billing ageing, substitution, performance, stages, contracts, attendance) is read in its own read-only transaction (`PROPAGATION_REQUIRES_NEW`), as `ActivityCalendarService` and the 014c sources do. Without it, a throwing source marks the section transaction rollback-only and the whole section fails (found in 014c).

## D8. Permissions and navigation

- Keys `REPORT_MARGIN`, `REPORT_COLLECTIONS`, `REPORT_SCHOOL` with View and Export. Eligible: Admin, Director, Manager. Defaults: Admin and Director all; Manager View and Export for own Schools. Teacher and System cannot be granted them.
- Three REPORTS items at order 810, after the existing report items (the REPORTS section sits between SYSTEM and AUDIT since the menu regrouping).
- `AuditVisibility` gets `REPORT_EXPORT_MARGIN`, `REPORT_EXPORT_COLLECTIONS`, `REPORT_EXPORT_SCHOOL`.

## D9. Rounding

Fee and pay are rupees with two decimals. Split pays are rounded half-even to the paisa per School, and the remainder paisa goes to the School with the most days so the parts add up to the payslip net exactly.

## D10. Filters

Margin: ZONE, SCHOOL, TEACHER. Collections: ZONE, SCHOOL. School: ZONE, SCHOOL. No new `ReportFilter`; the 014a filters and scope resolver are unchanged.

## Open items

None. All spec clarifications are resolved (Zone Manager sees fee, net pay and margin for own Schools).
