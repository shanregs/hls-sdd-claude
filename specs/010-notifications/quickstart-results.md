# Quickstart results: Notifications (spec 010)

Run on 2026-10-05 against the local app (PostgreSQL on 5433, backend on 8080 with the demo flag, Vite on
5173), on branch `feature/010-notifications` rebased onto `main` (which already contains spec 009 and the
mobile attendance work). Flyway applied `V18` (notification table) and `V19` (index review).

## Scenarios

| # | Scenario | Result | How it was checked |
| --- | --- | --- | --- |
| 1 | Bell and list (US3) | Pass | Tara's demo data: 3 notifications, 2 unread. Opening one marked it read; mark all read took the count to 0; delete one and clear read worked. In the browser the bell showed "Notifications, 1 unread", the page listed the rows with an "Unread" word and marker, opening one followed its link, and **Mark all as read** hid the badge. |
| 2 | Leave decision (US1) | Pass | Tara applied; Manoj rejected with a reason: Tara's count went 0 to 1 and the text contained "Exams that week". Approve then revoke both notified Tara. Tara cancelling her own request notified her of nothing. |
| 3 | New request (US2) | Pass | Tara submitted: Manoj's count went 2 to 3 with "New leave request" linking to `/operations/leave`; Asha's stayed at 1. Tara cancelled: Manoj got "Leave request withdrawn". The no-Manager School (Lakshmi, who has no sign-in) is covered by `LeaveRequestNotificationTest`. |
| 4 | Attendance changes (US4) | Pass | Manoj marked three days for Tara: one unread "updated 3 days of your attendance in October 2026". After she read it, one more mark made a new notification. Tara's own mark created none. Asha locked September 2026: Tara got one "is locked" notice; reopening gave one "was reopened: Quickstart reopen." |
| 5 | Own scope and System (US3) | Pass | Manoj's list shared no ids with Tara's. Another user's id answered 404 for read and delete. Sunil (System): the API answered 403, there is no menu item, and in the browser there is no bell and the direct URL shows the not-authorized page. Tara's menu item carries VIEW and DELETE. |
| 6 | Retention (US5) | Covered by tests, not run live | The job fires only at 02:30 and has no HTTP trigger, so it was not run against the live app. `NotificationRetentionTest` covers rows at 89 days, just inside and just outside 90 days, and 91 days, merged rows expiring with their first event, and the database refusing another channel or a link that is not an app route. |

The scenario script (22 API checks) and the browser script (8 UI checks) are not committed; they drove the
real endpoints and the real UI and all checks passed.

## Found and fixed while running it

- **The bell kept a stale count after reading or deleting on the Notifications page** (until the next 30 second
  poll). The page now announces the change with a window event and the bell refreshes at once
  (`NotificationBell.test.tsx` and `NotificationsPage.test.tsx` cover it).
- **Tests broken by this change or already red on `main`**, found by the first full backend run: three
  expectations in `AccessModelResolutionTest` (the new ACCOUNT item), two in `AttendanceAccessModelTest` (the
  Leave Management item from spec 009, already failing on `main`) and `IdentityActivityPublishingTest` (a test
  token without the `roles` claim that `SessionController` now needs, already failing on `main`).
  `ManagerControllerTest` looked a zone up on page one of 100; the shared test database now holds more zones than
  that, so it looks the zone up by name.

## Test counts

- Backend, whole suite: 572 tests, 571 pass, 1 fails: `LeaveDevSeederTest.demoLeaveRequestsAreSeededForTheManagerAndTheTeacherWithoutDuplicates`
  (the sign-in as the demo Admin answers 401 once the shared test database has been through the earlier classes). It
  passes on its own and with the other seeder tests, and **it fails the same way on a clean `main`** (516 tests, 5
  failures and 1 error there: `AttendanceAccessModelTest` x2, `IdentityActivityPublishingTest`, `LeaveDevSeederTest`,
  `ManagerControllerTest`, `MasterDataScopeBoundaryTest`). This branch fixes four of those six and `MasterDataScopeBoundaryTest`
  passed in its run; the `LeaveDevSeederTest` cause (something earlier in the suite leaves the demo Admin's password
  or state different) is not found and is not part of this spec.
