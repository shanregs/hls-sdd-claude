# Contract: MoU documents and approval API

Base path `/api/v1/school-contracts` (the existing 012 controller path; the new routes are added beside the 012 ones, which do not
change). JSON, except upload (multipart) and download (bytes). Dates `YYYY-MM-DD` on the wire (shown DD/MM/YYYY in the UI).
Every endpoint checks the grant on `SCHOOL_CONTRACTS` and the caller's scope on the server; an id out of scope answers 404 and
never reveals that a document exists. Errors use the shared problem format: 400 invalid input, 403 missing grant or forbidden role
action, 404 not found or out of scope, 409 rule refused, version changed or already decided.

## Approval object (added to the contract detail as `approval`, and returned by the approval endpoints)

```json
{ "contractId": "uuid",
  "state": "NOT_APPLICABLE|DRAFT|PENDING|APPROVED|APPROVED_WITHOUT_DOCUMENT|APPROVED_BEFORE_APPROVAL_STEP|REJECTED|CANCELLED",
  "round": 2,
  "submittedBy": {"id": "", "name": ""}, "submittedAt": "",
  "decidedBy": {"id": "", "name": ""}, "decidedAt": "", "decisionReason": null, "selfDecided": false,
  "document": {"state": "NONE|ATTACHED", "current": { "id": "uuid", "versionNo": 2, "name": "mou-signed.pdf",
               "contentType": "application/pdf", "sizeBytes": 482113, "addedBy": {"id": "", "name": ""}, "addedAt": "" } },
  "documents": [ { "id": "uuid", "versionNo": 2, "name": "", "contentType": "", "sizeBytes": 0, "addedBy": {}, "addedAt": "",
                   "current": true, "removed": null },
                 { "id": "uuid", "versionNo": 1, "name": "", "current": false,
                   "removed": {"by": {"id": "", "name": ""}, "at": "", "reason": "wrong file"} } ],
  "history": [ { "at": "", "action": "REJECTED", "round": 1, "actor": {"id": "", "name": ""}, "actorRole": "MANAGER",
                 "note": "Rate does not match the signed copy", "detail": null, "documentVersion": null } ],
  "version": 4,
  "actions": ["UPLOAD","REMOVE_DOCUMENT","SUBMIT","APPROVE","REJECT","AMEND_TERMS"] }
```

`actions` is computed for the caller from their grants, role, scope and the state (see "Who may do what"); it never offers an
action the server would refuse. `history` is oldest first. `selfDecided` is true when the decider is also the submitter.
`removed` is null for a version still available. A contract with no approval row (the "MoU pending" placeholder) answers
`NOT_APPLICABLE`, an empty `documents`, and no actions except none.

## Endpoints

| Method and path | Grant | Purpose |
| --- | --- | --- |
| `GET /schools/{schoolId}` (existing) | VIEW | Each contract in the response gains `approval` (state, round, document summary, `actions`, `version`) without `history` |
| `GET /?approval=&document=&status=&managerId=&page=&size=` (existing list) | VIEW | Rows gain `approvalState`, `documentState`, `pendingSince`; new filters `approval` (any state name above) and `document` (`NONE`, `ATTACHED`) |
| `GET /contracts/{id}/approval` | VIEW | Full approval object with documents and history |
| `POST /contracts/{id}/documents` | EDIT | Multipart field `file`. Adds the next version; on an APPROVED MoU also resets it to PENDING. Returns the approval object. Admin and Director only (a Manager holding EDIT is still refused with 403 by role rule) |
| `GET /contracts/{id}/documents/{documentId}/file` | VIEW | Bytes of a version that is not removed; `Content-Disposition: attachment`, `X-Content-Type-Options: nosniff`, content type decided by the server. 404 when out of scope, unknown, belongs to another contract, or removed |
| `POST /contracts/{id}/documents/{documentId}/remove` | EDIT | Body `{ "reason": "" }` (required, at most 300). Admin and Director only. Keeps the bytes and the history; status unchanged |
| `POST /contracts/{id}/submit` | EDIT | Body `{ "version": n }`. DRAFT or REJECTED to PENDING (RESUBMITTED when rejected before). No document needed. Admin and Director only |
| `POST /contracts/{id}/approve` | APPROVE | Body `{ "version": n }`. PENDING to APPROVED. Director, or a Manager whose scope covers the School |
| `POST /contracts/{id}/reject` | APPROVE | Body `{ "version": n, "reason": "" }` (required, at most 500). PENDING to REJECTED |
| `PATCH /contracts/{id}/terms` | APPROVE | Body `{ "version": n, "rate"?: "1200.00", "positions"?: [{"number": 1, "salary": "1500.00"}], "signedOn"?: "", "startsOn"?: "", "endsOn"?: "" }`. Only while PENDING. Returns the approval object plus the contract. Only the fields sent change |
| `GET /approvals/pending?page=&size=` | APPROVE | PENDING MoUs in the caller's scope, oldest submission first |

### Pending list row

```json
{ "contractId": "uuid", "schoolId": "uuid", "schoolName": "", "zoneManagerName": "", "round": 1,
  "submittedBy": {"id": "", "name": ""}, "submittedAt": "", "documentState": "ATTACHED|NONE",
  "teacherCount": 5, "salaryMode": "SAME_FOR_ALL", "rate": "1200.00", "startsOn": "", "endsOn": null, "approvalVersion": 3 }
```

### Request and response notes

- `version` is the approval `version` from the last read. A mismatch, or a term edit by another approver in between, answers 409
  with the current state in the problem body.
- Two simultaneous decisions: the second answers 409 `This MoU was already decided: approved by <name> on DD/MM/YYYY HH:mm.`
- `PATCH terms` refusals are plain `problems`: not pending; amount not above zero, more than two decimals or above 10,000,000;
  signed date in the future; end before start; start date change would leave a gap or overlap (reject and record a new MoU).
  A field not editable (`teacherCount`, `salaryMode`, `signatories`, `schoolId`) is refused with 400.
- A failed upload (type, size, empty, unreadable) answers 400 with the reason and stores nothing; the state is unchanged.
- Dates in `history` are instants; the UI shows DD/MM/YYYY HH:mm in Asia/Kolkata.

## Who may do what (server rules)

| Action | Admin | Director | Manager (Zone Manager) | Teacher, System |
| --- | --- | --- | --- | --- |
| View approval, list documents, download | yes (VIEW) | yes | yes, Schools in scope | no |
| Upload, replace, remove, submit | yes (EDIT) | yes | no (403, even if granted EDIT) | no |
| Approve, reject, amend terms | no (403) | yes, any School, own submission included | yes, own Zone's Schools only (404 otherwise) | no |

Pre-checks in order: grant, scope (404), contract state (CANCELLED refuses submit, decide and amend; the placeholder refuses
upload and submit), status (409), version (409).

## Permissions and defaults

`SCHOOL_CONTRACTS` gains `APPROVE`. Seeded: Director `APPROVE`; Manager `APPROVE` (and `VIEW` as before). Not eligible: Admin,
Teacher, System. The existing seeds for View, Create, Edit are unchanged. Navigation: OPERATIONS item "MoU Approvals"
(`/operations/school-contracts/approvals`) requires `APPROVE`; "School Contracts" is unchanged.
