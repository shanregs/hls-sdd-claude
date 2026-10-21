# Research: Manager Activity Calendar

Decisions taken from the code on main (not from the planning documents). Format: Decision, Rationale, Alternatives.

## 1. What C1 gives today

- `recruitment.api.PlannedActivity(kind, id, ownerUserId, date, place, status)` is used by drives and visits;
  `tasks.api.TaskActivities.PlannedActivity` has the same six fields. No Zone, completion date, attendees or reschedule history.
- Marketing feed (`MarketingActivitiesImpl`): skips every status except PLANNED; owner is `created_by`; a requested user matches
  creator or attendee. `activity_date_history` (old and new date, insert-only) exists and the web screen already marks
  Rescheduled and derives Missed.
- Drive feed (`DriveActivitiesImpl`): all statuses (PLANNED, HELD, CANCELLED), one entry per date, owner `scheduledBy`, place
  "college, city". `CampusDrive.change()` overwrites the date set; only Change History records the change; no held date.
- Task feed (`TaskActivitiesImpl`): non-cancelled tasks by due date; the task has `completedAt`, `linked_type/id/label`, `place`,
  and `task_history` holds `DUE_CHANGED` (before and after) and `COMPLETED` rows.
- The Field day helper (`attendance.manager.internal.PlannedActivities`, spec 032) reads the three `plannedBetween` methods and
  relies on the marketing feed returning only planned visits.

## 2. How to amend C1

- **Decision**: add a second method `activitiesBetween(from, to, userId)` returning a richer record (`ActivityEntry`) on each
  existing feed interface. Leave `plannedBetween` and `PlannedActivity` unchanged.
- **Rationale**: 032 and 034 keep exact behaviour (no status filter surprise, no constructor change in their tests); the
  amendment is purely additive, which is what the clarification promised ("no existing screen changes").
- **Alternatives**: widening `PlannedActivity` and its status set (breaks the Field day link and every constructor call);
  one shared `activities` module that owns all three feeds (moves code between specs, large blast radius); the calendar
  reading the three modules' internals (forbidden by Principle VII).

## 3. Entry shape

`ActivityEntry(kind, id, ownerUserId, attendeeUserIds, date, originalDate, place, schoolId, zoneId, linkedType, linkedId,
status, completedOn)`. The owner's native status string is kept (PLANNED / COMPLETED / CANCELLED, PLANNED / HELD / CANCELLED,
ASSIGNED ... VERIFIED / CANCELLED); the calendar maps it to Done / Open / Cancelled in `StatusRules`. A reschedule is
`originalDate != null`. No free text beyond `place` leaves the owning module (no notes, outcome, evidence, attachments:
FR-020). Two records, one in `recruitment.api` (drives and visits) and one nested in `TaskActivities`, because tasks must
not depend on a recruitment type for its own contract; the calendar maps both to `CalendarItem`.

## 4. Completion dates (needed for "done late")

- **Decision**: marketing: new nullable `marketing_activity.completed_on` (business date) set in `ActivityService.complete`;
  drives: new nullable `campus_drive.held_on` set in `markHeld`; tasks: derive from `completedAt` converted to the business
  zone (no column).
- **Rationale**: the only stored facts today are status and timestamps in history; asking Change History per item would be a
  per-item query. A nullable date on the row is the smallest durable fact.
- **Alternatives**: read the Change History tables (N+1, cross-module, free-text values); treat "completed" as "on time"
  (loses the required "done late" mark).
- Existing rows keep NULL: no late mark, never counted late (open item 2).
- **Drives**: a drive is one activity over several dates; lateness is judged against its last date. The feed reports
  `completedOn = heldOn` on the last date and `min(heldOn, date)` on earlier dates, so only a drive held after its last date
  is late.

## 5. Reschedule history

- **Visits**: `originalDate` = oldest `old_date` in `activity_date_history`; moved twice is one reschedule (FR-008). The date
  is the chain start, not the previous date.
- **Tasks**: `originalDueOn` = `before_value` of the earliest `DUE_CHANGED` row of the task; `rescheduled` when one exists. The
  history stores values as text; parse as ISO date, ignore a row that does not parse. Settles the spec's "task rescheduling
  to confirm": 025 already records the change, so tasks can show Rescheduled with no 025 behaviour change.
- **Drives**: a new insert-only `drive_date_change(drive_id, old_date, new_date, changed_by, changed_at)` written by
  `DriveService.update` when removed and added dates have equal count, paired in date order. Unequal counts are an add or a
  remove, not a move (open item 3). `originalDate` for a date follows the chain back to its first old date.
- **Alternatives**: infer from Change History (not queryable, differs per module); a generic cross-module reschedule table
  (new shared owner, contradicts "no new stored data" for the calendar itself).

## 6. Migration

