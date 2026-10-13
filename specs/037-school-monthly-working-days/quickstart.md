# Quickstart: validating School Monthly Working Days

Run guide only; details are in [data-model.md](./data-model.md) and [contracts/](./contracts/). See
`docs/running-locally.md` for starting the stack.

## Prerequisites

- Backend and frontend running locally with the dev seed (Admin, Director, Manager, Teacher, System users, several
  Schools in two Zones; at least one School with a weekly-off override in the spec 008 calendar).
- The seed adds a few rows for the current year (the dev seeder for this feature).

## Backend checks

```
cd backend
./mvnw -q -Dtest='SchoolWorkingDays*Test,WorkingDaysRulesTest,WorkingDaysModuleRulesTest' test
./mvnw -q -Dtest=ApplicationModulesTest test      # module boundaries still hold
```

Expected: all pass, including the per-role endpoint tests, the stale-version and row-deleted tests, the 50-School query
count test and the ArchUnit rules.

## Walkthrough (browser)

1. Sign in as **Admin**. MASTER DATA -> School Working Days appears; the grid shows the current year.
2. **Add** School A, March, 26. The row appears; days in month 31; the calendar-derived count is shown beside it.
3. **Add** School A, March again: refused, the message names the existing row and offers Edit.
4. **Edit** inline to 25.5, then to 25.3 (refused, "0 to 31 in half days"), then 32 (refused), then Cancel (figure
   unchanged). The finalised-month notice shows beside Save.
5. Open **History** for the row: CREATE, EDIT, in order, newest first.
6. **Delete** the row: the confirmation names School A and March, states the calendar fallback and shows the notice.
   Confirm. Open **Deleted rows** for School A and the year: March, last figure 25.5, who and when.
7. **Copy year**: School A, 2026 to 2027. Months already present are listed as existing; 29 working days into February
   2027 is listed as exceeding.
8. Open the same row in two browser windows, edit in both: the second save is told the row changed and shows the current
   figure. Delete in one, edit in the other: the edit is refused with "deleted", the row leaves the grid, nothing is
   recreated.
9. Sign in as **Director**: View, Add, Edit and Copy work; no Delete button anywhere.
10. Sign in as **Manager**, **Teacher** and **System**: no menu item; opening the address shows the not-authorized page;
    the API returns 403.
11. Narrow the window to phone width: no horizontal page scroll.

## Read interface check

In a backend test or the Spring shell, call `SchoolWorkingDays.forYear(allSchoolIds, 2027)`: Schools with rows show
`STORED` for those months and `CALENDAR_DERIVED` elsewhere; deleting a row flips that month back at once.

## Performance check (SC-008)

Load 500 Schools x 12 months for a year with the load helper in the test sources, request `GET
/api/v1/school-working-days?year=2027&size=25`: under 2 seconds, and the query count equals the one for 5 Schools.
