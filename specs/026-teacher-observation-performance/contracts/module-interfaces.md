# Contract: Module interfaces and events

## Amendment to `tasks.api.TaskCreation` (spec 025 C2)

All operations are addressed by (`source`, `sourceKey`), act on the open task only, run as the system with an actor note, write
task history and Change History, and return `false` when no open task matches.

```java
boolean reschedule(String source, String sourceKey, LocalDate dueOn, String title, String description);
boolean complete(String source, String sourceKey, String note, UUID completedByUserId);
boolean cancel(String source, String sourceKey, String reason);
boolean reassign(String source, String sourceKey, UUID assigneeUserId); // active Manager, else InvalidInputException
```

`reassign` publishes `TaskAssigned` to the new assignee. `complete` is the same transition as a user completing with a note.

## `performance.api.PerformanceReferrals` (read by spec 027)

```java
record Referral(UUID issueId, UUID teacherId, UUID schoolId, String recommendation /* TRANSFER | REPLACE */, String reason,
                UUID referredBy, LocalDate referredOn, List<LinkedRecord> supporting) {}
record LinkedRecord(String type /* OBSERVATION | FEEDBACK */, UUID id, LocalDate date, String result, String summary) {}

List<Referral> referredFor(UUID teacherId);                 // Referred issues only
Optional<Referral> referral(UUID issueId);
void reportDecision(UUID issueId, String outcome /* TRANSFERRED | REPLACED */, UUID actorUserId); // closes Referred issue
```

Read-only apart from `reportDecision`, which refuses an issue that is not Referred. Returns records, never entities. No caller
scope is applied: 027 applies its own.

## `performance.api.PerformanceSummary` (read by spec 028)

```java
record Counts(long observationsDone, long observationsPlanned, long observationsOverdue, long openIssues, long referrals,
              long resolvedIssues) {}
Map<UUID, Counts> byManager(LocalDate from, LocalDate to);
Map<UUID, Counts> byZone(LocalDate from, LocalDate to);
```

A Manager's Teachers are those placed at their Schools on the last day of the period; Zone comes from the School.

## Events (published by `performance`, consumed by `notification`)

- `ObservationPlanned(planId, teacherId, teacherName, managerUserId, plannedOn, reason)`: `OBSERVATION_PLANNED` to the Manager,
  text "Observation planned: {teacher}, DD/MM/YYYY ({reason})", link to the planned list.
- `ReferralRecorded(issueId, teacherId, teacherName, recommendation)`: `REFERRAL_RECORDED` to every active Director, link to
  the issue.

Task overdue and escalation use the existing `TaskOverdue` and `TaskEscalated` events.

## Consumed

- `teacher.api.TeacherScopeQueries`, `TeacherDirectory`, `TeacherPlacementSource` (scope, status, current School)
- `organization.api` (the Manager of a School), `school.api.SchoolContacts` (Principal name)
- `tasks.api.TaskCreation` (create and the four operations above)
- `files.api.FileStore` (attachments)
- `designation.api.BusinessDate`, `audit` `ChangeRecorder`

ArchUnit: only `notification`, 027 and 028 may read `performance.api`; `performance` reads no other module's `internal`.
