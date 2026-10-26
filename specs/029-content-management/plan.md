# Implementation Plan: Content Management

**Branch**: `spec/029-content-management` | **Date**: 2026-10-26 | **Spec**: [spec.md](spec.md)

## Summary

A new `content` module (package `com.hls.content`, api / internal / web) owns content records, numbered versions,
review entries and history. Files live in the shared `files` module, which is extended additively (video and audio
types, a limit per type, streamed storage and download). Content tasks are created through `tasks.api.TaskCreation`
(C2) for Manager authors with a due date. The `identity` module gains a **sixth fixed role, Content Contributor**
(`Role.CONTENT_CONTRIBUTOR`), the `CONTENT` permission module, navigation items and a Contributor dashboard.
`notification` gains four types and a listener; `reporting` gets a Content tile source. A React feature
(`features/content`) provides My Content (Manager, Contributor) and Content (Admin, Director).

## Technical Context

- Java 25, Spring Boot, Spring Modulith, JPA, Flyway, PostgreSQL (Testcontainers), React + Vite + Vitest.
- Migration `V95__create_content.sql` (max on main is V90). The role value `CONTENT_CONTRIBUTOR` is 19 characters and fits the
  existing `VARCHAR(20)` columns of `role_assignment` and `permission_matrix`, so no column change is needed; there is no
  check constraint on role values.
- Constraints: append-only history (trigger), no cross-module foreign keys, audit through `ChangeRecorder`, business date
  through `BusinessDate`, no whole-file `byte[]` for content files.

## Constitution Check

| Principle | How it is met |
| --- | --- |
| I Auditable | `content_history` append-only (trigger) plus Change History for every field change, version, review and file removal. Role assignment of the new role is audited by the existing 004 path. |
| II Roles, RBAC | **Amended**: five fixed roles become six (Content Contributor). Module `CONTENT` (View, Create, Edit, Approve); Approve eligible for Admin and Director only; Contributor eligible only for Content, Notifications, Dashboard, Account. See "Constitution amendment" below. |
| III Data scope | Admin and Director org-wide; Manager and Contributor see content they authored or submitted (server-filtered, others 404). Teacher and System: none. |
| IV Role-based UI | Menus from the navigation catalog: "Content" (Admin, Director) and "My Content" (Manager, Contributor) under OPERATIONS; Contributor dashboard; sixth matrix column. |
| V/VII Modules | `content` reads `identity` (users, grants), `files.api`, `tasks.api.TaskCreation`, `designation.api` (BusinessDate), `organization.api` (is the author an active Manager). Only `notification` and `reporting` read `content.api`. ArchUnit test enforces it. |
| VIII Concurrency | Optimistic version on the content record; second writer gets 409. |
| IX Testability | Unit, API, per-role authorization (six roles), ArchUnit, file-limit and frontend tests. |
| X Security | Server re-checks grant and scope on every endpoint; first-byte checks per type; downloads as attachments gated by visibility; no public links. |

## Constitution amendment (proposed 3.0.0)

Needs the owner's sign-off before merge; the text is already edited in `.specify/memory/constitution.md` on this branch.

- Principle II: title and list become six roles; "Admin may create new roles in future" becomes "a further role is a
  constitution amendment"; new Content Contributor bullet.
- Principle III: Content Contributor scope line. Principle IV: "All six roles", Contributor sections and dashboard.
- Default role access matrix: a Content row and a Content Contributor column (all other rows "—").
- Development Workflow item 2: "each of the six roles".
- Version: MAJOR 3.0.0 under the governance rule (a principle is redefined); MINOR 2.6.0 is the alternative.

## Impacts on other specs and code

