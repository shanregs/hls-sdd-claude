# Research: Notifications

Decisions that the plan rests on. Format: Decision, Rationale, Alternatives considered.

## 1. Creating notifications in the producer's transaction

**Decision**: producers publish typed Spring events from their own `api` packages inside their transaction;
`notification` handles them with plain `@EventListener` methods (synchronous, so they run in the publisher's
transaction). The notification row therefore commits or rolls back with the leave decision or mark.

**Rationale**: the spec requires "none lost or invented". The audit module's `@ApplicationModuleListener`
runs after commit and asynchronously, which can lose a notification if the process stops between commit and
delivery, and cannot be rolled back with the event. A synchronous listener is the simplest way to get
atomicity, and the producers stay independent of `notification`.

**Alternatives**: (a) `@ApplicationModuleListener` (after commit): at-least-once but not atomic with the
event. (b) Producers call a `Notifier` interface directly: couples leave and attendance to notification.
(c) Transactional outbox table: more machinery than one table needs.

## 2. Who receives a leave request

**Decision**: `RecipientResolver` asks `organization.api.ManagerQueries.managerOfSchool(schoolId)` for the
School of the request (stored on `leave_request.school_id`). If a Manager with an active user exists, only
that user is notified. Otherwise every active user holding the Admin or Director role is notified (through
the public `identity.user` role assignments).

**Rationale**: spec clarification (Manager only, Admin and Director when there is no Manager). A School has
one current Manager in the master data model, so "the Manager(s)" is that one user.

**Alternatives**: always notify Admin and Director (rejected in clarify); walk the Zone's Managers (the
School's Manager is the scope owner).

## 3. Merging attendance-change notifications

**Decision**: a `group_key` of `attendance:{actorUserId}:{teacherId}:{yyyy-MM}`. When a change event arrives,
look for an unread notification of the recipient with that key whose `updated_at` is within 10 minutes; if
found, add the date to its `detail` (a comma-separated set of ISO dates), rebuild title and message from the
set and bump `updated_at`; otherwise insert a new row. Read notifications are never merged into.

**Rationale**: matches the clarification (same actor, Teacher, month, 10 minutes, unread). Storing the dates
makes the day count exact when the same day is corrected twice. The lookup uses a partial index on unread
rows.

**Alternatives**: a count column (inexact for repeated days); merging by time only (would hide who acted).

## 4. Attendance events

**Decision**: `AttendanceMarkChanged(teacherId, date, actorUserId, kind)` is published by `MarkService` after
`setMark`/`clearMark` when the kind is `SUPERVISOR` and the mark is not leave-made (`setLeaveMark` publishes
nothing). `AttendanceMonthLocked` and `AttendanceMonthReopened` are published per Teacher by
`MonthLockService` inside its loop. `notification` resolves the Teacher's user through
`TeacherDirectory.teacherInfo(...).userId()` and does nothing when it is null.

**Rationale**: spec FR-003/FR-004. Leave approval already has its own notification; one per leave-marked day
would be noise.

## 5. The bell and polling

**Decision**: `NotificationBell` calls `GET /api/v1/me/notifications/unread-count` every 30 s while the tab
is visible (pauses when hidden) and on focus; the page reloads its list on open. No WebSocket or SSE.

**Rationale**: spec assumption (30 s is enough); polling is simple, works through the existing proxy and
auth, and a count request is a single index scan.

**Alternatives**: SSE/WebSocket (more infrastructure for a 30 s requirement); polling the full list.

## 6. Permission and navigation

**Decision**: module `NOTIFICATIONS(VIEW, DELETE)`, eligible for Admin, Director, Manager, Teacher (System is
excluded as a business module). VIEW covers list, count, open and mark read; DELETE covers delete and clear
read. Navigation: ACCOUNT → Notifications (`/account/notifications`), plus the bell, shown only when the
access model contains that item.

**Rationale**: the bell and the menu item must agree with the permission, and the access model is already
the server's source of truth for navigation.

## 7. Own scope

**Decision**: every query filters by `recipient_user_id = caller`. A foreign or unknown id answers 404 from
the same query (`findByIdAndRecipientUserId`).

**Rationale**: Principle III, and avoids leaking existence.

## 8. Retention

**Decision**: `@Scheduled(cron = "0 30 2 * * *")` in `RetentionJob` deletes rows with `created_at` older than
90 days (`updated_at` for merged ones is not used). The job is a public method so a test can call it with a
fixed clock. `@EnableScheduling` is added to the application class; the job is disabled in tests via a
property so it does not fire during the suite.

**Rationale**: spec FR-010, one cheap indexed delete per day.

## 9. Text and links

**Decision**: `MessageFactory` builds title (80 chars) and message (300 chars), truncating reasons at 200
characters with an ellipsis, formatting dates DD/MM/YYYY. Links are app routes only: `/leave/history`,
`/operations/leave`, `/my-attendance?month=YYYY-MM`.

**Rationale**: consistent text, testable in isolation, no HTML in stored text.

## 10. Demo seed

**Decision**: `NotificationDevSeeder` (demo flag only, idempotent per user: skipped when the user already has
any notification) gives Tara a read "leave approved", an unread "leave rejected" and an unread "attendance
updated"; Manoj two unread "new leave request" notices for Meena and Karthik; Asha one for Lakshmi (no
Manager). Runs after `LeaveDevSeeder`.

**Rationale**: spec FR-012; `LeaveDevSeeder` writes requests directly, so no events fired for the demo data.
