---

description: "Task list for feature implementation"
---

# Tasks: Access Model & App Shell

**Input**: Design documents from `/specs/002-access-model-app-shell/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/access-model-api.md,
quickstart.md, and **001-identity-access implemented** (this feature's Foundational phase resolves
an authenticated principal's roles, which requires 001's session/JWT endpoints to exist).

**Tests**: included as first-class tasks, not optional — Constitution Principle IX requires
per-role, per-scope authorization tests on every endpoint and UI tests asserting menu/action
visibility per role for every screen.

**Organization**: tasks are grouped by user story (spec.md's US1-US5, in priority order) so each
story can be implemented, tested, and delivered independently once the Foundational phase is done.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: which user story this task belongs to (US1-US5); absent for Setup/Foundational/Polish

## Path Conventions

Web application per plan.md: `backend/src/main/java/com/hls/identity/...` (Java/Spring) and
`frontend/src/...` (React/TypeScript).

---

## Phase 1: Setup

**Purpose**: verify the cross-spec prerequisite, then bring in this feature's new dependencies. No
backend dependency is new (Spring Security, Spring Data JPA, Flyway, ArchUnit, Testcontainers are
already in `backend/pom.xml`).

- [x] T000 Verify 001-identity-access is implemented: confirm its sign-in and session/"current user"
      endpoints exist and return an authenticated principal with resolved roles, per
      `specs/001-identity-access/spec.md` FR-011. Do not proceed to Phase 2 (Foundational) until
      this passes — Foundational tasks T010 and T016 resolve access from that authenticated
      principal.
- [x] T001 Add `@mui/x-data-grid` to `frontend/package.json` dependencies and install it.
      `react-router-dom`, `@mui/material`, `@mui/icons-material`, `@emotion/react`,
      `@emotion/styled`, `react-hook-form`, and an axe-core-based accessibility testing dependency
      are already present from spec 001 — do not re-add them.
- [x] T002 [P] Confirm the axe-core-based accessibility testing setup from spec 001 is present and
      runnable (`npm --prefix frontend run test` includes it), needed for the WCAG 2.2 AA checks
      required by SC-007.
- [x] T003 [P] Run `mvn -q -pl backend compile` and `npm --prefix frontend run build` to confirm both
      projects build cleanly on this branch before adding this feature's code.

**Checkpoint**: dependencies installed, both projects build.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: the permission matrix, its seeding and safeguard, the navigation catalog, the
access-model resolver, and the frontend shell skeleton every user story below builds on.

**🚨 CRITICAL**: no user story task may start until this phase is complete.

- [x] T004 Create Flyway migration `backend/src/main/resources/db/migration/V<next>__create_permission_matrix.sql`
      defining table `permission_matrix` with columns `id` (UUID PK), `role`, `module`, `action`,
      `granted` (boolean), `updated_by`, `updated_at`, and a UNIQUE constraint on `(role, module,
      action)`, per data-model.md's "Permission Matrix Entry" table.
- [x] T005 [P] Create `PermissionMatrixEntry` JPA entity in
      `backend/src/main/java/com/hls/identity/permissions/PermissionMatrixEntry.java` with `role`
      enum (`ADMIN`, `DIRECTOR`, `MANAGER`, `TEACHER`, `SYSTEM`), `module` code (`DASHBOARD`,
      `ACCOUNT_PROFILE`, `IDENTITY_PERMISSIONS`, extensible by later specs), `action` enum (`VIEW`,
      `CREATE`, `EDIT`, `DELETE`, `APPROVE`, `PROCESS`, `EXPORT`), `granted` boolean, `updatedBy`,
      `updatedAt` — exactly the fields in data-model.md.
- [x] T006 [P] Create `PermissionMatrixRepository` in
      `backend/src/main/java/com/hls/identity/permissions/PermissionMatrixRepository.java` (Spring
      Data JPA) relying on the DB-level unique `(role, module, action)` constraint from T004.
- [x] T007 Implement `PermissionMatrixService.seedDefaults()` in
      `backend/src/main/java/com/hls/identity/permissions/PermissionMatrixService.java`: on startup,
      idempotently seed `(role, DASHBOARD, VIEW, true)` for all five roles; `(role,
      ACCOUNT_PROFILE, VIEW, true)` and `(role, ACCOUNT_PROFILE, EDIT, true)` for all five roles;
      `(role, IDENTITY_PERMISSIONS, VIEW, true)` and `(role, IDENTITY_PERMISSIONS, EDIT, true)` for
      `ADMIN`, `DIRECTOR`, `SYSTEM` only. MUST NOT create duplicates or overwrite existing rows on
      restart (FR-001, data-model.md seed rules). Depends on T005, T006.
- [x] T008 Implement `PermissionMatrixService.updateGrant(role, module, action, granted)` with the
      last-manager safeguard: before committing, verify at least one of `ADMIN`, `DIRECTOR`,
      `SYSTEM` still holds `(IDENTITY_PERMISSIONS, EDIT, true)` after the change; if not, reject the
      whole edit transactionally with a plain-language reason and apply no partial change (FR-004,
      research.md §8). Depends on T007.
- [x] T009 [P] Create `NavigationCatalog` in
      `backend/src/main/java/com/hls/identity/accessmodel/NavigationCatalog.java` as a versioned
      code constant listing: Dashboard (section "Dashboard", for Admin/Director/Manager/Teacher);
      Dashboard (section "SYSTEM DASHBOARD", for System only, per Constitution Principle IV); Role
      & Permissions (section "SYSTEM" for Admin/Director, "SYSTEM CONFIGURATION" for System);
      Profile / "My Profile" (section "ACCOUNT", all five roles) — each entry carrying section,
      label, route, required module/action, and order, per data-model.md's "Navigation Item" shape.
- [x] T010 Implement `AccessModelService` in
      `backend/src/main/java/com/hls/identity/accessmodel/AccessModelService.java` resolving the
      current principal's roles into `{roles, navigation, actions, dataScope}` by filtering
      `NavigationCatalog` (T009) through the union of the principal's roles' grants in
      `PermissionMatrixEntry` (Constitution Principle II's "union of roles' grants"), omitting any
      section with no authorized item (FR-008). Depends on T007, T009.
- [x] T011 Implement `GET /api/v1/me/access-model` in
      `backend/src/main/java/com/hls/identity/accessmodel/AccessModelController.java`, callable by
      any authenticated caller, returning the shape in `contracts/access-model-api.md`. Depends on
      T010.
- [x] T012 Implement a Spring Security `AuthorizationManager` (or equivalent request-level check) in
      `backend/src/main/java/com/hls/identity/permissions/PermissionAuthorizationManager.java`
      backed by `PermissionMatrixService`, so every protected endpoint independently re-checks
      permission and scope server-side regardless of what the frontend renders (Constitution
      Principle X — "the backend is the security control"). Depends on T007.
- [x] T013 [P] ~~Extend `ArchitectureRulesTest.java`~~ — no such file exists (spec 001 made the same
      call: the existing `com.hls.ApplicationModulesTest` (Spring Modulith's `ApplicationModules
      .verify()`) already enforces module boundaries, and `permissions`/`accessmodel` are internal
      sub-packages of the single `identity` module, not a second top-level module — there is no
      cross-module boundary yet for a dedicated ArchUnit rule to check. Re-verified green after
      adding both packages.
- [x] T014 Set up the frontend app shell skeleton in `frontend/src/app/AppShell.tsx`: wire React
      Router's root layout with a route outlet (no theme or nav content yet — those are added by
      T015/T019 below).
- [x] T015 [P] Reuse `ThemeModeProvider` and design tokens from spec 001
      (`frontend/src/theme/ThemeModeProvider.tsx`, `frontend/src/theme/tokens.ts`) by wrapping
      `AppShell` in the existing provider — do not create a second theme provider. Extend
      `tokens.ts` only if a token this feature needs (e.g. nav-drawer-specific spacing) is missing.
- [x] T016 [P] Implement `useAccessModel` in `frontend/src/access-model/useAccessModel.ts` (types in
      `frontend/src/access-model/types.ts`) calling `GET /api/v1/me/access-model` on app load and on
      every session renewal (research.md §7), exposing `{roles, navigation, actions, dataScope,
      loading}`. Depends on T011.

**Checkpoint**: matrix, seeding, safeguard, navigation catalog, access-model API, backend
authorization framework, and frontend shell/theme/access-model plumbing all exist. User story work
can now begin.

---

## Phase 3: User Story 1 - Every Role Lands on Its Own Dashboard With Its Own Menu (Priority: P1) 🎯 MVP

**Goal**: a signed-in user of any role reaches a role-appropriate dashboard with a matching
navigation menu, with no role picker.

**Independent Test**: sign in as a user holding each of the five roles in turn (and once with two
roles) and confirm the dashboard and nav match exactly their role grants.

### Tests for User Story 1

- [x] T017 [P] [US1] Backend integration test in
      `backend/src/test/java/com/hls/identity/accessmodel/AccessModelResolutionTest.java` verifying,
      for each of the five roles and one two-role combination, that `GET /api/v1/me/access-model`
      returns exactly the seeded Dashboard/Account items for that principal's union of grants, with
      no duplicates (Acceptance Scenarios 1-2, 5).
- [x] T018 [P] [US1] Frontend test in `frontend/src/navigation/NavigationDrawer.test.tsx` asserting,
      per role fixture, only authorized sections/items render, and a section with zero authorized
      items is absent rather than shown empty (FR-007, FR-008).

### Implementation for User Story 1

- [x] T019 [P] [US1] Implement `NavigationDrawer` in `frontend/src/navigation/NavigationDrawer.tsx`
      rendering strictly from `useAccessModel()`'s `navigation` array (FR-006). Depends on T016.
- [x] T020 [P] [US1] Implement the five role dashboards as placeholder/empty-state components:
      `frontend/src/dashboards/AdminDashboard.tsx`, `DirectorDashboard.tsx`, `ManagerDashboard.tsx`,
      `TeacherDashboard.tsx`, `SystemDashboard.tsx` — Admin/Director show org-wide placeholder
      widgets, Manager shows assigned-scope placeholder widgets, Teacher shows self-service
      (attendance/leave/notifications) placeholders, System shows health/users/audit placeholders
      under the "SYSTEM DASHBOARD" nav section (not the shared "Dashboard" section — Constitution
      Principle IV), each in a clear "coming soon" empty state with no fabricated data (FR-012).
- [x] T021 [US1] In `frontend/src/app/AppShell.tsx`, route the signed-in user to a landing dashboard
      that composes the union of their role dashboards (T020) and mounts `NavigationDrawer` (T019)
      inside the shell, with no role-selection step (FR-011). Depends on T014, T019, T020.
- [x] T022 [US1] Add the ACCOUNT → Profile ("My Profile" for Teacher) entry to `NavigationCatalog`
      (T009) and confirm `NavigationDrawer` links it to spec 001's existing Profile screen rather
      than a new implementation (FR-013).

**Checkpoint**: User Story 1 is independently functional and testable.

---

## Phase 4: User Story 2 - Unauthorized Areas Are Invisible and Unreachable (Priority: P1)

**Goal**: a user cannot see or reach, by any means, a screen their role does not grant; a blocked
attempt shows a plain "not authorized" page and fetches no data.

**Independent Test**: using a fixture access model that omits a route, navigate to that route
directly and confirm the "not authorized" page renders with no data fetch — independent of which
real screen is eventually gated this way.

### Tests for User Story 2

- [x] T023 [P] [US2] Frontend test in `frontend/src/app/RouteGuard.test.tsx` using a fixture access
      model and route table: a route absent from the fixture's authorized items renders
      `NotAuthorizedPage` and never mounts (or fetches data for) the target route's component
      (FR-010).
- [x] T024 [P] [US2] Frontend test in `frontend/src/app/NotAuthorizedPage.test.tsx` verifying the
      page reveals nothing about the blocked route's content and offers a link back to the user's
      own dashboard.

### Implementation for User Story 2

- [x] T025 [US2] Implement `RouteGuard` in `frontend/src/app/RouteGuard.tsx`: before rendering a
      matched route's component, check it against `useAccessModel()`'s `navigation`/`actions`;
      render `NotAuthorizedPage` instead on failure (FR-010). Depends on T016.
- [x] T026 [P] [US2] Implement `NotAuthorizedPage` in `frontend/src/app/NotAuthorizedPage.tsx` with a
      plain "not authorized" message and a link back to the dashboard (FR-010).
- [x] T027 [US2] Register every route the shell defines so far (Dashboard, ACCOUNT/Profile from US1)
      through `RouteGuard` in `frontend/src/app/AppShell.tsx`, and add a browser back/forward
      regression test confirming a previously-authorized page is re-checked, not served from cache,
      after a role/grant change (Acceptance Scenario 2). Depends on T021, T025.

**Checkpoint**: User Stories 1 and 2 both work; the guarding mechanism is ready for any route
future stories register, including US3's.

---

## Phase 5: User Story 3 - Admin, Director, and System Maintain the Permission Matrix via a Role & Permissions Menu (Priority: P2)

**Goal**: Admin, Director, and System see a "Role & Permissions" menu item and can change grants
there; Manager and Teacher never see it; a change that would remove the last matrix-manager is
rejected.

**Independent Test**: as Admin, toggle a grant off and confirm it takes effect for an
already-signed-in affected user within one renewal cycle; then attempt to zero out every
Admin/Director/System matrix-manager grant and confirm it is rejected.

### Tests for User Story 3

- [x] T028 [P] [US3] Backend test (Testcontainers) in
      `backend/src/test/java/com/hls/identity/permissions/PermissionMatrixServiceTest.java`
      verifying: a valid edit applies and produces a change record with actor/timestamp/before/after
      (FR-003); an edit that would zero out `ADMIN`/`DIRECTOR`/`SYSTEM`'s `IDENTITY_PERMISSIONS.EDIT`
      grant is rejected with no partial change (FR-004); a `Manager`/`Teacher` caller is refused by
      `PermissionAuthorizationManager` (T012) with no matrix data returned (FR-002).
- [x] T029 [P] [US3] Frontend test in
      `frontend/src/features/permissions/RolePermissionsGrid.test.tsx` and
      `EditGrantDialog.test.tsx` verifying the grid renders current grants and a submitted edit
      surfaces the backend's 409 rejection reason inline when applicable (Acceptance Scenario 3).

### Implementation for User Story 3

- [x] T030 [US3] Implement `GET /api/v1/identity/permission-matrix` and
      `PUT /api/v1/identity/permission-matrix/{role}/{module}/{action}` in
      `backend/src/main/java/com/hls/identity/permissions/PermissionMatrixController.java`, gated by
      `PermissionAuthorizationManager` (T012) to `ADMIN`/`DIRECTOR`/`SYSTEM`; `PUT` calls
      `updateGrant` (T008) and returns 200 with the updated entry or 409 with a `reason` field per
      `contracts/access-model-api.md`.
- [x] T031 [US3] Publish a change-record event (actor, timestamp, role, module, action,
      before/after) from `PermissionMatrixService.updateGrant` (T008) on every successful edit, as an
      extension point spec 003's Audit module will consume — not a direct write to an audit table
      this feature does not own (FR-003).
- [x] T032 [P] [US3] Implement `RolePermissionsGrid` in
      `frontend/src/features/permissions/RolePermissionsGrid.tsx` using MUI X DataGrid Community to
      list all entries from `GET /api/v1/identity/permission-matrix` (T030).
- [x] T033 [P] [US3] Implement `EditGrantDialog` in
      `frontend/src/features/permissions/EditGrantDialog.tsx` using `react-hook-form`, calling `PUT`
      (T030) on submit and displaying the 409 rejection reason inline when the safeguard trips.
- [x] T034 [US3] Add the "Role & Permissions" route to `NavigationCatalog` (T009, already seeded) and
      the frontend route table, registered through `RouteGuard` (T025) so `Manager`/`Teacher` get no
      menu item and a direct URL attempt is refused (FR-002). Depends on T009, T025, T032.
- [x] T035 [US3] Verify (integration test or manual quickstart run) that a matrix edit reaches an
      already-signed-in affected user's `useAccessModel` (T016) within one renewal cycle (≤15
      minutes) without requiring logout (FR-015, SC-004).

**Checkpoint**: User Stories 1-3 all work together; the first genuinely role-restricted screen in
the product exists and is enforced both server-side and in the UI.

---

## Phase 6: User Story 4 - Theme Choice Persists (Priority: P2)

**Goal**: a light/dark theme choice persists on the same device across sign-out/sign-in, with no
unstyled flash and a safe fallback when storage is unavailable.

**Independent Test**: switch theme, sign out, close and reopen the browser, sign back in; confirm
the theme is already applied before content loads.

### Tests for User Story 4

- [x] T036 [P] [US4] Frontend test in `frontend/src/theme/ThemeModeProvider.test.tsx` verifying:
      toggling updates every themed element immediately; the stored preference is read before first
      paint on reload; a mocked storage-unavailable environment falls back to a default theme without
      throwing (Acceptance Scenarios 1-3).

### Implementation for User Story 4

- [x] T037 [US4] Place spec 001's existing theme toggle control in `AppShell`'s top bar
      (`frontend/src/app/AppShell.tsx`), wired to the reused `ThemeModeProvider` (T015). No new
      toggle component is built; persistence and pre-paint read are already spec 001's behavior
      (FR-014, research.md §6). Depends on T015.
- [x] T038 [P] [US4] Add an axe-core accessibility suite in `frontend/src/app/AppShell.a11y.test.tsx`
      running against the shell, `NavigationDrawer`, and all five dashboards in both themes, asserting
      zero critical WCAG 2.2 AA violations (SC-007).

**Checkpoint**: User Stories 1-4 all work together.

---

## Phase 7: User Story 5 - Navigation Adapts to Screen Size (Priority: P3)

**Goal**: the shell works from desktop to a 360px phone width; the nav becomes a drawer below
tablet width; nothing requires horizontal scrolling.

**Independent Test**: load the shell at desktop, tablet, and 360px widths and confirm the nav
pattern and absence of horizontal scroll at each.

### Tests for User Story 5

- [x] T039 [P] [US5] Frontend test in
      `frontend/src/navigation/NavigationDrawer.responsive.test.tsx` verifying the nav renders as a
      persistent column at desktop/tablet widths and as a closed overlay drawer at 360px (Acceptance
      Scenarios 1-2).

### Implementation for User Story 5

- [x] T040 [US5] Implement responsive breakpoints in `frontend/src/navigation/NavigationDrawer.tsx`
      (MUI's `useMediaQuery` + `Drawer` variant switching) so it becomes a temporary overlay drawer
      below tablet width and closes on item selection (FR-009, Acceptance Scenario 3). Depends on
      T019.
- [x] T041 [P] [US5] Check and fix any horizontal-scroll regressions at 360px across `AppShell`, all
      five dashboards, and `RolePermissionsGrid` (FR-009, SC-007).

**Checkpoint**: all five user stories are independently functional together.

---

## Final Phase: Polish & Cross-Cutting Concerns

- [x] T042 [P] Run all five of `quickstart.md`'s manual scenarios end-to-end against a local
      environment and record the results.
- [x] T043 [P] Update `docs/spec-roadmap.md` row 002's status to "Implemented" once every checkpoint
      above has passed.
- [x] T044 Run the full backend suite (`mvn test`, including the ArchUnit checks from T013) and the
      full frontend suite (`npm run test`, including the axe-core checks from T038) together and
      confirm both are green.
- [x] T045 [P] No automated end-to-end harness exists in this repo (no Playwright/Cypress), so no
      automated timing assertion was added. Verified manually instead: `GET /api/v1/me/access-model`
      responded in well under 100ms against the local dev database in live smoke testing, and the
      frontend fetches it once per app load/renewal (research.md §7) — comfortably inside SC-002's
      3-second budget. A real automated timing check is deferred to whenever an e2e framework is
      introduced.

---

## Phase 8: Compact Module × Role matrix screen (added 2026-10-04, User Story 3)

**Purpose**: the 2026-10-04 clarification (spec.md): replace the one-row-per-grant DataGrid with a
compact module × role table of action icons, define which grants are eligible, and refuse the rest.

- [x] T046 [US3] Backend eligibility: give each `PermissionModule` constant its applicable actions
      (`actions()`), add `PermissionEligibility` (module actions minus `IDENTITY_PERMISSIONS` for
      roles other than Admin/Director/System, and minus every teacher/school business module for
      System), and make `PermissionMatrixService.updateGrant` refuse an ineligible grant first, with
      "That permission does not apply to this role and module." and no change (FR-004b)
- [x] T047 [US3] Backend response: `GET /api/v1/identity/permission-matrix` also returns `modules`
      (each module with its eligible actions per role, fixed order) next to `entries`
      (`PermissionMatrixDtos.ModuleView`, `MatrixListResponse`); the PUT's 409 now covers both the
      last-manager safeguard and an ineligible grant (`contracts/access-model-api.md`)
- [x] T048 [US3] Backend tests in `PermissionEligibilityTest`: every module has an action, every
      seeded grant is eligible, only matrix managers can hold Role & Permissions, System has no
      business module but keeps Holiday Calendar and audit, the response lists every module with its
      per-role eligible actions, and ineligible edits return 409 and change nothing
- [x] T049 [US3] Frontend: rebuild `RolePermissionsGrid.tsx` as a Module × Role table (a row per
      module, a column per role, an icon per eligible action: coloured when granted, grey when not,
      a dash where nothing applies), each icon with a tooltip ("Edit - granted") and an accessible
      name that includes role, action, module and state; a click (editors only) opens
      `EditGrantDialog`, view-only users get non-clickable icons; legend limited to the actions in use;
      new `MatrixResponse`/`MatrixModule` types (FR-004a)
- [x] T050 [US3] Frontend tests in `RolePermissionsGrid.test.tsx` (rows and columns, granted vs grey
      icons including a switched-off grant and an action with no stored row, dash cells, click opens
      the confirmation and saves, server refusal shown, read-only mode, load error) and add the
      screen to the axe suite in both themes
- [x] T051 [US3] Update spec.md (clarification, acceptance scenarios 5-7, FR-004a/FR-004b, SC-005a),
      data-model.md (eligibility), contracts/access-model-api.md, research.md (§3 addendum), plan.md,
      quickstart.md (Scenario 3 step 0) and verify the screen against the running app (toggle one
      grant on and off)

- [x] T052 [US1] Menu section order (spec.md FR-008a, data-model.md `order` bands): renumber
      `NavigationCatalog` so the sections run Dashboard, MASTER DATA, OPERATIONS (MY ATTENDANCE for
      Teachers), SYSTEM (SYSTEM CONFIGURATION for System), AUDIT, ACCOUNT; test per role in
      `NavigationSectionOrderTest`; the frontend renders the server's order unchanged
- [x] T054 [US1] Sessions permissions (spec 001 T063-T068): `MY_SESSIONS` (every role) and
      `SESSION_MANAGEMENT` (System, matrix managers only) in the matrix, eligibility, seeds and the
      navigation (ACCOUNT → Sessions; SYSTEM CONFIGURATION → All Sessions)
- [x] T053 [US3] Role & Permissions layout follow-up: each action in its own small cell under each
      role, roles ordered System, Admin, Director, Manager, Teacher, and a Module column sized to the
      longest module name plus 5 characters (`RolePermissionsGrid.tsx`, tests)
- [x] T055 [US3] Per-action icon colours in Role & Permissions: a granted icon wears its action's
      colour (light and dark theme pairs), not-granted icons are grey, header icons match
      (`RolePermissionsGrid.tsx`, colour test, axe cases in both themes)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: no dependencies.
- **Foundational (Phase 2)**: depends on Setup; blocks every user story.
- **User Stories (Phase 3-7)**: all depend on Foundational. Additionally:
  - **US3 (Phase 5)** depends on **US2 (Phase 4)**'s `RouteGuard` (T025) to hide/guard its own
    screen on the frontend, and on Foundational's `PermissionAuthorizationManager` (T012) for
    server-side enforcement — it introduces the product's first genuinely role-restricted screen,
    so it cannot ship before the guarding mechanism exists.
  - **US1, US2, US4, US5** have no dependency on each other and can proceed in parallel once
    Foundational is done.
- **Polish (Final Phase)**: depends on all desired user stories being complete.

### Parallel Opportunities

- Within Foundational: T005/T006 (entity/repo), T009 (nav catalog), T013 (ArchUnit), T015
  (theme provider), T016 (access-model hook) can run in parallel once their own prerequisites land.
- Within US1: T017/T018 (tests) in parallel; T019/T020 (drawer/dashboards) in parallel.
- Within US2: T023/T024 (tests) in parallel; T026 (NotAuthorizedPage) in parallel with T025.
- Within US3: T028/T029 (tests) in parallel; T032/T033 (grid/dialog) in parallel.
- US1, US2, US4, and US5 can be staffed and worked on in parallel by different people once
  Foundational is done; only US3 has a hard cross-story dependency (on US2).

---

## Parallel Example: User Story 1

```bash
# Tests together:
Task: "Backend integration test for access-model resolution per role in backend/src/test/java/com/hls/identity/accessmodel/AccessModelResolutionTest.java"
Task: "Frontend test for per-role nav rendering in frontend/src/navigation/NavigationDrawer.test.tsx"

# Implementation together:
Task: "Implement NavigationDrawer in frontend/src/navigation/NavigationDrawer.tsx"
Task: "Implement the five role dashboards in frontend/src/dashboards/*.tsx"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1 (Setup) and Phase 2 (Foundational).
2. Complete Phase 3 (US1). At this point every role can sign in and see a correct, role-appropriate
   dashboard and menu — nothing sensitive is exposed yet, since Dashboard/Profile are open to all
   five roles.
3. **Stop and validate** against spec.md's User Story 1 acceptance scenarios.

### Incremental Delivery

1. Setup + Foundational → foundation ready.
2. US1 → validate → MVP demo.
3. US2 → validate (guarding mechanism ready, nothing restricted yet to guard).
4. US3 → validate (first restricted screen ships, exercising US2's guard end-to-end).
5. US4 → validate (theming polish).
6. US5 → validate (responsive polish).
7. Final Phase → quickstart run, roadmap update, full suite green.
