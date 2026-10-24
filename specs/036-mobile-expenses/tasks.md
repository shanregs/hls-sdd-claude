---

description: "Task list for feature implementation"
---

# Tasks: Mobile Expenses

**Input**: Design documents from `/specs/036-mobile-expenses/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/server-calls.md, contracts/screens.md, quickstart.md, and
**specs 015, 018, 019, 020, 033 and 034 implemented** (this feature adds one screen to the app shell of 018, reuses the date, photo,
upload, action and inner-back pieces of 019, 020, 033 and 034, and calls the My Expenses endpoints of 015 unchanged; there is no
backend work).

**Tests**: included as first-class tasks, written before the code they cover. Constitution Principle IX requires menu-visibility tests
per role, scope tests, and tests that every refusal leaves things unchanged and that nothing is shown as done before the server
confirms it (SC-002, SC-005, SC-006, SC-007).

**Organization**: grouped by user story in priority order (spec.md US1-US4). The shared plumbing (packages, API client, storage,
photo preparation, the outbox core, fakes, route) comes first because every story uses it.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: US1-US4; absent for Setup/Foundational/Polish

## Path Conventions

`MOB` = `mobile/src`, `MOBT` = `mobile/__tests__`. All work is under `mobile/` (plus `docs/` in Polish); `backend/` and `frontend/` are
not changed. Run commands from the `mobile` folder with JDK 17 (see `docs/running-mobile.md`). Server shapes are in `data-model.md` and
`contracts/server-calls.md`. In tests, `fireEvent` is asynchronous: always `await` it, and do not use `waitFor` in tests that mock the
clock; use `findBy*` or an `act` with a short wait.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: packages, folders, native fakes and realistic test data.

- [X] T001 From `mobile/` run `npx expo install expo-image-manipulator expo-file-system expo-crypto expo-document-picker @react-native-community/netinfo` and `npm i @noble/ciphers`; create the folders `MOB/expenses/`, `MOB/bills/`, `MOB/outbox/` and `MOBT/expenses/`; confirm `npm test`, `npm run lint`, `npm run typecheck` and `npm run check:manifest` pass before any other change; in `app.config.ts` make the camera permission text general ("to photograph a bill or a check-in photo") and check that `check:manifest` still shows no new permission
- [X] T002 [P] Add in-memory fakes to `mobile/jest.setup.ts` for `expo-file-system` (a map of paths to bytes: write, read, delete, list a folder, make a folder, free-space switch that makes a write fail), `expo-crypto` (deterministic `randomUUID` and `getRandomBytes`), `expo-image-manipulator` (records the requested resize and format, returns a small jpg and its size; a switch makes it fail), `expo-document-picker`, and `@react-native-community/netinfo` (a listener the test can fire online or offline); extend the `expo-image-picker` fake with a HEIC and a WebP asset
- [X] T003 [P] Create `MOBT/support/expenseFixtures.ts` with builders that copy the real shapes of data-model.md: `claimRow({status, category, amount, expenseDate, billCount, allowedActions, ...})`, `claimDetail({bills, history, decision, payment, link, version, allowedActions})`, `bill({id, name, contentType, sizeBytes, removed})`, `linkOption()`, `area()`, `myList({content, owed})` and `expenseModel({create, edit, delete})` for the access model of a user with OPERATIONS → My Expenses (`/operations/my-expenses`, module `MY_EXPENSES`); plus builders for a Manager, a Teacher, a Director and a System user's models
- [X] T004 [P] Create `MOBT/support/expensesServer.ts` that registers on the existing `FakeServer` the routes of contracts/server-calls.md: `GET /api/v1/my-expenses` (status, month, page, `owed`), `GET .../areas`, `GET .../link-options`, `POST .../my-expenses` (replays a repeat `clientRequestId` with 200, 409 "already used for a different claim" if the body differs, 201 when new), `GET .../{id}` (404 for another user's claim), `PUT .../{id}` (checks `version`, 409 "changed by someone else"), `DELETE`, `POST .../{id}/submit` (60-day and future date rule, Other needs a reason, Training Stay needs a batch, bill or no-bill reason, duplicate 409 with `matches` unless `confirmDuplicate`, 409 when not Draft or Rejected), `POST .../{id}/withdraw`, `POST .../{id}/bills` (extension jpg/jpeg/png/pdf, 10 MB, 5 live, only Draft or Rejected) and `GET .../{id}/bills/{billId}`; it computes `allowedActions` as 015 does, records every call, and has switches to drop the reply after the server acted (lost reply), to refuse any step, and to move its date
- [X] T005 Create `MOBT/support/expenseApp.tsx` with `openExpenseScreen(setup)`: like `openVisitTaskScreen` in `MOBT/support/visitTaskApp.tsx`, it signs in with a stored session as a chosen user (id kept so the outbox owner is known), installs the expenses routes with the setup's overrides, uses `expenseModel()` unless a model is given, opens My Expenses from the drawer, and can sign out and sign in as another user; the server's date is 2026-10-23 unless set

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: the API client, categories, bill preparation, encrypted storage and the outbox core that every story uses.

**⚠️ CRITICAL**: no user story work can begin until this phase is complete.

- [X] T006 Create `MOBT/expenses/expensesContract.test.ts` first (request shapes against the fake server): list `status` values and `month`, create body fields and that `submit` is always `false` and `amount` is a two-decimal string, `clientRequestId` only on create, `version` on update, submit body `{version, confirmDuplicate}`, withdraw body, multipart field `file`, and that a refusal reaches the caller as an `ApiError` carrying the server's `reason` and a duplicate 409 carries `matches`
- [X] T007 Create `MOB/api/expensesApi.ts` with the types of data-model.md (`ClaimRow`, `ClaimDetail`, `ClaimStatus`, `AllowedAction`, `BillRef`, `LinkOption`, `Area`, `ClaimBody`, `MyList`) and typed calls: `listMyExpenses({status, month, page, size})`, `getClaim(id)`, `createClaim(body)`, `updateClaim(id, body)`, `deleteClaim(id)`, `submitClaim(id, {version, confirmDuplicate})`, `withdrawClaim(id, version)`, `addBill(id, file)` (uses `upload`), `openBill(id, billId)` (uses `fetchBinary`), `listAreas()` and `listLinkOptions(category, date)`; a duplicate 409 is surfaced with its `matches` list (extend `MOB/api/errors.ts` or `httpClient.ts` minimally so `matches` is kept on the error); the app computes no status or action
- [X] T008 [P] Create `MOB/expenses/categories.ts` (the six keys and labels with flags `reasonRequired`, `batchRequired`, `linkOptional`, research §9 and gap G1) and `MOB/expenses/expenseMessages.ts` (status and outbox-state words, the app's own few texts of contracts/screens.md "Words"), with `MOBT/expenses/categories.test.ts` (labels match the server's, flags per category)
- [X] T009 Write `MOBT/expenses/prepareBill.test.ts` first, then create `MOB/bills/prepareBill.ts`: any image (jpeg, png, HEIC, WebP) is re-encoded to jpg with the long edge at most 2000 px (never enlarged) at compress 0.8; over 10 MB gets one more pass at 1600 px and compress 0.6 and still over is refused with a plain message; the output name is generated, unique and ends `.jpg`; a pdf is checked for 10 MB and passed through unchanged with a `.pdf` name; an unreadable file returns "This photo could not be read. Try another or retake it."; tests use the HEIC and WebP fake assets and assert the requested size, format and names
- [X] T010 [P] Create `MOB/bills/pickBill.ts`: `takeBillPhoto()` (camera permission asked only when chosen, refusal returns the explanation of contracts/screens.md and leaves the gallery available), `chooseBillPhoto()` (system gallery picker, `quality: 1`) and `chooseBillPdf()` (`expo-document-picker`, `application/pdf`, copy to cache); each result goes through `prepareBill`; add `MOBT/expenses/pickBill.test.ts` (camera refused, cancel returns nothing, pdf over 10 MB refused)
- [X] T011 Write `MOBT/outbox/cipher.test.ts` first, then create `MOB/outbox/cipher.ts`: AES-256-GCM with `@noble/ciphers`, a 12-byte random nonce prepended, the 256-bit key made once and kept in SecureStore as `hls.outboxKey` (research §5); tests: round trip, a tampered file fails to open, two encryptions of the same bytes differ, a missing key at first use creates one, an unreadable key reports "cannot be opened" instead of creating a new one over existing items
- [X] T012 Write `MOBT/outbox/outboxStore.test.ts` first, then create `MOB/outbox/outboxStore.ts` and its types from data-model.md A (`OutboxItem`, `OutboxBill`, states, stages): `saveItem`, `updateItem`, `listItems(ownerUserId)`, `removeItem`, `readBill(itemId, billId)` and `withBillFile(itemId, billId, fn)` (decrypts to a temporary cache file for an upload and deletes it afterwards, because the upload needs a file address), all under `outbox/<owner>/<itemId>/`; a failed write (storage full) throws a typed `StorageFullError` and leaves no half-written item; tests: items of two owners never appear for each other, a restart turns `SENDING` into `WAITING`, plain bytes are never present in the fake file system, removal deletes every file
- [X] T013 Write `MOBT/outbox/syncSteps.test.ts` first, then create `MOB/outbox/syncSteps.ts` with the idempotent resumable steps `ensureClaim` (create with the item's `clientRequestId`, or `PUT` with a freshly read `version` for an item that already has a `serverClaimId`), `uploadBills` (before each file read the claim and skip a file whose generated name is already listed; save `uploaded` and `serverBillId` after each 2xx) and `submit` (a 409 "Only a Draft or Rejected claim" is resolved by reading the claim: `SUBMITTED` means done); each step saves its progress to the item before returning; tests use the fake server's lost-reply switch after create, after one bill and after submit and assert one claim, no duplicate bill and the right stage afterwards; `SAVE_DRAFT` stops after the bills
- [X] T014 Write `MOBT/outbox/outboxEngine.test.ts` first, then create `MOB/outbox/outboxEngine.ts`: a single-flight runner over the current user's items in `createdAt` order that applies the outcome table of research §7 (2xx advance and delete at the end; no connection, timeout, 5xx and 429 stay `WAITING` with backoff 30 s, 60 s, 5 min; 401 after renewal failed stops the run and leaves items; duplicate 409 with `matches` becomes `NEEDS_CONFIRMATION`; stale version becomes `REFUSED` with the server's reason; other 400/403/404/409 become `REFUSED` with the reason); a refused item never blocks the next; nothing is shown as sent before the last 2xx; tests for each row of the table and for two items in order
- [X] T015 [P] Create `MOB/outbox/connectivity.ts` and `MOBT/outbox/connectivity.test.ts`: triggers for the engine from the provider mounting, `AppState` returning to active, NetInfo reporting a connection (a hint only), a new item, "Send now" and a 60 s timer while items wait; triggers are coalesced and the timer stops when nothing waits
- [X] T016 Create `MOB/outbox/ExpenseOutboxProvider.tsx` and `MOB/outbox/listCache.ts`: the provider mounts inside the signed-in tree for the current user id (from the auth state), loads that user's items, runs the engine on the triggers, and exposes `{items, waitingCount, state, saveItem, updateItem, removeItem, sendNow, confirmDuplicate}`; `listCache` keeps the encrypted last list, areas and link options per user; mount the provider in the signed-in provider tree of `MOB/navigation/AppShell.tsx` (or `App.tsx`); add `MOBT/outbox/provider.test.tsx` (items load for the right user only, a new user sees none, the engine runs on mount) and `MOBT/outbox/listCache.test.ts`
- [X] T017 Register the screen: in `MOB/access/screenRegistry.ts` add the key `myExpenses` mapped from `/operations/my-expenses` and an `EXPENSES_ROUTE` constant; in `MOB/navigation/AppShell.tsx` render `MyExpensesScreen` (placeholder in `MOB/screens/MyExpensesScreen.tsx` until the story tasks) with title "My Expenses"; add `MOBT/expenses/routes.test.ts` (the route maps; `/operations/expenses` still shows nothing, even when the server's menu offers it to a Director or Admin; the entry appears only when the model has it)

**Checkpoint**: the API, bill preparation, encrypted store and outbox core work against the fake server; no screen yet.

---

## Phase 3: User Story 1 - Raise an Expense with a Bill Photo (Priority: P1) 🎯 MVP

**Goal**: add a claim with its bill from the phone, with signal, and see it Submitted.

**Independent Test**: as a Manager, raise a Travel and Fuel claim for ₹850 dated yesterday, photograph a bill, submit; it is Submitted in the list and in the fake server with one bill.

### Tests for User Story 1

- [X] T018 [P] [US1] `MOBT/expenses/raiseClaim.test.tsx`: fill the form and Submit online (write-ahead item, then create, one bill, submit; the form closes only after the last 2xx and the claim is listed Submitted); Save as draft leaves a server Draft; the request bodies match the contract; double tap sends once; after success no item or file remains
- [X] T019 [P] [US1] `MOBT/expenses/categoryFields.test.tsx`: Other shows Reason and Submit stays disabled until filled; Training Stay requires a batch and shows the server's batches; Travel and Fuel offers the claimant's planned work for the chosen date, optional; changing category or date reloads the choices and clears an invalid one; a server refusal such as `reason required` is shown with every entry kept
- [X] T020 [P] [US1] `MOBT/expenses/billSlots.test.tsx`: preview, retake and remove; a HEIC and a WebP asset are accepted and the uploaded part is `.jpg`, at most 2000 px and 10 MB (SC-003, SC-004); a pdf passes unchanged; a sixth bill is refused with the reason; camera refused offers the gallery and the "no bill" reason; an unreadable photo says so and leaves the claim unchanged; the no-bill reason lets a claim submit without a bill
- [X] T021 [P] [US1] `MOBT/expenses/duplicateWarning.test.tsx`: the server's duplicate 409 opens the dialog with the matching claims; Submit anyway resends with `confirmDuplicate: true`; Cancel keeps the entries and leaves the claim a Draft

### Implementation for User Story 1

- [X] T022 [P] [US1] Create `MOB/expenses/BillSlots.tsx` (Take photo, Choose photo, Choose PDF; preview with Retake and Remove; "Preparing..." while `prepareBill` runs; "n of 5"; the no-bill reason field when there is no bill)
- [X] T023 [P] [US1] Create `MOB/expenses/LinkField.tsx` and `MOB/expenses/AreaField.tsx` (areas from `listAreas` with Head office; link options from `listLinkOptions`, cached through `listCache`; offline rules of research §9: a Training Stay batch not available offline marks the item "choose the batch when connected" and blocks sending until chosen)
- [X] T024 [P] [US1] Create `MOB/expenses/DuplicateDialog.tsx`
- [X] T025 [US1] Create `MOB/expenses/ClaimForm.tsx` (fields, category fields, hints from `businessToday()` for the 60-day range that never block, Save as draft and Submit; both write an outbox item first and then wait for that item's result when online, or close with "Saved. It will be sent when you are back online." when offline; storage full shows "Not saved. Free some space and try again." and keeps the form; a server refusal shows in place with entries kept) using `useAction`, `DateChooser` and the provider
- [X] T026 [US1] Fill `MOB/screens/MyExpensesScreen.tsx` with the list's Add expense button (menu `canCreate`) opening the form as an inner pane (back closes it via `useInnerBack`); make T018 to T021 pass

**Checkpoint**: a claim with a bill can be raised with signal.

---

## Phase 4: User Story 2 - Capture Offline and Send Later (Priority: P1)

**Goal**: with no signal the claim and photo are saved on the phone and sent once when the connection returns; refusals stay visible; one user never sees another's waiting claims.

**Independent Test**: in airplane mode capture a claim with a bill; restore signal and see it sent once and shown as Submitted, with no second copy.

### Tests for User Story 2

- [X] T027 [P] [US2] `MOBT/expenses/offlineCapture.test.tsx`: offline Submit saves "Waiting to send" and survives an app restart; reconnection (NetInfo event) sends it in capture order and clears the mark only on the 2xx; Training Stay without a cached batch is saved but not sent
- [X] T028 [P] [US2] `MOBT/expenses/syncRecovery.test.tsx`: kill and resume after create, after a bill and before submit; a lost reply at each step gives exactly one claim and no duplicate bill (SC-002); an item refused at submit (date now over 60 days) keeps its `serverClaimId`, and correcting the date sends a `PUT` and submit, never a second create
- [X] T029 [P] [US2] `MOBT/expenses/refusedDraft.test.tsx`: a refused item stays with the server's reason, can be edited and sent again or deleted (files gone); a stale version shows the reason and the server's current values and the claimant re-applies by hand; a duplicate warning on sync shows Confirm / Keep as Draft; none is dropped silently (SC-005)
- [X] T030 [P] [US2] `MOBT/expenses/sharedPhone.test.tsx`: Log out with unsent items shows the warning with the count (from the menu and from Profile); after sign-out nothing is sent; another user sees and sends none of them; the same user signing in again gets them back and they send; an expired session behaves the same (SC-006); no plain claim or photo bytes exist in the fake file system at any point

### Implementation for User Story 2

- [X] T031 [P] [US2] Create `MOB/auth/useSignOutGuard.ts` (counts the signed-in user's unsent items, shows the warning dialog, then calls `logout` without deleting anything) and use it in `MOB/navigation/AppShell.tsx` and `MOB/screens/ProfileScreen.tsx` instead of calling `logout` directly
- [X] T032 [P] [US2] Create the waiting section and banner in the list pane: cards with the state words, "Not sent" with the server's reason, the count, "Sending..." / "Waiting for a connection" with Send now; tap opens the item in `ClaimForm` (edit resets the refusal and sets `WAITING`), with Delete and a "Current claim on the server" comparison for a stale refusal; wire the duplicate Confirm / Keep as Draft
- [X] T033 [US2] Make T027 to T030 pass; fix what they find in the engine, steps or provider

**Checkpoint**: offline capture, recovery, refusals and shared-phone behaviour work.

---

## Phase 5: User Story 3 - My Expenses: Track My Claims (Priority: P1)

**Goal**: see my own claims by status with what is owed, open one, and act on it as the server allows.

**Independent Test**: with one claim in each status, check each filter, the owed total, and that no one else's claim shows.

### Tests for User Story 3

- [X] T034 [P] [US3] `MOBT/expenses/myList.test.tsx`: own claims newest first, each status filter, the server's `owed`, paging with Load more, an empty state, a load error with Try again, the month filter; another user's claim never appears
- [X] T035 [P] [US3] `MOBT/expenses/claimDetail.test.tsx`: bills (thumbnail from downloaded bytes, pdf by name, removed bills struck through), link, history, decision (who, when, why) for Rejected, payment details for Paid; buttons only for the `allowedActions` the server returns (Edit, Submit, Withdraw, Delete, Add bill, Correct and resubmit for Rejected); no review action is ever shown
- [X] T036 [P] [US3] `MOBT/expenses/claimActions.test.tsx`: Withdraw (confirm, becomes Draft), Delete a Draft, Correct and resubmit a Rejected claim as the same claim (update, bills, submit through the outbox), Add bill; each shown as done only after the 2xx; a refusal leaves the claim unchanged; withdraw and delete offline explain and do nothing
- [X] T037 [P] [US3] `MOBT/expenses/offlineList.test.tsx`: after a failed load the cached list is shown "may be out of date, loaded <time>", filtered locally, with the waiting items above; a claim opened offline shows its row and "More details need a connection"; the cache is per user

### Implementation for User Story 3

- [X] T038 [P] [US3] Create `MOB/expenses/useMyExpenses.ts` (filter, month, paging, `owed`, cache save on success, cache fallback on failure) and `MOB/expenses/ClaimCard.tsx`
- [X] T039 [P] [US3] Create `MOB/expenses/ClaimDetail.tsx` and `MOB/expenses/BillThumb.tsx` (reuse `AttachmentList`/`fetchBinary`; bytes in memory only; full-screen view) and `MOB/expenses/WithdrawDialog.tsx` (also used for Delete confirmation)
- [X] T040 [US3] Complete `MyExpensesScreen.tsx`: the list header (owed, status filter, month, Add expense), the waiting section from T032, claims, the inner detail pane and its buttons from `allowedActions`; Edit and Correct and resubmit open `ClaimForm` prefilled and send through the outbox; make T034 to T037 pass

**Checkpoint**: claimants can track and correct their claims.

---

## Phase 6: User Story 4 - Teacher Menu and Role Fit (Priority: P1)

**Goal**: the entry appears exactly where the server's menu offers it; System sees nothing; no review on the phone.

**Independent Test**: a Teacher sees only My Expenses; System sees none; removing the permission removes the entry.

### Tests for User Story 4

- [X] T041 [P] [US4] `MOBT/expenses/menuVisibility.test.tsx`: Teacher, Manager and Director models show My Expenses and never Expenses (review); a System model shows no expense entry and direct access shows "not authorized"; removing `MY_EXPENSES` from the model after a refresh removes the entry; a role-name scan finds no role names in the new files
- [X] T042 [P] [US4] `MOBT/expenses/neverFalseSuccess.test.tsx`: no step (create, bill, submit, withdraw, delete, update) is shown as done before its 2xx; a failed or offline step leaves the claim, the outbox item and the list as they were; a failed bill never shows the claim as submitted
- [X] T043 [P] [US4] `MOBT/a11y/expenses.a11y.test.tsx`: roles, labels and 48 dp targets for the list, detail, form, dialogs and banner; light and dark themes render; the `check:manifest` script still passes

### Implementation for User Story 4

- [X] T044 [US4] Fix whatever T041 to T043 find (labels, targets, states, registry); confirm no code path reads a role name and no screen is chosen by role

**Checkpoint**: menus, permissions and safety come from the server.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [X] T045 [P] Update `docs/running-mobile.md` (the new packages need a native rebuild, how to test offline with airplane mode, HEIC on a real phone) and the roadmap row for spec 036
- [X] T046 [P] Add the 015 gaps G1 to G4 (research.md) as a short note for a later 015 amendment, without changing 015; confirm with the owner how Admin-only accounts (web-only per spec 018) are meant to read the spec 036 role table
- [X] T047 Run the full `npm run typecheck`, `npm run lint`, `npm test` and `npm run check:manifest` from `mobile/` and fix everything they report; run a search that no file in `MOB` imports a review endpoint (`/api/v1/expenses`)
- [ ] T048 Run the quickstart.md walkthrough (menu per role, raise online, category fields, HEIC and WebP, offline capture and sync with a kill during upload, refusal on sync, duplicate, server wins, shared phone, My Expenses tracking, TalkBack and themes) on an emulator and one real phone, and record the results in `specs/036-mobile-expenses/quickstart-results.md`

---

## Dependencies & Execution Order

- **Setup** (T001 to T005) first; T002 to T004 in parallel.
- **Foundational** (T006 to T017) blocks every story. T006 before T007; T008 and T010 in parallel; T009, T011 to T014 are each test then code and run in the order cipher, store, steps, engine; T015 in parallel with T014; T016 after T012 to T015; T017 after T016.
- **US1** needs Foundational. **US2** needs US1's form (T025). **US3** needs US1's screen shell (T026) and the waiting section (T032). **US4** needs the screen to exist (T026) and is best done last.
- **Polish** last, with the manual walkthrough T048 as the final task.
- Within a story: tests first (they may be written to fail), then the pieces marked [P], then the screen.

### Parallel opportunities

- Phase 1: T002, T003 and T004 together.
- Phase 2: T008 and T010 together once T007 exists; T015 with T014.
- US1: T018 to T021 together; T022 to T024 together. US2: T027 to T030 together; T031 and T032 together. US3: T034 to T037 together; T038 and T039 together. US4: T041 to T043 together.

## Implementation Strategy

**MVP**: Phase 1, Phase 2 and User Story 1 (raise a claim with a bill with signal). Stop and run quickstart scenarios 1 to 4. Then add
US2 (offline capture, the main reason for the feature), US3 (tracking) and US4 (menu and safety checks). Because every send goes
through the outbox (research §3), the outbox core is built in Phase 2 and US2 adds the offline behaviour on top of it.
