# Quickstart: Mobile Attendance

How to prove the feature works end to end. Contract: [mobile-attendance-screens.md](contracts/mobile-attendance-screens.md).
Data: [data-model.md](data-model.md). Setup of the emulator, backend and app: `docs/running-mobile.md`.

## Prerequisites

- Backend, database and web app running with the demo data (`docs/running-locally.md`); the attendance
  demo data (Tamil Nadu 2026 holidays, status codes, marks for the demo Teacher) is seeded.
- The app from spec 018 installed on an Android 10+ emulator, signed out.
- Demo accounts: Tara Teacher (`9800000004`, one-time code printed in the backend log), Manoj Manager
  (`manoj.manager` / `Password123!`), Divya Director (`9800000002` / `Password123!`).
- A second Manager with a Teacher of their own (create it on the web) to test scope.

## Run the automated tests

```powershell
cd mobile
npm test              # includes the attendance suites
npm run lint
npm run typecheck
```

## Validation scenarios

1. **Teacher month (US1)**: sign in as Tara. Menu shows My Attendance, Attendance History, Holiday
   Calendar and no Manager or Admin entries. My Attendance opens on the current month with totals.
2. **Month picker (SC-002)**: reach January and December of the current year in two taps; no other
   year is offered on this screen.
3. **Mark today (SC-001)**: tap today, choose Present, whole day, Save. The cell shows Present with
   your name and the time; totals update. Time it from app start: under 30 seconds.
4. **Correct and half day**: change today to a half day. The cell shows the half-day marker. On the web,
   open the day's history: both values are there, marked from the Android app.
5. **Refusals (SC-004)**: try a future day (no Save offered), a day older than 3 days, a day set by
   Manoj (message says ask your Manager), and a day in a locked month (lock it on the web as Admin). Each
   shows a plain message and leaves the day unchanged.
6. **Offline save (SC-006)**: turn on airplane mode, Save. The app says there is no connection and keeps
   your entry; the web shows no change. Turn it off and Save again: saved.
7. **History (US2)**: Attendance History offers the current and the previous year, shows locked months as
   locked, and has no edit action.
8. **Holiday Calendar (US3)**: as Tara and as Manoj, open it. Holidays and weekly offs match the web
   calendar; "All holidays this year" lists every holiday in date order; there is no edit action. Sign in
   as Divya: the same read-only view, no grids.
9. **Manager list (US4, SC-005)**: as Manoj, Teacher Attendance lists only his Teachers with School and
   totals; the second Manager's Teacher never appears; the search box filters by name.
10. **Manager marks**: open a Teacher, mark a day, correct it, clear it, open the day's history. Tara can
    no longer change the day she was given by Manoj.
11. **Out of scope**: with the second Manager's Teacher id, open the Teacher view from a stale screen (test
    hook) or call the endpoint: the app shows "not found" and no data.
12. **Menus follow the server (SC-007)**: as Admin on the web, remove Tara's My Attendance permission;
    after the app's next menu refresh the entry is gone and an open screen shows "Not authorized".
13. **Audit (SC-009)**: as Admin on the web, AUDIT → Change History shows each mark with the app as the
    source; AUDIT → API Access shows the calls with the device location.
14. **Accessibility (SC-008)**: with TalkBack on, every calendar cell is announced with weekday, date,
    state and status; the month picker, day sheet and list are fully operable; check light and dark.
15. **Wrong phone clock**: set the emulator's date a day off. Which days can be changed is unchanged,
    because the server decides; only the default month may differ.

## Expected outcomes

Matches SC-001 to SC-009 in `spec.md`. Record results in `quickstart-results.md` when run.
