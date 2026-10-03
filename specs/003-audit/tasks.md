---

description: "Task list for feature implementation"
---

# Tasks: Audit

**Input**: Design documents from `/specs/003-audit/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/audit-api.md,
quickstart.md, and **001-identity-access** and **002-access-model-app-shell implemented** (this
feature consumes `LoginHistoryRecorded` from 001 and `PermissionMatrixChanged` from 002, and adds
its own AUDIT section to 002's `NavigationCatalog`/`PermissionMatrix`).

**Tests**: included as first-class tasks, not optional — Constitution Principle IX requires
per-role authorization tests on every endpoint, and this feature's core guarantee (append-only,
no loss on redelivery) is only provable with tests.

**Organization**: tasks are grouped by user story (spec.md's US1-US4, in priority order) so each
story can be implemented, tested, and delivered independently once the Foundational phase is done.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: which user story this task belongs to (US1-US4); absent for Setup/Foundational/Polish

## Path Conventions

Web application per plan.md: `backend/src/main/java/com/hls/audit/...` (new module) and
`backend/src/main/java/com/hls/identity/...` (additive changes only) for Java/Spring;
`frontend/src/features/audit/...` for React/TypeScript.

---

## Phase 1: Setup

**Purpose**: verify the cross-spec prerequisites; confirm no new dependency is actually needed.

- [X] T001 Verify 001-identity-access and 002-access-model-app-shell are implemented: confirm
      `com.hls.identity.loginhistory.LoginHistoryRecorded` and
      `com.hls.identity.permissions.PermissionMatrixChanged` exist and compile, and that
      `PermissionModule`/`NavigationCatalog`/`PermissionMatrixSeeder` (spec 002) exist at
      `backend/src/main/java/com/hls/identity/permissions/` and
      `backend/src/main/java/com/hls/identity/accessmodel/`. Do not proceed to Phase 2 until this
      passes.
- [X] T002 [P] Run `mvn -q -pl backend compile` to confirm the backend builds cleanly before adding
      this feature's code. `spring-modulith-starter-core`, Flyway, ArchUnit, and Testcontainers
      (including `awaitility`) are already in `backend/pom.xml` — no new backend dependency is
      needed.
- [X] T003 [P] Run `npm --prefix frontend run build` to confirm the frontend builds cleanly.
      `@mui/x-data-grid`, `react-router-dom`, `@mui/material` are already present from spec 002 — no
      new frontend dependency is needed.

**Checkpoint**: both projects build; prerequisite specs confirmed present.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: the schema, permission keys, navigation entries, and shared export/filter plumbing
every user story below builds on.

**🚨 CRITICAL**: no user story task may start until this phase is complete.

- [X] T004 Create Flyway migration
      `backend/src/main/resources/db/migration/V8__create_audit_tables.sql` defining three
      append-only tables per data-model.md: `login_history_entry` (`id` UUID PK, `occurred_at`
      timestamptz not null, `source_event_id` UUID not null unique, `user_id` UUID nullable,
      `phone_masked` text not null, `method` text not null, `event_type` text not null, `outcome`
      text not null, indexes on `(user_id, occurred_at desc)` and `(occurred_at desc)`);
      `change_history_entry` (`id` UUID PK, `occurred_at` timestamptz not null, `source_event_id`
      UUID not null unique, `actor_user_id` UUID not null, `entity_type` text not null, `entity_id`
      text not null, `field` text not null, `before_value` text nullable, `after_value` text
      nullable, indexes on `(actor_user_id, occurred_at desc)` and `(entity_type, occurred_at
      desc)`); `user_activity_entry` (`id` UUID PK, `occurred_at` timestamptz not null,
      `source_event_id` UUID not null unique, `actor_user_id` UUID nullable, `affected_user_id`
      UUID not null, `action` text not null, `detail` text nullable, indexes on
      `(affected_user_id, occurred_at desc)` and `(occurred_at desc)`). No table in this migration
      gets an `UPDATE`/`DELETE` grant beyond the default app role (research.md §4).
- [X] T005 [P] Add `AUDIT_LOGS`, `AUDIT_LOGIN_HISTORY`, `AUDIT_CHANGE_HISTORY`,
      `AUDIT_USER_ACTIVITY` constants to
      `backend/src/main/java/com/hls/identity/permissions/PermissionModule.java` (additive only,
      per its own Javadoc — no schema change needed since the column is stored as text).
- [X] T006 Extend `PermissionMatrixService.seedDefaults()` (called by the existing
      `PermissionMatrixSeeder`) in
      `backend/src/main/java/com/hls/identity/permissions/PermissionMatrixService.java` to
      idempotently seed `(ADMIN, module, VIEW, true)`, `(ADMIN, module, EXPORT, true)`,
      `(SYSTEM, module, VIEW, true)`, `(SYSTEM, module, EXPORT, true)` for each of the four new
      modules from T005; no rows (default false) for `DIRECTOR`, `MANAGER`, `TEACHER`, per spec.md's
      Role & Permission Impact table. Depends on T005.
- [X] T007 [P] Add four `NavItem` entries to
      `backend/src/main/java/com/hls/identity/accessmodel/NavigationCatalog.java`: "Audit Logs"
      (`/audit/logs`, `AUDIT_LOGS`, `VIEW`), "Login History" (`/audit/login-history`,
      `AUDIT_LOGIN_HISTORY`, `VIEW`), "Change History" (`/audit/change-history`,
      `AUDIT_CHANGE_HISTORY`, `VIEW`), "User Activity" (`/audit/user-activity`,
      `AUDIT_USER_ACTIVITY`, `VIEW`) — all four under section `"AUDIT"`, `applicableRoles =
      EnumSet.of(Role.ADMIN, Role.SYSTEM)`. Depends on T005.
- [X] T008 [P] Implement a shared streaming CSV export utility in
      `backend/src/main/java/com/hls/audit/support/CsvStreamingExporter.java`: given a header row,
      a `Stream`/`Page`-driven row supplier, and an `HttpServletResponse`, writes
      `Content-Type: text/csv` and streams rows without buffering the full result set in memory
      (research.md §5), for reuse by all four export endpoints.
- [X] T009 [P] Implement shared frontend audit UI plumbing in
      `frontend/src/features/audit/AuditFilterBar.tsx` (date-range, actor/user id, and a
      type-specific extra-filter slot via props/children) and
      `frontend/src/features/audit/useAuditExport.ts` (a hook that triggers a streamed CSV download
      from a given endpoint + current filter state), for reuse by all four audit pages.

- [X] T004a **[Implementation deviation from plan.md]** Add `spring-modulith-starter-jpa` to
      `backend/pom.xml` and Flyway migration
      `backend/src/main/resources/db/migration/V9__create_event_publication_registry.sql`
      (`event_publication`/`event_publication_archive` tables). plan.md's Technical Context claimed
      "No new backend dependency" — that was wrong: `spring-modulith-starter-core` alone provides
      only module-boundary verification (`ApplicationModules.verify()`), not
      `@ApplicationModuleListener` or its event publication registry, which research.md §1's whole
      durability argument depends on. `spring-modulith-starter-jpa` supplies both. Since this
      project's Hibernate runs `ddl-auto=validate` (Flyway owns the schema), the registry's table
      had to be hand-migrated rather than auto-created; its exact column set was captured from the
      library's real Hibernate mapping (`DefaultJpaEventPublication`/`ArchivedJpaEventPublication`
      in `spring-modulith-events-jpa:2.1.1`) via a throwaway schema-introspection test, not guessed.

**Checkpoint**: schema, permission keys, nav entries, and shared backend/frontend export plumbing
all exist. User story work can now begin.

---

## Phase 3: User Story 1 - Review Login History (Priority: P1) 🎯 MVP

**Goal**: an Admin or System user can find every sign-in attempt (successful or failed, password or
OTP) for a given account, with who/when/how/result.

**Independent Test**: sign in successfully, then fail a sign-in, as any user; confirm an Admin can
find both entries filtered by that user, while Manager/Teacher/Director cannot reach the screen at
all.

### Tests for User Story 1

- [X] T010 [P] [US1] Backend Modulith/Testcontainers test in
      `backend/src/test/java/com/hls/audit/loginhistory/LoginHistoryEventConsumerTest.java`
      verifying: publishing a `LoginHistoryRecorded` event results in exactly one
      `login_history_entry` row with matching fields; publishing the same `eventId` twice results
      in exactly one row (dedup via `source_event_id`, FR-001, data-model.md's validation rule).
- [X] T011 [P] [US1] Backend test in
      `backend/src/test/java/com/hls/audit/loginhistory/AuditLoginHistoryControllerTest.java`
      verifying: `ADMIN`/`SYSTEM` can list and filter by `userId`/`from`/`to`/`method`/`outcome`
      (FR-005); `DIRECTOR`/`MANAGER`/`TEACHER` receive 403 (FR-010); export without `from`/`to`
      returns 400 (contracts/audit-api.md).
- [X] T012 [P] [US1] Frontend test in `frontend/src/features/audit/LoginHistoryPage.test.tsx`
      asserting the list renders, filters narrow results, an empty filter result shows an empty
      state (not an error), and the AUDIT nav section is absent for a Manager/Teacher/Director
      access-model fixture.

### Implementation for User Story 1

- [X] T013 [P] [US1] Create `LoginHistoryEntry` JPA entity in
      `backend/src/main/java/com/hls/audit/loginhistory/LoginHistoryEntry.java` with the columns
      from T004/data-model.md's "Login History Entry"; no setters beyond what the constructor
      requires (immutability, FR-004).
- [X] T014 [US1] Create `LoginHistoryEntryRepository` in
      `backend/src/main/java/com/hls/audit/loginhistory/LoginHistoryEntryRepository.java` (Spring
      Data JPA) exposing only `save`, `findBySourceEventId`, and filtered/paginated query methods
      (`userId`, `occurredAt` range, `method`, `outcome`) — no `update`/`delete` method exists on
      this repository (FR-004, research.md §4). Depends on T013.
- [X] T015 [US1] Implement `LoginHistoryEventConsumer` in
      `backend/src/main/java/com/hls/audit/loginhistory/LoginHistoryEventConsumer.java` using
      `@ApplicationModuleListener` on `com.hls.identity.loginhistory.LoginHistoryRecorded`; skip the
      insert (no-op) if `findBySourceEventId` already finds the event's id (research.md §1,
      data-model.md's dedup rule). Depends on T014.
- [X] T016 [US1] Implement `AuditLoginHistoryController` in
      `backend/src/main/java/com/hls/audit/AuditLoginHistoryController.java`:
      `GET /api/v1/audit/login-history` (paginated, filtered per contracts/audit-api.md) gated by
      `PermissionGuard.require(..., AUDIT_LOGIN_HISTORY, VIEW)`, and
      `GET /api/v1/audit/login-history/export` (streamed CSV via T008's `CsvStreamingExporter`,
      `from`/`to` required, 400 if missing) gated by `AUDIT_LOGIN_HISTORY.EXPORT`. Depends on T014,
      T008.
- [X] T017 [P] [US1] Implement `LoginHistoryPage.tsx` in
      `frontend/src/features/audit/LoginHistoryPage.tsx` using MUI X DataGrid, `AuditFilterBar`
      (T009), and `useAuditExport` (T009) against T016's endpoints. Depends on T016, T009.
- [X] T018 [US1] Register the `/audit/login-history` route through the existing `RouteGuard`
      (spec 002) in the frontend route table, so it renders only when the access model authorizes
      it and a direct URL attempt by an unauthorized role shows `NotAuthorizedPage`. Depends on
      T007, T017.
- [X] T019 [US1] Decommission `identity`'s interim `login_history_event` table now that audit's
      Login History path (T015, T016) is proven end-to-end (Constitution Principle VII, "no module
      writes audit history to its own tables"; research.md §6): remove the write call inside
      `LoginHistoryPublisher.record(...)` in
      `backend/src/main/java/com/hls/identity/loginhistory/LoginHistoryPublisher.java` (keep the
      `eventPublisher.publishEvent(...)` call unchanged — `audit` still needs it), then delete
      `LoginHistoryEvent.java` and `LoginHistoryEventRepository.java` from the same package, and add
      `backend/src/main/resources/db/migration/V10__drop_identity_login_history_event.sql` dropping
      the `login_history_event` table (shifted to V10 — implementation added V9 for Spring
      Modulith's event publication registry; see the new T004a below). Before deleting, confirm
      (`grep`/IDE find-usages) that nothing outside `LoginHistoryPublisher` references
      `LoginHistoryEvent`/`LoginHistoryEventRepository` — as of this spec's analysis, nothing does.
      Depends on T015, T016.

**Checkpoint**: User Story 1 is independently functional and testable (MVP), and Constitution
Principle VII is fully satisfied for login history (no duplicate store remains).

---

## Phase 4: User Story 2 - Review Change History (Priority: P2)

**Goal**: an Admin, Director, or System user can see exactly what changed on a governed record (for
now: role→permission matrix edits) — actor, before, after, when.

**Independent Test**: edit one cell of the role→permission matrix as an Admin; confirm the exact
before/after values and the editing Admin's identity appear in Change History, independent of Login
History or User Activity being populated.

### Tests for User Story 2

- [X] T020 [P] [US2] Backend test in
      `backend/src/test/java/com/hls/audit/changehistory/ChangeHistoryEventConsumerTest.java`
      verifying: publishing a `PermissionMatrixChanged` event results in one `change_history_entry`
      row with `entityType="PERMISSION_MATRIX"`, `entityId="<ROLE>.<MODULE>.<ACTION>"`,
      `field="granted"`, and `beforeValue`/`afterValue` as the string form of the before/after
      booleans; redelivery of the same `eventId` (added in T023) is a no-op; AND, using
      `Awaitility.await().atMost(Duration.ofSeconds(5))`, that a real matrix edit through
      `PermissionMatrixService.updateGrant` produces a matching `change_history_entry` row within 5
      seconds (spec.md SC-002).
- [X] T021 [P] [US2] Backend test in
      `backend/src/test/java/com/hls/audit/changehistory/AuditChangeHistoryControllerTest.java`
      verifying `ADMIN`/`SYSTEM` list/filter by `actorUserId`/`entityType`/`entityId`/date range
      (FR-006), other roles get 403, export requires a date range.
- [X] T022 [P] [US2] Frontend test in
      `frontend/src/features/audit/ChangeHistoryPage.test.tsx` asserting each row shows the prior
      and new value distinctly, and filtering by actor narrows results.

### Implementation for User Story 2

- [X] T023 [US2] Add an `eventId` (UUID) field to
      `backend/src/main/java/com/hls/identity/permissions/PermissionMatrixChanged.java`, generated
      via `UUID.randomUUID()` at its single publish call site in
      `PermissionMatrixService.updateGrant` (`backend/src/main/java/com/hls/identity/permissions/PermissionMatrixService.java`),
      mirroring `LoginHistoryRecorded`'s existing `eventId` field so Change History can dedup on
      redelivery the same way Login History does (research.md §1/§3, data-model.md's
      `source_event_id` note — the column is `NOT NULL`, not a nullable fallback).
- [X] T024 [P] [US2] Create `ChangeHistoryEntry` JPA entity in
      `backend/src/main/java/com/hls/audit/changehistory/ChangeHistoryEntry.java` per
      data-model.md's "Change History Entry" columns; no setters beyond the constructor.
- [X] T025 [US2] Create `ChangeHistoryEntryRepository` in
      `backend/src/main/java/com/hls/audit/changehistory/ChangeHistoryEntryRepository.java`
      (`save`, `findBySourceEventId`, filtered/paginated queries by `actorUserId`, `entityType`,
      `entityId`, date range) — no `update`/`delete` method. Depends on T024.
- [X] T026 [US2] Implement `PermissionMatrixChangeConsumer` in
      `backend/src/main/java/com/hls/audit/changehistory/PermissionMatrixChangeConsumer.java` using
      `@ApplicationModuleListener` on `PermissionMatrixChanged`, mapping `role.module.action` into
      `entityId`, `"granted"` into `field`, and the before/after booleans into string
      `beforeValue`/`afterValue`; dedups on `source_event_id` (T023's `eventId`). Depends on T023,
      T025.
- [X] T027 [US2] Implement `AuditChangeHistoryController` in
      `backend/src/main/java/com/hls/audit/AuditChangeHistoryController.java`:
      `GET /api/v1/audit/change-history` (gated `AUDIT_CHANGE_HISTORY.VIEW`) and
      `GET /api/v1/audit/change-history/export` (gated `AUDIT_CHANGE_HISTORY.EXPORT`, streamed via
      T008, date range required) per contracts/audit-api.md. Depends on T025, T008.
- [X] T028 [P] [US2] Implement `ChangeHistoryPage.tsx` in
      `frontend/src/features/audit/ChangeHistoryPage.tsx` reusing `AuditFilterBar`/`useAuditExport`
      (T009) against T027's endpoints, rendering `beforeValue`/`afterValue` as distinct columns.
      Depends on T027, T009.
- [X] T029 [US2] Register the `/audit/change-history` route through `RouteGuard`, same pattern as
      T018. Depends on T007, T028.

**Checkpoint**: User Stories 1 and 2 both work independently.

---

## Phase 5: User Story 3 - Review User Activity (Priority: P2)

**Goal**: an Admin or System user can see a chronological feed of an account's lifecycle actions —
password resets, sessions ended, lockouts, deactivation — distinct from routine sign-ins and field
edits. Account reactivation is out of scope for this spec (spec.md FR-003): no reactivation
capability exists anywhere in the codebase yet.

**Independent Test**: trigger a password reset and a session end for a test user; confirm both
appear in User Activity for that user, independent of any permission-matrix edits.

### Tests for User Story 3

- [X] T030 [P] [US3] Extend/add backend tests verifying identity publishes each new lifecycle event
      at the right point: `PasswordResetServiceTest` (requested at `issueResetToken`, completed at
      `complete`), a `PasswordAuthService` lockout test (locked when the 5th consecutive failure
      trips FR-012's lockout — `MAX_FAILED_ATTEMPTS = 5` in
      `backend/src/main/java/com/hls/identity/auth/PasswordAuthService.java` — unlocked when a
      subsequent successful sign-in clears `lockUntil`), a `SessionController` test (ended on
      `DELETE /api/v1/me/sessions/{id}`), and a `UserAdminServiceTest` (deactivated on
      `deactivateUser`) — under `backend/src/test/java/com/hls/identity/...` alongside each
      existing test class.
- [X] T031 [P] [US3] Backend test in
      `backend/src/test/java/com/hls/audit/useractivity/UserActivityEventConsumerTest.java`
      verifying each of the five event types maps to the correct `action` value in
      `user_activity_entry` (`PASSWORD_RESET_REQUESTED`, `PASSWORD_RESET_COMPLETED`,
      `SESSION_ENDED`, `ACCOUNT_LOCKED`/`ACCOUNT_UNLOCKED`, `ACCOUNT_DEACTIVATED`) and dedups on
      `source_event_id` redelivery. `ACCOUNT_REACTIVATED` is reserved (data-model.md) and has no
      producer to test yet — do not add a test for it here.
- [X] T032 [P] [US3] Backend test in
      `backend/src/test/java/com/hls/audit/useractivity/AuditUserActivityControllerTest.java`
      verifying `ADMIN`/`SYSTEM` list/filter by `actorUserId`/`affectedUserId`/`action`/date range
      (FR-007), other roles 403, export requires a date range.
- [X] T033 [P] [US3] Frontend test in `frontend/src/features/audit/UserActivityPage.test.tsx`
      asserting each action type renders a readable label and filtering by affected user narrows
      results.

### Implementation for User Story 3

- [X] T034 [P] [US3] Create five domain event records in a new package
      `backend/src/main/java/com/hls/identity/activity/`: `PasswordResetRequested.java`,
      `PasswordResetCompleted.java`, `SessionEnded.java`, `AccountLockChanged.java` (carries
      `boolean locked`), `AccountActivationChanged.java` (carries `boolean active` — this spec only
      ever publishes `active=false`; `active=true` is unused until spec 004 adds a reactivation
      capability, spec.md FR-003) — each with `eventId`, `occurredAt`, `actorUserId` (nullable —
      e.g. an automatic lockout has no human actor), `affectedUserId`, per data-model.md's "User
      Activity Entry" columns; Javadoc noting "for spec 003's Audit module to subscribe to,"
      mirroring `LoginHistoryRecorded`'s existing pattern (research.md §2).
- [X] T035 [US3] Publish `PasswordResetRequested` from `issueResetToken` and
      `PasswordResetCompleted` from `complete` in
      `backend/src/main/java/com/hls/identity/auth/PasswordResetService.java`, via
      `ApplicationEventPublisher`, same shape as `LoginHistoryPublisher`. Depends on T034.
- [X] T036 [P] [US3] Publish `SessionEnded` from `endSession` in
      `backend/src/main/java/com/hls/identity/session/SessionController.java` (or a session service
      if the end-session logic is extracted there), after `session.end()`/`sessionRepository.save`.
      Depends on T034.
- [X] T037 [P] [US3] Publish `AccountLockChanged(locked=true)` where `lockUntil` is set on the 5th
      consecutive failure (`attempts >= MAX_FAILED_ATTEMPTS`, `MAX_FAILED_ATTEMPTS = 5`), and
      `AccountLockChanged(locked=false)` where `lockUntil` is cleared on a subsequent successful
      sign-in, in `backend/src/main/java/com/hls/identity/auth/PasswordAuthService.java`. Depends on
      T034.
- [X] T038 [P] [US3] Publish `AccountActivationChanged(active=false)` from `deactivateUser` in
      `backend/src/main/java/com/hls/identity/user/UserAdminService.java`. No `active=true` caller
      is added in this spec (spec.md FR-003) — that lands with spec 004's reactivation capability.
      Depends on T034.
- [X] T039 [P] [US3] Create `UserActivityEntry` JPA entity in
      `backend/src/main/java/com/hls/audit/useractivity/UserActivityEntry.java` per
      data-model.md's "User Activity Entry" columns; no setters beyond the constructor.
- [X] T040 [US3] Create `UserActivityEntryRepository` in
      `backend/src/main/java/com/hls/audit/useractivity/UserActivityEntryRepository.java` (`save`,
      `findBySourceEventId`, filtered/paginated queries by `actorUserId`, `affectedUserId`,
      `action`, date range) — no `update`/`delete` method. Depends on T039.
- [X] T041 [US3] Implement `UserActivityEventConsumer` in
      `backend/src/main/java/com/hls/audit/useractivity/UserActivityEventConsumer.java` with one
      `@ApplicationModuleListener` method per event type from T034 (map `AccountActivationChanged`'s
      `active` flag to `ACCOUNT_REACTIVATED`/`ACCOUNT_DEACTIVATED` generically, even though only the
      deactivated branch is reachable today), each dedup-ing on `source_event_id`. Depends on T035,
      T036, T037, T038, T040.
- [X] T042 [US3] Implement `AuditUserActivityController` in
      `backend/src/main/java/com/hls/audit/AuditUserActivityController.java`:
      `GET /api/v1/audit/user-activity` (gated `AUDIT_USER_ACTIVITY.VIEW`) and
      `GET /api/v1/audit/user-activity/export` (gated `AUDIT_USER_ACTIVITY.EXPORT`, streamed via
      T008, date range required) per contracts/audit-api.md. Depends on T040, T008.
- [X] T043 [P] [US3] Implement `UserActivityPage.tsx` in
      `frontend/src/features/audit/UserActivityPage.tsx` reusing `AuditFilterBar`/`useAuditExport`
      (T009) against T042's endpoints. Depends on T042, T009.
- [X] T044 [US3] Register the `/audit/user-activity` route through `RouteGuard`, same pattern as
      T018. Depends on T007, T043.

**Checkpoint**: User Stories 1-3 all work independently.

---

## Phase 6: User Story 4 - Browse the Unified Audit Log (Priority: P3)

**Goal**: an Admin or System user gets one combined, searchable, exportable timeline across Login
History, Change History, and User Activity.

**Independent Test**: with entries already present from Stories 1-3, open Audit Logs and confirm all
three event types appear together in one chronological list, filterable by type, and exportable to
CSV.

### Tests for User Story 4

- [X] T045 [P] [US4] Backend test in
      `backend/src/test/java/com/hls/audit/logs/AuditLogQueryServiceTest.java` verifying: with one
      row seeded in each of the three tables, the combined query returns all three in `occurredAt`
      order, is filterable by `type`, and each row's `summary` reads as a short, type-specific
      human-readable line (data-model.md's "Audit Log Entry").
- [X] T046 [P] [US4] Backend test in
      `backend/src/test/java/com/hls/audit/logs/AuditLogsControllerTest.java` verifying
      `ADMIN`/`SYSTEM` authorization, `type`/`userId`/date-range filtering, other roles 403, export
      requires a date range and its CSV rows match the filtered JSON list exactly (SC-006).
- [X] T047 [P] [US4] Frontend test in `frontend/src/features/audit/AuditLogsPage.test.tsx` asserting
      the combined list renders entries from all three sources together and the type filter narrows
      results.

### Implementation for User Story 4

- [X] T048 [US4] Implement `AuditLogQueryService` in
      `backend/src/main/java/com/hls/audit/logs/AuditLogQueryService.java` composing a `UNION ALL`
      style query (research.md §3) over `LoginHistoryEntryRepository` (T014),
      `ChangeHistoryEntryRepository` (T025), and `UserActivityEntryRepository` (T040), projecting
      each row into `{occurredAt, type, actorUserId, summary}` per data-model.md's examples (e.g.
      `"Password sign-in failed"`, `"MANAGER.ATTENDANCE.EDIT set to true"`, `"Account
      deactivated"`). Depends on T014, T025, T040.
