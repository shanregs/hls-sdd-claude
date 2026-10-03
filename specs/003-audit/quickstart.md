# Quickstart: Validating Audit

Prerequisites: backend running against PostgreSQL (Flyway migrated), specs 001 and 002 already
implemented and their test users available (see `DevDataSeeder`, `hls.seed.demo-data=true`).

## Scenario 1: Login History captures real sign-ins (User Story 1)

1. Sign in successfully as any seeded user (e.g. `9800000001` / `Password123!`), then sign out and
   attempt a sign-in with a wrong password for the same user. **Expect**: as Admin
   (`asha.admin`/`Password123!`), open Login History, filter by that user's phone — both the success
   and the failure appear with correct method/outcome/timestamp.
2. Repeat as a Manager, Teacher, or Director. **Expect**: no AUDIT section in the nav at all; a
   direct hit on the Login History route shows "not authorized."

## Scenario 2: Change History captures permission-matrix edits (User Story 2)

1. As Admin, toggle one grant in Role & Permissions (spec 002). **Expect**: Change History shows one
   new entry with the role/module/action, `before`/`after` values, the editing Admin as actor, and a
   timestamp within a few seconds of the edit.
2. Filter Change History by a different actor. **Expect**: the entry above does not appear.

## Scenario 3: User Activity captures account lifecycle actions (User Story 3)

1. Trigger a password reset (SMS or email) for a seeded user and complete it. **Expect**: User
   Activity for that user shows both `PASSWORD_RESET_REQUESTED` and `PASSWORD_RESET_COMPLETED`.
2. End a session from Profile (spec 001's session list) for a user with two active sessions.
   **Expect**: a `SESSION_ENDED` entry appears for that user.
3. Deactivate a user via the internal capability (spec 001). **Expect**: an `ACCOUNT_DEACTIVATED`
   entry appears, with the deactivating Admin as actor.

## Scenario 4: Unified Audit Logs (User Story 4)

1. With entries from Scenarios 1–3 already present, open Audit Logs with no filter. **Expect**: all
   three entry types appear together, most recent first.
2. Apply a date range and `type=CHANGE` filter, then export. **Expect**: the downloaded CSV's rows
   match exactly what the filtered screen shows.

## Edge cases to exercise

- Query any of the four views with a filter combination that matches nothing. **Expect**: an empty
  state, not an error.
- Attempt an export without a date range. **Expect**: 400, with a message asking for a date range.
- Attempt to reach any audit endpoint or screen as Manager/Teacher/Director. **Expect**: 403 from
  the API regardless of what the (absent) menu item would have shown.

## Automated verification

- Backend: `./mvnw test` — Testcontainers-backed tests per user story (event-consumption
  idempotency on redelivery, immutability — no update/delete path exists, per-role/per-action 403s,
  export date-range bound, ArchUnit module boundary: `audit` owns its three tables exclusively and
  no other module writes to them).
- Frontend: `npm run test` — Vitest/Testing Library flows for each of the four screens (list,
  filter, empty state, export trigger) plus the axe-core accessibility check (Constitution
  Principle IV) in both themes, and a role-visibility test confirming the AUDIT section is absent
  for Director/Manager/Teacher.
