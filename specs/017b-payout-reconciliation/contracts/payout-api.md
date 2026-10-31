# Contract: Substitute Payouts REST API

Base `/api/substitute-payouts`. Rights: V = View, E = Edit (mark days), A = Approve (lock and reopen a substitute-month), X = Export. Admin, Director org-wide; Zone Manager V and E on own Zone Schools only. Raising a Correction needs the Admin role in payroll (013b rule). Out-of-scope ids answer 404. A substitute has no login, so there is no `/mine` endpoint. Dates ISO on the wire, shown DD/MM/YYYY; money as decimal Rupees.

| Endpoint | Right | Notes |
| --- | --- | --- |
| `GET /days?month=&from=&to=&substituteId=&schoolId=&zoneId=&status=&page=&sort=` | V | rows `{id, substituteName, schoolName, zoneName, date, dayValue, attendance, status, statusText, rate, amount, runMonth, workMonth, actions[]}` plus totals `{allocated, attended, paid, unpaidByStatus{}, amountPaid}` for the whole filter |
| `GET /substitutes/{teacherId}?month=` | V | one substitute's month: designation, rate(s), days, totals, history (replaces the old Teacher view) |
| `GET /summary?month=&groupBy=substitute|school|month` | V | allocated, attended, paid and unpaid days and amounts per group |
| `GET /mismatches?...` | V | rows with `reason` and `nextStep` |
| `GET /days/{id}` | V | one day with its event history, run, paid amount, correction reference |
| `PUT /days/{id}/attendance` | E | `{ attendance: "PRESENT"|"ABSENT"|"NOT_MARKED", version }`; refused for future dates, a locked month, a cancelled day; `409` stale version; audit |
| `POST /months/{teacherId}/{yyyy-MM}/lock` | A | only after the month has ended and every live day is marked; `409` lists the unmarked days |
| `POST /months/{teacherId}/{yyyy-MM}/reopen` | A | `{ reason }` (required, at most 500) |
| `POST /months/lock?month=` | A | locks every substitute-month in that month that is complete; returns counts and the blocked ones |
| `POST /days/{id}/correction` | Admin | `{ amount, reason }`; only for "correction needed" or a differing amount; `409` when already raised; calls `CorrectionRaising` |
| `GET /export?...` | X | CSV of the days or mismatches, formula-safe |
| `POST /reconcile?month=` | E (Admin, Director) | runs the repair for a month; idempotent; returns counts |
| `POST /migration/run` | Admin | one-off go-live migration of existing credits (Story 6); idempotent; returns migrated, settle-first and skipped counts |

Designation day rates (payroll, 013a amendment): `GET /api/salary-structures/designation-day-rates`, `POST` (Admin, Director; `{ designationId, amount > 0, effectiveOn }`), history per designation; listed in the 013a API doc when merged.

Teacher record (005 amendment): `engagement` field on the Teacher create/update/read; setting it to SUBSTITUTE clears any account link.

Errors: 400 invalid, 403 no grant, 404 out of scope, 409 conflict. Events consumed: `SubstitutionAssigned`, `SubstitutionEnded` (017a), `PayrollRunApproved`, `PayrollRunReturned` (013b). Events published: none.
