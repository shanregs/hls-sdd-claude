# Spec 010 notifications: progress and handoff (2026-10-05)

Branch `feature/010-notifications` (stacked on `feature/009-leave`, PR #13). Spec, clarifications, plan, research,
data model, contract, quickstart and tasks are done and pushed. Implementation is in progress.

## Done and pushed

- T001-T009 (commit `acd31b4`): `notification` module skeleton, migration `V18`, `Notification` entity and
  repository, `NotificationService` (create, merge, own-scope operations, purge), `MessageFactory`, permission module
  `NOTIFICATIONS`, ACCOUNT -> Notifications navigation item (order 93, after Sessions), tests for all of these.
  Passing: `NotificationModuleRulesTest`, `MessageFactoryTest` (8), `NotificationServiceTest` (7),
  `PermissionEligibilityTest`, `NavigationSectionOrderTest`, `ApplicationModulesTest`.

## Also done (2026-10-05)

- T010-T011: `NotificationController` and `NotificationApiTest` (4 tests) pass.
- T012-T013 (commit `1de8bfd`): `features/notifications/` with `notificationsApi.ts`, `NotificationBell` (mounted in
  `AppShell`), `NotificationsPage`, route `/account/notifications`, 18 tests, a11y cases in both themes.
- T014-T018: events `LeaveDecided`/`LeaveRequested`/`LeaveCancelled` in `leave/api`, published from
  `LeaveDecisionService` and `LeaveRequestService`; `RecipientResolver` and `LeaveEventListener` in
  `notification/internal`; `LeaveDecisionNotificationTest` (8) and `LeaveRequestNotificationTest` (7) pass, and the
  existing leave suite still passes. Added `RoleAssignmentRepository.userIdsWithRole` (no lock) because `findByRole`
  takes a pessimistic write lock that a recipient lookup must not take.

- T019-T021: events `AttendanceMarkChanged`/`AttendanceMonthLocked`/`AttendanceMonthReopened` in `attendance/api`,
  published from `MarkService` (supervisor set and clear only) and `MonthLockService` (`freeze` covers lock and
  relock); `AttendanceEventListener`; `AttendanceNotificationTest` (9) passes, attendance suite still passes.
  Note: `AttendanceDevSeeder` marks as SUPERVISOR, so seeding now creates attendance notifications; handle in T024.

- T022-T024: `RetentionJob` (daily 02:30, `hls.notification.retention.enabled`, off in `IntegrationTestBase`),
  `@EnableScheduling`, `NotificationDevSeeder` (idempotent per user and type; Tara usually already has an attendance
  notice from the real event path because the attendance seeder marks as a supervisor), `NotificationRetentionTest` (4),
  `NotificationDevSeederTest` (4). Wider regression: 140 tests green.
- V19 (`V19__index_review.sql`) reviewed by the database-reviewer agent; comment corrected, OTP index dropped from it.
  Schema dump after V19: `docs/db/schema-v19.sql`.

## Next, in order (see tasks.md)

1. T025-T028 docs, Postman, roadmap, quickstart results, PR (PR must wait for #13 to merge, or target it).
2. Run the full backend and frontend suites once before the PR.

## Environment notes

- Local app: backend on 8080 (`scripts/run-backend.ps1`, demo data on), frontend on 5173, Postgres in Docker on 5433.
  The running backend does not have the notification code yet; restart it after the backend work to apply `V18`.
- Demo logins (all `Password123!`, or a one-time code): Asha 9800000001, Divya 9800000002, Manoj 9800000003,
  Tara 9800000004, Sunil 9800000005.
- Machine is slow: a single `mvn -o test -Dtest=...` run takes minutes; run one class at a time.

## Open pull requests

- #10 Role & Permissions icon colours, #11 Settings tabs, #13 Spec 009 leave (includes dashboard widget, Tara
  password, one-time code link), #14 Sunday-first calendars. All waiting for the user to merge.
