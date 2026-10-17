# Data Model: Mobile Visits and Tasks

The app stores nothing and the server gets no new table. This file lists the shapes the app reads and sends, copied from the 023 and
025 responses (code and the running server, 2026-10-18) and used by the fake server and the request-shape tests. The app computes none
of the values it shows. Dates are ISO (`2026-11-03`) and shown DD/MM/YYYY; instants are ISO and shown in the business time zone.

## Read: visits (spec 023)

### Visit (`GET /marketing/activities`, `GET /marketing/activities/{id}`)

The list answers `{ content: [Visit], drives: [...] }`; only `content` is used.

| Field | Meaning |
| --- | --- |
| `id`, `version` | the visit and its version, sent back on complete, reschedule and cancel |
| `type` | `VISIT`, `CALL`, `PROPOSAL_MEETING`, `FOLLOW_UP` |
| `status` | stored: `PLANNED`, `COMPLETED`, `CANCELLED` |
| `effectiveStatus` | what the Manager sees: the stored status, or `MISSED` for a planned visit whose date has passed |
| `rescheduled` | true when the date was moved at least once |
| `date` | the visit's date (no time of day) |
| `notes` | notes written when planned or completed |
| `outcome`, `followUpOn`, `followUpOverdue` | set when completed; whether the follow-up date has passed |
| `cancelReason` | set when cancelled |
| `prospect` / `school` | `{ id, name }` of the target (one of them) |
| `attendees` | `[{ userId, name }]` of the HLS colleagues attending |
| `attachments` | `[{ fileId, name, sizeBytes, contentType }]` |
| `dateHistory` | `[{ from, to }]` earlier dates |

### Prospect row (`GET /marketing/prospects?query=&page=&size=`)

`{ content: [Prospect], page, size, totalElements }`; a row has `id`, `name`, `board`, `zoneName`, `owner {userId, name}`, `stage`,
`effectiveStage`, `won`, `schoolId`, `contactPerson`. The picker shows `name`, the Zone and `contactPerson`, and sends `id`.

### Colleague (`GET /recruitment/interviewers`)

`[{ userId, name }]`.

## Read: tasks (spec 025)

### Task row (`GET /tasks?view=&page=&size=`)

`{ content: [Row], page, size, totalElements }`. A row: `id`, `type`, `typeLabel`, `title`, `status` (`ASSIGNED`, `ACKNOWLEDGED`,
`IN_PROGRESS`, `COMPLETED`, `VERIFIED`, `CANCELLED`), `dueOn`, `assigneeUserId`, `assigneeName`, `assigneeActive`, `linkedType`,
`linkedId`, `linkedLabel`, `place`, `overdue`, `daysOverdue`, `escalated`.

Views: `today`, `upcoming`, `overdue`, `awaiting`, `done`, `all`.

### Task detail (`GET /tasks/{id}`, and the answer of every task step)

`{ task: Row, description, createdBy, createdByName, escalateAfterDays, source, overdueReason, completionNote, completedAt, verifiedAt,
verifiedByName, escalatedAt, cancelReason, parentTaskId, followUpTaskIds, history, evidence, actions, version }`.

- `history`: `[{ event, actorUserId, actorName, at, before, after, note }]`, oldest first.
- `evidence`: `[{ fileId, name, contentType, sizeBytes, addedAt, addedBy }]`.
- `actions`: `{ acknowledge, start, complete, overdueReason, addEvidence, verify, sendBack, edit, cancel }`, all booleans. The app uses the
  first five and ignores the rest.

### Summary (`GET /tasks/summary`)

`{ today, upcoming, overdue, awaiting, escalated, done }`, counts for the caller.

### Types and assignees

`GET /tasks/types`: `[{ value, label }]` (nine types). `GET /tasks/assignees`: `[{ userId, name }]`; a Manager's list is only themselves.

## Sent

| Call | Body |
| --- | --- |
| Plan a visit | `{ prospectId, type, date, attendeeUserIds?, notes? }` (notes at most 1000 characters; no attendees means the Manager) |
| Complete a visit | `{ outcome, notes?, followUpOn?, version }` (outcome required, at most 1000 characters) |
| Reschedule a visit | `{ date, version }` (a different date) |
| Cancel a visit | `{ reason, version }` (reason required, at most 300 characters) |
| Visit photo | multipart `file` to `/marketing/activities/{id}/attachments` (at most 10 files of 10 MB per visit) |
| Acknowledge, start | no body |
| Overdue reason | `{ reason, followUpOn? }` (reason at least 5 characters) |
| Complete a task | `{ note }` (at least 10 characters) |
| Task evidence | multipart `file` to `/tasks/{id}/evidence` (at most 5 per task) |
| Add a task for oneself | `{ type, title, description?, assigneeUserId, dueOn, place? }` (title 3 to 120 characters) |

## In the app only (not stored)

- **Visit list state**: the month shown, the status filter, the visits loaded for that month.
- **Task list state**: the view, the pages loaded, the page size (25).
- **Drafts**: the complete-visit form (outcome, notes, follow-up date, photos), the plan form, the task completion note and
  reason, the add-task form; kept across a refusal and cleared only after the server confirms.
- **Action state**: idle, sending, done or failed for each button, so a tap during a request is ignored.

## State changes

The app causes the server's own transitions and shows them after the response: a visit goes `PLANNED` to `COMPLETED` or `CANCELLED`
(and may move date while planned); a task goes `ASSIGNED` to `ACKNOWLEDGED` to `IN_PROGRESS` to `COMPLETED`, and later `VERIFIED` or
back to `IN_PROGRESS` on the web. A follow-up date on a completed visit creates a follow-up task on the server.