`V70__amend_planned_activity_sources.sql`: two `ALTER TABLE ... ADD COLUMN ... DATE` and one `CREATE TABLE` with an
`BEFORE UPDATE OR DELETE` trigger like `activity_date_history`/`task_history`. Number 70 keeps clear of V53 (main), V60 (026)
and the gap for 015, 027, 028. No backfill: a wrong "on time" for old rows is worse than no information.

## 7. Where the module lives and how sources plug in

- **Decision**: new module `activitycalendar`, internal SPI `ActivitySource { String kind(); Set<String> kinds(); boolean
  available(); List<CalendarEntry> entries(from, to, userIdOrNull); }` with three adapters. Training is "not available" because
  no bean implements it; 024 adds a `TrainingSource` adapter over its own C1 feed.
- **Rationale**: reporting modules in this repo (payroll, schoolbilling) are their own packages with api / internal / web;
  a source SPI gives resilience (FR-017) and the training placeholder (FR-016) without a code change to the screen.
- **Alternatives**: putting the calendar in `attendance.manager` (it already reads all three feeds, but is internal to attendance
  and the spec says not to reuse it); in `recruitment` (tasks and training are not recruitment).

## 8. Status rules

One function (FR-007): Cancelled if the owner says cancelled; Done if completed, held, completed or verified (verification is
ignored: COMPLETED counts); Missed if not done, not cancelled and `date < today` (BusinessDate); else Planned. Done late when
`completedOn > date`. A task sent back by its verifier returns to IN_PROGRESS with `completedAt` cleared, so it is open
again by the same rule. Summary arithmetic: planned = done + open + missed (cancelled excluded), checked by a test.
Summary counts a drive by its last date's status and as one drive; the grid shows each date.

## 9. Zone

Order (FR-014): feed `zoneId` (visits: prospect zone, else School's zone through `SchoolDirectory`; drives: none), then for
tasks the linked School (`SCHOOL`, via `SchoolDirectory`) or linked prospect (`PROSPECT`, via a small `ProspectZones`
lookup added to `recruitment.api` that returns Zone ids for prospect ids in bulk), then the owner Manager's Zones from
`ScopeQueries.scopeOf(userId, MANAGER)`. A Manager with several Zones matches a Zone filter for any of them and shows the
first by name; an item with none is "No Zone" (Director and Admin only). Resolved with bulk calls, no per-item query.
Alternative: giving tasks a Zone column (a 025 schema change the spec does not need).

## 10. People and scope

A Manager user is any user id returned by `ManagerQueries.allManagers()` (includes inactive, so past work remains). An
item is shown under its owner and each attendee that is a Manager; if neither owner nor any attendee is a Manager (for
example a Director-created visit), it appears for Director and Admin under "Other users". The Manager role calls the feeds with
their own user id (the feeds already match owner or attendee/assignee), so scope is enforced at the source, not by filtering
afterwards. For Director and Admin the feed is called with null. A Manager asking for another `managerId` gets 404.

## 11. API shape and volume

One call returns a month with items, summary, source notes and partial flag; filters apply server-side so the summary follows
them. Month changes refetch. Detail needs no call (FR-002 fields are in the item). Needs attention is a second call over up to
93 days. No paging (hundreds of items per month). No cache: SC-007 requires the next load to show changes, and the three
feeds are indexed range reads (`marketing_activity.activity_date`, `task(status,due_on)`, drive dates).

## 12. UI

Month grid Monday-first with "+N more" opening the day list, list view of the same items, filters kept in `sessionStorage`
(month and filters survive a refresh, FR/US4), read-only detail drawer, links shown only when the caller has the owning screen's
View permission (`MARKETING`, `RECRUITMENT`, `TASKS`) using routes `/marketing/calendar`, `/recruitment/drives`,
`/operations/tasks`. Items are struck through when cancelled; "done late" and "Rescheduled from DD/MM" are marks, not statuses.
Navigation: Admin and Director "Activity Calendar", Manager "My Activity Calendar", all OPERATIONS (open item 1), no dashboard tile.

## 13. Permission and constitution

`ACTIVITY_CALENDAR(VIEW)` in `PermissionModule`; `ROLE_RESTRICTED_MODULES` entry for Admin, Director, Manager (the map is built
with `Map.of`, so move it to `Map.ofEntries` if it now exceeds ten pairs); seed in `PermissionMatrixService`; Teacher and
System ineligible. Constitution PATCH adds the default-matrix row.

## 14. Risks

- Feed volume: marketing feed filters the owner in memory after a date-range read; fine at this scale, and the new method
  filters in the query where cheap.
- Double counting: a marketing follow-up creates a separate Follow-up task, a different record from the visit; they are
  counted separately, as the owning screens do.
- Time: business-zone dates everywhere; `completedAt` Instants are converted with the same zone as `BusinessDate`.
