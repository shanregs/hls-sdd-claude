# Contract: Training sessions on the planned-activity feed (C1) and module interfaces

All additions are new; no existing interface changes except where stated. Same two-method shape as drives
(`recruitment.api.DriveActivities`).

## 1. `training.api.TrainingActivities` (read by 035, later 028 and 015)

```java
record PlannedActivity(String kind, UUID id, UUID ownerUserId, LocalDate date, String place, String status) {}

record ActivityEntry(String kind, String subKind, UUID id, UUID ownerUserId, Set<UUID> attendeeUserIds, LocalDate date,
                     LocalDate originalDate, String place, UUID schoolId, UUID zoneId, String linkedType, UUID linkedId,
                     String status, LocalDate completedOn) {}

/** Published (not held, not cancelled) sessions dated in the range. A given userId matches owner or attendee; null means all. */
List<PlannedActivity> plannedBetween(LocalDate from, LocalDate to, UUID userId);

/** Published, held and cancelled sessions dated in the range (spec 035). Draft and skipped sessions are never returned. */
List<ActivityEntry> activitiesBetween(LocalDate from, LocalDate to, UUID userId);
```

- One entry per session. `kind` = `TRAINING`; `subKind` = `WEEKEND` or `MONTHLY`; `id` = session id.
- `ownerUserId` = the user who created the session (Admin or Director); `attendeeUserIds` = user ids of the assigned
  (REQUIRED) Managers. Teachers are never in the feed. Plan open item 3 (FR-026 wording).
- `place` = venue text (a virtual link is returned as the word "Online"; the link itself is not exposed).
- `status` = `PLANNED` (published, not held), `HELD`, `CANCELLED`. 035's `StatusRules` already maps HELD to Done and CANCELLED.
- `completedOn` = the session date for HELD, else null. `originalDate` = oldest `date` before-value in
  `training_session_history`, else null.
- `schoolId`, `zoneId`, `linkedType`, `linkedId` = null (a session has no Zone; 035 shows "No Zone").
- No topic, trainer, roster, outcome, reason or remark is returned.
- Bulk, range-bounded; a fixed number of queries for any number of sessions.

## 2. Replacing the 035 placeholder (task T-REPLACE)

`activitycalendar.internal.TrainingSource` (today: `available() == false`, no entries) becomes:

```java
@Component class TrainingSource implements ActivitySource {
    group() -> TRAINING;  available() -> true;
    entries(from, to, userIdOrNull) -> trainingActivities.activitiesBetween(from, to, userIdOrNull)
        .stream().map(e -> new CalendarEntry("TRAINING", e.subKind(), e.id(), e.date(), e.date(), e.originalDate(),
             e.ownerUserId(), e.attendeeUserIds(), e.place(), e.zoneId(), e.schoolId(), e.linkedType(), e.linkedId(),
             e.status(), e.completedOn()))
}
```

plus a `TRAINING` case in `ActivityCalendarService.linkFor` (module `TRAINING`, route `/operations/training`). ArchUnit: the
calendar reads `training.api` only.

## 3. `attendance.api.ManagerTrainingDays` (owned by attendance, implemented by training; read by 032)

```java
record TrainingSessionRef(UUID sessionId, LocalDate date, String place, String status) {}
List<TrainingSessionRef> assignedSessionsOn(UUID managerUserId, LocalDate date);           // Field day link
Map<LocalDate, BigDecimal> attendedDays(UUID managerId, LocalDate from, LocalDate to);      // 1.00 or 0.50 per Present day
```

032 `PlannedActivities.forDay` adds `assignedSessionsOn` (kind `TRAINING`). What 032 does with `attendedDays` is Open Item 2;
024 writes nothing into 032 records.

## 4. `ManagerAttendanceReadApi.managersOnLeaveOn` (additive, 032)

```java
Set<UUID> managersOnLeaveOn(Collection<UUID> managerIds, LocalDate date);   // leave day rows, any type, whole or half
```

## 5. `training.api.TrainingSessions` (optional, task T-015)

`List<SessionView> openFrom(LocalDate today)` and `Optional<SessionView> session(UUID id)` (id, topic, date, type) so 015 can
link a Training Stay claim to a session later.

## 6. `training.api.TrainingSettings` (owned by training, implemented by settings)

`repeatThreshold()` (2), `repeatWindowMonths()` (6), `reminderDaysFirst()` (2), `reminderDaysSecond()` (1, 0 = off),
`attendanceDueDays()` (7), `generationHorizonMonths()` (3).

## 7. Events published from `training.api` (read only by notification)

`TrainingAssigned(sessionId, userIds, date, topic)`, `TrainingAssignmentRemoved(...)`, `TrainingSessionChanged(sessionId,
change = MOVED or CANCELLED, userIds, oldDate, newDate)`, `TrainingReminderDue(sessionId, userId, daysBefore, date)`,
`TrainingRegistered(sessionId, userId, date)`, `TrainingExceptionRaised(sessionId, personName, date)`,
`TrainingCannotAttend(sessionId, personName, note)`.

## 8. Consumed

`attendance.api` (`TrainingAttendance`, `AttendanceReadApi`, `WorkingCalendar`), `teacher.api` (`TeacherDirectory`,
`TeacherScopeQueries`, `TeacherPlacementSource`), `organization.api` (`ManagerQueries`, `ScopeQueries`), `school.api`
(`SchoolDirectory`, `ChangeRecorder`), `designation.api.BusinessDate`, identity (`PermissionGuard`).

ArchUnit: nothing reads `training.calendar.internal`; `training.calendar.web` calls only services; no `training` class
touches another module's `internal`; `attendance` does not depend on `training`.

## 9. Permissions and navigation

`PermissionModule`: `TRAINING(VIEW, CREATE, EDIT, DELETE, EXPORT)`, `TRAINING_ATTENDANCE(VIEW, EDIT)`,
`TRAINING_REGISTRATION(VIEW, CREATE, EDIT)`. Seeds: Admin and Director full on the first two; Manager `TRAINING` View and
`TRAINING_ATTENDANCE` View, Edit; Teacher `TRAINING_REGISTRATION` View, Create, Edit; System none. Navigation per plan open item 1.
