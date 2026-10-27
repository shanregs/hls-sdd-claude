# 014a Reports Framework, Attendance and Payroll: `/speckit-specify` input

2026-10-27. First third of roadmap row 014 (split into 014a, 014b, 014c on 2026-10-27, decision recorded in
`specs/014a-reports-framework/spec.md` Clarifications). Reads 008 and 032 (attendance), 009 (leave), 013a and 013b
(payroll) and builds on the 028 reporting module. Branch `spec/014-reports`.

## Feature description (paste as the argument to `/speckit-specify`)

014a-reports-framework: The REPORTS menu foundation and its first reports. Module `reporting` (created by spec 028), with
a new report package beside the tile package. The module owns no data: every figure is read from the owning module's
public interface, and where the Management Dashboard already has the figure the report calls the same source.

1. **Framework** used unchanged by 014b and 014c: a report definition (key, filters, columns, scope rule, grants), a
   month filter (default the last locked month, else the current; unlocked or unapproved months selectable with a
   warning label), Zone, School, Teacher and Manager filters, scope applied before counting, figure states (value, no
   data yet, not available, not matched), CSV export with a header block and formula neutralisation, audit of every
   export, and the REPORTS menu group with one View and Export permission per report.
2. **Attendance report** (Admin, Director; a Zone Manager for own Teachers, Schools and own row). Teacher attendance %,
   Manager attendance %, working days, present, absent, leave, LOP days, missed check-ins, by person, School, Zone and
   organization. Reads 008 rollups (with the 037 School working days when stored), 032 and 009.
3. **Payroll cost report** (Admin, Director only). Gross, LOP deduction, reimbursements and net by Teachers, Managers and
   other staff, by Zone and School, run state, cycle table by month. Reads 013b.
4. **My Report**. A Manager sees attendance and leave for Teachers in their Schools (within the Attendance report scope);
   a Teacher sees only their own attendance, leave and payslip history.

Out of scope: School, Collections, Margin (014b); Recruitment, Teacher, Manager, Expenses (014c); scheduled or emailed
reports, PDF or Excel, charts, saved filters, editing any figure, dashboard tile changes.

## Reuse (do not duplicate)

- `reporting.api` (028): `TileContext` scope resolution, `ZoneResolver`, the "not available" and "no data" rules, and
  `ManagerColumns` for any Manager column. Add a `ReportSource` beside `TileSource`; do not change tiles.
- `attendance.api`: `AttendanceReadApi.rollupOf`, `lockedTeachers`, `ManagerAttendanceReadApi.months`, `WorkingCalendar`,
  `SchoolWorkingDays`, `LossOfPayDays`.
- 013b run data (no public read interface today; the plan adds one in payroll, read-only).
- 008, 013b and 015 exports for the file style and audit action names.

## Decisions carried (2026-10-27, "defaults")

Split 014a/b/c; margin on payslip net (014b); pay split by placement days (014b); Zone Manager sees margin per School
and per Teacher but not the payroll report; default month = last locked month with a warning label for open months.
