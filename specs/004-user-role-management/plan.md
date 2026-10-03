# Implementation Plan: User & Role Management

**Branch**: `004-user-role-management` | **Date**: 2026-10-03 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/004-user-role-management/spec.md`

## Summary

Exposes `identity`'s existing, internal-only `UserAdminService` (spec 001) through a real screen
and API: Admin/System can create a user, search/browse the full user list, add or remove role
assignments on an existing user, deactivate/reactivate a user, and set a new password directly on
another user's account ("admin-triggered reset"). Every action publishes a lifecycle event spec
003's existing `UserActivityEventConsumer` already knows how to append to User Activity — this spec
only adds new event types and `@ApplicationModuleListener` branches, no new audit table. A new
user-level safeguard rejects, as a single all-or-nothing action, any deactivation or role removal
that would leave zero active accounts holding the Admin role — distinct from, and additional to,
the matrix-level "last matrix manager" safeguard spec 002 already built into
`PermissionMatrixService.updateGrant`. Role & Permission Management itself (editing the
role→permission matrix) needs no new work: it was already fully delivered by specs 002/003.

## Technical Context

**Language/Version**: Java 25 (backend, Spring Boot 4.1.1-based); TypeScript 5.7 with React 19
(frontend) — unchanged from specs 001-003.

**Primary Dependencies**: Spring Web, Spring Data JPA, Spring Security (JWT resource server),
Spring Modulith (`@ApplicationModuleListener`, already in use), Flyway, ArchUnit — all already in
`backend/pom.xml`, no new backend dependency. Frontend: React Router, MUI core, MUI X DataGrid
Community, `react-hook-form` — all already present from specs 001/002, no new frontend dependency.

**Storage**: PostgreSQL. No new table: this spec adds new role-assignment query methods to the
existing `role_assignment` table (spec 001) and reuses `audit`'s existing `user_activity_entry`
table (spec 003) for every new action type, since its `action` column is free text and additive by
design (the same pattern spec 003 itself used for `PermissionModule`).

**Testing**: Backend — JUnit 5, Spring Boot Test, Testcontainers (PostgreSQL) for integration tests,
per-role/per-action authorization tests on every new endpoint (Constitution Principle IX). Frontend
— Vitest + React Testing Library, axe-core accessibility check in both themes, consistent with
specs 001-003.

**Target Platform**: Browser (desktop, tablet, phone widths) served by the React SPA; backend as
the single Spring Boot deployable, unchanged.

**Project Type**: Web application (frontend + backend).

**Performance Goals**: User search/list returns within the same interactive-response expectations
as every other list screen in the system (spec 003's audit screens, spec 002's permission grid).
No new latency target; under 100 users total (constitution deployment constraint) makes this a
non-issue at this scale.

**Constraints**: Every new endpoint independently re-checks `ADMIN`/`SYSTEM`-only authorization
server-side (Constitution Principle X), regardless of what the frontend renders. The last-admin
safeguard (FR-007) must reject the *entire* request with no partial effect — no endpoint may
deactivate a user or strip a role and only then discover it broke the invariant. Deactivation,
reactivation, and admin-triggered reset all reuse spec 001's exact session-termination and
lockout-clearing behavior rather than re-implementing it.

Access-token validation gains a check that the token's `sid` session is not revoked and the user is
active (a per-request lookup; at under 100 users this is fine), so deactivation and admin reset take
effect on the next request, not after the 15-minute token lifetime (FR-004/FR-006). The last-admin
check runs inside the same transaction as the change, after taking a pessimistic write lock on the
Admin `role_assignment` rows, so concurrent requests cannot both pass it (FR-011).

**Scale/Scope**: Under 100 users growing ~30%/year. At this spec's scope, new identity
lifecycle event types feed `audit`'s existing User Activity consumer: three new (`UserCreated`,
`UserRoleChanged`, `PasswordResetByAdmin`) plus the existing `AccountActivationChanged`, which gains
its first `active=true` caller; no schema change to any audit table.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Principle I (Operational Truth Must Be Auditable)** — PASS. Every user-management action is
  recorded as an immutable User Activity entry (FR-008) via the same durable,
  `@ApplicationModuleListener`-based event path spec 003 already established; role assignments are
  explicitly named in Principle I's text as something that "MUST be audited."
- **Principle II (Five Fixed Roles, Configurable Permissions)** — PASS. No new role is introduced;
  role assignment is still limited to the fixed five. A new `userManagement.{view,create,edit}`
  permission key is seeded Admin/System-only, matching the constitution's own default role access
  matrix row for User Management verbatim. Role & Permission Management's own matrix-editing
  capability, its audit trail, and its matrix-level last-manager safeguard are unchanged — already
  fully delivered by specs 002/003.
- **Principle III (Data Scope)** — PASS. User Management's data scope is Org-wide for Admin/System
  and None for Director/Manager/Teacher (spec.md's Role & Permission Impact table); no Manager/Zone
  scoping applies, since Manager has no access at all.
- **Principle IV (Role-Based Experience)** — PASS. Two new `NavigationCatalog` entries ("User
  Management" under `SYSTEM` for Admin, under `SYSTEM CONFIGURATION` for System) follow the exact
  existing pattern; no hard-coded per-role frontend logic. Consistent list/search table with
  pagination (constitution's UI/UX constraint), reusing the DataGrid convention from spec 002/003.
- **Principle VII (Modular Monolith)** — PASS. All new code lives inside the existing `identity`
  module (`user` and `activity` packages); `audit` gains new `@ApplicationModuleListener` branches
  on its existing `UserActivityEventConsumer`, consuming four event types without a new table or a
  new dependency direction (still `identity → audit` via events only, never the reverse).
- **Principle IX (Reliability, Testability)** — PASS, requires: per-role authorization tests on
  every new endpoint; a dedicated last-admin-safeguard test for both deactivation and role removal,
  including the boundary case of two active Admins; an audit-consumer test per new event type.
- **Principle X (Security, Identity, Observability)** — PASS. Every new endpoint re-checks
  `ADMIN`/`SYSTEM` authorization server-side; admin-triggered password reset reuses spec 001's
  exact password-policy validation (≥10 characters, not equal to the phone number) rather than a
  weaker or divergent rule, extracted once into a small shared helper so both call sites (self-
  service reset and this spec's admin reset) can never drift apart.
- **Principles V, VI, VIII, XI** — not applicable (no payroll/financial formulas, no training/
  substitution, no virtual-thread batch processing, no recruitment).

No violations requiring justification. Complexity Tracking is not needed.

## Project Structure

### Documentation (this feature)

```text
specs/004-user-role-management/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/
│   └── user-management-api.md
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
backend/src/main/java/com/hls/identity/
├── user/
│   ├── AppUser.java                     # + reactivate() (mirrors existing deactivate())
│   ├── AppUserRepository.java           # + search(query, Pageable) and a role-membership helper
│   ├── RoleAssignmentRepository.java    # + findByRole(Role), deleteByUserIdAndRole(UUID, Role)
│   ├── UserAdminService.java            # + updateRoles, reactivateUser, adminResetPassword,
│   │                                    #   search; deactivateUser gains an actorUserId param and
│   │                                    #   the last-admin safeguard; createUser publishes UserCreated
│   ├── LastAdminGuard.java              # new: the one safeguard check, used by updateRoles and
│   │                                    #   deactivateUser so the rule lives in exactly one place
│   └── UserManagementController.java    # new: GET/POST/PUT endpoints, contracts/user-management-api.md
├── activity/
│   ├── UserCreated.java                 # new event
│   ├── UserRoleChanged.java             # new event (role, added)
│   └── PasswordResetByAdmin.java        # new event
├── auth/
│   ├── PasswordPolicy.java              # new: extracted shared validator (research.md §2), used by
│   │                                    #   both PasswordResetService.complete and the new admin reset
│   └── PasswordResetService.java        # uses PasswordPolicy instead of its own inline check
├── permissions/
│   └── PermissionModule.java            # + USER_MANAGEMENT constant (additive)
└── accessmodel/
    └── NavigationCatalog.java           # + 2 "User Management" NavItems (ADMIN, SYSTEM)

