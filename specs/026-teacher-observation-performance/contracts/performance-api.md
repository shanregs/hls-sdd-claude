# Contract: Teacher Performance REST API

Base path `/api/v1/teacher-performance`. Every endpoint needs a grant on `TEACHER_PERFORMANCE` (View for reads, Create for
new records, Edit for changes) and applies the data scope of the plan (Manager: Teachers placed today at their Schools and
records they authored). Teacher and System are refused with 403 before any lookup. A record outside the caller's scope answers
404. Errors follow the existing shape (400 invalid input, 404, 409 stale version or invalid transition). Writes take `version`
where the record is editable. Dates are ISO in the API and DD/MM/YYYY on screen.

## Observations

| Method and path | Grant | Purpose |
| --- | --- | --- |
| `GET /observations` | View | List current observations; filters `zoneId, schoolId, managerId, teacherId, result, status, from, to, principalDeclined`; page, counts |
| `GET /observations/{id}` | View | One observation with ratings, attachments, permission, `actions`; Admin and Director may add `?versions=true` |
| `POST /observations` | Create | Start a Draft for a Teacher: `teacherId, observedOn?` returns the form prefilled (School, observer, Principal name from contacts) |
| `PUT /observations/{id}` | Edit | Save a Draft (observer only): class, subject, duration, ratings, texts, permission |
| `POST /observations/preview` | Create | Returns average and suggested result for a set of ratings; stores nothing |
| `POST /observations/{id}/submit` | Create | Draft to Submitted; opens or joins an issue when the result is Needs improvement or Unsuitable; completes the Observation task when the observer is the assignee |
| `POST /observations/{id}/not-held` | Create | Draft to Not held: `reason, text?`; no ratings; the task stays open |
| `POST /observations/{id}/correct` | Edit | New version with `reason` (required); earlier version stays |
| `POST /observations/{id}/attachments` | Edit | Multipart upload, up to 5 |
| `GET /observations/{id}/attachments/{fileId}` | View | Download as attachment after the visibility check |
| `DELETE /observations/{id}/attachments/{fileId}` | Edit | Remove a file from a Draft, with reason |

Observation `actions`: `edit` (Draft and observer), `submit`, `notHeld`, `correct` (Submitted, author or Admin or Director),
`attach`.

## Principal feedback

| Method and path | Grant | Purpose |
| --- | --- | --- |
| `GET /feedback` | View | List; filters `zoneId, schoolId, managerId, teacherId, category, actionRequired, status, from, to` |
| `GET /feedback/{id}` | View | One record; `?versions=true` for Admin and Director |
| `POST /feedback` | Create | `teacherId, principalName, givenOn, category, comments, actionRequired, observationId?`; completes the open Principal Feedback task when the recorder is the assignee; returns `offerIssue` when action is required |
| `POST /feedback/{id}/correct` | Edit | New version with `reason` |
| `POST /feedback/{id}/withdraw` | Edit | `reason`; recorder, Admin or Director only |
| `POST /feedback/{id}/issue` | Create | Open an issue from this feedback, or join the Teacher's open one |
| `POST /feedback/{id}/plan` | Create | Plan an observation from this feedback (reason School feedback, due the same day by default) |

## Plans (planned observations)

| Method and path | Grant | Purpose |
| --- | --- | --- |
| `GET /plans` | View | Planned observations: open task per Teacher with date, reason, days overdue, last result |
| `POST /plans` | Create | `teacherId, plannedOn, reason, reasonText?`; Admin or Director for any ACTIVE placed Teacher, Manager for their Teachers; returns the existing plan with `created=false` if one is open |
| `PUT /plans/{id}` | Edit | Change date or reason of an open plan |
| `DELETE /plans/{id}` | Edit | Cancel an open plan with `reason` |

## Issues

| Method and path | Grant | Purpose |
| --- | --- | --- |
| `GET /issues` | View | List; filters as observations plus `status`; lists Open issues and Referred through `status` |
| `GET /issues/{id}` | View | Issue with links, corrective actions, round number, referral, `actions` |
| `POST /issues/{id}/corrective-actions` | Edit | `discussed, requiredChange, followUpOn (1 to 60 days)`; moves to Under correction; creates the follow-up task |
| `POST /issues/{id}/escalate` | Edit | `note`; tells the Directors (history only, no status change) |
| `POST /issues/{id}/referral` | Edit | `recommendation (TRANSFER or REPLACE), reason (>= 20)`; status Referred; notifies Directors |
| `POST /issues/{id}/close` | Edit | Admin or Director: `outcome (CANCELLED or CONTINUE), reason` |

## Teacher page and summary

| Method and path | Grant | Purpose |
| --- | --- | --- |
| `GET /teachers/{teacherId}` | View | Observations, feedback, issues, referrals in date order, latest result first, open plan |
| `GET /summary` | View | Counts per Manager and per Zone for `from`, `to` (done, planned, overdue, open issues, referrals, resolved); same data 028 reads through the module interface |

## Notifications and tasks (side effects)

Planning or re-planning creates or updates one Observation task and sends `OBSERVATION_PLANNED` to the Manager. A corrective
action creates one follow-up Observation task. A referral sends `REFERRAL_RECORDED` to every active Director. Task overdue and
escalation come from spec 025.
