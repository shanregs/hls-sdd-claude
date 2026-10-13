# Contract: Payroll Run HTTP API

Base path `/api/v1/payroll`. JSON unless stated. The caller's identity always comes from the JWT. Every endpoint
checks its permission on the server and answers `403` without a body of figures when the caller lacks it. Money is a
decimal string with two places (`"12500.00"`), dates are `yyyy-MM-dd` (the screens show DD/MM/YYYY), months are
`yyyy-MM`. Errors use the application's standard problem body; `409` for a state or uniqueness conflict, `400` for
invalid input with the field named, `404` when the id does not exist.

Permissions: **V** = `PAYROLL.VIEW`, **P** = `PAYROLL.PROCESS`, **A** = `PAYROLL.APPROVE`, **E** = `PAYROLL.EXPORT`.

## Runs

| Method and path | Perm | Purpose | Notes |
| --------------- | ---- | ------- | ----- |
| `GET /runs?month=&status=&page=&size=` | V | list runs, newest month first | |
| `POST /runs` | P | start a run `{ "month": "2026-09", "supplementary": false }` | `202` with the run (`computeState: QUEUED`). `400` month not ended; `409` a primary run exists, primary not approved, a supplementary run is open, or nobody is owed |
| `GET /runs/{id}` | V | header, status, totals, counts, progress, policy, history of events, last recompute summary | |
| `POST /runs/{id}/recompute` | P | compute a draft again | `202`; `409` unless draft and not currently computing (stale heartbeat allowed) |
| `POST /runs/{id}/cancel` | P | `{ "reason": "..." }` | draft only; `409` otherwise |
| `POST /runs/{id}/submit` | P | submit for approval | `409` if not draft, computing, or any line has a negative net (names the line) |
| `POST /runs/{id}/return` | A | `{ "reason": "..." }` back to draft | reason required, at most 500 characters |
| `POST /runs/{id}/approve` | A | approve and lock | `403` when the caller submitted it; `409` if not awaiting approval |

Run summary: `id, month, kind, sequence, status, computeState, progressDone, progressTotal, totals {earned, deduction,
adjustments, net}, payableCount, cannotPayCount, paidCount, unpaidCount, paidAmount, unpaidAmount, startedBy,
submittedBy, approvedBy, timestamps, policy, version`.

## Lines

| Method and path | Perm | Purpose |
| --------------- | ---- | ------- |
| `GET /runs/{id}/lines?state=&kind=&query=&page=&size=` | V | the lines (50 a page); `state` is `PAYABLE` or `CANNOT_PAY` |
| `GET /runs/{id}/lines/{lineId}` | V | one line with its full frozen working, adjustment lines, payment entries, warning |
| `GET /runs/{id}/owed` | V | people not yet paid for the run's month: reason left out, whether resolved now |

A `CANNOT_PAY` line has `reason` and `null` amounts, never zero.

## Adjustments

| Method and path | Perm | Purpose |
| --------------- | ---- | ------- |
| `GET /adjustments?personKind=&personId=&status=&page=` | V | list |
| `POST /adjustments` | P | `{ personKind, personId, kind: "CORRECTION", amount, reason }`; a reserved kind answers `400` ("created by spec 030/015/017b") |
| `POST /adjustments/{id}/withdraw` | P | `{ "reason": "..." }` waiting only; `409` when applied, or attached to a run awaiting approval |

## Payment

| Method and path | Perm | Purpose |
| --------------- | ---- | ------- |
| `PUT /runs/{id}/lines/{lineId}/payment` | P | `{ paid, paidOn, mode, reference }`; approved run, payable line only (`409` otherwise); `paidOn` not in the future |
| `GET /runs/{id}/lines/{lineId}/payment` | V | current entry and the earlier ones |

## Payslips and export

| Method and path | Perm | Purpose |
| --------------- | ---- | ------- |
| `GET /runs/{id}/lines/{lineId}/payslip` | V | the PDF (`application/pdf`); `404` when none (draft, awaiting, cannot-pay line) |
| `GET /runs/{id}/payslips` | V | a zip of every payslip of the run |
| `GET /runs/{id}/export` | E | CSV (`text/csv; charset=utf-8`); the first two rows state the month and the run status; audited |

## History

| Method and path | Perm | Purpose |
| --------------- | ---- | ------- |
| `GET /people/{kind}/{id}/history` | V | each run the person was in: month, run kind, net pay, status, payment entry |

## Authorization matrix (what the tests assert on every endpoint)

| Role | Read (V) | Process (P) | Approve (A) | Export (E) | Anything |
| ---- | -------- | ----------- | ----------- | ---------- | -------- |
| Admin | yes | yes | no | yes | |
| Director | yes | no | yes | yes | |
| Manager | `403` | `403` | `403` | `403` | no figures |
| Teacher | `403` | `403` | `403` | `403` | no figures |
| System | `403` | `403` | `403` | `403` | |
| Unauthenticated | `401` | | | | |

A custom grant (for example Process given to the Director) is honoured by the same checks; the submit/approve
separation still applies to the person, not the role.

## Audit and events

Each write above writes an audit entry (entity types `PAYROLL_RUN`, `PAYROLL_ADJUSTMENT`, `PAYROLL_PAYMENT`,
`PAYROLL_EXPORT`) with the actor, the run or line, and the reason where there is one. `submit`, `approve` and `return`
publish `PayrollRunSubmitted`, `PayrollRunApproved`, `PayrollRunReturned` (month, run id, actor, reason on return; no
amounts) for the notification module.
