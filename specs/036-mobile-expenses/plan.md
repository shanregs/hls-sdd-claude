# Implementation Plan: Mobile Expenses

**Branch**: `036-mobile-expenses` | **Date**: 2026-10-24 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/036-mobile-expenses/spec.md`

## Summary

Add the claimant's side of spec 015 to the Android app: one **My Expenses** screen (the server's `/operations/my-expenses` menu
entry, which 015 already seeds for Admin, Director, Manager and Teacher) with the status-filtered list and owed total, a claim
detail with bills and history, a claim form with bill photos (camera, gallery, pdf), and the server-driven buttons (edit, delete,
withdraw, correct and resubmit, add bill). The work is **app-only**: it calls the `/api/v1/my-expenses` endpoints of 015 unchanged
and adds no table, migration, permission key or server rule. Approve, reject, review, budgets and payment stay on the web.

The new part is **offline capture** (the first in the app). Every send goes through a **local outbox** kept in app-private encrypted
storage: the claim and its photos are written first, then a sync engine sends them in order as create, bills, submit, reusing one
`clientRequestId` per claim so a retry never makes a second claim, and resuming from the last step the server confirmed. A
refused item stays with the server's reason; an item belongs to the user who captured it and is hidden from, and never sent for,
anyone else. Photos are normalised to jpg at about 2000 px on the long edge, whatever the phone produced (HEIC, WebP, PNG).
See [research.md](research.md) for the decisions and the gaps found in 015.

## Technical Context

**Language/Version**: TypeScript 6.x on React Native 0.86 with Expo SDK 57 (the app from specs 018 to 034). No backend change.

**Primary Dependencies**: existing app stack (React Native Paper, `expo-image-picker`, `expo-secure-store`). New, all installed with
`npx expo install` so the SDK 57 versions are chosen: `expo-image-manipulator` (resize and convert to jpg), `expo-file-system`
(app-private files), `expo-crypto` (random key and ids), `expo-document-picker` (pdf bills), `@react-native-community/netinfo`
(reconnect trigger only); plus `@noble/ciphers` (AES-256-GCM, pure JS). No new Android permission.

**Storage**: on the phone only. Outbox items and photos as encrypted files under the app's private directory; the AES key in
Android Keystore-backed SecureStore; an encrypted copy of the last loaded list per user. Nothing new on the server.

**Testing**: Jest and React Native Testing Library against the in-memory fake server, extended with the `/my-expenses` routes
(copying the real response shapes and the 60-day, bill, duplicate and `clientRequestId` rules); the file, crypto, image and NetInfo
native modules replaced by fakes in `jest.setup.ts`; request-shape tests; outbox tests (kill and resume at each step, lost reply,
refusal, duplicate, two users on one phone); the role-name scan and accessibility checks extended. Manual: emulator pass with
airplane mode, and a real phone for HEIC (quickstart.md).

**Target Platform**: Android 10 (API 29) and later, phones (HEIC and WebP decoding is built into Android from API 28).

**Project Type**: mobile app (client of the existing web service). No new project.

**Performance Goals**: a claim with one bill raised in under 90 seconds (SC-001); a photo prepared (converted, resized, saved
encrypted) in under 3 seconds on a mid-range phone; a waiting claim is sent within 30 seconds of the connection returning while the
app is open.

**Constraints**: the server decides every rule; the phone shows early hints only; a step is shown as done only after a 2xx; nothing
is silently dropped; sending happens while the app is in the foreground (no background task, because the access token lives only
in memory); bills at most 10 MB, 5 per claim, jpg/png/pdf; light and dark themes; TalkBack-usable; 48 dp targets.

**Scale/Scope**: under 100 users (Constitution). One menu entry, one screen with an inner detail and form, six small dialogs
(withdraw, delete, duplicate, refusal, sign-out warning, bill source), a sync banner, and the outbox module.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design.*

| Principle | Assessment | Status |
| --------- | ---------- | ------ |
| I. Operational truth is auditable | Claims, bills, submissions and withdrawals are audited by 015 (`EXPENSE_CLAIM`, claim history) with the actor; the phone keeps no authoritative copy and a claim shows as sent only after a 2xx. Calls also appear in the API Access trail of spec 018. Retried sends are de-duplicated by `clientRequestId`, so history shows one creation. | Pass |
| II. Five fixed roles, configurable permissions | No new role or permission key; uses `MY_EXPENSES` as the menu grants it. Buttons follow the server's `allowedActions`; entry follows the menu. No role names in the app. | Pass |
| III. Data scope | Scope is the server's: own claims only, anyone else's claim is 404. On the phone a second layer separates users: every outbox item and cached list carries the owner id and is shown or sent only to that user. | Pass |
| IV. Role-based experience, one framework | The entry comes from the access model through the screen registry (`/operations/my-expenses`); `/operations/expenses` (review) is not in the registry so it is never shown on the phone; the Teacher gets the entry from the server menu; one design system, light and dark, loading, empty, error and "no connection" states, DD/MM/YYYY and rupees. | Pass |
| V, VI, VIII, XI | Not touched (no calculation; the owed total is the server's). | N/A |
| VII. Modular monolith | No backend change; no module boundary touched. | Pass |
| IX. Reliability and testability | Tests for each outbox step and failure, lost replies, refusals, duplicates, conversion, limits, two users, menu per role, System sees nothing; emulator walkthrough. The Constitution's "mobile expense capture works with temporary offline storage and sync later" is delivered here. | Pass |
| X. Security, identity, observability | The backend stays the control. Waiting claims and photos are encrypted at rest with a Keystore-held key, excluded from backup (`allowBackup: false`) and wiped on success or delete. Bills downloaded for viewing are held in memory only (the server sends `no-store`). The 015 error format is shown as it is. | Pass |

No violations, so Complexity Tracking is empty. Re-check after design: unchanged.

## Project Structure

### Documentation (this feature)

```text
specs/036-mobile-expenses/
├── plan.md              # This file
├── research.md          # Phase 0: decisions, rationale, alternatives, gaps found in 015
├── data-model.md        # Phase 1: the on-phone outbox entities and states, the shapes read and sent
├── quickstart.md        # Phase 1: how to run and prove it
├── contracts/
│   ├── server-calls.md  # the 015 endpoints and fields each screen and the sync engine use
│   └── screens.md       # the screens, dialogs and states
└── tasks.md             # Phase 2 (/speckit-tasks)
```

### Source Code (repository root)

```text
mobile/
├── app.config.ts                         # camera permission text made general; plugins for the new Expo packages
├── src/
│   ├── access/screenRegistry.ts          # + myExpenses ("/operations/my-expenses")
│   ├── api/expensesApi.ts                # NEW: list, detail, create, update, delete, submit, withdraw, bills, link options, areas
│   ├── auth/useSignOutGuard.ts           # NEW: counts the user's unsent claims and asks before logout (AppShell and Profile use it)
│   ├── expenses/                         # NEW
│   │   ├── categories.ts                 # six category keys and labels; which need a reason, a batch or may link (research §9)
│   │   ├── useMyExpenses.ts              # list: status filter, paging, owed, offline cache fallback
│   │   ├── ClaimCard.tsx, ClaimDetail.tsx, BillThumb.tsx
│   │   ├── ClaimForm.tsx, LinkField.tsx, AreaField.tsx
│   │   ├── BillSlots.tsx                 # camera / gallery / pdf, preview, retake, remove, "n of 5"
│   │   ├── DuplicateDialog.tsx, WithdrawDialog.tsx
│   │   └── expenseMessages.ts            # words for statuses and outbox states, the app's own few texts
│   ├── bills/                            # NEW: image and file preparation
│   │   ├── prepareBill.ts                # any image -> jpg, long edge ~2000 px, under 10 MB; pdf checked and passed through
│   │   └── pickBill.ts                   # camera, gallery, document picker
│   ├── outbox/                           # NEW: offline capture and sync (kept generic for later specs)
│   │   ├── cipher.ts                     # AES-256-GCM with the Keystore-held key
│   │   ├── outboxStore.ts                # encrypted item and photo files, per owner: list, save, update, remove
│   │   ├── syncSteps.ts                  # ensureClaim, uploadBills, submit: each idempotent and resumable
│   │   ├── outboxEngine.ts               # single-flight FIFO runner, outcome classification, backoff
│   │   ├── connectivity.ts               # NetInfo + AppState + timer triggers
│   │   ├── ExpenseOutboxProvider.tsx     # mounts the engine for the signed-in user; exposes items, counts, run-now
│   │   └── listCache.ts                  # encrypted last-loaded list per user
│   └── screens/MyExpensesScreen.tsx      # NEW: list with inner detail and form (back closes the inner pane)
└── __tests__/
    ├── support/                          # + expensesServer.ts, expenseFixtures.ts, expenseApp.tsx; native fakes in jest.setup.ts
    └── expenses/                         # NEW tests per story and for the outbox
```

**Structure Decision**: all work is in `mobile/`, following specs 019 to 034 (an `api/` file per area, a feature folder per screen
family, screens in `screens/`). The offline machinery has its own folder `outbox/` because later specs are expected to reuse it;
only `syncSteps.ts` knows about expenses. Shared files touched: the screen registry, `AppShell` and `ProfileScreen` (logout goes
through the sign-out guard), the signed-in provider tree (mounts the outbox provider), `jest.setup.ts`, `app.config.ts`. No
backend, web or database change.

## Complexity Tracking

Empty: the Constitution Check has no violations.
