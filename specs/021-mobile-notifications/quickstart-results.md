# Quickstart results: Mobile Notifications

## Automated (2026-10-08)

Run from `mobile/` on the branch `feature/021-mobile-notifications`:

| Check | Result |
| ----- | ------ |
| `npm run typecheck` | clean |
| `npm run lint` | clean |
| `npm test` | 75 suites, 792 tests pass (655 before this feature; 137 added) |

New suites: `__tests__/notifications/` (bell, polling, shell header, link mapping, list, open and follow, mark and
delete, clear read, refusals, menu and scope, headers and sign-out), `__tests__/api/notificationsContract.test.ts`,
`__tests__/attendance/myAttendanceMonth.test.tsx` and `__tests__/a11y/notifications.a11y.test.tsx`. The role-name scan
(`access/noHardCodedRoles`) covers the new source files with no exceptions.

Existing tests changed: the access-model menu expectations (Notifications is now an ACCOUNT item for Teacher, Manager
and Director), and four tests that captured only the last `AppState` listener now keep all of them
(`access/refresh`, `screens/notAuthorized`, `attendance/attendanceStale`, `leave/leaveStale`), because the bell
registers a listener of its own.

## Manual scenarios (emulator, quickstart.md)

Pending: no device or emulator run yet. Scenarios 1 to 6, TalkBack, light and dark themes, the largest text size on a
360 dp screen (T032) and the bell-to-destination timing and web comparison of scenario 2 (SC-002, SC-003) are to be
done and recorded here, as for specs 019 and 020.

Note for the device pass: the Signed-in devices and Location privacy sub-screens have their own header and show no
bell. This is intended (research §3).
