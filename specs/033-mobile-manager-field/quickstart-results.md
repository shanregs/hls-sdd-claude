# Quickstart results: Mobile Manager Field Day

Recorded 2026-10-17 on the implementation branch `impl/033-mobile-manager-field`. Nothing here is marked passed unless it
was run; everything that needs a phone or an emulator is **not done**.

## Automated checks (run)

From `mobile/`:

| Check | Result |
| --- | --- |
| `npm run typecheck` | clean |
| `npm run lint` | clean |
| `npm run check:manifest` | passed (also fails when the picker plugin asks for the microphone or gallery, checked by changing the config) |
| `npm test` | 95 suites, 1,107 tests passed (792 before this feature) |

The new tests are in `__tests__/manager/`, `__tests__/api/` (`upload`, `managerContract`), `__tests__/location/captureFresh`,
`__tests__/attendance/` (`managerDayPresentation`, `monthGridGeneral`) and `__tests__/a11y/manager.a11y`. The 019 and 020 tests
(159 in `__tests__/attendance` and `__tests__/screens`) pass unchanged before and after the month grid was generalised.

## Checks against the running server (run, read-only or refused requests only)

Against the local stack (backend of `main`) as the demo Manager, so the types of `data-model.md` are the real shapes:

- The access model offers MY ATTENDANCE with Check-in, My Month and My Leave on `/manager-attendance/me`, `/me/month` and
  `/me/leave`, each with View, Create and Delete.
- `GET /me/today`, `/me/months/2026-10`, `/me/leave/types`, `/me/leave` and `/me/regularizations` answer with the fields the
  app reads. **This found a mistake in the design:** a month day has ten statuses, not eight. A past working day with no
  check-in and no leave is `ABSENT` (not `MISSED`, which is only today after the cut-off), and `NO_CHECK_OUT` is a status as
  well as a flag. The types, wording, colours, the day sheet and the documents were corrected, and tests added.
- The write endpoints read the field names the app sends: check-in without a position, with latitude 123, and a Field day with
  nothing linked; check-out without a position; completing a check-out without a reason; leave with reversed dates; a forgotten
  check-in for a future day. Each was refused with the expected plain-language reason, and nothing was written.
- Not exercised: a successful write (check-in, check-out, leave, photo), to leave the demo data as it was for the walkthrough.

## Walkthrough on an emulator or phone (not done)

No Android SDK, emulator or phone is available on the machine this was built on, so none of `quickstart.md` steps 1 to 13 was
run on a device. These remain **not done** and are needed before the PR is merged:

| Step | What it proves | Status |
| --- | --- | --- |
| 1 Menu per role | the three entries appear for the Manager only | not done (covered by tests) |
| 2 to 4 Home, location refused, Office check-in | real location prompts and settings screen | **not done** |
| 5 Field check-in with a camera photo | the camera, the gallery, the multipart upload and the thumbnail on the real native stack | **not done** |
| 6 Photo failure | a refused or failed upload leaves the check-in | not done (covered by tests) |
| 7 Check-out | a fresh position at the tap | **not done** |
| 8 Missed check-out | completing a day from Home | not done (covered by tests) |
| 9 My Month against the web screen (SC-003) | the same days, flags and figures as the web | **not done** |
| 10 Leave | request, decision on the web, refresh | **not done** |
| 11 Forgotten check-in | request and approval on the web | **not done** |
| 12 Offline | nothing sent, entries kept | not done (covered by tests) |
| 13 Permissions | a removed permission removes the button | not done (covered by tests) |
| Timings | SC-001 (Office under 30 s, Field under 60 s) and SC-008 (complete a check-out under 45 s) | **not done** |
| TalkBack and the largest text size | the screens are usable with a screen reader | **not done** (a11y tests pass) |

Things only a real build can show, and so the first to look at on a device: the camera permission prompt and its wording; the
gallery picker returning a file the server accepts (a JPG or PNG; other types are refused by the server); the
`FormData` upload with a `file://` picture; the thumbnail drawn from the downloaded bytes; the "Open settings" action; and a new
native package, so `npx expo run:android` must be run again.
