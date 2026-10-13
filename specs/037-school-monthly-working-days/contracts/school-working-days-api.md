# HTTP contract: School Working Days

Base path `/api/v1/school-working-days`. JWT required. Every endpoint checks the permission itself, independent of the
menu, and fails closed. Permission module: `SCHOOL_WORKING_DAYS`. A Manager granted View sees only their assigned
Schools; Admin and Director see all. Dates are ISO in the API; the screen shows DD/MM/YYYY and month names.

Common error shape is the one the other master-data controllers use (`MasterDataExceptionAdvice`): `400` invalid input
(message names the allowed range), `403` no permission, `404` not found, `409` conflict.

## GET `/`  (View)

Query: `year` (default current year), `schoolId`, `zoneId`, `q` (School name contains), `sort`
(`school|zone|month|workingDays|updatedAt`, `,asc|,desc`), `page` (0-based), `size` (default 25, max 100).

200:

```json
{
  "items": [
    {
      "id": "uuid", "schoolId": "uuid", "schoolName": "Greenfield", "zoneId": "uuid", "zoneName": "North",
      "year": 2027, "month": 3, "workingDays": 26.0, "daysInMonth": 31, "calendarDerived": 27,
      "updatedBy": "Asha R", "updatedAt": "2027-02-01T10:15:00Z", "version": 2
    }
  ],
  "total": 132, "page": 0, "size": 25
}
```

Filters persist across pages (the same query repeats). Empty list when nothing matches.

## POST `/`  (Create)

Body: `{ "schoolId": "uuid", "year": 2027, "month": 3, "workingDays": 26.0 }`

- 201 with the grid row.
- 400: figure outside 0 to days in the month (message names it, e.g. "0 to 31 in half days"), not a half step, year
  outside the window (names the allowed years), unknown School.
- 409 `DUPLICATE`: `{ "code": "DUPLICATE", "existing": { <grid row> } }` so the screen can offer to edit it.

## PUT `/{id}`  (Edit)

Body: `{ "workingDays": 25.5, "version": 2 }`

- 200 with the updated grid row; one history row (`EDIT`) and one audit entry with old and new figure.
- 400 as above.
- 409 `STALE`: `{ "code": "STALE", "current": { <grid row> } }` when the version differs (FR-021).
- 404 `ROW_DELETED`: the row no longer exists; nothing is recreated or audited.

## DELETE `/{id}?version=2`  (Delete)

- 204; history row `DELETE` keeps the last figure; one audit entry.
- 409 `STALE` and 404 `ROW_DELETED` as for edit.

## GET `/{id}/history`  (View)

Entries for the row's School, year and month, newest first:
`[{ "action": "EDIT", "oldValue": 26.0, "newValue": 25.5, "actorName": "Asha R", "actorRoles": "ADMIN", "occurredAt": "…" }]`
Includes a `DELETE` entry if the row has since been deleted and recreated. If the row no longer exists the call returns
404 `ROW_DELETED` (history stores no row id); use the next endpoint, which works by key.

## GET `/history?schoolId=&year=&month=`  (View)

The same entries by key, usable when the row no longer exists.

## GET `/deleted?schoolId=&year=`  (View)

The Deleted rows view (FR-023): each deletion for the School and year, newest first:
`[{ "month": 3, "lastValue": 26.0, "deletedByName": "Asha R", "deletedAt": "…" }]`. A month whose row was later
recreated is still listed (it records a past deletion).

## POST `/copy`  (Create)

Body: `{ "schoolId": "uuid", "fromYear": 2026, "toYear": 2027 }`

200:

```json
{
  "created": [ { "month": 1, "workingDays": 26.0 } ],
  "skipped": [
    { "month": 2, "workingDays": 29.0, "reason": "EXCEEDS_DAYS", "daysInMonth": 28 },
    { "month": 3, "workingDays": 26.0, "reason": "EXISTS" }
  ]
}
```

- 400: `fromYear == toYear`, target year outside the window, unknown School, source year has no rows.
- One audit entry and one `COPY` history row per created row; existing rows are never changed.

## GET `/schools`  (View)

Options for the add and copy dialogs: `[{ "id", "name", "zoneId", "zoneName", "active" }]`, limited to the caller's
scope. Active and inactive Schools are both listed (rows for a deactivated School stay editable).

## Permission summary

| Endpoint | Needs |
|---|---|
| GET list, history, deleted, schools | View |
| POST create, POST copy | Create |
| PUT edit | Edit |
| DELETE | Delete |

A user without View who calls any endpoint gets 403. System never has access.
