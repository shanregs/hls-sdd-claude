# Quickstart: Teacher Observation and Performance

How to prove the feature end to end once built. Endpoints are in [contracts/performance-api.md](contracts/performance-api.md);
tables in [data-model.md](data-model.md).

## Prerequisites

- Backend running with the demo seeder (`docker-compose.yml` or `scripts/run-all.ps1`), migration V60 applied.
- Demo users: an Admin, a Director, two Managers (A and B) with their own Schools and placed Teachers, and a Teacher account.
- Frontend running; sign in as each role in turn.

## 1. Plan an observation (US2)

1. As Director, plan an observation for a Teacher at Manager A's School: date in 3 days, reason Management plan.
2. Expect one Observation task for Manager A due that date and one in-app notification.
3. Plan the same Teacher again: no second task or notification; the date and reason change.
4. Try a Teacher in training or on leave: refused with the reason. As Manager B, try Manager A's Teacher: 404.

## 2. Record and submit an observation (US1)

1. As Manager A, open the Teacher and start an observation; the School, observer, date and Principal name are filled in.
2. Rate every area, choose Good, add strengths, do not record permission, submit: accepted, "Permission not recorded" shown,
   average shown, the Observation task completes.
3. Correct it with a reason: a new version, the earlier one readable by the Director.
4. Record a second visit as Not held, reason Principal declined: no ratings needed, the task stays open, the visit is flagged in
   the Director's list, and Manager A can still plan another.
5. Check Manager B cannot read it and the Teacher account cannot reach any endpoint (403).

## 3. Needs improvement loop (US4)

1. Submit an observation rated Needs improvement: an issue opens at once (Open).
2. Record a corrective action with a follow-up in 14 days: Under correction, round 1, one follow-up task due that date.
3. Submit a follow-up observation rated Needs improvement: a further corrective action is asked for (round 2). Rate the next
   Good: the issue is Resolved - improved.
4. As Director, cancel an Open issue with a reason: Cancelled and kept in the history.

## 4. Principal feedback (US3)

1. Record feedback with action required: the offer to open an issue appears; accept it or plan an observation from it (the task
   reason is the feedback).
2. Withdraw feedback with a reason: marked Withdrawn, still in the history.

## 5. Referral (US5)

1. Submit an observation rated Unsuitable: an issue opens and a referral is asked for.
2. Record a referral (Replace, reason of 20 or more characters): Referred, every Director notified.
3. Read it through the 027 interface (integration test) with its supporting records; report a decision and see Resolved -
   replaced. Alternatively close it as Resolved - continue as Director.

## 6. Lists and Teacher page (US6)

- As Director: filter observations by Manager, School and result; open Planned, Overdue, Open issues and Referred; open a
  Teacher page and see everything in date order, latest result first.
- As Manager A: only own Teachers' records appear.
- Call the summary for a period and compare the counts with the lists.

## 7. Automatic checks

- Move the Teacher's School to Manager B (or change the School's Manager) and run the reconcile job: the open task is
  reassigned. Mark a Teacher Exited with an open issue: after the job, the issue is Resolved - exited and the plan is Cancelled.
- Run the backend suite (API tests per role and scope, status machine, average and suggestion, ArchUnit) and the frontend
  tests (menu and buttons per role).
- Load 2,000 observations and 500 issues with the demo seeder's bulk option: lists answer in under 2 seconds.
