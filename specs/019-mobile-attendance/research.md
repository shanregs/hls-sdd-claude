# Research: Mobile Attendance

Decisions taken before design, checked against the existing code: the attendance server code and
contract (`backend/.../attendance/`, `specs/008-attendance/contracts/attendance-api.md`), the web
attendance screens (`frontend/src/features/attendance/`), and the app from spec 018 (`mobile/`).

## 1. No server changes

- **Decision**: this feature is app-only. Every screen uses endpoints that spec 008 already provides:
  `GET /api/v1/attendance/me?month=`, `PUT /api/v1/attendance/me/marks/{date}`,
  `GET /api/v1/attendance/status-codes`, `GET /api/v1/attendance/calendar`,
  `GET /api/v1/attendance/teacher-grid`, `GET|PUT|DELETE /api/v1/attendance/teachers/{id}/...`, and
  the day-history endpoint. The menu entries already exist in the server's navigation catalog.
- **Rationale**: the server is the authority for every rule (window, locks, scope, audit). Adding app
  rules would duplicate them and drift.
- **Alternatives considered**: a purpose-built mobile summary endpoint (fewer calls, but a second
  contract to keep in step with 008); rejected until a real performance need appears.

## 2. Screens are driven by the server's menu, through the registry

- **Finding**: the server offers `/my-attendance`, `/my-attendance/history`,
  `/operations/teacher-attendance` and `/master-data/holiday-calendar` (for every role, under MASTER
  DATA). The app's registry (`mobile/src/access/screenRegistry.ts`) maps routes to screens and hides
  everything else (spec 018 FR-011).
- **Decision**: add the four routes to the registry and four screen keys. `MASTER DATA` then shows only
  Holiday Calendar for app users, because the other MASTER DATA routes have no screen. No role name is
  used anywhere.
- **Consequence**: the existing test that scans `src/` for role-name literals keeps guarding FR-015.

## 3. How the app knows "today" without trusting the phone clock

- **Finding**: the server decides the business date (Asia/Kolkata) and every day's `state` and
  `editableBy`, so the window and future-day rules never need the phone's date. The only place the app
  needs "today" is to pick the **default month**.
- **Decision**: take the server's time from the HTTP `Date` header of a response the app already
  receives (the access-model call at sign-in), convert it to the business time zone (UTC+05:30, no
  daylight saving), and use that month as the default. The phone clock is never used for rules; if the
  header is missing the app falls back to the phone's clock for the default month only.
- **Alternatives considered**: a new "server date" endpoint (a server change for one value); using the
  phone clock (wrong on a mis-set phone, and spec FR-008 forbids it for rules).

## 4. Which statuses a Teacher can choose (clarification Q1)

- **Finding**: `GET /api/v1/attendance/status-codes` returns active codes with `shortCode`, `name`,
  `category`, `weight`. The web mark dialog uses the same list.
- **Decision**: show the active codes in the server's order, except codes of category `NON_WORKING`
  (Holiday), which are not a Teacher's choice. Do not hard-code any code. The server remains the
  judge: a refused status shows its reason (FR-004).
- **Open point for tasks**: confirm the server returns Present first. If it does not, the first-listed
  worked code is shown first by position only, never by a literal code.

## 5. Month data and caching

- **Decision**: fetch one month at a time and keep only the month on screen (and the one just left) in
  memory. Nothing is stored on the phone's disk. A save re-fetches the month so totals, locks and "who
  set it" always come from the server. No offline queue (FR-005).
- **Rationale**: attendance is operational truth (Constitution Principle I); a local copy could show a
  stale mark as current (FR-020).

## 6. Concurrency and stale data

- **Decision**: a mark carries the `version` of the mark it replaces. A 409 for "changed meanwhile"
  re-loads the month and tells the user the day changed; the user's entry is kept on screen to retry.

## 7. Month picker

- **Decision**: a header with previous / next arrows and a tap-to-open list of months. The range is
  January to December of the current year; Attendance History alone adds the previous year (clarified
  Q5). The picker reaches any month in two taps (SC-002).

## 8. Calendar layout on a phone

- **Decision**: a seven-column month grid (week starts on Monday, as in India's office calendars and the
  web) with a colour and a letter per day state, plus a list fallback when the text size is large. Each
  cell is a button with an accessible label such as "Mon 6 October, Present, whole day".
- **Rationale**: matches the web legend (`DayLegend`, `dayStyle`) so the two clients read the same.
  Colours follow the existing theme tokens in both light and dark.

## 9. Holiday Calendar content

- **Finding**: `GET /api/v1/attendance/calendar` returns default weekly offs, per-School overrides, and all
  non-working dates for the year.
- **Decision**: the month view marks non-working dates and weekly offs; the year list (clarified Q3)
  comes from the same response, so there is only one call. For a Teacher or Manager, School-specific
  weekly offs are shown for the School the user's data belongs to; otherwise the default applies.

## 10. Manager list

- **Finding**: `GET /api/v1/attendance/teacher-grid?month=&query=&page=&size=` already returns one row per
  in-scope Teacher with the rollup figures; the server filters by scope.
- **Decision**: use it for the list (name, School, totals), with the always-visible search box (Q2)
  debounced by 300 ms, and page size 25 with "load more". Opening a Teacher uses the Teacher month
  endpoint, the same view model as My Attendance.

## 11. Marking and clearing as a Manager

- **Decision**: reuse the same day sheet (status, whole or half day, note) with a Clear action added. A
  Manager's PUT and DELETE go to `/attendance/teachers/{id}/marks/{date}`; the day-history endpoint feeds
  the history list. Scope is enforced by the server; a 404 shows "not found" and no data (US4 scenario 7).

## 12. Messages for refusals (FR-004, FR-014)

- **Decision**: show the server's `reason` text as given, and map the known reasons to the plain
  wording agreed in the spec only where the server text is technical. Keep the user's entry. No contact
  shortcut (clarification Q4).

## 13. Location, audit and security

- **Decision**: nothing new. All calls go through the existing client, which adds the location header
  and the client headers (spec 018). The server records the audit entry and the API Access entry as it
  does for any call.

## 14. Testing approach

- **Decision**: Jest with React Native Testing Library against the existing in-memory fake server in
  `mobile/__tests__/support/fakeServer.ts`, extended with attendance fixtures that copy the real
  response shapes. Contract tests pin the request shapes. Manager-scope tests use two Managers'
  Teachers in the fixtures. The role-name scan test already covers FR-015. Accessibility is checked with
  the existing label/role checks plus a manual TalkBack pass.

## Resolved unknowns

All Technical Context items are resolved. Carried into tasks: confirm Present is first in the server's
status-code order (point 4), and re-count the refusal reasons in SC-004 against the 008 contract.
