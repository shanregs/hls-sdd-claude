# Research: Mobile Expenses

Decisions for [plan.md](plan.md). Facts about the server were read from the merged 015 code and contract
(`specs/015-expenses/contracts/expenses-api.md`, `MyExpensesController`, `ClaimService`, `ClaimViews`, `AllowedActions`, `BillService`,
`LinkResolver`, `ExpensesExceptionAdvice`, `NavigationCatalog`, `MobileRoleEligibility`); about the app from `mobile/` after spec 034
(`httpClient`, `screenRegistry`, `useMenuActions`, `secureStore`, `AuthProvider`, `pickPhoto`, `uploadPhotos`, `serverClock`, `jest.setup.ts`).

## 1. Menu and screen: `/operations/my-expenses` only

**Decision**: add `"/operations/my-expenses": "myExpenses"` to the screen registry. The server's menu already offers it to Admin,
Director, Manager and Teacher (`NavigationCatalog`, item "My Expenses", module `MY_EXPENSES`), so 036 seeds nothing. The review entry
`/operations/expenses` is not in the registry, so the app never shows it (FR-021). Buttons on the screen follow the claim's
`allowedActions`; the Create button follows `useMenuActions(route).canCreate`.

**Rationale**: the same mechanism as 034; no role names.

**Finding (not a 015 gap)**: spec 018's `MobileRoleEligibility` makes **Admin web-only** on the phone (only Teacher, Manager and
Director may sign in). The role table in spec.md lists Admin on the phone; an Admin who is also Manager/Teacher/Director gets it, an
Admin-only account never signs in. No change here; the table should be read as "where the account can sign in".

## 2. A claim is sent in three steps, not one

**Decision**: sync runs **create (Draft) -> upload bills one by one -> submit**. `POST /my-expenses` with `submit: true` cannot be used
for a claim with a bill, because the "bill or no-bill reason required" rule is checked at submit time and bills can only be added to an
existing claim (`POST /my-expenses/{id}/bills`). Each step is idempotent and its success is saved in the outbox item (data-model.md), so
a kill or lost reply at any point resumes at the same step:

- create: same `clientRequestId` returns the first claim with 200 (201 when new). The server compares date, amount, category, area and
  note and answers 409 "already used for a different claim" if they differ, so an edit of an item that already created its claim
  switches to update (below), never to a new create.
- bills: before each upload the engine reads `GET /my-expenses/{id}` and skips a file whose generated unique name is already in `bills`
  (see gap G3).
- submit: a 409 "Only a Draft or Rejected claim can be submitted; this one is Submitted" after a lost reply is resolved by reading the
  claim: status `SUBMITTED` means the step was done.

**Alternatives considered**: create with `submit: true` and the bill added afterwards (rejected: submit is refused with no bill);
holding the whole claim and sending at the end as multipart (rejected: 015 has no such endpoint and may not change).

## 3. Everything goes through the outbox (write-ahead), online or not

**Decision**: pressing Save draft or Submit always writes an **outbox item** first, then asks the engine to run. Online, the form waits
for this item's result and shows a refusal inline (entries kept); offline, it shows "waiting to send". One code path means nothing is
lost if the app dies during an upload, and the offline path is exercised on every send.

**Intent**: an item is either `SAVE_DRAFT` (create and bills, leave a server Draft) or `SUBMIT` (also submit). Correcting a
Rejected or Draft claim from the server is an item with a `serverClaimId` and `baseVersion` (step `update` = `PUT` then bills then
submit if wanted). Withdraw and delete are online-only buttons (single calls, shown only when `allowedActions` has them); they are not
queued, because their meaning depends on the server's current status.

**Alternatives considered**: direct call when online and queue only on failure (rejected: two paths, and a kill between request and
reply is exactly the case the queue must cover).

## 4. Idempotency identity: one `clientRequestId` per claim

**Decision**: `clientRequestId` is a v4 UUID (`expo-crypto` `randomUUID`) made when the form is first saved, stored in the item and
reused on every retry (015 FR-009). It is not regenerated when the claimant edits a waiting item that has not yet reached the server
(the body then differs from nothing the server has seen). Once the server has accepted the create, edits go through `PUT` with the
claim's `version` read from `GET` (the claim is the claimant's own, created by this item, so no one else is expected to have
changed it; if the version is stale anyway the 409 is a refusal shown to the user, see 7).

