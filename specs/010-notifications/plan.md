# Implementation Plan: Notifications

**Branch**: `010-notifications` | **Date**: 2026-10-05 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/010-notifications/spec.md`

## Summary

Adds the `notification` bounded context (Constitution Principle VII):

- A **notification store** (one row per recipient user, type, title, message, link, channel, read time) with
  an own-scope API: list (paged, unread filter), unread count, open/mark read, mark all read, delete, clear
  read.
- **Event-driven creation in the producer's transaction.** Leave and attendance publish small typed events
  from their own `api` packages (`LeaveRequested`, `LeaveDecided`, `LeaveCancelled`,
  `AttendanceMarkChanged`, `AttendanceMonthLocked`, `AttendanceMonthReopened`); `notification` handles them
  with synchronous listeners, so the notification row commits or rolls back with the event and the producers
  never depend on `notification`.
- **Recipient rules** in one place: the Teacher's own user for decisions and attendance changes; the School's
  Manager (through `organization.api.ManagerQueries`), or every active Admin and Director when there is none,
  for leave requests and cancellations.
- **Merging** of attendance-change notifications: the same actor, Teacher and month within 10 minutes of the
  last change, while still unread, update one row (distinct day list and count).
- A **header bell** with unread count (polled every 30 s) and ACCOUNT → Notifications page.
- **Housekeeping**: a daily job deletes notifications older than 90 days.
- A **delivery channel** column (only `IN_APP`) keeps the door open for SMS or push without touching events.

`notification` depends on `leave.api`, `attendance.api`, `teacher.api`, `organization.api` and the public
`identity.user` types only; nothing depends on `notification.internal`. `identity` gets one permission module,
seed, eligibility, navigation item and data-scope entry; `leave` and `attendance` publish events.

## Technical Context

**Language/Version**: Java 25 (Spring Boot 4.1.1-based); TypeScript 5.7 with React 19 - unchanged.

**Primary Dependencies**: Spring Web, Spring Data JPA, Spring Security, Spring Modulith, Flyway, ArchUnit;
`@EnableScheduling` for the clean-up job (Spring core, no new library). Frontend: React Router, MUI. No new
dependency.

**Storage**: PostgreSQL via Flyway `V18__create_notification_table.sql` (one table, indexes for the
recipient list, the unread count, the merge lookup and retention). No cross-module foreign keys; ids are
plain.

**Testing**: JUnit 5, Spring Boot Test, Testcontainers (`IntegrationTestBase`); per-role/per-action
authorization tests on every endpoint; own-scope tests (two users); one test per event rule (recipient,
text, link, no notification on failure or rollback); a merge test; a retention test; ArchUnit rules; a
migration-free unit test for message building. Frontend: Vitest + Testing Library with role fixtures, bell
polling with fake timers, axe in both themes.

**Target Platform**: Browser; single Spring Boot deployable.

**Project Type**: Web application (frontend + backend).

**Performance Goals**: unread count under 200 ms and first list page under 1 s for 5,000 notifications
(partial index on unread rows, descending index on `(recipient, created_at)`); the bell adds one small
request every 30 s per open tab.

**Constraints**: every endpoint reads the recipient from the JWT only, never from the request, so one user
cannot reach another's rows (404, not 403, for a foreign id); events never fail because a recipient is
missing (no Teacher user, no Manager and no Admin: nothing is created); message text carries only data the
recipient may already see on the linked screen; no PII beyond names already shown elsewhere.

**Scale/Scope**: low thousands of users, tens of notifications per user per month; one module, 1 table,
6 endpoints, 2 screens (bell, page).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Principle I (Operational Truth Must Be Auditable)** - PASS. Notifications are derived from audited
  events and never replace them; creating, reading and deleting a user's own notifications need no audit
  entry (spec FR-009). The source event is unchanged.
- **Principle II (Five Fixed Roles, Configurable Permissions)** - PASS. No new role; module `NOTIFICATIONS`
  seeded for four roles, runtime-editable, System ineligible.
- **Principle III (Data Scope)** - PASS. Own-scope only, derived from the signed-in user; the Manager
  recipient comes from the shared `ManagerQueries`; text is limited to data in the recipient's scope.
- **Principle IV (Role-Based Experience)** - PASS. Server-driven navigation item, bell shown only with the
  permission, consistent loading/empty/error states, DD/MM/YYYY dates, keyboard- and screen-reader-usable
  (the unread count is announced).
- **Principle VII (Modular Monolith)** - PASS. Producers publish events from their `api` packages; the
  `notification` module listens; ArchUnit proves no producer depends on it.
- **Principle IX (Reliability, Testability)** - PASS, requires the tests listed above.
- **Principle X (Security, Identity, Observability)** - PASS. Recipient from the JWT; length limits on
  stored text; text rendered as plain text in the UI; structured logs without message bodies.
- **Principles V, VI, VIII, XI** - not applicable.

No violations requiring justification. Complexity Tracking is not needed.

## Project Structure

### Documentation (this feature)

```text
specs/010-notifications/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/
│   └── notifications-api.md
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
backend/src/main/java/com/hls/
├── identity/
│   ├── permissions/PermissionModule.java       # + NOTIFICATIONS(VIEW, DELETE)
│   ├── permissions/PermissionEligibility.java  # System excluded (business module)
│   ├── permissions/PermissionMatrixService.java    # + seed: Admin, Director, Manager, Teacher
│   └── accessmodel/{NavigationCatalog,AccessModelService}.java  # + ACCOUNT -> Notifications, scope Own
├── leave/api/                                  # LeaveRequested, LeaveDecided, LeaveCancelled (records)
├── leave/internal/{LeaveRequestService,LeaveDecisionService}.java   # publish the events
├── attendance/api/                             # AttendanceMarkChanged, AttendanceMonthLocked, AttendanceMonthReopened
├── attendance/internal/{MarkService,MonthLockService}.java          # publish the events
└── notification/
    ├── api/                                    # NotificationType, (reserved) NotificationChannel
    ├── internal/                               # Notification entity, repository, NotificationService,
    │                                           #   RecipientResolver, event listeners, MessageFactory,
    │                                           #   RetentionJob, NotificationDevSeeder
    └── web/                                    # NotificationController

backend/src/main/java/com/hls/HlsApplication.java   # + @EnableScheduling
backend/src/main/resources/db/migration/V18__create_notification_table.sql
backend/src/test/java/com/hls/notification/         # events, recipients, merge, own scope, authorization, retention, rules

frontend/src/features/notifications/
├── NotificationBell.tsx          # header bell with unread count, 30 s polling
├── NotificationsPage.tsx         # ACCOUNT -> Notifications list
└── notificationsApi.ts
frontend/src/app/AppShell.tsx     # + bell in the header
frontend/src/App.tsx              # + guarded route /account/notifications
```

**Structure Decision**: Web application (unchanged layout). One new backend module with the `api` /
`internal` / `web` split, one migration, additive changes to `identity`, `leave` and `attendance`, and one
new frontend feature folder.

### Delivery slices (for tasks.md, each independently demoable)

1. **Foundation**: permission, eligibility, navigation, migration, module skeleton, entity and service.
2. **Own-scope API and screens (US3)**: controller, bell, page, tests.
3. **Leave events (US1, US2)**: events, listeners, recipients, tests.
4. **Attendance events (US4)**: events, merge, lock/reopen, tests.
5. **Retention, seed, polish (US5)**: job, demo data, docs, Postman, a11y.

## Complexity Tracking

No violations. Table intentionally omitted.
