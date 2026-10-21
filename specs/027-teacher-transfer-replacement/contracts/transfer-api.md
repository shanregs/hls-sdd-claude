# Contract: Transfers & Replacements API

Base path `/api/transfers`. JSON. Dates `YYYY-MM-DD` on the wire (shown DD/MM/YYYY in the UI). Every endpoint checks the grant
on `TEACHER_TRANSFERS` and the caller's scope on the server; an id out of scope answers 404. Errors use the shared problem
format: 400 invalid input, 403 missing grant or forbidden decision, 404 not found or out of scope, 409 rule refused or version
changed. A refusal that comes from a rule returns `problems: [string]` in plain words and changes nothing.

## Request object

```json
{ "id": "uuid", "kind": "TRANSFER|REPLACEMENT",
  "status": "PENDING|APPROVED|APPLIED|REJECTED|CANCELLED",
  "teacher": {"id": "", "name": ""}, "fromSchool": {"id": "", "name": ""}, "toSchool": {"id": "", "name": ""},
  "position": {"id": "", "number": 2} , "replacementTeacher": {"id": "", "name": ""},
  "outgoingDestinationSchool": {"id": "", "name": ""},
  "effectiveOn": "2026-11-03", "reasonCategory": "SCHOOL_REQUEST", "note": "",
  "requestedBy": {"id": "", "name": ""}, "requestedAt": "", "decidedBy": null, "decidedAt": null, "decisionReason": null,
  "version": 3, "actions": ["APPROVE","REJECT","CANCEL","CHANGE"], "checks": [{"ok": true, "text": ""}] }
```

`actions` is computed for the caller (never offers APPROVE to the requester or to a role without the grant). `checks` is the
live result of the rules (empty on a final request).

## Endpoints

| Method and path | Grant | Purpose |
| --- | --- | --- |
| `GET /api/transfers?status=&schoolId=&from=&to=&page=&size=` | VIEW | List in scope; default status PENDING and APPROVED; the derived APPLIED is filterable |
| `GET /api/transfers/{id}` | VIEW | Detail with `checks` and the request's history entries |
| `POST /api/transfers/transfers` | CREATE | Raise a transfer: `teacherId, toSchoolId, positionId?, effectiveOn, reasonCategory, note` |
| `POST /api/transfers/replacements` | CREATE | Raise a replacement: `teacherId, replacementTeacherId, effectiveOn, outgoingDestinationSchoolId?, reasonCategory, note` |
| `POST /api/transfers/check` | CREATE | Dry run of either body (with `kind`); returns `{ok, problems[]}`, writes nothing |
| `GET /api/transfers/candidates?schoolId=&on=` | CREATE | Replacement candidates: ACTIVE, unplaced on the date, no open request; `{id, name, status}` |
| `GET /api/transfers/vacancies?schoolId=&on=` | CREATE | Vacant positions of the receiving School on the date, `{positionId, number, salary?}`; empty for a "MoU pending" School |
| `PATCH /api/transfers/{id}` | EDIT | Change date, position, destination or note of a PENDING request; `version` required; requester or a decider |
| `POST /api/transfers/{id}/approve` | APPROVE | `version`; applies the placement change; 403 for requester and Admin |
| `POST /api/transfers/{id}/reject` | APPROVE | `version`, `reason` required |
| `POST /api/transfers/{id}/cancel` | EDIT | `version`, `reason` required; Pending, or Approved with a future date; requester, Director or deciding Manager |
| `GET /api/transfers/teachers/{teacherId}/movements` | VIEW | A Teacher's placement lines (see below) |
| `GET /api/transfers/schools/{schoolId}/movements` | VIEW | A School's arrivals and departures |

Teacher list filter (existing endpoint, new parameter): `GET /api/teachers?movement=TRANSFERRED|REPLACED`, applied inside the
caller's scope.

## Movement line

```json
{ "teacher": {"id":"","name":""}, "school": {"id":"","name":""}, "positionNumber": 2,
  "from": "2026-04-01", "to": "2026-11-02", "kind": "INITIAL|DIRECT|TRANSFER|REPLACEMENT",
  "requestId": "uuid|null", "reason": "", "requestedBy": "", "approvedBy": "", "otherTeacher": {"id":"","name":""} }
```

## Rule summary (what the server refuses)

Not placed there on the date; Teacher in training or exited; effective date in the past; non-leave mark on or after the date;
locked month; receiving School inactive or without a vacant (or named vacant) position; replacement placed on the date or
exited or in training; Teacher already in an open request; scheduled move already present; deciding own request; Admin
deciding; Manager outside the leaving School's scope deciding (404 when not visible at all); stale version (409); empty
rejection or cancellation reason; missing note for Other or a School-initiated request.

## Permissions and defaults

`TEACHER_TRANSFERS`: Admin View, Create, Edit; Director and Manager View, Create, Edit, Approve; Teacher and System none.
