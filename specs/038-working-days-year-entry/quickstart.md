# Quickstart: Enter a Whole Year on One Screen

Validates spec 038 end to end. The backend tests need Docker (Testcontainers); the walkthrough needs the local stack.

## Prerequisites

- Spec 037 merged, with its dev seed (demo Schools).
- Check `docker info` works first (the DB script prints "ready" even when Docker is down). Then start the stack:
  `.\scripts\start-db.ps1`, `.\scripts\run-backend.ps1` (8080) and `.\scripts\run-frontend.ps1` (5173).
- Sign in at http://localhost:5173 as `asha.admin` / `Password123!` (other demo users: `docs/running-locally.md`).

## Automated checks

```powershell
cd backend
mvn test -Dtest=YearEntryApiTest,WorkingDaysRulesTest,WorkingDaysModuleRulesTest
cd ..\frontend
npm test -- YearEntryPage yearEntryState a11y
```

Expected: all pass. The backend test covers per-role access on both endpoints, the partial save, the retry (the same Save
twice writes nothing the second time), two-user conflicts, and a statement count that does not grow with the data.

## Walkthrough

1. **Open**: MASTER DATA → School Working Days → **Enter a year**. With the grid filtered to a School and year, they are
   preselected; otherwise pick a School and a future year with no figures.
2. **Hints**: all twelve months show a calendar-derived hint and an empty field; no stored figures yet.
3. **Enter a year**: type figures for all months and press Save once. Expect "12 created", the fields show stored
   figures, and the School Working Days grid lists 12 rows (FR-023). Time it (SC-001: under 1 minute).
4. **Change two months**: edit two fields and Save. Expect "2 updated, 10 unchanged"; only those two rows have a new
   version and a history entry.
5. **Bad figures**: type 31 for a 30-day month and 25.3 for another, then Save. Expect the other changes stored, the two
   months listed in calendar order with the allowed range, the typed values kept, and focus on the first failed month.
6. **Two windows**: open the same School and year in two browsers. Change a month in A and Save. In B change the same
   month to a different figure and Save: B shows that month as changed by someone else with the current figure, and B's
   other months are saved. Repeat with B typing the **same** figure as A: B reports it unchanged.
7. **Deleted meanwhile**: delete a row in the grid (Admin), then Save a change to that month from a stale year screen:
   it is reported as deleted and not recreated.
8. **Phone width**: DevTools device mode at 375 px (drag the content to scroll; the emulated scrollbar cannot be
   dragged). The page has no horizontal scroll, and every field, hint, error message and Save is reachable.
9. **Helpers**: clear a few fields, press "Use calendar counts" (fills only empty months), then "Same figure for all
   empty months" with 30 (February is listed as not filled). Nothing is saved until Save.
10. **Leaving**: type something, then change the School: a confirmation appears. Reload the tab: the browser warns.
11. **Permissions**: sign in as a Director (works). Sign in as a Manager, Teacher and System (no menu item, 403 on both
    endpoints). With a test grant of Create but not Edit, only empty months are editable.
12. **Retry**: with DevTools Network set to Offline press Save, go back online and press Save again: no duplicate rows and
    no duplicate history or audit entries.

## Performance check

Open a School and year (SC-009: under 2 seconds) and Save all twelve months (under 3 seconds).

## Results

Record outcomes, timings and the 375 px check in `quickstart-results.md` once implemented.
