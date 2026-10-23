# Quickstart: Manager Activity Calendar

How to prove the feature end to end once built. Endpoints are in [contracts/calendar-api.md](contracts/calendar-api.md); the feed
changes in [contracts/source-feed-amendments.md](contracts/source-feed-amendments.md); fields in [data-model.md](data-model.md).

## Prerequisites

- Backend running with the demo seeder (`docker-compose.yml` or `scripts/run-all.ps1`), migration V70 applied.
- Demo users: an Admin, a Director, Managers A and B in Zone 1, Manager C in Zone 2, a Teacher account. Today is the 12th of the
  month under test (use the test clock in automated runs).
- Frontend running; sign in as each role in turn.

## 1. Prepare the month (US1, SC-002)

Create for Manager A: a school visit on the 5th and complete it; a campus drive on the 8th and mark it held; a task due on the
10th left open. Add: a visit on the 14th moved to the 20th; a cancelled visit; a 3-date drive; a visit with A and B as
attendees; a visit with no School; a task due on the 10th completed on the 12th.

## 2. Director view (US1)

1. As Director open Activity Calendar for the month: the 5th shows Done, the 8th Done, the 10th Missed; summary 3 planned,
   2 done, 1 missed for A's first three items, and the full month's counts equal the hand count.
2. The moved visit appears on the 20th as Rescheduled with "first planned 14/MM"; the cancelled one is struck through and outside
   planned; the shared visit shows once under A and once under B but the total counts it once; the 3-date drive shows on each
   date and counts as one drive; the unplaced visit says "No place recorded".
3. Open an item: read-only detail with a link to its screen; no complete, move or cancel control exists.
4. Choose Zone 2 only: only Manager C's items and a recount. Choose an empty month: zero counts and the empty message.

## 3. Manager view (US2, SC-004)

1. As Manager A: only A's items (including the shared visit), A's own counts. Manager B sees the shared visit too, not A's others.
2. Call the API as A with `managerId` of B: 404. With `zoneId` of Zone 2: nothing. Repeat the two-Managers-in-a-Zone and
   two-Zones matrix over every filter combination in the integration test.
3. As the Teacher account and as the System account: 403.

## 4. Needs attention (US3)

1. As Director open Needs attention for the month: A's missed task with days late, oldest first, grouped by Manager; the moved
   visit under its Manager with days moved; Manager C with a clean month is absent.
2. Complete A's missed task late in the tasks screen and reload: it is Done with "done late" and leaves the list. Move the
   visit again: still one entry with both moves.

## 5. Filters and layout (US4)

Filter by Manager, Zone, kind and status in turn (including "Task, Missed"): grid, list and summary agree each time. A day
with 9 items shows a few and "6 more". Change month, refresh the page: month and filters are kept.

## 6. Training and resilience (US5, SC-006)

- With 024 absent: the calendar loads, the Training filter reads "not available yet", a note says training is not included.
- In a test, make the task source throw: drives and visits still show, a note names Tasks, `partial` is true.

## 7. Read-only and speed (SC-005, SC-007, SC-008)

- Run the ArchUnit rule (GET mappings only, no entity or write call) and the HTTP test that POST, PUT, PATCH and DELETE on the
  base path return 405.
- Load a month with 500 items across 20 Managers: under 3 seconds.
- Complete a visit and move a drive date in their own screens and reload the calendar: both changes show with no other step.

## 8. Regression of the amended feeds

Run the 032 Field day and 034 tests unchanged: `plannedBetween` of the three feeds returns what it did before (planned visits
only); the new `activitiesBetween` contract tests cover completed, cancelled, `completedOn`, `originalDate` and attendees.
