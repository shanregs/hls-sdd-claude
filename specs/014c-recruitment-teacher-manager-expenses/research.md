# Research: 014c

2026-10-27. Decisions, with the alternative rejected.

1. **Reuse `ManagerColumns` for the Manager report.** Decision: inject every `ManagerColumns` bean and add them as columns. Why: equals the dashboard comparison table by construction (FR-005). Rejected: report-only read interfaces (Q2 option C) duplicate logic.
2. **035 contributes a `ManagerColumns` bean rather than a public type.** `activitycalendar` already depends on `tasks`, `recruitment`, `training`; adding `reporting.api` matches how `tasks` and `recruitment.marketing` contribute columns; `reporting` does not depend on `activitycalendar`, so no cycle. Figures reuse `CalendarSummary.byManager`.
3. **Season through the framework (A0).** 016 counts by season label, the 014a request has only a month. A new `season` request field is additive; deriving the season from the month was rejected because the season label is free text on the drive and can span months.
4. **Teacher joined/exited needs dates the directory does not expose.** `TeacherView.statusEffectiveOn` is web-only; add a bulk `lifecycle` to `TeacherDirectory` (A3). The joining-date field (005a) is confirmed in the task before use; if absent, tenure and joiners fall back to the earliest placement start and the task is noted in the amendment.
5. **Per-Teacher performance and movement counts.** Existing `byManager`/`byZone` and id sets cannot give per-Teacher month counts; add `byTeacher` (A4) and `movementsBetween` (A5) with the same counting rules.
6. **Expense claimed per Manager** counts the claimant's own submitted and approved claims in the month (A9), the same claims My Expenses shows them. Budget against actual delegates to `BudgetQueries.overview` (A10).
7. **Pending claims are as of today**, not for the chosen month; labelled (FR-010).
8. **Performance**: every source is bulk (a fixed number of queries); sections are read independently under the 014a time limit.
9. **No migration**; numbering checked against main and the branches that carry V130 and V140.
