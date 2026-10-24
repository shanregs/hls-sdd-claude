# Contract: Server calls the app makes (spec 015, unchanged)

The full server contract is `specs/015-expenses/contracts/expenses-api.md`; this file says which calls the app makes, when, and what
it does with each answer. Base `/api/v1/my-expenses`, module `MY_EXPENSES`. Every call carries the audit headers of spec 018 and goes
through `httpClient` (renewal once on 401, typed errors). Errors use `{ "reason": "..." }`; the app shows `reason` as it is
(400, 403, 404, 409), says "No connection. Nothing was sent." when the request cannot be made, and sends the user to sign-in on 401 after
one renewal (waiting claims stay, research §11).

## Calls by screen

| When | Call | Needs | Body or answer used |
| --- | --- | --- | --- |
| My Expenses opens, filter changes, refresh, Load more | `GET /?status=&month=&page=&size=50` | View | `{content, page, size, totalElements, owed}`; `status` is one of `DRAFT SUBMITTED APPROVED REJECTED PAID PAID_BY_COMPANY` |
| A claim is opened or reloaded | `GET /{id}` | View | the Detail incl. `allowedActions`, `version` |
| Form opens (and is cached) | `GET /areas` | Create | `[{id, name}]`, `id` null = Head office |
| Link field opens or category/date changes | `GET /link-options?category=&date=` | Create | `[{kind, kindLabel, ref, date, label}]`; only for `TRAVEL_FUEL` and `TRAINING_STAY` |
| Bill is opened | `GET /{id}/bills/{billId}` | View | bytes with `Content-Type`, `Content-Disposition: attachment`, `no-store`; shown in memory (`fetchBinary`) |
| Withdraw (button when `allowedActions` has `WITHDRAW`) | `POST /{id}/withdraw` | Edit | `{version}`; answer: Detail (now Draft) |
| Delete (button when `DELETE`) | `DELETE /{id}` | Delete | 204 |
| Sync: first step of a new claim | `POST /` | Create | see below; 201 new or 200 replay, answer Detail |
| Sync: first step when correcting a claim | `PUT /{id}` | Edit | the same fields plus `version`; answer Detail |
| Sync: each bill | `POST /{id}/bills` (multipart `file`) | Edit | 201, answer Detail (the `bills` list is the check for lost replies) |
| Sync: lost-reply checks and version refresh | `GET /{id}` | View | `status`, `bills[].name`, `version` |
| Sync: last step | `POST /{id}/submit` | Edit | `{version, confirmDuplicate}`; answer Detail |

## Create / update body

```json
{
  "clientRequestId": "6f1c...-uuid",
  "expenseDate": "2026-10-23",
  "amount": "850.00",
  "category": "TRAVEL_FUEL",
  "zoneId": null,
  "note": "Petrol, school visit",
  "paidBy": "CLAIMANT",
  "reason": null,
  "noBillReason": null,
  "link": { "kind": "SCHOOL_VISIT", "ref": "<uuid from link-options>" },
  "confirmDuplicate": false,
  "submit": false,
  "version": 3
}
```

`clientRequestId` only on create; `version` only on update. Empty text fields are sent as null. `link` is null when none chosen (and
must be null for categories that take no link: the server refuses one). The app always sends `submit: false`; submitting is its own
call so that bills can be added first (research §2). A repeat of a create with the same `clientRequestId` and the same date, amount,
category, area and note returns the first claim with 200; with a different body it is 409 "already used for a different claim".

## What the app does with each answer

| Answer | App action |
| --- | --- |
| 2xx | advance the stage, save the new `serverClaimId` / `serverVersion` to the item; delete the item after the last step |
| 400 `reason` (e.g. `date in future`, `older than 60 days`, `reason required`, `batch required`, `bill or no-bill reason required`, "A claim can have at most 5 bills.", "A bill must be a jpg, png or pdf file.", file too large) | item `REFUSED` (sync) or inline message (form), entries kept |
| 403 `reason` | item `REFUSED` with the reason (e.g. a link not allowed for the category) |
| 404 | item `REFUSED` "Claim not found" (deleted on the web, or not the claimant's); the claimant can discard it |
| 409 with `matches[]` | `NEEDS_CONFIRMATION`; on Confirm resend submit with `confirmDuplicate: true` |
| 409 "Only a Draft or Rejected claim ..." on submit/bills/update | read the claim: `SUBMITTED` on a submit step means done; otherwise `REFUSED` with the reason (status changed elsewhere) |
| 409 "changed by someone else" (stale `version`) | `REFUSED`; read the claim and show its current values (server wins) |
| 401, 426, 429, network failure, timeout, 5xx | not a refusal; see research §7 |

## Rules the app relies on and does not repeat

- A claimant sees only their own claims; another person's id answers 404.
- `allowedActions` decides Edit, Submit, Withdraw, Delete and Add bill; the phone never computes them from the status.
- Duplicate = same claimant, date, amount and category; checked at submit.
- Date window, category rules and bill requirement are checked at submit; amount and area are checked on create and update.
- Bills: jpg/jpeg/png/pdf by file name, at most 10 MB, at most 5 live per claim, only while the claim is a Draft or Rejected.
- Teacher, Manager, Director and Admin hold `MY_EXPENSES` by default; System has no expense path (403/hidden).
- Date-sensitive checks use the server's business date, never the phone's clock.

## Not called

Everything under `/api/v1/expenses` (review, approve, reject, reverse, record paid, remove bill, export, budgets, pending, costs).
`GET /areas` is used but is missing from the 015 contract document (research gap G2).
