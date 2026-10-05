# Quickstart: Notifications

Runnable checks that prove the feature end to end. Setup is in `docs/running-locally.md` (database, backend
with demo data, frontend). Demo logins (all `Password123!`, or a one-time code): Tara Teacher `9800000004`,
Manoj Manager `9800000003`, Asha Admin `9800000001`, Divya Director `9800000002`, Sunil System `9800000005`.

## Prerequisites

- Specs 001 to 005, 008 and 009 running locally; the demo flag on (the seeder adds sample notifications).
- Flyway applies `V18`; check the log for `Migrating schema ... to version 18`.

## Scenarios

1. **Bell and list (US3)**: sign in as Tara. The header bell shows her unread count (demo data: 2). Open
   ACCOUNT → Notifications: newest first, unread highlighted. Open one: it becomes read and its link opens.
   Mark all as read: the bell hides. Delete one, then clear read.
2. **Leave decision (US1)**: as Tara apply for leave; as Manoj reject it with a reason. Back as Tara (within
   30 seconds, or after a refresh) the bell shows 1 and the notification contains the reason. Approve another
   and revoke it; both notify Tara. Cancelling your own request notifies Tara of nothing.
3. **New request (US2)**: as Tara submit a request; Manoj's bell goes up with "New leave request" linking to
   Leave Management; Asha's does not. For a Teacher whose School has no Manager (Lakshmi, Demo School Two, who has no sign-in in the demo
   data, so this one is covered by the automated tests) Asha and Divya are notified instead. Tara cancels a request: Manoj is told.
4. **Attendance changes (US4)**: as Manoj mark three days for Tara within a few minutes: Tara has one unread
   notification "updated 3 days". Mark one more after she read it: a new notification. Tara marking her own
   day creates nothing; approved leave creates only the leave notification. As Asha lock the month: Tara is
   told once; reopen it: told again.
5. **Own scope and System (US3)**: Manoj never sees Tara's notifications; `GET /api/v1/me/notifications/{id}`
   of someone else's id is 404; Sunil (System) has no bell, no menu item, and the API answers 403.
6. **Retention (US5)**: set a notification's `created_at` back 91 days in the database and run the clean-up;
   it is gone, newer ones stay.

## Automated

```powershell
cd backend; mvn -q test -Dtest="com.hls.notification.**,NavigationSectionOrderTest,PermissionEligibilityTest,ModuleBoundaryTest"
cd frontend; npx vitest run src/features/notifications src/a11y
```

Expected: all green; the a11y cases for `NotificationsPage` pass in light and dark themes.
