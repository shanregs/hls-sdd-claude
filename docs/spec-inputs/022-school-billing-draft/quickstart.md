# Quickstart: School Contracts and Billing

Runnable checks that prove the feature end to end. Setup is in `docs/running-locally.md` (database, backend
with demo data, frontend). Demo logins (all `Password123!`, or a one-time code): Asha Admin `9800000001`,
Divya Director `9800000002`, Manoj Manager `9800000003`, Tara Teacher `9800000004`, Sunil System `9800000005`.
Endpoints are in `contracts/schoolbilling-api.md`; tables in `data-model.md`.

## Prerequisites

- Specs 001 to 005, 008, 009 and 010 running locally; demo data on.
- Flyway applies `V20` and `V21`; the log shows both. Existing Teacher placements now appear as assignments
  and the Schools that had them have a "rate pending" contract.

## Scenarios

1. **Placements carried over (US2)**: before starting the upgraded backend, note each demo Teacher's School and
   start date on the Teachers screen. After it, the same Teachers show the same School and date, labelled as
   an assignment (no "interim"). `teacher_placement` no longer exists; the row count of `contract_assignment`
   equals the old count.
2. **Contract (US1)**: as Asha open OPERATIONS → School Billing → Demo School One. It shows "no rate set".
   Enter a per-Teacher rate of ₹15,000 from the first of this month: the contract becomes active. Start a new
   contract from the next month at ₹16,000: the first ends the day before, both are in the history. A lump
   sum with a per-Teacher rate in the same request is refused. As Manoj the same screen has no Create or Edit
   for contracts, and `POST /schools/{id}/contracts` answers 403.
3. **Assign a Teacher (US1)**: as Manoj assign a Teacher to Demo School One from the 1st; assigning her to a
   second School on overlapping dates is refused. A School with no contract refuses the assignment with the
   instruction to create the contract first.
4. **Receivable (US3)**: as Asha calculate this month. With 3 Teachers for the whole month the amount is 3 ×
   the rate and the breakdown names them. Assign a fourth from the 14th: recalculate; their line is
   pro-rated by the Teacher's working days from attendance (for example 13 of 26 working days is half the rate), the earlier
   figure is listed as superseded.
5. **Payments (US4)**: as Manoj record ₹4,500 Cash, then ₹9,000 Bank for Demo School One: balance falls by
   each. Reverse the first with a reason: balance rises by ₹4,500 and both entries show. There is no Edit or
   Delete; `PUT` or `DELETE` on a payment answers 405, and a direct `UPDATE` in the database fails on the
   trigger. A ₹1,00,000 payment on a ₹45,000 receivable is accepted and the School shows an advance. Recording
   the same amount and date twice shows the duplicate warning but saves.
6. **Overview and scope (US5)**: as Asha open the overview: expected, collected, outstanding, per Manager and
   per School add up to the totals. As Manoj the lists hold only his Schools and the totals match them. Export
   the CSV as each: Manoj's file has only his Schools. Sunil (System) has no menu item; the API answers 403.
7. **Overdue (US6)**: set the overdue days to 0 and the expected payment day to today's day in Settings, leave
   a balance, and run the daily job (in tests, call `OverdueJob.run()`). The School is flagged and Manoj and
   Divya each get one notification; running again sends nothing; paying in full clears the flag.
8. **Close (a month)**: as Asha close last month while one Teacher's attendance is still open: refused with
   that Teacher's name. Lock attendance for everyone and close it: recalculation is then refused and an
   adjustment line is only accepted in an open month. Divya (Director) has no Close button.
9. **Boundary**: `schoolbilling` is not called from `teacher.internal`, and no module reads billing tables;
   run `mvn -o test -Dtest=ApplicationModulesTest,SchoolBillingModuleRulesTest`.
