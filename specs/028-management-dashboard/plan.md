# Implementation Plan: Management Dashboard

**Branch**: `028-management-dashboard` | **Date**: 2026-10-21 | **Spec**: [spec.md](spec.md)

## Summary

A new `reporting` module (package `com.hls.reporting`, api / internal / web like the other modules) that owns no tables. It
assembles the Director's and Admin's one-screen overview from **tile sources** that the owning modules implement behind a small
SPI in `reporting.api` (`TileSource`, `ApprovalSource`, `ManagerColumns`), the same inversion the codebase already uses for
`PendingManagerRequests`, `SupplySource` and the planned-activity feeds. `reporting` therefore depends on no source module, never
touches another module's tables and cannot create a cycle; each source module implements the SPI in its own `internal`
package, mostly by calling the figure service it already has (task summary, marketing dashboard, recruitment dashboard,
attendance read APIs, payroll runs), so tile and screen cannot disagree. Three read endpoints under `/api/v1/dashboard`
(manifest, one area, inbox) serve the web and, unchanged, a later mobile spec. A React feature (`features/dashboard`) renders
the manifest, loads each area on its own, and replaces the placeholder Admin and Director dashboards. No migration, no new
permission module, no new navigation item. See [research.md](research.md) for the decisions.

Read from the code, not the planning documents (research.md section 1): none of the figures is behind a public interface
today (task summary, marketing and recruitment dashboards, payroll runs and the substitution counts all live in `internal`),
so each owning module gains a small `*Tiles` class; there is no `reporting` package yet; `DashboardRouter` tests role names
today.

## Technical Context

- Java 25, Spring Boot, Spring Modulith, JPA, PostgreSQL (Testcontainers in tests), React + Vite + Vitest.
- Migration: **none**. The Dashboard stores nothing (spec FR-021 and Key Entities).
- Constraints: read-only transactions, business date through `BusinessDate` (Asia/Kolkata), scope applied by each source with
  the shared `ScopeQueries` / `TeacherScopeQueries`, 404/403 shaped like other modules, no audit of reads (FR-020).
- Caching: none. Dashboard figures change all the time and must equal the lists (docs/caching.md rule 1); only a per-request
  memo lets several tiles share one computation. No cache row is added to docs/caching.md; a one-line "not cached" note is.
- Scale: under 100 users; the built areas must be complete within 3 seconds (SC-002), met by one request per area, bulk
  queries in each source and a per-tile time limit (research.md section 7).

## Constitution Check

| Principle | How it is met |
| --- | --- |
| I Auditable | Read-only; nothing to record (FR-020). Every figure links to the list whose records carry their own history. |
| II Five roles, RBAC | No new permission module. A tile requires the View grant of the module that owns its figure; an inbox kind requires that module's Approve or Process grant. Editing the matrix changes the tiles with no code change. Teacher and System hold none of these grants, so their manifest is empty. |
| III Data scope | Each source applies the caller's scope itself (Manager: own Zones, Schools, Teachers, own tasks). Tiles marked org-wide-only (comparison table, payroll, inbox) are never offered to a Manager. A Zone the caller cannot reach answers 404. |
| IV Role-based UI | The manifest, built on the server from grants and scope, is the only source of what appears; no role name is tested in the new screen. The existing Dashboard menu item is reused. |
| V Formula-driven | Not touched: no figure is computed here that a module does not already own; ratios come from the owning service ("no data yet" on a zero denominator). |
| VI Training and substitution | Not touched; substitution counts are read as the Substitutions list shows them. |
| VII Modular monolith | `reporting.api` holds the SPI and depends on nothing but `identity` types. `reporting.internal` depends on `reporting.api`, `identity`, `organization.api` (Zone and Manager names), `designation.api` (BusinessDate). Source modules depend on `reporting.api`, never the reverse. ArchUnit and Modulith verification enforce it. |
| VIII Concurrency | Request-path reads only; no locking; areas run independently. |
| IX Testability | A test per tile (figure equals the linked list, SC-003), per endpoint and role (SC-004), source missing and source failing (SC-005), a test tile registered in the test setup (SC-006), payroll permission (SC-007). |
| X Security | Server-side permission and scope on every request; a denied figure is never serialized; amounts only to Payroll holders; free text from other modules is escaped on output. |

Constitution change: PATCH to Principle VII naming the `reporting` module and its SPI (number assigned at merge, because 026
and 015 also claim the next patch). No principle changes, no matrix row changes. Gate result: pass before research and after
design, with no violations.