## 5. Offline storage: encrypted files, key in SecureStore

**Decision**: each outbox item is two kinds of file under the app-private directory `outbox/<ownerUserId>/<itemId>/`: `item.enc`
(the JSON of data-model.md) and `bill-<n>.enc` (the prepared photo bytes). Everything is AES-256-GCM (random 12-byte nonce per file
prepended) with a 256-bit key made once on first use and kept in `expo-secure-store` (Android Keystore). The key survives sign-out
(drafts must), so it is **not** the sign-in credential. `@noble/ciphers` does the AES (pure JS, audited, no native build step); photos
are about 0.3 to 1.5 MB after step 6 so JS speed is fine. `allowBackup` is already false, so the files are not backed up. Files are
removed when the server accepts the item or the user deletes it.

**Why not**: SecureStore for the data (limited to about 2 KB per value and holds no files); AsyncStorage (plain text, 6 MB total on
Android); SQLite/SQLCipher (a native database for a few dozen small records); relying on Android file-based encryption alone (the
spec asks for protected app storage that other apps and a backup cannot read, and the key in Keystore gives that explicitly).

**Honest limit**: it protects the files at rest, not against someone who unlocks the phone and opens the app as that user.

## 6. Photos: always normalise images to jpg

**Decision**: `expo-image-manipulator` (SDK 57 line, chosen by `expo install`). `prepareBill(uri, mimeType)` for any image: decode
(Android decodes HEIC, WebP, PNG and jpg natively on API 29+; EXIF rotation is applied), scale so the **long edge is at most 2000 px**
(never enlarge), save as JPEG with compress 0.8, then check the size: over 10 MB means one more pass at 1600 px and compress 0.6, and
over 10 MB again is refused with a plain message (the claimant may retake). The file name is generated, always ending `.jpg`, and
unique (`bill-<itemShortId>-<n>.jpg`), because the server accepts a file by **name extension only** (`jpg, jpeg, png, pdf`, checked
in `BillService.add`) and the unique name serves the lost-reply check in §2. **PDF** is picked with `expo-document-picker`
(`type: application/pdf`, `copyToCacheDirectory`), checked for 10 MB and passed through unchanged. A PNG is converted too, which
keeps bills small and one format in the approver's view.

**Rationale**: SC-003 (every common phone photo accepted) and SC-004 (never over 10 MB) hold by construction, and conversion at capture
time means the queued file is already valid and small (storage and mobile data).

**Alternatives considered**: leave jpg/png untouched when under 10 MB (rejected: 12 MP photos are 4 to 8 MB and slow on mobile data,
and two code paths); convert on the server (not allowed, 015 unchanged); `react-native-image-resizer` or Skia (rejected: extra
native dependency and no Expo SDK 57 alignment); `expo-image-picker`'s `quality` alone (rejected: does not convert HEIC/WebP or cap
the dimensions).

`pickBill` calls `ImagePicker.launchCameraAsync` and `launchImageLibraryAsync` with `quality: 1` (one lossy pass only, in
`prepareBill`); the camera permission is asked only when the claimant chooses the camera, as in 033, and the permission text in
`app.config.ts` is made general ("to photograph a bill or a check-in photo"). The gallery uses the system picker (no storage
permission).

## 7. Sync engine: foreground, single-flight, FIFO, per-outcome rules

**Decision**: one runner per signed-in user inside `ExpenseOutboxProvider`. Triggers: the provider mounting (app open or sign-in), the
app returning to the foreground (`AppState`), `@react-native-community/netinfo` reporting that a connection returned (a **hint
only**: the truth is whether the request succeeds), a new item saved, the user pressing "Send now", and a 60 s timer while items wait.
It takes the user's items in `createdAt` order and processes one at a time; a refused item does not block the next.

Outcome rules (the app's own words are only the no-connection text and hints; refusals show the server's `reason`):

