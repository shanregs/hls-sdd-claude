# Research: Mobile Manager Field Day

Decisions for [plan.md](plan.md). Facts about the server were read from the 032 code and contract
(`specs/032-manager-attendance/contracts/manager-attendance-api.md`, `MyManagerAttendanceController`, `CheckInService`,
`CheckInPhotoService`, `ManagerGridService`, `ManagerLeaveService`, `RegularizationService`) and about the app from
`mobile/src` as of 2026-10-17.

## 1. The server is used unchanged, and the menu already exists

**Decision**: call the `/api/v1/manager-attendance/me/**` endpoints of spec 032 as they are. The server's navigation
catalog already offers a Manager three entries under MY ATTENDANCE: **Check-in** (`/manager-attendance/me`), **My Month**
(`/manager-attendance/me/month`) and **My Leave** (`/manager-attendance/me/leave`), all on `MY_MANAGER_ATTENDANCE`. The
screen registry maps each route to a screen; a route it does not know is simply not shown (spec 018).

**Rationale**: this is what "spec 032 stays unchanged" means in practice, and it keeps role logic out of the app.

**Alternatives considered**: a new mobile-only endpoint set (rejected: duplicates rules and breaks the unchanged-032
promise); adding menu entries for forgotten check-in requests (rejected: the catalog is server-side, so it would be a
backend change; the requests live inside My Month instead, see §8).

## 2. Location: a fresh reading in the body, the same reading in the header

**Decision**: check-in and check-out take their own **fresh** reading when the Manager taps, and send it in the request
body (`latitude`, `longitude`, `accuracyMeters`) as spec 032 requires. A new `captureFresh()` in the location gate skips
the 10-second reuse window of spec 018 (a reading shared by calls made together), waits the same short time
(`DEFAULT_LOCATION_WAIT_SECONDS`, 4 s, server-configurable), and stores the reading it takes. The existing header provider
then finds that reading still fresh and attaches the same position to the audit header, so the body and the header agree.
If there is no reading (permission denied, services off, no fix in time, other) nothing is sent and the Manager sees the
matching plain-language message.

**Rationale**: the gate was built for best-effort header location and may serve a reading up to 10 seconds old; a check-in
is a statement about where the Manager is at the tap (D15), so it must not reuse an earlier reading. Sending the accuracy
the phone reports and never refusing for accuracy is the clarified decision.

**Alternatives considered**: reuse the gate's shared reading (rejected: up to 10 s stale, and a second tap could reuse a
reading from another place); a separate location call outside the gate (rejected: two sources of truth, and the audit header
could then disagree with the body); a higher accuracy setting (rejected: slower fixes indoors; `Balanced` already accepts
what the user allowed).

**Permission not yet asked**: when the Manager has not decided on location, the app uses the existing plain-language
consent of spec 018 (`LocationConsent`) before the first reading, then continues the tap. A refusal gives the message
"Check-in needs your location. Allow location for HLS in your phone settings, then try again" with an "Open settings"
action; services off gives the matching "Turn on location (GPS) on your phone" text.

## 3. Double taps and in-flight actions

**Decision**: each action (Check in, Check out, Complete, photo, leave, request, cancel) holds an in-flight flag; its button
is disabled while a request is outstanding and a second tap is ignored. The result is shown only from the response.

**Rationale**: SC-005 (never a false success) and the edge case of a quick double tap. The server also refuses a second
check-in (409), which the app shows with its reason if it ever happens.

## 4. Photo: `expo-image-picker`, camera or gallery, uploaded after the check-in

**Decision**: add `expo-image-picker` (~57.0.20, current for Expo SDK 57). "Take a photo" uses the camera; "Choose from
gallery" uses the system photo picker, which needs no storage permission on Android. Both use `quality` 0.7 so a picture is
normally far below the server's 10 MB limit. The picture is held in memory only. The check-in is sent first; when it
succeeds and a photo was picked, the app then uploads it to the day's photo endpoint. If the upload fails the check-in
stays, the failure is explained, and an "Add photo" action stays on today's status while the day has no photo.

**Rationale**: the photo is optional and a refused photo must not undo the check-in (FR-004, scenario 1.5), which matches
the server's own sequence (check-in, then `POST /me/days/{date}/photo`). The plugin is configured for the camera only
(`cameraPermission` text, `microphonePermission: false`, `photosPermission: false`), so no microphone or broad storage
permission is requested, and `check-manifest.mjs` is extended to fail the build if one appears.

**Alternatives considered**: `expo-camera` with a custom viewfinder (rejected: more code and more permissions for no gain);
a document picker (rejected: not a photo flow); uploading the photo in the same request (rejected: the server accepts the
photo only as a separate call after the check-in exists).

**Type and size**: the server accepts only jpg or png up to its limit and decides; the app does not pre-judge them. A
refusal ("A photo must be a jpg or png file", "This check-in already has a photo", too large) is shown as the server's reason.

## 5. Showing a photo and what the server tells us about it