## Project Structure

```text
specs/028-management-dashboard/ spec.md plan.md research.md data-model.md quickstart.md tasks.md contracts/
backend/src/main/java/com/hls/reporting/
  api/       TileSource, TileDefinition, TileContext, TileResult, Area, ApprovalSource, ApprovalItem, ManagerColumns, package-info
  internal/  TileRegistry, DashboardService, AreaReader (per-tile time limit, failure isolation), InboxService,
             ManagerComparisonService, ZoneResolver
  web/       DashboardController, DashboardExceptionAdvice
backend/src/main/java/com/hls/tasks/internal/TaskTiles.java, TaskApprovals.java, TaskManagerColumns.java
backend/src/main/java/com/hls/recruitment/marketing/internal/MarketingTiles.java, FinalStageApprovals.java, MarketingManagerColumns.java
backend/src/main/java/com/hls/recruitment/internal/RecruitmentTiles.java
backend/src/main/java/com/hls/teacher/internal/TeacherTiles.java
backend/src/main/java/com/hls/attendance/internal/TeacherAttendanceTiles.java
backend/src/main/java/com/hls/attendance/manager/internal/ManagerAttendanceTiles.java, ForgottenCheckInApprovals.java, ManagerAttendanceColumns.java
backend/src/main/java/com/hls/leave/internal/TeacherLeaveTiles.java
backend/src/main/java/com/hls/leave/manager/internal/ManagerLeaveTiles.java, ManagerLeaveApprovals.java
backend/src/main/java/com/hls/substitution/internal/SubstitutionTiles.java
backend/src/main/java/com/hls/payroll/run/internal/PayrollTiles.java, PayrollApprovals.java
frontend/src/features/dashboard/ (ManagementDashboard, AreaSection, TileCard, ManagerComparisonTable, InboxPanel,
  DashboardFilters, dashboardApi, format, __tests__)
frontend/src/dashboards/ (AdminDashboard and DirectorDashboard now render ManagementDashboard; ManagerDashboard keeps its cards)
postman/ (collection additions)
```

**Structure Decision**: one new backend module and one frontend feature; the only changes to existing modules are the small
`*Tiles` / `*Approvals` / `*Columns` classes that implement the SPI, each next to the service whose figure it exposes. Mobile
is out of scope (spec); the endpoints are client-neutral.

## Design Decisions

See [research.md](research.md). Highlights: the SPI is inverted so `reporting` has no dependency on a dozen modules; a tile is
described by data (key, area, order, label, period kind, grant, scope kind, link) and read one at a time so one failure is one
tile; a tile result is a small tagged value (`OK`, `NO_DATA`, `NOT_AVAILABLE`, `NOT_WORKING_DAY`, `ERROR`) so a missing source
can never be a 0; the web loads the manifest once and each area separately; the Manager-by-Manager table is merged from
columns that each module contributes; the inbox merges items from each module's `ApprovalSource`, drops the caller's own
requests and sorts oldest first; unbuilt modules register nothing, so their tiles are absent rather than "not available yet".
The tile list is in [contracts/tile-catalog.md](contracts/tile-catalog.md).

## Phases

1. `reporting` module: SPI types, registry, area reader, manifest and area endpoints, Zone and period handling, test tile (US7, US8).
2. Sources for Managers (tasks, Manager attendance, Manager leave) and the comparison table (US2).
3. Sources for Schools and Recruitment (US3), Teachers, operations, attendance and leave (US4).
4. Payroll tiles and the Payroll-permission guarantee (US5).
5. Approvals inbox and its five sources (US6).
6. Manager scoping tests (US9); frontend feature; replace the Admin and Director placeholders; Postman, demo data, roadmap,
   constitution patch.

## Open items needing a decision

1. **Manager's own Dashboard (spec US9, FR-016).** `ManagerDashboard` (spec 005) shows Zone, School and Teacher count cards
   fetched from their own endpoints. Default taken in this plan: keep those cards unchanged and render the scoped tiles of the
   manifest below them. Alternative: replace the cards with tiles. Needs the owner's choice before tasks; the default is
   additive and cheap to change later.
2. **Admin and Director see the same Dashboard** (spec Assumptions, still marked "to confirm"). Taken as true: differences come
   only from the permission matrix. Say so if the Director should have a different default layout.
3. **Constitution patch number.** Pending until 026 and 015 merge; assigned at merge (not a design question).