- Backend, notification module: `NotificationModuleRulesTest` 3, `MessageFactoryTest` 8, `NotificationServiceTest` 7,
  `NotificationApiTest` 4, `LeaveDecisionNotificationTest` 8, `LeaveRequestNotificationTest` 7,
  `AttendanceNotificationTest` 9, `NotificationRetentionTest` 4, `NotificationDevSeederTest` 4 (54 in all).
- Frontend: 51 files, 359 tests, all pass, including `src/a11y` in both themes.

## Success criteria and requirements checked against what was built

| Item | Status | Evidence |
| --- | --- | --- |
| FR-001 leave decisions notify the Teacher | Met | `LeaveDecisionNotificationTest`; a supervisor revoke publishes `LeaveDecided` with decision REVOKED |
| FR-002 submit and cancel notify the supervisors in scope | Met, one reading | The School's active Manager, otherwise every active Admin and Director (`RecipientResolver`, `LeaveRequestNotificationTest`). "Manager(s)" is read as the School's assigned Manager; Zone-level Managers are not also told. |
| FR-003 attendance changes, merged within 10 minutes | Met | `AttendanceNotificationTest`; own marks and leave-made marks create none |
| FR-004 lock and reopen | Met | `AttendanceNotificationTest`, live scenario 4 |
| FR-005 same transaction | Met | A refused approval (locked month, supervisor-set day) leaves no `LEAVE_DECIDED`; a refused mark leaves no attendance notification |
| FR-006 fields and limits | Met | V18 column limits and checks; `NotificationRetentionTest` for channel and link |
| FR-007 list, filter, count, read, delete, clear | Met | `NotificationApiTest`, live scenarios 1 and 5 |
| FR-008 bell, 99+, ACCOUNT item, refresh | Met | Bell tests (poll, 99+, hidden at zero, paused when the tab is hidden, refresh on change), live UI check |
| FR-009 `NOTIFICATIONS` module, never System | Met | `PermissionEligibilityTest`, `NavigationSectionOrderTest`, live 403 for System |
| FR-010 daily 90-day clean-up | Met by tests | `RetentionJob` (02:30), `NotificationRetentionTest`; not triggered live |
| FR-011 no data beyond what the recipient can see | Met | Manager notices name only that School's Teacher; System receives none |
| FR-012 demo data | Met | `NotificationDevSeederTest`, live scenario 1 |
| SC-001, SC-002 within 30 seconds | Met | The bell polls every 30 seconds and now refreshes at once after the user's own actions; only the recipient's other tabs wait for the poll |
| SC-003 rollback leaves none, commit gives exactly the expected ones | Met | The refused-action tests above |
| SC-004 own notifications only | Met | `NotificationApiTest`, live scenario 5 |
| SC-005 count under 200 ms, first page under 1 s at 5,000 notifications | Met | Measured live with 5,000 rows for Tara (500 unread): unread count 54 to 72 ms, first page 70 to 117 ms end to end; the count's query takes 1.9 ms on `idx_notification_unread_updated` |
| SC-006 keyboard, announced count, accessibility checks | Partly verified | axe checks pass for the Notifications page in both themes and the bell is a labelled link, but keyboard-only use and a screen reader were not exercised by hand |
| SC-007 older than 90 days gone | Met by tests | `NotificationRetentionTest` |

## Gaps and notes

- Retention was not run against the live app (scenario 6), and SC-006 was not checked by hand with a keyboard or a
  screen reader.
- Demo data: the attendance seeder marks days as a supervisor, so every demo Teacher gets "your attendance was
  updated" notices from the real event path when the demo data is first created. `NotificationDevSeeder` adds its own
  attendance notice only when the Teacher has none.
- The open question about the OTP "mark sibling codes used" lookup (it is not scoped by user) came up during the
  V19 review and is outside this spec.