| Result of a step | Item becomes |
| --- | --- |
| 2xx | next step; after submit (or after bills for `SAVE_DRAFT`) the item and its files are deleted |
| `NoConnectionError`, timeout, 5xx | stays `WAITING`, retried later with backoff (30 s, 60 s, then 5 min) |
| 429 | `WAITING`, after `Retry-After` |
| 401 after the client's own renewal failed | engine stops for this session; items untouched; resumes after sign-in as the same user |
| 409 with `matches` (duplicate warning) | `NEEDS_CONFIRMATION`: the user sees the warning and may confirm (`confirmDuplicate: true` on submit) or leave it as a server Draft |
| 409 stale version / "changed by someone else" | `REFUSED` with the reason; the current claim is read and shown; server wins (see below) |
| 400, 403, 404, other 409 | `REFUSED` with the server's `reason`, stage kept; the user corrects and sends again or deletes |

Because the date rule is checked only at submit (gap G5), an old waiting claim is created as a Draft first and then refused at submit;
the item keeps `serverClaimId` and `serverVersion`, so "correct and send again" is a `PUT` of the new date then submit, never a
second claim.

**Server wins on conflict**: a stale `version` is never overridden; the item is `REFUSED` with the reason, the screen shows the
server's current values next to the waiting ones, and the claimant either re-applies by hand (edit the item, which resets the base
version to the current one) or discards it. No automatic merge.

**Why not a background task**: the access token is memory-only (spec 018 FR-004); a background job would need the renewal credential
and a headless runtime, and Android defers such jobs unpredictably. The spec asks for sync when the connection returns and the app is
opened, which this meets.

## 8. Connectivity detection

**Decision**: no "online" flag is trusted. NetInfo (`isConnected`/`isInternetReachable` changes) only **triggers** a run and drives
the banner's wording; the engine learns real connectivity from `NoConnectionError`, which `httpClient` already raises on any fetch
failure or the 20 s timeout. Form submit with an unreachable server does not wait: if NetInfo says offline the item is saved and the
form closes at once, otherwise the form waits up to the normal request timeout and then saves as waiting.

**Alternatives considered**: polling a health endpoint (rejected: extra server call, adds nothing the real request does not tell);
trusting NetInfo alone (rejected: "connected" with no data is common on mobile).

## 9. Categories, areas, links

**Decision**: the six category keys and labels (`RENT_UTILITIES`, `TRAVEL_FUEL`, `TRAINING_STAY`, `MARKETING`, `OFFICE_SUPPLIES`, `OTHER`)
live in `expenses/categories.ts` with three flags: `reasonRequired` (OTHER), `batchRequired` (TRAINING_STAY), `linkOptional`
(TRAVEL_FUEL). They are a hint; the server's refusals decide (gap G1). Areas come from `GET /my-expenses/areas` (Head office has
`id: null`; gap G2). Link options come from `GET /my-expenses/link-options?category=&date=` (needs Create).

**Offline**: link options are fetched online and kept in the encrypted list cache (last result per category). Offline, Travel and Fuel
claims may skip the optional link; a Training Stay claim can use a cached batch, and without one the item can be saved but is marked
"choose the batch when connected" and is not sent until chosen. Areas are cached the same way.

## 10. Date and amount hints

**Decision**: the date chooser defaults to `businessToday()` (server-corrected clock) and its range is the last 60 days to today as an
**early hint**; the form still sends whatever the claimant keeps, and the server's `date in future` / `older than 60 days` decides
(no stricter local cap, per the clarified answer). An item that waits long enough to become over-age is refused at submit with the
server's reason (§7). Amount: positive, at most two decimals, typed in rupees, sent as the string `"850.00"` (Jackson accepts a string
for `BigDecimal`); the server's message decides anything else. Paid by: `CLAIMANT` or `COMPANY`.

## 11. Users on a shared phone

**Decision**: each item and cached list carries `ownerUserId` (the signed-in user's id from the auth state) and lives in that user's
directory. The provider loads and runs only the current user's items; another user signing in sees none, sends none and cannot reach
the files (the app has no listing across users). **Sign-out** goes through `useSignOutGuard`: if the user has unsent items it asks
"N claims are not sent yet. They stay on this phone and are sent when you sign in again" with Sign out / Stay; it then signs out
without deleting them (the key stays in SecureStore). Session expiry ends in the same state. The same user signing in again resumes.
Items are never deleted automatically; a long-unused user's items remain until that user returns (a known trade-off: the spec sets no
limit, and silent deletion would lose money claims).

