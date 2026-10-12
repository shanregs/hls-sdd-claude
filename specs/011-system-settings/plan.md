# Implementation Plan: System Settings

**Branch**: `feature/011-system-settings` | **Date**: 2026-10-11 | **Spec**: [spec.md](spec.md)

## Summary

Add the `settings` module: a catalog of eleven runtime settings stored as overrides in one table, edited from two
screens (System Settings, Security Settings), read by `identity` through a small interface it owns, and audited
through the existing Change History path. Add the System dashboard (health, users, recent audit). Fold the
`otp_policy_settings` table and the three `hls.mobile.*` properties into the store.

## Technical Context

- Backend: Java 25, Spring Boot, Spring Modulith, JPA, Flyway, Postgres (Testcontainers in tests).
- Frontend: React, TypeScript, MUI, vitest.
- Performance: settings are read on sign-in, so a `SnapshotCache` serves them from memory.
- Constraints: one migration, V40 (see research §8). Shared files touched minimally and only by appending: the
  `PermissionModule` enum, the matrix seeder, `NavigationCatalog`, `PermissionEligibility`, `AuditVisibility`,
  `App.tsx`.

## Constitution Check

| Principle | Result |
| --- | --- |
| I Auditable | Every changed value goes to Change History (FR-010). |
| II Five roles, configurable permissions | Three new modules in the seeded matrix; Admin, Director, System only. |
| III Data scope | No business data; the dashboard's audit feed is filtered by the caller's grants. |
| IV One shared application | Items appear in SYSTEM CONFIGURATION (System) and SYSTEM (Admin). |
| V Payroll and receivables | Not touched (FR-015). |
| VII Modular monolith | New `settings` module; `settings` -> `identity`, `audit`; `identity` -> its own interface only. |
| VIII Concurrency | Per-key transactional upsert; later write wins; each audited with its own before value. |
| IX Testability | Per-role endpoint tests, validation tests, behaviour tests at the read sites, UI tests. |
| X Security | Backend enforces every grant; the public app-config exposes only the three mobile values. |

Constitution header gets patch 2.5.3 (Admin joins the System & Security Settings row).

## Project Structure

```text
specs/011-system-settings/   spec, plan, research, data-model, contracts/settings-api.md, quickstart, tasks
backend/src/main/java/com/hls/
  identity/policy/           RuntimeSettings (interface), DefaultRuntimeSettings      [named interface]
  settings/internal/         SettingKey, SystemSetting(+Repository), SettingsService (implements RuntimeSettings),
                             DashboardService
  settings/web/              SettingsController, SystemDashboardController
  audit/api/                 RecentAudit (interface) ; audit/support/RecentAuditImpl
backend/src/main/resources/db/migration/V40__create_system_settings.sql
frontend/src/features/settings/  settingsApi.ts, SettingsForm.tsx, SystemSettingsPage.tsx, SecuritySettingsPage.tsx
frontend/src/dashboards/SystemDashboard.tsx   (real widgets)
```

Edited in place: `PasswordAuthService`, `PasswordPolicy` (+callers), `OtpService`, `OtpRequestThrottle`,
`OtpPolicySettings` (no longer an entity), `SessionService`, `AuthController`, `OtpController`, `JwtTokenProvider`,
`AppConfigController`, `MobileVersionGate`, `MobileFilterConfig`.

## Phases

1. Setup: migration, module skeleton, matrix and navigation entries.
2. Foundational: `RuntimeSettings`, catalog, store, cache, service, validation, audit.
3. US2 Security settings API and the identity read sites (the MVP).
4. US3 Mobile settings and read sites.
5. US1 Dashboard API and `RecentAudit`.
6. US4 Audit visibility.
7. Frontend screens, dashboard, routes.
8. Polish: docs, roadmap, constitution patch, full test run.

## Risks

- Changing `OtpPolicySettings` from an entity touches two existing tests; keep their assertions unchanged.
- The migration drops a table: it must copy first, in one script.
- The version gate is built at startup today; it must read per request or the edit would need a restart.
