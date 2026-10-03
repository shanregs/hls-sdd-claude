# Quickstart: Validating User & Role Management

Prerequisites: backend running against PostgreSQL (Flyway migrated), specs 001-003 already
implemented and their test users available (`DevDataSeeder`, `hls.seed.demo-data=true`).

## Scenario 1: Create a new user (User Story 1)

1. As Admin (`asha.admin`/`Password123!`), open User Management and create a user with the
   Manager role, a phone number, and no password. **Expect**: the user appears in the list, active,
   with the Manager role.
2. Sign in as that new user by requesting a one-time code for their phone. **Expect**: sign-in
   succeeds and they are recognized by exactly the Manager role.
3. Try to create another user with the same phone number. **Expect**: refused with a clear
   duplicate-phone message; nothing is created.

## Scenario 2: Find users and check role-gating (User Story 2)

1. As Admin, search User Management by part of a seeded user's display name. **Expect**: only
   matching users appear, each showing roles and active/inactive status.
2. Search for a name that matches nobody. **Expect**: an empty state, not an error.
3. Sign in as a Manager, Director, or Teacher. **Expect**: no User Management item in the nav, and
   a direct hit on its route shows "not authorized."

## Scenario 3: Change a user's roles (User Story 3)

1. As Admin, add the Director role to the Manager-only user from Scenario 1. **Expect**: their next
   sign-in recognizes both Manager and Director.
2. Remove the Director role again. **Expect**: their next sign-in recognizes only Manager.
3. Attempt to remove every role from a user at once. **Expect**: refused — a user must hold at
   least one role.

## Scenario 4: Deactivate and reactivate (User Story 4)

1. Sign the Scenario-1 user in twice (two sessions), then deactivate them as Admin. **Expect**:
   both sessions end immediately and no further sign-in (password, OTP) succeeds for them.
2. Reactivate the same user. **Expect**: they can sign in again and are recognized by exactly the
   roles they held before deactivation.

## Scenario 5: Reset another user's password (User Story 5)

1. As Admin, set a new password for a user with an active session. **Expect**: that session ends
   immediately; the user can sign in with the new password but not the old one.
2. Submit a new password shorter than 10 characters. **Expect**: refused with the same password
   policy message spec 001's self-service reset uses.

## Scenario 6: The last Admin cannot be locked out (User Story 6)

1. With exactly one active Admin account (`asha.admin`), attempt to deactivate it. **Expect**:
   refused with a clear explanation; the account remains active.
2. Attempt to remove the Admin role from that same account. **Expect**: refused; the Admin role
   remains.
3. Create a second Admin account, then repeat steps 1-2 on the *first* Admin account. **Expect**:
   both actions now succeed, since the second Admin keeps the system unlocked.

## Edge cases to exercise

- Attempt any User Management endpoint as Director, Manager, or Teacher. **Expect**: 403 from the
  API regardless of what the (absent) menu item would have shown.
- Deactivate a user, then attempt to reset their password or change their roles. **Expect**: these
  remain possible on an inactive account (so an Admin can fix a mistake before reactivating), but
  the account still cannot sign in until reactivated.

## Automated verification

- Backend: `./mvnw test` — Testcontainers-backed tests per user story (duplicate-phone/role-empty
  rejection, per-role/per-action 403s, the last-admin safeguard for both deactivation and role
  removal including the two-Admin boundary case, and that every action lands in User Activity).
- Frontend: `npm run test` — Vitest/Testing Library flows for the user list/search/create/edit-roles
  /deactivate/reactivate/reset-password dialogs, plus the axe-core accessibility check (Constitution
  Principle IV) in both themes, and a role-visibility test confirming the User Management nav item
  is absent for Director/Manager/Teacher.
