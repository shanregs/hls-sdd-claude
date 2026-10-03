# Phase 1 Data Model: User & Role Management

No new table. This spec amends two existing entities (spec 001's `AppUser`, read/write patterns on
`RoleAssignment`) and adds three new domain events (and the first `active=true` use of an existing one) that feed spec 003's existing
`user_activity_entry` table through its existing consumer.

## App User (amended)

Spec 001's `app_user` table and `AppUser` entity are unchanged in shape. This spec adds one new
behavior:

| Change | Notes |
| --- | --- |
| `reactivate()` | Mirrors the existing `deactivate()`: sets `active = true`. No new column — `active` already exists (spec 001). |

## Role Assignment (unchanged shape, new access patterns)

Spec 001's `role_assignment` table (`id`, `user_id` FK, `role` enum, unique `(user_id, role)`) is
unchanged. This spec adds the query/mutation patterns User Story 3 needs:

- Read all roles currently held by a user (already exists: `RoleAssignmentRepository.findByUserId`).
- Read all users currently holding a given role (new: `findByRole(Role)`), used by the last-admin
  safeguard to count active Admin-role holders.
- Remove one specific role from a user (new: `deleteByUserIdAndRole(UUID, Role)`).
- Add one role to a user (already exists as a plain `save` of a new `RoleAssignment`).

**Validation rule (FR-003)**: a role-update request that would leave a user holding zero roles is
rejected outright, with no partial change — every `AppUser` row MUST have at least one
`RoleAssignment` row at all times (the same invariant `UserAdminService.createUser` already
enforces at creation).

**Validation rule (FR-007, the last-admin safeguard)**: a role-update request that removes the
Admin role, or a deactivation request, is rejected outright if it would leave zero *active*
`AppUser` rows holding the Admin role. "Active" matters here — a deactivated Admin account does not
count toward keeping the system unlocked, since it cannot sign in anyway.

## User Activity Entry (existing table, new `action` values)

Spec 003's `user_activity_entry` table (`id`, `occurred_at`, `source_event_id`, `actor_user_id`
nullable, `affected_user_id`, `action` text, `detail` text nullable) is unchanged in shape. This
spec adds new values to the `action` vocabulary it already documents as open-ended:

| New `action` value | Produced by | `detail` |
| --- | --- | --- |
| `USER_CREATED` | `UserCreated` event, published from `UserAdminService.createUser` | the roles assigned, e.g. `"MANAGER, DIRECTOR"` |
| `ROLE_ASSIGNED` | `UserRoleChanged(role, added=true)`, from `UserAdminService.updateRoles` | the role name, e.g. `"DIRECTOR"` |
| `ROLE_REMOVED` | `UserRoleChanged(role, added=false)`, from `UserAdminService.updateRoles` | the role name |
| `ACCOUNT_REACTIVATED` | `AccountActivationChanged(active=true)` — the type already existed (spec 003), reserved for this spec; this is its first real caller | `null` |
| `PASSWORD_RESET_BY_ADMIN` | `PasswordResetByAdmin` event, from `UserAdminService.adminResetPassword` | `null` |

`ACCOUNT_DEACTIVATED` (existing value) continues to be produced by the same
`AccountActivationChanged(active=false)` event, now with a real `actor_user_id` (the deactivating
Admin/System user) instead of always `null`, since this spec gives `deactivateUser` its first real
human caller (research.md §4).

## New Domain Events (`com.hls.identity.activity`)

All four follow the exact shape `LoginHistoryRecorded`/`PermissionMatrixChanged`/spec 003's five
events already established: an `eventId` (dedup key), `occurredAt`, an actor, and the data needed
to describe what happened — published via `ApplicationEventPublisher` inside the same transaction
as the write that caused them (research.md's transactional requirement, confirmed the hard way in
spec 003's `SessionController` incident — every publisher method here is `@Transactional`).

| Event | Fields | Published from |
| --- | --- | --- |
| `UserCreated` | `eventId`, `occurredAt`, `actorUserId` (nullable — bootstrap/dev-seed have no human actor), `newUserId`, `roles` (`Set<Role>`, for the `detail` text) | `UserAdminService.createUser` |
| `UserRoleChanged` | `eventId`, `occurredAt`, `actorUserId`, `affectedUserId`, `role`, `added` (`boolean`) | `UserAdminService.updateRoles`, once per changed role |
| `PasswordResetByAdmin` | `eventId`, `occurredAt`, `actorUserId`, `affectedUserId` | `UserAdminService.adminResetPassword` |

(`AccountActivationChanged` is not listed above — it already exists, spec 003; this spec adds its
second call site and first `active=true` caller, no field change.)

## Validation rules (consolidated)

- A user account always holds at least one role (spec 001's existing rule, reused by role changes).
- The last-admin safeguard (FR-007) is checked, and may reject, *before* any write — a rejected
  role-change or deactivation request changes nothing at all, atomically.
- An admin-triggered password reset follows the exact same policy as a self-service reset (≥10
  characters, not equal to the account's phone number), enforced by the shared `PasswordPolicy`
  helper (research.md §2) — never a looser rule for the admin path.
- Reactivating a user does not touch their `RoleAssignment` rows — whatever roles they held at
  deactivation are exactly what they hold after reactivation, since deactivation never deletes
  them.

## State transitions

`AppUser.active`: `true → false` (deactivate, spec 001) and now `false → true` (reactivate, new).
No other state machine is introduced — role assignment is a set-membership change (a role is either
assigned or not), not a multi-state lifecycle.
