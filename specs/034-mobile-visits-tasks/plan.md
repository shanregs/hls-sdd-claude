# Implementation Plan: Mobile Visits and Tasks

**Branch**: `034-mobile-visits-tasks` | **Date**: 2026-10-18 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/034-mobile-visits-tasks/spec.md`

## Summary

Add the Manager's visits and tasks to the Android app: a **visits** screen (the server's Marketing Calendar entry) that lists a
month at a time, opens a visit, completes it with an outcome, plans, moves and cancels visits; a **tasks** screen (the server's
My Tasks entry) with the server's six views, a task detail, and acknowledge, start, overdue reason, complete with a note,
photo evidence and adding a task for oneself; and two **Home** cards (today's visits, overdue tasks). The work is **app-only**.
It calls the marketing activity endpoints of spec 023 and the task endpoints of spec 025 unchanged, reaches the screens through
the server-provided menu (spec 018), and adds no data, no migration and no server rule. The photo picking, multipart upload,
binary download, in-flight action hook, month picker and date chooser come from specs 019, 020 and 033. See
[research.md](research.md) for the decisions.

Read from the running server and code, not the input document (research.md §1 and §2): a task carries an `actions` object that
says exactly which buttons are allowed, but a visit carries none, so visit buttons follow the menu's Create/Edit grants and the
visit's stored `PLANNED` status; a visit is planned against a prospect (a won prospect is the School); attendees default to the
Manager; and the colleague list is the one the web already uses.

## Technical Context

**Language/Version**: TypeScript 6.x on React Native 0.86 with Expo SDK 57 (the app from specs 018 and 033). No backend change.

**Primary Dependencies**: the existing app stack (React Native Paper, `expo-image-picker` from spec 033). No new package.

**Storage**: none. Nothing is persisted on the phone and nothing new on the server. Lists and details are held in memory while a
screen is open; a picked photo is held only until it is uploaded or removed.

**Testing**: Jest and React Native Testing Library against the in-memory fake server, extended with the marketing activity,
prospect, colleague and task endpoints and fixtures that copy the real response shapes; request-shape contract tests; the
existing role-name scan and accessibility checks extended to the new screens. Manual: emulator pass and TalkBack
(quickstart.md).

**Target Platform**: Android 10 (API 29) and later, phones, as in spec 018.

**Project Type**: mobile app (client of the existing web service). No new project.

**Performance Goals**: Home shows today's visits and overdue tasks within 5 seconds of opening (SC-001); a visit completed with
an outcome and one photo in under 90 seconds, a task with a note and one photo in under 60 (SC-002); a month of visits and a page
of tasks load in under 2 seconds on a typical mobile connection.

**Constraints**: the server decides every rule, status and permission; the app computes none of them (not lateness, not
overdue days, not Missed); a step is shown as done only after a 2xx; no offline queue; location is the ordinary best-effort
header only, never a separate reading and never required; photos only, optional, jpg/png from the camera or gallery;
light and dark themes; TalkBack-usable; 48 dp targets.

**Scale/Scope**: under 100 users (Constitution). Two menu entries, two list screens with an inner detail each, six forms or
dialogs (complete visit, plan visit, reschedule, cancel, task completion and overdue reason, add task), two Home cards.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design.*

| Principle | Assessment | Status |
| --------- | ---------- | ------ |
| I. Operational truth is auditable | Every plan, completion, move, cancel, attachment and task step is written and audited by the 023 and 025 services (Change History, task history). The app keeps no authoritative copy, shows nothing as done before a 2xx, and the calls also appear in the API Access trail of spec 018. | Pass |
| II. Five fixed roles, configurable permissions | No new role or permission key. Screens use `MARKETING` and `TASKS` as the server's menu grants them. Only Admin, Director and System can edit the matrix, unchanged. | Pass |
| III. Data scope | Scope is the server's: a Manager's own tasks and visits in their Zones; a visit or prospect outside them answers 404. The app adds no scope logic and takes no Manager id. Tests use two Managers. | Pass |
| IV. Role-based experience, one framework | Menu entries come only from the server's access model through the screen registry (`/marketing/calendar`, `/operations/tasks`); no role names; task buttons follow the `actions` object, visit buttons the menu's grants; light and dark; 48 dp targets; loading, empty and error states. | Pass |
| V, VI, VIII, XI | Not touched. | N/A |
| VII. Modular monolith | No backend change, so no module boundary is touched. | Pass |
| IX. Reliability and testability | Tests for every refusal of 023 and 025, for a failed or offline action never shown as done, for a failed photo never undoing its action, for two Managers, for menu visibility per role and per permission, and an emulator walkthrough. | Pass |
| X. Security, identity, observability | The backend stays the control. A photo is held in memory only until upload; downloaded attachments are `no-store` and held in memory only; no new permission (the camera is already declared by spec 033); the manifest check still applies. | Pass |

No violations, so Complexity Tracking is empty. Re-check after design: unchanged.

## Project Structure

### Documentation (this feature)

```text
specs/034-mobile-visits-tasks/
├── plan.md              # This file
├── research.md          # Phase 0: decisions
├── data-model.md        # Phase 1: the shapes the app reads and sends
├── quickstart.md        # Phase 1: how to run and prove it
├── contracts/
│   ├── server-calls.md  # the 023 and 025 endpoints the app calls and how it uses each
│   └── screens.md       # the screens, dialogs and Home cards with their states
└── tasks.md             # Phase 2 (/speckit-tasks)
```

### Source Code (repository root)

```text
mobile/
├── src/
│   ├── access/
│   │   ├── screenRegistry.ts            # + visits, tasks
│   │   └── useMenuActions.ts            # NEW: the granted actions of one menu route (useManagerActions delegates to it)
│   ├── api/
│   │   ├── marketingApi.ts              # NEW: activities, prospects, colleagues, attachments
│   │   └── tasksApi.ts                  # NEW: list, summary, detail, steps, evidence, create
│   ├── attendance/serverClock.ts        # + businessToday()
│   ├── media/                           # NEW: pieces shared by visits and tasks
│   │   ├── PhotoSlots.tsx               # pick photos for upload, "n of limit"
│   │   ├── AttachmentList.tsx           # names, sizes, image thumbnails from downloaded bytes
│   │   └── uploadPhotos.ts              # uploads photos after an action; failures reported, never undoing the action
│   ├── visits/                          # NEW
│   │   ├── useVisits.ts                 # one month, grouped today / later / earlier, status filter
│   │   ├── VisitCard.tsx, VisitDetail.tsx
│   │   ├── CompleteVisitForm.tsx        # outcome, notes, follow-up date, photos
│   │   ├── PlanVisitForm.tsx            # prospect search, kind, date, colleagues, notes
│   │   ├── RescheduleDialog.tsx, CancelVisitDialog.tsx
│   │   └── visitMessages.ts             # words for statuses and kinds
│   ├── tasks/                           # NEW
│   │   ├── useTasks.ts                  # one view, paged
│   │   ├── TaskCard.tsx, TaskDetail.tsx
│   │   ├── TaskSteps.tsx                # acknowledge / start / overdue reason / complete, from `actions`
│   │   ├── EvidenceSection.tsx          # n of 5, add photo
│   │   ├── AddTaskForm.tsx
│   │   └── taskMessages.ts
│   ├── home/VisitsTasksCards.tsx        # NEW: the two Home cards
│   └── screens/
│       ├── VisitsScreen.tsx, TasksScreen.tsx   # NEW (list with an inner detail, back closes the detail)
│       └── HomeScreen.tsx                      # + the two cards when the menu offers the screens
└── __tests__/
    ├── support/                          # + marketingServer.ts, tasksServer.ts, visitTaskFixtures.ts, visitTaskApp.tsx
    └── visitsTasks/                      # NEW tests per story
```

**Structure Decision**: all work is in `mobile/`, following specs 019 to 033 (an `api/` file per area, a feature folder per
screen family, screens in `screens/`). Files shared by visits and tasks go in `media/`; the only shared files touched are the
screen registry, the server clock, the Home screen and the manager-actions hook (generalised, behaviour unchanged). No
backend, web or database change.

## Complexity Tracking

Empty: the Constitution Check has no violations.
