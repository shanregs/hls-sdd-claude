# 014c Recruitment, Teacher, Manager and Expenses Reports: `/speckit-specify` input

2026-10-27. Last third of roadmap row 014. Depends on **014a** (the report framework), 016 (recruitment), 023, 024, 025,
026, 027, 035 and 015 (expenses); reuses the 028 `ManagerColumns`.

## Feature description (paste as the argument to `/speckit-specify`)

014c-recruitment-teacher-manager-expenses: Four reports on the 014a framework, with the same filters, scope, CSV export
and audit. Every figure is read from the owning module; where the dashboard has the figure the report calls the same
source.

1. **Recruitment report** (Admin, Director; a Zone Manager sees the same counts as in 016). By college and season: drives
   held, candidates, interviewed, selected, offers issued and accepted, joiners, joining ratio, induction completed,
   ready to deploy, and demand versus supply (023).
2. **Teacher report** (Admin, Director; a Zone Manager for own Teachers). Teachers by status (in training, active, on
   leave, exited), joiners and exits in the month, placement, tenure, observations and open performance issues (026),
   transfers and replacements (027), training attendance (024); one row per Teacher.
3. **Manager report** ("Manager productivity"; Admin, Director; a Zone Manager sees only their own row, as in 035).
   Tasks assigned, completed, overdue, escalated (025), planned versus completed activity, missed and rescheduled (035),
   visits and Schools won (023), training attended (024), attendance % (014a), expense claimed (015). Common columns
   equal the dashboard comparison table.
4. **Expenses report** (Admin, Director only). Spend by area, category and month, budget versus actual, pending and
   ageing, cost per School acquired and per Teacher trained (015).

Out of scope: School, Collections, Margin (014b); Attendance and Payroll (014a); charts, scheduled output.

## Notes

- Mostly reuse; no new data. 015 and 026 are expected to need small read-only additions only if a figure is not yet on a
  public interface; the plan lists them.
- Decisions carried from 014a Clarifications (2026-10-27): default month is the last locked month with a warning label for
  open months; a Zone Manager has no access to Payroll or Expenses.
