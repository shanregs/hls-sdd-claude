# Research: Training Calendar

Decisions taken while planning, with what was read in the code on `origin/main` (merge of 2026-10-24). Format: Decision,
Rationale, Alternatives. Section 11 lists every gap or conflict found with merged code.

## 1. What the code offers today

- **`training` module (spec 016)**: `training.api` has `InductionBatches` and `InductionBatchView`; `training.internal` has the
  batch, enrolment, absence and sign-off classes and `InductionAttendanceService`. It already depends on `attendance.api`
  (`TrainingAttendance`, `AttendanceReadApi`), `teacher.api` and `school.api` (`ChangeRecorder`). There is no session, pattern
  or exception concept anywhere.
- **Attendance (008)**: one mark per (Teacher, date) in `attendance_mark`; status code `T` "Training day" has category TRAINING
  and weight 1.00. `attendance.api.TrainingAttendance` has `markTrainingDay(actor, teacherId, date, value)` and
  `clearTrainingDay(actor, teacherId, date)`. `MarkService.setTrainingMark` refuses a future date, a locked month (409) and a
  day that already holds a mark of another status (409 "already has an attendance mark that is not a training day"); it
  attaches the School of the placement in effect when there is one (null only for an unplaced recruit), keeps history and
  audit. `clearTrainingMark` refuses a non-training mark and a locked month. `AttendanceReadApi` offers `marksOf`,
  `teachersAwayOn` (leave-category marks on a date, in bulk) and `isLocked(teacher, month)`. `WorkingCalendar` answers working
  days and `businessToday()`.
- **Manager attendance (032)**: `attendance.manager.internal.PlannedActivities.forDay` links a Field day to drives
  (`DriveActivities.plannedBetween`), visits (`MarketingActivities.plannedBetween`) and tasks (`TaskActivities.plannedBetween`).
  There is nothing about training in 032's code. `ManagerAttendanceReadApi.month(es)` returns counts (field days, leave paid
  and unpaid, unpaid days) and no per-date leave. `ManagerLeaveAttendance` is write-only (apply, remove).
- **Activity calendar (035)**: `activitycalendar.internal.ActivitySource` (SPI), `CalendarEntry`, three adapters and the
  placeholder `TrainingSource` (`available()` false, no entries). `StatusRules` treats `COMPLETED`, `HELD`, `VERIFIED` as done
  and `CANCELLED` as cancelled, anything else as open (Missed when the judged date has passed). Items are keyed
  `kind:id:date`; a person is the owner plus every attendee who is a Manager; per-Manager summaries count only Manager people.
  `linkFor` has no case for TRAINING (returns null). The Zone of an item comes from its own Zone, linked School or prospect,
  else the owner's Zone, else "No Zone".
- **Expenses (015)**: `LinkResolver` offers `InductionBatches` for a Training Stay claim and the three C1 feeds for Travel and
  Fuel; the Training Stay link kind is fixed to `INDUCTION_BATCH`.
- **Identity**: `PermissionModule` enum (no training module), `PermissionEligibility` (per-feature restricted maps),
  `PermissionMatrixService` (a `seedX()` method per module), `AccessModelService` (data-scope labels), `NavigationCatalog`
  (items with fixed sections: OPERATIONS, MY ATTENDANCE, RECRUITMENT, MARKETING, LEAVE, ...; the section order is tested by
  `NavigationSectionOrderTest`).
- **Notifications**: `NotificationType` is an enum stored as text with no database CHECK, so a new type needs no migration; one
  listener per source module (`TransferEventListener`), texts in `MessageFactory`, recipients through `RecipientResolver`.
- **Audit**: `ChangeRecorder.record(...)` plus `AuditVisibility.REQUIRED_VIEW` (entity type to the module whose View hides it).
- **Settings (011)**: `SettingKey` is a code catalog (groups SYSTEM and SECURITY) edited on the System Settings screens;
  `RuntimeSettings` is an `identity.policy` interface implemented by `settings`; no business module reads a setting today
  (marketing and Manager attendance keep their own settings tables).
