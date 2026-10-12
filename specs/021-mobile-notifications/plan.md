# Implementation Plan: Mobile Notifications

**Branch**: `021-mobile-notifications` | **Date**: 2026-10-08 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/021-mobile-notifications/spec.md`

## Summary

Add notifications to the Android app: a **bell with the unread count** in the shell header, and a **Notifications
screen** (ACCOUNT → Notifications) with a paged list, an Unread-only filter, open-and-follow-link, mark one or all
read, delete one and clear read. The work is **app-only**. It uses the notification endpoints of spec 010 unchanged,
reaches the screen through the server-provided menu (spec 018), maps the three server links to the screens of specs
019 and 020, and adds no data, no migration and no server rule. See [research.md](research.md) for the decisions and
for the answers to the three questions the spec left to plan time (My Attendance takes a month after a small change;
the shell has one shared header; the list response's `unread` makes opening the list one request).

## Technical Context

**Language/Version**: TypeScript on React Native with Expo (the app from specs 018 to 020). No backend change.

**Primary Dependencies**: the existing app stack (React Native Paper, the in-app shell of spec 018). No new package.

**Storage**: none. The count and the list live in memory while the shell is mounted; nothing is written to the phone
and nothing new on the server.

**Testing**: Jest and React Native Testing Library against the in-memory `FakeServer` with a notifications module and
fixtures that copy the real response shape; fake timers and an `AppState` mock for polling; request-shape contract
test; link-mapping unit tests; the existing role-name scan and accessibility checks extended to the new files. Manual:
emulator pass and TalkBack.

**Target Platform**: Android 10 (API 29) and later, phones, as in spec 018.

**Project Type**: mobile app (client of the existing web service). No new project.

**Performance Goals**: bell reflects a new notification within 30 s (SC-001); bell to destination screen in under
10 s (SC-002); the list loads in under 2 s on a typical mobile connection; pages of 25.

**Constraints**: the server decides scope, permissions and counts; the app computes none of them; no action is shown
as done before the server confirms; no request while the app is in the background (SC-009); no offline queue; every
call carries the location or its reason (spec 018); light and dark themes; TalkBack-usable.

**Scale/Scope**: under 100 users (Constitution). One menu entry, one screen, one bell, one confirmation dialog, one
detail dialog, a small provider.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design.*

| Principle | Assessment | Status |
| --------- | ---------- | ------ |
| I. Operational truth is auditable | No attendance, leave or financial record changes. Reading and deleting a notification is not audited, as in spec 010; deleting never touches the leave or attendance record behind it. Calls appear in the API Access trail with the location or its reason (spec 018). | Pass |
| II. Five fixed roles, configurable permissions | No new role or permission key. Uses `NOTIFICATIONS` (View, Delete). Delete is offered only when the server's `actions` for the item contain `DELETE`. Only Admin, Director and System can edit the matrix, unchanged. | Pass |
| III. Data scope | Each user reads only their own notifications; the server answers 404 for anyone else's and the app shows no data. The app adds no scope logic. A link never widens access: the destination applies its own scope. | Pass |
| IV. Role-based experience, one framework | The bell and the menu item come only from the server's access model, filtered by the screen registry; no role names; light and dark; labelled bell, 48 dp targets, loading, empty and error states. The bell sits in the shell's one header. | Pass |
| V, VI, VIII, XI | Not touched (no payroll, training, batch or recruitment). | N/A |
| VII. Modular monolith | No backend change, so no module boundary is touched. | Pass |
| IX. Reliability and testability | Tests for scope (two users), menu visibility per access model, every refusal, polling start and stop, and that a failed read, delete or clear is never shown as done. | Pass |
| X. Security, identity, observability | The backend stays the control; the app keeps no copy on disk and clears memory on sign-out; no polling in the background. | Pass |

Also checked against *Additional Constraints*: React Native Android-first; dates as DD/MM/YYYY; the "offline
readiness" constraint is deliberately not met here (spec Out of scope).

**Post-design re-check**: no violations. The judgement calls (bell only in the shell header, not in the Devices and
Location privacy sub-screens, research §3; `history` range for a previous-year month link, research §2) change no rule
and need no waiver.

## Project Structure

### Documentation (this feature)

```text
specs/021-mobile-notifications/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── mobile-notifications-screens.md
├── checklists/
│   └── requirements.md
└── tasks.md            # created by /speckit-tasks, not here
```

### Source Code (repository root)

```text
mobile/
├── src/
│   ├── access/screenRegistry.ts             # CHANGED: ScreenKey "notifications" for /account/notifications
│   ├── api/notificationsApi.ts              # NEW: typed calls and shapes for the endpoints in the contract
│   ├── config/constants.ts                  # CHANGED: NOTIFICATION_POLL_MS (30 000), NOTIFICATION_PAGE_SIZE (25)
│   ├── notifications/
│   │   ├── NotificationsProvider.tsx        # NEW: count, canDelete, polling with AppState, refreshCount()
│   │   ├── NotificationBell.tsx             # NEW: header action with badge and accessible label
│   │   ├── linkTarget.ts                    # NEW: link string -> { route, month? } or null
│   │   ├── notificationMessages.ts          # NEW: plain wording for failures
│   │   ├── NotificationRow.tsx              # NEW: one row with read state and actions
│   │   ├── NotificationDetailDialog.tsx     # NEW: full text when no link can be followed
│   │   └── useNotificationList.ts           # NEW: paged list with filter, reload and error state
│   │                                        # (Clear read reuses leave/ReasonDialog in its no-text mode)
│   ├── navigation/AppShell.tsx              # CHANGED: provider under AccessModelProvider, bell in the header,
│   │                                        #          render the screen, RouteState.month
│   ├── screens/NotificationsScreen.tsx      # NEW
│   ├── screens/MyAttendanceScreen.tsx       # CHANGED: optional initialMonth, range kind for a previous-year month
│   └── formats/dates.ts                     # unchanged: formatDateTime already gives DD/MM/YYYY HH:mm
└── __tests__/
    ├── support/notificationsFixtures.ts     # NEW: realistic notifications and pages
    ├── support/notificationsServer.ts       # NEW: notification routes on the FakeServer
    ├── notifications/                       # NEW: bell, polling, list, open and links, mark/delete/clear, scope, a11y
    └── api/notificationsContract.test.ts    # NEW: request shapes against the contract

