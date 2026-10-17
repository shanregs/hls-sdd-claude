# Contract: Server calls the app makes (specs 023 and 025, unchanged)

The full server contracts are `specs/023-school-marketing-mou/contracts/marketing-api.md` and
`specs/025-manager-tasks/contracts/tasks-api.md`; this file says which calls the app makes, when, and what it does with each
answer. Every call carries the audit headers of spec 018 (the position, or the reason there is none); no call takes a separate
location reading. Errors use the house format `{ "reason": "..." }`; the app shows `reason` as it is (400, 403, 404, 409), sends the
Manager to sign-in on 401 after one renewal, and says "no connection" when the request cannot be made.

## Visits (`/api/v1/marketing`, module `MARKETING`)

| When | Call | Needs | Body or answer used |
| --- | --- | --- | --- |
| Visits screen opens, month changes, refresh | `GET /activities?from=&to=&mine=true` | View | `content` (visits); `drives` ignored |
| A visit is opened or reloaded after an action | `GET /activities/{id}` | View | one visit |
| Complete visit | `POST /activities/{id}/complete` | Edit | `{outcome, notes?, followUpOn?, version}`; answer: the visit |
| After a successful completion, once per picked photo | `POST /activities/{id}/attachments` (multipart `file`) | Create | `{fileId, name, sizeBytes, contentType}` |
| Plan visit | `POST /activities` | Create | `{prospectId, type, date, attendeeUserIds?, notes?}`; answer: the visit |
| Reschedule | `POST /activities/{id}/reschedule` | Edit | `{date, version}`; answer: the visit |
| Cancel | `POST /activities/{id}/cancel` | Edit | `{reason, version}`; answer: the visit |
| Target chooser | `GET /prospects?query=&page=&size=` | View | `content` of prospects |
| Colleague chooser | `GET /recruitment/interviewers` (module `RECRUITMENT`) | View | `[{userId, name}]` |
| Attachment thumbnail | `GET /files/{fileId}` | View | image bytes with type, length and file name headers |
| Home card | `GET /activities?from=<today>&to=<today>&mine=true` | View | count of `effectiveStatus = PLANNED` |

## Tasks (`/api/v1/tasks`, module `TASKS`)

| When | Call | Needs | Body or answer used |
| --- | --- | --- | --- |
| Tasks screen opens, view changes, Load more | `GET /?view=&page=&size=25` | View | `{content, page, size, totalElements}` |
| A task is opened or reloaded | `GET /{id}` | View | the detail, with `actions` |
| Acknowledge | `POST /{id}/acknowledge` | Edit | no body; answer: the detail |
| Start | `POST /{id}/start` | Edit | no body; answer: the detail |
| Give an overdue reason | `POST /{id}/overdue-reason` | Edit | `{reason, followUpOn?}`; answer: the detail |
| Complete | `POST /{id}/complete` | Edit | `{note}`; answer: the detail |
| Add evidence | `POST /{id}/evidence` (multipart `file`) | Edit | answer: the detail |
| Evidence thumbnail | `GET /files/{fileId}` | View | image bytes with type, length and file name headers |
| Add a task for oneself | `GET /assignees`, `GET /types`, then `POST /` | Create | `{type, title, description?, assigneeUserId, dueOn, place?}`; answer: the detail |
| Home card | `GET /summary` | View | `{today, overdue, ...}` |

## Rules the app relies on and does not repeat

- Visits: a visit needs an outcome to be completed (400 otherwise); a follow-up date cannot precede the visit; a stale `version` is 409;
  only a planned visit can be completed, moved or cancelled (409); a move to the same date is 409; a cancel needs a reason (400);
  at most 10 files per visit; a target outside the Manager's Zones is 404.
- Tasks: only the assignee acts; acknowledge from Assigned, start from Acknowledged, an overdue task needs a reason before completion,
  completion needs a note of at least 10 characters, at most 5 evidence files; a task of another Manager is 404.
- Files: only jpg, png, pdf, docx, xlsx and txt are accepted, up to 10 MB; the phone sends photos only.
- Teacher, Admin (without a Manager role) and System are refused with 403 on every call here; the menu never offers the screens to them.

## Not called

Prospect, pipeline, proposal, win, owner, review, dashboard and settings endpoints of 023; update, verify, send-back and cancel of 025;
removing a visit attachment.
