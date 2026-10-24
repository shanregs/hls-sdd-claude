# Implementation Plan: Manager Activity Calendar

**Branch**: `035-manager-activity-calendar` | **Date**: 2026-10-21 | **Spec**: [spec.md](spec.md)

## Summary

A read-only reporting module `activitycalendar` (package `com.hls.activitycalendar`, api / internal / web) that merges the
planned-activity feeds (contract C1) of campus drives (016), marketing visits, calls and meetings (023) and tasks (025) into
one month calendar, a summary and a "Needs attention" list, scoped by role. It has no tables, no entities and no write
endpoint. Training (024) plugs in later as one more source adapter.

C1 as built cannot answer "planned versus completed" (research.md section 1): the marketing feed returns only planned
visits, none of the three feeds carries a completion date, only visits keep a reschedule history, and drive date changes and
the Zone of an item are not exposed. So three owning modules get **additive** amendments: a second, richer feed method on
each existing C1 interface (`activitiesBetween`), leaving `plannedBetween` and everything built on it (spec 032 Field day,
spec 034) untouched. One migration `V70__amend_planned_activity_sources.sql` adds `marketing_activity.completed_on`,
`campus_drive.held_on` and `drive_date_change`; tasks need no migration (completion time and due-date changes are already
stored). The exact amendments to 023, 025 and 016 are in [contracts/source-feed-amendments.md](contracts/source-feed-amendments.md).

A new `ACTIVITY_CALENDAR` permission module (View only) is seeded for Admin, Director and Manager; the React feature
`features/activityCalendar` provides the month grid, the list, the summary, filters and the Needs attention list.

## Technical Context

- Java 25, Spring Boot, Spring Modulith, JPA, Flyway, PostgreSQL (Testcontainers), React + Vite + Vitest.
- Migration: `V70__amend_planned_activity_sources.sql` (max on main is V53; 026 takes V60; a gap is kept for 015, 027, 028).
  Adds two nullable columns and one insert-only table; no data is changed, no backfill (open item 2).
- Constraints: read-only (`@Transactional(readOnly = true)`, GET mappings only), no cross-module foreign keys, dates are plain
  calendar dates, "today" comes from `BusinessDate`, 404 for anything out of scope, no caching (reflects owning screens at load).
- Scale: one month, tens of Managers, some hundreds of items (SC-005: 500 items, 20 Managers under 3 seconds). Three
  range-bounded feed calls plus bulk Zone and name lookups; no per-item query.
- No `NEEDS CLARIFICATION` remains; three small decisions are recorded as open items below.

## Constitution Check

| Principle | How it is met |
| --- | --- |
| I Auditable | The calendar writes nothing, so it adds no history (FR-019). The amendments add history where a fact is currently lost: `drive_date_change` is insert-only (DB trigger), and drive date changes, "held" and visit completion keep going to Change History through the existing services. |
| II Five roles, RBAC | New module `ACTIVITY_CALENDAR` with View only; seeded for Admin, Director, Manager; matrix-editable; Teacher and System ineligible in `PermissionEligibility`. |
| III Data scope | Manager: own items only (owner, attendee or assignee), resolved on the server from the token; a different `managerId` answers 404; the Zone filter only narrows. Admin and Director org-wide. Teacher and System 403. |
| IV Role-based UI | One navigation item per role from `NavigationCatalog` (Admin and Director "Activity Calendar", Manager "My Activity Calendar"); no action buttons exist; links to owning screens are shown only when the caller holds that screen's permission. |
| V Formula-driven | Not touched: no pay, receivable or margin effect. |
| VI Training and substitution | Not touched; training appears only when 024 offers a source. |
| VII Modular monolith | `activitycalendar` reads `recruitment.api` (drives, visits, prospect Zone), `tasks.api`, `organization.api`, `school.api`, `designation.api` (BusinessDate) and identity callers; nothing reads `activitycalendar`. ArchUnit and Modulith verification enforce it. Amendments touch only the owning modules' own `api` and `internal`. |
| VIII Concurrency | Request-path reads only; no locks, no conflicts possible. |
| IX Testability | Feed contract tests per source, a hand-counted month fixture (SC-002), the two-Manager scope matrix (SC-004), a no-write ArchUnit and HTTP test (SC-008), a 500-item timing test (SC-005), frontend tests for grants and filters. |
| X Security | Server re-checks the grant and scope on every request; no evidence, notes, outcome text or attachments are read (FR-020): the feeds expose only the fields in the contract; free text (place, names) is escaped on output. |

Constitution change: PATCH, an Activity Calendar row in the default access matrix and the OPERATIONS navigation items
(already promised in the spec). No principle changes. Gate result: pass before research and after design, no violations.

## Project Structure

