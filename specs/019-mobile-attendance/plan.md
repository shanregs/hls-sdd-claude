# Implementation Plan: Mobile Attendance

**Branch**: `019-mobile-attendance` | **Date**: 2026-10-04 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/019-mobile-attendance/spec.md`

## Summary

Add the attendance screens to the Android app: Teacher **My Attendance** and **Attendance History**, a
read-only **Holiday Calendar** for every role, and Manager **Teacher Attendance** (list, month view,
mark, correct, clear, day history). The work is **app-only**. It uses the attendance endpoints of spec
008 unchanged, reaches each screen through the server-provided menu (spec 018), and adds no data, no
migration and no server rule. A shared month view and day sheet serve all three month screens. See
[research.md](research.md) for the decisions.

## Technical Context

**Language/Version**: TypeScript 5.x on React Native with Expo (the app from spec 018). No backend change.

**Primary Dependencies**: the existing app stack (React Native Paper, React Navigation stack for sign-in,
the in-app shell of spec 018). No new package is planned. Dates use plain JavaScript with the business
time zone (UTC+05:30) handled by a small helper, no date library.

**Storage**: none. Nothing is persisted on the phone and nothing new on the server. Month data is held in
memory while a screen is open.

**Testing**: Jest and React Native Testing Library against the in-memory fake server
(`mobile/__tests__/support/fakeServer.ts`) with attendance fixtures that copy the real response shapes;
request-shape contract tests; Manager-scope tests with two Managers' Teachers; the existing role-name scan
and accessibility checks extended to the new screens. Manual: emulator pass and TalkBack.

**Target Platform**: Android 10 (API 29) and later, phones, as in spec 018.

**Project Type**: mobile app (client of the existing web service). No new project.

**Performance Goals**: mark today in under 30 seconds from opening the app (SC-001); any month of the
current year in two taps (SC-002); a month loads in under 2 seconds on a typical mobile connection; the
Manager list pages 25 at a time.

**Constraints**: the server decides every rule and every day's state and editability; the app computes no
totals, windows or scope; a mark is shown as saved only after the server confirms it; no offline queue;
every call carries the location or its reason (spec 018); light and dark themes; TalkBack-usable.

**Scale/Scope**: under 100 users (Constitution). Four menu entries, three month-based screens sharing one
month view and one day sheet, one list screen, one calendar screen.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design.*

| Principle | Assessment | Status |
| --------- | ---------- | ------ |
| I. Operational truth is auditable | Every mark, correction and clearing is written by the server's existing attendance service and audited as in spec 008. The app keeps no authoritative copy and shows nothing as saved before a 2xx. Calls also appear in the API Access trail of spec 018. | Pass |
| II. Five fixed roles, configurable permissions | No new role or permission key. Screens use `MY_ATTENDANCE`, `TEACHER_ATTENDANCE` and `HOLIDAY_CALENDAR`. Only Admin, Director and System can edit the matrix, unchanged. | Pass |
| III. Data scope | A Manager sees only Teachers the server returns; a 404 shows no data. The app adds no scope logic. Teacher screens use the caller's own record only. Tests use two Managers. | Pass |
| IV. Role-based experience, one framework | Menu entries come only from the server's access model, filtered by the screen registry; no role names; light and dark; WCAG-oriented labels and 48 dp targets; loading, empty and error states on every screen. | Pass |
| V, VI, VIII, XI | Not touched (no payroll, training, batch or recruitment). | N/A |
| VII. Modular monolith | No backend change, so no module boundary is touched. | Pass |
| IX. Reliability and testability | Per-scope tests (two Managers), menu-visibility tests per role, tests for every refusal, and tests that a failed or offline save is never shown as saved. | Pass |
| X. Security, identity, observability | The backend stays the control; no data cached on disk; location and client headers as in spec 018. | Pass |

Also checked against *Additional Constraints*: React Native Android-first; dates as DD/MM/YYYY and month
names; the "offline readiness" constraint is deliberately not met here (spec Out of scope) and is the
next mobile spec.

**Post-design re-check**: no violations. The one judgement call, using the HTTP `Date` header to choose the
default month (research §3), changes no rule and needs no waiver.

## Project Structure

### Documentation (this feature)

```text
specs/019-mobile-attendance/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── mobile-attendance-screens.md
├── checklists/
│   └── requirements.md
└── tasks.md            # created by /speckit-tasks, not here
```

### Source Code (repository root)

```text
mobile/
├── src/
│   ├── access/screenRegistry.ts          # CHANGED: register the four routes and screen keys
│   ├── navigation/AppShell.tsx           # CHANGED: render the four new screens; back handling
│   ├── api/attendanceApi.ts              # NEW: typed calls and shapes for the endpoints above
│   ├── attendance/
│   │   ├── monthRange.ts                 # NEW: allowed months, default month, business time zone
│   │   ├── serverClock.ts                # NEW: offset from the HTTP Date header
│   │   ├── dayPresentation.ts            # NEW: colour, letter and accessible label per day state
│   │   ├── refusalMessages.ts            # NEW: plain wording for known 409 reasons
│   │   ├── useMonthView.ts               # NEW: load, reload and error state for one month
│   │   ├── MonthPicker.tsx               # NEW
│   │   ├── MonthGrid.tsx                 # NEW: seven-column grid with list fallback
│   │   ├── DayLegend.tsx                 # NEW
│   │   ├── RollupStrip.tsx               # NEW
│   │   └── DaySheet.tsx                  # NEW: details, edit form, Clear, History
│   ├── screens/
│   │   ├── MyAttendanceScreen.tsx        # NEW
│   │   ├── AttendanceHistoryScreen.tsx   # NEW
│   │   ├── HolidayCalendarScreen.tsx     # NEW (month view, list, year list)
│   │   ├── TeacherAttendanceScreen.tsx   # NEW (search, list, paging)
│   │   └── TeacherMonthScreen.tsx        # NEW (a Manager's view of one Teacher)
│   └── formats/dates.ts                  # CHANGED: month names, weekday names
└── __tests__/
    ├── support/attendanceFixtures.ts     # NEW: realistic month, status code, calendar and grid data
    ├── attendance/                       # NEW: screens, refusals, scope, offline, picker, a11y
    └── api/attendanceContract.test.ts    # NEW: request shapes against the contract

docs/spec-roadmap.md                      # status of row 019 as work proceeds
```

**Structure Decision**: all work is under `mobile/`. `backend/` and `frontend/` are unchanged. The month
view, day sheet and picker are shared components so My Attendance, History and a Manager's Teacher view
differ only in the data source, the allowed month range and whether editing is offered.

## Complexity Tracking

No Constitution Check violations to justify.

## Risks and notes for tasks

- **Present first**: confirm the server returns Present first in the status-code order; if not, order by
  position only (research §4), never by a hard-coded code.
- **Server clock**: a missing `Date` header must fall back to the phone clock for the default month only.
- **Large text**: the seven-column grid must degrade to a list so cells stay tappable and readable.
- **Refusal count (SC-004)**: re-count the Teacher and Manager refusal reasons against the 008 contract
  when writing the tests; the spec's "6" and "4" were taken from memory of that contract.
- **School weekly offs**: for the calendar, use the School of the user's own placement when the response
  lists a School override; otherwise show the default.
- **Stale screens**: a screen open when permissions change shows "Not authorized" on the next menu refresh,
  as spec 018 already does.
