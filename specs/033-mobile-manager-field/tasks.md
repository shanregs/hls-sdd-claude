---

description: "Task list for feature implementation"
---

# Tasks: Mobile Manager Field Day

**Input**: Design documents from `/specs/033-mobile-manager-field/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/server-calls.md, contracts/screens.md,
quickstart.md, and **specs 018, 019 and 032 implemented** (this feature adds screens to the app shell of 018, reuses the
month pieces of 019, and calls the Manager attendance and Manager leave endpoints of 032 unchanged; there is no backend,
web or database work).

**Tests**: included as first-class tasks. Constitution Principle IX requires per-scope tests (two Managers), menu-visibility
tests per role, and tests that every refusal leaves things unchanged and that nothing is shown as done before the server
confirms it (SC-005, SC-006, SC-007).

**Organization**: grouped by user story in priority order (spec.md US1-US7). The shared plumbing (location, upload, API
clients, today hook, messages, routes) comes first because several screens use it.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: US1-US7; absent for Setup/Foundational/Polish

## Path Conventions

`MOB` = `mobile/src`, `MOBT` = `mobile/__tests__`. All work is under `mobile/` (plus `docs/` in Polish); `backend/` and
`frontend/` are not changed. Run commands from the `mobile` folder with JDK 17 (see `docs/running-mobile.md`). The real
server shapes are in `data-model.md` (they follow the 032 code, not only the contract document).

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: dependency, permissions, folders and realistic test data.

- [X] T001 Create the folders `MOB/manager/` and `MOBT/manager/`, and confirm `npm test`, `npm run lint`, `npm run typecheck` and `npm run check:manifest` pass from `mobile/` before any change
- [X] T002 Add the package `expo-image-picker` at `~57.0.20` to `mobile/package.json` (run `npx expo install expo-image-picker` from `mobile/`), add the plugin to `mobile/app.config.ts` as `["expo-image-picker", { cameraPermission: "HLS uses the camera only when you choose Take a photo for a check-in.", microphonePermission: false, photosPermission: false }]`, and extend `mobile/scripts/check-manifest.mjs` so the build check fails if the resolved Android config requests `RECORD_AUDIO`, `READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE`, `READ_MEDIA_IMAGES` or `READ_MEDIA_VIDEO` (the camera permission is allowed); run `npm run check:manifest`
- [X] T003 [P] Create `MOBT/support/managerFixtures.ts` with builders that copy the real shapes of data-model.md: `managerDay({dayType, purpose, checkInAt, checkOutAt, late, hasPhoto, leave, flags, activities})` for the server's day (`id, date, dayType` one of `OFFICE`, `FIELD`, `purpose, checkInAt, late, checkOutAt, hasPhoto, leave {fraction, unpaid}, flags` any of `LATE`, `NO_CHECK_OUT`, `ADDED_LATER`, `CORRECTED`, `UNLINKED`, `activities [{kind, id}]`), `today({workingDay, day, leave, missingCheckOutDates, plannedActivities})` (`date, workingDay, missedCutoff, lateAfter, day, leave, missingCheckOutDates, plannedActivities [{kind, id, date, place, status}]`), `managerMonth({days, figures, figuresUnavailable, locked})` (`month, locked, figures {workingDays, daysWorked, leavePaid, leaveUnpaid, unpaid, fieldDays, pending}, figuresUnavailable, days` each with `status` one of `WORKED`, `FIELD`, `LEAVE`, `HOLIDAY`, `WEEKLY_OFF`, `NOT_EMPLOYED`, `PENDING`, `MISSED`), `managerLeave({status})` (`id, leaveTypeId, leaveTypeName, lossOfPay, firstDate, lastDate, firstHalf, lastHalf, reason, status` one of `PENDING`, `APPROVED`, `REJECTED`, `CANCELLED`, `REVOKED`, `requestedAt, decidedAt, decisionReason`) and `regularization({status})` (`id, date, proposedTime, dayType, purpose, reason, status` one of `PENDING`, `APPROVED`, `REJECTED`, `CANCELLED`, `requestedAt, decidedAt, decisionReason`)
- [X] T004 [P] Create `MOBT/support/managerServer.ts` that registers the Manager routes on the existing `FakeServer` (`GET /api/v1/manager-attendance/me/today`, `POST .../check-in`, `POST .../check-out`, `POST .../days/{date}/complete-check-out`, `POST .../days/{date}/photo` multipart and `GET .../days/{date}/photo` returning bytes with `Content-Type`, `Content-Length` and `Content-Disposition` headers, `GET .../months/{yyyy-MM}`, `GET/POST .../regularizations`, `DELETE .../regularizations/{id}`, `GET .../leave/types`, `GET/POST .../leave`, `DELETE .../leave/{id}`), applies check-ins, check-outs, completions, photos, requests and cancels to its own state, records every call (method, path, body, headers) for the request-shape tests, and answers the 032 refusals with `{ "reason": "..." }` and the statuses of contracts/server-calls.md (400 without a location or a reason, 409 second check-in, locked month and second photo, 400 for a photo that is not a jpg or png)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: location, upload, the two API clients, the today hook, messages and routes every story uses.

**⚠️ CRITICAL**: no user story work can begin until this phase is complete.

- [X] T005 Add `captureFresh(): Promise<CaptureResult>` to `MOB/location/LocationGate.ts`: it ignores the reuse window (`DEFAULT_LOCATION_REUSE_SECONDS`), joins no earlier lookup, waits at most `timings.waitSeconds`, stores the reading it takes in `lastReading` (so the header provider of the same call attaches the same position), and returns `{kind: "fix", reading}` or `{kind: "none", reason}` with the reasons `PERMISSION_DENIED`, `SERVICES_OFF`, `NO_FIX`, `OTHER`; it never starts a watcher or a timer and runs only when called; add `MOBT/location/captureFresh.test.ts` (a reading 5 seconds old is not reused, the reading taken is the one the next header gets, denied permission, services off, a timeout and a thrown error each give the matching reason, no lookup happens until called, and nothing runs in the background)
- [X] T006 Add `upload(path, field, file, options)` and `fetchBinary(path, options)` to `MOB/api/httpClient.ts`: `upload` sends a multipart `FormData` with one file part `{uri, name, type}` under the field name given (no `Content-Type` header set by hand so the boundary is added), with the same client headers, provider headers (location), bearer token, one renewal retry on 401 and typed errors as `apiRequest`, and a timeout of three times `REQUEST_TIMEOUT_MS`; `fetchBinary` returns `{ status, headers (Content-Type, Content-Length, Content-Disposition), uri/bytes handle }` for the photo thumbnail without writing to disk; add `MOBT/api/upload.test.ts` (multipart body and field name, auth and location headers present, 401 renews once, 400/409 turn into `ApiError` with the server's `reason`, no connection gives `NoConnectionError`)
- [X] T007 Create `MOB/api/managerAttendanceApi.ts` with the types of data-model.md and typed calls for every endpoint of contracts/server-calls.md under `/api/v1/manager-attendance/me`: `getToday()`, `checkIn(body)`, `checkOut(body)`, `completeCheckOut(date, {time, reason})`, `attachPhoto(date, file)` (uses `upload`), `openPhoto(date)` (uses `fetchBinary`), `getMonth(yyyyMm)`, `listRegularizations()`, `requestRegularization(body)`, `cancelRegularization(id)`; the position fields of `checkIn` and `checkOut` are `latitude`, `longitude` and `accuracyMeters` sent exactly as read (latitude between -90 and 90 and longitude between -180 and 180 as the server checks; no rounding other than the six decimals of the audit header); the app computes none of the figures, flags or statuses it returns; add `MOBT/api/managerAttendanceApi.test.ts` (request shapes against the fake server, one test per call)
- [X] T008 [P] Create `MOB/api/managerLeaveApi.ts` with `listManagerLeaveTypes()`, `listManagerLeave()`, `requestManagerLeave({leaveTypeId, firstDate, lastDate, firstHalf, lastHalf, reason})` and `cancelManagerLeave(id)` against `/api/v1/manager-attendance/me/leave`, with the types of data-model.md (`LeaveView` and `TypeView {id, name, lossOfPay}`); add `MOBT/api/managerLeaveApi.test.ts` (request shapes, status mapping of the five statuses)
- [X] T009 [P] Create `MOB/manager/managerMessages.ts`: `locationMessage(reason)` returning the three texts of contracts/screens.md ("Check-in needs your location. Allow location for HLS in your phone settings, then try again." for `PERMISSION_DENIED` with an "Open settings" flag, "Turn on location (GPS) on your phone, then try again." for `SERVICES_OFF`, "Your phone could not find its position. Move to an open area and try again." for `NO_FIX` and `OTHER`), `flagText(flag)` ("Late", "No check-out", "Added later", "Corrected by your Director", "No planned activity linked"), `statusText(status)` for the ten month statuses, and `managerRefusalText(reason)` that returns the server's reason unchanged (no code, no generic text); add `MOBT/manager/managerMessages.test.ts`
- [X] T010 [P] Create `MOB/manager/useAction.ts`: `useAction(fn)` returns `{run, sending, error, clearError}`; while a call is outstanding `run` ignores a second tap (returns the same promise), the error is the server's reason or "No connection. Nothing was sent." for `NoConnectionError`, and success is reported only when `fn` resolves; add `MOBT/manager/useAction.test.ts` (a double tap sends one request, a failure keeps the error until cleared, an offline call says no connection)
- [X] T011 Create `MOB/manager/useToday.ts`: `useToday()` loads `getToday()` and returns `{state: "loading"|"ready"|"error"|"noConnection", today, reload}`; it reloads after any action that changes the day (the caller calls `reload` once the server confirmed), keeps the last good data visible while reloading, and drops it on a failed load (nothing stale is shown as current); add `MOBT/manager/useToday.test.tsx`
- [X] T012 Register the three Manager screens and shell navigation: in `MOB/access/screenRegistry.ts` add keys `managerCheckIn`, `managerMonth`, `managerLeave` mapped from `/manager-attendance/me`, `/manager-attendance/me/month`, `/manager-attendance/me/leave`; in `MOB/navigation/AppShell.tsx` render them (placeholder components until the story tasks) with titles "Check-in", "My Month", "My Leave"; extend `MOBT/access/screenRegistry.test.ts` and `MOBT/navigation` tests so a route not in the registry is still not shown and the three routes appear under MY ATTENDANCE only when the server's menu contains them

**Checkpoint**: location, upload, API clients, today hook, messages and routes work; no screen is complete yet.

---

## Phase 3: User Story 1 - A Manager Checks In From the Phone (Priority: P1) 🎯 MVP

**Goal**: a Manager chooses Office or Field (with activities or a purpose), optionally adds a photo, checks in with a fresh location, and sees the time the server recorded.

**Independent Test**: sign in as a Manager with a joining date, check in as Office; on another day check in as Field with a planned visit selected and a photo, and confirm the day shows the visit and the photo (quickstart scenarios 3-6).

### Tests for User Story 1

- [X] T013 [P] [US1] `MOBT/manager/checkIn.test.tsx`: the form offers Office and Field only (never Leave), Office preselected; choosing Field lists today's planned activities exactly as the server returned them (kind, place, status, server order) and a purpose field of at most 500 characters ("purpose of at most 500 characters"); Check in sends `POST /check-in` with `{dayType, purpose, activities, latitude, longitude, accuracyMeters}` where the position is the one just read; the success line shows "Checked in at <time>" taken from `checkInAt` of the response, Check in is no longer offered, and Check out is offered; a Field day with the activities selected sends their `{kind, id}` pairs and an Office day sends none; the Check in button is disabled while a request is outstanding
- [X] T014 [P] [US1] `MOBT/manager/checkInLocation.test.tsx`: with permission denied, services off, no fix in time, or any other lookup failure, nothing is sent (the fake server records zero calls to `/check-in`), the matching message of T009 is shown (with an Open settings action only for a denied permission), nothing is shown as checked in, and every entry is kept; a retry after the cause is fixed sends the request; two taps 5 seconds apart each take a fresh reading (the first reading is not reused) and the position in the body equals the position in the `X-HLS-Location` header of the same call; the accuracy the phone reports is sent unchanged and a poor accuracy (900 m) is not refused (FR-002)
- [X] T015 [P] [US1] `MOBT/manager/checkInRefusals.test.tsx`: for every check-in refusal of contracts/server-calls.md (second check-in 409, locked month 409, approved full-day leave 400, no joining date 400, day outside employment 400, activity not the Manager's or not dated that day 404, a Field day the server refuses) the server's `reason` appears in plain language, nothing is shown as checked in, the entries (day type, activities, purpose, photo) are kept; a Field day with neither an activity nor a purpose is not sent and the message says which of the two is needed; an activity list reloaded after a 404 keeps the purpose text; on approved half-day leave one check-in is accepted
- [X] T016 [P] [US1] `MOBT/manager/checkInPhoto.test.tsx` (the picker module is mocked): Take a photo and Choose from gallery each return an image held in memory and shown as a thumbnail with its file name, type and size; Remove clears it; Check in sends the check-in first and then `POST /days/{date}/photo` multipart with the date of the response; a photo the server refuses (400 not a jpg or png, 409 already has a photo) leaves the check-in recorded, shows the reason and keeps "Add photo" on today's status while the day has none; a check-in without a photo makes no photo call; the camera permission is requested only when Take a photo is chosen and a refusal of it does not affect the check-in; after success the day's photo is shown with its file name, type and size read from the headers of `GET /days/{date}/photo` and no "who attached it" or "when" text appears
- [X] T017 [P] [US1] `MOBT/manager/checkInOffline.test.tsx`: with the fake server offline, Check in says "No connection. Nothing was sent.", sends nothing further, keeps every entry and shows nothing as checked in; going back online and tapping again succeeds (SC-005); a 401 renews once and repeats; a double tap sends one request

### Implementation for User Story 1

- [X] T018 [P] [US1] Create `MOB/manager/ActivityList.tsx`: planned activities as checkable rows `{kind, place, status}` in the server's order, or read-only rows when `selectable` is false; each row has an accessible label such as "College drive, Anna Nagar, planned, not selected"; an empty list shows "No planned activities for today."; props `{activities, selected, onToggle?}`; add `MOBT/manager/ActivityList.test.tsx`
- [X] T019 [P] [US1] Create `MOB/manager/PhotoPicker.tsx`: buttons "Take a photo" (`launchCameraAsync({mediaTypes: ["images"], quality: 0.7})`, requesting camera permission only on this tap) and "Choose from gallery" (`launchImageLibraryAsync({mediaTypes: ["images"], quality: 0.7})`, the system picker, no storage permission), a thumbnail with file name, type and size once picked, and Remove; it holds the picture in memory only and never writes it to disk; props `{value, onChange}`; add `MOBT/manager/PhotoPicker.test.tsx`
- [X] T020 [P] [US1] Create `MOB/manager/PhotoView.tsx`: shows the Manager's own photo for a date from `openPhoto(date)` as a thumbnail (image request with the bearer token in its headers, never cached to disk) with the file name, type and size from the response headers, and an "Add photo" action when `hasPhoto` is false and the Manager may create; a failed load shows a short message and Try again; add `MOBT/manager/PhotoView.test.tsx`
- [X] T021 [US1] Create `MOB/manager/CheckInForm.tsx`: the day type choice, `ActivityList` (selectable) for Field, the purpose field, `PhotoPicker`, and a Check in button using `useAction` that: validates only the Field rule of T015, calls `captureFresh()` (T005), sends nothing when there is no fix and shows `locationMessage`, then calls `checkIn(body)` with the position, then `attachPhoto` when a photo was picked (T007), then asks the caller to reload; the form keeps every entry until the server confirms
- [X] T022 [US1] Create `MOB/screens/ManagerCheckInScreen.tsx`: loads `useToday()`; before the check-in shows the cut-off help text and `CheckInForm`; after it shows today's status (time and Office or Field from the response, Late mark when the server says so, linked activities or purpose, `PhotoView`); a non-working day or approved full-day leave shows the one-line note and no form; wire the `managerCheckIn` key in `MOB/navigation/AppShell.tsx`; Create actions are shown only when the menu item's `actions` list `CREATE`

**Checkpoint**: US1 is complete and demonstrable on its own (the MVP).

---

## Phase 4: User Story 2 - A Manager Checks Out, and Completes a Missed Check-out (Priority: P1)

**Goal**: check out with a fresh location, and complete an earlier day's missing check-out with a time and a reason.

**Independent Test**: check in and out and confirm the times show; leave a past day without a check-out and complete it, and confirm the flag clears (quickstart scenarios 7-8).

### Tests for User Story 2

- [X] T023 [P] [US2] `MOBT/manager/checkOut.test.tsx`: Check out is offered only when today's day has a check-in and no check-out; it takes a fresh reading and posts `{latitude, longitude, accuracyMeters}` to `/check-out`; today's status then shows "Checked out at <time>" from the response; with location unavailable nothing is sent and the message of T009 is shown; a refusal (no check-in 400, already checked out 409, locked month 409) shows the server's reason and changes nothing; offline says "No connection. Nothing was sent." and keeps the screen as it was; a double tap sends one request
- [X] T024 [P] [US2] `MOBT/manager/completeCheckOut.test.tsx`: a date in `missingCheckOutDates` shows "No check-out on DD/MM/YYYY" with Complete check-out; the dialog has a time field (HH:mm) and a required reason of at most 500 characters ("reason of at most 500 characters"), Send disabled until both are present; it posts `{time, reason}` to `/days/{date}/complete-check-out`, reloads, and the flag is gone; refusals (a time not on that day, a future time, a locked month, a missing reason) show the server's reason with the entries kept; the same action is offered from a `NO_CHECK_OUT` day of My Month (checked again in T031); offline keeps the entries and shows nothing as completed

### Implementation for User Story 2

- [X] T025 [P] [US2] Create `MOB/manager/CompleteCheckOutDialog.tsx`: Paper dialog `{date, onDone, onCancel}` with the time and reason fields, Send disabled while either is empty after trimming, `useAction` for the call, an error line (role alert) with the entries kept on a refusal; add `MOBT/manager/CompleteCheckOutDialog.test.tsx`
- [X] T026 [US2] Add Check out to `MOB/screens/ManagerCheckInScreen.tsx`: a button using `useAction` that calls `captureFresh()` (T005), sends nothing without a fix, then `checkOut(body)`, then reloads `useToday()`; add the "No check-out on <date>" list with Complete check-out opening `CompleteCheckOutDialog` for each date in `missingCheckOutDates`

**Checkpoint**: US1 and US2 give a Manager the whole day: check in, check out, and put right a missed check-out.

---

## Phase 5: User Story 3 - Home Shows Today and Today's Plan (Priority: P2)

**Goal**: the Home screen shows today's state and plan for a Manager whose menu offers the check-in route.

**Independent Test**: read the Home card before checking in, after checking in and after checking out, then on a holiday (quickstart scenarios 2, 4 and 7).

### Tests for User Story 3

- [X] T027 [P] [US3] `MOBT/manager/todayCard.test.tsx`: the card appears on Home only when the server's menu contains `/manager-attendance/me` (a Teacher or Director menu shows none, a role-name scan finds no role check); it shows Not checked in with a Check in button that opens Check-in, then "Checked in at <time>, Office|Field" with Check out, then "Checked out at <time>"; a weekly off, a holiday and approved full-day leave show a one-line note and no Check in; after the cut-off with no check-in it says the Manager has not checked in and still offers Check in (the Late mark is only what the server returns); today's planned activities are listed in the server's order with kind, place and status; an empty plan says "No planned activities for today."; tapping an activity opens a details sheet with kind, place, date and status; each date in `missingCheckOutDates` is listed with Complete check-out; a failed load shows an error with Try again and offline shows the no-connection state

### Implementation for User Story 3

- [X] T028 [P] [US3] Create `MOB/manager/ActivityDetailsSheet.tsx`: a sheet showing an activity's kind, place, date and status (the server's record has no title) with a Close action; there is no record screen for any kind yet (spec 034), so it is the only destination; add `MOBT/manager/ActivityDetailsSheet.test.tsx`
- [X] T029 [US3] Create `MOB/manager/TodayCard.tsx` per contracts/screens.md "Home card": status line, linked activities or purpose, `PhotoView` thumbnail when present, Check in / Check out buttons (shown only when the menu item's `actions` list `CREATE` and the server's day allows them), the plan using `ActivityList` read-only with `ActivityDetailsSheet`, and the missing check-out list (T026); and add it to `MOB/screens/HomeScreen.tsx` when `model.navigation` contains the route `/manager-attendance/me`, in the way the Pending count appears only when the menu offers `/operations/leave`; pull-to-refresh reloads `useToday()`

**Checkpoint**: Home is the Manager's daily start page.

---

## Phase 6: User Story 4 - A Manager Sees Their Own Month (Priority: P2)

**Goal**: the Manager's month with status per day, times, links, flags and the server's figures, reusing the month pieces of spec 019.

**Independent Test**: sign in as a Manager with a mix of days and confirm every day, flag and figure matches the web Manager Attendance screen (quickstart scenario 9).

### Tests for User Story 4

- [X] T030 [P] [US4] `MOBT/attendance/monthGridGeneral.test.tsx`: after the refactor of T033 the Teacher My Attendance, Teacher Attendance, Holiday Calendar and Attendance History screens render the same grid, labels, colours and list layout at large text as before (the existing 019 snapshots and tests pass unchanged), and a grid built from Manager days renders the same layout rules (seven columns, list layout from font scale 1.3, 48 dp targets, accessible labels)
- [X] T031 [P] [US4] `MOBT/manager/managerMonth.test.tsx`: the screen opens on the current month from the server clock; each day shows the status text of T009 for all ten statuses (`WORKED`, `FIELD`, `NO_CHECK_OUT`, `LEAVE`, `HOLIDAY`, `WEEKLY_OFF`, `NOT_EMPLOYED`, `PENDING`, `MISSED`, `ABSENT`), check-in and check-out times from the server, Office or Field, the purpose and linked activities, leave (whole or half, paid or unpaid) and the flags in plain words (`LATE`, `NO_CHECK_OUT`, `ADDED_LATER`, `CORRECTED`, `UNLINKED`); the figures strip shows exactly the server's `figures` (working days, days worked, leave paid and unpaid, unpaid, field days) or the `figuresUnavailable` message in its place, and the app computes none of them (a fixture whose figures disagree with its days still shows the server's figures); a locked month shows "Locked" and offers no action that would change it; moving to another month loads it and a failed load shows an error with Try again; a month with no data shows an empty state; the day sheet offers Complete check-out on a `NO_CHECK_OUT` day (opening `CompleteCheckOutDialog`), and the photo thumbnail when `hasPhoto`

### Implementation for User Story 4

- [X] T032 [P] [US4] Create `MOB/attendance/managerDayPresentation.ts`: `presentManagerDay(day, mode)` returning the `DayLook` of `MOB/attendance/dayPresentation.ts` (label, background, border and text colours for light and dark from `MOB/theme/tokens.ts`, accessible label such as "Tuesday 3 November 2026, field day, checked in 09:42, no check-out") for the ten statuses and the flags; add `MOBT/attendance/managerDayPresentation.test.ts` (every status and flag in both themes, contrast of text on background)
- [X] T033 [US4] Generalise `MOB/attendance/MonthGrid.tsx` to take already-presented days (`{key, date, look, onPress?}`) instead of Teacher `DayView`s, keep the Teacher, Holiday Calendar and Attendance History call sites unchanged in behaviour by wrapping `presentDay` and `presentCalendarDay` around their existing calls, and keep `LIST_LAYOUT_FONT_SCALE`, the seven-column layout and the accessibility roles exactly as they are; run the existing 019 tests first and after
- [X] T034 [US4] Create `MOB/manager/ManagerDaySheet.tsx` (reuse `MOB/attendance/DaySheet.tsx` for the frame): the day's times, Office or Field, purpose, linked activities (read-only `ActivityList`), leave, flags in plain words, `PhotoView`, and the actions Complete check-out (for `NO_CHECK_OUT`) and, in US6, Ask to add a check-in; actions are shown only when the menu item's `actions` list `CREATE` and the month is not locked
- [X] T035 [US4] Create `MOB/screens/ManagerMonthScreen.tsx`: `useMonthView(getMonth, month)` with the `MonthPicker` of spec 019, the figures strip, the generalised `MonthGrid` fed by `presentManagerDay`, `ManagerDaySheet` on a tapped day, a Locked note, pull-to-refresh; wire the `managerMonth` key in `MOB/navigation/AppShell.tsx`

**Checkpoint**: a Manager can check their own month on the phone.

---

## Phase 7: User Story 5 - A Manager Requests Leave and Sees the Decision (Priority: P2)

**Goal**: request leave, follow it through the Director's decision, and cancel a Pending request.

**Independent Test**: request three days, see Pending, approve it on the web and see Approved; reject another and see the reason (quickstart scenario 10).

### Tests for User Story 5

- [X] T036 [P] [US5] `MOBT/manager/managerLeaveRequest.test.tsx`: the type chooser lists exactly the server's types in server order with the Loss-of-Pay type marked, none built in; first and last date, "Half day on the first day", "Half day on the last day" and a Reason field with a counter of 500 characters ("reason of at most 500 characters") are shown; Submit is disabled until a type, both dates and a reason are present and the last date is not before the first, and the app checks nothing else; Submit posts `{leaveTypeId, firstDate, lastDate, firstHalf, lastHalf, reason}` (reason trimmed, half-day flags false when unset) straight to `POST /leave` (there is no preview step); on success the list shows the new Pending request at the top with "Request submitted" and the form is cleared so the same request is not sent twice; a double tap sends one request
- [X] T037 [P] [US5] `MOBT/manager/managerLeaveRefusals.test.tsx`: for every leave refusal of contracts/server-calls.md (dates reversed, no working day in the range, a date before joining, no joining date, a missing reason, a half day on a middle day: 400; an overlap with another Pending or Approved request: 409) the server's `reason` appears in plain language, nothing is created, and every entry is kept; offline says "No connection. Nothing was sent." and keeps the form
- [X] T038 [P] [US5] `MOBT/manager/managerLeaveList.test.tsx`: the Manager's requests newest first with type, dates (DD/MM/YYYY) and half-day marks, status for all five (`PENDING`, `APPROVED`, `REJECTED`, `CANCELLED`, `REVOKED`) and, once decided, the decision and the decision reason; Cancel is shown on a Pending request exactly when the menu item's `actions` list `DELETE` and never on any other status; it confirms first, calls `DELETE /leave/{id}`, then reloads; a refusal shows the server's reason and the list is unchanged; pull-to-refresh reloads from the server; two Managers' fixtures show each only their own requests (the server answers per token)

### Implementation for User Story 5

- [X] T039 [P] [US5] Create `MOB/manager/useManagerLeave.ts`: `useManagerLeave()` returns `{state: "loading"|"ready"|"error"|"noConnection", types, items, reload}`; the list drops on a failed load; add `MOBT/manager/useManagerLeave.test.tsx`
- [X] T040 [US5] Create `MOB/screens/ManagerLeaveScreen.tsx`: the request form of contracts/screens.md "My Leave" using `DateChooser` from `MOB/leave/DateChooser.tsx` (reused unchanged), the list of requests with `StatusFilter`-style status chips reused from `MOB/leave/StatusFilter.tsx` if it fits, otherwise a plain list, `ReasonDialog` from `MOB/leave/ReasonDialog.tsx` for the Cancel confirmation (no text field), status labels for the five Manager statuses, pull-to-refresh; wire the `managerLeave` key in `MOB/navigation/AppShell.tsx`

**Checkpoint**: a Manager can ask for and follow leave on the phone.

---

## Phase 8: User Story 6 - A Manager Asks to Add a Forgotten Check-in (Priority: P3)

**Goal**: ask for a check-in on a past day with no check-in, see the request through to the decision, and cancel a Pending one.

**Independent Test**: ask to add a check-in for yesterday, see it Pending, approve it on the web and confirm the day shows as worked and "Added later" (quickstart scenario 11).

### Tests for User Story 6

- [X] T041 [P] [US6] `MOBT/manager/regularization.test.tsx`: a past working day with status `ABSENT` offers "Ask to add a check-in" in its day sheet and a locked month, a future day and a day with a check-in do not; the form has the date (fixed to the tapped day), the time (HH:mm), Office or Field (a purpose of at most 500 characters for Field) and a required reason of at most 500 characters; it posts `{date, proposedTime, dayType, purpose, reason}` to `POST /regularizations` and the "Check-in requests" list under the month shows the new Pending request; refusals (a future day or time, a day that already has a check-in, a pending request for the date, a missing reason: 400; a locked month: 409) show the server's reason with the entries kept; Cancel appears on a Pending request when the menu item's `actions` list `DELETE`, confirms, calls `DELETE /regularizations/{id}` and reloads; a decided request shows the outcome and, for a rejection, the decision reason; an approved one shows the day as "Added later" after the month reloads; offline keeps the entries and shows nothing as sent

### Implementation for User Story 6

- [X] T042 [P] [US6] Create `MOB/manager/RegularizationForm.tsx`: sheet `{date, onDone, onCancel}` with the time, day type, purpose (Field) and reason fields, Send disabled while time or reason is empty, `useAction` for the call, error line with the entries kept; add `MOBT/manager/RegularizationForm.test.tsx`
- [X] T043 [US6] Add the "Check-in requests" section to `MOB/screens/ManagerMonthScreen.tsx` (list, status, decision reason, Cancel for Pending) and the Ask to add a check-in action to `MOB/manager/ManagerDaySheet.tsx` for a past day without a check-in in an unlocked month, opening `RegularizationForm`; reload both the month and the requests after a send or cancel

**Checkpoint**: all Manager attendance requests are available on the phone.

---

## Phase 9: User Story 7 - Menus, Permissions and Safety Come From the Server (Priority: P2)

**Goal**: prove the screens appear and behave only because the server says so, and that nothing is ever shown as done unless confirmed.

**Independent Test**: sign in as a Manager, a Teacher and a Director and confirm only the Manager sees MY ATTENDANCE; remove a permission and confirm the matching button disappears (quickstart scenarios 1 and 13).

### Tests for User Story 7

- [X] T044 [P] [US7] `MOBT/manager/menuVisibility.test.tsx`: a Manager's menu shows Check-in, My Month and My Leave under MY ATTENDANCE exactly as the server returns them; a Teacher, a Director and an account with another app role show none of them; a deep link to one of the three routes by a user whose menu lacks it shows the not-authorized screen; removing `CREATE` from the item's `actions` hides Check in, Check out, Complete check-out, Add photo, Request leave and Ask to add a check-in, and removing `DELETE` hides every Cancel, while the screens still load; a source scan of `MOB/manager`, `MOB/screens/Manager*` and `MOB/api/manager*` finds no role name (`MANAGER`, `DIRECTOR`, `TEACHER`, `ADMIN`) used to decide what to show
- [X] T045 [P] [US7] `MOBT/manager/neverFalseSuccess.test.tsx`: for check-in, photo, check-out, completion, leave request, leave cancel, forgotten check-in request and its cancel, run each against (a) a refusal of the server, (b) no connection, (c) a 500, and (d) a 401 that cannot be renewed: in 20 runs there is no false "done" state, every entered value is kept (except after sign-in redirect where nothing was sent), the server's reason or the no-connection text is shown, and after a sign-in redirect nothing is shown as saved (SC-005, SC-006)
- [X] T046 [P] [US7] `MOBT/a11y/managerScreens.test.tsx`: the Check-in, My Month and My Leave screens and the Home card pass the accessibility checks of the existing suite in light and dark themes and at font scale 1.3 (labels on every control, 48 dp targets, no information in colour alone, the live regions that announce success and refusals)

### Implementation for User Story 7

- [X] T047 [US7] Apply the permission rules in one place: create `MOB/manager/useManagerActions.ts` that reads the matching menu item's `actions` from the access model (route `/manager-attendance/me`, `/me/month` or `/me/leave`) and returns `{canCreate, canDelete}`; use it in every Manager screen and component instead of reading `actions` separately, and make Check in, Check out, Complete check-out, Add photo, Request leave, Ask to add a check-in and every Cancel respect it; fix whatever T044 to T046 find

**Checkpoint**: all stories work and the safety properties are tested.

---

## Phase 10: Polish & Cross-Cutting Concerns

- [X] T048 [P] Update `docs/running-mobile.md` with the Manager walkthrough prerequisites (a Manager demo user with a joining date, a planned activity for today, and mocking the emulator location and camera) and the `expo-image-picker` rebuild note (a new native module needs `npx expo run:android` again)
- [X] T049 [P] Update `docs/spec-roadmap.md` row 033 to "Implemented" with the number of app tests, the new package and "device pass pending", and note on row 032 that the Manager screens now exist on mobile
- [X] T050 Run `npm run typecheck`, `npm run lint`, `npm test` and `npm run check:manifest` from `mobile/` and fix everything they report
- [ ] T051 (NOT DONE: needs an emulator or phone; see quickstart-results.md) Run the walkthrough in `quickstart.md` end to end on the emulator (steps 1 to 13) and record the results in `specs/033-mobile-manager-field/quickstart-results.md`, including the time to check in as Office (SC-001: under 30 seconds), as Field with an activity (under 60 seconds) and to complete a missed check-out (SC-008: under 45 seconds); steps that need a person at the device (camera, TalkBack, timings) are marked **not done** when not done, never as passed

---

## Dependencies & Execution Order

- **Setup (Phase 1)** first. **Foundational (Phase 2)** needs Phase 1 and blocks every story.
- **US1** needs Phase 2 only (the MVP). **US2** needs US1's screen (T022) to add Check out to it. **US3** needs T011, T018 and T026. **US4** needs Phase 2 and `CompleteCheckOutDialog` (T025); T033 (the grid refactor) must run its existing tests before and after. **US5** is independent of US1 to US4 after Phase 2. **US6** needs US4's screen and day sheet (T034, T035). **US7** is last because it tests everything.
- **Polish** last.
- Within a story: tests first (they may be written to fail), then the pieces marked [P], then the screen.

### Parallel opportunities

- Phase 1: T003 and T004 together.
- Phase 2: T008, T009 and T010 together once T006 and T007 exist; T005 and T006 are independent of each other.
- US1: T013 to T017 together; T018, T019 and T020 together.
- US5 can run in parallel with US1 and US2 once Phase 2 is done.
- US7: T044, T045 and T046 together.

## Implementation Strategy

**MVP**: Phase 1, Phase 2 and User Story 1 (check in with a fresh location, Office or Field, optional photo). Stop and run quickstart steps 1 to 6.
Then add US2 (check out and complete), US3 (Home), US4 (month), US5 (leave), US7 (permission pass) and finally US6 (the lowest
priority, which can be cut without affecting the others).
