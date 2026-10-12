# Contract: Tasks HTTP API

All under `/api/v1/tasks`, module `TASKS`. Errors use `{"reason": "..."}`: 400 invalid, 403 not allowed, 404 not found
(including another Manager's task), 409 conflict or stale version.

| Method and path | Needs | Notes |
| --- | --- | --- |
| GET `/` `?view=today|upcoming|overdue|awaiting|done|all&assigneeUserId&type&status&page&size` | View | Manager: own tasks only. Returns `{content,page,size,totalElements}`. Each row: id, type, title, status, dueOn, assignee id and name, linked type, id and label, place, overdue, escalated, daysOverdue. |
| GET `/summary` | View | `{today, upcoming, overdue, awaiting, escalated, done}` for the caller's scope. |
| GET `/types` | View | The nine types with labels. |
| GET `/assignees` | Create | Active Managers: Admin and Director all; Manager only themselves. |
| POST `/` | Create | Body: type, title, description, assigneeUserId, dueOn, escalateAfterDays, linkedType, linkedId, linkedLabel, place. A Manager may only assign to self. Due date not in the past. 201. |
| GET `/{id}` | View | Detail with history, evidence, parent and follow-up links, allowed actions. |
| PUT `/{id}` | Edit | Admin and Director: title, description, assignee, dueOn, escalateAfterDays, place, `version`. Reassign notifies. |
| POST `/{id}/acknowledge` | Edit | Assignee only. |
| POST `/{id}/start` | Edit | Assignee only; from Acknowledged. |
| POST `/{id}/overdue-reason` | Edit | Assignee; `{reason (>=5), followUpOn?}`; creates the Follow-up task when a date is given. |
| POST `/{id}/complete` | Edit | Assignee; `{note (>=10)}`; from Acknowledged or In progress; overdue needs a reason first. |
| POST `/{id}/evidence` | Edit | Assignee; multipart `file`; max 5; while open or Completed. 201. |
| GET `/files/{fileId}` | View | Download as attachment if the task is visible. |
| POST `/{id}/verify` | Approve | Not the assignee; Completed to Verified. |
| POST `/{id}/send-back` | Approve | `{reason}`; Completed to In progress. |
| POST `/{id}/cancel` | Edit | Admin and Director; `{reason}`; not Completed or Verified. |

Statuses: ASSIGNED, ACKNOWLEDGED, IN_PROGRESS, COMPLETED, VERIFIED, CANCELLED. Types: SCHOOL_VISIT, FOLLOW_UP,
CONTENT_SUBMISSION, RECRUITMENT, TRAINING, TEACHER_ASSIGNMENT, OBSERVATION, PRINCIPAL_FEEDBACK,
SUBSTITUTE_ARRANGEMENT.
