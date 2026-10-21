# Contract: Amendments to the C1 feeds (specs 023, 025, 016) and module interfaces

All amendments are additive. The existing `plannedBetween` methods, their result types and every caller (spec 032 Field day,
spec 034, existing tests) are unchanged. Each amended module adds `activitiesBetween` and its own regression test that
`plannedBetween` still returns the same items (marketing: planned visits only).

## 1. Spec 023 school-marketing-mou: `recruitment.api.MarketingActivities`

```java
record ActivityEntry(String kind, UUID id, UUID ownerUserId, Set<UUID> attendeeUserIds, LocalDate date, LocalDate originalDate,
                     String place, UUID schoolId, UUID zoneId, String linkedType, UUID linkedId, String status,
                     LocalDate completedOn) {}   // in recruitment.api, shared with DriveActivities

/** Every visit, call and meeting dated in the range: PLANNED, COMPLETED and CANCELLED. A null user means everyone's. */
List<ActivityEntry> activitiesBetween(LocalDate from, LocalDate to, UUID userId);
```

- Same kind strings as today (`SCHOOL_VISIT`, `SCHOOL_CALL`, `SCHOOL_PROPOSAL_MEETING`, `SCHOOL_FOLLOW_UP`).
- `ownerUserId` is the creator; `attendeeUserIds` the attendees; a given `userId` matches creator or attendee (as today).
- `status` is the stored status. `completedOn` is the new column (null when unknown). `originalDate` is the oldest `old_date` in
  `activity_date_history`, null when never moved.
- `place` is the prospect name (or School name for an account visit). `zoneId` is the prospect's `zone_id`, else the School's
  Zone through `SchoolDirectory`. `schoolId` is set for account visits.
- Behaviour change inside 023: `ActivityService.complete` also stores `completed_on = BusinessDate.today()`. No screen, endpoint
  or response of 023 changes.
- Reads dates and history in bulk (one query for history of the returned ids).

## 2. Spec 025 manager-tasks: `tasks.api.TaskActivities`

```java
record ActivityEntry(String kind /* task type */, UUID id, UUID ownerUserId, LocalDate date, LocalDate originalDate,
                     String place, String linkedType, UUID linkedId, String status, LocalDate completedOn) {}

/** Every task due in the range including CANCELLED ones. A null owner means everyone's. */
List<ActivityEntry> activitiesBetween(LocalDate from, LocalDate to, UUID ownerUserId);
```

- `kind` is the `TaskType` name; `status` the `TaskStatus` name (ASSIGNED, ACKNOWLEDGED, IN_PROGRESS, COMPLETED, VERIFIED,
  CANCELLED). No attendees: the assignee is the owner.
- `completedOn` = `completedAt` in the business time zone when status is COMPLETED or VERIFIED, else null. A task sent back to
  IN_PROGRESS has no `completedOn`.
- `originalDate` = `before_value` of the earliest `DUE_CHANGED` `task_history` row for the task (parsed as an ISO date;
  unparsable rows ignored); null when never moved.
- `linkedType/linkedId` let the calendar find a Zone (SCHOOL or PROSPECT); no title, description, note or evidence is returned.
- No migration, no behaviour change. `TaskActivities.PlannedActivity` and `plannedBetween` are untouched.

## 3. Spec 016 campus-recruitment: `recruitment.api.DriveActivities`

```java
/** One entry per drive date in the range for every status: PLANNED, HELD, CANCELLED. A null user means every drive. */
List<ActivityEntry> activitiesBetween(LocalDate from, LocalDate to, UUID userId);
```

- `ownerUserId` is `scheduledBy`; `attendeeUserIds` are the interviewers; a given `userId` matches either (as today).
- `completedOn`: for a HELD drive, `held_on` on the last date and `min(held_on, date)` on earlier dates; null when `held_on` is null.
- `originalDate`: per date, follows `drive_date_change` back to the first old date; null when the date was never moved.
- `zoneId` is null (a College has no Zone); the calendar uses the owner's Zone. `schoolId` null; `place` is "college, city" as today.
- Behaviour change inside 016: `DriveService.markHeld` stores `held_on`; `DriveService.update` writes `drive_date_change` rows for
  equal-sized date swaps (research.md section 5). Change History entries are unchanged.

## 4. Lookup added to `recruitment.api` for task Zones

```java
interface ProspectZones { Map<UUID, UUID> zonesOf(Collection<UUID> prospectIds); }   // prospect id to Zone id
```

Read-only, bulk, no scope applied (the calendar applies its own).

## 5. Interfaces of the new module

`activitycalendar` exposes no `api` types. Inside it:

```java
interface ActivitySource {
    String group();                 // DRIVES | VISITS | TASKS | TRAINING
    boolean available();            // false for TRAINING until 024
    List<CalendarEntry> entries(LocalDate from, LocalDate to, UUID userIdOrNull);
}
```

Spec 024 adds one `TrainingSource` bean over its own C1 feed; no other change to the calendar.

## 6. Consumed

- `recruitment.api` (`DriveActivities`, `MarketingActivities`, `ProspectZones`), `tasks.api.TaskActivities`
- `organization.api` (`ManagerQueries.allManagers`, `ScopeQueries.scopeOf`), `school.api.SchoolDirectory` (Zone and School names)
- `designation.api.BusinessDate`, identity caller context and permission checks

ArchUnit: nothing reads `activitycalendar`; `activitycalendar` reads no other module's `internal`; `activitycalendar.web`
declares only `@GetMapping`; `activitycalendar.internal` has no `@Entity`, no repository and no write call.

## 7. Permission and navigation

- `PermissionModule.ACTIVITY_CALENDAR(VIEW)`; eligible roles Admin, Director, Manager; seeded granted for those three.
- `NavigationCatalog`: "Activity Calendar" (Admin, Director) and "My Activity Calendar" (Manager), path
  `/operations/activity-calendar`, section OPERATIONS (open item 1 in plan.md).
