# Implementation Plan: Access Model & App Shell

**Branch**: `002-access-model-app-shell` | **Date**: 2026-09-23 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/002-access-model-app-shell/spec.md`

## Summary

Give every signed-in user (from spec 001) a role-appropriate landing dashboard and navigation menu,
built from a seeded, runtime-editable role→permission matrix. The `identity` module resolves each
user's roles into an access model (menus, actions, data scope); the frontend renders the shared app
shell (nav, router with guards, theme) strictly from that model, never from hard-coded per-role
logic. Admin, Director, and System maintain the matrix through a single "Role & Permissions" screen,
with every change audit-ready and a safeguard against locking everyone out of it.

## Technical Context

**Language/Version**: Java 25 (backend, Spring Boot 4.1.1-based, per `backend/pom.xml`); TypeScript
5.7 with React 19 (frontend, per `frontend/package.json`).

**Primary Dependencies**:
- Backend: Spring Web, Spring Security + OAuth2 Resource Server (validates the JWTs spec 001
  issues), Spring Data JPA, Spring Modulith (module boundaries), Flyway (schema), ArchUnit (boundary
  tests) — all already in `backend/pom.xml`.
- Frontend: React Router, MUI / Material UI core + `ThemeProvider`, and `react-hook-form` — all
  introduced by spec 001 (which ships first) and reused here unchanged, no new dependency. MUI X
  DataGrid Community edition (every later master-data list/edit grid; the Role & Permissions
  matrix itself became a compact module × role icon table on 2026-10-04, research.md §3 addendum)
  is the one genuinely new dependency this spec adds — MIT-licensed, no paid tier
  required for sorting/filtering/pagination/inline editing.

**Storage**: PostgreSQL via Flyway migration, adding `permission_matrix` (role, module, action,
granted, updated_by, updated_at). No storage for navigation items — the navigation tree is a small,
versioned code constant per module, filtered at resolution time by the matrix; this avoids a second
source of truth for "what screens exist."

**Testing**: Backend — JUnit 5, Spring Boot Test (`@WebMvcTest`/`@SpringBootTest`), Spring Modulith
Test, ArchUnit (module-boundary and no-hard-coded-role-logic checks), Testcontainers (PostgreSQL) for
the matrix repository and access-model resolution. Frontend — Vitest + React Testing Library for
component/route-guard behavior, plus an automated accessibility check (axe-core via
`@testing-library/jest-dom`-compatible tooling) to verify WCAG 2.2 AA per SC-007.

**Target Platform**: Browser (desktop, tablet, phone widths) served by the React SPA; backend as the
single Spring Boot deployable per the constitution's deployment constraints (one EC2/VM, no load
balancer at this scale).

**Project Type**: Web application (frontend + backend).

**Performance Goals**: Landing dashboard reachable within 3 seconds of sign-in (SC-002). The
access-model resolution endpoint is on the critical path of every page load and session renewal, so
it targets p95 < 200ms server-side (an internal engineering target supporting SC-002, not a
user-facing success criterion itself).

**Constraints**: No horizontal scroll at 360px width (FR-009); WCAG 2.2 AA in both themes (FR-014);
a matrix edit that would remove the last Admin/Director/System matrix-manager MUST be rejected,
never partially applied (FR-004); permission checks MUST fail closed and be enforced server-side —
the frontend's hiding of menu items is a UX convenience only, never the authorization boundary
(Constitution Principle X).

**Scale/Scope**: Under 100 users growing ~30%/year (constitution deployment constraint). At this
spec's scope, the matrix and navigation model cover exactly two modules (Dashboard, Account/Profile)
plus the Role & Permissions screen itself; every later spec (003+) adds its own rows without
changing this feature's schema or contract shape.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Principle II (Five Fixed Roles, Configurable Permissions)** — PASS. This spec *is* the seeded,
  runtime-editable matrix. Editing is restricted to Admin/Director/System (FR-002); the last-manager
  safeguard (FR-004) directly implements "MUST NOT allow any combination that removes the last user
  able to manage permissions."
- **Principle III (Data Scope)** — PASS. The access-model contract carries a data-scope
  classification (Org-wide/Assigned/Own/None) per item now, even though only Dashboard uses it at
  this stage, so specs 005+ attach real scoped data without a contract change (documented as an
  Assumption in spec.md).
- **Principle IV (Role-Based Experience in One Shared Application)** — PASS, this is the primary
  scope: persistent/collapsible nav, phone-width drawer, server-driven menus, light/dark theme,
  WCAG 2.2 AA, per-role dashboards, no role picker.
- **Principle VII (Modular Monolith)** — PASS. The permission matrix and access-model resolution
  live in `identity`, matching the constitution's module list ("`identity`: ... the role→permission
  matrix ... and the per-user navigation and permission model consumed by the UI"). No new module is
  introduced; ArchUnit continues to enforce that no other module reads/writes the matrix table
  directly.
- **Principle IX (Reliability, Testability)** — PASS, requires explicit coverage: authorization
  tests per role and per module/action on the access-model and matrix-edit endpoints, plus frontend
  tests asserting which menu items and actions each of the five roles sees (FR-006/FR-007). Planned
  in Phase 1 as part of the test strategy, not deferred.
- **Principle X (Security, Identity, Observability)** — PASS with an explicit design rule: the
  backend independently re-checks permission and scope on every request; the frontend's hidden menus
  are never treated as the enforcement point. Matrix changes are structured-logged and published as
  change records for spec 003.
- **Principles I, V, VI, VIII, XI** — not applicable to this spec's scope (no financial data, no
  payroll/training, no batch concurrency, no recruitment).

No violations requiring justification. Complexity Tracking is not needed.

## Project Structure

### Documentation (this feature)

```text
specs/002-access-model-app-shell/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
│   └── access-model-api.md
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
backend/src/main/java/com/hls/identity/
├── permissions/
│   ├── PermissionMatrixEntry.java         # entity: role, module, action, granted, audit columns
│   ├── PermissionMatrixRepository.java
│   ├── PermissionMatrixService.java       # seed-on-startup, edit + last-manager safeguard
│   └── PermissionMatrixController.java    # GET/PUT, Admin/Director/System only
├── accessmodel/
│   ├── NavigationCatalog.java             # code-defined tree of nav items per module (versioned)
│   ├── AccessModelService.java            # resolves a principal's roles -> menus/actions/scope
│   └── AccessModelController.java         # GET /api/v1/me/access-model
└── (existing spec-001 packages: auth, session, otp, user)