```text
specs/035-manager-activity-calendar/ spec.md plan.md research.md data-model.md quickstart.md tasks.md contracts/
backend/src/main/java/com/hls/activitycalendar/
  api/       package-info only (nothing is exposed to other modules)
  internal/  ActivitySource (SPI), DriveSource, VisitSource, TaskSource, (TrainingSource added by 024), CalendarService,
             StatusRules, CalendarZoneResolver, PeopleDirectory, CalendarItem, CalendarSummary, NeedsAttention
  web/       ActivityCalendarController (GET only), ActivityCalendarExceptionAdvice
backend/src/main/java/com/hls/recruitment/api/        DriveActivities, MarketingActivities (+ activitiesBetween), ActivityEntry (new record)
backend/src/main/java/com/hls/recruitment/internal/   DriveActivitiesImpl, CampusDrive (held_on), DriveService (record date changes, held_on), DriveDateChange (+ repository)
backend/src/main/java/com/hls/recruitment/marketing/internal/ MarketingActivitiesImpl, MarketingActivity (completed_on), ActivityService
backend/src/main/java/com/hls/tasks/api/              TaskActivities (+ activitiesBetween, ActivityEntry)
backend/src/main/java/com/hls/tasks/internal/         TaskActivitiesImpl (reads task_history DUE_CHANGED)
backend/src/main/java/com/hls/identity/               PermissionModule, PermissionEligibility, PermissionMatrixService (seed), NavigationCatalog
backend/src/main/resources/db/migration/V70__amend_planned_activity_sources.sql
frontend/src/features/activityCalendar/ (ActivityCalendarPage, MonthGrid, ListView, SummaryBar, NeedsAttention, ItemDetail,
  activityCalendarApi, __tests__) + route /operations/activity-calendar in App.tsx
postman/ (collection additions)
```

**Structure Decision**: one new backend module and one frontend feature; mobile is out of scope. The only changes to existing
modules are the additive feed amendments and the permission and navigation entries.

## Design Decisions

See [research.md](research.md). Highlights:

- A second feed method, `activitiesBetween`, per source instead of changing `plannedBetween`: spec 032 and 034 keep their exact
  behaviour (the marketing feed must keep returning only planned visits for the Field day link).
- Status is derived in one place (`StatusRules`): Cancelled, then Done, then Missed (open and date before today), else Planned;
  "done late" when the completion date is after the item date; a drive is judged on its last date.
- Zone: the item's own Zone (prospect Zone, School Zone, or the Zone of a linked School or prospect) first, then the owner's
  current Zone, else "No Zone" (FR-014). Resolved in bulk.
- People: an item belongs to its owner and every attendee who is a Manager; per-Manager counts count it for each, organization
  totals count it once (distinct kind and id, so a multi-date drive counts once).
- Sources are `ActivitySource` beans; each is called in its own try/catch so one failure gives a named `partial` note; training
  is reported `available:false` until spec 024 adds a source bean.
- No new stored data; no cache; the month is the unit of work (a calendar range over 31 days is refused; Needs attention up to 93 days).

## Amendments Summary (details in the contract)

| Spec | Module | Amendment |
| --- | --- | --- |
| 023 school-marketing-mou | `recruitment.marketing` | `MarketingActivities.activitiesBetween` returns planned, completed and cancelled visits with attendees, `schoolId`, Zone, `completedOn`, `originalDate` (from `activity_date_history`); `marketing_activity.completed_on` set on completion. |
| 025 manager-tasks | `tasks` | `TaskActivities.activitiesBetween` returns cancelled tasks too, with `completedOn`, `originalDueOn` and `rescheduled` (from `task_history` `DUE_CHANGED`), linked type and id. No migration. |
| 016 campus-recruitment | `recruitment` | `DriveActivities.activitiesBetween` adds `heldOn` (new `campus_drive.held_on`), interviewers as attendees and per-date `originalDate` from new `drive_date_change`. |

Each amendment is additive (no existing screen, endpoint or `plannedBetween` result changes) and is covered by a regression
test that the old method returns what it did. When this plan is accepted, a short "Amended by 035" note is added to the
spec of 023, 025 and 016 and to the roadmap C1 entry.

## Phases

1. Migration V70, feed amendments in 023, 025 and 016 with their regression and contract tests (blocks the rest).
2. Permission module, eligibility, seeding, navigation, controller skeleton and scope rules (US2 scope, SC-004).
3. `StatusRules`, `ZoneResolver`, calendar and summary for the three sources (US1, SC-002).
4. Needs attention (US3), filters, partial and empty states (US4, resilience).
5. Training SPI placeholder and "not available" source report (US5).
6. Frontend (grid, list, summary, filters, session-kept month and filters, detail), then Postman, demo data, roadmap and
   constitution row, and the 023, 025 and 016 spec notes.

## Open Items (need the user's decision; defaults are used until decided)

1. **Navigation section for the Manager.** The spec says MY WORK, but the navigation catalog has no such section (the
   Manager's "My Tasks" sits under OPERATIONS). Default: "My Activity Calendar" under OPERATIONS next to My Tasks. Confirm, or
   ask for a new section.
2. **History before the amendment.** Visits completed and drives held before V70 have no completion date, so they show as
   Done with no "done late" mark. Default: no backfill (a guess would be worse than none). Confirm, or approve a backfill of
   `completed_on` and `held_on` from Change History.
3. **Drive date changes.** A drive is shown as Rescheduled only when the number of removed and added dates is equal (paired in
   date order); adding or removing a date is not a reschedule. Default as stated; confirm this reading of "moved to another date".
