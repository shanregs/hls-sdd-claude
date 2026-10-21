# Contract: Expenses HTTP API

Base path `/api/v1`. JSON. The actor is always the JWT user. Errors use the body `{"reason": "..."}` of the other modules:
400 invalid input, 403 forbidden field or action, 404 not found (also for out-of-scope), 409 conflict or stale `version`.
Money is a JSON string with two decimals (`"850.00"`). Dates are `YYYY-MM-DD`. Every claim in a response carries
`allowedActions` (FR-031) and `version`.

## Claimant: My Expenses (`MY_EXPENSES`)

| Method and path | Action | Purpose |
| --- | --- | --- |
| `GET /my-expenses?status=&month=&page=` | VIEW | own claims, newest first; response includes `owed` (sum of own Approved personally paid, unpaid) |
| `POST /my-expenses` | CREATE | create a Draft, or submit at once with `submit: true`. Body: `clientRequestId`, `expenseDate`, `amount`, `category`, `zoneId` (null = Head office), `note`, `paidBy`, `reason`, `noBillReason`, `link {kind, ref}`, `confirmDuplicate`. A repeat `clientRequestId` returns the first claim (200) |
| `GET /my-expenses/{id}` | VIEW | one own claim with bills, link, history, allowed actions |
| `PUT /my-expenses/{id}` | EDIT | change a Draft or Rejected claim; body as create plus `version` |
| `DELETE /my-expenses/{id}` | DELETE | delete an own Draft |
| `POST /my-expenses/{id}/submit` | EDIT | Draft or Rejected to Submitted; body `{version, confirmDuplicate}`; 409 with `matches` when a duplicate is found and not confirmed |
| `POST /my-expenses/{id}/withdraw` | EDIT | Submitted to Draft before any decision |
| `POST /my-expenses/{id}/bills` | EDIT | multipart file; jpg, png or pdf, at most 10 MB, at most 5 live bills; 400 with the reason otherwise |
| `GET /my-expenses/{id}/bills/{billId}` | VIEW | download one bill (own claim only) |
| `GET /my-expenses/link-options?category=&date=` | CREATE | planned activities of the caller on that date and their own (Travel and Fuel), or open induction batches (Training Stay) |

## Review and payment (`EXPENSES`)

| Method and path | Action | Purpose |
| --- | --- | --- |
| `GET /expenses?status=&claimant=&zone=&category=&from=&to=&page=` | VIEW | all claims; default Submitted oldest first; each row has age and the area's remaining budget for the claim's month |
| `GET /expenses/{id}` | VIEW | claim with bills, link, history, allowed actions, payroll state |
| `GET /expenses/{id}/bills/{billId}` | VIEW | download a bill |
| `POST /expenses/{id}/approve` | APPROVE | body `{version}`; refused if the caller is the claimant (403 "you raised this claim") or already decided (409 `{decidedBy, decidedAt}`); a company-paid claim becomes Paid by company |
| `POST /expenses/{id}/reject` | APPROVE | body `{version, reason}`; reason required |
| `POST /expenses/{id}/reverse` | APPROVE or PROCESS | body `{version, reason}`; refused when paid or held by a pay run awaiting approval |
| `POST /expenses/{id}/record-paid` | PROCESS | body `{version, paidOn, mode, reference}`; withdraws the payroll offer; refused when held by a run awaiting approval or applied |
| `DELETE /expenses/{id}/bills/{billId}` | APPROVE | body `{reason}`; recorded removal, claim status unchanged |
| `GET /expenses/export?month=` | EXPORT | CSV, one row per claim: claimant, date, category, area, amount, status, approver, decision date, linked work, paid-by, payment details; formula characters neutralised; audited |

## Budgets and measures (`EXPENSES`)

| Method and path | Action | Purpose |
| --- | --- | --- |
| `GET /expenses/budgets?month=` | VIEW | per area (and total): `budget` (null = "No budget set"), `actual`, `pending`, `remaining`, `percentUsed`, `over` |
| `GET /expenses/budgets/detail?month=&zoneId=` | VIEW | claims behind actual, grouped by category and by claimant |
| `PUT /expenses/budgets` | CREATE or EDIT | body `{zoneId|null, month, amount}`; keeps a change row; never blocks claims |
| `GET /expenses/budgets/{id}/history` | VIEW | who changed it and when |
| `GET /expenses/pending` | VIEW | counts and totals for 0-2, 3-7, 8-14 and over 14 days, oldest first |
| `GET /expenses/costs/per-school?from=&to=&zoneId=` | VIEW | `{cost, schools, state, claims[]}`, `state` in `OK`, `NO_SCHOOLS` |
| `GET /expenses/costs/per-teacher?from=&to=&batchId=` | VIEW | `{cost, teachers, state, claims[]}`, `state` in `OK`, `NO_TEACHERS` |

## Rules

- A claim response never includes another person's data to a claimant; `GET /my-expenses/{id}` of someone else's claim is 404.
- System has no access to any path; a Teacher reaches only `/my-expenses`.
- `allowedActions` for a claim is computed from the viewer's grants, ownership and status: `EDIT`, `SUBMIT`, `WITHDRAW`,
  `DELETE`, `ADD_BILL`, `APPROVE`, `REJECT`, `REVERSE`, `RECORD_PAID`, `REMOVE_BILL`.
- Date errors name the rule: `date in future`, `older than 60 days`; category errors: `reason required`, `batch required`,
  `bill or no-bill reason required`.
- Notifications: submit and resubmit notify every active Admin and Director; approve, reject and reverse notify the claimant.
- Audit entity types: `EXPENSE_CLAIM`, `EXPENSE_BUDGET`, `EXPENSE_EXPORT`.
