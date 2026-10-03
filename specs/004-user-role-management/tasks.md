---

description: "Task list for feature implementation"
---

# Tasks: User & Role Management

**Input**: Design documents from `/specs/004-user-role-management/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/user-management-api.md,
quickstart.md, and **002-access-model-app-shell** and **003-audit implemented** (this feature adds
its own `USER_MANAGEMENT` module to 002's `PermissionMatrixSeeder`/`NavigationCatalog`, and feeds
003's existing `UserActivityEventConsumer` with three new event types plus the existing `AccountActivationChanged`).

**Tests**: included as first-class tasks — Constitution Principle IX requires per-role
authorization tests on every endpoint, and this spec's own Independent Test per user story
(including the last-admin safeguard's boundary cases) is exactly that kind of test.

**Organization**: tasks are grouped by user story (spec.md's US1-US6, in priority order).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: which user story this task belongs to (US1-US6); absent for Setup/Foundational/Polish

## Path Conventions

Web application per plan.md: `backend/src/main/java/com/hls/identity/...` (additive changes) and
`backend/src/main/java/com/hls/audit/...` (additive changes only) for Java/Spring;
`frontend/src/features/users/...` for React/TypeScript.

---

## Phase 1: Setup

**Purpose**: verify the cross-spec prerequisites; confirm no new dependency is actually needed.

- [X] T001 Verify 002-access-model-app-shell and 003-audit are implemented: confirm
      `PermissionMatrixSeeder`/`PermissionMatrixService`/`NavigationCatalog`
      (`backend/src/main/java/com/hls/identity/permissions/`,
      `backend/src/main/java/com/hls/identity/accessmodel/`) and
      `UserActivityEventConsumer`/`UserActivityEntryRepository`
      (`backend/src/main/java/com/hls/audit/useractivity/`) exist and compile. Do not proceed to
      Phase 2 until this passes.
- [X] T002 [P] Run `mvn -q -pl backend compile` and `npm --prefix frontend run build` to confirm
      both projects build cleanly before adding this feature's code. No new backend or frontend
      dependency is needed (plan.md's Technical Context).

**Checkpoint**: both projects build; prerequisite specs confirmed present.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: the new permission key, navigation entries, shared password-policy helper,
role-assignment query methods, the last-admin safeguard itself, and the new domain events every
user story below builds on.

**🚨 CRITICAL**: no user story task may start until this phase is complete.

- [X] T003 Add `USER_MANAGEMENT` constant to
      `backend/src/main/java/com/hls/identity/permissions/PermissionModule.java` (additive only,
      matching spec 003's four `AUDIT_*` constants).
- [X] T004 Extend `PermissionMatrixService.seedDefaults()` in
      `backend/src/main/java/com/hls/identity/permissions/PermissionMatrixService.java` to
      idempotently seed `(ADMIN, USER_MANAGEMENT, VIEW, true)`, `(ADMIN, USER_MANAGEMENT, CREATE,
      true)`, `(ADMIN, USER_MANAGEMENT, EDIT, true)`, and the same three for `SYSTEM`; no rows
      (default false) for `DIRECTOR`, `MANAGER`, `TEACHER` (spec.md's Role & Permission Impact
      table). Depends on T003.
- [X] T005 [P] Add two `NavItem` entries to
      `backend/src/main/java/com/hls/identity/accessmodel/NavigationCatalog.java`: "User
      Management" (`/identity/users`, `USER_MANAGEMENT`, `VIEW`) under section `"SYSTEM"` for
      `EnumSet.of(Role.ADMIN)`, order 15 (before the existing Role & Permissions entry's order
      20); and the same label/route/module/action under section `"SYSTEM CONFIGURATION"` for
      `EnumSet.of(Role.SYSTEM)`, order 15. Depends on T003.
- [X] T006 Extract `PasswordPolicy` in `backend/src/main/java/com/hls/identity/auth/PasswordPolicy.java`
      (research.md §2): a static `violation(String newPassword, String phone)` (or equivalent)
      returning whether the password is shorter than 10 characters or equal to `phone`. Rework
      `PasswordResetService.complete` in
      `backend/src/main/java/com/hls/identity/auth/PasswordResetService.java` to call it instead of
      its current inline check, with no behavior change (existing `PasswordResetIntegrationTest`-style
      tests must stay green).
- [X] T007 [P] Add `reactivate()` to `backend/src/main/java/com/hls/identity/user/AppUser.java`
      (data-model.md): sets `active = true`, mirroring the existing `deactivate()`.
- [X] T008 [P] Add `findByRole(Role)` and `deleteByUserIdAndRole(UUID, Role)` to
      `backend/src/main/java/com/hls/identity/user/RoleAssignmentRepository.java`.
- [X] T009 [P] Add a case-insensitive free-text `search(String term, Pageable pageable)` returning
      `Page<AppUser>` to `backend/src/main/java/com/hls/identity/user/AppUserRepository.java` via a
      `@Query` JPQL method matching `term` against `display_name` or `phone` with `LIKE`
      (research.md §6).
- [X] T010 Create `LastAdminGuard` in
      `backend/src/main/java/com/hls/identity/user/LastAdminGuard.java` (research.md §3): a method
      that, given a target user id and the set of roles about to be removed from them (empty set
      for a plain deactivation check), returns whether this would leave zero *active* `AppUser`
      rows holding `Role.ADMIN` — querying `RoleAssignmentRepository.findByRole(Role.ADMIN)` then
      `AppUserRepository.findAllById(...)` for their `active` flags, excluding the target user's own
      current Admin-role membership when it is one of the roles being removed. Per FR-011 the
      `findByRole(Role.ADMIN)` read takes a `PESSIMISTIC_WRITE` lock, and callers MUST invoke the
      guard inside their own `@Transactional` method so the check and the change are atomic.
      Depends on T007, T008.
- [X] T010a Add a session/active check to access-token validation (FR-004/FR-006): a custom
      `OAuth2TokenValidator<Jwt>` (or equivalent filter) wired in
      `backend/src/main/java/com/hls/identity/security/SecurityConfig.java` that rejects a token
      whose `sid` session is revoked or whose user is inactive, so deactivation and admin reset take
      effect on the next request. Tests: a revoked-session token and a deactivated-user token each
      get 401; all existing spec 001-003 auth tests stay green.
- [X] T011 [P] Add a unit test `LastAdminGuardTest` in
      `backend/src/test/java/com/hls/identity/user/LastAdminGuardTest.java`: with exactly one active
      Admin, both "deactivate them" and "remove their Admin role" are refused; with two active
      Admins, both succeed; a deactivated Admin does not count as keeping the system unlocked.
      Add a concurrency test (FR-011): with two Admins, two threads each deactivate the other, and
      exactly one succeeds. Depends on T010.
- [X] T012 [P] Create three new domain event records in
      `backend/src/main/java/com/hls/identity/activity/`: `UserCreated.java` (`eventId`,
      `occurredAt`, `actorUserId` nullable, `newUserId`, `roles`), `UserRoleChanged.java`
      (`eventId`, `occurredAt`, `actorUserId`, `affectedUserId`, `role`, `added`),
      `PasswordResetByAdmin.java` (`eventId`, `occurredAt`, `actorUserId`, `affectedUserId`) —
      data-model.md's field lists, Javadoc noting "for spec 003's Audit module to subscribe to,"
      mirroring the existing five spec-003 events' pattern exactly.
- [X] T013 Change `UserAdminService.deactivateUser(UUID userId)`'s signature to
      `deactivateUser(UUID actorUserId, UUID userId)` in
      `backend/src/main/java/com/hls/identity/user/UserAdminService.java`, passing `actorUserId`
      (instead of the current hard-coded `null`) into the `AccountActivationChanged` publish call
      (research.md §4). Update its two existing callers —
      `backend/src/test/java/com/hls/identity/bootstrap/BootstrapAndDeactivationIntegrationTest.java`
      and
      `backend/src/test/java/com/hls/identity/activity/IdentityActivityPublishingTest.java`'s
      `deactivatingAUserPublishesAccountActivationChangedActiveFalse` — to pass `null` explicitly.

**Checkpoint**: permission key, nav entries, shared password policy, role-assignment query
methods, the last-admin safeguard, and the three new event types all exist. User story work can
now begin.

---

## Phase 3: User Story 1 - Create a New Staff or Teacher Account (Priority: P1) 🎯 MVP

**Goal**: an Admin or System user creates a new user account with a display name, phone, one or
more roles, and optionally a username/email/initial password, through a real screen.

**Independent Test**: as Admin, create a new user with the Manager role and a phone number; confirm
the new user can immediately sign in and is recognized by exactly that role.

### Tests for User Story 1

- [X] T014 [P] [US1] Backend integration test in
      `backend/src/test/java/com/hls/identity/user/UserManagementControllerTest.java`:
      `ADMIN`/`SYSTEM` can create a user with one or more roles and the user can then sign in and
      is recognized by exactly those roles; a duplicate phone is refused with a 409 and the same
      message as spec 001's existing validation, and a duplicate username or duplicate email is
      likewise refused with a 409 naming the field; a role value outside the five fixed roles is
      refused with 400; `DIRECTOR`/`MANAGER`/`TEACHER` get 403
      (contracts/user-management-api.md). **End-to-end audit assertion (FR-008/SC-005, through the
      real endpoint, not a manually-published event)**: after a successful create,
      `await().atMost(Duration.ofSeconds(5)).untilAsserted(...)` that
      `GET /api/v1/audit/user-activity?affectedUserId=<newUserId>&action=USER_CREATED` returns that
      entry — mirroring the `SessionEnded` regression test added to
      `AuditUserActivityControllerTest` this session, which exists precisely because a controller
      can publish an event that never actually reaches the audit consumer (e.g. a missing
      `@Transactional`) without any test noticing unless it goes through the real HTTP path.
- [X] T015 [P] [US1] Frontend test in
      `frontend/src/features/users/UserManagementPage.test.tsx` (create flow): submitting the
      create form with name/phone/role calls the create endpoint and the new row appears; a
      duplicate-phone error from the API is shown inline.

### Implementation for User Story 1

- [X] T016 [US1] Add the actor-aware overload
      `createUser(UUID actorUserId, String displayName, String rawPhone, Set<Role> roles, UUID
      linkedTeacherId, String initialPassword, String username, String email)` to
      `UserAdminService.java` (research.md §5): delegates to the existing creation logic, then
      publishes `UserCreated(UUID.randomUUID(), clock.instant(), actorUserId, user.getId(), roles)`.
      The two existing no-actor overloads are unchanged. The controller passes `null` for
      `linkedTeacherId`; teacher linking is not part of this spec (spec 005 will own it).
      Depends on T012.
- [X] T017 [US1] Implement `UserManagementController` in
      `backend/src/main/java/com/hls/identity/user/UserManagementController.java`:
      `POST /api/v1/identity/users` (contracts/user-management-api.md) gated by
      `PermissionGuard.require(..., USER_MANAGEMENT, CREATE)`, calling T016's new overload with
      the caller's user id as `actorUserId`. Depends on T003, T016.
- [X] T018 [US1] Implement `UserManagementPage.tsx` in
      `frontend/src/features/users/UserManagementPage.tsx` (initial shell: title, empty list,
      "Create user" button) and `CreateUserDialog.tsx` in
      `frontend/src/features/users/CreateUserDialog.tsx`, plus a `userManagementApi.ts` client in
      `frontend/src/features/users/userManagementApi.ts` mirroring
      `frontend/src/features/permissions`'s `authFetch`-based pattern. Depends on T017.
- [X] T019 [US1] Register the `/identity/users` route through the existing `RouteGuard` (spec 002)
      in `frontend/src/App.tsx`, so it renders only when the access model authorizes it. Depends on
      T005, T018.

**Checkpoint**: User Story 1 is independently functional — Admin/System can create a user and that
user can sign in.

---

## Phase 4: Find and Review Existing Users (Priority: P1) [US2]

**Goal**: Admin/System can search/browse the full user list, seeing each one's roles and
active/inactive status.

**Independent Test**: with several seeded users of different roles, search by a partial name and by
a phone number and confirm matching users appear with roles/status; a non-matching search shows an
empty state.

### Tests for User Story 2

- [X] T020 [P] [US2] Backend integration test (extend `UserManagementControllerTest.java`):
      `GET /api/v1/identity/users` with no filter returns all users paginated; `?query=` narrows by
      display name or phone; `?role=` and `?active=` filter by role and status (alone and
      combined with `query`); a non-matching query returns an empty `content` array, not an error;
      `DIRECTOR`/`MANAGER`/`TEACHER` get 403.
- [X] T021 [P] [US2] Frontend test in `UserManagementPage.test.tsx` (list/search): the list renders
      seeded rows with roles and active/inactive status; typing a search term that matches nothing
      shows an empty state, not an error; a Manager/Teacher/Director access-model fixture hides the
      User Management nav section entirely.
- [X] T021a [P] [US2] Backend test in
      `backend/src/test/java/com/hls/identity/accessmodel/UserManagementAccessModelTest.java`
      (Constitution Principle IX): `GET /api/v1/me/access-model` shows "User Management" under
      `SYSTEM` for an Admin and under `SYSTEM CONFIGURATION` for a System user, with VIEW/CREATE/EDIT
      actions; Director, Manager, and Teacher get no such nav entry; the seeded matrix has
      `USER_MANAGEMENT` VIEW/CREATE/EDIT true for ADMIN and SYSTEM only.

### Implementation for User Story 2

- [X] T022 [US2] Add `GET /api/v1/identity/users` to `UserManagementController.java`
      (contracts/user-management-api.md) gated by `USER_MANAGEMENT.VIEW`, calling T009's
      `AppUserRepository.search` and projecting each `AppUser` plus its roles
      (`UserAdminService.rolesOf`) into the response shape. Accepts optional `role` and `active`
      query params in addition to `query` (FR-002). Depends on T009, T017.
- [X] T023 [US2] Wire `UserManagementPage.tsx`'s list/search against T022's endpoint, using the
      DataGrid convention from `frontend/src/features/audit`/`frontend/src/features/permissions`
      (pagination, a search field, an active/inactive status column). Depends on T018, T022.

**Checkpoint**: User Stories 1 and 2 work together — Admin/System can create and then find users.

---

## Phase 5: Change a User's Roles (Priority: P2) [US3]

**Goal**: Admin/System adds or removes one or more of a user's role assignments without recreating
the account, protected by the last-admin safeguard when removing Admin.

**Independent Test**: add the Director role to an existing Manager-only user and confirm their next
sign-in recognizes both; remove one role and confirm only the remaining role is recognized
afterward; attempt to remove the Admin role from the last active Admin and confirm it is refused.

### Tests for User Story 3

- [X] T024 [P] [US3] Backend integration test in
      `backend/src/test/java/com/hls/identity/user/UserRoleChangeIntegrationTest.java`: adding a
      role is reflected on the user's next sign-in; removing a role is reflected the same way;
      submitting an empty role set is refused with 400 ("A user must hold at least one role.");
      removing the Admin role from the sole active Admin is refused with 409 and the role is
      unchanged (FR-007); the same removal succeeds once a second active Admin exists;
      `DIRECTOR`/`MANAGER`/`TEACHER` get 403 on `PUT /api/v1/identity/users/{userId}/roles`
      (FR-009, Constitution Principle IX — every endpoint, not just create/list, needs its own
      per-role check). **End-to-end audit assertion (FR-008/SC-005)**: after a role add and a role
      remove, `await().atMost(Duration.ofSeconds(5)).untilAsserted(...)` that
      `GET /api/v1/audit/user-activity?affectedUserId=<userId>` shows one `ROLE_ASSIGNED` and one
      `ROLE_REMOVED` entry through the real endpoint (same rationale as T014's audit assertion).
- [X] T025 [P] [US3] Frontend test in `frontend/src/features/users/EditRolesDialog.test.tsx`:
      toggling roles and saving calls the roles endpoint with the full new set; a 409 rejection
      (last-admin safeguard) is shown as an inline error and the dialog's selection is not silently
      cleared.

### Implementation for User Story 3

- [X] T026 [US3] Implement `UserAdminService.updateRoles(UUID actorUserId, UUID targetUserId, Set<Role>
      newRoles)` in `UserAdminService.java`: reject with an `EmptyRoleSetException` if `newRoles` is
      empty; compute added/removed roles against `rolesOf(targetUserId)`; if any removed role is
      `ADMIN`, consult `LastAdminGuard` and reject with a `LastAdminException` (carrying the message
      from contracts/user-management-api.md) before changing anything; otherwise apply the diff via
      `RoleAssignmentRepository.save`/`deleteByUserIdAndRole` and publish one `UserRoleChanged` per
      changed role. Depends on T008, T010, T012.
- [X] T027 [US3] Add `PUT /api/v1/identity/users/{userId}/roles` to `UserManagementController.java`
      gated by `USER_MANAGEMENT.EDIT`, mapping `EmptyRoleSetException` to 400 and
      `LastAdminException` to 409 (contracts/user-management-api.md). Depends on T017, T026.
- [X] T028 [US3] Implement `EditRolesDialog.tsx` in
      `frontend/src/features/users/EditRolesDialog.tsx` (a checkbox per fixed role) and wire a
      "Roles" row-action in `UserManagementPage.tsx` to open it against T027's endpoint. Depends on
      T023, T027.

**Checkpoint**: User Stories 1-3 work together.

---

## Phase 6: Deactivate and Reactivate a User (Priority: P2) [US4]

**Goal**: Admin/System deactivates a user (ending their sessions and sign-in ability immediately)
and can later reactivate the same account, protected by the last-admin safeguard when deactivating.

**Independent Test**: deactivate an active user with two open sessions; confirm both end and no
sign-in method succeeds afterward. Reactivate the same user; confirm their original roles are
intact and they can sign in again.

### Tests for User Story 4

- [X] T029 [P] [US4] Backend integration test in
      `backend/src/test/java/com/hls/identity/user/UserDeactivationIntegrationTest.java`:
      deactivating a user with two active sessions ends both and refuses every subsequent sign-in
      attempt (password and OTP); reactivating restores sign-in and exactly their prior roles;
      deactivating the sole active Admin is refused with 409 and they remain active (FR-007); the
      same deactivation succeeds once a second active Admin exists; reactivation is never refused
      by the safeguard; after deactivation the user's still-unexpired access token gets 401 on the
      next call (T010a, FR-004); deactivating an already-inactive user and reactivating an
      already-active one are no-ops that publish no event; an Admin deactivating their own account
      succeeds (when not the last Admin) and ends their own session;
      `DIRECTOR`/`MANAGER`/`TEACHER` get 403 on both
      `POST .../deactivate` and `POST .../reactivate` (FR-009, Constitution Principle IX). **End-to-
      end audit assertion (FR-008/SC-005)**: after deactivating and then reactivating,
      `await().atMost(Duration.ofSeconds(5)).untilAsserted(...)` that
      `GET /api/v1/audit/user-activity?affectedUserId=<userId>` shows one `ACCOUNT_DEACTIVATED` and
      one `ACCOUNT_REACTIVATED` entry through the real endpoints.
- [X] T030 [P] [US4] Frontend test in `UserManagementPage.test.tsx` (deactivate/reactivate row
      actions): the action calls the right endpoint and the row's status updates; a 409 rejection
      is shown as an inline error.

### Implementation for User Story 4

- [X] T031 [US4] Rework `UserAdminService.deactivateUser(UUID actorUserId, UUID userId)` (T013) to
      consult `LastAdminGuard` first and throw `LastAdminException` before deactivating, if the
      target is the sole active Admin. Depends on T010, T013.
- [X] T032 [US4] Implement `UserAdminService.reactivateUser(UUID actorUserId, UUID userId)` in
      `UserAdminService.java`: calls `AppUser.reactivate()` (T007), saves, and publishes
      `AccountActivationChanged(UUID.randomUUID(), clock.instant(), actorUserId, userId, true)` —
      never consulting `LastAdminGuard` (reactivation only increases active Admins). Depends on
      T007, T012.
- [X] T033 [US4] Add `POST /api/v1/identity/users/{userId}/deactivate` and
      `POST /api/v1/identity/users/{userId}/reactivate` to `UserManagementController.java`, the
      first gated by `USER_MANAGEMENT.EDIT` and mapping `LastAdminException` to 409, the second
      also gated by `USER_MANAGEMENT.EDIT` (contracts/user-management-api.md). Depends on T017,
      T031, T032.
- [X] T034 [US4] Add "Deactivate"/"Reactivate" row actions to `UserManagementPage.tsx` (shown based
      on the row's current `active` status) wired to T033's endpoints. Depends on T023, T033.

**Checkpoint**: User Stories 1-4 work together.

---

## Phase 7: Reset Another User's Password (Priority: P2) [US5]

**Goal**: Admin/System sets a new password directly on another user's account, ending their
sessions and clearing any lockout, under the same password policy as self-service reset.

**Independent Test**: as Admin, set a new password for a user with an existing active session;
confirm that session ends immediately and the user can sign in with the new password but not the
old one.

### Tests for User Story 5

- [X] T035 [P] [US5] Backend integration test in
      `backend/src/test/java/com/hls/identity/user/AdminPasswordResetIntegrationTest.java`: setting
      a new password ends the target's existing sessions and clears any lockout; the user can then
      sign in with the new password and not the old one; a user who previously had no password can
      subsequently sign in with the new one; a password shorter than 10 characters or equal to the
      user's phone is refused with 400 and spec 001's exact policy message (T006); the target's
      old, still-unexpired access token gets 401 on the next call (T010a, FR-006); the submitted
      password string appears nowhere in captured logs or in the `user_activity_entry` row (FR-006);
      `DIRECTOR`/`MANAGER`/`TEACHER` get 403 on `POST .../reset-password` (FR-009, Constitution
      Principle IX). **End-to-end audit assertion (FR-008/SC-005)**: after a successful reset,
      `await().atMost(Duration.ofSeconds(5)).untilAsserted(...)` that
      `GET /api/v1/audit/user-activity?affectedUserId=<userId>&action=PASSWORD_RESET_BY_ADMIN`
      returns that entry through the real endpoint.
- [X] T036 [P] [US5] Frontend test in `frontend/src/features/users/ResetPasswordDialog.test.tsx`:
      submitting a new password calls the reset endpoint; a 400 policy-violation response is shown
      inline with the same message spec 001's `ForgotPasswordPage` uses.

### Implementation for User Story 5

- [X] T037 [US5] Implement `UserAdminService.adminResetPassword(UUID actorUserId, UUID targetUserId,
      String newPassword)` in `UserAdminService.java`: validates via `PasswordPolicy` (T006,
      returning a `PasswordPolicyViolationException` on failure), sets the encoded password, calls
      `sessionService.revokeAllSessionsForUser(targetUserId)` and clears `failedAttemptCount`/
      `lockUntil` (mirroring `PasswordResetService.complete`'s side effects), then publishes
      `PasswordResetByAdmin(UUID.randomUUID(), clock.instant(), actorUserId, targetUserId)`. The
      password MUST NOT be logged, echoed in `PasswordPolicyViolationException`, or placed in the
      event (FR-006). Depends on T006, T012.
- [X] T038 [US5] Add `POST /api/v1/identity/users/{userId}/reset-password` to
      `UserManagementController.java` gated by `USER_MANAGEMENT.EDIT`, mapping
      `PasswordPolicyViolationException` to 400 (contracts/user-management-api.md). Depends on
      T017, T037.
- [X] T039 [US5] Implement `ResetPasswordDialog.tsx` in
      `frontend/src/features/users/ResetPasswordDialog.tsx` and a "Reset password" row action in
      `UserManagementPage.tsx` wired to T038's endpoint. Depends on T023, T038.
- [X] T039a [P] [US5] Gate every User Management action by the access model (FR-012, Constitution
      Principle IV): hide "Create user" unless `USER_MANAGEMENT.CREATE` is granted, and hide the
      Roles, Deactivate/Reactivate, and Reset password row actions unless `USER_MANAGEMENT.EDIT` is
      granted. Show a confirmation warning that the actor's own session will end when they target
      their own account for deactivation or password reset. Frontend test in
      `UserManagementPage.test.tsx`: a fixture with VIEW but no EDIT/CREATE renders the list with no
      actions. Depends on T034, T039.

**Checkpoint**: User Stories 1-5 work together.

---

## Phase 8: The Last Admin Cannot Be Locked Out (Priority: P2) [US6]

**Goal**: prove, end-to-end through the real endpoints, that the last-admin safeguard built into
US3 (T026) and US4 (T031) actually holds — this story adds no new production code of its own (the
guard and its two call sites already exist by this point); it is the cross-cutting verification
spec.md's User Story 6 calls for.

**Independent Test**: with exactly one active Admin account, attempt to deactivate it and,
separately, attempt to remove its Admin role; confirm both are refused and nothing changed. Create
a second Admin account; confirm the same two attempts now succeed on the first account.

### Tests for User Story 6

- [X] T040 [US6] Backend integration test in
      `backend/src/test/java/com/hls/identity/user/LastAdminSafeguardIntegrationTest.java`,
      through the real HTTP endpoints (not direct service calls): with exactly one active Admin,
      `POST .../deactivate` on them returns 409 and they remain active; `PUT .../roles` removing
      `ADMIN` from them (with or without other roles changing in the same request) returns 409 and
      their roles are unchanged; creating a second Admin via `POST /api/v1/identity/users` and
      repeating both requests against the *first* Admin now succeeds (Acceptance Scenarios 1-3).
      Depends on T027, T033.
- [X] T041 [P] [US6] Frontend test asserting `EditRolesDialog.test.tsx` and the deactivate row
      action in `UserManagementPage.test.tsx` both render the last-admin rejection message inline
      rather than a generic error, when the API returns 409 with a `reason`.

**Checkpoint**: all six user stories are independently functional together — this spec is
complete.

---

## Final Phase: Polish & Cross-Cutting Concerns

- [X] T042 [P] Extend `backend/src/test/java/com/hls/audit/useractivity/UserActivityEventConsumerTest.java`
      with three new cases: `UserCreated` → `action="USER_CREATED"`, `detail` lists the roles;
      `UserRoleChanged` (both `added=true` and `added=false`) → `action="ROLE_ASSIGNED"`/
      `"ROLE_REMOVED"`, `detail` is the role name; `PasswordResetByAdmin` →
      `action="PASSWORD_RESET_BY_ADMIN"`; and that `AccountActivationChanged(active=true)` now maps
      to `action="ACCOUNT_REACTIVATED"` through a real call (not just the type-level branch that
      already existed) — all dedup on `source_event_id` redelivery like every other event.
- [X] T043 [P] Add an axe-core accessibility suite covering `UserManagementPage`,
      `CreateUserDialog`, `EditRolesDialog`, and `ResetPasswordDialog` in both themes to
      `frontend/src/a11y/a11y.test.tsx`, asserting zero critical WCAG 2.2 AA violations
      (FR-024/SC-008 precedent from spec 001/003).
- [ ] T044 [P] Run all six of `quickstart.md`'s scenarios end-to-end and record the results,
      including timing the SC-001 (create-and-sign-in under 2 minutes) and SC-002 (find and
      deactivate in 3 actions) walkthroughs by hand.
- [X] T045 [P] Update `docs/spec-roadmap.md` row 004's status to "Implemented" once every
      checkpoint above has passed.
- [X] T046 Run the full backend suite (`mvn test`) and the full frontend suite (`npm run test`,
      including T043's axe-core checks) together and confirm both are green.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: no dependencies.
- **Foundational (Phase 2)**: depends on Setup; blocks every user story.
- **User Stories (Phase 3-8)**: all depend on Foundational.
  - **US1 → US2**: US2's list screen is far more useful once US1 can populate it, and US2's
    controller task (T022) extends the same `UserManagementController` US1 creates (T017) — build
    in order, though US2's tests only assume users already exist (seeded or created by US1).
  - **US3, US4, US5** each extend `UserAdminService`/`UserManagementController` independently and
    do not depend on each other's logic, but all three depend on US1's controller (T017) existing
    first.
  - **US6** has a hard dependency on **US3** (T027) and **US4** (T033) — it verifies both endpoints
    together and adds no new endpoint of its own.
- **Polish (Final Phase)**: depends on all six user stories being complete.

### Parallel Opportunities

- Within Foundational: T005/T007/T008/T009/T011/T012 can run in parallel once T003 (where needed)
  and T010's own dependencies (T007, T008) land; T006 is independent of all of them.
- Within each user story: test tasks marked `[P]` run in parallel with each other; independent
  implementation tasks (e.g. backend service vs. frontend dialog) marked `[P]` run in parallel.
- US3, US4, and US5's implementation work (T026-T028, T031-T034, T037-T039) can be staffed in
  parallel by different people once US1's controller (T017) exists, since each touches a disjoint
  set of `UserAdminService` methods and `UserManagementController` endpoints.

---

## Parallel Example: User Story 3

```bash
# Tests together:
Task: "Backend integration test for role changes and the last-admin safeguard in backend/src/test/java/com/hls/identity/user/UserRoleChangeIntegrationTest.java"
Task: "Frontend test for EditRolesDialog in frontend/src/features/users/EditRolesDialog.test.tsx"
```

---

## Implementation Strategy

### MVP First (User Stories 1 and 2 Only)

1. Complete Phase 1 (Setup) and Phase 2 (Foundational).
2. Complete Phase 3 (US1) and Phase 4 (US2). At this point Admin/System can create and find users
   through a real screen — the minimum that replaces "a developer runs internal code directly."
3. **Stop and validate** against spec.md's User Story 1 and 2 acceptance scenarios.

### Incremental Delivery

1. Setup + Foundational → foundation ready (including the last-admin safeguard, built once).
2. US1 → validate → create users.
3. US2 → validate → MVP demo (create + find).
4. US3 → validate (role changes, including the safeguard's role-removal path).
5. US4 → validate (deactivate/reactivate, including the safeguard's deactivation path).
6. US5 → validate (admin-triggered password reset).
7. US6 → validate (the safeguard's two-Admin boundary case, end-to-end through both US3 and US4's
   real endpoints together) → spec complete.
8. Final Phase → audit-consumer coverage for all new action types, axe-core checks, quickstart
   run, roadmap update, full suite green.
