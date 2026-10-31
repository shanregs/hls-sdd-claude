# Contract: Substitute Payouts REST API

Base `/api/substitute-payouts`. Roles: V = View (Admin, Director org-wide, Zone Manager own Zone), X = Export, E = Edit (Admin only). Out-of-scope ids answer 404. Dates ISO on the wire, shown DD/MM/YYYY; money as decimal Rupees.

| Endpoint | Right | Notes |
| --- | --- | --- |
| `GET /?month=&from=&to=&teacherId=&schoolId=&zoneId=&status=&page=&sort=` | V | rows `{id, teacherName, schoolName, zoneName, date, dayValue, amount, status, statusText, runMonth, actions[]}` plus totals `{daysCredited, daysPaid, daysUnpaidByStatus{}, amountPaid}` for the whole filter |
| `GET /summary?month=&groupBy=teacher|school|month` | V | credited, paid and unpaid days and amounts per group; credited = paid + unpaid |
| `GET /mismatches?...` | V | the mismatch rows with `reason` and `nextStep` |
| `GET /{id}` | V | one payout with status history (events), the run, the adjustment, the correction |
| `POST /{id}/correction` | E | `{ amount, reason }` prefilled by the client from `GET /{id}`; only for VOIDED_AFTER_PAYMENT or an amount mismatch; `409` when one is already raised; writes audit |
| `GET /export?...` | X | CSV of the list or the mismatches, formula-safe |
| `POST /reconcile` | E | runs the daily job now for a month; idempotent; returns counts |
| `GET /mine?page=` | Teacher own | `{date, schoolName, dayValue, payStatus: WAITING|IN_NEXT_PAYSLIP|PAID, paidInMonth, amount (only PAID)}`; used by My Substitutions |

Errors: 400 invalid, 403 no grant, 404 out of scope, 409 conflict (already corrected, stale `version`).

Events consumed: `SubstitutionAssigned`, `SubstitutionEnded` (017a), `PayrollAdjustmentApplied` (013b). Events published: none.
