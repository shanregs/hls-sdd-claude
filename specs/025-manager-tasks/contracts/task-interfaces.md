# Contract: Module interfaces and events (package `com.hls.tasks.api`)

## C2 `TaskCreation`

```java
TaskRef createFollowUp(FollowUpRequest request);
record FollowUpRequest(TaskType type, String title, String description, UUID assigneeUserId, LocalDate dueOn,
    Integer escalateAfterDays /* null = 3 */, String linkedType, UUID linkedId, String linkedLabel, String place,
    String source /* calling module key */, String sourceKey /* idempotency */, UUID createdByUserId /* null = system */) {}
record TaskRef(UUID id, boolean created, String status) {}
```

Rules: title 3 to 120, source required; assignee must be an active Manager (else `InvalidInputException`); if an open
task with the same source and source key exists it is returned with `created=false`; a past due date is accepted;
publishes `TaskAssigned` when created; history and Change History written with the source as actor note.

## C1 `TaskActivities`

```java
List<PlannedActivity> plannedBetween(LocalDate from, LocalDate to, UUID ownerUserId /* null = everyone */);
record PlannedActivity(String kind, UUID id, UUID ownerUserId, LocalDate date, String place, String status) {}
```

Cancelled tasks are excluded; `kind` is the task type name; `date` is the due date; `status` the task status.

## Events (published by `tasks`, consumed by `notification`)

- `TaskAssigned(taskId, title, assigneeUserId, assignedByUserId, dueOn)`
- `TaskOverdue(taskId, title, assigneeUserId, dueOn)`
- `TaskEscalated(taskId, title, assigneeUserId, dueOn, daysOverdue)`

Notifications: TASK_ASSIGNED to the assignee ("New task: title, due DD/MM/YYYY"); TASK_OVERDUE to the assignee;
TASK_ESCALATED to every active Director. Links go to `/operations/tasks/{id}`.

## Consumed

`recruitment.api.FollowUpScheduled` (023) creates a FOLLOW_UP task, source `MARKETING_FOLLOW_UP`, key = activity id.
