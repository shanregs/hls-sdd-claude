# Contract: Activity Calendar REST API

Base path `/api/v1/activity-calendar`. Every endpoint needs `ACTIVITY_CALENDAR` View. Teacher and System are refused with 403
before any lookup. There is no POST, PUT, PATCH or DELETE (SC-008). Dates are ISO in the API and DD/MM/YYYY on screen.
Errors follow the existing shape (400 invalid input, 403, 404 out of scope).

Scope: Admin and Director see all Managers. A Manager always gets only their own items (owned, attended or assigned); the
server takes the user from the token. A `managerId` that is not the caller's own answers 404.

## Endpoints

| Method and path | Purpose |
| --- | --- |
| `GET /?month=YYYY-MM&managerId&zoneId&kind&status` | Month calendar: items, summary, sources. `month` defaults to the current business month. Filters combine; the summary follows them. Used by both the grid and the list view. |
| `GET /needs-attention?from&to&managerId&zoneId&kind` | Missed and rescheduled items for the period (up to 93 days; default current month), grouped by Manager. |
| `GET /filters` | Choices for the screen: Managers (Director and Admin; a Manager gets only themselves), Zones, kinds and which kinds are available (Training `available:false` until 024). |

## `GET /` response

```json
{
  "month": "2026-10",
  "today": "2026-10-12",
  "partial": false,
  "sources": [
    {"group": "DRIVES", "available": true, "read": true},
    {"group": "VISITS", "available": true, "read": true},
    {"group": "TASKS", "available": true, "read": true},
    {"group": "TRAINING", "available": false, "read": false, "note": "Training is not included yet"}
  ],
  "summary": {
    "planned": 3, "done": 2, "doneLate": 0, "open": 0, "missed": 1, "cancelled": 0, "rescheduled": 0,
    "byKind": {"SCHOOL_VISIT": {"planned": 1, "done": 1}, "CAMPUS_DRIVE": {"planned": 1, "done": 1}, "TASK": {"planned": 1, "missed": 1}},
    "byManager": [{"userId": "...", "name": "...", "planned": 3, "done": 2, "missed": 1, "cancelled": 0, "rescheduled": 0}]
  },
  "items": [
    {"key": "SCHOOL_VISIT:...:2026-10-05", "activityId": "...", "kind": "SCHOOL_VISIT", "subKind": null,
     "date": "2026-10-05", "status": "DONE", "doneLate": false, "completedOn": "2026-10-05",
     "rescheduled": false, "originalDate": null, "daysMoved": null, "daysLate": null,
     "people": [{"userId": "...", "name": "...", "zoneName": "..."}],
     "zoneId": "...", "zoneName": "...", "place": "...",
     "link": {"screen": "MARKETING", "path": "/marketing/calendar"}}
  ]
}
```

- `summary.byManager` is present for Director and Admin only. Totals count each activity once; `byManager` counts a shared
  visit for each Manager.
- `link` is omitted when the caller lacks the owning screen's View permission (`MARKETING`, `RECRUITMENT`, `TASKS`).
- Items never carry notes, outcome text, evidence, attachments or incentive data (FR-020).
- A source that cannot be read: its group has `read:false` and a `note` ("Tasks could not be loaded"), `partial` is true and
  the counts are marked partial on screen; the other groups are unaffected.
- An empty month returns `items: []` and zero counts with 200.

## `GET /needs-attention` response

```json
{
  "from": "2026-10-01", "to": "2026-10-31", "partial": false,
  "groups": [
    {"userId": "...", "name": "...", "missed": 3, "rescheduled": 0,
     "entries": [{"item": { "...CalendarItem..." }, "reasons": ["MISSED"], "daysLate": 4, "daysMoved": null}]}
  ]
}
```

Groups hold only Managers with at least one entry, sorted by name; entries oldest date first. An item rescheduled twice is one
entry showing both moves in `originalDate` and the current date.

## Errors

| Case | Result |
| --- | --- |
| Teacher or System | 403 |
| Manager passes another `managerId` | 404 |
| Range over 31 days (calendar) or 93 days (needs attention), bad month, unknown kind or status | 400 |
| All sources fail | 200 with empty items, `partial: true`, every group `read:false` |
