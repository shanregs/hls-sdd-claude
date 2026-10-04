# Contract: Mobile Attendance screens and server calls

This feature adds **no server endpoint**. The contract below is (a) which existing server calls each
screen makes and (b) what each screen must show and do. Server shapes are in
`specs/008-attendance/contracts/attendance-api.md`; the headers and error handling of every call are in
`specs/018-android-app-foundation/contracts/mobile-api.md`. Each call carries the client and location
headers of spec 018 and the bearer token.

## Menu routes (from the server's access model, unchanged)

| Route | Menu label | Shown to | Screen |
| ----- | ---------- | -------- | ------ |
| `/my-attendance` | My Attendance | Teacher | My Attendance |
| `/my-attendance/history` | Attendance History | Teacher | Attendance History |
| `/operations/teacher-attendance` | Teacher Attendance | Manager | Teacher Attendance (list, then month) |
| `/master-data/holiday-calendar` | Holiday Calendar | every role | Holiday Calendar |

The app registers exactly these four routes in `mobile/src/access/screenRegistry.ts`. A route the server
does not offer is never shown, and `canOpen(route)` still guards every navigation (spec 018 FR-013).

## Server calls per screen

### My Attendance and Attendance History (Teacher)

| Call | When | Notes |
| ---- | ---- | ----- |
| `GET /api/v1/attendance/me?month=YYYY-MM` | opening the screen, changing month, after every save | Returns `TeacherMonthView`. `404 "Your profile has not been set up yet."` is shown as given. History uses the same call with earlier months. |
| `GET /api/v1/attendance/status-codes?activeOnly=true` | the first time the day sheet opens | Active codes in server order; `NON_WORKING` category codes are not offered. |
| `PUT /api/v1/attendance/me/marks/{date}` | Save in the day sheet | Body `{statusCode, dayValue, note, version?}`. 2xx means saved. 409 carries `{reason}` (see Errors). |

### Holiday Calendar (every role)

| Call | When | Notes |
| ---- | ---- | ----- |
| `GET /api/v1/attendance/calendar` | opening the screen, and on retry | One response serves the month view and the year list. Read-only: no write call exists in the app. |

### Teacher Attendance (Manager)

| Call | When | Notes |
| ---- | ---- | ----- |
| `GET /api/v1/attendance/teacher-grid?month=&query=&page=&size=25` | opening the list, changing month, search (debounced 300 ms), "load more" | Server returns only in-scope Teachers. |
| `GET /api/v1/attendance/teachers/{teacherId}?month=` | opening a Teacher, changing month, after every change | `TeacherMonthView`; 404 means "outside your scope" and shows "not found" with no data. |
| `PUT /api/v1/attendance/teachers/{teacherId}/marks/{date}` | Save in the day sheet | Same body as above; writes a supervisor mark. |
| `DELETE /api/v1/attendance/teachers/{teacherId}/marks/{date}` | Clear in the day sheet | 409 if the month is locked. |
| `GET /api/v1/attendance/teachers/{teacherId}/marks/{date}/history` | "History" in the day sheet | Newest first. |

## What each screen must show

### Month view (shared by My Attendance, History and a Manager's Teacher view)
- Header: month name and year, previous and next arrows, tap to open the month list. Allowed months:
  current year, plus the previous year on Attendance History only.
- Totals strip from `rollup`: working days, days worked, leave, unmarked. A locked month says "Locked".
- Seven-column grid, one cell per `days[]` entry: day number, a letter and colour for the state, the
  status short code for a marked day, a half-day marker when `dayValue` is 0.5. Each cell has an
  accessible label (weekday, date, state, status, day value).
- Legend for the states and colours, with light and dark variants.
- Tapping a cell opens the day sheet: read-only details for any day; an editable form only when the
  day's `editableBy` allows it for the viewer: `SELF` on a Teacher's own screens, `SUPERVISOR` on a
  Manager's Teacher view. The app never works this out from the day's state, the month or the phone's date.
- Loading, empty and error states with a Retry; an error never leaves stale data shown as current.

### Day sheet
- Shows the date, school, state, status, day value, note, who set it and when. A supervisor mark shows
  "Set by <name>" and the sentence "Ask your Manager to correct it" for a Teacher.
- Edit form: status list (active codes in server order, minus the non-working Holiday code, no hard-coded codes), whole or half day, note, Save. Manager
  view adds Clear and History.
- Save is disabled with the reason shown when the day is not editable. The draft stays on screen after a
  failure; nothing is shown as saved before a 2xx (FR-005).

### Manager list
- Search box always visible, month selector, rows with name, school and the rollup figures, "load more".
- Empty state: "No Teachers found". The list never shows a Teacher the server did not return.

### Holiday Calendar
- Month grid (default current month) marking holidays and weekly offs, a legend, a list of the
  month's holidays with descriptions, and a button "All holidays this year" that opens the year list in
  date order. No edit action anywhere.

## Errors and messages

| Server answer | The app shows |
| ------------- | ------------- |
| 409 with `{reason}` | The reason, in plain words, on the day sheet; the day is unchanged and the draft is kept. Known reasons: future date, older than 3 days, locked month, no placement that date, already set by a supervisor ("Your Manager set this day. Ask them to correct it."), Teacher exited, changed meanwhile (the month is re-loaded). |
| 404 on a Teacher | "Not found", no data. |
| 404 "profile not set up" | The server's message. |
| 401 | Handled by the sign-in layer of spec 018 (renew once, else sign out). |
| No connection | "No connection" with Retry; saves keep the draft. |
| 403 | "You are not allowed to do this." |

No contact shortcut or correction request exists in this release (clarification Q4).

## Not in this contract

Offline queue, push notifications, leave, photos, Admin and Director grids, month lock or reopen, status
code setup, calendar editing, CSV and PDF export: web only or later specs.