| Spec | Impact | Kind |
| --- | --- | --- |
| 001 identity-access | `Role` enum gains `CONTENT_CONTRIBUTOR`; sign-in recognises it; bootstrap unchanged; no mobile eligibility (`MobileRoleEligibility` stays Teacher, Manager, Director) | code + doc note |
| 002 access-model-app-shell | exhaustive role switches in `AccessModelService` (`masterDataScopeFor`, `dashboardScopeFor`, line ~188) and `NavigationCatalog` role sets; `PermissionEligibility` (Contributor ineligible except allowed modules); `PermissionMatrixService` seeds; matrix grid shows a sixth column; "five dashboards" becomes six (Contributor dashboard); FR-001/FR-012 wording | code + doc note |
| 003 audit | `AuditVisibility` entries `CONTENT`, `CONTENT_VERSION`, `CONTENT_REVIEW` require View on `CONTENT`; actor-roles text already stores any role | code |
| 004 user-role-management | role picker and API accept the sixth role; "one or more of the five fixed roles" wording; create-user with only this role; last-Admin rule untouched; `UserAdminService` default role stays Teacher | code + doc note |
| 005 / 012 / 016 / 023 / 031 | none functionally; `files` store changed additively (023 owns it, 031 reuses it) and their 10 MB limit must stay | regression tests |
| 010 notifications | four new types; recipients: author, reviewers (Approve holders), Directors and Admins for "no reviewer" | code |
| 013a / 013b | a Contributor has no salary record; payroll must not list them (verify the person lists filter by Teacher/Manager) | check only |
| 015 expenses | `My Expenses` not granted to the Contributor | none |
| 018 / 021 / 033 / 034 / 036 | mobile sign-in refuses the new role as for System/Admin | regression test |
| 025 tasks | C2 unchanged (assignee must be an active Manager); `CONTENT_SUBMISSION` type reused. **Amendment**: 025 exposes no programmatic status change, so `tasks.api` gains a small `TaskProgress` interface (complete with evidence note, verify, send back, by source and source key) used by `content`; the 025 rule that the verifier is not the assignee is applied to the content reviewer, not the system | amendment |
| 028 dashboard | Content tile source (`TileSource`) and an Approvals-inbox item for submitted content | code |
| 035 calendar | none (content is not a planned activity in v1) | none |
| Frontend | role types, `DashboardRouter`, user dialogs and `userManagementApi`, `RolePermissionsGrid`, a11y tests, navigation tests | code |
| docs | roadmap row 029 and the constitution row; `docs/spec-roadmap.md` role count; README/running docs that list five roles | docs |

## Project Structure

```text
specs/029-content-management/ spec.md plan.md research.md data-model.md quickstart.md tasks.md contracts/
backend/src/main/java/com/hls/content/
  api/       ContentSubmitted, ContentReviewed, ContentNoReviewer, ContentStatus, package-info
  internal/  Content, ContentVersion, ContentReview, ContentHistory, repositories, ContentService, ContentQueryService,
             ReviewerResolver, ContentTaskBridge, ContentTiles, ContentDevSeeder
  web/       ContentController
backend/src/main/java/com/hls/files/ (api: FileLimits, streamed store/open; internal: type policy, streaming)
backend/src/main/java/com/hls/identity/ (Role, accessmodel, permissions, user admin changes)
backend/src/main/java/com/hls/notification/internal/ContentEventListener.java
backend/src/main/resources/db/migration/V95__create_content.sql
frontend/src/features/content/ (ContentPage, ContentDetailPage, contentApi, __tests__) + dashboards/ContributorDashboard
```

## Design Decisions

See [research.md](research.md). Highlights: the author is a user holding Create on Content, resolved by grant not role name;
task is optional and tied to Manager authors; reviewer rule is "Approve and not author or submitter of that version";
file limits are a per-type table passed to the store so other owners keep 10 MB; the multipart ceiling is raised globally
and a guard refuses oversize requests early on every non-content upload path.

## Phases

1. Constitution amendment text, sixth role in `identity` (enum, eligibility, seed, navigation, access model, user admin), migration.
2. File store extension (types, per-type limits, streaming) with regression tests for 023/025/031.
3. Content lifecycle service and controller (US1, US2, US4).
4. Lists, visibility, dashboard tile and inbox item (US3), Contributor dashboard (US5).
5. Task bridge, notifications, no-reviewer notice.
6. Frontend, Postman, demo data, roadmap and docs.
