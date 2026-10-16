# HTTP contract: School Working Days, year entry

Added to the base path `/api/v1/school-working-days` of
[spec 037](../../037-school-monthly-working-days/contracts/school-working-days-api.md). JWT required. Same error shape:
`400` invalid input, `403` no permission, `404` not found. Permission module `SCHOOL_WORKING_DAYS`. Dates are ISO; the
screen shows DD/MM/YYYY and month names.

## GET `/year?schoolId=&year=`  (View)

Both parameters required. `year` must be within the current year minus 5 to plus 5 (400 naming the allowed years
otherwise). The School must exist (400 if unknown) and be in the caller's scope (403 outside it).

200:

```json
{
  "schoolId": "uuid", "schoolName": "Greenfield", "zoneName": "North", "year": 2027,
  "months": [
    {
      "month": 1, "daysInMonth": 31, "calendarDerived": 26,
      "id": "uuid", "workingDays": 26.0, "version": 2,
      "updatedBy": "Asha R", "updatedAt": "2027-01-04T10:15:00Z"
    },
    {
      "month": 2, "daysInMonth": 28, "calendarDerived": 24,
      "id": null, "workingDays": null, "version": null, "updatedBy": null, "updatedAt": null
    }
  ]
}
```

Always twelve entries in month order. A month with no figure has `id`, `workingDays`, `version`, `updatedBy` and
`updatedAt` all `null`; `calendarDerived` is present for every month.

## PUT `/year`  (View, and Create or Edit)

Body (only the months the user changed):

```json
{
  "schoolId": "uuid", "year": 2027,
  "months": [
    { "month": 2, "workingDays": 24.0, "version": null },
    { "month": 3, "workingDays": 25.5, "version": 2 }
  ]
}
```

`version` is the value loaded for that month, `null` when the screen loaded no figure for it.

**Request-level refusals** (nothing is saved):

- `403` no View, or neither Create nor Edit, or the School is outside the caller's scope.
- `400` unknown School; `year` outside the window (names the allowed years); `month` outside 1 to 12; the same `month`
  twice; zero entries or more than 12; a missing field.

**200** otherwise, even if some or all months were not saved:

```json
{
  "created": 1, "updated": 0, "unchanged": 0, "notSaved": 1,
  "months": [
    {
      "month": 2, "status": "CREATED",
      "current": { "month": 2, "daysInMonth": 28, "calendarDerived": 24, "id": "uuid", "workingDays": 24.0,
                   "version": 0, "updatedBy": "Asha R", "updatedAt": "2027-02-01T10:20:00Z" }
    },
    {
      "month": 3, "status": "NOT_SAVED", "reason": "STALE",
      "message": "This figure was changed by someone else. The current figure is shown.",
      "current": { "month": 3, "daysInMonth": 31, "calendarDerived": 27, "id": "uuid", "workingDays": 27.0,
                   "version": 3, "updatedBy": "Ravi K", "updatedAt": "2027-02-01T10:19:00Z" }
    }
  ]
}
```

Per-month `status`: `CREATED`, `UPDATED`, `UNCHANGED`, `NOT_SAVED`. For `NOT_SAVED`, `reason` is one of:

| reason | when | `current` |
|---|---|---|
| `INVALID_FIGURE` | outside 0 to the days in the month, or not a half step; the message names the allowed range | the stored state (or an empty month) |
| `STALE` | the stored figure changed since it was loaded and now differs from the typed one | the stored row |
| `ALREADY_EXISTS` | no figure when loaded, one exists now and differs from the typed one | the stored row |
| `ROW_DELETED` | a figure existed when loaded and is gone; never recreated | an empty month |
| `NOT_PERMITTED` | the caller lacks Create (no figure) or Edit (stored figure) | the stored state |

`UNCHANGED` means the stored figure already equals the typed one (a retry, or someone entered the same figure) and
nothing was written. `current` is the month's state after the request, with its new `version`, so the screen can save
again without reloading. Months come back in the order of the request.

**Effects**: each `CREATED` or `UPDATED` month is one transaction holding the row, one history row (`CREATE` or `EDIT`)
and one central audit event, as from the grid. `UNCHANGED` and `NOT_SAVED` write nothing. Repeating an identical request
writes nothing the second time.

## Permission summary

| Endpoint | Needs |
|---|---|
| GET `/year` | View |
| PUT `/year` | View and (Create or Edit) for the request; Create for each new month, Edit for each stored month |

A user without View gets 403 on both. System never has access.
