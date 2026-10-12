# Quickstart: Mobile Notifications

How to prove the feature works end to end. Contracts: [contracts/mobile-notifications-screens.md](contracts/mobile-notifications-screens.md).
Shapes: [data-model.md](data-model.md).

## Prerequisites

- Backend running with demo data (`docs/running-locally.md`), web app available for comparison.
- Android emulator or device with the Expo build (`docs/running-mobile.md`).
- Demo users: a Teacher, the Manager assigned to that Teacher, and a Director.

## Automated

From `mobile/`:

```text
npm run typecheck
npm run lint
npm test
```

Expected: all green, including the new `__tests__/notifications/` suite, the extended role-name scan
(`access/noHardCodedRoles`), the accessibility checks (`a11y/notifications.a11y.test.tsx`) and the request-shape
contract test (`api/notificationsContract.test.ts`).

## Manual scenarios (emulator)

1. **Bell and refresh (US1)**: sign in as the Manager. On the web, sign in as the Teacher and submit leave. Within 30
   seconds the Manager's bell shows 1 without any tap. Background the app, create another, return: the bell updates at
   once. Turn the network off: the bell keeps no number and no error appears.
2. **Open and follow (US2)**: time the path from the bell to the destination screen (target under 10 seconds) and
   compare titles, messages and times with the web for the same user (SC-002, SC-003). Open the list; the unread request is highlighted. Tap it: it becomes read, the bell
   drops, Leave Management opens. As the Teacher, reject the request on the web; in the app open "Your leave was
   rejected": My Leave History opens. Trigger an attendance change for the Teacher: the notification opens My
   Attendance on the linked month.
3. **Unfollowable link**: with test data holding a notification whose link is `/something-else`, tap it: it becomes
   read, the dialog shows the full text, nothing else opens.
4. **Mark, delete, clear (US3)**: Mark all as read hides the bell; delete one; Clear read asks to confirm, Cancel
   removes nothing, Confirm removes only read ones. Remove the Delete permission for the role on the web, refresh the
   menu (return after 5 minutes or sign in again): delete and clear disappear, marking read still works.
5. **Scope and sign-out (US4)**: another user's notifications never appear; sign out and sign in as someone else, no
   previous count or rows. Remove View on the web: after the next menu refresh the bell and the item are gone.
6. **TalkBack and themes**: the bell announces "Notifications, N unread"; rows, actions and dialogs are reachable;
   check light and dark and large text.

Record results in `quickstart-results.md` when implementing.