backend/src/main/java/com/hls/audit/useractivity/
└── UserActivityEventConsumer.java       # + 3 @ApplicationModuleListener methods (UserCreated,
                                          #   UserRoleChanged, PasswordResetByAdmin); its existing
                                          #   AccountActivationChanged listener is untouched — it
                                          #   already branches on active true/false

backend/src/test/java/com/hls/identity/user/
├── UserManagementControllerTest.java    # per-role authorization, create, search, role change,
│                                        #   (re)activate, admin reset — Testcontainers integration
└── LastAdminGuardTest.java              # unit: single Admin refused, two Admins allowed, combined
                                         #   role-removal-plus-deactivation-in-one-request refused

backend/src/test/java/com/hls/audit/useractivity/
└── UserActivityEventConsumerTest.java   # extended: 3 new event types map to the right `action`

frontend/src/features/users/
├── UserManagementPage.tsx               # list + search, reusing the DataGrid convention
├── CreateUserDialog.tsx
├── EditRolesDialog.tsx
├── ResetPasswordDialog.tsx
└── userManagementApi.ts                 # authFetch-based client, mirrors features/permissions

frontend/src/App.tsx                     # + /identity/users route through the existing RouteGuard
```

**Structure Decision**: Web application (Option 2: `backend/` + `frontend/`, unchanged repo
layout). All backend changes are additive within the existing `identity` module and `audit`'s
existing `useractivity` consumer — no new bounded context, no new table. Frontend adds one new
`features/users` folder following the same page/dialog conventions spec 002 established for
`features/permissions`; no changes to the shell, router, or theme beyond one new guarded route.

## Complexity Tracking

> Fill ONLY if Constitution Check has violations that must be justified

No violations. Table intentionally omitted.
