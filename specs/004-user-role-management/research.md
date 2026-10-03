# Phase 0 Research: User & Role Management

## 1. Reusing audit's existing `user_activity_entry` table vs a new table

**Decision**: this spec's four new events (`UserCreated`, `UserRoleChanged`,
`AccountActivationChanged` with its first `active=true` caller, `PasswordResetByAdmin`) all feed
`audit`'s existing `UserActivityEventConsumer` and `user_activity_entry` table from spec 003,
mapping to new `action` text values. No new table, no schema change.

**Rationale**: `user_activity_entry.action` is a plain `text` column by design (data-model.md,
spec 003) specifically so later specs could add action types without a migration — the same
additive pattern spec 003 itself used for `PermissionModule`. Every one of this spec's new actions
("a user account was created," "a role was added/removed," "a password was reset by an admin," "an
account was reactivated") is the same *kind* of fact spec 003's User Activity already models: an
account-lifecycle action with an actor, an affected user, and a timestamp.

**Alternatives considered**:
- A dedicated `user_management_entry` table: rejected — it would duplicate `user_activity_entry`'s
  exact shape (actor, affected user, action, detail, timestamp) for no reason, and split one
  investigation ("what happened to this account?") across two screens/tables.
- Recording role changes in `change_history_entry` instead (treating a role assignment like a
  field edit, `entity_type="APP_USER"`, `field="roles"`): considered, since Change History already
  models before/after field values generically. Rejected in favor of User Activity because a role
  change is naturally one discrete lifecycle action per role (added/removed), not a before/after
  pair on one field — `UserRoleChanged(role, added)` reads better as "MANAGER role added" than as a
  diff of two role-set strings, and it keeps every account-lifecycle fact in one place (User
  Activity) rather than splitting role changes into Change History while deactivation stays in User
  Activity.

## 2. Extracting a shared `PasswordPolicy` instead of duplicating the check

**Decision**: extract `PasswordResetService.complete`'s inline password-policy check (≥10
characters, not equal to the user's phone number) into a small static helper, `PasswordPolicy`, in
`com.hls.identity.auth`. The new admin-triggered reset path calls the same helper.

**Rationale**: this spec adds a second call site that must enforce the *identical* password rule
(FR-006 explicitly says "under the same password policy spec 001 already enforces"). Leaving the
check inline in `PasswordResetService` and duplicating it in the new admin-reset method would let
the two rules silently drift apart on a future edit — exactly the kind of security-relevant
duplication the constitution's "backend is the security control" principle (X) warns against.
Two real callers enforcing the same security rule is the textbook case where extracting a shared
helper earns its keep, rather than premature abstraction.

**Alternatives considered**:
- Duplicate the check in the new method: rejected for the drift risk above.
- Move the check into `AppUser` itself (e.g., a `canSetPassword(String)` method): rejected — the
  rule needs the plain-text candidate password and the encoder is a separate concern; a small
  stateless helper keeps `AppUser` focused on state, not policy.

## 3. Last-admin safeguard: one guard, two call sites

**Decision**: a single `LastAdminGuard` component holds the one check — "would this leave zero
active users holding the Admin role?" — called from both `UserAdminService.updateRoles` (before
removing the Admin role) and `UserAdminService.deactivateUser` (before deactivating). Both callers
pass the target user id and, for role changes, which roles are being removed; the guard queries
current active Admin-role holders and rejects as a single atomic decision before any write happens
(FR-007's "no partial effect").

**Rationale**: spec.md's edge case explicitly covers removing Admin *and* deactivating in the same
request needing to fail as one unit — but in this spec's design each HTTP action (role change,
deactivate) is a separate endpoint and a separate transaction, so "the same request" in practice
means "the same role-change request removing multiple roles at once," which `updateRoles` already
handles atomically by checking the guard before applying any role diff. A single shared guard class
(rather than copy-pasted logic in two service methods) is the smallest change that keeps the rule
in exactly one place, consistent with how spec 002's analogous matrix-level safeguard
(`PermissionMatrixService.isLastMatrixManagerRemoval`) already lives in one place for its one
caller — this spec's guard has two callers, which is exactly why it is its own small class instead
of a private method on one of them.

