# 033 Mobile Manager Field Day: `/speckit-specify` input

2026-10-08. Roadmap row 033 (new). Depends on 032 (Manager attendance, server side) and 018 (Android app
foundation); 019 supplies the shared month and day pieces. Wave 3 in the delivery plan: do not run
`/speckit-specify` until 032 is specified and its contracts exist. It adds screens to the Android app and
no new server rules. Decision D15 (location required at check-in and check-out, photo optional) applies.

## Feature description (paste as the argument to `/speckit-specify`)

033-mobile-manager-field: Field-day screens for the Manager in the HLS Android app, built on the app shell,
sign-in and server-driven menus of spec 018 and the Manager attendance rules and APIs of spec 032, which
stay unchanged.

1. **Check-in.** On a working day the Manager checks in from the phone. The app captures the device
   location and sends it with the request (required, D15); an optional photo can be added. The Manager
   chooses the day type the server offers: Office, Field (school visit, college drive or training) or Leave.
   If location is unavailable or denied, the check-in is not sent and the app explains how to fix it.
2. **Field-day activities.** For a Field day the Manager picks one or more of today's planned activities
   from the shared planned-activity list (visits, drives, sessions, tasks); the app shows exactly what
   the server returns. Where spec 032 still accepts a purpose text, the app offers that instead.
3. **Check-out.** The Manager checks out with the device location. A missing check-out from an earlier day
   shows as a flag with the server's "complete with a reason" action.
4. **Today's plan.** The Home screen shows today's status (not checked in, checked in at a time, checked
   out) and the planned activities for the day, each tapping through to its record where the app has a
   screen for it.
5. **My month.** The Manager sees their own month: status per day, field-day links, leave and missed
   check-ins, reusing the month component of spec 019.
6. **Manager leave.** The Manager requests leave (type, dates, reason) and sees the Director's decision,
   using the same pattern as spec 020 against the 032 leave endpoints.
7. Menu entries appear only because the server's access model offers them (spec 018); nothing is chosen by
   role name in the app, and what each button may do comes from the server's `allowedActions`. Refusals
   (already checked in, locked month, no planned activity, leave day) are shown in plain language and the
   entered data is kept.
8. Every call carries the device location or its reason, as in spec 018; every change is audited by the
   server as in spec 032.

Out of scope: the Admin and Director grid, corrections and month lock (web), geofencing and distance
checks, offline check-in (a later spec), background location tracking, push notifications, and iOS.

## Role & Permission Impact (to carry into the spec)

Manager only, on their own data. Admin and Director use the web screens of 032. Teacher and System have no
access. No new permission keys: 033 uses those 032 adds.

## Points for `/speckit-clarify`

- What if the phone is offline at check-in: refuse with a message, or queue (offline is deferred)?
- Is the photo taken in the app camera only, or can the Manager pick from the gallery?
- How old may the device location fix be, and what accuracy is acceptable?
- May a Manager change the day type after checking in?
- Does the app show a reminder before the missed check-in cut-off (needs 021 notifications)?
- Is the Home plan limited to today, or does it show the coming week?
