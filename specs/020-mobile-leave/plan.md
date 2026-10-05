# Implementation Plan: Mobile Leave

**Branch**: `020-mobile-leave` | **Date**: 2026-10-05 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/020-mobile-leave/spec.md`

## Summary

Add the leave screens to the Android app: Teacher **Apply Leave** (with the server's preview) and
**My Leave History** (with cancel), Manager and Director **Leave Management** (list, detail, approve,
reject, revoke), and a **Pending count widget** on their Home. The work is **app-only**. It uses the leave
endpoints of spec 009 unchanged, reaches each screen through the server-provided menu (spec 018), reuses
the month, date, refusal and screen-state pieces of spec 019, and adds no data, no migration and no server
rule. See [research.md](research.md) for the decisions and for two places where the real server differs
from what the spec assumed (the reason is required; the preview lists only counted days).

## Technical Context

**Language/Version**: TypeScript 5.x on React Native with Expo (the app from specs 018 and 019). No backend change.

**Primary Dependencies**: the existing app stack (React Native Paper, the in-app shell of spec 018). No new
package: dates are chosen in a small in-app date chooser built from the month picker of spec 019, not a
native picker (research §5).

**Storage**: none. Nothing is persisted on the phone and nothing new on the server. A draft lives in memory
while the Apply Leave screen is open.

**Testing**: Jest and React Native Testing Library against the in-memory fake server
(`mobile/__tests__/support/fakeServer.ts`, already supporting path parameters) with leave fixtures that
copy the real response shapes; request-shape contract tests; Manager-scope tests with two Managers'
Teachers; the existing role-name scan and accessibility checks extended to the new screens. Manual: emulator
pass and TalkBack.

**Target Platform**: Android 10 (API 29) and later, phones, as in spec 018.

**Project Type**: mobile app (client of the existing web service). No new project.

**Performance Goals**: apply for leave including the preview in under 90 seconds (SC-001); decide a request
in under 30 seconds from opening Leave Management (SC-002); a list or detail loads in under 2 seconds on a
typical mobile connection; lists page 25 at a time.

**Constraints**: the server decides every rule, every preview and every allowed action; the app computes no
working days, limits or scope; a request or decision is shown as saved only after the server confirms it;
no offline queue; every call carries the location or its reason (spec 018); light and dark themes;
TalkBack-usable.

**Scale/Scope**: under 100 users (Constitution). Three menu entries, three screens (apply, history,
management) plus a request detail, one date chooser, one Home widget.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design.*

| Principle | Assessment | Status |
| --------- | ---------- | ------ |
| I. Operational truth is auditable | Every create, cancel, approve, reject and revoke is written and audited by the server's leave service (spec 009). The app keeps no authoritative copy and shows nothing as saved before a 2xx. Calls also appear in the API Access trail with the location or its reason (spec 018). | Pass |
| II. Five fixed roles, configurable permissions | No new role or permission key. Screens use `MY_LEAVE` and `LEAVE_MANAGEMENT`. Only Admin, Director and System can edit the matrix, unchanged. | Pass |
| III. Data scope | A Manager sees only the requests the server returns; a 404 shows no data. The app adds no scope logic. Teacher screens use the caller's own record only. Tests use two Managers. | Pass |
| IV. Role-based experience, one framework | Menu entries come only from the server's access model, filtered by the screen registry; no role names; what a button may do comes from `allowedActions`; light and dark; WCAG-oriented labels and 48 dp targets; loading, empty and error states on every screen. | Pass |
| V, VI, VIII, XI | Not touched (no payroll, training, batch or recruitment). | N/A |
| VII. Modular monolith | No backend change, so no module boundary is touched. | Pass |
| IX. Reliability and testability | Per-scope tests (two Managers), menu-visibility tests per role, tests for every refusal, and tests that a failed or offline submit, cancel or decision is never shown as done. | Pass |
| X. Security, identity, observability | The backend stays the control; no data cached on disk; location and client headers as in spec 018. | Pass |

Also checked against *Additional Constraints*: React Native Android-first; dates as DD/MM/YYYY and month
names; the "offline readiness" constraint is deliberately not met here (spec Out of scope).

**Post-design re-check**: no violations. The judgement calls (labelling non-counted days from the Holiday
Calendar, research §3; an in-app date chooser, research §5) change no rule and need no waiver.

## Project Structure

### Documentation (this feature)

```text
specs/020-mobile-leave/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── mobile-leave-screens.md
├── checklists/
│   └── requirements.md
└── tasks.md            # created by /speckit-tasks, not here
```

### Source Code (repository root)

```text
mobile/
├── src/
│   ├── access/screenRegistry.ts          # CHANGED: routes /leave/apply, /leave/history, /operations/leave
│   ├── navigation/AppShell.tsx           # CHANGED: render the three screens; open Leave Management from Home
│   ├── api/leaveApi.ts                   # NEW: typed calls and shapes for the endpoints in the contract
│   ├── leave/
│   │   ├── leaveMessages.ts              # NEW: plain wording for the server's leave refusals
│   │   ├── draft.ts                      # NEW: draft state, the three app-side checks, request body
│   │   ├── previewDays.ts                # NEW: lists the range's days, counted from the server, others labelled
│   │   ├── statusPresentation.ts         # NEW: label, colour and accessible text per request status
│   │   ├── LeaveRequestCard.tsx          # NEW: one request in a list (dates, days, status, decision)
│   │   ├── StatusFilter.tsx              # NEW: chips for the status filter
│   │   ├── DateChooser.tsx               # NEW: month picker plus day list/grid in a sheet
│   │   ├── PreviewPanel.tsx              # NEW: working days, day list, problems
│   │   ├── ReasonDialog.tsx              # NEW: confirmation with a required or optional text
│   │   ├── useLeaveList.ts               # NEW: paged list with filter, reload and error state
│   │   └── usePendingCount.ts            # NEW: the Home widget's count
│   ├── screens/
│   │   ├── ApplyLeaveScreen.tsx          # NEW
│   │   ├── MyLeaveHistoryScreen.tsx      # NEW (list, cancel, "Request submitted" message)
│   │   ├── LeaveManagementScreen.tsx     # NEW (status filter, list, opens the detail)
│   │   ├── LeaveRequestDetailScreen.tsx  # NEW (details, days it would mark, approve, reject, revoke)
│   │   └── HomeScreen.tsx                # CHANGED: Pending leave widget for users whose menu offers Leave Management
│   ├── attendance/monthRange.ts          # CHANGED: a "leave" range (previous, current and next year)
│   └── formats/dates.ts                  # unchanged
└── __tests__/
    ├── support/leaveFixtures.ts          # NEW: realistic requests, types, preview and detail data
    ├── support/leaveServer.ts            # NEW: leave routes on the FakeServer
    ├── leave/                            # NEW: screens, refusals, scope, offline, widget, a11y
    └── api/leaveContract.test.ts         # NEW: request shapes against the contract