**Alternatives considered**:
- A database constraint (e.g., a trigger refusing the last Admin's deactivation): rejected — the
  check needs to reason about *role assignments*, not just the `active` flag, and triggers would
  duplicate business logic outside the module boundary ArchUnit already enforces.
- Checking only at deactivation time, not at role-removal time: rejected — spec.md's User Story 6,
  Acceptance Scenario 2 explicitly requires refusing Admin-role *removal* even when the user stays
  active, which a deactivation-only check would miss entirely.

## 4. `deactivateUser`'s signature gains an `actorUserId` parameter

**Decision**: `UserAdminService.deactivateUser(UUID userId)` becomes
`deactivateUser(UUID actorUserId, UUID userId)`. Its existing two callers
(`BootstrapAndDeactivationIntegrationTest`, `IdentityActivityPublishingTest`) are updated to pass
`null` (no human actor), matching the existing convention `PasswordAuthService`'s automatic lockout
already uses for a system-triggered action.

**Rationale**: spec 001 built `deactivateUser` before any real caller existed (`UserAdminService`'s
own Javadoc says "no HTTP endpoint exists for it in this spec — screens are spec 004's"), so it had
no actor to record and `AccountActivationChanged` was always published with `actorUserId=null`.
This spec adds the first real caller — an Admin or System user acting through a screen — and
FR-008 requires the audit entry to capture *who* deactivated the account, so the parameter has to
exist now. This is additive signature evolution of not-yet-externally-depended-upon internal code,
the same latitude spec 003's tasks.md used when it added `eventId` to `PermissionMatrixChanged`.

**Alternatives considered**:
- Add a second overload (`deactivateUser(UUID userId)` defaulting `actorUserId` to null) to avoid
  touching existing callers: rejected — it would let a real, UI-triggered deactivation silently use
  the no-actor overload by mistake, defeating the point of recording who acted.

## 5. `UserCreated` is published by a new overload, not the existing `createUser` methods

**Decision**: `UserAdminService` gains one new overload,
`createUser(UUID actorUserId, String displayName, ..., String email)`, used only by
`UserManagementController`'s create endpoint. It does the identical work as the existing
no-actor overloads (which it delegates to internally) and additionally publishes `UserCreated`.
The existing overloads — used today by `BootstrapUserInitializer`, `DevDataSeeder`, and dozens of
test fixtures across specs 001-003 that call `createUser(...)` directly to set up a test user —
are left completely unchanged: same signature, same behavior, still no event published.

**Rationale**: `createUser`'s two existing overloads are called from a large number of existing
test files (every spec's controller tests sign a user in via a `signInAs` helper that calls
`userAdminService.createUser(...)` directly). Changing either existing overload's signature or
behavior would ripple across specs 001-003's already-green test suites for no real benefit —
those call sites have no human "admin" actor in the FR-008 sense anyway (bootstrap, dev-seed data,
and test fixtures are not a person using User Management). A new, additive overload used by
exactly one real caller (the new controller) keeps the blast radius to zero on existing code,
matching the same "additive, not a breaking change" posture research.md §4 uses for
`deactivateUser` — the difference is that `deactivateUser` had only two low-risk callers worth
updating directly, while `createUser` has many, so a new overload is the better fit here.

**Alternatives considered**:
- Add `actorUserId` to the existing overloads and update every call site: rejected — dozens of
  unrelated test files across three already-shipped specs would need a mechanical but
  unnecessary edit for a parameter they'd always pass as `null`.
- Publish `UserCreated` from the controller instead of the service: rejected — every other
  identity activity event in this codebase is published by the service that performs the write,
  inside the same transaction (research.md's transactional requirement); publishing from the
  controller would break that single consistent pattern for no reason.

## 6. Searching users: a `@Query` over `AppUserRepository` vs `Specification`

**Decision**: one `@Query` JPQL method,
`search(String term, Pageable pageable)`, matching `term` case-insensitively against `display_name`
or `phone` with a `LIKE '%term%'`, returning `Page<AppUser>`.

**Rationale**: spec 003's audit screens already established the project's pagination/filter
convention (`Page`-shaped responses, `page`/`size` query params); reusing it here keeps
`UserManagementPage.tsx` consistent with every other list screen. A single two-field `OR LIKE`
query is simple enough that a derived Spring Data method name would be unreadable, but not complex
enough to justify the `Specification` API spec 003's `AuditLogQueryService` used for its multi-field
optional filters — `AppUserRepository`'s search has exactly one free-text term against exactly two
fixed columns, no combination of optional filters to compose.

**Alternatives considered**:
- `Specification<AppUser>` with optional predicates: rejected as more machinery than one
  always-present free-text term needs.
- Full-text search (PostgreSQL `tsvector`): rejected — under 100 users total makes a `LIKE` scan
  trivially fast; full-text search is unjustified infrastructure at this scale.
