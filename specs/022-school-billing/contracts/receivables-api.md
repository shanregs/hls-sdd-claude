# Contract: School Billing HTTP API

All under `/api/v1/school-billing`, module `SCHOOL_BILLING`. Errors use `{"reason": "..."}`: 400 invalid, 403 not allowed, 404 not found (including a School outside the caller's scope), 409 conflict (a due already exists for the School and month; likely-duplicate collection, body `{"warning":"LIKELY_DUPLICATE"}`). Money is a decimal string with two places; dates are ISO `yyyy-MM-dd`. A Zone Manager sees and acts on their own Schools only on every endpoint.

| Method and path | Needs | Notes |
| --- | --- | --- |
| GET `/overview?month=YYYY-MM&zoneId&managerId&schoolId` | View | Totals owed, collected, outstanding, advance credit; rows per School with Zone and Zone Manager; scope applied. |
| GET `/ageing?asOf&zoneId&managerId` | View | Buckets `NOT_YET_DUE, B0_30, B31_60, B61_90, B90_PLUS` by School, Zone and Zone Manager, grand totals. |
| GET `/not-billed?month` | View | Schools without a signed MoU and Teachers with no position, each with `reason` (`MOU_PENDING`, `NO_POSITION`, `NO_ACTIVE_CONTRACT`). |
| POST `/dues/raise` | Edit (Admin, Director; a Zone Manager cannot hold Edit) | `{month, schoolIds?}`; omitted list raises every School in scope. Returns `{raised[], skipped[{schoolId,reason}], notBilled[]}`. Month must have started. |
| GET `/dues?schoolId&month&status&page&size` | View | Rows: id, reference, school, month, raisedOn, dueOn, total, adjustments, collected, balance, status, overdue, daysOverdue. |
| GET `/dues/{id}` | View | Lines with working, adjustments, allocations, follow-up task link, history. |
| POST `/dues/{id}/cancel` | Approve (Admin, Director) | `{reason}`; 400 while allocations exist. |
| POST `/dues/{id}/adjustments` | Edit (Admin, Director) | `{amount, reason}`; refused above the balance. 201. |
| POST `/adjustments/{id}/reverse` | Edit (Admin, Director) | `{reason}`. |
| GET `/schools/{schoolId}/ledger` | View | Dues, adjustments, collections, advance credit, balance, all months. |
| POST `/collections` | Create | `{schoolId, amount, receivedOn, mode, reference, receiver, comment, allocations?[{dueId,amount}], confirmDuplicate?}`; no allocations means oldest due first, excess kept as advance credit. 201 with the allocations made. |
| POST `/collections/{id}/allocate` | Create | `{allocations[{dueId,amount}]}` from the unallocated amount (default oldest first). |
| POST `/collections/{id}/reverse` | Edit | `{reason}`; undoes its allocations with negative entries. |
| GET `/export?view=overview|ageing|ledger&...` | Export | CSV in the caller's scope. |
| GET `/settings` , PUT `/settings` | View / Edit (Admin, Director) | `{paymentDueDays, escalationDays}`; kept in the `billing_setting` table and edited on the School Billing screen. |

Statuses (derived): OPEN, PART_PAID, PAID, CANCELLED. Modes: BANK, CASH, CHEQUE, UPI.
