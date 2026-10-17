# Implementation Plan: Mobile Manager Field Day

**Branch**: `033-mobile-manager-field` | **Date**: 2026-10-17 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/033-mobile-manager-field/spec.md`

## Summary

Add the Manager's own attendance to the Android app: **Check-in** (with Home status and today's plan), **My Month**
and **My Leave**, the three entries the server already offers a Manager under MY ATTENDANCE. The work is **app-only**.
It calls the Manager attendance and Manager leave endpoints of spec 032 unchanged, reaches each screen through the
server-provided menu (spec 018), and adds no data, no migration and no server rule. Three things are new in the app's
plumbing: a **fresh location reading** sent in the request body (check-in and check-out refuse to go without one, D15),
a **multipart photo upload**, and a **camera/gallery picker** (`expo-image-picker`). The month grid of spec 019 is
generalised so Teacher days and Manager days share one grid. See [research.md](research.md) for the decisions.

Two points in the spec were corrected after reading the 032 server (recorded in research.md §9): the day view carries
only a "has photo" flag, so a photo's file name, type and size come from the photo download and "who and when" is
not shown; and a planned activity has a kind, place, date and status but no name.

## Technical Context

**Language/Version**: TypeScript 6.x on React Native 0.86 with Expo SDK 57 (the app from spec 018). No backend change.

**Primary Dependencies**: the existing app stack (React Native Paper, `expo-location`, the in-app shell of spec 018).
One new package: `expo-image-picker` (~57.0.20) for the camera and the gallery. No date library; the business time zone
helper of `src/formats/dates.ts` is reused.

**Storage**: none. Nothing is persisted on the phone and nothing new on the server. Day, month and leave data are held in
memory while a screen is open; a picked photo is held only until it is uploaded or discarded.

**Testing**: Jest and React Native Testing Library against the in-memory fake server
(`mobile/__tests__/support/fakeServer.ts`), extended with the Manager attendance and leave endpoints and fixtures that
copy the real response shapes; request-shape contract tests; the existing role-name scan and accessibility checks
extended to the new screens; manifest check extended for the camera. Manual: emulator pass with a mock location and
TalkBack (quickstart.md).

**Target Platform**: Android 10 (API 29) and later, phones, as in spec 018.

**Project Type**: mobile app (client of the existing web service). No new project.

**Performance Goals**: check in as Office in under 30 seconds from opening the app and as Field with an activity in
under 60 (SC-001); Home status and month load in under 2 seconds on a typical mobile connection; a photo of up to the
server's size limit uploads without blocking the check-in.

**Constraints**: the server decides every rule, day state, flag and figure; the app computes none of them; nothing is
shown as done before a 2xx; no offline queue; location is read only at a tap or a load, never in the background (spec 018);
location is **required** for check-in and check-out and never refused for accuracy; light and dark themes; TalkBack-usable;
48 dp targets.

**Scale/Scope**: under 100 users (Constitution). Three menu entries, three screens, one Home card, one photo flow, two
request forms (leave, forgotten check-in), one generalised month grid.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design.*

| Principle | Assessment | Status |
| --------- | ---------- | ------ |
| I. Operational truth is auditable | Every check-in, check-out, completion, photo, leave and forgotten-check-in request is written and audited by the 032 services (data-model.md of 032, "Audit entity types"). The app keeps no authoritative copy, shows nothing as done before a 2xx, and the calls also appear in the API Access trail of spec 018. | Pass |
| II. Five fixed roles, configurable permissions | No new role or permission key. Screens use `MY_MANAGER_ATTENDANCE` (View, Create, Delete). Only Admin, Director and System can edit the matrix, unchanged. | Pass |
| III. Data scope | Own data only: the server resolves the Manager from the token and no screen takes a Manager id. Tests use two Managers. | Pass |
| IV. Role-based experience, one framework | Menu entries come only from the server's access model through the screen registry (routes `/manager-attendance/me`, `/me/month`, `/me/leave`); no role names; buttons follow the granted actions; light and dark; 48 dp targets; loading, empty and error states. | Pass |
| V, VI, VIII, XI | Not touched (no payroll, training, batch or recruitment change). | N/A |
| VII. Modular monolith | No backend change, so no module boundary is touched. | Pass |
| IX. Reliability and testability | Tests for every refusal of 032, for a failed or offline action never shown as done, for location refusal (nothing sent), for two Managers, for menu visibility per role, and an emulator walkthrough. | Pass |
| X. Security, identity, observability | The backend stays the control. A photo is held in memory only until upload; location only at a tap or load; the camera permission is requested only when the Manager chooses "Take a photo"; no microphone or broad storage permission; the manifest check enforces it. | Pass |

No violations, so Complexity Tracking is empty. Re-check after design: unchanged (the one new package and the multipart
helper add no module, permission or storage).

## Project Structure

### Documentation (this feature)

```text
specs/033-mobile-manager-field/
├── plan.md              # This file
├── research.md          # Phase 0: decisions
├── data-model.md        # Phase 1: the shapes the app reads and sends
├── quickstart.md        # Phase 1: how to run and prove it
├── contracts/
│   ├── server-calls.md  # the 032 endpoints the app calls and how it uses each
│   └── screens.md       # the three screens, Home card and their states
└── tasks.md             # Phase 2 (/speckit-tasks)
```

### Source Code (repository root)

```text
mobile/
├── app.config.ts                         # + expo-image-picker plugin (camera only)
├── package.json                          # + expo-image-picker
├── scripts/check-manifest.mjs            # + no microphone / broad storage permission
├── src/
│   ├── access/screenRegistry.ts          # + managerCheckIn, managerMonth, managerLeave
│   ├── api/
│   │   ├── httpClient.ts                 # + upload() (multipart) and download of a photo with headers
│   │   ├── managerAttendanceApi.ts       # NEW: today, check-in/out, complete, month, photo, regularizations
│   │   └── managerLeaveApi.ts            # NEW: types, list, request, cancel
│   ├── location/LocationGate.ts          # + captureFresh() (no reuse window), same reading goes in body and header
│   ├── attendance/
│   │   ├── MonthGrid.tsx, dayPresentation.ts   # generalised: a Teacher day and a Manager day share the grid
│   │   └── managerDayPresentation.ts     # NEW: Manager statuses and flags to a DayLook
│   ├── manager/                          # NEW
│   │   ├── useToday.ts                   # today's status + plan, reload after each action
│   │   ├── TodayCard.tsx                 # the Home card
│   │   ├── CheckInForm.tsx               # day type, activities / purpose, photo, Check in
│   │   ├── ActivityList.tsx              # planned activities, selectable or read-only
│   │   ├── PhotoPicker.tsx               # take / choose / remove; shows file name, type, size
│   │   ├── CompleteCheckOutDialog.tsx    # time + reason
│   │   ├── RegularizationForm.tsx        # forgotten check-in request
│   │   ├── managerMessages.ts            # plain-language texts for location and 032 refusals
│   │   └── useManagerLeave.ts            # list + request + cancel
│   └── screens/
│       ├── ManagerCheckInScreen.tsx      # NEW
│       ├── ManagerMonthScreen.tsx        # NEW (month, figures, flags, request list)
│       ├── ManagerLeaveScreen.tsx        # NEW (list + request form)
│       └── HomeScreen.tsx                # + TodayCard when the menu offers the check-in route
└── __tests__/
    ├── support/fakeServer.ts             # + Manager endpoints
    └── manager/                          # NEW tests per story
```

**Structure Decision**: all work is in `mobile/`, following the layout of specs 019 to 021 (an `api/` file per area, a
feature folder for its pieces, screens in `screens/`). The only shared files touched are the HTTP client (multipart),
the location gate (a fresh reading), the screen registry, the home screen and the month grid; each change is additive and
leaves the Teacher and Director screens behaving as before. No backend, web or database change.

## Complexity Tracking

Empty: the Constitution Check has no violations.
