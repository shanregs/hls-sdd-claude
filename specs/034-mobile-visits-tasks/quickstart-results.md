# Quickstart results: Mobile Visits and Tasks

Recorded 2026-10-18 on the implementation branch `impl/034-mobile-visits-tasks`. Nothing here is marked passed unless it was run;
everything that needs a phone or an emulator is **not done**.

## Automated checks (run)

From `mobile/`:

| Check | Result |
| --- | --- |
| `npm run typecheck` | clean |
| `npm run lint` | clean |
| `npm run check:manifest` | passed |
| `npm test` | 116 suites, 1,428 tests passed (1,107 before this feature) |

The new tests are in `__tests__/visitsTasks/` (list, detail, complete, plan, move and cancel, task list, steps, overdue and completion,
evidence, add task, Home cards, menu visibility, never-a-false-success, the API clients and the shared pieces),
`__tests__/a11y/visitsTasks.a11y.test.tsx`, `__tests__/access/useMenuActions.test.tsx` and `__tests__/attendance/businessToday.test.ts`.
The spec 033 Manager tests pass unchanged after the menu-actions hook was generalised and the photo picker was split.

## Checks against the running server (run, read-only or refused requests only)

Against the local stack (backend of `main`) as the demo Manager and the demo Director, so the types of `data-model.md` are the real shapes:

- The Manager's menu offers OPERATIONS → My Tasks (`/operations/tasks`: View, Create, Edit) and MARKETING → Marketing Calendar
  (`/marketing/calendar`: View, Create, Edit, Approve).
- `GET /tasks`, `/tasks/summary`, `/tasks/types`, `/tasks/assignees` and a task's detail (with its `actions` object) answer with the
  fields the app reads. A real visit (read as the Admin, since the demo Manager has none) has exactly the fields of `Visit`.
- The write endpoints read the field names the app sends: planning without a date or with an unknown kind, completing an unknown
  visit, an overdue reason of 3 characters, and a task due in the past were each refused with the expected plain-language reason, and
  nothing was written.
- A Director's `/tasks/assignees` does not include the Director, so "Add a task for me" stays hidden for them, as designed.
- **Found by a test, not by the server check:** when a photo upload from the visit's "Add photos" panel failed, the failure was recorded
  but not shown while the panel stayed open. The never-a-false-success test caught it and it is fixed (the failed photos are listed in
  the panel).
- Not exercised: a successful write (complete, plan, move, cancel, a task step, a photo), to leave the demo data as it was for the
  walkthrough.

## Walkthrough on an emulator or phone (not done)

No Android SDK, emulator or phone is available on the machine this was built on, so none of `quickstart.md` steps 1 to 11 was run on a
device. These remain **not done**:

| Step | What it proves | Status |
| --- | --- | --- |
| 1 Menu per role | the two entries for the Manager, both for the Director with tasks read-only | not done (covered by tests) |
| 2 Home cards | the counts match the web | **not done** |
| 3 Visits list | grouping and Missed match the web (SC-003) | **not done** |
| 4 Complete a visit with a photo | the real camera, multipart upload and thumbnail | **not done** |
| 5 Plan, move, cancel | the prospect search and date chooser on a phone | **not done** |
| 6 and 7 Tasks and evidence | the steps against real tasks, up to five photos | **not done** |
| 8 Stale task | a task cancelled on the web while open on the phone | **not done** |
| 9 Add a task for me | offered to the Manager, not the Director | not done (covered by tests) |
| 10 Offline | nothing sent, entries kept | not done (covered by tests) |
| 11 Permissions | a removed permission removes the button | not done (covered by tests) |
| Timings | SC-001 (Home within 5 seconds), SC-002 (a visit with a photo under 90 seconds, a task with a photo under 60) | **not done** |
| TalkBack | the screens are usable with a screen reader | **not done** (a11y tests pass) |

Things only a real build can show, and so the first to look at on a device: the camera and gallery for visit and task photos (the same
native module as spec 033, so no new rebuild); the date chooser and the prospect search on a small screen; the thumbnail drawn from
downloaded bytes; and the Android back button closing a visit or task detail before leaving the screen.
