# Research: Manager Tasks

## R1. Overdue is derived

Decision: no stored overdue status. Queries compare `due_on` to the business date and exclude closed statuses. The
deadline job only records that a notice was sent (`overdue_notified_for` = the due date notified). A moved due date
that becomes overdue again for a new date can be notified again; the same date never twice.
Alternative rejected: an OVERDUE status; it goes stale between job runs and needs repair on date changes.

## R2. Escalation

Decision: `escalate_after_days` per task (1 to 30, default 3). The job escalates open tasks where
`today - due_on >= escalate_after_days` and `escalated_at is null`, setting `escalated_at` and publishing
`TaskEscalated`. Recipients are all active Directors. Moving the due date forward does not clear `escalated_at`
(history of what happened stays true).

## R3. Idempotency of C2

Decision: `source` and `source_key` columns with a partial unique index where status is open; the service looks up
first and returns the existing task. The index is the race backstop (a loser re-reads).

## R4. Assignee validation

Decision: assignee must have a Manager record and active user, checked through `ManagerQueries.managerByUserId` and
`AppUserRepository`. Names for lists come from the Manager display name.

## R5. Evidence

Decision: files stored through `files.api.FileStore` with owner type `TASK`; `task_evidence` links task and file so
the task module can list, count (max 5) and gate downloads. Removal is out of scope for version 1 (Admin or Director
could use a later spec); evidence is therefore append-only.

## R6. Notification type constraint

Decision: V45 replaces `notification_type_check` with `type ~ '^[A-Z_]+$'`. Spec 010's explicit list is edited by
every spec that adds events; with several specs in parallel, last migration would drop the others' types. The Java
enum remains the source of truth.

## R7. Verification rule

Decision: Approve on `TASKS` and not the assignee. Because Manager is ineligible for Approve this is Admin and
Director unless the matrix is edited; the "not the assignee" rule holds even then.

## R8. Scope for Managers

Decision: Manager sees tasks where `assignee_user_id` = caller. Zone scope is not used because a task is personal to a
Manager; Admin and Director are org-wide. Access model data scope: `OWN` is not an existing meaning for Manager in
the model, so the entry is `ASSIGNED` for Manager (their assigned tasks) and `ORG_WIDE` for Admin and Director.

## R9. 023 follow-up listener

Decision: `FollowUpListener` handles `FollowUpScheduled` with source `MARKETING_FOLLOW_UP` and source key = activity
id; assignee = prospect owner; skipped with a log line when the owner is not an active Manager. Runs after commit
of the publisher's transaction (`@TransactionalEventListener`-free plain `@EventListener` inside the transaction, as
the notification listeners do) and must not fail the visit completion: failures are caught and logged.

## R10. Planned-activity shape

Decision: `tasks.api.PlannedActivity` has the same fields as `recruitment.api.PlannedActivity` (kind, id, ownerUserId,
date, place, status) but is its own record so `tasks` does not depend on `recruitment` for a type; consumers map both.