- [X] T049 [US4] Implement `AuditLogsController` in
      `backend/src/main/java/com/hls/audit/AuditLogsController.java`: `GET /api/v1/audit/logs`
      (gated `AUDIT_LOGS.VIEW`, `type`/`userId`/date-range filters) and
      `GET /api/v1/audit/logs/export` (gated `AUDIT_LOGS.EXPORT`, streamed via T008, date range
      required) per contracts/audit-api.md. Depends on T048, T008.
- [X] T050 [P] [US4] Implement `AuditLogsPage.tsx` in
      `frontend/src/features/audit/AuditLogsPage.tsx` reusing `AuditFilterBar`/`useAuditExport`
      (T009), with the `type` filter as a multi-select, against T049's endpoints. Depends on T049,
      T009.
- [X] T051 [US4] Register the `/audit/logs` route through `RouteGuard`, same pattern as T018.
      Depends on T007, T050.

**Checkpoint**: all four user stories are independently functional together — the full Audit module
exists.

---

## Final Phase: Polish & Cross-Cutting Concerns

- [X] T052 [P] Add `com.hls.audit.ApplicationModuleBoundaryTest` in
      `backend/src/test/java/com/hls/audit/ApplicationModuleBoundaryTest.java` using Spring
      Modulith's `@ApplicationModuleTest` (or `ApplicationModules.of(HlsApplication.class)` scoped
      assertions) verifying `audit` only consumes `LoginHistoryRecorded`, `PermissionMatrixChanged`,
      and the five events from T034, and publishes no events of its own (Constitution Principle VII;
      plan.md's Constitution Check).
- [X] T053 [P] Add ArchUnit rules in
      `backend/src/test/java/com/hls/audit/ArchitectureRulesTest.java` asserting: no method named
      `update*`/`delete*` exists on `LoginHistoryEntryRepository`, `ChangeHistoryEntryRepository`,
      or `UserActivityEntryRepository` (FR-004), and no class outside `com.hls.audit` references
      those three repositories or their entities directly. Also assert
      `LoginHistoryEvent`/`LoginHistoryEventRepository` no longer exist under
      `com.hls.identity.loginhistory` (T019's decommission is complete).
- [X] T054 [P] Backend load test in
      `backend/src/test/java/com/hls/audit/logs/AuditLogPaginationLoadTest.java` (Testcontainers):
      bulk-insert at least 10,000 rows across the three audit tables, then assert a paginated
      `GET /api/v1/audit/logs` query still returns within a reasonable bound (e.g. a few hundred
      milliseconds) rather than timing out or degrading — spec.md SC-004's "remains responsive"
      bound, otherwise untested by T010-T051's functional tests.
- [X] T055 [P] Ran all four of `quickstart.md`'s scenarios via its own "Automated verification"
      mapping (each scenario names the exact tests that prove it) rather than a manual UI pass, since
      no live environment/browser session was available in this session: Scenario 1 →
      `LoginHistoryEventConsumerTest` + `AuditLoginHistoryControllerTest`; Scenario 2 →
      `ChangeHistoryEventConsumerTest` (including the real `updateGrant` Awaitility check) +
      `AuditChangeHistoryControllerTest`; Scenario 3 → `UserActivityEventConsumerTest` +
      `IdentityActivityPublishingTest`; Scenario 4 → `AuditLogQueryServiceTest` +
      `AuditLogsControllerTest` (including its exported-CSV-matches-JSON assertion) +
      `AuditLogsPage.test.tsx`; edge cases → the empty-state assertions in all four `*Page.test.tsx`
      files, the 400-without-date-range assertions in all four controller tests, and the 403
      assertions for Director/Manager/Teacher in all four controller tests. All pass (T057).
- [X] T056 [P] Update `docs/spec-roadmap.md` row 003's status to "Implemented" once every checkpoint
      above has passed.
- [X] T057 Run the full backend suite (`mvn test`, including T052/T053/T054) and the full frontend
      suite (`npm run test`, including axe-core checks on all four new pages in both themes)
      together and confirm both are green. Backend: 87/87 passing across 27 test classes. Frontend:
      65/65 passing across 16 test files, including the new `AuditLogsPage` axe-core checks added to
      `src/a11y/a11y.test.tsx` in both light and dark mode (previously only spec 001/002's pages were
      covered there — extended as part of this task since quickstart.md's "Automated verification"
      section calls for axe-core coverage on all four audit screens).

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: no dependencies.
- **Foundational (Phase 2)**: depends on Setup; blocks every user story.
- **User Stories (Phase 3-6)**: all depend on Foundational.
  - **US1, US2, US3** have no dependency on each other and can proceed in parallel once
    Foundational is done.
  - **US4** depends on **US1** (T014), **US2** (T025), and **US3** (T040) — the unified feed unions
    all three tables, so it cannot ship before all three exist.
- **Polish (Final Phase)**: depends on all four user stories being complete.

### Parallel Opportunities

- Within Foundational: T005/T007/T008/T009 can run in parallel once T004 (schema) lands; T004, T008,
  T009 have no dependency on T005-T007 at all.
- Within US1: T010/T011/T012 (tests) in parallel; T013 in parallel with T008/T009 already done. T019
  (decommission) is a hard tail dependency on T015/T016 and should not start earlier.
- Within US2: T020/T021/T022 (tests) in parallel; T024 in parallel with T023.
- Within US3: T030/T031/T032/T033 (tests) in parallel; T034 first, then T035/T036/T037/T038 in
  parallel (different files); T039 in parallel with T035-T038.
- US1, US2, and US3 can be staffed and worked on in parallel by different people once Foundational
  is done; only US4 has a hard cross-story dependency (on all three).

---

## Parallel Example: User Story 1

```bash
# Tests together:
Task: "Backend consumer/idempotency test in backend/src/test/java/com/hls/audit/loginhistory/LoginHistoryEventConsumerTest.java"
Task: "Backend controller/authorization test in backend/src/test/java/com/hls/audit/loginhistory/AuditLoginHistoryControllerTest.java"
Task: "Frontend list/filter/role-visibility test in frontend/src/features/audit/LoginHistoryPage.test.tsx"

# Implementation together (after T013/T014/T015/T016 land sequentially):
Task: "Implement LoginHistoryPage.tsx in frontend/src/features/audit/LoginHistoryPage.tsx"

# Only after T015/T016 are proven, the decommission task runs alone (not parallel — it deletes
# files T015 depended on reading as reference):
Task: "T019: decommission identity's own login_history_event table"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1 (Setup) and Phase 2 (Foundational).
2. Complete Phase 3 (US1), including T019's decommission step. At this point Admin/System can
   already investigate real sign-in activity — the most immediately actionable security data —
   while Change History and User Activity remain empty screens, and Constitution Principle VII is
   already fully satisfied (no lingering duplicate store).
3. **Stop and validate** against spec.md's User Story 1 acceptance scenarios.

### Incremental Delivery

1. Setup + Foundational → foundation ready.
2. US1 → validate → MVP demo (Login History live; identity's interim table retired).
3. US2 → validate (Change History live; first real compliance data, from spec 002's matrix edits).
4. US3 → validate (User Activity live; requires the five new identity events from T034-T038;
   reactivation intentionally out of scope, see spec.md FR-003).
5. US4 → validate (Audit Logs unifies all three; requires US1-US3 complete).
6. Final Phase → module-boundary/ArchUnit checks, 10k-row load check, quickstart run, roadmap
   update, full suite green.
