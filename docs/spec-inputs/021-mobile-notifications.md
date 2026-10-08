# 021 Mobile Notifications: `/speckit-specify` input

2026-10-08. Roadmap row 021. Depends on 010 (notifications, server side) and 018 (Android app foundation);
019 (mobile attendance) and 020 (mobile leave) supply the screens that notification links open. It adds
screens to the Android app; it adds no new server rules and no new event types.

## Feature description (paste as the argument to `/speckit-specify`)

021-mobile-notifications: Notifications for the HLS Android app, built on the app shell, sign-in and
server-driven menus of spec 018 and the notifications API of spec 010, which stays unchanged.

1. **Bell.** A bell in the app header shows the number of unread notifications (hidden at zero, "99+"
   above 99) and opens the list. The count is loaded when the app opens, when it returns to the
   foreground, on pull-to-refresh and every 30 seconds while the app is open. There is no push.
2. **Notifications list.** ACCOUNT, Notifications opens the user's own notifications, newest first,
   unread highlighted, in pages, with a filter to unread. Each shows title, message and time.
3. **Actions.** Open one (it is marked read and, when its link leads to a screen the app has, that
   screen opens; otherwise the notification is only shown), mark one or all as read, delete one, clear
   all read ones. Delete and clear are offered only when the user holds the Delete permission.
4. **Links.** The web links the server sends are mapped to app screens: `/leave/history` to My Leave
   History (spec 020), `/operations/leave` to Leave Management (spec 020) and `/my-attendance?month=`
   to My Attendance for that month (spec 019). A link to a screen the app does not have, or that the
   user's menu does not offer, is not followed and no error is shown.
5. The bell and the Notifications item appear only because the server's access model offers them
   (spec 018). Nothing is chosen by role name in the app. System accounts get none.
6. Every call carries the device location or its reason, as in spec 018. Reading, marking and deleting
   one's own notifications need no audit entry, as in spec 010.

Out of scope: push notifications (FCM or any push), SMS and email, offline capture, notification
settings or preferences, new event types or texts, iOS, and Admin and System use (web only).

## Role & Permission Impact (required section)

- No new permission keys. Uses `NOTIFICATIONS` (View, Delete) from spec 010; the navigation already
  offers the ACCOUNT, Notifications item.
- Teacher, Manager and Director see their own notifications only. Admin is web-only. System has none.
- Scope: always the signed-in user's own; another user's id is answered as not found by the server.

## Points for `/speckit-clarify`

- Is polling every 30 seconds in the foreground enough, or is a refresh only on open and pull enough?
- Opening a notification whose link the app cannot follow: mark it read, or leave it unread?
- Does the bell appear on every screen header or on Home only?
- Should Delete and Clear read ask for confirmation?
- What does the list do when a link points to a leave request or month the user can no longer see?
