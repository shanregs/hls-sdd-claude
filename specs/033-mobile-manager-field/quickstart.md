# Quickstart: Mobile Manager Field Day

Validates spec 033 end to end. Prerequisites and the device setup are in `docs/running-mobile.md`; the backend and the
database are in `docs/running-locally.md`. No backend change is part of this spec, so the backend of `main` is enough.

## Prerequisites

- The stack running (`.\scripts\run-all.ps1`, or `start-db.ps1` and `run-backend.ps1`) with the demo data, which has a
  Manager user (see `docs/running-locally.md`) with a joining date, a Teacher, and a Director.
- For a Field day: a planned activity for today for the Manager (a college drive from spec 016, a marketing visit from 023,
  or a task from 025 dated today), created on the web.
- An Android emulator (Pixel 7, API 34) with a mocked location (Extended controls, Location) and the app built with
  `npx expo run:android` from `mobile/`. Sign in as the Manager.

## Automated checks

```powershell
cd mobile
npm run typecheck
npm run lint
npm test
npm run check:manifest
```

Expected: all pass. The tests cover each story against the fake server: check-in (Office, Field with and without an
activity, refused for each 032 reason, photo refused, location unavailable sends nothing, a fresh position each tap),
check-out and completion, Home states, the month and its flags, leave, forgotten check-in requests, menu visibility per role
and permission, and that a failed or offline action is never shown as done. The manifest check fails if a microphone,
background location or broad storage permission appears.

## Walkthrough (emulator or phone)

1. **Menu**: sign in as the Manager. MY ATTENDANCE shows Check-in, My Month and My Leave. Sign in as a Teacher and as the
   Director: none of the three appears.
2. **Home, not checked in**: the card says Not checked in and offers Check in; today's planned activities are listed.
3. **Location refused**: in the emulator turn Location off, tap Check in: nothing is sent and the message says to turn it on.
   Turn it on, deny the app's permission, tap again: the message says to allow it, with Open settings.
4. **Office check-in**: allow location, choose Office, tap Check in. Home shows "Checked in at <time>" from the server and
   offers Check out. A second check-in attempt is not offered.
5. **Field check-in** (next day, or after the Director corrects the day on the web): choose Field, tick today's activity,
   take a photo (emulator camera) and Check in. The day shows the activity and the photo with its file name, type and size.
   Try Field with nothing selected: the app says to pick an activity or write a purpose.
6. **Photo failure**: cut the connection right after the check-in so the photo upload fails: the check-in stays and Add
   photo remains.
7. **Check-out**: tap Check out. Home shows "Checked out at <time>".
8. **Missed check-out**: on the web, correct a past day to have a check-in and no check-out (or check in and wait a day).
   Home lists "No check-out on <date>"; Complete check-out with a time and a reason clears it; a missing reason is not sent.
9. **My Month**: compare the days, flags and figures with the web Manager Attendance screen for the same Manager (SC-003).
   Lock the month on the web: the app shows Locked and offers no change.
10. **Leave**: request three days, see Pending, approve it on the web, pull to refresh: Approved. Reject another: the reason
    shows. Cancel a Pending one.
11. **Forgotten check-in**: ask to add a check-in for yesterday with a time and reason; approve on the web; the day shows
    "Added later".
12. **No connection**: switch the emulator offline and try Check in, Check out and a leave request: each says there is no
    connection, keeps the entries and shows nothing as done.
13. **Permissions**: on the web remove Create from the Manager's `MY_MANAGER_ATTENDANCE`; after the next load the buttons are
    gone.

## Results

Record outcomes and timings (SC-001, SC-008) in `quickstart-results.md` once implemented, marking anything not done as
**not done**.
