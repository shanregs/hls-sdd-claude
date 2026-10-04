# Quickstart: Validating Access Model & App Shell

Prerequisites: spec 001 (Identity & Access) implemented and running, so a signed-in session with a
JWT exists for each of the five roles. PostgreSQL running (or Testcontainers for automated tests).

## Setup

1. Start the backend (`./mvnw spring-boot:run` from `backend/`) against a fresh database. Confirm on
   startup that the `permission_matrix` table is created (Flyway) and seeded exactly as described in
   `data-model.md` — no more, no fewer rows.
2. Start the frontend (`npm run dev` from `frontend/`).
3. Sign in as a test user holding each of the five roles in turn (create them via spec 001's
   bootstrap/minimal user-creation capability if not already present).

## Scenario 1: Role-appropriate landing (User Story 1, SC-001/SC-002)

1. Sign in as a Teacher-only user, timing from credential submission to the landing dashboard's
   first render. **Expect**: under 3 seconds (SC-002); landing dashboard shows Teacher's
   self-service placeholder widgets; left nav shows only Dashboard and ACCOUNT → My Profile.
2. Sign in as a user holding both Admin and Director. **Expect**: one combined dashboard/nav, no
   duplicate menu items, no role picker at any point.
3. Repeat for Manager and System. **Expect**: Manager sees "assigned"-scoped placeholder wording;
   System sees no business-module items anywhere.

## Scenario 2: Unauthorized routes are unreachable (User Story 2, SC-003)

1. As a Manager, note the Role & Permissions route (`/identity/permissions`) from the frontend
   route table in `frontend/src/App.tsx`.
2. Type that URL directly in the browser while signed in as the Manager. **Expect**: the
   "not authorized" page renders; check the network tab and confirm no request to
   `/api/v1/identity/permission-matrix` was made and no matrix data reached the browser.
3. Select the page's "back to my dashboard" action. **Expect**: returns to the Manager's own
   dashboard.

## Scenario 3: Matrix edit and safeguard (User Story 3, SC-004/SC-005)

0. As Admin, open Role & Permissions. **Expect**: one table, a row per module and a column per role;
   coloured icons for granted actions (view, create, edit, delete, process, export), grey icons for
   actions that apply but are not granted, and a dash where nothing applies (for example Manager and
   Teacher on `IDENTITY_PERMISSIONS`, or System on any teacher or school module). Hover an icon:
   the tooltip reads, for example, "Edit - granted".
1. As Admin, click Manager's `DASHBOARD` View icon (a confirmation opens) and revoke the grant.
2. As the already-signed-in Manager (separate session), wait for the next renewal (or trigger one)
   and confirm the Dashboard item disappears from their nav without them logging out.
3. As Admin, attempt to revoke `IDENTITY_PERMISSIONS.EDIT` from Admin, Director, and System all the
   way to zero (e.g., if only Admin currently holds it, try to revoke Admin's own grant). **Expect**:
   409 response, plain-language reason, and the matrix is unchanged (re-fetch and confirm).

## Scenario 4: Theme persistence (User Story 4, SC-006)

1. Toggle to dark mode. **Expect**: shell, nav, and dashboard restyle immediately, no flash of the
   previous theme.
2. Sign out, close the tab, reopen the app. **Expect**: dark mode is active before sign-in even
   completes.
3. In a private/incognito window (or with storage disabled), load the app. **Expect**: it renders
   with a default theme and no console error.

## Scenario 5: Responsive shell (User Story 5)

1. Resize the browser to desktop width. **Expect**: persistent, collapsible nav column (~25-30%
   width).
2. Resize to 360px. **Expect**: nav collapses behind a menu control, opens as an overlay drawer, and
   no page shows a horizontal scrollbar.

## Automated verification

- Backend: `./mvnw test` runs the ArchUnit module-boundary check, the per-role/per-scope
  authorization tests on both new controllers, and the Testcontainers-backed
  `PermissionMatrixService` tests (seeding idempotency, last-manager safeguard, change-record
  publishing).
- Frontend: `npm run test` runs Vitest/Testing Library route-guard and navigation-rendering tests
  per role, plus the axe-core accessibility check (SC-007) against the shell, nav, and all five
  dashboards in both themes.
