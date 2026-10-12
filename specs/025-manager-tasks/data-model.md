# Data Model: Manager Tasks (migration V45)

## task

| Column | Type | Notes |
| --- | --- | --- |
| id | uuid pk | |
| type | varchar(30) | one of the nine task types |
| title | varchar(120) | 3 to 120 |
| description | varchar(2000) | optional |
| assignee_user_id | uuid | an active Manager's user; no foreign key |
| created_by | uuid | null when created by the system |
| due_on | date | |
| escalate_after_days | int | 1 to 30, default 3 |
| status | varchar(20) | ASSIGNED, ACKNOWLEDGED, IN_PROGRESS, COMPLETED, VERIFIED, CANCELLED |
| linked_type / linked_id / linked_label | varchar(40) / uuid / varchar(160) | optional link to a record |
| place | varchar(160) | optional (C1) |
| source / source_key | varchar(40) / varchar(120) | MANUAL, or the calling module; key for idempotency |
| parent_task_id | uuid | the overdue task a follow-up was raised from |
| overdue_reason | varchar(500) | latest reason |
| completion_note | varchar(2000) | |
| completed_at / verified_at / verified_by | timestamptz / timestamptz / uuid | |
| overdue_notified_for | date | the due date an overdue notice was sent for |
| escalated_at | timestamptz | |
| cancel_reason | varchar(300) | |
| version, created_at, updated_at | | optimistic lock |

Indexes: (assignee_user_id, due_on) for the lists; (status, due_on) for the job; partial unique
(source, source_key) where source_key is not null and status in the open statuses.
Checks: status and type values, title length, escalation range.

## task_history (append-only)

id, task_id, event (CREATED, REASSIGNED, DUE_CHANGED, EDITED, ACKNOWLEDGED, STARTED, COMPLETED, OVERDUE_REASON,
EVIDENCE_ADDED, VERIFIED, SENT_BACK, CANCELLED, OVERDUE_NOTICE, ESCALATED), actor_user_id (null for system), at,
before_value, after_value, note. A trigger refuses UPDATE and DELETE.

## task_evidence

id, task_id, file_id (unique), added_by, added_at.

## notification

`notification_type_check` is replaced by `type ~ '^[A-Z_]+$'` (see research R6).
