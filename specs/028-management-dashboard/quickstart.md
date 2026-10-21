# Quickstart: Management Dashboard

How to prove the feature end to end once built. Endpoints are in [contracts/dashboard-api.md](contracts/dashboard-api.md), the tile
list in [contracts/tile-catalog.md](contracts/tile-catalog.md), values in [data-model.md](data-model.md).

## Prerequisites

- Backend running with the demo seeders (`docker-compose.yml` or `scripts/run-all.ps1`). No migration is involved.
- Demo users: an Admin, a Director, two Managers (A in Zone 1, B in Zone 2) with Schools, tasks, visits and Teachers, and a
  Teacher and a System account. Demo data includes an overdue task, a missed visit, a Manager with no check-in past the cut-off,
  a Teacher absent today with an open substitution, one pending Manager leave and a payroll run awaiting approval.
- Frontend running; sign in as each role in turn.

## 1. One screen for today (US1, US2, US3, US4, US5)

1. As Director, open the Dashboard: areas Managers, Schools, Recruitment, Teachers, Attendance and leave, Payroll appear with
   tiles and a "figures taken at" time. No area is blank and no figure is a placeholder.
2. Open each tile: you land on the listed screen with the filter applied and the list's count equals the tile.
3. Overdue tasks, Final Stage, Teachers away and uncovered substitutions match their lists (the parity tests cover every tile).
4. Choose a weekly off in the test clock: attendance tiles read "not a working day", not absences.

## 2. Nothing is zero when it is missing (FR-007, SC-005)

1. With the supply provider disabled, the demand-against-supply tile reads "not available yet", not 0.
2. A ratio with no joiners or offers reads "no data yet".
3. In a test, make one source throw: only its tile shows an error with a retry; every other tile in the area still shows.

## 3. Permissions and scope (US9, FR-015, SC-004, SC-007)

1. As Manager A: only Zone 1 figures and A's own tasks, visits and check-in; no comparison table, payroll or inbox.
   `GET /api/v1/dashboard/managers` and `/inbox` answer 403; payroll area answers 404; Zone 2 as `zoneId` answers 404.
2. As Teacher and System: empty manifest; their own dashboards are unchanged.
3. Remove Payroll View from Admin in the permission matrix: no payroll tile and no payroll value in any response.
4. A user with Manager and Director roles sees the Director's scope once, no duplicates.

## 4. Zone and period (US8)

1. As Director pick Zone 1: zone-aware tiles change; payroll and recruitment cards say "organization-wide".
2. Pick last month: per-month tiles change, "today" tiles do not.

## 5. Approvals inbox (US6, FR-014)

1. With one pending Manager leave and one payroll run awaiting approval, the inbox lists two entries, oldest first, with totals.
2. Open an entry: you reach its decision screen; decide it, then Refresh: it is gone.
3. As the requester of a leave request who is also an Admin, your own request is not listed.
4. Remove Approve on Manager Leave: that kind disappears from list and totals.

## 6. Refresh and no live push (US1, FR-017)

1. Leave the page open and change a task elsewhere: nothing changes until Refresh; Refresh updates the time shown.
2. Throttle one area in dev tools: the other areas appear first.

## 7. Adding a tile later (US7, SC-006)

1. Run the backend tests with the test tile configuration: the tile appears under its area for a user with its grant, is absent
   for one without, and every other tile's result is unchanged.

## 8. Layout

Phone width and dark mode: no horizontal page scroll, figures readable, rupees in Indian grouping, dates DD/MM/YYYY, tiles
reachable by keyboard with the figure and label read together.

## Automated

```
mvn -f backend/pom.xml test -Dtest='reporting.*,*Tiles*,*Approvals*,ArchitectureTest,ModulithTest'
npm --prefix frontend run test -- dashboard
```
