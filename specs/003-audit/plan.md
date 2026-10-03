# Implementation Plan: Audit

**Branch**: `003-audit` | **Date**: 2026-09-24 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/003-audit/spec.md`

## Summary

A new `audit` module that is the single append-only store for Login History, Change History, and
User Activity, plus a unified Audit Logs view combining all three. It never receives direct writes
from another module's code — it subscribes to domain events (`LoginHistoryRecorded` from spec 001,
`PermissionMatrixChanged` from spec 002, and five new lifecycle events this spec adds to `identity`)
via Spring Modulith's `@ApplicationModuleListener`, so the write path is durable across restarts and
`identity` keeps zero compile-time dependency on `audit`. Four screens under a new AUDIT navigation
section, visible only to Admin and System, each with search/filter, pagination, and CSV export. As
part of this spec, `identity`'s interim `login_history_event` table (spec 001) is decommissioned now
that `audit` is the real store, closing out Constitution Principle VII for login history
(research.md §6).

## Technical Context

**Language/Version**: Java 25 (backend, Spring Boot 4.1.1-based, per `backend/pom.xml`); TypeScript
5.7 with React 19 (frontend, per `frontend/package.json`) — unchanged from specs 001/002.

**Primary Dependencies**:
- Backend: Spring Web, Spring Data JPA, Spring Modulith (`spring-modulith-starter-core`, already a
  dependency), Flyway, ArchUnit — all already in `backend/pom.xml`. **Correction found during
  implementation**: `spring-modulith-starter-core` alone does not provide
  `@ApplicationModuleListener` or its event publication registry — only module-boundary
  verification. `spring-modulith-starter-jpa` (new dependency, JPA-backed durable event registry,
  reusing the existing PostgreSQL datasource) was added; see tasks.md T004a.
- Frontend: React Router, MUI core, MUI X DataGrid Community (already added in spec 002 for the
  Role & Permissions grid, reused here for the four audit tables) — no new dependency.

**Storage**: PostgreSQL via Flyway migration `V8__create_audit_tables.sql`, adding
`login_history_entry`, `change_history_entry`, `user_activity_entry` (data-model.md) — three typed,
append-only tables rather than one polymorphic table (research.md §3) — followed by
`V9__drop_identity_login_history_event.sql`, which drops `identity`'s now-decommissioned
`login_history_event` table (research.md §6) once its replacement is proven.

**Testing**: Backend — JUnit 5, Spring Boot Test, Spring Modulith Test (`@ApplicationModuleTest` to
verify `audit` only consumes events and publishes none, and event-consumption tests using Modulith's
test support for at-least-once delivery), Testcontainers (PostgreSQL) for repository and
export-streaming tests, ArchUnit (module boundary: only `audit` touches its three tables; no
`update`/`delete` method exists on any audit repository). Frontend — Vitest + React Testing Library,
axe-core accessibility check, consistent with specs 001/002.

**Target Platform**: Browser (desktop, tablet, phone widths) served by the React SPA; backend as the
single Spring Boot deployable, unchanged from prior specs.

**Project Type**: Web application (frontend + backend).

**Performance Goals**: List/search views return within the same interactive-response expectations as
other list screens in the system; export streams rather than buffering so a 10,000-row export
(SC-004) does not spike backend memory. No new user-facing latency target beyond what specs 001/002
already established for list screens.

**Constraints**: Audit entries are never updatable or deletable through any code path (FR-004) — no
`PUT`/`PATCH`/`DELETE` endpoint exists for any of the three entry types, and their repositories
expose no update/delete methods. Exports require a bounded date range (no unbounded full-table CSV).
Every audit endpoint independently re-checks `ADMIN`/`SYSTEM`-only authorization server-side
(Constitution Principle X), regardless of the frontend's (absent) menu item for other roles.

**Scale/Scope**: Under 100 users growing ~30%/year (constitution deployment constraint); at this
spec's scope, three event sources feed the store (login, permission-matrix change, and the five new
account-lifecycle events this spec adds) — later specs (004+) add more Change History and User
Activity sources by publishing their own events, without any contract or schema change here.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Principle I (Operational Truth Must Be Auditable)** — PASS, this spec *is* the implementation of
  that principle's audit-trail requirement: append-only, immutable (FR-004), covering login history,
  change history (starting with permission-matrix edits), and user activity.
- **Principle II (Five Fixed Roles, Configurable Permissions)** — PASS. New permission keys
  (`AUDIT_LOGS`, `AUDIT_LOGIN_HISTORY`, `AUDIT_CHANGE_HISTORY`, `AUDIT_USER_ACTIVITY` ×
  `VIEW`/`EXPORT`) are seeded `ADMIN`/`SYSTEM`-only via the existing `PermissionMatrixSeeder`
  pattern; runtime-editable like every other matrix row, per spec 002.
- **Principle III (Data Scope)** — PASS. Audit data scope is Org-wide for Admin/System and None for
  everyone else (spec.md's Role & Permission Impact table) — no Manager/Zone scoping applies since
  Manager has no access at all.
- **Principle IV (Role-Based Experience)** — PASS. A new AUDIT navigation section appears only for
  Admin/System, following the existing `NavigationCatalog` pattern exactly (no hard-coded per-role
  frontend logic); consistent tables with pagination/sorting/filters (constitution's UI/UX
  constraint) for all four screens.
- **Principle VII (Modular Monolith)** — PASS, conditional on this spec's own decommission task
  (research.md §6) landing: this is the constitution's own `audit` module definition realized, "the
  single append-only store ... No module writes audit history to its own tables." `identity`'s
  interim `login_history_event` table (spec 001, pre-dating `audit`) is dropped once its replacement
  is proven — this spec does not leave that table in place, which would otherwise be a direct
  conflict with Principle VII, not a PASS. `identity`'s live `permission_matrix` table is unaffected
  and is not an audit-history table (it is current-state configuration, not a log); `identity`
  publishes events `audit` subscribes to but never depends on `audit` (research.md §1/§2),
  satisfying the one-directional dependency the constitution implies.
- **Principle IX (Reliability, Testability)** — PASS, requires: per-role/per-scope authorization
  tests on all four endpoints and their export variants, an ArchUnit rule asserting no other module
  writes to the three audit tables, and a Modulith test asserting `audit` has no outbound
  dependency on `identity` beyond the event types it consumes.
- **Principle X (Security, Identity, Observability)** — PASS. Every endpoint re-checks
  `ADMIN`/`SYSTEM` authorization server-side independent of the frontend; audit itself contains no
  secrets and introduces no new authentication surface.
- **Principles V, VI, VIII, XI** — not applicable (no payroll/financial formulas, no training/
  substitution, no virtual-thread batch processing, no recruitment).

No violations requiring justification. Complexity Tracking is not needed.

## Project Structure

### Documentation (this feature)

```text
specs/003-audit/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/
│   └── audit-api.md
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
backend/src/main/java/com/hls/audit/
├── loginhistory/
│   ├── LoginHistoryEntry.java             # entity: data-model.md's Login History Entry
│   ├── LoginHistoryEntryRepository.java   # save + query only, no update/delete methods
│   └── LoginHistoryEventConsumer.java     # @ApplicationModuleListener on LoginHistoryRecorded
├── changehistory/
│   ├── ChangeHistoryEntry.java
│   ├── ChangeHistoryEntryRepository.java
│   └── PermissionMatrixChangeConsumer.java # @ApplicationModuleListener on PermissionMatrixChanged
├── useractivity/
│   ├── UserActivityEntry.java
│   ├── UserActivityEntryRepository.java
│   └── UserActivityEventConsumer.java     # @ApplicationModuleListener on the 4 new identity events
├── logs/
│   ├── AuditLogQueryService.java          # UNION ALL read model over the three tables (US4)
│   └── AuditExportService.java            # streaming CSV, shared by all four export endpoints
├── AuditLoginHistoryController.java       # GET .../login-history, GET .../login-history/export
├── AuditChangeHistoryController.java
├── AuditUserActivityController.java
└── AuditLogsController.java

