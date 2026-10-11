# Data Model: System Settings (spec 011)

## system_setting (migration V40)

| Column | Type | Notes |
| --- | --- | --- |
| setting_key | varchar(80) PK | a `SettingKey` name in kebab form, e.g. `password.min-length` |
| value | varchar(60) NOT NULL | the override as text; parsed and range-checked by the catalog |
| updated_at | timestamptz NOT NULL | |
| updated_by | uuid NULL | the actor; null for the migration's own copy of OTP values |

The migration also copies the single row of `otp_policy_settings` into three overrides
(`otp.resend-cooldown-seconds`, `otp.max-consecutive-requests`, `otp.lockout-hours`) and then drops
`otp_policy_settings`.

## Catalog (code, `SettingKey`)

| Key | Group | Unit | Min | Max | Built-in default | Deployed property |
| --- | --- | --- | --- | --- | --- | --- |
| mobile.min-app-version | SYSTEM | x.y.z | | | 0.0.0 | hls.mobile.min-app-version |
| mobile.location-wait-seconds | SYSTEM | seconds | 1 | 30 | 4 | hls.mobile.location-wait-seconds |
| mobile.location-reuse-seconds | SYSTEM | seconds | 0 | 300 | 10 | hls.mobile.location-reuse-seconds |
| password.min-length | SECURITY | characters | 8 | 64 | 10 | |
| lockout.max-failed-attempts | SECURITY | attempts | 3 | 20 | 5 | |
| lockout.duration-minutes | SECURITY | minutes | 1 | 1440 | 30 | |
| otp.resend-cooldown-seconds | SECURITY | seconds | 10 | 600 | 30 | |
| otp.max-consecutive-requests | SECURITY | requests | 1 | 20 | 5 | |
| otp.lockout-hours | SECURITY | hours | 1 | 72 | 4 | |
| session.renewal-ttl-days | SECURITY | days | 1 | 90 | 14 | hls.session.renewal-ttl-days |
| session.access-token-ttl-seconds | SECURITY | seconds | 300 | 3600 | 900 | hls.security.jwt.access-token-ttl-seconds |

## Rules

- Whole numbers only (the version excepted). A stored value out of range, or unparseable, is ignored and the next tier
  applies (clamping would hide a bad row; ignoring and flagging is safer for sign-in).
- Rows are overridden in place (this is a current-value table; history is in Change History, which is append-only).
- Entity types in Change History: `SYSTEM_SETTING` (SYSTEM group), `SECURITY_SETTING` (SECURITY group).

## Permission matrix additions (no schema change; modules are text)

`SYSTEM_DASHBOARD(VIEW)`, `SYSTEM_SETTINGS(VIEW, EDIT)`, `SECURITY_SETTINGS(VIEW, EDIT)`.