- **Teachers**: `TeacherStatus` IN_TRAINING, ACTIVE, ON_LEAVE, EXITED. `TeacherScopeQueries.teacherScope` gives a Manager the
  Teachers placed today at their Schools. `TeacherPlacementSource.teacherIdsAtSchoolsOn` lists Teachers at Schools.
  `SchoolDirectory.schoolsInZone`, `ManagerQueries.managersOfZone`, `currentSchoolIds` and `allManagers` cover group expansion.
- **Jobs**: daily `@Scheduled(cron, zone = business time zone)` jobs exist (026 `ManagerReconcileJob`, 027 `MovementEffectJob`,
  with a property to switch them off in tests).

## 2. Where the logic lives

**Decision**: sub-package `com.hls.training.calendar` (`internal`, `web`) in the existing `training` module; public interfaces
and events in `training.api`. Induction code is not touched.
**Rationale**: the roadmap and spec already say "the `training` module (planned by 016 for induction)"; one module keeps the
person's history (induction plus sessions) behind one boundary; a sub-package keeps the new classes apart from the 12
induction classes (the same shape as `recruitment.marketing` and `attendance.manager`).
**Alternatives**: a new `trainingcalendar` module (then the history would have to read induction through an API and two
modules would share the permission and navigation concept); adding the classes flat to `training.internal` (mixes two
unrelated sets of tables).

## 3. Module direction: ports owned by the module that needs them

`training` depends on `attendance` (it writes Teacher training days). Spec 032 lives in `attendance.manager` and must read
training (the Field day link; attended days). A direct call would make `attendance` depend on `training`: a cycle, which
Spring Modulith rejects. The code base already solves this twice: `recruitment.api.InductionProgress` (owned by recruitment,
implemented by training) and `teacher.api.TeacherPlacementSource` (owned by teacher, implemented by schoolbilling).

**Decision**: `attendance.api.ManagerTrainingDays`, owned by `attendance`, implemented by `training`:

```java
interface ManagerTrainingDays {
    record TrainingSessionRef(UUID sessionId, LocalDate date, String place, String status) {}
    /** Sessions the Manager (by user id) is assigned to on the date, not cancelled, not draft. */
    List<TrainingSessionRef> assignedSessionsOn(UUID managerUserId, LocalDate date);
    /** Dates in the range on which the Manager was recorded Present; value 1.00 or 0.50 (spec 032 reads these). */
    Map<LocalDate, BigDecimal> attendedDays(UUID managerId, LocalDate from, LocalDate to);
}
```

For 035, 015 and 028, which nobody in `training`'s dependency chain depends on, the interfaces live in `training.api`:
`TrainingActivities` (C1), `TrainingSessions` (a small read for linking an expense). `training.api.TrainingSettings` is owned by
`training` and implemented by `settings` (the `RuntimeSettings` pattern), because `settings` already depends on `identity` and
nothing may depend on `settings.internal`.
**Alternatives**: duplicate the data in `attendance` (two truths); read the training tables from 032 (breaks Principle VII).

## 4. Teacher attendance through spec 008

**Decision**: a Present or Half outcome of a Teacher calls `TrainingAttendance.markTrainingDay(actor, teacherId, date, 1.00 or
0.50)`; a change away from Present calls `clearTrainingDay`. The training register keeps its own entry (outcome, reason, notes,
history) because 008 cannot hold Absent, Excused, reasons or the session link; 008 holds only the day mark. Before each call
`AttendanceBridge` checks, with read methods only:

1. `date` is not in the future (else the whole save is refused for that row: "attendance cannot be recorded before the day");
2. `AttendanceReadApi.isLocked(teacher, YearMonth)` is false, else the row is refused with the 008 message "This month is
   locked. Attendance can only change after it is reopened." and nothing is saved for that row (FR "same lock and re-open
   rules as 008 and says so");
