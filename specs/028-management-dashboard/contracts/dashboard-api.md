# Contract: Dashboard REST API

Base path `/api/v1/dashboard`. Client-neutral: the web uses it now and a later mobile spec can reuse it unchanged (FR-018).
Any signed-in user may call it; what comes back is the tiles whose grant the caller holds, scoped to the caller. Teacher and
System receive an empty manifest. Anything not in the caller's manifest answers 404 (a figure is never sent, FR-020). Nothing is
written and nothing is audited. Errors follow the existing shape (400 invalid input, 401, 404). Dates are ISO in the API.

| Method and path | Purpose |
| --- | --- |
| `GET /` | The manifest: areas in order, each with the tiles the caller may see (definitions only, no figures), the filters allowed (`zones` the caller may pick, default month), `inbox` available or not |
| `GET /areas/{area}?zoneId=&period=YYYY-MM` | The results of every tile in one area, computed now |
| `GET /areas/{area}/tiles/{key}?zoneId=&period=` | One tile again (the retry after an `ERROR`) |
| `GET /managers?zoneId=&period=&sort=&dir=` | The Manager-by-Manager table (same as the comparison tile's rows, sortable); org-wide callers only |
| `GET /inbox?kind=&page=&size=` | The approvals waiting for the caller, oldest first, with totals per kind |

## Manifest

```json
{ "areas": [ { "area": "MANAGERS", "title": "Managers",
    "tiles": [ { "key": "managers.tasks.overdue", "label": "Overdue tasks", "periodKind": "TODAY",
                 "zoneAware": true, "link": { "path": "/tasks", "query": { "view": "overdue" } } } ] } ],
  "filters": { "zones": [ { "id": "...", "name": "..." } ], "defaultPeriod": "2026-10" },
  "inbox": true }
```

## Area response

```json
{ "area": "MANAGERS", "takenAt": "2026-10-21T06:30:12Z",
  "tiles": [ { "key": "managers.tasks.overdue", "state": "OK", "organizationWide": false,
               "values": [ { "label": "Overdue", "value": 7, "unit": "COUNT", "link": { "path": "/tasks", "query": { "view": "overdue" } } } ],
               "note": null },
             { "key": "managers.noCheckIn", "state": "NOT_WORKING_DAY", "values": [], "note": "Weekly off" },
             { "key": "managers.pendingRequests", "state": "ERROR", "values": [], "note": "Could not be read" } ] }
```

`state` is one of `OK`, `NO_DATA`, `NOT_AVAILABLE`, `NOT_WORKING_DAY`, `ERROR`. `values` is empty unless `state` is `OK`.
Amounts are numbers in rupees with `unit: "RUPEES"`; the screen formats them. `organizationWide` is true when a Zone was chosen
and the tile cannot be split by Zone. A period is ignored by tiles whose `periodKind` is `TODAY`.

## Rules

- A `zoneId` the caller cannot reach, an unknown area or an unknown tile answers 404; a bad `period` answers 400.
- A tile whose source throws or exceeds 2.5 seconds is returned as `ERROR` in an otherwise normal 200; the area is never
  failed for one tile.
- Payroll tiles are present only for callers holding View on Payroll; the response for anyone else has no payroll key, amount
  or text (SC-007).
- A Manager-only caller gets neither `/managers`, nor the payroll area, nor the inbox (403 on `/managers` and `/inbox`).
- Inbox items never include a request the caller made; each has `kind, itemId, requester, subject, askedOn, ageDays, link`.
  `kind` is absent from the list and the totals when the caller lacks that module's Approve or Process grant.

## Inbox response

```json
{ "totals": { "MANAGER_LEAVE": 1, "PAYROLL_RUN": 1 }, "takenAt": "...", "page": 0, "size": 50, "total": 2,
  "items": [ { "kind": "MANAGER_LEAVE", "itemId": "...", "requester": "Asha K", "subject": "Casual leave 3 days",
               "askedOn": "2026-10-18", "ageDays": 3, "link": { "path": "/manager-leave/...", "query": {} } } ] }
```
