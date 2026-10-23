# Quickstart: Teacher Transfer and Replacement

How to prove the feature end to end once built. Endpoints: [contracts/transfer-api.md](contracts/transfer-api.md); interfaces:
[contracts/module-interfaces.md](contracts/module-interfaces.md); tables: [data-model.md](data-model.md).

## Prerequisites

- Backend running with the demo seeder (`docker-compose.yml` or `scripts/run-all.ps1`), migration applied.
- Demo users: Admin, Director, Manager A (Schools A1, A2) and Manager B (School B1), a placed Teacher T1 at A1, a placed
  Teacher T2 at A2, an unplaced active Teacher T3, a Teacher in training T4, and a School with a vacant position.
- Frontend running; sign in as each role in turn.

## 1. Transfer (US1)

1. As Manager A, OPERATIONS then Transfers & Replacements, Request a transfer: T1 to a School with a vacant position, a date
   next week, reason Manager rebalancing. Expect one Pending request, deciders notified, T1's placement unchanged.
2. Try again for T1: refused (one open request). Try T4 (in training) and a School with no vacancy: refused with the reason.
3. As Manager A, open the request: no Approve button; calling approve directly returns 403. As Admin: no Approve either.
4. As the Director, approve: status Approved, T1 notified. Read T1's history: A1 ends the day before, the new School starts on
   the date, reason and approver shown.
5. As Manager B (not involved), open the request id: 404.

## 2. Replacement (US2)

1. As Manager A, Replace a Teacher at A2 (T2); the candidate list shows T3 and not placed Teachers or T4.
2. Name T3, a date next week, and a destination for T2 or none. Approve as the Director.
3. Read A2's occupancy for the day before, the date and the day after: filled each day, never double. With no destination, T2
   shows in the available list from the date.
4. Try naming a placed Teacher by calling the API directly: refused.

## 3. Placement rules around the date (US3)

1. Approve a transfer for tomorrow. Mark the Teacher present today (old School). Approve leave spanning tomorrow: leave days
   from tomorrow carry the new School; earlier days stay with the old.
2. Raise a transfer for a Teacher with a non-leave mark today: refused.
3. Give the Teacher an open substitution requirement at the old School for a date after the move: after approval it shows at the
   new School, OPEN, and the assigned substitute was told.
4. Run the payroll read for the month: two consecutive placement spans meeting on the effective date, no gap, no overlap.
5. Lock the month and raise a request in it: refused; an approved payroll run is never changed.

## 4. Reject, cancel, stale (US1, US5)

1. Reject without a reason: refused; with a reason: Rejected, requester notified, nothing moved.
2. Cancel an Approved future request with a reason: placements restore, T1 stays at the old School, history shows it.
3. Approve a future request, then move the Teacher directly through the existing Teacher action: after the daily job (or on the
   next read) the request shows Cancelled "Teacher no longer placed there".
4. Two deciders approve at once: one succeeds, the other gets 409.

## 5. History and lists (US4, US5)

1. Open T1: the placement history shows Initial assignment and the Transfer line. Open the School: arrivals and departures.
2. Teacher list filter Transferred and Replaced show the right Teachers within the caller's scope.
3. Request list filters by status, School and dates; Applied shows once the date arrives with no job run.

## Automated

- Backend: `./mvnw -pl backend test -Dtest='Transfer*,Movement*,PlacementMoves*,PlacementAttendance*,PlacementSubstitution*'`, plus
  `ModulithVerificationTest` and the module-rule ArchUnit tests.
- Frontend: `npm test -- transfers` in `frontend/`.