## 12. Offline reading

**Decision**: each successful list load (first page of the unfiltered list, with `owed`) is saved to the user's encrypted list cache
with its load time. Offline, or on a network error, the list shows that cache marked "may be out of date, loaded <time>", filters it
locally by status, and shows the waiting items above it. A claim opened offline shows its row (amount, date, category, status, area,
note, link label, bill count) and "More details need a connection"; detail, bills and history are not cached. Bill images are shown
only after an online download (`fetchBinary`, memory only).

**Alternative considered**: caching details and bills (rejected for v1: more sensitive data kept on the phone, and the spec only asks
that the last list be readable).

## 13. Reuse from earlier specs

Reuse as they are: `api` and `upload`/`fetchBinary` (`httpClient`), `useAction` (one request at a time), `useMenuActions`, `MonthPicker`
and `monthRange` for the optional month filter (the contract's `month=`), `DateChooser`, `ReasonDialog`, `AttachmentList` (name, type,
size, thumbnail from bytes), `useInnerBack`, `businessToday`, `managerRefusalText` (to turn errors into text). Add `expensesApi.ts` and
the modules in plan.md. PDF bills are shown by name, type and size only (as 034 attachments); opening a PDF in a viewer is left for
later.

## 14. Testing

**Decision**: native modules (`expo-file-system`, `expo-crypto`, `expo-image-manipulator`, `expo-document-picker`, NetInfo) get in-memory
fakes in `jest.setup.ts` (the file fake is a map; the cipher is tested for real against known vectors with `@noble/ciphers`). The fake
server implements `/my-expenses` including `clientRequestId` replay, the submit rules, the 60-day rule, `allowedActions`, bill
extension/size/count limits and the duplicate 409, with switches to drop a reply after the server acted (the lost-reply case). A
conversion test feeds a HEIC and a WebP asset and asserts the uploaded part is `.jpg`, at most 2000 px, at most 10 MB.

## Gaps found in 015 (recorded, not worked around)

| # | Gap | Effect on 036 | Suggested change to 015 |
| --- | --- | --- | --- |
| G1 | No endpoint lists the categories or says which need a reason, batch or link; the web hard-codes them too. | The phone keeps the six keys and labels in one file; a new category on the server would show only after an app update. Server refusals still decide. | `GET /my-expenses/categories` with key, label, flags. |
| G2 | `GET /my-expenses/areas` (needs Create) exists in `MyExpensesController` but is missing from `contracts/expenses-api.md`; it returns every Zone plus Head office to any claimant. | The phone uses it as built. | Add it to the contract. |
| G3 | Bill upload has no idempotency key; a lost reply followed by a retry can add the same bill twice (5 per claim limit would also be eaten). | The phone avoids it by a unique generated file name and a `GET` check before each upload; this is a client habit, not a server rule. A rare duplicate bill remains possible if the name check races. | Accept an `X-Client-Request-Id` or `clientBillId` on `POST /bills`. |
| G4 | Rows of `GET /my-expenses` carry no decision note (`decision` is only on the detail), but spec 036 FR-015 asks the list to show the decision note. | The list shows the status label; the decision (who, when, reason) shows when the claim is opened. Fetching every detail for the list was rejected (N+1 calls). | Add `decisionReason` and `decidedAt` to `Row`. |
| G5 | `PUT` and `submit` carry the whole claim again and the date rule runs only at submit, so a Draft can hold an over-60-day date. | Offline claims are created as Drafts and refused at submit with the server's reason (handled, see 7). Nothing to change; noted so the web and phone behave alike. | None. |

Gaps G1, G2 and G4 are small and can be fixed in a later 015 amendment; none blocks 036. The duplicate response (`matches`) holds
only claim ids; the phone shows the claimant's own matching claims by reading them (`GET /my-expenses/{id}`), which is allowed because
they are the claimant's own.

## Not in this spec

Approve, reject and any review action; viewing other people's claims; budgets, ageing, cost measures, payment recording and export;
background sync; notification deep links to a claim; opening PDF bills in an external viewer; removing a bill from a Submitted claim
(an approver action on the web); iOS.