3. `marksOf(teacher, month)` has no mark on the date, or the mark's category is TRAINING. Otherwise it is a **clash**: the
   training row is saved with `sync_state = CLASH` and the existing school mark is described to the user (status, value,
   School); the 008 mark is not written or changed.

**Why check first**: `setTrainingMark` throws a `ConflictException` for both cases. Our save is one transaction around many
rows; an exception thrown inside a joined `@Transactional` method marks the shared transaction rollback-only even when caught,
so one clash would undo the other 99 rows. Reading first keeps the batch whole. A race between the check and the write
(someone marks the day in between) surfaces as a 409 for the whole save, with "try again", which is rare and safe.

**The clash rule.** 008 keeps one mark per Teacher and date and says a day split between two statuses "is recorded as one
mark with the larger share and a note", set by a supervisor. The spec says the system shows the clash and 008's half-day
rules decide. Therefore 024 never overwrites: the row shows "Clash with school attendance: Present at <School>, 1.00 day".
A supervisor resolves the day in 008 (change or clear the school mark); then `POST .../entries/{id}/sync` retries the write
(also tried by the nightly reconcile job for clashes up to 31 days old). Until then the training outcome is in the register and
the history but not in the 008 rollup. (Open Item 5.)

**Corrections** replace the entry value, append to `training_attendance_history`, and update or clear the 008 mark by the same
path. Rows already `WRITTEN` that are corrected in a locked month are refused.

**School on the mark.** The spec says "with no School"; `setTrainingMark` attaches the placement School when the Teacher is
placed (a School is required by the database trigger unless the code is TRAINING, and is allowed when present). We do not
change this; the statement in the spec is read as "does not require a School".

**Side effect on non-working days (gap, not blocking).** `RollupCalculator` counts a TRAINING mark on a non-working date
(Saturday, Sunday, holiday) into `workingDays`, `trainingAvailable`, `trainingAttended` and `weightedTotal`. Weekend refreshers
therefore add one to the Teacher's working-day count in the 008 month. Payroll (013a/013b) deducts only Loss-of-Pay days
(`loss_of_pay` flag), so no pay changes, but the 008 percentage figures move. The 008 spec note "until the HR calendars spec
exists, training days available equals attended" is also now stale: 024 knows the expected days. Both are 008 follow-ups, not
part of this plan; recorded in the 008 notes at the end of the work.

## 5. Patterns, generation and editing

- **Rules**: `NTH_WEEKDAY` (n = 1 to 4 or 5 meaning "last", weekday 1 to 7) and `DAY_OF_MONTH` (1 to 31, clamped to the last
  day of a short month; "fifth Saturday" in the spec's edge case is read as the "last" rule, which is what the user sees in the
  pattern preview).
- **Materialised rows, not computed on read**: a session needs identity for assignments, registrations and C1 ids. Rows are
  created for every month from `max(start_month, current month)` to the current month plus the horizon (setting, default 3),
  when a pattern is created, edited or resumed, and by `GenerationJob` (daily 03:10, business zone). Uniqueness
  `(pattern_id, pattern_month)` makes a second run a no-op (FR-005, SC-001). Generated sessions start Published.
- **Skip**: `training_pattern_skip(pattern_id, month, reason)`; generation creates the month as a SKIPPED row (visible on the
  calendar with the reason, no obligations); removing the skip turns the row back to Published when not past.
- **Hand edit**: any edit of a generated session sets `hand_edited = true` and, for date/trainer/venue/topic changes, appends
  history; the pattern never rewrites it.
