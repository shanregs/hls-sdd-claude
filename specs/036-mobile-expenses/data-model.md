# Data Model: Mobile Expenses

The server gets no new table or field. This file lists (A) what the phone stores and its states, and (B) the shapes it reads and
sends, copied from the merged 015 code (`ClaimViews`, `MyExpensesQueries`, `MyExpensesController.ClaimBody`). Money is a string with
two decimals (`"850.00"`), dates are `YYYY-MM-DD` (shown DD/MM/YYYY), instants are ISO.

## A. On the phone

All files are AES-256-GCM encrypted under `outbox/<ownerUserId>/` in the app-private directory; the key is in SecureStore
(research §5). Nothing here is authoritative: the server's claim is.

### OutboxItem (`outbox/<owner>/<itemId>/item.enc`)

| Field | Meaning |
| --- | --- |
| `itemId` | local UUID, also the folder name |
| `ownerUserId` | the signed-in user who captured it; the item is shown and sent only to this user |
| `clientRequestId` | UUID made at first save, reused on every retry, never regenerated (research §4) |
| `intent` | `SAVE_DRAFT` or `SUBMIT` |
| `createdAt`, `updatedAt` | local instants; `createdAt` is the send order |
| `fields` | `expenseDate`, `amount`, `category`, `zoneId` (null = Head office), `areaName`, `note`, `paidBy` (`CLAIMANT`/`COMPANY`), `reason`, `noBillReason`, `link {kind, ref, label, date}` or null |
| `bills[]` | see OutboxBill, 0 to 5 |
| `serverClaimId` | set after the server accepted the create (or when the item corrects an existing claim) |
| `serverVersion` | last `version` the phone read for that claim; the `PUT` sends it |
| `baseSnapshot` | for a correction of an existing claim: the row the claimant started from, to show next to the server's current values on a stale refusal |
| `confirmDuplicate` | true once the claimant confirmed the duplicate warning |
| `state` | see below |
| `stage` | `CREATE`, `UPDATE`, `BILLS`, `SUBMIT`: the next step to run |
| `refusal` | `{status, reason, at}` of the last refusal, or null |
| `attempts`, `nextTryAt` | backoff bookkeeping for `WAITING` |

### OutboxBill (`outbox/<owner>/<itemId>/bill-<n>.enc` plus metadata in the item)

`localId`, `fileName` (generated, unique, `.jpg` for images, `.pdf` for pdf), `contentType` (`image/jpeg` or `application/pdf`),
`sizeBytes`, `uploaded` (true once the server listed this file name on the claim), `serverBillId` (when known).

### States of an OutboxItem

| State | Meaning | Shown as |
| --- | --- | --- |
| `WAITING` | saved, not yet accepted; will be sent when possible | "Waiting to send" |
| `SENDING` | the engine is running it now (in memory only; after a restart an item is `WAITING`) | "Sending..." |
| `NEEDS_CONFIRMATION` | submit answered with the duplicate warning | "Possible duplicate" with Confirm / Keep as Draft |
| `REFUSED` | the server refused a step (400/403/404/409) or the version is stale | "Not sent" with the server's reason |

Transitions: `WAITING -> SENDING -> (deleted | WAITING | NEEDS_CONFIRMATION | REFUSED)`; `REFUSED -> WAITING` when the claimant edits
it or presses Send again; `NEEDS_CONFIRMATION -> WAITING` on Confirm (sets `confirmDuplicate`) or `-> deleted` on Keep as Draft (the
claim stays a server Draft, bills included); any state `-> deleted` by Delete (with its files). The item is deleted from the phone
only after the last step returned 2xx. `stage` moves forward only on a 2xx or on the lost-reply checks of research §2.

Stage by intent: `SAVE_DRAFT` runs `CREATE` (or `UPDATE`), `BILLS`, then ends. `SUBMIT` runs the same and then `SUBMIT`.

### ListCache (`outbox/<owner>/list-cache.enc`)

`loadedAt`, `owed`, `rows[]` (first page of the unfiltered `GET /my-expenses`), `areas[]`, `linkOptions` (last result per category).
Replaced on each successful load; read when offline. One per user.

### In memory only

List state (status filter, page, rows, owed), the open claim, the form state before it is saved as an item, downloaded bill bytes for
viewing, sync banner state (engine running, last attempt, next try), and per-button action state (idle, sending, failed).

### Encryption key

One 256-bit random key per install, key name `hls.outboxKey` in SecureStore; created on first use, never sent anywhere, not removed
at sign-out. If it cannot be read, existing items cannot be opened: they are shown as "could not be opened" with Delete, and new
items cannot be saved (the claimant is told).

## B. Read from the server (015)

### Row (`GET /my-expenses` -> `content[]`)

`id`, `claimantId`, `claimantName`, `expenseDate`, `amount`, `category`, `categoryLabel`, `zoneId`, `areaName`, `status`
(`DRAFT`, `SUBMITTED`, `APPROVED`, `REJECTED`, `PAID`, `PAID_BY_COMPANY`; the label comes as `statusLabel`), `paidBy`, `billCount`,
`noBillReason`, `linkLabel`, `note`, `submittedAt`, `ageDays`, `allowedActions[]`. (`remainingBudget` is review-only and ignored.)
The list answer is `{content, page, size, totalElements, owed}`.

### Detail (`GET /my-expenses/{id}`, and the answer of create, update, submit, withdraw and add bill)

Row fields plus `reason`, `link {kind, kindLabel, ref, date, label}`, `decision {by, byName, at, reason}`, `payment {how, paidOn,
mode, reference, payrollRunId, payrollMonth, recordedByName}`, `payrollState`, `bills[] {id, name, contentType, sizeBytes, addedBy,
addedByName, addedAt, removed, removedByName, removedAt, removalReason}`, `history[] {at, actorId, actorName, event, reason, detail}`,
`ownClaim`, `version`, `createdAt`. `allowedActions` values the app uses: `EDIT`, `SUBMIT`, `WITHDRAW`, `DELETE`, `ADD_BILL`; the
review values (`APPROVE`, `REJECT`, `REVERSE`, `RECORD_PAID`, `REMOVE_BILL`) never appear on a claimant's own claim and are ignored.
Removed bills (`removed: true`) are shown struck through with the removal reason and not downloadable.

### Link option (`GET /my-expenses/link-options`) and area (`GET /my-expenses/areas`)

Link option: `{kind, kindLabel, ref, date, label}`. Area: `{id (null for Head office), name}`.

## C. Sent to the server

| Call | Body |
| --- | --- |
| Create | `{clientRequestId, expenseDate, amount, category, zoneId, note, paidBy, reason, noBillReason, link {kind, ref}, confirmDuplicate: false, submit: false}` (the app always sends `submit: false` and submits in a separate step) |
| Update | the same fields plus `version` (`PUT /my-expenses/{id}`) |
| Submit | `{version, confirmDuplicate}` |
| Withdraw | `{version}` |
| Bill | multipart part `file` (generated name, `image/jpeg` or `application/pdf`) |
| Delete | none |

Limits the server enforces and the app only hints: amount above zero, two decimals; note up to 1000 and reason / no-bill reason up to
500 characters; date not in the future and not older than 60 days (checked at submit); at most 5 live bills; 10 MB each; jpg, jpeg,
png or pdf by name; Other needs a reason; Training Stay needs a batch; a submitted claim needs a bill or a no-bill reason.

## State changes caused

The app causes only 015's own transitions and shows them after the response: Draft or Rejected to Submitted (submit), Submitted to
Draft (withdraw), Draft deleted. The decisions (Approved, Rejected, Paid, Paid by company) happen on the web.