docs/spec-roadmap.md                      # status of row 020 as work proceeds
docs/running-mobile.md                    # manual checklist item for leave
```

**Structure Decision**: all work is under `mobile/`. `backend/` and `frontend/` are unchanged. Lists, the
detail and the dialogs are small shared pieces so My Leave History and Leave Management differ only in the
data source, the filter options and the actions the server offers.

## Complexity Tracking

No Constitution Check violations to justify.

## Risks and notes for tasks

- **Reason is required by the server** (research §4): clarification Q1 was corrected on 2026-10-05; the
  server answers "Give a reason." on preview and submit, so the app requires it and keeps Submit disabled
  until it is entered.
- **Preview lists only counted days** (research §3): the spec's per-day "weekly off, holiday, before
  placement" labels come from the Holiday Calendar (spec 019 data) for weekly offs and holidays, and
  everything else not counted reads "Not counted".
- **Pending first**: the Manager list returns Pending only unless a status is chosen, so "Pending first"
  is the default filter, not a sort; there is no "All" on Leave Management (research §2).
- **Stale versions**: every decision sends the request's `version`; a 409 "changed by someone else" reloads
  the request and shows its new state.
- **Wrong phone clock**: nothing in these screens uses the phone's date; the date chooser's range comes from
  the server clock helper of spec 019 and the 30-day look-back is left to the server.
- **Unknown request**: a 404 from the detail (out of scope or deleted) shows "Not found" only.
- **Stale screens**: a screen open when permissions change shows "Not authorized" on the next menu refresh,
  as spec 018 already does.