- **Edit the pattern from month M**: the stored pattern changes in place; every session of the pattern in month M or later
  that is not Held, not Cancelled, not SKIPPED and not hand-edited is rewritten from the pattern (assignments and
  registrations stay); earlier sessions are untouched. Because only future untouched rows follow the pattern, an in-place
  edit gives the "from a chosen month on" behaviour without versioned pattern rows. A change of day rule that moves a session
  to another date appends a `date` history row (so C1 reports it as rescheduled).
- **Stop from month M**: `end_month = M - 1`; untouched sessions (not Held, not hand-edited, no attendance) from M on are
  deleted along with their assignments and registrations (people with assignments are notified, as for a removal); held and
  hand-edited ones stay. Hand-edited later sessions stay and are no longer "from a pattern" in the calendar legend: the
  session keeps its `pattern_id` for history but the calendar shows "Pattern stopped".
- **Duplicate pattern**: unique partial index on the date rule and start time among active patterns (FR edge case).
- **Holiday notice**: computed on read with `WorkingCalendar.workingDays(null, d, d)`; an empty result gives the notice. Nothing
  is stored and nothing blocks (spec: the session is still created).
- **Default group**: the pattern holds an optional group (kind and value). At generation the group is resolved to people and
  assignments are created with source GROUP (snapshot, FR-011). Later joiners are surfaced by "N people in the group are not
  assigned" (resolved on read, cached per request) and a button that adds only the missing ones.

## 6. People: groups, scope, leave and exits

- **Group expansion** (all through public interfaces): `INDIVIDUAL` (ids), `SCHOOL` (`teacherIdsAtSchoolsOn`), `MANAGER` (the
  Manager's `currentSchoolIds` then the Teachers there), `ZONE` (`schoolsInZone` then the Teachers there, plus the Zone's
  Managers when the audience includes Managers), `ALL_TEACHERS`, `ALL_MANAGERS`, `ALL` (both). Teachers with status ACTIVE are
  included in groups; an individual pick may also be ON_LEAVE (the leave then shows). IN_TRAINING and EXITED are refused
  (FR-013). Managers must be active.
