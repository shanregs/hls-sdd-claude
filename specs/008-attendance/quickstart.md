# Quickstart: Validating Attendance

Prerequisites: the local stack from `docs/running-locally.md` (`scripts\start-db.ps1`,
`run-backend.ps1`, `run-frontend.ps1`) with the demo data on, so Asha (Admin), Divya (Director),
Manoj (Manager, manages Demo School One), Tara (Teacher, placed there, OTP sign-in) and Sunil
(System) exist. Endpoints are in [contracts/attendance-api.md](./contracts/attendance-api.md).
Tara's placement starts the day the demo data was seeded, so mark days from then on.

## Scenario 1: Teacher marks and corrects (User Story 1)

1. Sign in as Tara; open MY ATTENDANCE -> My Attendance. **Expect**: the current month calendar.
2. Mark today Present (whole day), yesterday Present (half day). **Expect**: both saved with
   "Demo School One" and her name as the setter.
3. Change today's mark to Leave. **Expect**: the calendar shows Leave; the day's history lists
   both values and who set them.
4. Try a date 5 days ago and tomorrow. **Expect**: refused with the reason (needs her Manager /
   future date).

## Scenario 2: Manager grid and supervisor protection (User Story 2)

1. Sign in as Manoj; open OPERATIONS -> Teacher Attendance. **Expect**: only Tara (and not the
   unplaced teacher) as a row; day columns equal the month's real length.
2. Set a day Tara marked herself to Present. **Expect**: attributed to Manoj; Tara sees "set by
   Manoj" and can no longer change that day (try it).
3. Request another Manager's Teacher by id. **Expect**: 404.

## Scenario 3: Rollup (User Story 3)

1. As Asha mark a known month for Tara: 10 Present whole days, 2 half days, 2 Leave days, 1 Training
   day, and add one holiday on a day she marked nothing. **Expect**: working days = calendar days -
   Sundays - the holiday; days worked 11; leave 2; training 1; weighted total 12; unmarked = the rest
   of the past working days.
2. Change one mark. **Expect**: the rollup updates immediately.

## Scenario 4: Admin/Director grid and filters (User Story 4)

1. As Divya open OPERATIONS -> Attendance. **Expect**: all placed Teachers; filter by School, Zone
   and Manager; search by name; totals follow the filter.
2. Correct a cell. **Expect**: attributed to Divya, visible in Tara's calendar.
3. As Tara or Sunil open the Attendance route. **Expect**: no menu item; "not authorized".

## Scenario 5: Setup (User Story 5)

1. As Asha add a holiday for a day inside the current month. **Expect**: every Teacher's working
   days drop by one with no marks.
2. Give Demo School One a Monday-to-Saturday override. **Expect**: Tara's Sundays still off, her
   Saturdays now working days.
3. Add a custom code "Sick" (category LEAVE) and mark with it; then deactivate it. **Expect**: old
   marks stay; it cannot be picked for new ones.

## Scenario 6: Lock and reopen (User Story 6)

1. As Divya lock the *current* month. **Expect**: refused (month has not ended).
2. Lock the previous month with unmarked working days. **Expect**: 409 listing the Teachers and
   dates; nothing locked. Fill them in (as Manager or Admin), lock again. **Expect**: success;
   Manoj's edit of any day in that month is refused.
3. Reopen Tara's month with a reason, change a day, try to relock with a gap. **Expect**: refused;
   fix and relock. **Expect**: the event history shows lock, reopen (reason), relock.

## Scenario 7: Teacher history and export (User Stories 7-8)

1. As Tara open Attendance History and choose the locked month. **Expect**: read-only calendar,
   frozen rollup.
2. As Asha export the month filtered to Demo School One. **Expect**: a CSV with one row per
   Teacher; a Manager or Teacher request for it is refused; AUDIT -> Change History shows the
   export (and System sees nothing about attendance).

## Scenario 8: Day colours and the Holiday Calendar (User Story 9, FR-013, FR-025-FR-027)

1. As Asha open Attendance and go back a month. **Expect**: Sundays read "Sun" on a grey tint,
   holidays read "H" on an amber tint, Leave and Absent marks show their code on a red tint, and a
   legend explains them; the same words are in each cell's accessible name.
2. Open MASTER DATA -> Holiday Calendar. **Expect**: the year with every holiday highlighted; switch
   to Month and move between months; the holiday list matches the highlights.
3. Choose the download icon in each view. **Expect**: a PDF of the year, then of the month, each with
   the highlighted days, legend and holiday list.
4. Sign in as Tara and as Manoj, open Holiday Calendar. **Expect**: the same calendar and PDF, no
   edit controls; as Asha the weekly-off and holiday editing is available below the calendar.

## Automated verification

Run the backend suite (`mvn test` in `backend/`) and the frontend suite (`npm run test` in
`frontend/`); both must be green, including the calculator tables, per-role authorization, the
two-Manager scope tests, the lock/mark concurrency test, `ApplicationModulesTest`, the ArchUnit
rules, and the axe-core checks for every new screen in both themes.