**Decision**: today's status and My Month show a small thumbnail of the Manager's own photo, loaded from
`GET /me/days/{date}/photo` with the bearer token in the image request's headers, and the details the response carries:
**file name** (`Content-Disposition`), **type** (`Content-Type`) and **size** (`Content-Length`). The day view itself carries
only `hasPhoto`.

**Rationale**: no 032 endpoint returns who attached a photo or when (the day view has just a boolean), and the clarification
chose not to change 032. So the app shows what the photo download itself returns and does not claim more. The thumbnail
request is `no-store` (the server sends `Cache-Control: no-store`), so nothing is kept on disk.

**Spec consequence**: FR-004 and scenario 1.5 said the app shows "who attached it and when". That cannot be met without a
032 change, so the spec is amended to "file name, type and size" (§9).

## 6. Generalise the month grid instead of copying it

**Decision**: `MonthGrid` (spec 019) takes a Teacher `DayView` and builds its look with `presentDay`. It is changed to take a
list of already-presented days (date, label, colours, optional press handler) built by the caller, with the Teacher's
`presentDay`/`presentCalendarDay` wrapped around the existing call sites so Teacher, Holiday and Manager-of-Teachers
screens look and behave exactly as before, and a new `managerDayPresentation.ts` builds the Manager look. `MonthPicker`,
`useMonthView`, `monthRange` and `serverClock` are reused as they are; `DaySheet` is reused for a Manager day with Manager
actions.

**Rationale**: spec 033 says "reusing the month component of spec 019"; the grid's layout rules (seven columns, list layout
at large text, 48 dp targets) should exist once. The existing 019 and 020 tests guard the refactor.

**Manager statuses and flags** (from the server): `WORKED`, `FIELD`, `LEAVE`, `HOLIDAY`, `WEEKLY_OFF`, `NOT_EMPLOYED`,
`PENDING`, `MISSED`; flags `LATE`, `NO_CHECK_OUT`, `ADDED_LATER`, `CORRECTED`, `UNLINKED`. The app labels them in plain
words and computes none of them (FR-010). `CORRECTED` and `UNLINKED` are review marks for the Director; the Manager sees
them as "Corrected by your Director" and "No planned activity linked".

**Alternatives considered**: a second grid component for Managers (rejected: duplicate layout and accessibility work).

## 7. Home card and permissions come from the menu items

**Decision**: the Home screen shows the check-in card when the server's menu contains the route `/manager-attendance/me`,
the same way it shows the Pending count only when the menu offers `/operations/leave`. Create and Delete buttons are shown
only when that menu item's `actions` list them (the navigation items already carry `actions`). No role name is read.

**Rationale**: FR-014 and spec 018 FR-009. An Admin who removes Create from a Manager makes the buttons disappear on the next
load; the server refuses regardless.

**Manager leave cancel**: the Manager leave response has no `allowedActions`. The cancel button is shown for a **Pending**
request when the menu item grants Delete, which is exactly the rule in the 032 contract ("cancel a pending request; an
approved one is revoked by the approver"); the server refuses anything else with its reason.

## 8. Forgotten check-in requests live in My Month

**Decision**: a past working day with status `MISSED` (or no check-in) in My Month offers "Ask to add a check-in" in its day
sheet; a "Check-in requests" section under the month lists the Manager's requests with status and the decider's reason and a
Cancel for Pending ones. There is no new menu entry.

**Rationale**: the menu is server-side (§1), and the request belongs to a particular day. Priority P3: it can be cut without
touching the other stories.

## 9. Corrections to the spec after reading the 032 server

Found while planning; the spec is amended in the same change:

1. **Photo details (FR-004, scenario 1.5, clarification 1)**: "file name, type, size, who attached it and when" becomes
   "file name, type and size"; who and when are not exposed by any Manager endpoint and 032 is not changed.
2. **Planned activity (FR-009, scenario 3.5)**: "name, kind, time and place" becomes "kind, place, date and status"; the
   server's planned-activity record has exactly those fields and no title.
3. **Forgotten check-in and Manager leave (stories 5 and 6)**: no preview step exists for Manager leave (unlike spec 009), so
   the form submits straight to the server.

## 10. Refusals and error text

**Decision**: the server's `reason` is the message for every 400, 403, 404 and 409 (`messageOf` in the HTTP client already
reads `reason`). The app adds its own text only for what it detects itself: location unavailable (denied, services off, no
fix), no connection, a Field day with neither an activity nor a purpose (checked before sending, because the server's
refusal would arrive only after the location wait), and a missing reason or time on the forms. Entered data is held in the
screen state across refusals.

## 11. Testing

**Decision**: extend the in-memory fake server with the `/me/today`, check-in, check-out, complete, photo (multipart and
download), month, regularization and Manager leave endpoints and fixtures that copy the real shapes. Contract tests assert
the request shapes (body fields, no location when none), the fresh-reading rule (a check-in after a stale reading takes a new
one), and that no request is sent without a location. A role-name scan and the accessibility test cover the new screens. The
camera and gallery are mocked at the picker module. Manual: emulator with a mocked location and TalkBack (quickstart.md).