- **Manager scope** for attendance, exceptions and history: `TeacherScopeQueries.teacherScope(userId, roles)` (Teachers placed
  today at the Manager's Schools) plus the Manager's own record. Recording attendance for a Manager is Admin or Director only.
- **On leave, Teachers**: `AttendanceReadApi.teachersAwayOn(ids, date)` (leave marks are real marks even for future approved
  leave). **On leave, Managers (gap)**: nothing in `attendance.api` answers "which Managers have leave on this date".
  **Decision**: add one additive read to `ManagerAttendanceReadApi`: `Set<UUID> managersOnLeaveOn(Collection<UUID> managerIds,
  LocalDate date)`; it is a read of 032's own day rows, owned by 032, tested in 032's suite. Spec 032 is "Implemented" and
  merged, so this is a recorded amendment ("Amended by 024").
- **Exits**: no `TeacherExited` or Manager-change event exists (026 hit the same gap and used a daily check). `ReconcileJob`
  (daily 03:30) removes assignments and registrations on not-held sessions of EXITED Teachers and inactive Managers, with a
  note and a notification to the Admin and Director list, and retries clashes. Read paths also ignore such people, so a stale
  obligation never shows as Required between runs.
- **Group changes after assignment**: none by design (snapshot). A Teacher moved to another Manager keeps the assignment; the
  new Manager sees them through placement scope from the move date.

## 7. Planned-activity feed (C1) and the 035 placeholder

**Decision**: `training.api.TrainingActivities` (contracts/planned-activity-feed.md): `plannedBetween` (planned sessions only,
for 032's Field day link and 015's Travel link in the future) and `activitiesBetween` (planned, held, cancelled, for 035), the
same two-method shape as drives. One entry per published session; `ownerUserId` is the scheduling user; `attendeeUserIds` are
the assigned Managers' user ids; a given `userId` matches owner or attendee. Statuses: PLANNED (Published, not held), HELD,
CANCELLED; Draft and Skipped sessions are not returned. `completedOn` for HELD is the session date (the date attendance was
saved is bookkeeping, and using it would make 035 call every late-entered session "done late"). `originalDate` is the oldest old
date in `training_session_history`.

**Conflict with FR-026**: it asks for "one entry per assigned or registered Manager". Only Teachers register, so "registered
Manager" cannot occur. One entry per Manager with the same id would make 035's key `kind:id:date` collide, double-list the
session on the org-wide calendar and need dedupe code in 035 (which already dedupes for multi-date drives by `kind:id`, so
org totals would hold, but the item list would not). The drive precedent (owner = `scheduledBy`, attendees = interviewers) gives
the same per-Manager visibility with one entry; 035's summaries count only Manager people so the Admin owner never appears as a
Manager. (Open Item 3.)

**Zone**: a session has no Zone. `zoneId` is null, so 035 shows Training under "No Zone" and a Zone filter hides it. Setting the
Zone of the assigned Managers when they share one is possible later; it is not done now because it would change with every
assignment and the calendar's Zone is documented as an "item's own Zone, else owner's".

**Replacing the placeholder (task T-REPLACE)**: the adapter is a `@Component TrainingSource` in `activitycalendar.internal` over
`TrainingActivities.activitiesBetween`, `group() = TRAINING`, `available() = true`; kind `TRAINING`, sub-kind `WEEKEND` or
`MONTHLY`. `ActivityCalendarService.linkFor` gets a TRAINING case (module `TRAINING`, route `/operations/training`; a Manager
reaches `/operations/my-training`, see section 9). Because the owner is not a Manager, a Manager caller sees the item only as
attendee, which is the intended scope. Tests: `SourceAdaptersTest` adds the training adapter; the 035 fixture
`CalendarMonthIntegrationTest` counts one more kind; `CalendarReadOnlyTest` unchanged.

**Spec 032 and 015** (both read C1 by constructor-injected feed interfaces, so "no change" in the spec is not literally true):
032's `PlannedActivities` gets the `ManagerTrainingDays` port (one more list in `forDay`); 015's `LinkResolver` is untouched in
this plan and gets an optional follow-up (T-015) to link a Training Stay claim to a session. This is the honest statement of
FR-026's "without any change to how they read other kinds".

## 8. Settings, notifications, reminders and flags

- **Settings (gap)**: the spec says the repeat threshold, reminder days and the 7-day flag are "set in System Settings".
  `SettingKey` can hold them (group SYSTEM, INTEGER with range): `training.repeat-threshold` (2, 1 to 10),
  `training.repeat-window-months` (6, 1 to 24), `training.reminder-days-first` (2, 0 to 30), `training.reminder-days-second` (1, 0
  to 30; 0 disables), `training.attendance-due-days` (7, 1 to 60), `training.generation-horizon-months` (3, 1 to 12).
  `training.api.TrainingSettings` (owned by training) is implemented by `settings`. Existing settings tests count the catalog
  (`SettingKeyTest` checks every key; any count assertions must be updated) and the System Settings screen renders the catalog,
  so no frontend work is expected there (check at implementation).
- **Events and types**: `TRAINING_ASSIGNED`, `TRAINING_ASSIGNMENT_REMOVED`, `TRAINING_CHANGED` (moved or cancelled),
  `TRAINING_REMINDER`, `TRAINING_REGISTERED`, `TRAINING_EXCEPTION` (to Admin and Director), `TRAINING_CANNOT_ATTEND` (to Admin
  and Director). Published from `training.api`; `TrainingEventListener` in `notification` follows `TransferEventListener`.
  Teachers' notices reach the phone list through spec 021 (no mobile change). Bulk assignment publishes one event per person
  inside the transaction (SC-002: 100 people in under a minute is a trivial load).
- **Reminders**: `ReminderJob` (daily 07:30 business zone) finds Published sessions dated today plus N days for each configured
  N, and every Required or Registered person not yet reminded for that offset (`training_reminder_sent`, unique per session,
  person, offset), then publishes `TrainingReminderDue`. A moved session sends a change notice instead and the reminder keys
  of the session are cleared by the move so the new date is reminded afresh.
- **Missing attendance flag**: derived: session date plus the due days is before today and status is not Held/Cancelled/Skipped.
  Shown on the session and on the calendar for Admin and Director; no stored field.

## 9. Permissions, navigation and audit

- `TRAINING` eligible roles Admin, Director (all five actions) and Manager (View only, enforced in `PermissionEligibility`).
  `TRAINING_ATTENDANCE` Admin, Director, Manager (View, Edit). `TRAINING_REGISTRATION` Teacher only. System none (all three are
  added to `BUSINESS_MODULES`). Seeds follow `seedTeacherTransfers`. `AccessModelService` data scope: `TRAINING` and
  `TRAINING_ATTENDANCE` ORG_WIDE for Admin and Director, ASSIGNED for Manager; `TRAINING_REGISTRATION` OWN.
- **Navigation (gap, Open Item 1)**: the spec's "MY WORK" and "MY TRAINING" sections do not exist. Using them would change the
  fixed section order tested by `NavigationSectionOrderTest` (spec 002 FR-008a). Default: OPERATIONS "Training Calendar"
  (`/operations/training`) and "Training Exceptions" (`/operations/training/exceptions`) for Admin and Director; OPERATIONS "My
  Training" (`/operations/my-training`) for Manager, next to My Tasks; MY ATTENDANCE "Upcoming Training"
  (`/my-attendance/training`) and "My Training History" (`/my-attendance/training/history`) for Teacher.
- **Audit**: entity types `TRAINING_SESSION`, `TRAINING_PATTERN`, `TRAINING_ASSIGNMENT`, `TRAINING_REGISTRATION`,
  `TRAINING_ATTENDANCE`, `TRAINING_EXCEPTION` added to `AuditVisibility.REQUIRED_VIEW` against `TRAINING` (a role without
  `TRAINING` View does not see them in Change History). A Teacher's own registration events are visible to Admin and Director.

## 10. Exceptions, repeat count, history and privacy

- An exception exists per (session, person) where the person is an assigned (Required) person with outcome ABSENT and
  `on_leave = false`. Created and withdrawn by the attendance save (same transaction). Status OPEN, CLOSED (closure EXCUSED,
  MADE_UP, ACKNOWLEDGED) or WITHDRAWN (the absence was corrected). Closing with MADE_UP requires a later, non-cancelled session
  where the same person is recorded Present (checked on close; any session type, FR clarification).
- **Repeat marker**: count of the person's exceptions whose session date falls in the last N months (default 6) with status
  OPEN or closure ACKNOWLEDGED; a person at or above the threshold (default 2) is marked on the exceptions list and in the
  attendance grid. EXCUSED, MADE_UP and WITHDRAWN do not count: the spec says "unexcused", and a made-up session is
  treated as put right. This reading is a default (listed in the spec's "Remaining Defaults" as a threshold, not the counting).
  No task is created through spec 025 (decided).
- **Registered no-show** (open weekend, Registered, outcome ABSENT): displayed as "No-show", never an exception (decided).
- **Privacy**: `certificate_note` is visible to the person; `internal_remark` and every exception are never in any response to
  a Teacher. A Manager sees internal remarks and exceptions of their own Teachers (they need them to follow up) but not those
  recorded against themselves.
- **Induction in the history**: read from the 016 enrolment and sign-off rows by `HistoryQueries` (same module), shown as a
  read-only entry type INDUCTION with batch name, dates, result and sign-off date.
- **Yearly figure**: for a calendar year, expected = the person's Required assignments on non-cancelled, non-skipped sessions
  that are Held or dated before today; attended = Present value sum (1.00 or 0.50) over those sessions; weekend registrations
  appear in the list but not in the expected figure (they are optional).

## 11. Gaps and conflicts found with merged code or the spec text

| # | Finding | Effect and resolution |
| --- | --- | --- |
| G1 | `TrainingSource` in 035 is a placeholder with `available() == false`, no entries, and `linkFor` has no TRAINING case. | Task T-REPLACE (plan.md): real source, link case, tests. The comment in the placeholder says "this one is removed". |
| G2 | The spec says 035 and 032 read training "without any change". In code, 032 `PlannedActivities` and 015 `LinkResolver` take their three feeds by constructor and 035 needs a source bean. | 035: a bean replacing a bean (no change to how it reads others). 032: one more list in `forDay` through the new port (T-032). 015: optional T-015. |
| G3 | Spec 032 has no read of training attendance and its `ManagerMonth` has no training field; a Manager at training without a check-in is an unpaid day. | Port `ManagerTrainingDays` offered; the payroll effect needs the user's decision (Open Item 2). Nothing from 024 writes into 032 (decided). |
| G4 | No read of "Managers on leave on date" in `attendance.api`. | Additive `ManagerAttendanceReadApi.managersOnLeaveOn` (section 6), amendment recorded in 032. |
| G5 | `TrainingAttendance.markTrainingDay` throws on a clash and on a locked month; catching would poison the transaction. | Read first with `AttendanceReadApi` (section 4); clash saved with the 008 write held back. |
| G6 | 008 counts a training mark on a non-working day into `workingDays` and `weightedTotal`; 008's note says available equals attended. | No pay effect (payroll uses Loss-of-Pay). Recorded as a 008 follow-up. |
| G7 | The spec says the training mark has "no School"; code attaches the placement School when there is one. | Harmless; read as "does not need a School". |
| G8 | Navigation sections MY WORK and MY TRAINING do not exist and the order is tested. | Open Item 1 with a default. |
| G9 | System Settings are a code catalog read through `identity.policy.RuntimeSettings`; no business module reads settings. | New `SettingKey`s and a `training.api.TrainingSettings` port implemented by `settings` (section 8). |
| G10 | No Teacher-exit or Manager-change event. | Daily `ReconcileJob` (as 026), reads ignore such people. |
| G11 | FR-026 "per assigned or registered Manager" cannot be built (only Teachers register; 035 keys by kind, id, date). | One entry per session, attendees = assigned Managers (Open Item 3). |
| G12 | `TRAINING_ATTENDANCE` is described for the Manager only, while Admin and Director record "for any session". | Seeded for Admin and Director too (Open Item 4). |
| G13 | Spec edge case "fifth Saturday" is not a supported rule (rules are first to fourth or last). | Read as "last"; the preview says so. |
| G14 | `activitycalendar` shows a session as Missed when its date has passed and it is not Held; Held is set by the first saved attendance. | Intended; the "attendance not recorded" flag on the session matches the 035 Missed state. |
| G15 | A Training session has no Zone, so 035 lists it under "No Zone" and a Zone filter hides it. | Accepted limitation; noted in the 035 spec amendment note. |
| G16 | The 008 spec lists the training calendar as belonging to "the later HR calendars spec". | This spec; the note is updated at the end of the work. |

## 12. Alternatives not chosen

- **Generate sessions only on read** (no rows for the future): cannot hold assignments, a skip or a hand edit, and gives no stable
  id for C1.
- **Write Absent/Excused into 008** as a leave or no-mark: 008 has no such status and would mix reason and training outcomes
  into payroll-relevant records.
- **A stored `held` flag set by a job on the session date**: replaced by "first saved attendance sets Held", which cannot be
  wrong about whether attendance exists.
- **Live group rules** instead of a snapshot: contradicts the decided clarification and would silently move obligations.
- **One exception row per occurrence in a second table of "make-up links"**: a make-up is only a link to a later session on the
  exception row.
