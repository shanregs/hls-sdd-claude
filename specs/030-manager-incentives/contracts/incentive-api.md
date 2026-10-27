# Contract: Incentive REST API

Base `/api/v1/incentives`. Every response is filtered by the caller's scope; anything out of scope answers 404.
Scope: Admin and Director all; a Manager sees incentives where they are the earner, plus those of Schools in their Zone if they are a Zone Manager.
Teacher and System: 403.

| Method and path | Grant | Purpose |
| --- | --- | --- |
| GET `/` | VIEW | page of incentives; filters `status`, `managerId`, `zoneId`, `month` (closure or run month), `q`; sort; returns `actions` per row |
| GET `/{id}` | VIEW | detail with School, Zone, earner, MoU link, status, history (`incentive_event`), payroll status text |
| POST `/{id}/approve` | APPROVE | body `{version}`; Director, or Zone Manager of the School's Zone (also when the earner); 409 if already decided (names decider) |
| POST `/{id}/reject` | APPROVE | body `{version, reason}` reason required, at most 500 |
| POST `/{id}/reopen` | APPROVE (Director, or Zone Manager of the School's Zone) | body `{version, reason}`; REJECTED back to PENDING_APPROVAL |
| GET `/summary` | VIEW | counts and totals per status, and per Manager and month (`from`, `to`) |
| GET `/export.csv` | EXPORT | same filters as the list; formula cells neutralised |
| GET `/no-earner` | VIEW (Admin, Director) | Schools with an approved MoU and no incentive, reason, resolved flag |
| GET `/mine` | VIEW | the caller's own incentives (any Manager) |

Row shape: `id, schoolId, schoolName, zoneName, earnerManagerId, earnerName, contractId, closureDate (DD/MM/YYYY in UI), amount, status,
mouChanged, holdReason, decidedBy, decidedAt, approvedByEarner, rejectReason, runMonth, payrollStatus, version, actions[]`.
`actions` contains `approve`, `reject`, `reopen` only when allowed for this caller and state.

Errors: 400 validation (missing reason), 403 grant, 404 scope, 409 stale version or already decided (body names decider and time).
Audit: each POST writes `incentive_event` and Change History. Amount and earner are never accepted from the client.