backend/src/main/java/com/hls/identity/
├── auth/PasswordResetService.java         # + publish PasswordResetRequested/Completed
├── session/SessionController.java         # + publish SessionEnded
├── auth/PasswordAuthService.java          # + publish AccountLockChanged (lock + unlock branches)
├── user/UserAdminService.java             # + publish AccountActivationChanged(active=false)
├── activity/                              # new package: the 5 event records themselves
│   ├── PasswordResetRequested.java · PasswordResetCompleted.java · SessionEnded.java
│   └── AccountLockChanged.java · AccountActivationChanged.java
└── loginhistory/
    ├── LoginHistoryPublisher.java         # write to its own table removed once audit's consumer
    │                                      #   is proven (research.md §6) — publish call unchanged
    └── LoginHistoryEvent.java · LoginHistoryEventRepository.java   # removed by the same task
    # each publish addition mirrors LoginHistoryPublisher's existing publish-after-write shape; no
    # new dependency from identity onto audit's packages

backend/src/main/resources/db/migration/
├── V8__create_audit_tables.sql
└── V9__drop_identity_login_history_event.sql   # decommission (research.md §6); no reader exists
                                                 # today, confirmed by repo-wide search

backend/src/main/java/com/hls/identity/permissions/
└── PermissionModule.java                  # + AUDIT_LOGS, AUDIT_LOGIN_HISTORY, AUDIT_CHANGE_HISTORY,
                                            #   AUDIT_USER_ACTIVITY constants (additive, per its own
                                            #   Javadoc)

backend/src/main/java/com/hls/identity/accessmodel/
└── NavigationCatalog.java                 # + 4 AUDIT-section NavItems (ADMIN, SYSTEM only)

backend/src/test/java/com/hls/audit/
├── loginhistory/ · changehistory/ · useractivity/ · logs/   # unit + Testcontainers integration
├── ApplicationModuleBoundaryTest.java     # @ApplicationModuleTest: audit consumes, publishes nothing
└── ArchitectureRulesTest.java             # extended: audit tables owned only by audit; no update/delete

frontend/src/
├── features/
│   └── audit/
│       ├── LoginHistoryPage.tsx
│       ├── ChangeHistoryPage.tsx
│       ├── UserActivityPage.tsx
│       ├── AuditLogsPage.tsx
│       ├── AuditFilterBar.tsx             # shared filter controls (date range, actor/user, type)
│       └── useAuditExport.ts              # triggers the streamed CSV download
└── navigation/
    └── NavigationDrawer.tsx               # unchanged component, renders the new AUDIT section
                                            # because it comes from the server access model
```

**Structure Decision**: Web application (Option 2: `backend/` + `frontend/`, unchanged repo layout).
Backend introduces exactly one new bounded context, `audit`, matching the constitution's module list
verbatim. It touches `identity` only additively (new event-publish calls, new enum constants, new
nav entries) — no existing `identity` behavior changes. Frontend adds one new `features/audit`
folder following the same page/filter/grid conventions spec 002 established for
`features/permissions`; no changes to the shell, router, or theme.

## Complexity Tracking

> Fill ONLY if Constitution Check has violations that must be justified

No violations. Table intentionally omitted.