backend/src/main/resources/db/migration/
└── V<next>__create_permission_matrix.sql

backend/src/test/java/com/hls/identity/
├── permissions/                           # unit + Testcontainers integration tests
├── accessmodel/                           # per-role/per-scope authorization tests
└── ArchitectureRulesTest.java             # extended: matrix table owned only by identity

frontend/src/
├── app/
│   ├── AppShell.tsx                       # persistent nav + main content + router outlet
│   ├── RouteGuard.tsx                     # checks access model before rendering a route
│   └── NotAuthorizedPage.tsx
├── theme/
│   ├── tokens.ts                          # color/spacing/type tokens, light + dark palettes
│   └── ThemeModeProvider.tsx              # toggle + persisted preference (device storage)
├── access-model/
│   ├── useAccessModel.ts                  # fetches/caches the current user's access model
│   └── types.ts
├── navigation/
│   └── NavigationDrawer.tsx               # renders sections/items strictly from the access model
├── dashboards/
│   ├── AdminDashboard.tsx
│   ├── DirectorDashboard.tsx
│   ├── ManagerDashboard.tsx
│   ├── TeacherDashboard.tsx
│   └── SystemDashboard.tsx
├── features/
│   └── permissions/
│       ├── RolePermissionsGrid.tsx        # Module × Role table of action icons (2026-10-04; was a DataGrid)
│       └── EditGrantDialog.tsx            # react-hook-form dialog for a single grant edit
└── account/
    └── ProfilePage.tsx                    # reused from spec 001, not rebuilt here
```

**Structure Decision**: Web application (Option 2: `backend/` + `frontend/`, already the repo's
layout). Backend work lands entirely inside the existing `identity` module (two new sub-packages:
`permissions`, `accessmodel`) — no new bounded context is introduced. Frontend work establishes the
shell/navigation/theme conventions that every later spec's screens plug into, so its structure here
is the one the rest of the project follows, not a one-off for this feature.

## Complexity Tracking

> Fill ONLY if Constitution Check has violations that must be justified

No violations. Table intentionally omitted.
