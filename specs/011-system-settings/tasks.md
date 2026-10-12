---

description: "Task list for System Settings"
---

# Tasks: System Settings

**Input**: `/specs/011-system-settings/` (spec, plan, research, data-model, contracts/settings-api.md, quickstart).
**Prerequisites**: specs 001-004 and 018 merged. Tests are first-class tasks (Constitution IX).
Format: `[ID] [P?] [Story] Description`. P = can run in parallel.

## Phase 1: Setup

- [x] T001 Migration `V40__create_system_settings.sql`: create `system_setting`, copy `otp_policy_settings` into three overrides, drop the old table.
- [x] T002 [P] Add `SYSTEM_DASHBOARD`, `SYSTEM_SETTINGS`, `SECURITY_SETTINGS` to `PermissionModule`; add the three to `PermissionEligibility.MATRIX_MANAGER_ONLY_MODULES`.
- [x] T003 [P] Seed defaults in `PermissionMatrixService` (System: all three; Admin: both settings modules).
- [x] T004 [P] Add nav items in `NavigationCatalog` (System Settings `/system/settings`, Security Settings `/system/security`) for SYSTEM CONFIGURATION (System) and SYSTEM (Admin, Director).
- [x] T005 [P] `AuditVisibility`: map `SYSTEM_SETTING` and `SECURITY_SETTING` to the matching modules.

## Phase 2: Foundational

- [x] T006 `identity.policy.RuntimeSettings` interface and `DefaultRuntimeSettings`; package-info named interface.
- [x] T007 `settings.internal`: `SettingKey` catalog with validation, `SystemSetting` entity and repository.
- [x] T008 Unit tests for `SettingKey` (ranges, parsing, version format) - write first.
- [x] T009 `SettingsService` implements `RuntimeSettings` with a `SnapshotCache`, effective-value order, all-or-nothing save, audit via `EntityChanged`.

## Phase 3: US2 Security settings (P1) MVP

- [x] T010 [US2] Tests: `SecuritySettingsApiTest` (per role 200/403, validation 422 with nothing stored, no-op save, audit entries, lockout takes effect, password minimum takes effect, OTP cooldown takes effect).
- [x] T011 [US2] `SettingsController` GET and PUT `/api/v1/settings/{group}`.
- [x] T012 [US2] Read sites: `PasswordAuthService`, `PasswordPolicy` and its callers, `OtpService` and `OtpRequestThrottle` (remove the OTP entity, repository, `policyChanged`), `SessionService`, `AuthController`, `OtpController`, `JwtTokenProvider`.
- [x] T013 [US2] Update the existing tests that built these classes by hand.

## Phase 4: US3 Mobile settings (P2)

- [x] T014 [US3] Tests: app-config and version gate follow an edit; a malformed version is refused; default from the property.
- [x] T015 [US3] `AppConfigController`, `MobileVersionGate`, `MobileFilterConfig` read per request.

## Phase 5: US1 Dashboard (P1)

- [x] T016 [US1] `audit.api.RecentAudit` and its implementation (merge change and login history, hide by `AuditVisibility`, count failed sign-ins).
- [x] T017 [US1] Tests: `SystemDashboardApiTest` (per role, counts, business entity types hidden from System, newest first, limit 10).
- [x] T018 [US1] `DashboardService` and `SystemDashboardController` GET `/api/v1/system/dashboard`.

## Phase 6: US4 Audit (P2)

- [x] T019 [US4] Tests: Change History shows the entries to System/Admin and hides them from a role without View; unchanged value writes none.

## Phase 7: Frontend

- [x] T020 [P] `settingsApi.ts`, `SettingsForm.tsx`, `SystemSettingsPage.tsx`, `SecuritySettingsPage.tsx`, routes in `App.tsx`.
- [x] T021 [P] Real `SystemDashboard.tsx` (health, users, recent audit).
- [x] T022 UI tests: form validation and save, error display, dashboard render, not-authorized.

## Phase 8: Polish

- [x] T023 Run `mvn test` and frontend `npm test`, `npm run build`, lint; record in `quickstart-results.md`.
- [x] T024 Constitution patch 2.5.3, roadmap row 011, `ApplicationModulesTest` green.

## Dependencies

T001 -> T007 -> T009 -> T011/T012/T015/T018. T002-T005 are independent. Frontend T020-T022 need T011 and T018.
