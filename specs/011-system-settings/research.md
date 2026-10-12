# Research: System Settings (spec 011)

## 1. One key-value store with a code catalog

**Decision**: one table `system_setting(setting_key PK, value, updated_at, updated_by)` holds only overrides. The list
of settings, their group, unit, range and built-in default is an enum in code (`SettingKey`).

**Why**: eleven scalar values do not justify a table per policy. A catalog in code gives one place for validation and
for the screen's labels, and a new setting later is one enum constant and no migration. A missing row means "use the
default", so a new release never needs a data backfill.

**Rejected**: one wide row like `otp_policy_settings` (every new value is a migration); Spring `@ConfigurationProperties`
only (needs a redeploy, which this spec exists to avoid).

## 2. Effective value order

stored override, then deployed property (`hls.mobile.min-app-version`, `hls.mobile.location-wait-seconds`,
`hls.mobile.location-reuse-seconds`, `hls.session.renewal-ttl-days`, `hls.security.jwt.access-token-ttl-seconds`),
then built-in default. Keeping the property as the second tier keeps every existing environment, test and deployment
working with no data change (SC-006). The OTP table values are copied into overrides by the migration so a tuned
installation keeps its tuning.

## 3. Avoiding a module cycle

`identity` must read the settings (sign-in, OTP, tokens, mobile gate), and the `settings` module must call
`identity` (permission guard, users). A direct dependency in both directions fails Spring Modulith verification.
**Decision**: `identity` owns a small interface `com.hls.identity.policy.RuntimeSettings` (a named interface) and
`settings` implements it as a bean. The dependency points one way: `settings` -> `identity`. Identity code asks
the interface; it never imports `settings`. A `DefaultRuntimeSettings` with the built-in defaults serves unit tests
that build a service by hand.

## 4. Cache

A `SnapshotCache` (the repo's existing in-memory copy of a small table) holds the effective values as one immutable
snapshot. A save calls `invalidateAround()` in its transaction, so readers never keep an uncommitted view. 60 second
time limit as the safety net for a hand-edited row. `OtpService.policyChanged()` (written for this spec by spec 004)
is no longer needed because OTP reads through the same snapshot; the OTP policy cache and entity are removed.

## 5. Where each setting is read

| Setting | Read at |
| --- | --- |
| password min length | `PasswordPolicy` callers (self reset, admin reset) |
| lockout attempts and minutes | `PasswordAuthService.authenticate` |
| OTP cooldown, consecutive, lockout hours | `OtpService.requestCode` through `OtpRequestThrottle` |
| renewal days | `SessionService`, `AuthController`, `OtpController` (credential expiry and cookie max-age) |
| access-token seconds | `JwtTokenProvider.issue` |
| mobile three | `AppConfigController` and `MobileVersionGate` (per request) |

## 6. Audit

`EntityChanged` (published by `audit.api`) is the existing path into Change History. The `settings` module publishes
it directly (one event per changed value, entity type `SYSTEM_SETTING` or `SECURITY_SETTING`, entity id = the key,
field `value`). Events are delivered after commit (Spring Modulith `@ApplicationModuleListener`), so a rolled-back
save writes nothing. `AuditVisibility` maps the two entity types to the matching View grant.

## 7. Dashboard data

- Health: `SELECT 1` through `JdbcTemplate` (UP or DOWN), application version from `hls.app.version`, latest
  successful Flyway version from `flyway_schema_history`, start time and uptime from the JVM runtime bean.
- Users: counts from `AppUserRepository` (total, active, inactive, locked now) and `RoleAssignmentRepository`
  (per role). Both are in the `identity.user` named interface.
- Recent audit: a new `audit.api.RecentAudit` interface implemented inside `audit`, which merges the newest Change
  History and Login History rows, hides entity types the caller cannot view (`AuditVisibility`), and returns 10.
  The settings module holds no audit tables.
- Failures never fail the page: the audit and migration lookups are wrapped, and the response carries a per-section
  status.

## 8. Migration number

Next free is V27 on `main` today, but specs 017a and 025 are being built in parallel and both will take V27+. This
spec uses **V40** to avoid a clash. Flyway is configured with `outOfOrder` unset; V40 is higher than anything else, so
there is no ordering problem on any branch.

## 9. Frontend

Two pages reuse one `SettingsForm` component driven by the server's catalog response (key, label, unit, min, max,
default, current, isDefault, updatedBy, updatedAt). The form validates ranges before sending but the server is the
authority. No new library.