docs/spec-roadmap.md                         # status of row 021 as work proceeds
docs/running-mobile.md                       # manual checklist item for notifications
```

**Structure Decision**: all work is under `mobile/`; `backend/` and `frontend/` are unchanged. The provider sits
above `ShellContent` (which returns early for overlays and loading states) so the count survives them. The list,
the rows and the dialogs are small shared pieces; the screen stays thin.

## Complexity Tracking

No Constitution Check violations to justify.

## Risks and notes for tasks

- **Provider placement**: put `NotificationsProvider` under `AccessModelProvider` in `AppShell`, enabled from the
  model, so it is not reset when `ShellContent` returns early. Tests must cover opening Devices and returning.
- **Polling hygiene**: one request in flight; timer cleared on `background`/`inactive` and on unmount; restart and an
  immediate refresh on `active`. Test with fake timers that no request is made while backgrounded (SC-009).
- **openRoute shows "Not authorized" for a refused route**: check `canOpen` before calling it, so an unfollowable
  link shows the detail dialog instead (FR-005).
- **My Attendance remount**: pass `key={month}` so a second link to another month re-initialises `MonthPane`; the
  month is also dropped from `RouteState` when the user navigates elsewhere.
- **Time zone**: use the existing `formatDateTime` from `formats/dates.ts` for `createdAt` (it formats in the device's
  zone, like the other screens); add no second formatter.
- **Single-tap safety**: a row with a request in flight ignores further taps; repeated opens of a read notification
  send no call.
- **Header on sub-screens**: Devices and Location privacy keep their own header with no bell (research §3); say so in
  `quickstart-results.md` so it is not mistaken for a defect.
