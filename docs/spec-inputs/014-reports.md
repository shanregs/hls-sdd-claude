# 014 Reports: `/speckit-specify` input

2026-10-27. Roadmap row 014 (expanded), decisions D12 and D14, amendment "014: add the deck's reports". Reads from the
merged specs 008 and 032 (attendance), 009 (leave), 013a and 013b (payroll), 015 (expenses), 016 (recruitment), 012 and
022 (contracts and receivables), 023 (marketing), 024 (training), 025 (tasks), 026 (performance), 027 (transfers),
028 (dashboard) and 035 (activity calendar). Branch `spec/014-reports`.

## Feature description (paste as the argument to `/speckit-specify`)

014-reports: The REPORTS menu of the HLS platform. A read-only set of month-based reports that answer the questions the
Director deck lists (slides 14 to 16), each with filters and a CSV export. The module owns no transactional data and no
table of figures: every figure is read from the owning module's public interface, and where the Management Dashboard
(spec 028) already has a figure, the report reads the same source so a tile and its report never disagree. The module is
`reporting` (the module spec 028 created), with a new report package beside the tile package. REPORTS is already a menu
group in the access model (spec 002).

1. **Attendance report** (Admin, Director; a Zone Manager for own Teachers and own Schools). Per Teacher and per School
   for a month: working days, present, absent, leave, LOP days, and **Teacher attendance %**; per Manager for a month:
   working days, checked-in days, leave, missed check-ins and **Manager attendance %**. Rolls up by Zone and for the
   organization. Reads 008 rollups (with the 037 School working days when stored), 032 and 009. A month that is not
   locked is labelled "open, figures may change".
2. **Payroll cost report** (Admin, Director). Per month: payroll cost by Teachers, Managers and other staff, by Zone and
   by School, gross, LOP deduction, reimbursements and net, and the run state (draft, approved, paid). Month-on-month
   for an academic cycle. Reads 013b. A Zone Manager has no access to pay.
3. **Teacher report** (Admin, Director; a Zone Manager for own Teachers). Teachers by status (in training, active, on
   leave, exited), joiners and exits in the month, placement (School), tenure, observations and open performance issues
   (026), transfers and replacements (027), training attendance (024). One row per Teacher, with a drill link to the
   Teacher.
4. **School report** (Admin, Director; a Zone Manager for own Schools). Per School for a month: Teachers placed, contract
   term and end date, attendance %, absences covered by substitution (017a), observations and issues, billed, collected
   and outstanding (022), prospects to active stage (023).
5. **Recruitment report** (Admin, Director; a Zone Manager reads the same counts as in 016). By college and season:
   drives held, candidates, interviewed, selected, offers issued and accepted, joiners, joining ratio, induction
   completed and ready to deploy (016), plus demand versus supply (023).
6. **Manager report** (Admin, Director; a Zone Manager sees only their own row, as in 035). Per Manager for a month:
   tasks assigned, completed, overdue, escalated (025), planned versus completed activity, missed and rescheduled
   (035), visits and Schools won (023), training attended (024), attendance % (032), expense claimed (015). This is the
   "Manager productivity" report; it reuses the 028 comparison columns (`ManagerColumns`) rather than recomputing them.
7. **Expenses report** (Admin, Director). Spend by area, category and month, budget versus actual, pending and ageing,
   cost per School acquired and per Teacher trained (015). Reads the 015 figures; a Zone Manager has no access.
8. **Collections report** (Admin, Director; a Zone Manager for own Schools). Billed, collected, outstanding, and ageing
   (0 to 30, 31 to 60, 61 to 90, over 90 days past due) by School, Zone and organization (022).
9. **Margin report** (D14: Admin, Director, and a Zone Manager for own Schools only). **School fee minus Teacher pay**
   per Teacher, per School and per month, with totals by School and by Zone. The fee is the 022 monthly due line for the
   Teacher; the pay is the 013b payslip net (or cost to HLS, see clarification). A Teacher paid in a month with no due
   line, or a due line with no payslip, is shown as "not matched" and left out of the total, never as zero.
10. **Teacher attendance and leave report** (Manager for assigned Teachers; the Teacher's own report for a Teacher).
    A Manager sees attendance and leave for the Teachers in their Schools; a Teacher sees only their own attendance,
    leave and payslip history (read-only, no export beyond their own).
11. **Filters and shape.** Every report takes a month (default the last locked month, else the current), a Zone, and
    where it applies a School, Teacher or Manager. Rows are scoped by the caller's data scope before anything is
    counted, so a total never includes a record the caller cannot see. A figure with no source data shows "no data
    yet"; a source that is not built or fails shows "not available" for that column only, never a zero.
12. **CSV export** for each report, for the same filter and scope the screen shows (Admin and Director; a Zone Manager
    only for the reports the Manager can open, own scope). The file carries the filter, the month, the state of the
    month (open or locked) and the time it was made in its first rows. Every export is recorded in the audit trail (003)
    with who, which report, the filter and when.
13. **Screens and permissions.** REPORTS → Attendance, Payroll, Teachers, Schools, Recruitment, Managers, Expenses,
    Collections, Margin. One permission per report on the `REPORTS` module group (View, Export) so that access can be
    set per role, with the roles below as the default. Payroll and Margin figures are only visible to a caller who holds
    the matching grant.

Roles (default): Admin and Director see all reports and export. Zone Manager sees Attendance, Teachers, Schools,
Recruitment (16 counts), Collections and Margin for own Schools, and their own row of Managers; no Payroll or Expenses.
Teacher sees only their own attendance, leave and payslip history. System has none.

Out of scope for version 1: scheduled and emailed reports, PDF or Excel files (CSV only), charts and trends beyond the
month and academic-cycle table, saved filter sets, editing any figure, drill-through to a record the caller cannot open,
incentives (030), tax and statutory reports, the Teacher and System dashboards, and reports of unbuilt modules (they
show "not available"). Changing the dashboard tiles is not part of this spec.

## Reuse (do not duplicate)

- `reporting.api.TileSource`, `TileContext` and `ManagerColumns` (spec 028): a report is a new `ReportSource` beside it,
  owned by the module that holds the data, using the same scope and "not available" rules. Reports and tiles for the same
  figure call the same query in the owning module.
- Existing exports and queries: 008 month export, 013b run export, 015 monthly claims export, 022 balances and ageing,
  024 history export. The report reads their queries, not their files.
- `attendance.api.WorkingCalendar` and `SchoolWorkingDays` for working days; `PlacementAttendance` (027) for placement
  days.

## Should it be split?

Yes, recommended, because the nine reports touch ten modules and carry different audiences (pay is Admin and Director
only; margin and collections reach Zone Managers):

- **014a Attendance and Payroll**: reports 1, 2, 10 and the shared report framework (filters, scope, CSV, audit).
- **014b School, Collections and Margin**: reports 4, 8, 9 (needs 012, 022, 013b together; the highest-value D14 item).
- **014c Recruitment, Teacher, Manager and Expenses**: reports 3, 5, 6, 7 (mostly reuse of 016, 026, 035, 015 figures).

If kept as one spec, the order of delivery is the same and the framework lands first.

## Points for `/speckit-clarify`

See `specs/014-reports/clarification-questions.md`.
