# Contract: Server calls the app makes (spec 032, unchanged)

All paths are under `/api/v1/manager-attendance/me`. The full server contract is
`specs/032-manager-attendance/contracts/manager-attendance-api.md`; this file says which calls the app makes, when, and
what it does with each answer. Every call carries the audit headers of spec 018 (the position, or the reason there is none).
Errors use the house format `{ "reason": "..." }`; the app shows `reason` as it is (400, 403, 404 and 409), sends the
Manager to sign-in on 401 after one renewal, and says "no connection" when the request cannot be made.

| When | Call | Needs | Body or answer used |
| --- | --- | --- | --- |
| Check-in screen and Home load or refresh | `GET /today` | View | the whole Today view (data-model.md) |
| Tap Check in | `POST /check-in` | Create, and a **fresh position** | body: day type, purpose, activities, position; answer: the day |
| After a successful check-in with a picked photo | `POST /days/{date}/photo` (multipart `file`) | Create | answer: the day (with `hasPhoto`) |
| Photo thumbnail and details | `GET /days/{date}/photo` | View | image bytes; type, size and file name from the headers |
| Tap Check out | `POST /check-out` | Create, and a **fresh position** | body: position; answer: the day |
| Tap Complete check-out on a flagged day | `POST /days/{date}/complete-check-out` | Create | body: `time` (HH:mm) and `reason` |
| My Month load, month change | `GET /months/{yyyy-MM}` | View | the month (data-model.md) |
| Ask to add a check-in | `POST /regularizations` | Create | the request body of data-model.md; answer: the request |
| Check-in requests list | `GET /regularizations` | View | the Manager's requests |
| Cancel a Pending check-in request | `DELETE /regularizations/{id}` | Delete | 204 |
| My Leave load | `GET /leave/types`, `GET /leave` | View | the types and the Manager's requests |
| Request leave | `POST /leave` | Create | body: type, dates, half days, reason; answer: the request |
| Cancel a Pending leave request | `DELETE /leave/{id}` | Delete | 204 |

## Rules the app relies on and does not repeat

- A position is **required** for check-in and check-out: the server answers 400 without one. The app therefore never sends
  either call without a reading, and never judges its accuracy.
- A Field day needs at least one activity or a purpose; an activity must be the Manager's and dated that day. The app checks
  only that one of the two is present (because the refusal would otherwise come after the position wait); the server decides
  the rest.
- One check-in a day (409 for a second); a locked month (409); approved full-day leave, a missing joining date, a day outside
  employment (400 with the reason). One photo per check-in (409 for a second); jpg or png only (400).
- Leave: dates reversed, a range without a working day, a date before joining, no reason, a half day on a middle day
  (400); an overlap with another Pending or Approved request (409). A cancel works on a Pending request only.
- Forgotten check-in: a future day or time, a day with a check-in, a pending request for the date, a missing reason (400); a
  locked month (409).
- Teacher, Admin (without the Manager role), Director and System are refused with 403 on every call here; the menu never
  offers the screens to them.

## Not called

The Admin and Director grid, corrections, locks, settings and decisions (`/grid`, `/managers/**`, `/months/**`, `/settings`,
`/leave/{id}/approve`, and so on) stay on the web.
