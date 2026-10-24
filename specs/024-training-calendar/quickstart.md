# Quickstart: Training Calendar

How to prove the feature end to end once built. API: [contracts/training-api.md](contracts/training-api.md); feed:
[contracts/planned-activity-feed.md](contracts/planned-activity-feed.md); tables: [data-model.md](data-model.md).

## Prerequisites

- Backend with the demo seeder (`docker-compose.yml` or `scripts/run-all.ps1`), migration applied; frontend running.
- Demo users: Admin, Director, Manager A (Schools A1, A2) with Teachers T1, T2 and Manager B with Teacher T3; Manager C with
  no Teachers; an unplaced recruit in induction; Teacher T4 with approved leave next Saturday.

## 1. Weekend refresher (US1)

1. As Admin: OPERATIONS then Training Calendar, New session, Weekend refresher, seat limit 3, publish.
2. As T1, T2, T3 (Upcoming Training): register. T4 or the recruit: refused. A fourth Teacher: "no seats left"; cancel T3, the
   fourth now registers. After the start time cancelling is refused.
3. Admin moves the date: registrations kept, notified, earlier date listed in the history.

## 2. Standing monthly pattern (US2)

1. Admin: New pattern, second Saturday 10:00 to 13:00, preview shows the next dates; save. Three months ahead appear.
2. Edit one month's trainer (only that month changes), skip another with a reason, edit the pattern from month 3 on, stop it
   from month 5. Run the generation job twice: no duplicates.

## 3. Assign (US3)

1. Open a monthly session, Assign: Manager A's group, plus Manager B. Preview shows counts; confirm. Everyone is notified and
   sees "Required". Assign T1 again: still one obligation. Remove one before the session.
2. As T1: "I cannot attend" with a note: Admin gets a notice, the obligation stays.

## 4. Attendance (US4)

1. On the session day as Manager A: only T1 and T2 are listed. Mark T1 Present, T2 Absent with reason. Marking Manager B or
   yourself is refused.
2. As Admin: mark Manager A Present; T4 (leave) shows "On leave". T3 already has a school Present mark that day: the row saves
   with a clash note and Teacher attendance (OPERATIONS, Attendance) is unchanged. Open T1's month in 008: the day is a
   training day, value 1.00. Correct T1 to half day: 0.50 and a history line.
3. Lock T2's month in 008, then try to correct T2: refused with the 008 message.

## 5. Exceptions and history (US5, US6)

1. As Director: Training Exceptions shows T2 Open; filter by month and Zone. Close as Excused with a reason. Record T2 absent at
   a second session within 6 months: marked repeat.
2. A registered Teacher who skipped the weekend refresher shows as No-show and has no exception.
3. Open T1's history: sessions, outcomes, induction entry (read-only), yearly attended versus expected. As T1: same, without
   internal remarks; as Manager B opening T1: 404.

## 6. Calendar feed (US7)

1. As Manager A open the Manager Activity Calendar (035): the monthly session appears as Training on its date, Planned, then
   Done after attendance is saved; a moved session shows as rescheduled; "Training is not included yet" is gone.
2. Create a Field day (032) for Manager A on a session date: the session is offered as a link.
3. Two days before a session, run the reminder job: each assigned or registered person gets one reminder.

## Checks

- Backend: unit, API, scope, integration and ArchUnit suites for the `training` module and the amended 035 and 032 tests.
- Frontend: Vitest for grants (Manager View only, Teacher registration) and the attendance grid results.
- Timing: 100-person assign and attendance pass under the SC-002 and SC-008 limits.
