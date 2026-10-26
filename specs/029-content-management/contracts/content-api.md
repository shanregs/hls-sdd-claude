# Contract: Content HTTP API

All under `/api/v1/content`, module `CONTENT`. Errors use `{"reason": "..."}`: 400 invalid, 403 not allowed, 404 not found
(including content outside the caller's scope), 409 conflict or stale version, 413 over the limit for the file type.

| Method and path | Needs | Notes |
| --- | --- | --- |
| GET `/` `?view=mine|all&authorUserId&category&subject&status&from&to&page&size` | View | Admin and Director: all; others: authored or submitted only. Row: id, code, title, category, subject, author id and name, status, currentVersion, dueOn, overdue, updatedAt. |
| GET `/summary` | View | `{draft, assigned, submitted, approved, returned, overdue}` in the caller's scope (feeds the Contributor dashboard). |
| GET `/authors` | Create | Active users holding Create on Content (id, name, roles). Admin and Director: all; others only themselves. |
| GET `/limits` | View | The accepted file types and the limit for each (the FR-005 table). |
| POST `/` | Create | title, category, subject, description, authorUserId (default self; another author needs Admin or Director), dueOn?. Creates a task when the author is an active Manager and dueOn is set. 201. |
| GET `/{id}` | View | Detail: record, versions, reviews, history, linked task, `allowedActions`. |
| PUT `/{id}` | Edit | Author, Admin, Director: title, category, subject, description, dueOn, authorUserId (Admin, Director), `version`. |
| POST `/{id}/versions` | Edit | multipart `file` + optional `note`; author, or another user with Edit when permitted (Admin, Director). Required file; 201 with the version. Status becomes Submitted; completes the task. 413 over the type's limit. |
| GET `/{id}/versions/{number}/file` | View | Streamed attachment download. |
| DELETE `/{id}/versions/{number}/file` | Edit | Admin and Director; `{reason}`; hides the file, keeps the row. |
| POST `/{id}/approve` | Approve | Not author, not the submitter of the current version; Submitted to Approved; verifies the task. |
| POST `/{id}/return` | Approve | `{comment}` required; Submitted to Returned; sends the task back. |

Statuses: DRAFT, ASSIGNED, SUBMITTED, APPROVED, RETURNED. File types: mp4, mp3, m4a, wav, pdf, docx, xlsx.

Related changes in other APIs: `GET /api/v1/users/roles` (spec 004) and the create/update user bodies accept `CONTENT_CONTRIBUTOR`;
the access-model response lists `CONTENT` menus; the permission matrix response has a sixth role column.
