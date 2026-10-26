# Data Model: Content Management (migration V95)

## content

| Column | Type | Notes |
| --- | --- | --- |
| id | UUID PK | |
| code | VARCHAR(20) UNIQUE | Content ID, `CNT-000001` from a sequence |
| title | VARCHAR(120) | 3 to 120 |
| category | VARCHAR(60) | plain text |
| subject | VARCHAR(80) | plain text |
| description | VARCHAR(2000) | |
| author_user_id | UUID | user holding Create on Content; no foreign key across modules |
| created_by_user_id | UUID | |
| due_on | DATE NULL | optional |
| status | VARCHAR(12) | DRAFT, ASSIGNED, SUBMITTED, APPROVED, RETURNED |
| current_version | INT | 0 until the first submission |
| last_approved_version | INT NULL | moved only by an approval |
| reviewer_user_id | UUID NULL | of the latest decision |
| approved_on | DATE NULL | |
| task_id | UUID NULL | linked task, Manager authors with a due date only |
| no_reviewer_notified_version | INT NULL | one notice per version |
| version | BIGINT | optimistic lock |
| created_at, updated_at | TIMESTAMPTZ | |

Indexes: (author_user_id, status), (status, due_on), (category), (subject).

## content_version (never updated: trigger refuses UPDATE except the removal fields)

id, content_id, number (UNIQUE with content_id), file_id (UUID into the file store), file_name, content_type, size_bytes,
author_user_id, submitted_by_user_id, note VARCHAR(500) NULL, submitted_at, removed_by, removed_at, removal_reason.

## content_review

id, content_id, version_number, reviewer_user_id, decision (APPROVED, RETURNED), comment VARCHAR(1000) (required for RETURNED), decided_at.

## content_history (append-only, trigger refuses UPDATE and DELETE)

id, content_id, event, actor_user_id, actor_note, before_json, after_json, at.

## Identity (migration-free)

`role_assignment.role` and `permission_matrix.role` carry `CONTENT_CONTRIBUTOR`. Permission module `CONTENT` is a code constant
(`PermissionModule`, 7 characters, fits `VARCHAR(40)`). Seeded rows are written by `PermissionMatrixService` on startup.

## Seeded grants (CONTENT)

| Role | View | Create | Edit | Approve |
| --- | --- | --- | --- | --- |
| Admin | yes | yes | yes | no (eligible) |
| Director | yes | yes | yes | yes |
| Manager | yes | yes | yes | not eligible |
| Content Contributor | yes | yes | yes | not eligible |
| Teacher, System | not eligible | | | |

## Eligibility for Content Contributor on other modules

Not eligible: every module except `CONTENT`, `NOTIFICATIONS` (view own), the dashboard and the profile/account items.
